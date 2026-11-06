CREATE TABLE audit_logs (
    id uuid NOT NULL,
    company_id uuid,
    user_id uuid,
    user_email text,
    action text NOT NULL,
    entity_type text NOT NULL,
    entity_id text,
    old_values jsonb,
    new_values jsonb,
    ip text,
    user_agent text,
    request_id text,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_audit_logs" PRIMARY KEY (id)
);

CREATE TABLE companies (
    id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    legal_name text,
    tax_number text,
    tax_office text,
    email text,
    phone text,
    address text,
    city text,
    country text NOT NULL,
    base_currency text NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT "PK_companies" PRIMARY KEY (id)
);

CREATE TABLE currencies (
    code text NOT NULL,
    name text NOT NULL,
    symbol text NOT NULL,
    decimals integer NOT NULL,
    is_active boolean NOT NULL,
    CONSTRAINT "PK_currencies" PRIMARY KEY (code)
);

CREATE TABLE permissions (
    id uuid NOT NULL,
    code text NOT NULL,
    module text NOT NULL,
    action text NOT NULL,
    description text,
    CONSTRAINT "PK_permissions" PRIMARY KEY (id)
);

CREATE TABLE users (
    id uuid NOT NULL,
    email text NOT NULL,
    password_hash text NOT NULL,
    first_name text NOT NULL,
    last_name text NOT NULL,
    phone text,
    avatar_url text,
    locale text NOT NULL,
    is_active boolean NOT NULL,
    is_super_admin boolean NOT NULL,
    two_factor_enabled boolean NOT NULL,
    two_factor_secret text,
    failed_login_count integer NOT NULL,
    locked_until timestamp with time zone,
    last_login_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT "PK_users" PRIMARY KEY (id)
);

CREATE TABLE asset_categories (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    useful_life_months integer NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_asset_categories" PRIMARY KEY (id),
    CONSTRAINT "FK_asset_categories_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE branches (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    email text,
    phone text,
    address text,
    city text,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT "PK_branches" PRIMARY KEY (id),
    CONSTRAINT "FK_branches_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE budget_items (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    kind text NOT NULL,
    source text NOT NULL,
    category_ids uuid NOT NULL,
    description text,
    is_active boolean NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_budget_items" PRIMARY KEY (id),
    CONSTRAINT "FK_budget_items_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE business_partners (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    title text NOT NULL,
    type text NOT NULL,
    tax_number text,
    tax_office text,
    email text,
    phone text,
    address text,
    city text,
    contact_name text,
    payment_term_days integer NOT NULL,
    currency_code text NOT NULL,
    e_invoice_alias text,
    credit_limit numeric(18,2),
    notes text,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT "PK_business_partners" PRIMARY KEY (id),
    CONSTRAINT "FK_business_partners_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE competencies (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    name text NOT NULL,
    description text,
    is_active boolean NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_competencies" PRIMARY KEY (id),
    CONSTRAINT "FK_competencies_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE contract_types (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    name text NOT NULL,
    party text NOT NULL,
    flow text NOT NULL,
    default_term_months integer,
    default_notice_days integer NOT NULL,
    description text,
    is_active boolean NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_contract_types" PRIMARY KEY (id),
    CONSTRAINT "FK_contract_types_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE expense_categories (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    kind text NOT NULL,
    rate numeric(18,2),
    limit_amount numeric(18,2),
    requires_receipt boolean NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_expense_categories" PRIMARY KEY (id),
    CONSTRAINT "FK_expense_categories_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE files (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    storage_key text NOT NULL,
    original_name text NOT NULL,
    mime_type text NOT NULL,
    size integer NOT NULL,
    entity_type text,
    entity_id text,
    uploaded_by_id uuid,
    created_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT "PK_files" PRIMARY KEY (id),
    CONSTRAINT "FK_files_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE holidays (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    date date NOT NULL,
    name text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_holidays" PRIMARY KEY (id),
    CONSTRAINT "FK_holidays_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE leave_types (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    default_days integer NOT NULL,
    is_paid boolean NOT NULL,
    requires_balance boolean NOT NULL,
    color text NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_leave_types" PRIMARY KEY (id),
    CONSTRAINT "FK_leave_types_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE ledger_accounts (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    type text NOT NULL,
    parent_id uuid,
    is_system boolean NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_ledger_accounts" PRIMARY KEY (id),
    CONSTRAINT "FK_ledger_accounts_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_ledger_accounts_ledger_accounts_parent_id" FOREIGN KEY (parent_id) REFERENCES ledger_accounts (id) ON DELETE SET NULL
);

CREATE TABLE number_sequences (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    prefix text NOT NULL,
    padding integer NOT NULL,
    include_year boolean NOT NULL,
    reset_yearly boolean NOT NULL,
    current_year integer NOT NULL,
    next_value integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_number_sequences" PRIMARY KEY (id),
    CONSTRAINT "FK_number_sequences_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE payroll_parameters (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    year integer NOT NULL,
    minimum_wage numeric(18,2) NOT NULL,
    sgk_ceiling numeric(18,2) NOT NULL,
    sgk_employee_rate numeric(6,4) NOT NULL,
    unemployment_employee_rate numeric(6,4) NOT NULL,
    sgk_employer_rate numeric(6,4) NOT NULL,
    unemployment_employer_rate numeric(6,4) NOT NULL,
    stamp_tax_rate numeric(8,6) NOT NULL,
    overtime_rate numeric(4,2) NOT NULL,
    monthly_hours integer NOT NULL,
    tax_brackets jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_payroll_parameters" PRIMARY KEY (id),
    CONSTRAINT "FK_payroll_parameters_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE product_categories (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    parent_id uuid,
    code text NOT NULL,
    name text NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_product_categories" PRIMARY KEY (id),
    CONSTRAINT "FK_product_categories_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_product_categories_product_categories_parent_id" FOREIGN KEY (parent_id) REFERENCES product_categories (id) ON DELETE SET NULL
);

CREATE TABLE roles (
    id uuid NOT NULL,
    company_id uuid,
    code text NOT NULL,
    name text NOT NULL,
    description text,
    is_system boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT "PK_roles" PRIMARY KEY (id),
    CONSTRAINT "FK_roles_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE SET NULL
);

CREATE TABLE settings (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    key text NOT NULL,
    value jsonb NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_settings" PRIMARY KEY (id),
    CONSTRAINT "FK_settings_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE shifts (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    start_time text NOT NULL,
    end_time text NOT NULL,
    break_minutes integer NOT NULL,
    color text NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_shifts" PRIMARY KEY (id),
    CONSTRAINT "FK_shifts_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE tax_rates (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    rate numeric(5,2) NOT NULL,
    is_default boolean NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_tax_rates" PRIMARY KEY (id),
    CONSTRAINT "FK_tax_rates_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE ticket_sla_policies (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    priority text NOT NULL,
    first_response_hours integer NOT NULL,
    resolution_hours integer NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_ticket_sla_policies" PRIMARY KEY (id),
    CONSTRAINT "FK_ticket_sla_policies_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE training_courses (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    title text NOT NULL,
    category text NOT NULL,
    format text NOT NULL,
    duration_hours numeric(6,1) NOT NULL,
    provider text,
    description text,
    is_mandatory boolean NOT NULL,
    validity_months integer,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_training_courses" PRIMARY KEY (id),
    CONSTRAINT "FK_training_courses_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE treasury_accounts (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    type text NOT NULL,
    currency_code text NOT NULL,
    bank_name text,
    branch_name text,
    iban text,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_treasury_accounts" PRIMARY KEY (id),
    CONSTRAINT "FK_treasury_accounts_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE units (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_units" PRIMARY KEY (id),
    CONSTRAINT "FK_units_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE workflow_definitions (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    entity_type text NOT NULL,
    description text,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_workflow_definitions" PRIMARY KEY (id),
    CONSTRAINT "FK_workflow_definitions_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE exchange_rates (
    id uuid NOT NULL,
    currency_code text NOT NULL,
    rate_date date NOT NULL,
    buying numeric(18,6) NOT NULL,
    selling numeric(18,6) NOT NULL,
    source text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_exchange_rates" PRIMARY KEY (id),
    CONSTRAINT "FK_exchange_rates_currencies_currency_code" FOREIGN KEY (currency_code) REFERENCES currencies (code) ON DELETE RESTRICT
);

CREATE TABLE journal_entries (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    entry_date date NOT NULL,
    status text NOT NULL,
    source text NOT NULL,
    source_id uuid,
    source_number text,
    description text NOT NULL,
    total numeric(18,2) NOT NULL,
    reverses_id uuid,
    posted_at timestamp with time zone,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_journal_entries" PRIMARY KEY (id),
    CONSTRAINT "FK_journal_entries_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_journal_entries_journal_entries_reverses_id" FOREIGN KEY (reverses_id) REFERENCES journal_entries (id) ON DELETE SET NULL,
    CONSTRAINT "FK_journal_entries_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE notifications (
    id uuid NOT NULL,
    company_id uuid,
    user_id uuid NOT NULL,
    type text NOT NULL,
    title text NOT NULL,
    body text NOT NULL,
    link text,
    data jsonb,
    read_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_notifications" PRIMARY KEY (id),
    CONSTRAINT "FK_notifications_users_user_id" FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE password_reset_tokens (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    token_hash text NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_password_reset_tokens" PRIMARY KEY (id),
    CONSTRAINT "FK_password_reset_tokens_users_user_id" FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE refresh_tokens (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    token_hash text NOT NULL,
    family_id uuid NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    replaced_by_id uuid,
    user_agent text,
    ip text,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_refresh_tokens" PRIMARY KEY (id),
    CONSTRAINT "FK_refresh_tokens_users_user_id" FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE review_cycles (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    name text NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    self_review_due date NOT NULL,
    manager_review_due date NOT NULL,
    goal_weight integer NOT NULL,
    status text NOT NULL,
    launched_at timestamp with time zone,
    closed_at timestamp with time zone,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_review_cycles" PRIMARY KEY (id),
    CONSTRAINT "FK_review_cycles_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_review_cycles_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE ticket_categories (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    name text NOT NULL,
    description text,
    default_assignee_id uuid,
    is_active boolean NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_ticket_categories" PRIMARY KEY (id),
    CONSTRAINT "FK_ticket_categories_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_ticket_categories_users_default_assignee_id" FOREIGN KEY (default_assignee_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE asset_locations (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    branch_id uuid,
    code text NOT NULL,
    name text NOT NULL,
    description text,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_asset_locations" PRIMARY KEY (id),
    CONSTRAINT "FK_asset_locations_branches_branch_id" FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE SET NULL,
    CONSTRAINT "FK_asset_locations_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE user_companies (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    company_id uuid NOT NULL,
    default_branch_id uuid,
    is_default boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_user_companies" PRIMARY KEY (id),
    CONSTRAINT "FK_user_companies_branches_default_branch_id" FOREIGN KEY (default_branch_id) REFERENCES branches (id) ON DELETE SET NULL,
    CONSTRAINT "FK_user_companies_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
    CONSTRAINT "FK_user_companies_users_user_id" FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE warehouses (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    branch_id uuid,
    code text NOT NULL,
    name text NOT NULL,
    address text,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_warehouses" PRIMARY KEY (id),
    CONSTRAINT "FK_warehouses_branches_branch_id" FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE SET NULL,
    CONSTRAINT "FK_warehouses_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE leads (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    company_name text NOT NULL,
    contact_name text,
    email text,
    phone text,
    city text,
    source text NOT NULL,
    status text NOT NULL,
    estimated_value numeric(18,2),
    currency_code text NOT NULL,
    notes text,
    owner_id uuid,
    disqualify_reason text,
    partner_id uuid,
    converted_at timestamp with time zone,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_leads" PRIMARY KEY (id),
    CONSTRAINT "FK_leads_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE SET NULL,
    CONSTRAINT "FK_leads_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_leads_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_leads_users_owner_id" FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE partner_ledger_entries (
    id uuid NOT NULL,
    seq bigint GENERATED BY DEFAULT AS IDENTITY,
    company_id uuid NOT NULL,
    partner_id uuid NOT NULL,
    entry_date date NOT NULL,
    due_date date,
    document_type text NOT NULL,
    document_id uuid,
    document_number text NOT NULL,
    description text,
    debit numeric(18,2) NOT NULL,
    credit numeric(18,2) NOT NULL,
    currency_code text NOT NULL,
    exchange_rate numeric(18,6) NOT NULL,
    debit_base numeric(18,2) NOT NULL,
    credit_base numeric(18,2) NOT NULL,
    reverses_id uuid,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_partner_ledger_entries" PRIMARY KEY (id),
    CONSTRAINT "FK_partner_ledger_entries_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_partner_ledger_entries_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_partner_ledger_entries_partner_ledger_entries_reverses_id" FOREIGN KEY (reverses_id) REFERENCES partner_ledger_entries (id) ON DELETE SET NULL,
    CONSTRAINT "FK_partner_ledger_entries_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE role_permissions (
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL,
    CONSTRAINT "PK_role_permissions" PRIMARY KEY (role_id, permission_id),
    CONSTRAINT "FK_role_permissions_permissions_permission_id" FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE,
    CONSTRAINT "FK_role_permissions_roles_role_id" FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

CREATE TABLE user_roles (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    company_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_user_roles" PRIMARY KEY (id),
    CONSTRAINT "FK_user_roles_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
    CONSTRAINT "FK_user_roles_roles_role_id" FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT "FK_user_roles_users_user_id" FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE training_sessions (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    course_id uuid NOT NULL,
    starts_at timestamp with time zone NOT NULL,
    ends_at timestamp with time zone NOT NULL,
    location text,
    instructor text,
    capacity integer,
    cost numeric(18,2),
    self_enrollment boolean NOT NULL,
    status text NOT NULL,
    notes text,
    completed_at timestamp with time zone,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_training_sessions" PRIMARY KEY (id),
    CONSTRAINT "FK_training_sessions_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_training_sessions_training_courses_course_id" FOREIGN KEY (course_id) REFERENCES training_courses (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_training_sessions_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE partner_payments (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    direction text NOT NULL,
    method text NOT NULL,
    partner_id uuid NOT NULL,
    payment_date date NOT NULL,
    account_id uuid,
    amount numeric(18,2) NOT NULL,
    currency_code text NOT NULL,
    exchange_rate numeric(18,6) NOT NULL,
    amount_base numeric(18,2) NOT NULL,
    description text,
    status text NOT NULL,
    cancelled_at timestamp with time zone,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_partner_payments" PRIMARY KEY (id),
    CONSTRAINT "FK_partner_payments_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_partner_payments_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_partner_payments_treasury_accounts_account_id" FOREIGN KEY (account_id) REFERENCES treasury_accounts (id) ON DELETE SET NULL,
    CONSTRAINT "FK_partner_payments_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE payroll_periods (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    year integer NOT NULL,
    month integer NOT NULL,
    status text NOT NULL,
    imported boolean NOT NULL,
    employee_count integer NOT NULL,
    gross_total numeric(18,2) NOT NULL,
    net_total numeric(18,2) NOT NULL,
    sgk_total numeric(18,2) NOT NULL,
    tax_total numeric(18,2) NOT NULL,
    deduction_total numeric(18,2) NOT NULL,
    employer_cost_total numeric(18,2) NOT NULL,
    approved_at timestamp with time zone,
    approved_by_id uuid,
    payment_account_id uuid,
    payment_date date,
    paid_at timestamp with time zone,
    paid_by_id uuid,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_payroll_periods" PRIMARY KEY (id),
    CONSTRAINT "FK_payroll_periods_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_payroll_periods_treasury_accounts_payment_account_id" FOREIGN KEY (payment_account_id) REFERENCES treasury_accounts (id) ON DELETE SET NULL,
    CONSTRAINT "FK_payroll_periods_users_approved_by_id" FOREIGN KEY (approved_by_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT "FK_payroll_periods_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_payroll_periods_users_paid_by_id" FOREIGN KEY (paid_by_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE treasury_transactions (
    id uuid NOT NULL,
    seq bigint GENERATED BY DEFAULT AS IDENTITY,
    company_id uuid NOT NULL,
    account_id uuid NOT NULL,
    tx_date date NOT NULL,
    document_type text NOT NULL,
    document_id uuid,
    document_number text NOT NULL,
    description text,
    amount_in numeric(18,2) NOT NULL,
    amount_out numeric(18,2) NOT NULL,
    reverses_id uuid,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_treasury_transactions" PRIMARY KEY (id),
    CONSTRAINT "FK_treasury_transactions_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_treasury_transactions_treasury_accounts_account_id" FOREIGN KEY (account_id) REFERENCES treasury_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_treasury_transactions_treasury_transactions_reverses_id" FOREIGN KEY (reverses_id) REFERENCES treasury_transactions (id) ON DELETE SET NULL,
    CONSTRAINT "FK_treasury_transactions_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE products (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    description text,
    category_id uuid,
    unit_id uuid NOT NULL,
    type text NOT NULL,
    tracking_type text NOT NULL,
    purchase_price numeric(18,2),
    sale_price numeric(18,2),
    currency text NOT NULL,
    vat_rate_id uuid,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT "PK_products" PRIMARY KEY (id),
    CONSTRAINT "FK_products_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_products_product_categories_category_id" FOREIGN KEY (category_id) REFERENCES product_categories (id) ON DELETE SET NULL,
    CONSTRAINT "FK_products_tax_rates_vat_rate_id" FOREIGN KEY (vat_rate_id) REFERENCES tax_rates (id) ON DELETE SET NULL,
    CONSTRAINT "FK_products_units_unit_id" FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE RESTRICT
);

CREATE TABLE workflow_instances (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    definition_id uuid NOT NULL,
    entity_type text NOT NULL,
    entity_id text NOT NULL,
    title text NOT NULL,
    status text NOT NULL,
    current_step_order integer NOT NULL,
    started_by_id uuid NOT NULL,
    started_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    CONSTRAINT "PK_workflow_instances" PRIMARY KEY (id),
    CONSTRAINT "FK_workflow_instances_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_workflow_instances_workflow_definitions_definition_id" FOREIGN KEY (definition_id) REFERENCES workflow_definitions (id) ON DELETE RESTRICT
);

CREATE TABLE workflow_steps (
    id uuid NOT NULL,
    definition_id uuid NOT NULL,
    "order" integer NOT NULL,
    name text NOT NULL,
    approver_type text NOT NULL,
    approver_role_id uuid,
    approver_user_id uuid,
    CONSTRAINT "PK_workflow_steps" PRIMARY KEY (id),
    CONSTRAINT "FK_workflow_steps_roles_approver_role_id" FOREIGN KEY (approver_role_id) REFERENCES roles (id) ON DELETE SET NULL,
    CONSTRAINT "FK_workflow_steps_workflow_definitions_definition_id" FOREIGN KEY (definition_id) REFERENCES workflow_definitions (id) ON DELETE CASCADE
);

CREATE TABLE review_cycle_competencies (
    cycle_id uuid NOT NULL,
    competency_id uuid NOT NULL,
    CONSTRAINT "PK_review_cycle_competencies" PRIMARY KEY (cycle_id, competency_id),
    CONSTRAINT "FK_review_cycle_competencies_competencies_competency_id" FOREIGN KEY (competency_id) REFERENCES competencies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_review_cycle_competencies_review_cycles_cycle_id" FOREIGN KEY (cycle_id) REFERENCES review_cycles (id) ON DELETE CASCADE
);

CREATE TABLE tickets (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    subject text NOT NULL,
    description text NOT NULL,
    category_id uuid,
    priority text NOT NULL,
    status text NOT NULL,
    requester_id uuid NOT NULL,
    assignee_id uuid,
    first_response_due_at timestamp with time zone NOT NULL,
    resolution_due_at timestamp with time zone NOT NULL,
    first_responded_at timestamp with time zone,
    resolved_at timestamp with time zone,
    closed_at timestamp with time zone,
    paused_at timestamp with time zone,
    paused_minutes integer NOT NULL,
    satisfaction_rating integer,
    satisfaction_comment text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_tickets" PRIMARY KEY (id),
    CONSTRAINT "FK_tickets_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_tickets_ticket_categories_category_id" FOREIGN KEY (category_id) REFERENCES ticket_categories (id) ON DELETE SET NULL,
    CONSTRAINT "FK_tickets_users_assignee_id" FOREIGN KEY (assignee_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT "FK_tickets_users_requester_id" FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE asset_counts (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    status text NOT NULL,
    count_date date NOT NULL,
    location_id uuid,
    category_id uuid,
    notes text,
    created_by_id uuid NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_asset_counts" PRIMARY KEY (id),
    CONSTRAINT "FK_asset_counts_asset_categories_category_id" FOREIGN KEY (category_id) REFERENCES asset_categories (id) ON DELETE SET NULL,
    CONSTRAINT "FK_asset_counts_asset_locations_location_id" FOREIGN KEY (location_id) REFERENCES asset_locations (id) ON DELETE SET NULL,
    CONSTRAINT "FK_asset_counts_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_asset_counts_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE assets (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    asset_tag text NOT NULL,
    name text NOT NULL,
    category_id uuid NOT NULL,
    brand text,
    model text,
    serial_number text,
    status text NOT NULL,
    condition text NOT NULL,
    location_id uuid,
    purchase_date date,
    purchase_cost numeric(18,2),
    currency text NOT NULL,
    salvage_value numeric(18,2) NOT NULL,
    useful_life_months integer,
    supplier text,
    invoice_no text,
    warranty_end_date date,
    notes text,
    retired_at timestamp with time zone,
    retire_reason text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT "PK_assets" PRIMARY KEY (id),
    CONSTRAINT "FK_assets_asset_categories_category_id" FOREIGN KEY (category_id) REFERENCES asset_categories (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_assets_asset_locations_location_id" FOREIGN KEY (location_id) REFERENCES asset_locations (id) ON DELETE SET NULL,
    CONSTRAINT "FK_assets_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE stock_movements (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    type text NOT NULL,
    movement_date date NOT NULL,
    source_warehouse_id uuid,
    target_warehouse_id uuid,
    reference text,
    notes text,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_stock_movements" PRIMARY KEY (id),
    CONSTRAINT "FK_stock_movements_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_stock_movements_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_stock_movements_warehouses_source_warehouse_id" FOREIGN KEY (source_warehouse_id) REFERENCES warehouses (id) ON DELETE SET NULL,
    CONSTRAINT "FK_stock_movements_warehouses_target_warehouse_id" FOREIGN KEY (target_warehouse_id) REFERENCES warehouses (id) ON DELETE SET NULL
);

CREATE TABLE opportunities (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    title text NOT NULL,
    partner_id uuid NOT NULL,
    lead_id uuid,
    stage text NOT NULL,
    amount numeric(18,2) NOT NULL,
    currency_code text NOT NULL,
    probability integer NOT NULL,
    expected_close_date date,
    owner_id uuid,
    description text,
    lost_reason text,
    closed_at timestamp with time zone,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_opportunities" PRIMARY KEY (id),
    CONSTRAINT "FK_opportunities_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_opportunities_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_opportunities_leads_lead_id" FOREIGN KEY (lead_id) REFERENCES leads (id) ON DELETE SET NULL,
    CONSTRAINT "FK_opportunities_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_opportunities_users_owner_id" FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE cheques (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    kind text NOT NULL,
    direction text NOT NULL,
    status text NOT NULL,
    serial_no text NOT NULL,
    bank_name text,
    branch_name text,
    drawer text,
    issue_date date NOT NULL,
    due_date date NOT NULL,
    amount numeric(18,2) NOT NULL,
    currency_code text NOT NULL,
    exchange_rate numeric(18,6) NOT NULL,
    partner_id uuid NOT NULL,
    endorsed_to_id uuid,
    account_id uuid,
    origin_payment_id uuid NOT NULL,
    endorsement_payment_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_cheques" PRIMARY KEY (id),
    CONSTRAINT "FK_cheques_business_partners_endorsed_to_id" FOREIGN KEY (endorsed_to_id) REFERENCES business_partners (id) ON DELETE SET NULL,
    CONSTRAINT "FK_cheques_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_cheques_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_cheques_partner_payments_endorsement_payment_id" FOREIGN KEY (endorsement_payment_id) REFERENCES partner_payments (id) ON DELETE SET NULL,
    CONSTRAINT "FK_cheques_partner_payments_origin_payment_id" FOREIGN KEY (origin_payment_id) REFERENCES partner_payments (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_cheques_treasury_accounts_account_id" FOREIGN KEY (account_id) REFERENCES treasury_accounts (id) ON DELETE SET NULL
);

CREATE TABLE product_variants (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    product_id uuid NOT NULL,
    sku text NOT NULL,
    name text,
    attributes jsonb,
    barcode text,
    min_stock numeric(18,3) NOT NULL,
    average_cost numeric(18,4) NOT NULL,
    is_default boolean NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_product_variants" PRIMARY KEY (id),
    CONSTRAINT "FK_product_variants_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_product_variants_products_product_id" FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT
);

CREATE TABLE workflow_actions (
    id uuid NOT NULL,
    instance_id uuid NOT NULL,
    step_order integer NOT NULL,
    type text NOT NULL,
    user_id uuid NOT NULL,
    comment text,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_workflow_actions" PRIMARY KEY (id),
    CONSTRAINT "FK_workflow_actions_workflow_instances_instance_id" FOREIGN KEY (instance_id) REFERENCES workflow_instances (id) ON DELETE CASCADE
);

CREATE TABLE ticket_comments (
    id uuid NOT NULL,
    ticket_id uuid NOT NULL,
    author_id uuid NOT NULL,
    body text NOT NULL,
    is_internal boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_ticket_comments" PRIMARY KEY (id),
    CONSTRAINT "FK_ticket_comments_tickets_ticket_id" FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT "FK_ticket_comments_users_author_id" FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE ticket_events (
    id uuid NOT NULL,
    ticket_id uuid NOT NULL,
    actor_id uuid,
    type text NOT NULL,
    from_value text,
    to_value text,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_ticket_events" PRIMARY KEY (id),
    CONSTRAINT "FK_ticket_events_tickets_ticket_id" FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT "FK_ticket_events_users_actor_id" FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE asset_count_lines (
    id uuid NOT NULL,
    count_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    expected boolean NOT NULL,
    expected_holder text,
    found_at timestamp with time zone,
    found_by_id uuid,
    condition text,
    note text,
    CONSTRAINT "PK_asset_count_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_asset_count_lines_asset_counts_count_id" FOREIGN KEY (count_id) REFERENCES asset_counts (id) ON DELETE CASCADE,
    CONSTRAINT "FK_asset_count_lines_assets_asset_id" FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_asset_count_lines_users_found_by_id" FOREIGN KEY (found_by_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE asset_maintenances (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    type text NOT NULL,
    status text NOT NULL,
    title text NOT NULL,
    description text,
    vendor text,
    cost numeric(18,2),
    started_at date NOT NULL,
    completed_at date,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_asset_maintenances" PRIMARY KEY (id),
    CONSTRAINT "FK_asset_maintenances_assets_asset_id" FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_asset_maintenances_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);

CREATE TABLE stock_counts (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    warehouse_id uuid NOT NULL,
    status text NOT NULL,
    count_date date NOT NULL,
    notes text,
    created_by_id uuid NOT NULL,
    completed_at timestamp with time zone,
    adjustment_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_stock_counts" PRIMARY KEY (id),
    CONSTRAINT "FK_stock_counts_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_stock_counts_stock_movements_adjustment_id" FOREIGN KEY (adjustment_id) REFERENCES stock_movements (id) ON DELETE SET NULL,
    CONSTRAINT "FK_stock_counts_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_stock_counts_warehouses_warehouse_id" FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE RESTRICT
);

CREATE TABLE crm_activities (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    type text NOT NULL,
    subject text NOT NULL,
    description text,
    due_at timestamp with time zone,
    completed_at timestamp with time zone,
    owner_id uuid NOT NULL,
    lead_id uuid,
    opportunity_id uuid,
    partner_id uuid,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_crm_activities" PRIMARY KEY (id),
    CONSTRAINT "FK_crm_activities_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE SET NULL,
    CONSTRAINT "FK_crm_activities_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_crm_activities_leads_lead_id" FOREIGN KEY (lead_id) REFERENCES leads (id) ON DELETE CASCADE,
    CONSTRAINT "FK_crm_activities_opportunities_opportunity_id" FOREIGN KEY (opportunity_id) REFERENCES opportunities (id) ON DELETE CASCADE,
    CONSTRAINT "FK_crm_activities_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_crm_activities_users_owner_id" FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE sales_quotes (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    partner_id uuid NOT NULL,
    opportunity_id uuid,
    quote_date date NOT NULL,
    valid_until date,
    currency_code text NOT NULL,
    exchange_rate numeric(18,6) NOT NULL,
    status text NOT NULL,
    notes text,
    subtotal numeric(18,2) NOT NULL,
    discount_total numeric(18,2) NOT NULL,
    vat_total numeric(18,2) NOT NULL,
    grand_total numeric(18,2) NOT NULL,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_sales_quotes" PRIMARY KEY (id),
    CONSTRAINT "FK_sales_quotes_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_sales_quotes_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_sales_quotes_opportunities_opportunity_id" FOREIGN KEY (opportunity_id) REFERENCES opportunities (id) ON DELETE SET NULL,
    CONSTRAINT "FK_sales_quotes_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE cheque_events (
    id uuid NOT NULL,
    cheque_id uuid NOT NULL,
    event_date date NOT NULL,
    from_status text,
    to_status text NOT NULL,
    account_id uuid,
    partner_id uuid,
    note text,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_cheque_events" PRIMARY KEY (id),
    CONSTRAINT "FK_cheque_events_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE SET NULL,
    CONSTRAINT "FK_cheque_events_cheques_cheque_id" FOREIGN KEY (cheque_id) REFERENCES cheques (id) ON DELETE CASCADE,
    CONSTRAINT "FK_cheque_events_treasury_accounts_account_id" FOREIGN KEY (account_id) REFERENCES treasury_accounts (id) ON DELETE SET NULL,
    CONSTRAINT "FK_cheque_events_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE bills_of_materials (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    variant_id uuid NOT NULL,
    quantity numeric(18,3) NOT NULL,
    labor_cost numeric(18,2) NOT NULL,
    overhead_cost numeric(18,2) NOT NULL,
    is_default boolean NOT NULL,
    is_active boolean NOT NULL,
    notes text,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_bills_of_materials" PRIMARY KEY (id),
    CONSTRAINT "FK_bills_of_materials_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_bills_of_materials_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_bills_of_materials_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE serial_numbers (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    variant_id uuid NOT NULL,
    serial_no text NOT NULL,
    status text NOT NULL,
    warehouse_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_serial_numbers" PRIMARY KEY (id),
    CONSTRAINT "FK_serial_numbers_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_serial_numbers_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_serial_numbers_warehouses_warehouse_id" FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE SET NULL
);

CREATE TABLE stock_balances (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    variant_id uuid NOT NULL,
    quantity numeric(18,3) NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_stock_balances" PRIMARY KEY (id),
    CONSTRAINT "FK_stock_balances_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_stock_balances_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_stock_balances_warehouses_warehouse_id" FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE RESTRICT
);

CREATE TABLE stock_lots (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    variant_id uuid NOT NULL,
    lot_no text NOT NULL,
    expiry_date date,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_stock_lots" PRIMARY KEY (id),
    CONSTRAINT "FK_stock_lots_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_stock_lots_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT
);

CREATE TABLE sales_orders (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    partner_id uuid NOT NULL,
    quote_id uuid,
    order_date date NOT NULL,
    delivery_date date,
    warehouse_id uuid NOT NULL,
    customer_ref text,
    currency_code text NOT NULL,
    exchange_rate numeric(18,6) NOT NULL,
    status text NOT NULL,
    notes text,
    subtotal numeric(18,2) NOT NULL,
    discount_total numeric(18,2) NOT NULL,
    vat_total numeric(18,2) NOT NULL,
    grand_total numeric(18,2) NOT NULL,
    created_by_id uuid NOT NULL,
    approved_by_id uuid,
    approved_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_sales_orders" PRIMARY KEY (id),
    CONSTRAINT "FK_sales_orders_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_sales_orders_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_sales_orders_sales_quotes_quote_id" FOREIGN KEY (quote_id) REFERENCES sales_quotes (id) ON DELETE SET NULL,
    CONSTRAINT "FK_sales_orders_users_approved_by_id" FOREIGN KEY (approved_by_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT "FK_sales_orders_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_sales_orders_warehouses_warehouse_id" FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE RESTRICT
);

CREATE TABLE sales_quote_lines (
    id uuid NOT NULL,
    quote_id uuid NOT NULL,
    line_no integer NOT NULL,
    variant_id uuid,
    description text NOT NULL,
    quantity numeric(18,3) NOT NULL,
    unit_price numeric(18,4) NOT NULL,
    discount_rate numeric(5,2) NOT NULL,
    vat_rate numeric(5,2) NOT NULL,
    line_total numeric(18,2) NOT NULL,
    CONSTRAINT "PK_sales_quote_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_sales_quote_lines_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE SET NULL,
    CONSTRAINT "FK_sales_quote_lines_sales_quotes_quote_id" FOREIGN KEY (quote_id) REFERENCES sales_quotes (id) ON DELETE CASCADE
);

CREATE TABLE bom_lines (
    id uuid NOT NULL,
    bom_id uuid NOT NULL,
    line_no integer NOT NULL,
    variant_id uuid NOT NULL,
    quantity numeric(18,4) NOT NULL,
    scrap_rate numeric(5,2) NOT NULL,
    notes text,
    CONSTRAINT "PK_bom_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_bom_lines_bills_of_materials_bom_id" FOREIGN KEY (bom_id) REFERENCES bills_of_materials (id) ON DELETE CASCADE,
    CONSTRAINT "FK_bom_lines_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT
);

CREATE TABLE work_orders (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    bom_id uuid NOT NULL,
    variant_id uuid NOT NULL,
    quantity numeric(18,3) NOT NULL,
    produced_qty numeric(18,3) NOT NULL,
    status text NOT NULL,
    planned_start date NOT NULL,
    planned_end date NOT NULL,
    source_warehouse_id uuid NOT NULL,
    target_warehouse_id uuid NOT NULL,
    labor_per_unit numeric(18,4) NOT NULL,
    overhead_per_unit numeric(18,4) NOT NULL,
    reference text,
    notes text,
    created_by_id uuid NOT NULL,
    released_at timestamp with time zone,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_work_orders" PRIMARY KEY (id),
    CONSTRAINT "FK_work_orders_bills_of_materials_bom_id" FOREIGN KEY (bom_id) REFERENCES bills_of_materials (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_work_orders_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_work_orders_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_work_orders_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_work_orders_warehouses_source_warehouse_id" FOREIGN KEY (source_warehouse_id) REFERENCES warehouses (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_work_orders_warehouses_target_warehouse_id" FOREIGN KEY (target_warehouse_id) REFERENCES warehouses (id) ON DELETE RESTRICT
);

CREATE TABLE lot_balances (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    lot_id uuid NOT NULL,
    quantity numeric(18,3) NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_lot_balances" PRIMARY KEY (id),
    CONSTRAINT "FK_lot_balances_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_lot_balances_stock_lots_lot_id" FOREIGN KEY (lot_id) REFERENCES stock_lots (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_lot_balances_warehouses_warehouse_id" FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE RESTRICT
);

CREATE TABLE stock_count_lines (
    id uuid NOT NULL,
    count_id uuid NOT NULL,
    variant_id uuid NOT NULL,
    lot_id uuid,
    expected_qty numeric(18,3) NOT NULL,
    counted_qty numeric(18,3),
    CONSTRAINT "PK_stock_count_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_stock_count_lines_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_stock_count_lines_stock_counts_count_id" FOREIGN KEY (count_id) REFERENCES stock_counts (id) ON DELETE CASCADE,
    CONSTRAINT "FK_stock_count_lines_stock_lots_lot_id" FOREIGN KEY (lot_id) REFERENCES stock_lots (id) ON DELETE SET NULL
);

CREATE TABLE stock_movement_lines (
    id uuid NOT NULL,
    movement_id uuid NOT NULL,
    variant_id uuid NOT NULL,
    lot_id uuid,
    quantity numeric(18,3) NOT NULL,
    unit_cost numeric(18,4),
    CONSTRAINT "PK_stock_movement_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_stock_movement_lines_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_stock_movement_lines_stock_lots_lot_id" FOREIGN KEY (lot_id) REFERENCES stock_lots (id) ON DELETE SET NULL,
    CONSTRAINT "FK_stock_movement_lines_stock_movements_movement_id" FOREIGN KEY (movement_id) REFERENCES stock_movements (id) ON DELETE CASCADE
);

CREATE TABLE delivery_notes (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    order_id uuid NOT NULL,
    partner_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    delivery_date date NOT NULL,
    shipping_address text,
    carrier text,
    notes text,
    stock_movement_id uuid,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_delivery_notes" PRIMARY KEY (id),
    CONSTRAINT "FK_delivery_notes_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_delivery_notes_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_delivery_notes_sales_orders_order_id" FOREIGN KEY (order_id) REFERENCES sales_orders (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_delivery_notes_stock_movements_stock_movement_id" FOREIGN KEY (stock_movement_id) REFERENCES stock_movements (id) ON DELETE SET NULL,
    CONSTRAINT "FK_delivery_notes_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_delivery_notes_warehouses_warehouse_id" FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE RESTRICT
);

CREATE TABLE sales_invoices (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    partner_id uuid NOT NULL,
    order_id uuid,
    invoice_date date NOT NULL,
    due_date date NOT NULL,
    currency_code text NOT NULL,
    exchange_rate numeric(18,6) NOT NULL,
    status text NOT NULL,
    payment_status text NOT NULL,
    paid_amount numeric(18,2) NOT NULL,
    notes text,
    subtotal numeric(18,2) NOT NULL,
    discount_total numeric(18,2) NOT NULL,
    vat_total numeric(18,2) NOT NULL,
    grand_total numeric(18,2) NOT NULL,
    created_by_id uuid NOT NULL,
    cancelled_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_sales_invoices" PRIMARY KEY (id),
    CONSTRAINT "FK_sales_invoices_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_sales_invoices_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_sales_invoices_sales_orders_order_id" FOREIGN KEY (order_id) REFERENCES sales_orders (id) ON DELETE SET NULL,
    CONSTRAINT "FK_sales_invoices_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE sales_order_lines (
    id uuid NOT NULL,
    order_id uuid NOT NULL,
    line_no integer NOT NULL,
    variant_id uuid NOT NULL,
    description text NOT NULL,
    quantity numeric(18,3) NOT NULL,
    delivered_qty numeric(18,3) NOT NULL,
    invoiced_qty numeric(18,3) NOT NULL,
    unit_price numeric(18,4) NOT NULL,
    discount_rate numeric(5,2) NOT NULL,
    vat_rate numeric(5,2) NOT NULL,
    line_total numeric(18,2) NOT NULL,
    CONSTRAINT "PK_sales_order_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_sales_order_lines_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_sales_order_lines_sales_orders_order_id" FOREIGN KEY (order_id) REFERENCES sales_orders (id) ON DELETE CASCADE
);

CREATE TABLE production_records (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    work_order_id uuid NOT NULL,
    number text NOT NULL,
    record_date date NOT NULL,
    quantity numeric(18,3) NOT NULL,
    material_cost numeric(18,2) NOT NULL,
    labor_cost numeric(18,2) NOT NULL,
    overhead_cost numeric(18,2) NOT NULL,
    unit_cost numeric(18,4) NOT NULL,
    issue_movement_id uuid,
    receipt_movement_id uuid NOT NULL,
    notes text,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_production_records" PRIMARY KEY (id),
    CONSTRAINT "FK_production_records_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_production_records_stock_movements_issue_movement_id" FOREIGN KEY (issue_movement_id) REFERENCES stock_movements (id) ON DELETE SET NULL,
    CONSTRAINT "FK_production_records_stock_movements_receipt_movement_id" FOREIGN KEY (receipt_movement_id) REFERENCES stock_movements (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_production_records_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_production_records_work_orders_work_order_id" FOREIGN KEY (work_order_id) REFERENCES work_orders (id) ON DELETE RESTRICT
);

CREATE TABLE work_order_components (
    id uuid NOT NULL,
    work_order_id uuid NOT NULL,
    line_no integer NOT NULL,
    variant_id uuid NOT NULL,
    quantity_per numeric(18,6) NOT NULL,
    required_qty numeric(18,3) NOT NULL,
    consumed_qty numeric(18,3) NOT NULL,
    CONSTRAINT "PK_work_order_components" PRIMARY KEY (id),
    CONSTRAINT "FK_work_order_components_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_work_order_components_work_orders_work_order_id" FOREIGN KEY (work_order_id) REFERENCES work_orders (id) ON DELETE CASCADE
);

CREATE TABLE stock_movement_serials (
    line_id uuid NOT NULL,
    serial_id uuid NOT NULL,
    CONSTRAINT "PK_stock_movement_serials" PRIMARY KEY (line_id, serial_id),
    CONSTRAINT "FK_stock_movement_serials_serial_numbers_serial_id" FOREIGN KEY (serial_id) REFERENCES serial_numbers (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_stock_movement_serials_stock_movement_lines_line_id" FOREIGN KEY (line_id) REFERENCES stock_movement_lines (id) ON DELETE CASCADE
);

CREATE TABLE stock_transactions (
    id uuid NOT NULL,
    seq bigint GENERATED BY DEFAULT AS IDENTITY,
    company_id uuid NOT NULL,
    movement_id uuid NOT NULL,
    line_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    variant_id uuid NOT NULL,
    lot_id uuid,
    quantity numeric(18,3) NOT NULL,
    unit_cost numeric(18,4) NOT NULL,
    balance_after numeric(18,3) NOT NULL,
    movement_date date NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_stock_transactions" PRIMARY KEY (id),
    CONSTRAINT "FK_stock_transactions_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_stock_transactions_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_stock_transactions_stock_lots_lot_id" FOREIGN KEY (lot_id) REFERENCES stock_lots (id) ON DELETE SET NULL,
    CONSTRAINT "FK_stock_transactions_stock_movement_lines_line_id" FOREIGN KEY (line_id) REFERENCES stock_movement_lines (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_stock_transactions_stock_movements_movement_id" FOREIGN KEY (movement_id) REFERENCES stock_movements (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_stock_transactions_warehouses_warehouse_id" FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE RESTRICT
);

CREATE TABLE sales_returns (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    invoice_id uuid NOT NULL,
    partner_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    return_date date NOT NULL,
    reason text NOT NULL,
    currency_code text NOT NULL,
    exchange_rate numeric(18,6) NOT NULL,
    subtotal numeric(18,2) NOT NULL,
    discount_total numeric(18,2) NOT NULL,
    vat_total numeric(18,2) NOT NULL,
    grand_total numeric(18,2) NOT NULL,
    stock_movement_id uuid,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_sales_returns" PRIMARY KEY (id),
    CONSTRAINT "FK_sales_returns_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_sales_returns_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_sales_returns_sales_invoices_invoice_id" FOREIGN KEY (invoice_id) REFERENCES sales_invoices (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_sales_returns_stock_movements_stock_movement_id" FOREIGN KEY (stock_movement_id) REFERENCES stock_movements (id) ON DELETE SET NULL,
    CONSTRAINT "FK_sales_returns_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_sales_returns_warehouses_warehouse_id" FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE RESTRICT
);

CREATE TABLE delivery_note_lines (
    id uuid NOT NULL,
    delivery_id uuid NOT NULL,
    order_line_id uuid NOT NULL,
    variant_id uuid NOT NULL,
    quantity numeric(18,3) NOT NULL,
    lot_no text,
    serial_nos text NOT NULL,
    CONSTRAINT "PK_delivery_note_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_delivery_note_lines_delivery_notes_delivery_id" FOREIGN KEY (delivery_id) REFERENCES delivery_notes (id) ON DELETE CASCADE,
    CONSTRAINT "FK_delivery_note_lines_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_delivery_note_lines_sales_order_lines_order_line_id" FOREIGN KEY (order_line_id) REFERENCES sales_order_lines (id) ON DELETE RESTRICT
);

CREATE TABLE sales_invoice_lines (
    id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    line_no integer NOT NULL,
    order_line_id uuid,
    variant_id uuid,
    description text NOT NULL,
    quantity numeric(18,3) NOT NULL,
    returned_qty numeric(18,3) NOT NULL,
    unit_price numeric(18,4) NOT NULL,
    discount_rate numeric(5,2) NOT NULL,
    vat_rate numeric(5,2) NOT NULL,
    line_total numeric(18,2) NOT NULL,
    CONSTRAINT "PK_sales_invoice_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_sales_invoice_lines_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE SET NULL,
    CONSTRAINT "FK_sales_invoice_lines_sales_invoices_invoice_id" FOREIGN KEY (invoice_id) REFERENCES sales_invoices (id) ON DELETE CASCADE,
    CONSTRAINT "FK_sales_invoice_lines_sales_order_lines_order_line_id" FOREIGN KEY (order_line_id) REFERENCES sales_order_lines (id) ON DELETE SET NULL
);

CREATE TABLE stock_reservations (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    variant_id uuid NOT NULL,
    order_line_id uuid NOT NULL,
    quantity numeric(18,3) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_stock_reservations" PRIMARY KEY (id),
    CONSTRAINT "FK_stock_reservations_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_stock_reservations_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_stock_reservations_sales_order_lines_order_line_id" FOREIGN KEY (order_line_id) REFERENCES sales_order_lines (id) ON DELETE CASCADE,
    CONSTRAINT "FK_stock_reservations_warehouses_warehouse_id" FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE RESTRICT
);

CREATE TABLE sales_return_lines (
    id uuid NOT NULL,
    return_id uuid NOT NULL,
    line_no integer NOT NULL,
    invoice_line_id uuid NOT NULL,
    variant_id uuid,
    description text NOT NULL,
    quantity numeric(18,3) NOT NULL,
    unit_price numeric(18,4) NOT NULL,
    discount_rate numeric(5,2) NOT NULL,
    vat_rate numeric(5,2) NOT NULL,
    line_total numeric(18,2) NOT NULL,
    lot_no text,
    serial_nos text NOT NULL,
    CONSTRAINT "PK_sales_return_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_sales_return_lines_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE SET NULL,
    CONSTRAINT "FK_sales_return_lines_sales_invoice_lines_invoice_line_id" FOREIGN KEY (invoice_line_id) REFERENCES sales_invoice_lines (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_sales_return_lines_sales_returns_return_id" FOREIGN KEY (return_id) REFERENCES sales_returns (id) ON DELETE CASCADE
);

CREATE TABLE application_activities (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    type text NOT NULL,
    from_stage text,
    to_stage text,
    body text,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_application_activities" PRIMARY KEY (id),
    CONSTRAINT "FK_application_activities_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE asset_assignments (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    asset_id uuid NOT NULL,
    assignee_type text NOT NULL,
    employee_id uuid,
    department_id uuid,
    location_id uuid,
    status text NOT NULL,
    assigned_at date NOT NULL,
    expected_return_date date,
    condition_out text NOT NULL,
    notes text,
    assigned_by_id uuid NOT NULL,
    acknowledged_at timestamp with time zone,
    returned_at date,
    condition_in text,
    return_notes text,
    returned_by_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_asset_assignments" PRIMARY KEY (id),
    CONSTRAINT "FK_asset_assignments_asset_locations_location_id" FOREIGN KEY (location_id) REFERENCES asset_locations (id) ON DELETE SET NULL,
    CONSTRAINT "FK_asset_assignments_assets_asset_id" FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_asset_assignments_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_asset_assignments_users_assigned_by_id" FOREIGN KEY (assigned_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_asset_assignments_users_returned_by_id" FOREIGN KEY (returned_by_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE attendance_records (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    date date NOT NULL,
    check_in timestamp with time zone,
    check_out timestamp with time zone,
    source text NOT NULL,
    note text,
    updated_by_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_attendance_records" PRIMARY KEY (id),
    CONSTRAINT "FK_attendance_records_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_attendance_records_users_updated_by_id" FOREIGN KEY (updated_by_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE budget_alerts (
    id uuid NOT NULL,
    budget_id uuid NOT NULL,
    item_id uuid NOT NULL,
    level text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_budget_alerts" PRIMARY KEY (id),
    CONSTRAINT "FK_budget_alerts_budget_items_item_id" FOREIGN KEY (item_id) REFERENCES budget_items (id) ON DELETE RESTRICT
);

CREATE TABLE budget_entries (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    item_id uuid NOT NULL,
    department_id uuid,
    entry_date date NOT NULL,
    amount numeric(18,2) NOT NULL,
    description text NOT NULL,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_budget_entries" PRIMARY KEY (id),
    CONSTRAINT "FK_budget_entries_budget_items_item_id" FOREIGN KEY (item_id) REFERENCES budget_items (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_budget_entries_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_budget_entries_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE budget_events (
    id uuid NOT NULL,
    budget_id uuid NOT NULL,
    actor_id uuid,
    type text NOT NULL,
    note text,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_budget_events" PRIMARY KEY (id),
    CONSTRAINT "FK_budget_events_users_actor_id" FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE budget_lines (
    id uuid NOT NULL,
    budget_id uuid NOT NULL,
    item_id uuid NOT NULL,
    month integer NOT NULL,
    amount numeric(18,2) NOT NULL,
    CONSTRAINT "PK_budget_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_budget_lines_budget_items_item_id" FOREIGN KEY (item_id) REFERENCES budget_items (id) ON DELETE RESTRICT
);

CREATE TABLE budgets (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    year integer NOT NULL,
    department_id uuid,
    name text NOT NULL,
    status text NOT NULL,
    owner_id uuid,
    note text,
    revision integer NOT NULL,
    approved_at timestamp with time zone,
    approved_by_id uuid,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_budgets" PRIMARY KEY (id),
    CONSTRAINT "FK_budgets_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_budgets_users_approved_by_id" FOREIGN KEY (approved_by_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT "FK_budgets_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_budgets_users_owner_id" FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE candidates (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    first_name text NOT NULL,
    last_name text NOT NULL,
    email text NOT NULL,
    phone text,
    city text,
    source text NOT NULL,
    current_title text,
    current_company text,
    linkedin_url text,
    expected_salary numeric(18,2),
    notes text,
    cv_file_id uuid,
    referred_by_id uuid,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_candidates" PRIMARY KEY (id),
    CONSTRAINT "FK_candidates_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_candidates_files_cv_file_id" FOREIGN KEY (cv_file_id) REFERENCES files (id) ON DELETE SET NULL,
    CONSTRAINT "FK_candidates_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE competency_ratings (
    id uuid NOT NULL,
    review_id uuid NOT NULL,
    competency_id uuid NOT NULL,
    self_rating integer,
    manager_rating integer,
    comment text,
    CONSTRAINT "PK_competency_ratings" PRIMARY KEY (id),
    CONSTRAINT "FK_competency_ratings_competencies_competency_id" FOREIGN KEY (competency_id) REFERENCES competencies (id) ON DELETE RESTRICT
);

CREATE TABLE contract_events (
    id uuid NOT NULL,
    contract_id uuid NOT NULL,
    actor_id uuid,
    type text NOT NULL,
    from_value text,
    to_value text,
    note text,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_contract_events" PRIMARY KEY (id),
    CONSTRAINT "FK_contract_events_users_actor_id" FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE contracts (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    title text NOT NULL,
    type_id uuid NOT NULL,
    party text NOT NULL,
    partner_id uuid,
    employee_id uuid,
    owner_id uuid NOT NULL,
    start_date date NOT NULL,
    end_date date,
    signed_date date,
    amount numeric(18,2),
    currency_code text NOT NULL,
    billing_cycle text NOT NULL,
    auto_renew boolean NOT NULL,
    renewal_months integer,
    notice_days integer NOT NULL,
    reminder_days integer NOT NULL,
    status text NOT NULL,
    renewal_count integer NOT NULL,
    description text,
    terminated_on date,
    termination_reason text,
    reminded_for date,
    activated_at timestamp with time zone,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_contracts" PRIMARY KEY (id),
    CONSTRAINT "FK_contracts_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE SET NULL,
    CONSTRAINT "FK_contracts_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_contracts_contract_types_type_id" FOREIGN KEY (type_id) REFERENCES contract_types (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_contracts_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_contracts_users_owner_id" FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE departments (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    parent_id uuid,
    manager_id uuid,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT "PK_departments" PRIMARY KEY (id),
    CONSTRAINT "FK_departments_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_departments_departments_parent_id" FOREIGN KEY (parent_id) REFERENCES departments (id) ON DELETE SET NULL
);

CREATE TABLE positions (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    department_id uuid,
    code text NOT NULL,
    name text NOT NULL,
    description text,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT "PK_positions" PRIMARY KEY (id),
    CONSTRAINT "FK_positions_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_positions_departments_department_id" FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL
);

CREATE TABLE purchase_requests (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    title text NOT NULL,
    request_date date NOT NULL,
    needed_by date,
    department_id uuid,
    notes text,
    status text NOT NULL,
    workflow_instance_id uuid,
    decided_at timestamp with time zone,
    requested_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_purchase_requests" PRIMARY KEY (id),
    CONSTRAINT "FK_purchase_requests_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_purchase_requests_departments_department_id" FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL,
    CONSTRAINT "FK_purchase_requests_users_requested_by_id" FOREIGN KEY (requested_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE vehicles (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    plate text NOT NULL,
    brand text NOT NULL,
    model text NOT NULL,
    model_year integer,
    color text,
    vin text,
    fuel_type text NOT NULL,
    ownership text NOT NULL,
    contract_ends_on date,
    department_id uuid,
    status text NOT NULL,
    odometer integer NOT NULL,
    service_interval_km integer,
    service_interval_months integer,
    next_service_km integer,
    next_service_date date,
    service_reminded_for text,
    notes text,
    retired_on date,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_vehicles" PRIMARY KEY (id),
    CONSTRAINT "FK_vehicles_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_vehicles_departments_department_id" FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL,
    CONSTRAINT "FK_vehicles_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE employees (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    branch_id uuid,
    user_id uuid,
    employee_no text NOT NULL,
    first_name text NOT NULL,
    last_name text NOT NULL,
    national_id text,
    email text,
    phone text,
    birth_date date,
    gender text,
    address text,
    emergency_contact_name text,
    emergency_contact_phone text,
    hire_date date NOT NULL,
    termination_date date,
    termination_reason text,
    status text NOT NULL,
    employment_type text NOT NULL,
    department_id uuid,
    position_id uuid,
    manager_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT "PK_employees" PRIMARY KEY (id),
    CONSTRAINT "FK_employees_branches_branch_id" FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE SET NULL,
    CONSTRAINT "FK_employees_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_employees_departments_department_id" FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL,
    CONSTRAINT "FK_employees_employees_manager_id" FOREIGN KEY (manager_id) REFERENCES employees (id) ON DELETE SET NULL,
    CONSTRAINT "FK_employees_positions_position_id" FOREIGN KEY (position_id) REFERENCES positions (id) ON DELETE SET NULL,
    CONSTRAINT "FK_employees_users_user_id" FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE purchase_request_lines (
    id uuid NOT NULL,
    request_id uuid NOT NULL,
    line_no integer NOT NULL,
    variant_id uuid,
    description text NOT NULL,
    quantity numeric(18,3) NOT NULL,
    estimated_price numeric(18,4),
    CONSTRAINT "PK_purchase_request_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_purchase_request_lines_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE SET NULL,
    CONSTRAINT "FK_purchase_request_lines_purchase_requests_request_id" FOREIGN KEY (request_id) REFERENCES purchase_requests (id) ON DELETE CASCADE
);

CREATE TABLE supplier_quotes (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    request_id uuid,
    partner_id uuid NOT NULL,
    quote_date date NOT NULL,
    valid_until date,
    delivery_days integer,
    currency_code text NOT NULL,
    exchange_rate numeric(18,6) NOT NULL,
    status text NOT NULL,
    notes text,
    subtotal numeric(18,2) NOT NULL,
    discount_total numeric(18,2) NOT NULL,
    vat_total numeric(18,2) NOT NULL,
    grand_total numeric(18,2) NOT NULL,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_supplier_quotes" PRIMARY KEY (id),
    CONSTRAINT "FK_supplier_quotes_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_supplier_quotes_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_supplier_quotes_purchase_requests_request_id" FOREIGN KEY (request_id) REFERENCES purchase_requests (id) ON DELETE SET NULL,
    CONSTRAINT "FK_supplier_quotes_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE vehicle_documents (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    vehicle_id uuid NOT NULL,
    type text NOT NULL,
    start_date date,
    end_date date NOT NULL,
    provider text,
    policy_number text,
    cost numeric(18,2) NOT NULL,
    note text,
    reminded_state text,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_vehicle_documents" PRIMARY KEY (id),
    CONSTRAINT "FK_vehicle_documents_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_vehicle_documents_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_vehicle_documents_vehicles_vehicle_id" FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE
);

CREATE TABLE vehicle_services (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    vehicle_id uuid NOT NULL,
    type text NOT NULL,
    service_date date NOT NULL,
    odometer integer,
    partner_id uuid,
    description text NOT NULL,
    cost numeric(18,2) NOT NULL,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_vehicle_services" PRIMARY KEY (id),
    CONSTRAINT "FK_vehicle_services_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE SET NULL,
    CONSTRAINT "FK_vehicle_services_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_vehicle_services_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_vehicle_services_vehicles_vehicle_id" FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE
);

CREATE TABLE job_postings (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    title text NOT NULL,
    department_id uuid,
    position_id uuid,
    branch_id uuid,
    employment_type text NOT NULL,
    location text,
    description text,
    requirements text,
    openings integer NOT NULL,
    status text NOT NULL,
    hiring_manager_id uuid,
    salary_min numeric(18,2),
    salary_max numeric(18,2),
    published_at timestamp with time zone,
    closes_at date,
    closed_at timestamp with time zone,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_job_postings" PRIMARY KEY (id),
    CONSTRAINT "FK_job_postings_branches_branch_id" FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE SET NULL,
    CONSTRAINT "FK_job_postings_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_job_postings_departments_department_id" FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL,
    CONSTRAINT "FK_job_postings_employees_hiring_manager_id" FOREIGN KEY (hiring_manager_id) REFERENCES employees (id) ON DELETE SET NULL,
    CONSTRAINT "FK_job_postings_positions_position_id" FOREIGN KEY (position_id) REFERENCES positions (id) ON DELETE SET NULL,
    CONSTRAINT "FK_job_postings_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE journal_lines (
    id uuid NOT NULL,
    entry_id uuid NOT NULL,
    line_no integer NOT NULL,
    account_id uuid NOT NULL,
    partner_id uuid,
    employee_id uuid,
    description text,
    debit numeric(18,2) NOT NULL,
    credit numeric(18,2) NOT NULL,
    CONSTRAINT "PK_journal_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_journal_lines_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE SET NULL,
    CONSTRAINT "FK_journal_lines_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE SET NULL,
    CONSTRAINT "FK_journal_lines_journal_entries_entry_id" FOREIGN KEY (entry_id) REFERENCES journal_entries (id) ON DELETE CASCADE,
    CONSTRAINT "FK_journal_lines_ledger_accounts_account_id" FOREIGN KEY (account_id) REFERENCES ledger_accounts (id) ON DELETE RESTRICT
);

CREATE TABLE leave_balances (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    leave_type_id uuid NOT NULL,
    year integer NOT NULL,
    entitled_days integer NOT NULL,
    used_days integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_leave_balances" PRIMARY KEY (id),
    CONSTRAINT "FK_leave_balances_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_leave_balances_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT "FK_leave_balances_leave_types_leave_type_id" FOREIGN KEY (leave_type_id) REFERENCES leave_types (id) ON DELETE RESTRICT
);

CREATE TABLE leave_requests (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    employee_id uuid NOT NULL,
    leave_type_id uuid NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    days integer NOT NULL,
    reason text,
    status text NOT NULL,
    workflow_instance_id uuid,
    created_by_id uuid NOT NULL,
    decided_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_leave_requests" PRIMARY KEY (id),
    CONSTRAINT "FK_leave_requests_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_leave_requests_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_leave_requests_leave_types_leave_type_id" FOREIGN KEY (leave_type_id) REFERENCES leave_types (id) ON DELETE RESTRICT
);

CREATE TABLE payslips (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    period_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    employee_no text NOT NULL,
    employee_name text NOT NULL,
    department_name text,
    position_name text,
    monthly_salary numeric(18,2) NOT NULL,
    employed_days integer NOT NULL,
    missing_days integer NOT NULL,
    pay_days integer NOT NULL,
    overtime_hours numeric(6,2) NOT NULL,
    base_pay numeric(18,2) NOT NULL,
    overtime_pay numeric(18,2) NOT NULL,
    extra_earnings numeric(18,2) NOT NULL,
    gross_pay numeric(18,2) NOT NULL,
    sgk_base numeric(18,2) NOT NULL,
    sgk_employee numeric(18,2) NOT NULL,
    unemployment_employee numeric(18,2) NOT NULL,
    income_tax_base numeric(18,2) NOT NULL,
    cumulative_tax_base numeric(18,2) NOT NULL,
    income_tax numeric(18,2) NOT NULL,
    income_tax_exemption numeric(18,2) NOT NULL,
    stamp_tax numeric(18,2) NOT NULL,
    stamp_tax_exemption numeric(18,2) NOT NULL,
    other_deductions numeric(18,2) NOT NULL,
    net_pay numeric(18,2) NOT NULL,
    sgk_employer numeric(18,2) NOT NULL,
    unemployment_employer numeric(18,2) NOT NULL,
    employer_cost numeric(18,2) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_payslips" PRIMARY KEY (id),
    CONSTRAINT "FK_payslips_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_payslips_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_payslips_payroll_periods_period_id" FOREIGN KEY (period_id) REFERENCES payroll_periods (id) ON DELETE CASCADE
);

CREATE TABLE performance_reviews (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    cycle_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    reviewer_id uuid,
    status text NOT NULL,
    self_comment text,
    manager_comment text,
    strengths text,
    improvements text,
    goal_score numeric(4,2),
    competency_score numeric(4,2),
    overall_score numeric(4,2),
    self_submitted_at timestamp with time zone,
    manager_submitted_at timestamp with time zone,
    acknowledged_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_performance_reviews" PRIMARY KEY (id),
    CONSTRAINT "FK_performance_reviews_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_performance_reviews_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_performance_reviews_employees_reviewer_id" FOREIGN KEY (reviewer_id) REFERENCES employees (id) ON DELETE SET NULL,
    CONSTRAINT "FK_performance_reviews_review_cycles_cycle_id" FOREIGN KEY (cycle_id) REFERENCES review_cycles (id) ON DELETE CASCADE
);

CREATE TABLE projects (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    name text NOT NULL,
    description text,
    partner_id uuid,
    manager_id uuid NOT NULL,
    status text NOT NULL,
    start_date date,
    due_date date,
    completed_at timestamp with time zone,
    budget_amount numeric(18,2),
    expense_cost numeric(18,2) NOT NULL,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_projects" PRIMARY KEY (id),
    CONSTRAINT "FK_projects_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE SET NULL,
    CONSTRAINT "FK_projects_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_projects_employees_manager_id" FOREIGN KEY (manager_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_projects_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE salary_records (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    effective_from date NOT NULL,
    gross_salary numeric(18,2) NOT NULL,
    note text,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_salary_records" PRIMARY KEY (id),
    CONSTRAINT "FK_salary_records_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_salary_records_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT "FK_salary_records_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE shift_assignments (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    date date NOT NULL,
    shift_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_shift_assignments" PRIMARY KEY (id),
    CONSTRAINT "FK_shift_assignments_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_shift_assignments_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT "FK_shift_assignments_shifts_shift_id" FOREIGN KEY (shift_id) REFERENCES shifts (id) ON DELETE SET NULL
);

CREATE TABLE training_enrollments (
    id uuid NOT NULL,
    session_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    status text NOT NULL,
    score integer,
    certificate_expires_at date,
    feedback_rating integer,
    feedback_comment text,
    feedback_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_training_enrollments" PRIMARY KEY (id),
    CONSTRAINT "FK_training_enrollments_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_training_enrollments_training_sessions_session_id" FOREIGN KEY (session_id) REFERENCES training_sessions (id) ON DELETE CASCADE
);

CREATE TABLE vehicle_assignments (
    id uuid NOT NULL,
    vehicle_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    start_date date NOT NULL,
    end_date date,
    start_odometer integer NOT NULL,
    end_odometer integer,
    note text,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_vehicle_assignments" PRIMARY KEY (id),
    CONSTRAINT "FK_vehicle_assignments_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_vehicle_assignments_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_vehicle_assignments_vehicles_vehicle_id" FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE
);

CREATE TABLE vehicle_fuel_entries (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    vehicle_id uuid NOT NULL,
    entry_date date NOT NULL,
    odometer integer NOT NULL,
    liters numeric(10,2) NOT NULL,
    amount numeric(18,2) NOT NULL,
    full_tank boolean NOT NULL,
    station text,
    employee_id uuid,
    distance integer,
    consumption numeric(6,2),
    note text,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_vehicle_fuel_entries" PRIMARY KEY (id),
    CONSTRAINT "FK_vehicle_fuel_entries_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_vehicle_fuel_entries_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE SET NULL,
    CONSTRAINT "FK_vehicle_fuel_entries_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_vehicle_fuel_entries_vehicles_vehicle_id" FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE
);

CREATE TABLE purchase_orders (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    partner_id uuid NOT NULL,
    request_id uuid,
    quote_id uuid,
    order_date date NOT NULL,
    expected_date date,
    warehouse_id uuid NOT NULL,
    currency_code text NOT NULL,
    exchange_rate numeric(18,6) NOT NULL,
    status text NOT NULL,
    notes text,
    subtotal numeric(18,2) NOT NULL,
    discount_total numeric(18,2) NOT NULL,
    vat_total numeric(18,2) NOT NULL,
    grand_total numeric(18,2) NOT NULL,
    created_by_id uuid NOT NULL,
    approved_by_id uuid,
    approved_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_purchase_orders" PRIMARY KEY (id),
    CONSTRAINT "FK_purchase_orders_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_purchase_orders_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_purchase_orders_purchase_requests_request_id" FOREIGN KEY (request_id) REFERENCES purchase_requests (id) ON DELETE SET NULL,
    CONSTRAINT "FK_purchase_orders_supplier_quotes_quote_id" FOREIGN KEY (quote_id) REFERENCES supplier_quotes (id) ON DELETE SET NULL,
    CONSTRAINT "FK_purchase_orders_users_approved_by_id" FOREIGN KEY (approved_by_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT "FK_purchase_orders_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_purchase_orders_warehouses_warehouse_id" FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE RESTRICT
);

CREATE TABLE supplier_quote_lines (
    id uuid NOT NULL,
    quote_id uuid NOT NULL,
    line_no integer NOT NULL,
    request_line_id uuid,
    variant_id uuid,
    description text NOT NULL,
    quantity numeric(18,3) NOT NULL,
    unit_price numeric(18,4) NOT NULL,
    discount_rate numeric(5,2) NOT NULL,
    vat_rate numeric(5,2) NOT NULL,
    line_total numeric(18,2) NOT NULL,
    CONSTRAINT "PK_supplier_quote_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_supplier_quote_lines_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE SET NULL,
    CONSTRAINT "FK_supplier_quote_lines_purchase_request_lines_request_line_id" FOREIGN KEY (request_line_id) REFERENCES purchase_request_lines (id) ON DELETE SET NULL,
    CONSTRAINT "FK_supplier_quote_lines_supplier_quotes_quote_id" FOREIGN KEY (quote_id) REFERENCES supplier_quotes (id) ON DELETE CASCADE
);

CREATE TABLE job_applications (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    posting_id uuid NOT NULL,
    candidate_id uuid NOT NULL,
    stage text NOT NULL,
    rating integer,
    sort_order integer NOT NULL,
    applied_at timestamp with time zone NOT NULL,
    stage_changed_at timestamp with time zone NOT NULL,
    reject_reason text,
    hired_at timestamp with time zone,
    employee_id uuid,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_job_applications" PRIMARY KEY (id),
    CONSTRAINT "FK_job_applications_candidates_candidate_id" FOREIGN KEY (candidate_id) REFERENCES candidates (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_job_applications_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_job_applications_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE SET NULL,
    CONSTRAINT "FK_job_applications_job_postings_posting_id" FOREIGN KEY (posting_id) REFERENCES job_postings (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_job_applications_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE payslip_items (
    id uuid NOT NULL,
    payslip_id uuid NOT NULL,
    kind text NOT NULL,
    description text NOT NULL,
    amount numeric(18,2) NOT NULL,
    CONSTRAINT "PK_payslip_items" PRIMARY KEY (id),
    CONSTRAINT "FK_payslip_items_payslips_payslip_id" FOREIGN KEY (payslip_id) REFERENCES payslips (id) ON DELETE CASCADE
);

CREATE TABLE performance_goals (
    id uuid NOT NULL,
    review_id uuid NOT NULL,
    title text NOT NULL,
    description text,
    weight integer NOT NULL,
    progress integer NOT NULL,
    self_rating integer,
    manager_rating integer,
    sort_order integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_performance_goals" PRIMARY KEY (id),
    CONSTRAINT "FK_performance_goals_performance_reviews_review_id" FOREIGN KEY (review_id) REFERENCES performance_reviews (id) ON DELETE CASCADE
);

CREATE TABLE expense_reports (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    employee_id uuid NOT NULL,
    project_id uuid,
    title text NOT NULL,
    description text,
    status text NOT NULL,
    total_amount numeric(18,2) NOT NULL,
    workflow_instance_id uuid,
    submitted_at timestamp with time zone,
    decided_at timestamp with time zone,
    payment_account_id uuid,
    payment_date date,
    paid_at timestamp with time zone,
    paid_by_id uuid,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_expense_reports" PRIMARY KEY (id),
    CONSTRAINT "FK_expense_reports_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_expense_reports_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_expense_reports_projects_project_id" FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE SET NULL,
    CONSTRAINT "FK_expense_reports_treasury_accounts_payment_account_id" FOREIGN KEY (payment_account_id) REFERENCES treasury_accounts (id) ON DELETE SET NULL,
    CONSTRAINT "FK_expense_reports_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_expense_reports_users_paid_by_id" FOREIGN KEY (paid_by_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE project_members (
    id uuid NOT NULL,
    project_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    role text NOT NULL,
    hourly_cost numeric(18,2),
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_project_members" PRIMARY KEY (id),
    CONSTRAINT "FK_project_members_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_project_members_projects_project_id" FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE TABLE project_tasks (
    id uuid NOT NULL,
    project_id uuid NOT NULL,
    task_no integer NOT NULL,
    title text NOT NULL,
    description text,
    status text NOT NULL,
    priority text NOT NULL,
    assignee_id uuid,
    due_date date,
    estimated_hours numeric(8,2),
    sort_order integer NOT NULL,
    completed_at timestamp with time zone,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_project_tasks" PRIMARY KEY (id),
    CONSTRAINT "FK_project_tasks_employees_assignee_id" FOREIGN KEY (assignee_id) REFERENCES employees (id) ON DELETE SET NULL,
    CONSTRAINT "FK_project_tasks_projects_project_id" FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT "FK_project_tasks_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE vehicle_odometer_readings (
    id uuid NOT NULL,
    vehicle_id uuid NOT NULL,
    reading_date date NOT NULL,
    odometer integer NOT NULL,
    source text NOT NULL,
    fuel_entry_id uuid,
    service_id uuid,
    note text,
    created_by_id uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_vehicle_odometer_readings" PRIMARY KEY (id),
    CONSTRAINT "FK_vehicle_odometer_readings_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT "FK_vehicle_odometer_readings_vehicle_fuel_entries_fuel_entry_id" FOREIGN KEY (fuel_entry_id) REFERENCES vehicle_fuel_entries (id) ON DELETE CASCADE,
    CONSTRAINT "FK_vehicle_odometer_readings_vehicle_services_service_id" FOREIGN KEY (service_id) REFERENCES vehicle_services (id) ON DELETE CASCADE,
    CONSTRAINT "FK_vehicle_odometer_readings_vehicles_vehicle_id" FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE
);

CREATE TABLE goods_receipts (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    order_id uuid NOT NULL,
    partner_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    receipt_date date NOT NULL,
    delivery_note_no text,
    notes text,
    stock_movement_id uuid,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_goods_receipts" PRIMARY KEY (id),
    CONSTRAINT "FK_goods_receipts_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_goods_receipts_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_goods_receipts_purchase_orders_order_id" FOREIGN KEY (order_id) REFERENCES purchase_orders (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_goods_receipts_stock_movements_stock_movement_id" FOREIGN KEY (stock_movement_id) REFERENCES stock_movements (id) ON DELETE SET NULL,
    CONSTRAINT "FK_goods_receipts_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_goods_receipts_warehouses_warehouse_id" FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE RESTRICT
);

CREATE TABLE purchase_invoices (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    number text NOT NULL,
    supplier_invoice_no text NOT NULL,
    partner_id uuid NOT NULL,
    order_id uuid,
    invoice_date date NOT NULL,
    due_date date NOT NULL,
    currency_code text NOT NULL,
    exchange_rate numeric(18,6) NOT NULL,
    status text NOT NULL,
    payment_status text NOT NULL,
    paid_amount numeric(18,2) NOT NULL,
    notes text,
    subtotal numeric(18,2) NOT NULL,
    discount_total numeric(18,2) NOT NULL,
    vat_total numeric(18,2) NOT NULL,
    grand_total numeric(18,2) NOT NULL,
    created_by_id uuid NOT NULL,
    cancelled_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_purchase_invoices" PRIMARY KEY (id),
    CONSTRAINT "FK_purchase_invoices_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_purchase_invoices_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_purchase_invoices_purchase_orders_order_id" FOREIGN KEY (order_id) REFERENCES purchase_orders (id) ON DELETE SET NULL,
    CONSTRAINT "FK_purchase_invoices_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE purchase_order_lines (
    id uuid NOT NULL,
    order_id uuid NOT NULL,
    line_no integer NOT NULL,
    request_line_id uuid,
    variant_id uuid NOT NULL,
    description text NOT NULL,
    quantity numeric(18,3) NOT NULL,
    received_qty numeric(18,3) NOT NULL,
    invoiced_qty numeric(18,3) NOT NULL,
    unit_price numeric(18,4) NOT NULL,
    discount_rate numeric(5,2) NOT NULL,
    vat_rate numeric(5,2) NOT NULL,
    line_total numeric(18,2) NOT NULL,
    CONSTRAINT "PK_purchase_order_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_purchase_order_lines_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_purchase_order_lines_purchase_orders_order_id" FOREIGN KEY (order_id) REFERENCES purchase_orders (id) ON DELETE CASCADE,
    CONSTRAINT "FK_purchase_order_lines_purchase_request_lines_request_line_id" FOREIGN KEY (request_line_id) REFERENCES purchase_request_lines (id) ON DELETE SET NULL
);

CREATE TABLE interviews (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    application_id uuid NOT NULL,
    type text NOT NULL,
    status text NOT NULL,
    scheduled_at timestamp with time zone NOT NULL,
    duration_minutes integer NOT NULL,
    location text,
    notes text,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_interviews" PRIMARY KEY (id),
    CONSTRAINT "FK_interviews_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_interviews_job_applications_application_id" FOREIGN KEY (application_id) REFERENCES job_applications (id) ON DELETE CASCADE,
    CONSTRAINT "FK_interviews_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE job_offers (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    application_id uuid NOT NULL,
    gross_salary numeric(18,2) NOT NULL,
    start_date date NOT NULL,
    expires_at date,
    status text NOT NULL,
    notes text,
    responded_at timestamp with time zone,
    created_by_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_job_offers" PRIMARY KEY (id),
    CONSTRAINT "FK_job_offers_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_job_offers_job_applications_application_id" FOREIGN KEY (application_id) REFERENCES job_applications (id) ON DELETE CASCADE,
    CONSTRAINT "FK_job_offers_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE expense_lines (
    id uuid NOT NULL,
    report_id uuid NOT NULL,
    line_no integer NOT NULL,
    category_id uuid NOT NULL,
    expense_date date NOT NULL,
    description text NOT NULL,
    merchant text,
    quantity numeric(18,2) NOT NULL,
    amount numeric(18,2) NOT NULL,
    vat_amount numeric(18,2) NOT NULL,
    currency_code text NOT NULL,
    exchange_rate numeric(18,6) NOT NULL,
    amount_base numeric(18,2) NOT NULL,
    receipt_file_id uuid,
    CONSTRAINT "PK_expense_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_expense_lines_expense_categories_category_id" FOREIGN KEY (category_id) REFERENCES expense_categories (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_expense_lines_expense_reports_report_id" FOREIGN KEY (report_id) REFERENCES expense_reports (id) ON DELETE CASCADE,
    CONSTRAINT "FK_expense_lines_files_receipt_file_id" FOREIGN KEY (receipt_file_id) REFERENCES files (id) ON DELETE SET NULL
);

CREATE TABLE time_entries (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    project_id uuid NOT NULL,
    task_id uuid,
    employee_id uuid NOT NULL,
    work_date date NOT NULL,
    hours numeric(5,2) NOT NULL,
    description text,
    cost_amount numeric(18,2) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_time_entries" PRIMARY KEY (id),
    CONSTRAINT "FK_time_entries_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_time_entries_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_time_entries_project_tasks_task_id" FOREIGN KEY (task_id) REFERENCES project_tasks (id) ON DELETE SET NULL,
    CONSTRAINT "FK_time_entries_projects_project_id" FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT
);

CREATE TABLE e_invoices (
    id uuid NOT NULL,
    company_id uuid NOT NULL,
    direction text NOT NULL,
    profile text NOT NULL,
    status text NOT NULL,
    number text NOT NULL,
    uuid uuid NOT NULL,
    sales_invoice_id uuid,
    purchase_invoice_id uuid,
    partner_id uuid NOT NULL,
    issue_date date NOT NULL,
    currency_code text NOT NULL,
    exchange_rate numeric(18,6) NOT NULL,
    line_extension_amount numeric(18,2) NOT NULL,
    allowance_total numeric(18,2) NOT NULL,
    tax_total numeric(18,2) NOT NULL,
    payable_amount numeric(18,2) NOT NULL,
    sender_alias text,
    receiver_alias text,
    sender_tax_id text,
    receiver_tax_id text,
    lines jsonb NOT NULL,
    xml text NOT NULL,
    status_code text,
    status_message text,
    response_note text,
    sent_at timestamp with time zone,
    delivered_at timestamp with time zone,
    responded_at timestamp with time zone,
    created_by_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_e_invoices" PRIMARY KEY (id),
    CONSTRAINT "FK_e_invoices_business_partners_partner_id" FOREIGN KEY (partner_id) REFERENCES business_partners (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_e_invoices_companies_company_id" FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_e_invoices_purchase_invoices_purchase_invoice_id" FOREIGN KEY (purchase_invoice_id) REFERENCES purchase_invoices (id) ON DELETE SET NULL,
    CONSTRAINT "FK_e_invoices_sales_invoices_sales_invoice_id" FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoices (id) ON DELETE SET NULL,
    CONSTRAINT "FK_e_invoices_users_created_by_id" FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE payment_allocations (
    id uuid NOT NULL,
    payment_id uuid NOT NULL,
    sales_invoice_id uuid,
    purchase_invoice_id uuid,
    amount numeric(18,2) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_payment_allocations" PRIMARY KEY (id),
    CONSTRAINT "FK_payment_allocations_partner_payments_payment_id" FOREIGN KEY (payment_id) REFERENCES partner_payments (id) ON DELETE CASCADE,
    CONSTRAINT "FK_payment_allocations_purchase_invoices_purchase_invoice_id" FOREIGN KEY (purchase_invoice_id) REFERENCES purchase_invoices (id) ON DELETE SET NULL,
    CONSTRAINT "FK_payment_allocations_sales_invoices_sales_invoice_id" FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoices (id) ON DELETE SET NULL
);

CREATE TABLE goods_receipt_lines (
    id uuid NOT NULL,
    receipt_id uuid NOT NULL,
    order_line_id uuid NOT NULL,
    variant_id uuid NOT NULL,
    quantity numeric(18,3) NOT NULL,
    lot_no text,
    expiry_date date,
    serial_nos text NOT NULL,
    CONSTRAINT "PK_goods_receipt_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_goods_receipt_lines_goods_receipts_receipt_id" FOREIGN KEY (receipt_id) REFERENCES goods_receipts (id) ON DELETE CASCADE,
    CONSTRAINT "FK_goods_receipt_lines_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_goods_receipt_lines_purchase_order_lines_order_line_id" FOREIGN KEY (order_line_id) REFERENCES purchase_order_lines (id) ON DELETE RESTRICT
);

CREATE TABLE purchase_invoice_lines (
    id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    line_no integer NOT NULL,
    order_line_id uuid,
    variant_id uuid,
    description text NOT NULL,
    quantity numeric(18,3) NOT NULL,
    unit_price numeric(18,4) NOT NULL,
    discount_rate numeric(5,2) NOT NULL,
    vat_rate numeric(5,2) NOT NULL,
    line_total numeric(18,2) NOT NULL,
    CONSTRAINT "PK_purchase_invoice_lines" PRIMARY KEY (id),
    CONSTRAINT "FK_purchase_invoice_lines_product_variants_variant_id" FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE SET NULL,
    CONSTRAINT "FK_purchase_invoice_lines_purchase_invoices_invoice_id" FOREIGN KEY (invoice_id) REFERENCES purchase_invoices (id) ON DELETE CASCADE,
    CONSTRAINT "FK_purchase_invoice_lines_purchase_order_lines_order_line_id" FOREIGN KEY (order_line_id) REFERENCES purchase_order_lines (id) ON DELETE SET NULL
);

CREATE TABLE interview_participants (
    id uuid NOT NULL,
    interview_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    rating integer,
    recommendation text,
    feedback text,
    submitted_at timestamp with time zone,
    CONSTRAINT "PK_interview_participants" PRIMARY KEY (id),
    CONSTRAINT "FK_interview_participants_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT "FK_interview_participants_interviews_interview_id" FOREIGN KEY (interview_id) REFERENCES interviews (id) ON DELETE CASCADE
);

CREATE TABLE e_invoice_events (
    id uuid NOT NULL,
    e_invoice_id uuid NOT NULL,
    status text NOT NULL,
    code text,
    message text NOT NULL,
    user_id uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT "PK_e_invoice_events" PRIMARY KEY (id),
    CONSTRAINT "FK_e_invoice_events_e_invoices_e_invoice_id" FOREIGN KEY (e_invoice_id) REFERENCES e_invoices (id) ON DELETE CASCADE,
    CONSTRAINT "FK_e_invoice_events_users_user_id" FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX "IX_application_activities_application_id_created_at" ON application_activities (application_id, created_at);

CREATE INDEX "IX_application_activities_created_by_id" ON application_activities (created_by_id);

CREATE INDEX "IX_asset_assignments_asset_id_status" ON asset_assignments (asset_id, status);

CREATE INDEX "IX_asset_assignments_assigned_by_id" ON asset_assignments (assigned_by_id);

CREATE INDEX "IX_asset_assignments_company_id_employee_id_status" ON asset_assignments (company_id, employee_id, status);

CREATE UNIQUE INDEX "IX_asset_assignments_company_id_number" ON asset_assignments (company_id, number);

CREATE INDEX "IX_asset_assignments_department_id" ON asset_assignments (department_id);

CREATE INDEX "IX_asset_assignments_employee_id" ON asset_assignments (employee_id);

CREATE INDEX "IX_asset_assignments_location_id" ON asset_assignments (location_id);

CREATE INDEX "IX_asset_assignments_returned_by_id" ON asset_assignments (returned_by_id);

CREATE UNIQUE INDEX "IX_asset_categories_company_id_code" ON asset_categories (company_id, code);

CREATE INDEX "IX_asset_count_lines_asset_id" ON asset_count_lines (asset_id);

CREATE UNIQUE INDEX "IX_asset_count_lines_count_id_asset_id" ON asset_count_lines (count_id, asset_id);

CREATE INDEX "IX_asset_count_lines_found_by_id" ON asset_count_lines (found_by_id);

CREATE INDEX "IX_asset_counts_category_id" ON asset_counts (category_id);

CREATE UNIQUE INDEX "IX_asset_counts_company_id_number" ON asset_counts (company_id, number);

CREATE INDEX "IX_asset_counts_company_id_status" ON asset_counts (company_id, status);

CREATE INDEX "IX_asset_counts_created_by_id" ON asset_counts (created_by_id);

CREATE INDEX "IX_asset_counts_location_id" ON asset_counts (location_id);

CREATE INDEX "IX_asset_locations_branch_id" ON asset_locations (branch_id);

CREATE UNIQUE INDEX "IX_asset_locations_company_id_code" ON asset_locations (company_id, code);

CREATE INDEX "IX_asset_maintenances_asset_id_status" ON asset_maintenances (asset_id, status);

CREATE INDEX "IX_asset_maintenances_company_id" ON asset_maintenances (company_id);

CREATE INDEX "IX_assets_category_id" ON assets (category_id);

CREATE UNIQUE INDEX "IX_assets_company_id_asset_tag" ON assets (company_id, asset_tag);

CREATE INDEX "IX_assets_company_id_category_id" ON assets (company_id, category_id);

CREATE UNIQUE INDEX "IX_assets_company_id_serial_number" ON assets (company_id, serial_number);

CREATE INDEX "IX_assets_company_id_status" ON assets (company_id, status);

CREATE INDEX "IX_assets_location_id" ON assets (location_id);

CREATE INDEX "IX_attendance_records_company_id_date" ON attendance_records (company_id, date);

CREATE UNIQUE INDEX "IX_attendance_records_employee_id_date" ON attendance_records (employee_id, date);

CREATE INDEX "IX_attendance_records_updated_by_id" ON attendance_records (updated_by_id);

CREATE INDEX "IX_audit_logs_company_id_created_at" ON audit_logs (company_id, created_at);

CREATE INDEX "IX_audit_logs_entity_type_entity_id" ON audit_logs (entity_type, entity_id);

CREATE INDEX "IX_audit_logs_user_id" ON audit_logs (user_id);

CREATE UNIQUE INDEX "IX_bills_of_materials_company_id_code" ON bills_of_materials (company_id, code);

CREATE INDEX "IX_bills_of_materials_company_id_variant_id" ON bills_of_materials (company_id, variant_id);

CREATE INDEX "IX_bills_of_materials_created_by_id" ON bills_of_materials (created_by_id);

CREATE INDEX "IX_bills_of_materials_variant_id" ON bills_of_materials (variant_id);

CREATE UNIQUE INDEX "IX_bom_lines_bom_id_variant_id" ON bom_lines (bom_id, variant_id);

CREATE INDEX "IX_bom_lines_variant_id" ON bom_lines (variant_id);

CREATE INDEX "IX_branches_company_id" ON branches (company_id);

CREATE UNIQUE INDEX "IX_branches_company_id_code" ON branches (company_id, code);

CREATE UNIQUE INDEX "IX_budget_alerts_budget_id_item_id_level" ON budget_alerts (budget_id, item_id, level);

CREATE INDEX "IX_budget_alerts_item_id" ON budget_alerts (item_id);

CREATE INDEX "IX_budget_entries_company_id_entry_date" ON budget_entries (company_id, entry_date);

CREATE INDEX "IX_budget_entries_created_by_id" ON budget_entries (created_by_id);

CREATE INDEX "IX_budget_entries_department_id" ON budget_entries (department_id);

CREATE INDEX "IX_budget_entries_item_id" ON budget_entries (item_id);

CREATE INDEX "IX_budget_events_actor_id" ON budget_events (actor_id);

CREATE INDEX "IX_budget_events_budget_id" ON budget_events (budget_id);

CREATE UNIQUE INDEX "IX_budget_items_company_id_code" ON budget_items (company_id, code);

CREATE UNIQUE INDEX "IX_budget_lines_budget_id_item_id_month" ON budget_lines (budget_id, item_id, month);

CREATE INDEX "IX_budget_lines_item_id" ON budget_lines (item_id);

CREATE INDEX "IX_budgets_approved_by_id" ON budgets (approved_by_id);

CREATE INDEX "IX_budgets_company_id_year" ON budgets (company_id, year);

CREATE INDEX "IX_budgets_created_by_id" ON budgets (created_by_id);

CREATE INDEX "IX_budgets_department_id" ON budgets (department_id);

CREATE INDEX "IX_budgets_owner_id" ON budgets (owner_id);

CREATE UNIQUE INDEX "IX_business_partners_company_id_code" ON business_partners (company_id, code);

CREATE INDEX "IX_business_partners_company_id_type" ON business_partners (company_id, type);

CREATE UNIQUE INDEX "IX_candidates_company_id_email" ON candidates (company_id, email);

CREATE INDEX "IX_candidates_created_by_id" ON candidates (created_by_id);

CREATE INDEX "IX_candidates_cv_file_id" ON candidates (cv_file_id);

CREATE INDEX "IX_candidates_referred_by_id" ON candidates (referred_by_id);

CREATE INDEX "IX_cheque_events_account_id" ON cheque_events (account_id);

CREATE INDEX "IX_cheque_events_cheque_id_created_at" ON cheque_events (cheque_id, created_at);

CREATE INDEX "IX_cheque_events_created_by_id" ON cheque_events (created_by_id);

CREATE INDEX "IX_cheque_events_partner_id" ON cheque_events (partner_id);

CREATE INDEX "IX_cheques_account_id" ON cheques (account_id);

CREATE INDEX "IX_cheques_company_id_direction_status_due_date" ON cheques (company_id, direction, status, due_date);

CREATE UNIQUE INDEX "IX_cheques_company_id_number" ON cheques (company_id, number);

CREATE INDEX "IX_cheques_endorsed_to_id" ON cheques (endorsed_to_id);

CREATE UNIQUE INDEX "IX_cheques_endorsement_payment_id" ON cheques (endorsement_payment_id);

CREATE UNIQUE INDEX "IX_cheques_origin_payment_id" ON cheques (origin_payment_id);

CREATE INDEX "IX_cheques_partner_id" ON cheques (partner_id);

CREATE UNIQUE INDEX "IX_companies_code" ON companies (code);

CREATE UNIQUE INDEX "IX_competencies_company_id_name" ON competencies (company_id, name);

CREATE INDEX "IX_competency_ratings_competency_id" ON competency_ratings (competency_id);

CREATE UNIQUE INDEX "IX_competency_ratings_review_id_competency_id" ON competency_ratings (review_id, competency_id);

CREATE INDEX "IX_contract_events_actor_id" ON contract_events (actor_id);

CREATE INDEX "IX_contract_events_contract_id_created_at" ON contract_events (contract_id, created_at);

CREATE UNIQUE INDEX "IX_contract_types_company_id_name" ON contract_types (company_id, name);

CREATE UNIQUE INDEX "IX_contracts_company_id_number" ON contracts (company_id, number);

CREATE INDEX "IX_contracts_company_id_status_end_date" ON contracts (company_id, status, end_date);

CREATE INDEX "IX_contracts_created_by_id" ON contracts (created_by_id);

CREATE INDEX "IX_contracts_employee_id" ON contracts (employee_id);

CREATE INDEX "IX_contracts_owner_id" ON contracts (owner_id);

CREATE INDEX "IX_contracts_partner_id" ON contracts (partner_id);

CREATE INDEX "IX_contracts_type_id" ON contracts (type_id);

CREATE INDEX "IX_crm_activities_company_id_owner_id_completed_at" ON crm_activities (company_id, owner_id, completed_at);

CREATE INDEX "IX_crm_activities_created_by_id" ON crm_activities (created_by_id);

CREATE INDEX "IX_crm_activities_lead_id" ON crm_activities (lead_id);

CREATE INDEX "IX_crm_activities_opportunity_id" ON crm_activities (opportunity_id);

CREATE INDEX "IX_crm_activities_owner_id" ON crm_activities (owner_id);

CREATE INDEX "IX_crm_activities_partner_id" ON crm_activities (partner_id);

CREATE INDEX "IX_delivery_note_lines_delivery_id" ON delivery_note_lines (delivery_id);

CREATE INDEX "IX_delivery_note_lines_order_line_id" ON delivery_note_lines (order_line_id);

CREATE INDEX "IX_delivery_note_lines_variant_id" ON delivery_note_lines (variant_id);

CREATE UNIQUE INDEX "IX_delivery_notes_company_id_number" ON delivery_notes (company_id, number);

CREATE INDEX "IX_delivery_notes_created_by_id" ON delivery_notes (created_by_id);

CREATE INDEX "IX_delivery_notes_order_id" ON delivery_notes (order_id);

CREATE INDEX "IX_delivery_notes_partner_id" ON delivery_notes (partner_id);

CREATE UNIQUE INDEX "IX_delivery_notes_stock_movement_id" ON delivery_notes (stock_movement_id);

CREATE INDEX "IX_delivery_notes_warehouse_id" ON delivery_notes (warehouse_id);

CREATE UNIQUE INDEX "IX_departments_company_id_code" ON departments (company_id, code);

CREATE INDEX "IX_departments_company_id_parent_id" ON departments (company_id, parent_id);

CREATE INDEX "IX_departments_manager_id" ON departments (manager_id);

CREATE INDEX "IX_departments_parent_id" ON departments (parent_id);

CREATE INDEX "IX_e_invoice_events_e_invoice_id_created_at" ON e_invoice_events (e_invoice_id, created_at);

CREATE INDEX "IX_e_invoice_events_user_id" ON e_invoice_events (user_id);

CREATE UNIQUE INDEX "IX_e_invoices_company_id_direction_number" ON e_invoices (company_id, direction, number);

CREATE INDEX "IX_e_invoices_company_id_direction_status" ON e_invoices (company_id, direction, status);

CREATE INDEX "IX_e_invoices_created_by_id" ON e_invoices (created_by_id);

CREATE INDEX "IX_e_invoices_partner_id" ON e_invoices (partner_id);

CREATE UNIQUE INDEX "IX_e_invoices_purchase_invoice_id" ON e_invoices (purchase_invoice_id);

CREATE UNIQUE INDEX "IX_e_invoices_sales_invoice_id" ON e_invoices (sales_invoice_id);

CREATE UNIQUE INDEX "IX_e_invoices_uuid" ON e_invoices (uuid);

CREATE INDEX "IX_employees_branch_id" ON employees (branch_id);

CREATE INDEX "IX_employees_company_id_department_id" ON employees (company_id, department_id);

CREATE UNIQUE INDEX "IX_employees_company_id_employee_no" ON employees (company_id, employee_no);

CREATE INDEX "IX_employees_company_id_manager_id" ON employees (company_id, manager_id);

CREATE UNIQUE INDEX "IX_employees_company_id_user_id" ON employees (company_id, user_id);

CREATE INDEX "IX_employees_department_id" ON employees (department_id);

CREATE INDEX "IX_employees_manager_id" ON employees (manager_id);

CREATE INDEX "IX_employees_position_id" ON employees (position_id);

CREATE INDEX "IX_employees_user_id" ON employees (user_id);

CREATE UNIQUE INDEX "IX_exchange_rates_currency_code_rate_date_source" ON exchange_rates (currency_code, rate_date, source);

CREATE UNIQUE INDEX "IX_expense_categories_company_id_code" ON expense_categories (company_id, code);

CREATE INDEX "IX_expense_lines_category_id" ON expense_lines (category_id);

CREATE INDEX "IX_expense_lines_receipt_file_id" ON expense_lines (receipt_file_id);

CREATE UNIQUE INDEX "IX_expense_lines_report_id_line_no" ON expense_lines (report_id, line_no);

CREATE UNIQUE INDEX "IX_expense_reports_company_id_number" ON expense_reports (company_id, number);

CREATE INDEX "IX_expense_reports_company_id_status" ON expense_reports (company_id, status);

CREATE INDEX "IX_expense_reports_created_by_id" ON expense_reports (created_by_id);

CREATE INDEX "IX_expense_reports_employee_id" ON expense_reports (employee_id);

CREATE INDEX "IX_expense_reports_paid_by_id" ON expense_reports (paid_by_id);

CREATE INDEX "IX_expense_reports_payment_account_id" ON expense_reports (payment_account_id);

CREATE INDEX "IX_expense_reports_project_id" ON expense_reports (project_id);

CREATE INDEX "IX_files_company_id_entity_type_entity_id" ON files (company_id, entity_type, entity_id);

CREATE UNIQUE INDEX "IX_files_storage_key" ON files (storage_key);

CREATE INDEX "IX_goods_receipt_lines_order_line_id" ON goods_receipt_lines (order_line_id);

CREATE INDEX "IX_goods_receipt_lines_receipt_id" ON goods_receipt_lines (receipt_id);

CREATE INDEX "IX_goods_receipt_lines_variant_id" ON goods_receipt_lines (variant_id);

CREATE UNIQUE INDEX "IX_goods_receipts_company_id_number" ON goods_receipts (company_id, number);

CREATE INDEX "IX_goods_receipts_created_by_id" ON goods_receipts (created_by_id);

CREATE INDEX "IX_goods_receipts_order_id" ON goods_receipts (order_id);

CREATE INDEX "IX_goods_receipts_partner_id" ON goods_receipts (partner_id);

CREATE UNIQUE INDEX "IX_goods_receipts_stock_movement_id" ON goods_receipts (stock_movement_id);

CREATE INDEX "IX_goods_receipts_warehouse_id" ON goods_receipts (warehouse_id);

CREATE UNIQUE INDEX "IX_holidays_company_id_date" ON holidays (company_id, date);

CREATE INDEX "IX_interview_participants_employee_id" ON interview_participants (employee_id);

CREATE UNIQUE INDEX "IX_interview_participants_interview_id_employee_id" ON interview_participants (interview_id, employee_id);

CREATE INDEX "IX_interviews_application_id" ON interviews (application_id);

CREATE INDEX "IX_interviews_company_id_scheduled_at" ON interviews (company_id, scheduled_at);

CREATE INDEX "IX_interviews_created_by_id" ON interviews (created_by_id);

CREATE INDEX "IX_job_applications_candidate_id" ON job_applications (candidate_id);

CREATE INDEX "IX_job_applications_company_id_stage" ON job_applications (company_id, stage);

CREATE INDEX "IX_job_applications_created_by_id" ON job_applications (created_by_id);

CREATE UNIQUE INDEX "IX_job_applications_employee_id" ON job_applications (employee_id);

CREATE UNIQUE INDEX "IX_job_applications_posting_id_candidate_id" ON job_applications (posting_id, candidate_id);

CREATE INDEX "IX_job_offers_application_id" ON job_offers (application_id);

CREATE INDEX "IX_job_offers_company_id" ON job_offers (company_id);

CREATE INDEX "IX_job_offers_created_by_id" ON job_offers (created_by_id);

CREATE INDEX "IX_job_postings_branch_id" ON job_postings (branch_id);

CREATE UNIQUE INDEX "IX_job_postings_company_id_number" ON job_postings (company_id, number);

CREATE INDEX "IX_job_postings_company_id_status" ON job_postings (company_id, status);

CREATE INDEX "IX_job_postings_created_by_id" ON job_postings (created_by_id);

CREATE INDEX "IX_job_postings_department_id" ON job_postings (department_id);

CREATE INDEX "IX_job_postings_hiring_manager_id" ON job_postings (hiring_manager_id);

CREATE INDEX "IX_job_postings_position_id" ON job_postings (position_id);

CREATE INDEX "IX_journal_entries_company_id_entry_date" ON journal_entries (company_id, entry_date);

CREATE UNIQUE INDEX "IX_journal_entries_company_id_number" ON journal_entries (company_id, number);

CREATE INDEX "IX_journal_entries_company_id_source_source_id" ON journal_entries (company_id, source, source_id);

CREATE INDEX "IX_journal_entries_created_by_id" ON journal_entries (created_by_id);

CREATE UNIQUE INDEX "IX_journal_entries_reverses_id" ON journal_entries (reverses_id);

CREATE INDEX "IX_journal_lines_account_id" ON journal_lines (account_id);

CREATE INDEX "IX_journal_lines_employee_id" ON journal_lines (employee_id);

CREATE UNIQUE INDEX "IX_journal_lines_entry_id_line_no" ON journal_lines (entry_id, line_no);

CREATE INDEX "IX_journal_lines_partner_id" ON journal_lines (partner_id);

CREATE UNIQUE INDEX "IX_leads_company_id_number" ON leads (company_id, number);

CREATE INDEX "IX_leads_company_id_status" ON leads (company_id, status);

CREATE INDEX "IX_leads_created_by_id" ON leads (created_by_id);

CREATE INDEX "IX_leads_owner_id" ON leads (owner_id);

CREATE INDEX "IX_leads_partner_id" ON leads (partner_id);

CREATE INDEX "IX_leave_balances_company_id_year" ON leave_balances (company_id, year);

CREATE UNIQUE INDEX "IX_leave_balances_employee_id_leave_type_id_year" ON leave_balances (employee_id, leave_type_id, year);

CREATE INDEX "IX_leave_balances_leave_type_id" ON leave_balances (leave_type_id);

CREATE UNIQUE INDEX "IX_leave_requests_company_id_number" ON leave_requests (company_id, number);

CREATE INDEX "IX_leave_requests_company_id_status" ON leave_requests (company_id, status);

CREATE INDEX "IX_leave_requests_employee_id_start_date" ON leave_requests (employee_id, start_date);

CREATE INDEX "IX_leave_requests_leave_type_id" ON leave_requests (leave_type_id);

CREATE UNIQUE INDEX "IX_leave_types_company_id_code" ON leave_types (company_id, code);

CREATE UNIQUE INDEX "IX_ledger_accounts_company_id_code" ON ledger_accounts (company_id, code);

CREATE INDEX "IX_ledger_accounts_parent_id" ON ledger_accounts (parent_id);

CREATE INDEX "IX_lot_balances_company_id" ON lot_balances (company_id);

CREATE INDEX "IX_lot_balances_lot_id" ON lot_balances (lot_id);

CREATE UNIQUE INDEX "IX_lot_balances_warehouse_id_lot_id" ON lot_balances (warehouse_id, lot_id);

CREATE INDEX "IX_notifications_user_id_read_at" ON notifications (user_id, read_at);

CREATE UNIQUE INDEX "IX_number_sequences_company_id_code" ON number_sequences (company_id, code);

CREATE UNIQUE INDEX "IX_opportunities_company_id_number" ON opportunities (company_id, number);

CREATE INDEX "IX_opportunities_company_id_stage" ON opportunities (company_id, stage);

CREATE INDEX "IX_opportunities_created_by_id" ON opportunities (created_by_id);

CREATE INDEX "IX_opportunities_lead_id" ON opportunities (lead_id);

CREATE INDEX "IX_opportunities_owner_id" ON opportunities (owner_id);

CREATE INDEX "IX_opportunities_partner_id" ON opportunities (partner_id);

CREATE INDEX "IX_partner_ledger_entries_company_id_partner_id_seq" ON partner_ledger_entries (company_id, partner_id, seq);

CREATE INDEX "IX_partner_ledger_entries_created_by_id" ON partner_ledger_entries (created_by_id);

CREATE INDEX "IX_partner_ledger_entries_document_type_document_id" ON partner_ledger_entries (document_type, document_id);

CREATE INDEX "IX_partner_ledger_entries_partner_id" ON partner_ledger_entries (partner_id);

CREATE UNIQUE INDEX "IX_partner_ledger_entries_reverses_id" ON partner_ledger_entries (reverses_id);

CREATE INDEX "IX_partner_payments_account_id" ON partner_payments (account_id);

CREATE INDEX "IX_partner_payments_company_id_direction_payment_date" ON partner_payments (company_id, direction, payment_date);

CREATE UNIQUE INDEX "IX_partner_payments_company_id_number" ON partner_payments (company_id, number);

CREATE INDEX "IX_partner_payments_company_id_partner_id" ON partner_payments (company_id, partner_id);

CREATE INDEX "IX_partner_payments_created_by_id" ON partner_payments (created_by_id);

CREATE INDEX "IX_partner_payments_partner_id" ON partner_payments (partner_id);

CREATE UNIQUE INDEX "IX_password_reset_tokens_token_hash" ON password_reset_tokens (token_hash);

CREATE INDEX "IX_password_reset_tokens_user_id" ON password_reset_tokens (user_id);

CREATE INDEX "IX_payment_allocations_payment_id" ON payment_allocations (payment_id);

CREATE INDEX "IX_payment_allocations_purchase_invoice_id" ON payment_allocations (purchase_invoice_id);

CREATE INDEX "IX_payment_allocations_sales_invoice_id" ON payment_allocations (sales_invoice_id);

CREATE UNIQUE INDEX "IX_payroll_parameters_company_id_year" ON payroll_parameters (company_id, year);

CREATE INDEX "IX_payroll_periods_approved_by_id" ON payroll_periods (approved_by_id);

CREATE UNIQUE INDEX "IX_payroll_periods_company_id_number" ON payroll_periods (company_id, number);

CREATE UNIQUE INDEX "IX_payroll_periods_company_id_year_month" ON payroll_periods (company_id, year, month);

CREATE INDEX "IX_payroll_periods_created_by_id" ON payroll_periods (created_by_id);

CREATE INDEX "IX_payroll_periods_paid_by_id" ON payroll_periods (paid_by_id);

CREATE INDEX "IX_payroll_periods_payment_account_id" ON payroll_periods (payment_account_id);

CREATE INDEX "IX_payslip_items_payslip_id" ON payslip_items (payslip_id);

CREATE INDEX "IX_payslips_company_id" ON payslips (company_id);

CREATE INDEX "IX_payslips_employee_id" ON payslips (employee_id);

CREATE UNIQUE INDEX "IX_payslips_period_id_employee_id" ON payslips (period_id, employee_id);

CREATE INDEX "IX_performance_goals_review_id" ON performance_goals (review_id);

CREATE INDEX "IX_performance_reviews_company_id_status" ON performance_reviews (company_id, status);

CREATE UNIQUE INDEX "IX_performance_reviews_cycle_id_employee_id" ON performance_reviews (cycle_id, employee_id);

CREATE INDEX "IX_performance_reviews_employee_id" ON performance_reviews (employee_id);

CREATE INDEX "IX_performance_reviews_reviewer_id" ON performance_reviews (reviewer_id);

CREATE UNIQUE INDEX "IX_permissions_code" ON permissions (code);

CREATE INDEX "IX_permissions_module" ON permissions (module);

CREATE UNIQUE INDEX "IX_positions_company_id_code" ON positions (company_id, code);

CREATE INDEX "IX_positions_department_id" ON positions (department_id);

CREATE UNIQUE INDEX "IX_product_categories_company_id_code" ON product_categories (company_id, code);

CREATE INDEX "IX_product_categories_parent_id" ON product_categories (parent_id);

CREATE UNIQUE INDEX "IX_product_variants_company_id_barcode" ON product_variants (company_id, barcode);

CREATE UNIQUE INDEX "IX_product_variants_company_id_sku" ON product_variants (company_id, sku);

CREATE INDEX "IX_product_variants_product_id" ON product_variants (product_id);

CREATE UNIQUE INDEX "IX_production_records_company_id_number" ON production_records (company_id, number);

CREATE INDEX "IX_production_records_created_by_id" ON production_records (created_by_id);

CREATE UNIQUE INDEX "IX_production_records_issue_movement_id" ON production_records (issue_movement_id);

CREATE UNIQUE INDEX "IX_production_records_receipt_movement_id" ON production_records (receipt_movement_id);

CREATE INDEX "IX_production_records_work_order_id" ON production_records (work_order_id);

CREATE INDEX "IX_products_category_id" ON products (category_id);

CREATE INDEX "IX_products_company_id_category_id" ON products (company_id, category_id);

CREATE UNIQUE INDEX "IX_products_company_id_code" ON products (company_id, code);

CREATE INDEX "IX_products_unit_id" ON products (unit_id);

CREATE INDEX "IX_products_vat_rate_id" ON products (vat_rate_id);

CREATE INDEX "IX_project_members_employee_id" ON project_members (employee_id);

CREATE UNIQUE INDEX "IX_project_members_project_id_employee_id" ON project_members (project_id, employee_id);

CREATE INDEX "IX_project_tasks_assignee_id" ON project_tasks (assignee_id);

CREATE INDEX "IX_project_tasks_created_by_id" ON project_tasks (created_by_id);

CREATE INDEX "IX_project_tasks_project_id_status" ON project_tasks (project_id, status);

CREATE UNIQUE INDEX "IX_project_tasks_project_id_task_no" ON project_tasks (project_id, task_no);

CREATE UNIQUE INDEX "IX_projects_company_id_number" ON projects (company_id, number);

CREATE INDEX "IX_projects_company_id_status" ON projects (company_id, status);

CREATE INDEX "IX_projects_created_by_id" ON projects (created_by_id);

CREATE INDEX "IX_projects_manager_id" ON projects (manager_id);

CREATE INDEX "IX_projects_partner_id" ON projects (partner_id);

CREATE INDEX "IX_purchase_invoice_lines_invoice_id" ON purchase_invoice_lines (invoice_id);

CREATE INDEX "IX_purchase_invoice_lines_order_line_id" ON purchase_invoice_lines (order_line_id);

CREATE INDEX "IX_purchase_invoice_lines_variant_id" ON purchase_invoice_lines (variant_id);

CREATE UNIQUE INDEX "IX_purchase_invoices_company_id_number" ON purchase_invoices (company_id, number);

CREATE INDEX "IX_purchase_invoices_company_id_partner_id_status" ON purchase_invoices (company_id, partner_id, status);

CREATE INDEX "IX_purchase_invoices_created_by_id" ON purchase_invoices (created_by_id);

CREATE INDEX "IX_purchase_invoices_order_id" ON purchase_invoices (order_id);

CREATE INDEX "IX_purchase_invoices_partner_id" ON purchase_invoices (partner_id);

CREATE INDEX "IX_purchase_order_lines_order_id" ON purchase_order_lines (order_id);

CREATE INDEX "IX_purchase_order_lines_request_line_id" ON purchase_order_lines (request_line_id);

CREATE INDEX "IX_purchase_order_lines_variant_id" ON purchase_order_lines (variant_id);

CREATE INDEX "IX_purchase_orders_approved_by_id" ON purchase_orders (approved_by_id);

CREATE UNIQUE INDEX "IX_purchase_orders_company_id_number" ON purchase_orders (company_id, number);

CREATE INDEX "IX_purchase_orders_company_id_status" ON purchase_orders (company_id, status);

CREATE INDEX "IX_purchase_orders_created_by_id" ON purchase_orders (created_by_id);

CREATE INDEX "IX_purchase_orders_partner_id" ON purchase_orders (partner_id);

CREATE UNIQUE INDEX "IX_purchase_orders_quote_id" ON purchase_orders (quote_id);

CREATE INDEX "IX_purchase_orders_request_id" ON purchase_orders (request_id);

CREATE INDEX "IX_purchase_orders_warehouse_id" ON purchase_orders (warehouse_id);

CREATE INDEX "IX_purchase_request_lines_request_id" ON purchase_request_lines (request_id);

CREATE INDEX "IX_purchase_request_lines_variant_id" ON purchase_request_lines (variant_id);

CREATE UNIQUE INDEX "IX_purchase_requests_company_id_number" ON purchase_requests (company_id, number);

CREATE INDEX "IX_purchase_requests_company_id_status" ON purchase_requests (company_id, status);

CREATE INDEX "IX_purchase_requests_department_id" ON purchase_requests (department_id);

CREATE INDEX "IX_purchase_requests_requested_by_id" ON purchase_requests (requested_by_id);

CREATE INDEX "IX_refresh_tokens_family_id" ON refresh_tokens (family_id);

CREATE UNIQUE INDEX "IX_refresh_tokens_token_hash" ON refresh_tokens (token_hash);

CREATE INDEX "IX_refresh_tokens_user_id" ON refresh_tokens (user_id);

CREATE INDEX "IX_review_cycle_competencies_competency_id" ON review_cycle_competencies (competency_id);

CREATE INDEX "IX_review_cycles_company_id_status" ON review_cycles (company_id, status);

CREATE INDEX "IX_review_cycles_created_by_id" ON review_cycles (created_by_id);

CREATE INDEX "IX_role_permissions_permission_id" ON role_permissions (permission_id);

CREATE UNIQUE INDEX "IX_roles_company_id_code" ON roles (company_id, code);

CREATE INDEX "IX_salary_records_company_id" ON salary_records (company_id);

CREATE INDEX "IX_salary_records_created_by_id" ON salary_records (created_by_id);

CREATE UNIQUE INDEX "IX_salary_records_employee_id_effective_from" ON salary_records (employee_id, effective_from);

CREATE INDEX "IX_sales_invoice_lines_invoice_id" ON sales_invoice_lines (invoice_id);

CREATE INDEX "IX_sales_invoice_lines_order_line_id" ON sales_invoice_lines (order_line_id);

CREATE INDEX "IX_sales_invoice_lines_variant_id" ON sales_invoice_lines (variant_id);

CREATE UNIQUE INDEX "IX_sales_invoices_company_id_number" ON sales_invoices (company_id, number);

CREATE INDEX "IX_sales_invoices_company_id_partner_id_status" ON sales_invoices (company_id, partner_id, status);

CREATE INDEX "IX_sales_invoices_created_by_id" ON sales_invoices (created_by_id);

CREATE INDEX "IX_sales_invoices_order_id" ON sales_invoices (order_id);

CREATE INDEX "IX_sales_invoices_partner_id" ON sales_invoices (partner_id);

CREATE INDEX "IX_sales_order_lines_order_id" ON sales_order_lines (order_id);

CREATE INDEX "IX_sales_order_lines_variant_id" ON sales_order_lines (variant_id);

CREATE INDEX "IX_sales_orders_approved_by_id" ON sales_orders (approved_by_id);

CREATE UNIQUE INDEX "IX_sales_orders_company_id_number" ON sales_orders (company_id, number);

CREATE INDEX "IX_sales_orders_company_id_status" ON sales_orders (company_id, status);

CREATE INDEX "IX_sales_orders_created_by_id" ON sales_orders (created_by_id);

CREATE INDEX "IX_sales_orders_partner_id" ON sales_orders (partner_id);

CREATE UNIQUE INDEX "IX_sales_orders_quote_id" ON sales_orders (quote_id);

CREATE INDEX "IX_sales_orders_warehouse_id" ON sales_orders (warehouse_id);

CREATE INDEX "IX_sales_quote_lines_quote_id" ON sales_quote_lines (quote_id);

CREATE INDEX "IX_sales_quote_lines_variant_id" ON sales_quote_lines (variant_id);

CREATE UNIQUE INDEX "IX_sales_quotes_company_id_number" ON sales_quotes (company_id, number);

CREATE INDEX "IX_sales_quotes_company_id_status" ON sales_quotes (company_id, status);

CREATE INDEX "IX_sales_quotes_created_by_id" ON sales_quotes (created_by_id);

CREATE INDEX "IX_sales_quotes_opportunity_id" ON sales_quotes (opportunity_id);

CREATE INDEX "IX_sales_quotes_partner_id" ON sales_quotes (partner_id);

CREATE INDEX "IX_sales_return_lines_invoice_line_id" ON sales_return_lines (invoice_line_id);

CREATE INDEX "IX_sales_return_lines_return_id" ON sales_return_lines (return_id);

CREATE INDEX "IX_sales_return_lines_variant_id" ON sales_return_lines (variant_id);

CREATE UNIQUE INDEX "IX_sales_returns_company_id_number" ON sales_returns (company_id, number);

CREATE INDEX "IX_sales_returns_created_by_id" ON sales_returns (created_by_id);

CREATE INDEX "IX_sales_returns_invoice_id" ON sales_returns (invoice_id);

CREATE INDEX "IX_sales_returns_partner_id" ON sales_returns (partner_id);

CREATE UNIQUE INDEX "IX_sales_returns_stock_movement_id" ON sales_returns (stock_movement_id);

CREATE INDEX "IX_sales_returns_warehouse_id" ON sales_returns (warehouse_id);

CREATE INDEX "IX_serial_numbers_company_id_warehouse_id_status" ON serial_numbers (company_id, warehouse_id, status);

CREATE UNIQUE INDEX "IX_serial_numbers_variant_id_serial_no" ON serial_numbers (variant_id, serial_no);

CREATE INDEX "IX_serial_numbers_warehouse_id" ON serial_numbers (warehouse_id);

CREATE UNIQUE INDEX "IX_settings_company_id_key" ON settings (company_id, key);

CREATE INDEX "IX_shift_assignments_company_id_date" ON shift_assignments (company_id, date);

CREATE UNIQUE INDEX "IX_shift_assignments_employee_id_date" ON shift_assignments (employee_id, date);

CREATE INDEX "IX_shift_assignments_shift_id" ON shift_assignments (shift_id);

CREATE UNIQUE INDEX "IX_shifts_company_id_code" ON shifts (company_id, code);

CREATE INDEX "IX_stock_balances_company_id_variant_id" ON stock_balances (company_id, variant_id);

CREATE INDEX "IX_stock_balances_variant_id" ON stock_balances (variant_id);

CREATE UNIQUE INDEX "IX_stock_balances_warehouse_id_variant_id" ON stock_balances (warehouse_id, variant_id);

CREATE INDEX "IX_stock_count_lines_count_id" ON stock_count_lines (count_id);

CREATE INDEX "IX_stock_count_lines_lot_id" ON stock_count_lines (lot_id);

CREATE INDEX "IX_stock_count_lines_variant_id" ON stock_count_lines (variant_id);

CREATE UNIQUE INDEX "IX_stock_counts_adjustment_id" ON stock_counts (adjustment_id);

CREATE UNIQUE INDEX "IX_stock_counts_company_id_number" ON stock_counts (company_id, number);

CREATE INDEX "IX_stock_counts_created_by_id" ON stock_counts (created_by_id);

CREATE INDEX "IX_stock_counts_warehouse_id" ON stock_counts (warehouse_id);

CREATE INDEX "IX_stock_lots_company_id" ON stock_lots (company_id);

CREATE UNIQUE INDEX "IX_stock_lots_variant_id_lot_no" ON stock_lots (variant_id, lot_no);

CREATE INDEX "IX_stock_movement_lines_lot_id" ON stock_movement_lines (lot_id);

CREATE INDEX "IX_stock_movement_lines_movement_id" ON stock_movement_lines (movement_id);

CREATE INDEX "IX_stock_movement_lines_variant_id" ON stock_movement_lines (variant_id);

CREATE INDEX "IX_stock_movement_serials_serial_id" ON stock_movement_serials (serial_id);

CREATE INDEX "IX_stock_movements_company_id_movement_date" ON stock_movements (company_id, movement_date);

CREATE UNIQUE INDEX "IX_stock_movements_company_id_number" ON stock_movements (company_id, number);

CREATE INDEX "IX_stock_movements_created_by_id" ON stock_movements (created_by_id);

CREATE INDEX "IX_stock_movements_source_warehouse_id" ON stock_movements (source_warehouse_id);

CREATE INDEX "IX_stock_movements_target_warehouse_id" ON stock_movements (target_warehouse_id);

CREATE INDEX "IX_stock_reservations_company_id" ON stock_reservations (company_id);

CREATE UNIQUE INDEX "IX_stock_reservations_order_line_id" ON stock_reservations (order_line_id);

CREATE INDEX "IX_stock_reservations_variant_id" ON stock_reservations (variant_id);

CREATE INDEX "IX_stock_reservations_warehouse_id_variant_id" ON stock_reservations (warehouse_id, variant_id);

CREATE INDEX "IX_stock_transactions_company_id_variant_id_seq" ON stock_transactions (company_id, variant_id, seq);

CREATE INDEX "IX_stock_transactions_line_id" ON stock_transactions (line_id);

CREATE INDEX "IX_stock_transactions_lot_id" ON stock_transactions (lot_id);

CREATE INDEX "IX_stock_transactions_movement_id" ON stock_transactions (movement_id);

CREATE INDEX "IX_stock_transactions_variant_id" ON stock_transactions (variant_id);

CREATE INDEX "IX_stock_transactions_warehouse_id_variant_id" ON stock_transactions (warehouse_id, variant_id);

CREATE INDEX "IX_supplier_quote_lines_quote_id" ON supplier_quote_lines (quote_id);

CREATE INDEX "IX_supplier_quote_lines_request_line_id" ON supplier_quote_lines (request_line_id);

CREATE INDEX "IX_supplier_quote_lines_variant_id" ON supplier_quote_lines (variant_id);

CREATE UNIQUE INDEX "IX_supplier_quotes_company_id_number" ON supplier_quotes (company_id, number);

CREATE INDEX "IX_supplier_quotes_created_by_id" ON supplier_quotes (created_by_id);

CREATE INDEX "IX_supplier_quotes_partner_id" ON supplier_quotes (partner_id);

CREATE INDEX "IX_supplier_quotes_request_id" ON supplier_quotes (request_id);

CREATE UNIQUE INDEX "IX_tax_rates_company_id_code" ON tax_rates (company_id, code);

CREATE UNIQUE INDEX "IX_ticket_categories_company_id_name" ON ticket_categories (company_id, name);

CREATE INDEX "IX_ticket_categories_default_assignee_id" ON ticket_categories (default_assignee_id);

CREATE INDEX "IX_ticket_comments_author_id" ON ticket_comments (author_id);

CREATE INDEX "IX_ticket_comments_ticket_id_created_at" ON ticket_comments (ticket_id, created_at);

CREATE INDEX "IX_ticket_events_actor_id" ON ticket_events (actor_id);

CREATE INDEX "IX_ticket_events_ticket_id_created_at" ON ticket_events (ticket_id, created_at);

CREATE UNIQUE INDEX "IX_ticket_sla_policies_company_id_priority" ON ticket_sla_policies (company_id, priority);

CREATE INDEX "IX_tickets_assignee_id" ON tickets (assignee_id);

CREATE INDEX "IX_tickets_category_id" ON tickets (category_id);

CREATE UNIQUE INDEX "IX_tickets_company_id_number" ON tickets (company_id, number);

CREATE INDEX "IX_tickets_company_id_status" ON tickets (company_id, status);

CREATE INDEX "IX_tickets_requester_id" ON tickets (requester_id);

CREATE INDEX "IX_time_entries_company_id_work_date" ON time_entries (company_id, work_date);

CREATE INDEX "IX_time_entries_employee_id_work_date" ON time_entries (employee_id, work_date);

CREATE INDEX "IX_time_entries_project_id_work_date" ON time_entries (project_id, work_date);

CREATE INDEX "IX_time_entries_task_id" ON time_entries (task_id);

CREATE UNIQUE INDEX "IX_training_courses_company_id_code" ON training_courses (company_id, code);

CREATE INDEX "IX_training_enrollments_employee_id" ON training_enrollments (employee_id);

CREATE UNIQUE INDEX "IX_training_enrollments_session_id_employee_id" ON training_enrollments (session_id, employee_id);

CREATE INDEX "IX_training_sessions_company_id_starts_at" ON training_sessions (company_id, starts_at);

CREATE INDEX "IX_training_sessions_course_id" ON training_sessions (course_id);

CREATE INDEX "IX_training_sessions_created_by_id" ON training_sessions (created_by_id);

CREATE UNIQUE INDEX "IX_treasury_accounts_company_id_code" ON treasury_accounts (company_id, code);

CREATE INDEX "IX_treasury_transactions_account_id" ON treasury_transactions (account_id);

CREATE INDEX "IX_treasury_transactions_company_id_account_id_seq" ON treasury_transactions (company_id, account_id, seq);

CREATE INDEX "IX_treasury_transactions_created_by_id" ON treasury_transactions (created_by_id);

CREATE INDEX "IX_treasury_transactions_document_type_document_id" ON treasury_transactions (document_type, document_id);

CREATE UNIQUE INDEX "IX_treasury_transactions_reverses_id" ON treasury_transactions (reverses_id);

CREATE UNIQUE INDEX "IX_units_company_id_code" ON units (company_id, code);

CREATE INDEX "IX_user_companies_company_id" ON user_companies (company_id);

CREATE INDEX "IX_user_companies_default_branch_id" ON user_companies (default_branch_id);

CREATE UNIQUE INDEX "IX_user_companies_user_id_company_id" ON user_companies (user_id, company_id);

CREATE INDEX "IX_user_roles_company_id" ON user_roles (company_id);

CREATE INDEX "IX_user_roles_role_id" ON user_roles (role_id);

CREATE INDEX "IX_user_roles_user_id_company_id" ON user_roles (user_id, company_id);

CREATE UNIQUE INDEX "IX_user_roles_user_id_role_id_company_id" ON user_roles (user_id, role_id, company_id);

CREATE UNIQUE INDEX "IX_users_email" ON users (email);

CREATE INDEX "IX_vehicle_assignments_created_by_id" ON vehicle_assignments (created_by_id);

CREATE INDEX "IX_vehicle_assignments_employee_id_end_date" ON vehicle_assignments (employee_id, end_date);

CREATE INDEX "IX_vehicle_assignments_vehicle_id_start_date" ON vehicle_assignments (vehicle_id, start_date);

CREATE INDEX "IX_vehicle_documents_company_id_end_date" ON vehicle_documents (company_id, end_date);

CREATE INDEX "IX_vehicle_documents_created_by_id" ON vehicle_documents (created_by_id);

CREATE INDEX "IX_vehicle_documents_vehicle_id_type_end_date" ON vehicle_documents (vehicle_id, type, end_date);

CREATE INDEX "IX_vehicle_fuel_entries_company_id_entry_date" ON vehicle_fuel_entries (company_id, entry_date);

CREATE INDEX "IX_vehicle_fuel_entries_created_by_id" ON vehicle_fuel_entries (created_by_id);

CREATE INDEX "IX_vehicle_fuel_entries_employee_id" ON vehicle_fuel_entries (employee_id);

CREATE INDEX "IX_vehicle_fuel_entries_vehicle_id_entry_date" ON vehicle_fuel_entries (vehicle_id, entry_date);

CREATE INDEX "IX_vehicle_odometer_readings_created_by_id" ON vehicle_odometer_readings (created_by_id);

CREATE UNIQUE INDEX "IX_vehicle_odometer_readings_fuel_entry_id" ON vehicle_odometer_readings (fuel_entry_id);

CREATE UNIQUE INDEX "IX_vehicle_odometer_readings_service_id" ON vehicle_odometer_readings (service_id);

CREATE INDEX "IX_vehicle_odometer_readings_vehicle_id_reading_date" ON vehicle_odometer_readings (vehicle_id, reading_date);

CREATE INDEX "IX_vehicle_services_company_id_service_date" ON vehicle_services (company_id, service_date);

CREATE INDEX "IX_vehicle_services_created_by_id" ON vehicle_services (created_by_id);

CREATE INDEX "IX_vehicle_services_partner_id" ON vehicle_services (partner_id);

CREATE INDEX "IX_vehicle_services_vehicle_id_service_date" ON vehicle_services (vehicle_id, service_date);

CREATE UNIQUE INDEX "IX_vehicles_company_id_plate" ON vehicles (company_id, plate);

CREATE INDEX "IX_vehicles_company_id_status" ON vehicles (company_id, status);

CREATE INDEX "IX_vehicles_created_by_id" ON vehicles (created_by_id);

CREATE INDEX "IX_vehicles_department_id" ON vehicles (department_id);

CREATE INDEX "IX_warehouses_branch_id" ON warehouses (branch_id);

CREATE UNIQUE INDEX "IX_warehouses_company_id_code" ON warehouses (company_id, code);

CREATE INDEX "IX_work_order_components_variant_id" ON work_order_components (variant_id);

CREATE UNIQUE INDEX "IX_work_order_components_work_order_id_variant_id" ON work_order_components (work_order_id, variant_id);

CREATE INDEX "IX_work_orders_bom_id" ON work_orders (bom_id);

CREATE UNIQUE INDEX "IX_work_orders_company_id_number" ON work_orders (company_id, number);

CREATE INDEX "IX_work_orders_company_id_status" ON work_orders (company_id, status);

CREATE INDEX "IX_work_orders_created_by_id" ON work_orders (created_by_id);

CREATE INDEX "IX_work_orders_source_warehouse_id" ON work_orders (source_warehouse_id);

CREATE INDEX "IX_work_orders_target_warehouse_id" ON work_orders (target_warehouse_id);

CREATE INDEX "IX_work_orders_variant_id" ON work_orders (variant_id);

CREATE INDEX "IX_workflow_actions_instance_id" ON workflow_actions (instance_id);

CREATE UNIQUE INDEX "IX_workflow_definitions_company_id_code" ON workflow_definitions (company_id, code);

CREATE INDEX "IX_workflow_instances_company_id_status" ON workflow_instances (company_id, status);

CREATE INDEX "IX_workflow_instances_definition_id" ON workflow_instances (definition_id);

CREATE INDEX "IX_workflow_instances_entity_type_entity_id" ON workflow_instances (entity_type, entity_id);

CREATE INDEX "IX_workflow_steps_approver_role_id" ON workflow_steps (approver_role_id);

CREATE UNIQUE INDEX "IX_workflow_steps_definition_id_order" ON workflow_steps (definition_id, "order");

ALTER TABLE application_activities ADD CONSTRAINT "FK_application_activities_job_applications_application_id" FOREIGN KEY (application_id) REFERENCES job_applications (id) ON DELETE CASCADE;

ALTER TABLE asset_assignments ADD CONSTRAINT "FK_asset_assignments_departments_department_id" FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL;

ALTER TABLE asset_assignments ADD CONSTRAINT "FK_asset_assignments_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE SET NULL;

ALTER TABLE attendance_records ADD CONSTRAINT "FK_attendance_records_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE;

ALTER TABLE budget_alerts ADD CONSTRAINT "FK_budget_alerts_budgets_budget_id" FOREIGN KEY (budget_id) REFERENCES budgets (id) ON DELETE CASCADE;

ALTER TABLE budget_entries ADD CONSTRAINT "FK_budget_entries_departments_department_id" FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL;

ALTER TABLE budget_events ADD CONSTRAINT "FK_budget_events_budgets_budget_id" FOREIGN KEY (budget_id) REFERENCES budgets (id) ON DELETE CASCADE;

ALTER TABLE budget_lines ADD CONSTRAINT "FK_budget_lines_budgets_budget_id" FOREIGN KEY (budget_id) REFERENCES budgets (id) ON DELETE CASCADE;

ALTER TABLE budgets ADD CONSTRAINT "FK_budgets_departments_department_id" FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL;

ALTER TABLE candidates ADD CONSTRAINT "FK_candidates_employees_referred_by_id" FOREIGN KEY (referred_by_id) REFERENCES employees (id) ON DELETE SET NULL;

ALTER TABLE competency_ratings ADD CONSTRAINT "FK_competency_ratings_performance_reviews_review_id" FOREIGN KEY (review_id) REFERENCES performance_reviews (id) ON DELETE CASCADE;

ALTER TABLE contract_events ADD CONSTRAINT "FK_contract_events_contracts_contract_id" FOREIGN KEY (contract_id) REFERENCES contracts (id) ON DELETE CASCADE;

ALTER TABLE contracts ADD CONSTRAINT "FK_contracts_employees_employee_id" FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE SET NULL;

ALTER TABLE departments ADD CONSTRAINT "FK_departments_employees_manager_id" FOREIGN KEY (manager_id) REFERENCES employees (id) ON DELETE SET NULL;

