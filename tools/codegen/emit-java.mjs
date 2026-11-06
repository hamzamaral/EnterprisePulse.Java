// Emits JPA entities and enums from the parsed Prisma schema, plus the shared resource metadata.
import { mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { parseSchema, snake } from './prisma.mjs';
import { buildMeta } from './meta.mjs';

const ROOT = new URL('../../', import.meta.url).pathname.replace(/^\/(\w:)/, '$1');
const PKG = 'dev.enterprisepulse.data';
const OUT = join(ROOT, 'api/src/main/java', ...PKG.split('.'), 'generated');
const META_OUT = join(ROOT, 'api/src/main/resources/metadata.json');

const JAVA_KEYWORDS = new Set([
  'abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch', 'char', 'class', 'const', 'continue', 'default', 'do', 'double',
  'else', 'enum', 'extends', 'final', 'finally', 'float', 'for', 'goto', 'if', 'implements', 'import', 'instanceof', 'int', 'interface',
  'long', 'native', 'new', 'package', 'private', 'protected', 'public', 'return', 'short', 'static', 'strictfp', 'super', 'switch',
  'synchronized', 'this', 'throw', 'throws', 'transient', 'try', 'void', 'volatile', 'while', 'record', 'var', 'yield',
]);

const schema = parseSchema(join(ROOT, 'tools/codegen/source/schema'));
const { models, enums } = schema;

export const javaName = (name) => (JAVA_KEYWORDS.has(name) ? `${name}_` : name);

function javaType(f) {
  const req = !f.optional;
  if (f.kind === 'enum') return f.type;
  if (f.type === 'String') return f.dbType === 'Uuid' ? 'UUID' : 'String';
  if (f.type === 'Int') return req ? 'int' : 'Integer';
  if (f.type === 'BigInt') return f.default === 'autoincrement()' ? 'Long' : req ? 'long' : 'Long';
  if (f.type === 'Decimal') return 'BigDecimal';
  if (f.type === 'Float') return req ? 'double' : 'Double';
  if (f.type === 'Boolean') return req ? 'boolean' : 'Boolean';
  if (f.type === 'DateTime') return f.dbType === 'Date' ? 'LocalDate' : 'Instant';
  if (f.type === 'Json') return 'JsonNode';
  throw new Error(`Unknown type ${f.type}`);
}

function initializer(f) {
  const d = f.default;
  if (f.optional && d === undefined) return '';
  if (d === 'uuid()') return ' = UUID.randomUUID()';
  if (d === 'now()' || f.updatedAt) return ' = Instant.now()';
  if (d === 'autoincrement()' || d === undefined) return '';
  if (f.kind === 'enum') return ` = ${f.type}.${d}`;
  if (f.type === 'Decimal') return ` = new BigDecimal("${d}")`;
  if (f.type === 'String') return ` = ${d}`;
  if (f.type === 'BigInt') return ` = ${d}L`;
  if (f.type === 'Float') return ` = ${d}d`;
  return ` = ${d}`;
}

const HIDDEN = new Set(['passwordHash', 'twoFactorSecret', 'tokenHash']);

function emitModel(model) {
  const scalars = model.fields.filter((f) => f.kind !== 'relation');
  const has = (name) => scalars.find((f) => f.name === name);
  const tenant = scalars.some((f) => f.name === 'companyId' && !f.optional && f.dbType === 'Uuid');
  const soft = !!has('deletedAt');
  const identified = scalars.some((f) => f.name === 'id' && f.isId && f.dbType === 'Uuid');
  const ifaces = [identified && 'Identified', tenant && 'TenantEntity', soft && 'SoftDelete'].filter(Boolean);

  const imports = new Set(['jakarta.persistence.*', 'dev.enterprisepulse.data.*']);
  const lines = [];
  for (const f of scalars) {
    const t = javaType(f);
    if (t === 'UUID') imports.add('java.util.UUID');
    if (t === 'BigDecimal') imports.add('java.math.BigDecimal');
    if (t === 'Instant') imports.add('java.time.Instant');
    if (t === 'LocalDate') imports.add('java.time.LocalDate');
    if (t === 'JsonNode') {
      imports.add('com.fasterxml.jackson.databind.JsonNode');
      imports.add('org.hibernate.annotations.JdbcTypeCode');
      imports.add('org.hibernate.type.SqlTypes');
    }
    const ann = [];
    if (f.isId || model.compositeId?.includes(f.name)) ann.push('@Id');
    if (HIDDEN.has(f.name)) {
      ann.push('@JsonIgnore');
      imports.add('com.fasterxml.jackson.annotation.JsonIgnore');
    }
    const jn = javaName(f.name);
    if (jn !== f.name) {
      ann.push(`@JsonProperty("${f.name}")`);
      imports.add('com.fasterxml.jackson.annotation.JsonProperty');
    }
    if (f.kind === 'enum') ann.push('@Enumerated(EnumType.STRING)');
    if (f.type === 'Json') ann.push('@JdbcTypeCode(SqlTypes.JSON)');
    if (f.default === 'autoincrement()') {
      ann.push('@org.hibernate.annotations.Generated(event = org.hibernate.generator.EventType.INSERT)');
      ann.push(`@Column(name = "${snake(f.name)}", insertable = false, updatable = false)`);
    } else if (f.updatedAt) {
      ann.push('@org.hibernate.annotations.UpdateTimestamp');
      ann.push(`@Column(name = "${snake(f.name)}")`);
    } else {
      ann.push(`@Column(name = "${snake(f.name)}")`);
    }
    lines.push(`    ${ann.join(' ')}\n    public ${t} ${jn}${initializer(f)};`);
  }

  const accessors = [];
  if (identified) accessors.push('    @Override public UUID getId() { return id; }');
  if (tenant) {
    accessors.push('    @Override public UUID getCompanyId() { return companyId; }');
    accessors.push('    @Override public void setCompanyId(UUID value) { companyId = value; }');
  }
  if (soft) {
    accessors.push('    @Override public Instant getDeletedAt() { return deletedAt; }');
    accessors.push('    @Override public void setDeletedAt(Instant value) { deletedAt = value; }');
  }

  const head = [];
  head.push('@Entity');
  head.push(`@Table(name = "${model.table ?? snake(model.name)}")`);
  if (tenant || soft) {
    imports.add('org.hibernate.annotations.SQLRestriction');
    head.push(`@SQLRestriction(Tenancy.${tenant && soft ? 'TENANT_LIVE' : tenant ? 'TENANT' : 'LIVE'})`);
  }
  head.push('@EntityListeners(EntityStamps.class)');
  let keyClass = '';
  if (model.compositeId) {
    head.push(`@IdClass(${model.name}.Key.class)`);
    const keyFields = model.compositeId.map((c) => scalars.find((f) => f.name === c));
    keyClass = `

    public static class Key implements java.io.Serializable {
${keyFields.map((f) => `        public ${javaType(f)} ${javaName(f.name)};`).join('\n')}

        public Key() {}

        public Key(${keyFields.map((f) => `${javaType(f)} ${javaName(f.name)}`).join(', ')}) {
${keyFields.map((f) => `            this.${javaName(f.name)} = ${javaName(f.name)};`).join('\n')}
        }

        @Override public boolean equals(Object o) {
            return o instanceof Key k && ${keyFields.map((f) => `java.util.Objects.equals(${javaName(f.name)}, k.${javaName(f.name)})`).join(' && ')};
        }

        @Override public int hashCode() { return java.util.Objects.hash(${keyFields.map((f) => javaName(f.name)).join(', ')}); }
    }`;
  }
  const doc = model.doc ? `/** ${model.doc.replace(/\*\//g, '* /')} */\n` : '';
  return `// Generated by tools/codegen/emit-java.mjs. Do not edit.
package ${PKG}.generated;

${[...imports].sort().map((i) => `import ${i};`).join('\n')}

${doc}${head.join('\n')}
public class ${model.name}${ifaces.length ? ' implements ' + ifaces.join(', ') : ''} {
${lines.join('\n')}
${accessors.length ? '\n' + accessors.join('\n') : ''}${keyClass}
}
`;
}

rmSync(OUT, { recursive: true, force: true });
mkdirSync(OUT, { recursive: true });

for (const e of Object.values(enums)) {
  writeFileSync(
    join(OUT, `${e.name}.java`),
    `// Generated by tools/codegen/emit-java.mjs. Do not edit.\npackage ${PKG}.generated;\n\npublic enum ${e.name} {\n${e.values.map((v) => `    ${v}`).join(',\n')}\n}\n`,
  );
}
for (const m of Object.values(models)) writeFileSync(join(OUT, `${m.name}.java`), emitModel(m));

const meta = buildMeta(schema, join(ROOT, 'tools/codegen/source/locales'));
writeFileSync(META_OUT, JSON.stringify(meta, null, 1));
console.log(`Generated ${Object.keys(models).length} entities, ${Object.keys(enums).length} enums.`);
