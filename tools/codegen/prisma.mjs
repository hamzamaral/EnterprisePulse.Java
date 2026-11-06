// Minimal Prisma schema parser: enums, models, fields, attributes and paired relations.
import { readFileSync, readdirSync } from 'node:fs';
import { join, basename } from 'node:path';

const SCALARS = new Set(['String', 'Int', 'BigInt', 'Decimal', 'Float', 'Boolean', 'DateTime', 'Json']);

function stripComment(line) {
  let inString = false;
  for (let i = 0; i < line.length - 1; i++) {
    if (line[i] === '"') inString = !inString;
    if (!inString && line[i] === '/' && line[i + 1] === '/') return line.slice(0, i);
  }
  return line;
}

/** Splits `@a @b(x, y(z)) @c` into attribute strings, respecting parentheses and quotes. */
function splitAttributes(text) {
  const out = [];
  let depth = 0;
  let current = '';
  let inString = false;
  for (const ch of text) {
    if (ch === '"') inString = !inString;
    if (!inString) {
      if (ch === '(') depth++;
      if (ch === ')') depth--;
      if (ch === '@' && depth === 0 && current.trim()) {
        out.push(current.trim());
        current = '';
      }
    }
    current += ch;
  }
  if (current.trim()) out.push(current.trim());
  return out;
}

function parseArgs(inner) {
  const args = { positional: [] };
  let depth = 0;
  let inString = false;
  let current = '';
  const parts = [];
  for (const ch of inner) {
    if (ch === '"') inString = !inString;
    if (!inString) {
      if (ch === '[' || ch === '(') depth++;
      if (ch === ']' || ch === ')') depth--;
      if (ch === ',' && depth === 0) {
        parts.push(current.trim());
        current = '';
        continue;
      }
    }
    current += ch;
  }
  if (current.trim()) parts.push(current.trim());
  for (const part of parts) {
    const m = part.match(/^(\w+)\s*:\s*(.+)$/s);
    if (m) args[m[1]] = m[2].trim();
    else args.positional.push(part);
  }
  return args;
}

const list = (text) =>
  text
    .replace(/^\[|\]$/g, '')
    .split(',')
    .map((s) => s.trim().replace(/\(.*\)$/, ''))
    .filter(Boolean);

function parseField(line, modelName) {
  const m = line.match(/^(\w+)\s+(\w+)(\[\])?(\?)?\s*(.*)$/);
  if (!m) throw new Error(`Cannot parse field in ${modelName}: ${line}`);
  const [, name, type, isList, optional, rest] = m;
  const field = { name, type, isList: !!isList, optional: !!optional, attrs: {} };
  for (const attr of splitAttributes(rest)) {
    const am = attr.match(/^@([\w.]+)(?:\((.*)\))?$/s);
    if (!am) continue;
    const [, key, inner] = am;
    if (key === 'id') field.isId = true;
    else if (key === 'unique') field.unique = true;
    else if (key === 'updatedAt') field.updatedAt = true;
    else if (key === 'default') field.default = inner;
    else if (key.startsWith('db.')) {
      field.dbType = key.slice(3);
      if (inner) field.dbArgs = inner.split(',').map((s) => Number(s.trim()));
    } else if (key === 'relation') {
      const args = parseArgs(inner ?? '');
      field.relation = {
        name: args.name ? JSON.parse(args.name) : args.positional[0] ? JSON.parse(args.positional[0]) : '',
        fields: args.fields ? list(args.fields) : null,
        references: args.references ? list(args.references) : null,
        onDelete: args.onDelete ?? null,
      };
    }
  }
  return field;
}

export function parseSchema(dir) {
  const enums = {};
  const models = {};
  for (const file of readdirSync(dir).filter((f) => f.endsWith('.prisma')).sort()) {
    const module = basename(file, '.prisma');
    const lines = readFileSync(join(dir, file), 'utf8').split(/\r?\n/);
    let block = null;
    let doc = [];
    for (const raw of lines) {
      const trimmed = raw.trim();
      if (trimmed.startsWith('///')) {
        doc.push(trimmed.slice(3).trim());
        continue;
      }
      const line = stripComment(raw).trim();
      if (!line) continue;
      const open = line.match(/^(model|enum)\s+(\w+)\s*\{$/);
      if (open) {
        block =
          open[1] === 'enum'
            ? { kind: 'enum', name: open[2], values: [], file: module }
            : {
                kind: 'model',
                name: open[2],
                file: module,
                fields: [],
                uniques: [],
                indexes: [],
                compositeId: null,
                table: null,
                doc: doc.join(' '),
              };
        doc = [];
        continue;
      }
      if (line === '}') {
        if (block?.kind === 'enum') enums[block.name] = block;
        if (block?.kind === 'model') models[block.name] = block;
        block = null;
        doc = [];
        continue;
      }
      if (!block) continue;
      if (block.kind === 'enum') {
        block.values.push(line.split(/\s+/)[0]);
        continue;
      }
      if (line.startsWith('@@')) {
        const bm = line.match(/^@@(\w+)\((.*)\)$/);
        if (!bm) continue;
        const [, key, inner] = bm;
        const args = parseArgs(inner);
        if (key === 'map') block.table = JSON.parse(args.positional[0]);
        if (key === 'unique') block.uniques.push(list(args.positional[0]));
        if (key === 'index') block.indexes.push(list(args.positional[0]));
        if (key === 'id') block.compositeId = list(args.positional[0]);
        continue;
      }
      const field = parseField(line, block.name);
      field.doc = doc.join(' ');
      doc = [];
      block.fields.push(field);
    }
  }

  for (const model of Object.values(models)) {
    for (const f of model.fields) {
      f.kind = SCALARS.has(f.type) ? 'scalar' : enums[f.type] ? 'enum' : models[f.type] ? 'relation' : null;
      if (!f.kind) throw new Error(`Unknown type ${f.type} in ${model.name}.${f.name}`);
    }
  }

  // Pair each relation field with its counterpart on the other model.
  for (const model of Object.values(models)) {
    for (const f of model.fields.filter((x) => x.kind === 'relation')) {
      const relName = f.relation?.name ?? '';
      const target = models[f.type];
      const candidates = target.fields.filter(
        (x) => x.kind === 'relation' && x.type === model.name && (x.relation?.name ?? '') === relName && x !== f,
      );
      if (candidates.length !== 1) {
        throw new Error(`Cannot pair relation ${model.name}.${f.name} -> ${f.type} (${candidates.length})`);
      }
      f.inverse = candidates[0].name;
      f.owning = !!f.relation?.fields;
      if (!f.owning && !candidates[0].relation?.fields) {
        throw new Error(`Implicit many-to-many not supported: ${model.name}.${f.name}`);
      }
    }
  }

  return { enums, models };
}

export const pascal = (s) => s[0].toUpperCase() + s.slice(1);
export const camel = (s) => s[0].toLowerCase() + s.slice(1);
export const snake = (s) =>
  s
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/([A-Z])([A-Z][a-z])/g, '$1_$2')
    .toLowerCase();

/** PurchaseOrder -> purchase-orders */
export function resourceName(model) {
  const kebab = snake(model).replace(/_/g, '-');
  if (/[^s]s$/.test(kebab)) return kebab;
  if (/(ss|x|ch|sh)$/.test(kebab)) return kebab + 'es';
  if (/[^aeiou]y$/.test(kebab)) return kebab.slice(0, -1) + 'ies';
  return kebab + 's';
}
