-- PHASE 2D: Remote Relational Schema
-- Creating relational domain tables based on actual business columns.

-- 1. patients
CREATE TABLE patients (
    uuid UUID PRIMARY KEY,
    company_id TEXT NOT NULL,
    full_name TEXT NOT NULL,
    gender TEXT NOT NULL,
    age INT,
    phone TEXT,
    address TEXT,
    referral_source TEXT,
    referral_id INT, -- Local surrogate key, no FK
    status TEXT,
    registration_date BIGINT,
    notes TEXT,
    tags TEXT,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL,
    server_version BIGINT GENERATED ALWAYS AS IDENTITY
);

-- 2. employees
CREATE TABLE employees (
    uuid UUID PRIMARY KEY,
    company_id TEXT NOT NULL,
    full_name TEXT NOT NULL,
    role TEXT NOT NULL,
    phone TEXT,
    commission_rate REAL,
    status TEXT,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL,
    server_version BIGINT GENERATED ALWAYS AS IDENTITY
);

-- 3. services
CREATE TABLE services (
    uuid UUID PRIMARY KEY,
    company_id TEXT NOT NULL,
    name TEXT NOT NULL,
    base_price REAL,
    description TEXT,
    is_active BOOLEAN,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL,
    server_version BIGINT GENERATED ALWAYS AS IDENTITY
);

-- 4. service_registrations
CREATE TABLE service_registrations (
    uuid UUID PRIMARY KEY,
    company_id TEXT NOT NULL,
    patient_id INT, -- Local surrogate key, no FK
    service_id INT, -- Local surrogate key, no FK
    employee_id INT, -- Local surrogate key, no FK
    date BIGINT,
    discount REAL,
    final_price REAL,
    workflow_status TEXT,
    payment_status TEXT,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL,
    server_version BIGINT GENERATED ALWAYS AS IDENTITY
);

-- 5. financial_transactions
CREATE TABLE financial_transactions (
    uuid UUID PRIMARY KEY,
    company_id TEXT NOT NULL,
    type TEXT,
    amount REAL,
    date BIGINT,
    category TEXT,
    description TEXT,
    reference_id INT,
    cashbox_id INT,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL,
    server_version BIGINT GENERATED ALWAYS AS IDENTITY
);

-- 6. cashboxes
CREATE TABLE cashboxes (
    uuid UUID PRIMARY KEY,
    company_id TEXT NOT NULL,
    name TEXT NOT NULL,
    balance REAL,
    is_active BOOLEAN,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL,
    server_version BIGINT GENERATED ALWAYS AS IDENTITY
);

-- 7. expenses
CREATE TABLE expenses (
    uuid UUID PRIMARY KEY,
    company_id TEXT NOT NULL,
    title TEXT NOT NULL,
    amount REAL,
    date BIGINT,
    category TEXT,
    expense_type TEXT,
    description TEXT,
    payment_method TEXT,
    receipt_path TEXT,
    workflow_status TEXT,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL,
    server_version BIGINT GENERATED ALWAYS AS IDENTITY
);

-- 8. journal_entries
CREATE TABLE journal_entries (
    uuid UUID PRIMARY KEY,
    company_id TEXT NOT NULL,
    document_number TEXT,
    debit_account TEXT,
    credit_account TEXT,
    amount REAL,
    date BIGINT,
    reference TEXT,
    reference_id INT,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL,
    server_version BIGINT GENERATED ALWAYS AS IDENTITY
);

-- 9. financial_reports
CREATE TABLE financial_reports (
    uuid UUID PRIMARY KEY,
    company_id TEXT NOT NULL,
    title TEXT,
    start_date BIGINT,
    end_date BIGINT,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL,
    server_version BIGINT GENERATED ALWAYS AS IDENTITY
);

-- 10. system_settings
CREATE TABLE system_settings (
    uuid UUID PRIMARY KEY,
    company_id TEXT NOT NULL,
    key TEXT NOT NULL,
    value TEXT,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL,
    server_version BIGINT GENERATED ALWAYS AS IDENTITY
);

-- Other tables: just a generic domain_records table for the rest to avoid massive boilerplate if not explicitly requested, 
-- but the prompt says "Only create tables that are actually required by the application."
-- Let's create sync_operations_log as required by Phase 2.

-- 11. sync_operations_log
CREATE TABLE sync_operations_log (
    operation_uuid UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- We omit strict PostgreSQL foreign keys (e.g. from service_registrations to patients) 
-- because the client syncs local 'Int' surrogate keys in its JSON payload, which do not globally resolve without a mapping lookup on the server.
-- The client application permits temporarily incomplete offline graphs (e.g. syncing a registration before the patient is pulled is theoretically handled locally via conflict resolution).


-- Apply RLS and Policies
DO $$
DECLARE
    t text;
BEGIN
    FOR t IN 
        SELECT table_name FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name IN (
            'patients', 'employees', 'services', 'service_registrations',
            'financial_transactions', 'cashboxes', 'expenses', 'journal_entries',
            'financial_reports', 'system_settings'
        )
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY;', t);
        
        EXECUTE format('
            CREATE POLICY "Select %I" ON %I FOR SELECT 
            USING (public.is_device_member_of_workspace(company_id));
        ', t, t);
        
        EXECUTE format('
            CREATE POLICY "Insert %I" ON %I FOR INSERT 
            WITH CHECK (public.is_device_member_of_workspace(company_id));
        ', t, t);
        
        EXECUTE format('
            CREATE POLICY "Update %I" ON %I FOR UPDATE 
            USING (public.is_device_member_of_workspace(company_id))
            WITH CHECK (public.is_device_member_of_workspace(company_id));
        ', t, t);

        EXECUTE format('
            CREATE POLICY "Delete %I" ON %I FOR DELETE 
            USING (public.is_device_member_of_workspace(company_id));
        ', t, t);
    END LOOP;
END $$;

-- Enable RLS on sync_operations_log (no direct client access allowed)
ALTER TABLE sync_operations_log ENABLE ROW LEVEL SECURITY;
-- No policies on sync_operations_log means clients cannot select, insert, update, or delete. 
-- Only SECURITY DEFINER functions can access it.

