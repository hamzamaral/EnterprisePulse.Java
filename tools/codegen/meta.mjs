// Builds the runtime metadata (resources, labels, permissions, navigation) shared by API and web.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { camel, resourceName } from './prisma.mjs';
import { MODEL_TR, FIELD_TR } from './tr-dict.mjs';

export const PERMISSION_REGISTRY = {
  companies: ['read', 'create', 'update', 'delete'],
  branches: ['read', 'create', 'update', 'delete'],
  users: ['read', 'create', 'update', 'delete'],
  roles: ['read', 'create', 'update', 'delete'],
  audit: ['read'],
  files: ['read', 'create', 'delete'],
  numbering: ['read', 'update'],
  settings: ['read', 'update'],
  workflow: ['read', 'create', 'update', 'delete', 'start'],
  notifications: ['send'],
  departments: ['read', 'create', 'update', 'delete'],
  positions: ['read', 'create', 'update', 'delete'],
  employees: ['read', 'create', 'update', 'delete'],
  leave: ['read', 'create', 'update'],
  attendance: ['read', 'update'],
  assets: ['read', 'create', 'update', 'delete', 'assign', 'count'],
  inventory: ['read', 'create', 'update', 'delete', 'move', 'count'],
  partners: ['read', 'create', 'update', 'delete'],
  purchasing: ['read', 'create', 'update', 'approve', 'receive'],
  sales: ['read', 'create', 'update', 'approve', 'deliver', 'invoice'],
  finance: ['read', 'create', 'update', 'approve'],
  crm: ['read', 'create', 'update', 'delete'],
  expenses: ['read', 'create', 'update', 'pay'],
  projects: ['read', 'create', 'update', 'delete'],
  payroll: ['read', 'create', 'update', 'approve', 'pay'],
  recruitment: ['read', 'create', 'update', 'delete', 'hire'],
  performance: ['read', 'create', 'update', 'delete'],
  training: ['read', 'create', 'update', 'delete'],
  support: ['read', 'create', 'update', 'delete'],
  contracts: ['read', 'create', 'update', 'delete'],
  budgets: ['read', 'update', 'approve'],
  fleet: ['read', 'update'],
  accounting: ['read', 'update', 'approve', 'close'],
  einvoice: ['read', 'send', 'update'],
  production: ['read', 'update', 'record'],
};

const ALL_CODES = Object.entries(PERMISSION_REGISTRY).flatMap(([m, actions]) => actions.map((a) => `${m}:${a}`));

export const ROLES = {
  admin: { name: 'Şirket Yöneticisi', description: 'Şirket içindeki tüm modüllere tam erişim', codes: ALL_CODES },
  manager: {
    name: 'Müdür',
    description: 'Kayıtları görüntüler, onay süreçlerini yönetir',
    codes: [
      'branches:read', 'users:read', 'roles:read', 'audit:read', 'files:read', 'files:create', 'numbering:read',
      'settings:read', 'workflow:read', 'workflow:start', 'notifications:send', 'departments:read',
      'positions:read', 'employees:read', 'leave:read', 'attendance:read', 'assets:read', 'inventory:read',
      'partners:read', 'purchasing:read', 'purchasing:approve', 'sales:read', 'sales:approve', 'finance:read',
      'crm:read', 'crm:create', 'crm:update', 'expenses:read', 'projects:read', 'projects:create',
      'recruitment:read', 'training:read', 'support:read', 'support:update', 'contracts:read', 'budgets:read',
      'fleet:read', 'accounting:read', 'einvoice:read', 'production:read',
    ],
  },
  employee: {
    name: 'Personel',
    description: 'Temel self-servis erişimi',
    codes: ['files:read', 'files:create', 'workflow:start', 'settings:read'],
  },
};

const FILE_MODULE = {
  accounting: 'accounting', assets: 'assets', attendance: 'attendance', budgets: 'budgets', contracts: 'contracts',
  crm: 'crm', einvoice: 'einvoice', expenses: 'expenses', finance: 'finance', fleet: 'fleet', hr: 'leave',
  identity: 'users', inventory: 'inventory', organization: 'companies', partners: 'partners', payroll: 'payroll',
  performance: 'performance', platform: 'settings', production: 'production', projects: 'projects',
  purchasing: 'purchasing', recruitment: 'recruitment', sales: 'sales', settings: 'settings', support: 'support',
  training: 'training', workflow: 'workflow',
};

const MODEL_MODULE = {
  Department: 'departments', Position: 'positions', Employee: 'employees', Role: 'roles', Permission: 'roles',
  RolePermission: 'roles', Branch: 'branches', AuditLog: 'audit', FileObject: 'files', NumberSequence: 'numbering',
};

/** Internal tables without a generic endpoint. */
const HIDDEN = new Set(['RefreshToken', 'PasswordResetToken', 'Notification', 'RolePermission']);

/** Ledgers, histories and derived tables: readable, written only by business services. */
const READ_ONLY = new Set([
  'AuditLog', 'Permission', 'StockBalance', 'LotBalance', 'StockTransaction', 'StockReservation',
  'PartnerLedgerEntry', 'TreasuryTransaction', 'PaymentAllocation', 'WorkflowInstance', 'WorkflowAction',
  'BudgetEvent', 'BudgetAlert', 'ContractEvent', 'ChequeEvent', 'EInvoiceEvent', 'TicketEvent', 'JournalLine',
  'ApplicationActivity', 'UserRole', 'UserCompany',
]);

/** Created by a dedicated endpoint (password, invitation...). */
const CUSTOM_CREATE = new Set(['User', 'FileObject', 'Company']);

const SENSITIVE = new Set(['passwordHash', 'twoFactorSecret', 'tokenHash']);
const SYSTEM_FIELDS = new Set(['id', 'companyId', 'createdAt', 'updatedAt', 'deletedAt']);

/** Number series used when a document is created without a number. */
export const SEQUENCES = {
  PurchaseRequest: 'PURCHASE_REQUEST', PurchaseOrder: 'PURCHASE_ORDER', SupplierQuote: 'SUPPLIER_QUOTE',
  GoodsReceipt: 'GOODS_RECEIPT', PurchaseInvoice: 'PURCHASE_INVOICE', SalesQuote: 'SALES_QUOTE',
  SalesOrder: 'SALES_ORDER', DeliveryNote: 'DELIVERY_NOTE', SalesInvoice: 'SALES_INVOICE', SalesReturn: 'SALES_RETURN',
  Cheque: 'CHEQUE', Asset: 'ASSET', AssetAssignment: 'ASSET_ASSIGNMENT', AssetCount: 'ASSET_COUNT',
  StockMovement: 'STOCK_MOVEMENT', StockCount: 'STOCK_COUNT', LeaveRequest: 'LEAVE_REQUEST', Employee: 'EMPLOYEE',
  BusinessPartner: 'PARTNER', Lead: 'LEAD', Opportunity: 'OPPORTUNITY', ExpenseReport: 'EXPENSE_REPORT',
  Project: 'PROJECT', PayrollPeriod: 'PAYROLL', JobPosting: 'JOB_POSTING', Ticket: 'TICKET', Contract: 'CONTRACT',
  JournalEntry: 'JOURNAL_ENTRY',
};

export const SEQUENCE_DEFS = [
  ['PURCHASE_REQUEST', 'Satın Alma Talebi', 'STA'], ['PURCHASE_ORDER', 'Satın Alma Siparişi', 'SAS'],
  ['SUPPLIER_QUOTE', 'Tedarikçi Teklifi', 'TKL'], ['GOODS_RECEIPT', 'Mal Kabul', 'MKB'],
  ['PURCHASE_INVOICE', 'Alış Faturası', 'ALF'], ['SALES_QUOTE', 'Satış Teklifi', 'STK'],
  ['SALES_ORDER', 'Satış Siparişi', 'SPS'], ['DELIVERY_NOTE', 'İrsaliye', 'IRS'],
  ['SALES_INVOICE', 'Satış Faturası', 'FTR'], ['SALES_RETURN', 'Satış İadesi', 'IAD'],
  ['COLLECTION', 'Tahsilat', 'TAH'], ['PAYMENT', 'Ödeme', 'ODM'], ['CHEQUE', 'Çek', 'CEK'],
  ['PROMISSORY_NOTE', 'Senet', 'SNT'], ['ASSET', 'Demirbaş', 'DMB'], ['ASSET_ASSIGNMENT', 'Zimmet Tutanağı', 'ZMT'],
  ['ASSET_COUNT', 'Demirbaş Sayımı', 'DSY'], ['STOCK_MOVEMENT', 'Stok Hareketi', 'STH'],
  ['STOCK_COUNT', 'Stok Sayımı', 'SAY'], ['LEAVE_REQUEST', 'İzin Talebi', 'IZN'],
  ['EMPLOYEE', 'Personel Sicil No', 'PRS', false], ['PARTNER', 'Cari Hesap', 'CAR', false],
  ['LEAD', 'Potansiyel Müşteri', 'ADY'], ['OPPORTUNITY', 'Satış Fırsatı', 'FRS'],
  ['EXPENSE_REPORT', 'Masraf Formu', 'MSF'], ['PROJECT', 'Proje', 'PRJ'], ['PAYROLL', 'Bordro', 'BRD'],
  ['JOB_POSTING', 'İş İlanı', 'ILN'], ['TICKET', 'Destek Talebi', 'TKT'], ['CONTRACT', 'Sözleşme', 'SZL'],
  ['JOURNAL_ENTRY', 'Yevmiye Fişi', 'YEV'], ['EINVOICE', 'e-Fatura', 'EPF'], ['EARCHIVE', 'e-Arşiv', 'EPA'],
  ['WORK_ORDER', 'İş Emri', 'IEM'],
];

/** [labelKey, target, permission]. target: `r:<resource>[?query]` or `p:<page path>`. */
export const NAVIGATION = [
  ['nav.groups.general', [
    ['nav.dashboard', 'p:', null], ['nav.inbox', 'p:workflow/inbox', null],
  ]],
  ['nav.groups.hr', [
    ['nav.myLeave', 'p:hr/my-leave', null], ['nav.myAttendance', 'p:hr/my-attendance', null],
    ['nav.orgChart', 'p:hr/org-chart', null], ['nav.employees', 'r:employees', 'employees:read'],
    ['nav.departments', 'r:departments', 'departments:read'], ['nav.positions', 'r:positions', 'positions:read'],
    ['nav.leaveRequests', 'r:leave-requests', 'leave:read'], ['nav.leaveSettings', 'r:leave-types', 'leave:update'],
    ['nav.timesheet', 'p:hr/timesheet', 'attendance:read'], ['nav.shifts', 'r:shifts', 'attendance:read'],
  ]],
  ['nav.groups.expenses', [
    ['nav.myExpenses', 'p:expenses/my', null], ['nav.expenseReports', 'r:expense-reports', 'expenses:read'],
    ['nav.expenseSettings', 'r:expense-categories', 'expenses:update'],
  ]],
  ['nav.groups.payroll', [
    ['nav.myPayslips', 'p:payroll/my', null], ['nav.payrollPeriods', 'r:payroll-periods', 'payroll:read'],
    ['nav.salaries', 'r:salary-records', 'payroll:read'], ['nav.payrollSettings', 'r:payroll-parameters', 'payroll:update'],
  ]],
  ['nav.groups.recruitment', [
    ['nav.myInterviews', 'p:recruitment/interviews', null], ['nav.jobPostings', 'r:job-postings', 'recruitment:read'],
    ['nav.candidates', 'r:candidates', 'recruitment:read'], ['nav.applications', 'r:job-applications', 'recruitment:read'],
  ]],
  ['nav.groups.development', [
    ['nav.myReviews', 'p:performance/my', null], ['nav.myTrainings', 'p:training/my', null],
    ['nav.reviewCycles', 'r:review-cycles', 'performance:read'], ['nav.competencies', 'r:competencies', 'performance:read'],
    ['nav.trainingSessions', 'r:training-sessions', 'training:read'], ['nav.trainingCourses', 'r:training-courses', 'training:read'],
    ['nav.trainingCompliance', 'p:training/compliance', 'training:read'],
  ]],
  ['nav.groups.support', [
    ['nav.myTickets', 'p:support/my', null], ['nav.supportQueue', 'r:tickets', 'support:read'],
    ['nav.supportSettings', 'r:ticket-categories', 'support:update'],
  ]],
  ['nav.groups.projects', [
    ['nav.myTasks', 'p:projects/my-tasks', null], ['nav.projectTimesheet', 'p:projects/timesheet', null],
    ['nav.projects', 'r:projects', null],
  ]],
  ['nav.groups.assets', [
    ['nav.myAssets', 'p:assets/my', null], ['nav.assetList', 'r:assets', 'assets:read'],
    ['nav.assignments', 'r:asset-assignments', 'assets:read'], ['nav.assetCounts', 'r:asset-counts', 'assets:read'],
    ['nav.assetSettings', 'r:asset-categories', 'assets:update'],
  ]],
  ['nav.groups.inventory', [
    ['nav.stockLevels', 'p:inventory', 'inventory:read'], ['nav.products', 'r:products', 'inventory:read'],
    ['nav.stockMovements', 'r:stock-movements', 'inventory:read'], ['nav.stockCounts', 'r:stock-counts', 'inventory:read'],
    ['nav.inventorySettings', 'r:warehouses', 'inventory:update'],
  ]],
  ['nav.groups.production', [
    ['nav.workOrders', 'r:work-orders', 'production:read'], ['nav.boms', 'r:bill-of-materials', 'production:read'],
    ['nav.requirements', 'p:production/requirements', 'production:read'],
  ]],
  ['nav.groups.purchasing', [
    ['nav.purchaseRequests', 'r:purchase-requests', ['workflow:start', 'purchasing:create', 'purchasing:read']],
    ['nav.supplierQuotes', 'r:supplier-quotes', 'purchasing:read'], ['nav.purchaseOrders', 'r:purchase-orders', 'purchasing:read'],
    ['nav.goodsReceipts', 'r:goods-receipts', 'purchasing:read'],
    ['nav.purchaseInvoices', 'r:purchase-invoices', ['purchasing:read', 'finance:read']],
  ]],
  ['nav.groups.crm', [
    ['nav.leads', 'r:leads', 'crm:read'], ['nav.opportunities', 'r:opportunities', 'crm:read'],
    ['nav.crmActivities', 'r:crm-activities', 'crm:read'],
  ]],
  ['nav.groups.sales', [
    ['nav.salesQuotes', 'r:sales-quotes', 'sales:read'], ['nav.salesOrders', 'r:sales-orders', 'sales:read'],
    ['nav.deliveries', 'r:delivery-notes', 'sales:read'],
    ['nav.salesInvoices', 'r:sales-invoices', ['sales:read', 'finance:read']],
    ['nav.salesReturns', 'r:sales-returns', ['sales:read', 'finance:read']],
  ]],
  ['nav.groups.finance', [
    ['nav.partners', 'r:business-partners', 'partners:read'], ['nav.treasuryAccounts', 'r:treasury-accounts', 'finance:read'],
    ['nav.collections', 'r:partner-payments?direction=IN', 'finance:read'],
    ['nav.payments', 'r:partner-payments?direction=OUT', 'finance:read'],
    ['nav.cheques', 'r:cheques', 'finance:read'], ['nav.aging', 'p:finance/aging', 'finance:read'],
  ]],
  ['nav.groups.accounting', [
    ['nav.accounting', 'p:accounting', 'accounting:read'], ['nav.journal', 'r:journal-entries', 'accounting:read'],
    ['nav.chartOfAccounts', 'r:ledger-accounts', 'accounting:read'],
    ['nav.trialBalance', 'p:accounting/trial-balance', 'accounting:read'],
    ['nav.balanceSheet', 'p:accounting/balance-sheet', 'accounting:read'],
    ['nav.incomeStatement', 'p:accounting/income-statement', 'accounting:read'],
    ['nav.eInvoices', 'r:e-invoices', 'einvoice:read'],
  ]],
  ['nav.groups.contracts', [
    ['nav.contracts', 'r:contracts', 'contracts:read'], ['nav.contractTypes', 'r:contract-types', 'contracts:update'],
  ]],
  ['nav.groups.budgets', [
    ['nav.budgets', 'r:budgets', 'budgets:read'], ['nav.budgetEntries', 'r:budget-entries', 'budgets:read'],
    ['nav.budgetItems', 'r:budget-items', 'budgets:update'],
  ]],
  ['nav.groups.fleet', [
    ['nav.fleet', 'r:vehicles', 'fleet:read'], ['nav.fuelLog', 'r:vehicle-fuel-entries', 'fleet:read'],
    ['nav.myVehicles', 'p:fleet/my', null],
  ]],
  ['nav.groups.reports', [
    ['nav.salesReport', 'p:reports/sales', 'sales:read'], ['nav.purchaseReport', 'p:reports/purchases', 'purchasing:read'],
    ['nav.cashFlow', 'p:reports/cash-flow', 'finance:read'],
  ]],
  ['nav.groups.workflow', [
    ['nav.instances', 'r:workflow-instances', 'workflow:read'], ['nav.definitions', 'r:workflow-definitions', 'workflow:read'],
  ]],
  ['nav.groups.organization', [
    ['nav.companies', 'r:companies', 'companies:read'], ['nav.branches', 'r:branches', 'branches:read'],
    ['nav.users', 'r:users', 'users:read'], ['nav.roles', 'p:roles', 'roles:read'],
  ]],
  ['nav.groups.system', [
    ['nav.audit', 'r:audit-logs', 'audit:read'], ['nav.settings', 'p:settings', 'settings:read'],
  ]],
];

function flatten(obj, prefix = '', out = {}) {
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object') flatten(v, key, out);
    else out[key] = String(v);
  }
  return out;
}

function humanize(name) {
  const words = name
    .replace(/Id$/, '')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .toLowerCase();
  return words[0].toUpperCase() + words.slice(1);
}

class Labels {
  constructor(dict) {
    this.dict = dict;
    this.flat = flatten(dict);
  }
  key(key) {
    return this.flat[key];
  }
  /** Field label: the model's own section first, then common, then anywhere. */
  field(name, sections) {
    const variants = [name, name.replace(/Id$/, ''), name.replace(/Ids$/, 's')];
    for (const variant of variants) {
      for (const section of [...sections, 'common']) {
        const v = this.flat[`${section}.${variant}`];
        if (v && !v.includes('{{')) return v;
      }
    }
    for (const variant of variants) {
      const hit = Object.entries(this.flat).find(
        ([k, v]) => k.endsWith(`.${variant}`) && !v.includes('{{') && !/^(validation|errors|auth|nav)\./.test(k),
      );
      if (hit) return hit[1];
    }
    return null;
  }
  /** Enum labels: an object in the section whose keys cover every enum value. */
  enumValues(values, sections) {
    const find = (root) => {
      const stack = [root];
      while (stack.length) {
        const node = stack.pop();
        if (!node || typeof node !== 'object') continue;
        const keys = Object.keys(node);
        if (values.every((v) => typeof node[v] === 'string') && keys.length <= values.length + 2) return node;
        stack.push(...Object.values(node));
      }
      return null;
    };
    for (const section of sections) {
      const hit = find(this.dict[section]);
      if (hit) return Object.fromEntries(values.map((v) => [v, hit[v]]));
    }
    const hit = find(this.dict);
    if (hit) return Object.fromEntries(values.map((v) => [v, hit[v]]));
    return Object.fromEntries(values.map((v) => [v, this.flat[Object.keys(this.flat).find((k) => k.endsWith(`.${v}`))] ?? humanize(v)]));
  }
}

const TR_FALLBACK = {
  orderDate: 'Sipariş tarihi', currencyCode: 'Para birimi', exchangeRate: 'Kur', approvedAt: 'Onay tarihi',
  deletedAt: 'Silinme', id: 'Kimlik', number: 'Numara', date: 'Tarih', amount: 'Tutar', quantity: 'Miktar',
  unitPrice: 'Birim fiyat', total: 'Toplam', lineNo: 'Sıra', sortOrder: 'Sıra', isActive: 'Aktif', type: 'Tür',
  title: 'Başlık', priority: 'Öncelik', dueDate: 'Vade', issueDate: 'Düzenleme tarihi', invoiceDate: 'Fatura tarihi',
  startDate: 'Başlangıç', endDate: 'Bitiş', year: 'Yıl', month: 'Ay', rate: 'Oran', note: 'Not', postedAt: 'Kayıt tarihi',
  cancelledAt: 'İptal tarihi', completedAt: 'Tamamlanma', startedAt: 'Başlama', decidedAt: 'Karar tarihi',
  vatRate: 'KDV oranı', vatAmount: 'KDV tutarı', discountRate: 'İskonto oranı', netAmount: 'Net tutar',
  lineTotal: 'Satır toplamı', amountBase: 'Yerel tutar', entityType: 'Kayıt türü', entityId: 'Kayıt', action: 'İşlem',
  ip: 'IP', userAgent: 'Tarayıcı', requestId: 'İstek', oldValues: 'Eski değerler', newValues: 'Yeni değerler',
  userEmail: 'Kullanıcı e-postası', workflowInstanceId: 'Onay süreci', receivedAt: 'Teslim tarihi',
  expectedDate: 'Beklenen tarih', validUntil: 'Geçerlilik', cost: 'Maliyet', unitCost: 'Birim maliyet',
  value: 'Değer', key: 'Anahtar', symbol: 'Sembol', decimals: 'Ondalık', buying: 'Alış', selling: 'Satış', source: 'Kaynak',
  rateDate: 'Kur tarihi', prefix: 'Önek', padding: 'Basamak', includeYear: 'Yıl içerir', resetYearly: 'Yıllık sıfırla',
  currentYear: 'Yıl', nextValue: 'Sıradaki', order: 'Sıra', approverType: 'Onaycı türü', currentStepOrder: 'Adım',
  isDefault: 'Varsayılan', isSystem: 'Sistem', module: 'Modül', locale: 'Dil', isSuperAdmin: 'Süper yönetici',
  twoFactorEnabled: 'İki adımlı doğrulama', lastLoginAt: 'Son giriş', failedLoginCount: 'Hatalı giriş', lockedUntil: 'Kilit bitişi',
  baseCurrency: 'Ana para birimi', legalName: 'Ünvan', taxNumber: 'Vergi no', taxOffice: 'Vergi dairesi', country: 'Ülke',
  avatarUrl: 'Profil resmi', storageKey: 'Depolama anahtarı', originalName: 'Dosya adı', mimeType: 'Tür', size: 'Boyut',
  link: 'Bağlantı', readAt: 'Okunma', seq: 'Sıra',
};

const LABEL_CANDIDATES = ['name', 'title', 'number', 'code', 'plate', 'sku', 'subject', 'email', 'key'];

function fieldKind(f) {
  if (f.kind === 'enum') return 'enum';
  if (f.type === 'String') return f.dbType === 'Uuid' ? 'uuid' : /description|notes|body|comment|reason|address|content/i.test(f.name) ? 'text' : 'string';
  if (f.type === 'Int') return 'int';
  if (f.type === 'BigInt') return 'long';
  if (f.type === 'Float') return 'decimal';
  if (f.type === 'Decimal') return 'decimal';
  if (f.type === 'Boolean') return 'bool';
  if (f.type === 'DateTime') return f.dbType === 'Date' ? 'date' : 'datetime';
  if (f.type === 'Json') return 'json';
  throw new Error(f.type);
}

export function buildMeta(schema, localesDir) {
  const tr = new Labels(JSON.parse(readFileSync(join(localesDir, 'tr.json'), 'utf8')));
  const en = new Labels(JSON.parse(readFileSync(join(localesDir, 'en.json'), 'utf8')));
  const { models, enums } = schema;
  const resources = [];

  for (const model of Object.values(models)) {
    if (HIDDEN.has(model.name)) continue;
    const module = MODEL_MODULE[model.name] ?? FILE_MODULE[model.file];
    const resource = resourceName(model.name);
    const sections = [
      ...new Set([camel(resource.replace(/-(\w)/g, (_, c) => c.toUpperCase())), module, model.file, 'trade']),
    ];
    const plural = camel(resource.replace(/-(\w)/g, (_, c) => c.toUpperCase()));
    const fkTargets = {};
    for (const f of model.fields) {
      if (f.kind === 'relation' && f.owning) fkTargets[f.relation.fields[0]] = { model: f.type, relation: f.name };
    }
    const scalarFields = model.fields.filter((f) => f.kind !== 'relation');
    const names = new Set(scalarFields.map((f) => f.name));
    const labelFields =
      names.has('firstName') && names.has('lastName')
        ? ['firstName', 'lastName']
        : [LABEL_CANDIDATES.find((c) => names.has(c)) ?? 'id'];

    const fields = scalarFields.map((f) => {
      const fk = fkTargets[f.name];
      const out = {
        name: f.name,
        kind: fieldKind(f),
        required: !f.optional && f.default === undefined && !f.updatedAt && !SYSTEM_FIELDS.has(f.name) && f.type !== 'Boolean',
        readOnly: SYSTEM_FIELDS.has(f.name) || !!f.updatedAt || f.default === 'autoincrement()' || f.isId,
        label: {
          tr: TR_FALLBACK[f.name] && ['id', 'number', 'deletedAt'].includes(f.name)
            ? TR_FALLBACK[f.name]
            : tr.field(f.name, sections) ?? FIELD_TR[f.name] ?? TR_FALLBACK[f.name] ?? (fk ? `@${resourceName(fk.model)}` : humanize(f.name)),
          en: en.field(f.name, sections) ?? humanize(f.name),
        },
      };
      if (SENSITIVE.has(f.name)) out.hidden = true;
      if (f.kind === 'enum') {
        out.enum = f.type;
        out.values = enums[f.type].values;
      }
      if (f.type === 'Decimal' && f.dbArgs) out.scale = f.dbArgs[1];
      if (fk && models[fk.model] && !HIDDEN.has(fk.model)) out.ref = resourceName(fk.model);
      if (f.default !== undefined && f.default !== 'uuid()' && f.default !== 'now()' && f.default !== 'autoincrement()') {
        out.default = f.default.replace(/^"|"$/g, '');
      }
      return out;
    });

    // Children: models that point here with a cascading foreign key (lines, steps, events...).
    const children = [];
    for (const f of model.fields.filter((x) => x.kind === 'relation' && !x.owning && x.isList)) {
      const child = models[f.type];
      const back = child.fields.find((x) => x.name === f.inverse);
      if (HIDDEN.has(child.name) || back.relation.onDelete !== 'Cascade') continue;
      children.push({ resource: resourceName(child.name), field: back.relation.fields[0], name: f.name });
    }

    // Parent: the cascading owner of a line-like model.
    const parentRel = model.fields.find((f) => f.kind === 'relation' && f.owning && f.relation.onDelete === 'Cascade');
    const statusField = scalarFields.find((f) => f.name === 'status' && f.kind === 'enum');

    resources.push({
      resource,
      model: model.name,
      table: model.table ?? model.name,
      module,
      label: {
        tr: tr.key(`nav.${plural}`) ?? tr.key(`${plural}.title`) ?? MODEL_TR[model.name] ?? humanize(model.name),
        en: en.key(`nav.${plural}`) ?? en.key(`${plural}.title`) ?? humanize(model.name),
      },
      labelFields,
      tenant: scalarFields.some((f) => f.name === 'companyId' && !f.optional),
      softDelete: names.has('deletedAt'),
      readOnly: READ_ONLY.has(model.name),
      customCreate: CUSTOM_CREATE.has(model.name),
      sequence: SEQUENCES[model.name] ?? null,
      draftOnly: !!statusField && enums[statusField.type].values.includes('DRAFT'),
      parent: parentRel ? { field: parentRel.relation.fields[0], resource: resourceName(parentRel.type) } : null,
      compositeKey: model.compositeId,
      fields,
      children,
    });
  }

  const byResource = Object.fromEntries(resources.map((r) => [r.resource, r]));
  for (const r of resources) {
    for (const f of r.fields) {
      if (f.label.tr.startsWith('@')) f.label.tr = byResource[f.label.tr.slice(1)]?.label.tr ?? humanize(f.name);
    }
  }

  const navigation = NAVIGATION.map(([groupKey, items]) => ({
    label: { tr: tr.key(groupKey), en: en.key(groupKey) },
    items: items.map(([key, target, permission]) => {
      const [type, rest] = [target.slice(0, 1), target.slice(2)];
      const [path, query] = rest.split('?');
      return {
        label: { tr: tr.key(key) ?? humanize(key.split('.').pop()), en: en.key(key) ?? humanize(key.split('.').pop()) },
        resource: type === 'r' ? path : null,
        page: type === 'p' ? path : null,
        query: query ?? null,
        permission: permission === null ? [] : Array.isArray(permission) ? permission : [permission],
      };
    }),
  }));

  const permissions = Object.entries(PERMISSION_REGISTRY).map(([module, actions]) => ({
    module,
    label: { tr: tr.key(`permissions.modules.${module}`) ?? tr.key(`nav.${module}`) ?? humanize(module), en: en.key(`permissions.modules.${module}`) ?? humanize(module) },
    actions: actions.map((a) => ({ code: `${module}:${a}`, action: a, label: { tr: tr.key(`permissions.actions.${a}`) ?? a, en: en.key(`permissions.actions.${a}`) ?? a } })),
  }));

  const enumLabels = {};
  for (const e of Object.values(enums)) {
    const sections = [e.file, FILE_MODULE[e.file]];
    enumLabels[e.name] = { tr: tr.enumValues(e.values, sections), en: en.enumValues(e.values, sections) };
  }

  return {
    resources,
    navigation,
    permissions,
    roles: ROLES,
    sequences: SEQUENCE_DEFS.map(([code, name, prefix, yearly = true]) => ({ code, name, prefix, yearly })),
    enums: enumLabels,
    common: { tr: tr.dict.common, en: en.dict.common },
  };
}
