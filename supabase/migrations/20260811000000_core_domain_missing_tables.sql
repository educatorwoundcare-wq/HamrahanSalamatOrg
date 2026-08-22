CREATE TABLE IF NOT EXISTS public.referrals (
    id UUID DEFAULT gen_random_uuid(),
    uuid UUID PRIMARY KEY,
    company_id TEXT NOT NULL,
    name TEXT NOT NULL,
    phone TEXT,
    description TEXT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    server_updated_at TIMESTAMPTZ DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    server_version BIGINT GENERATED ALWAYS AS IDENTITY
);

CREATE TABLE IF NOT EXISTS public.referral_commissions (
    id UUID DEFAULT gen_random_uuid(),
    uuid UUID PRIMARY KEY,
    company_id TEXT NOT NULL,
    referral_id UUID NOT NULL,
    patient_id UUID,
    service_registration_id UUID,
    amount NUMERIC,
    status TEXT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    server_updated_at TIMESTAMPTZ DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    server_version BIGINT GENERATED ALWAYS AS IDENTITY
);

CREATE TABLE IF NOT EXISTS public.commission_settlements (
    id UUID DEFAULT gen_random_uuid(),
    uuid UUID PRIMARY KEY,
    company_id TEXT NOT NULL,
    employee_id UUID,
    referral_id UUID,
    amount NUMERIC,
    status TEXT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    server_updated_at TIMESTAMPTZ DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    server_version BIGINT GENERATED ALWAYS AS IDENTITY
);

CREATE TABLE IF NOT EXISTS public.expense_categories (
    id UUID DEFAULT gen_random_uuid(),
    uuid UUID PRIMARY KEY,
    company_id TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    server_updated_at TIMESTAMPTZ DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    server_version BIGINT GENERATED ALWAYS AS IDENTITY
);

CREATE TABLE IF NOT EXISTS public.fixed_expense_templates (
    id UUID DEFAULT gen_random_uuid(),
    uuid UUID PRIMARY KEY,
    company_id TEXT NOT NULL,
    name TEXT NOT NULL,
    amount NUMERIC,
    category_id UUID,
    period TEXT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    server_updated_at TIMESTAMPTZ DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    server_version BIGINT GENERATED ALWAYS AS IDENTITY
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_referrals_company ON public.referrals(company_id);
CREATE INDEX IF NOT EXISTS idx_referrals_deleted_at ON public.referrals(deleted_at);

CREATE INDEX IF NOT EXISTS idx_referral_commissions_company ON public.referral_commissions(company_id);
CREATE INDEX IF NOT EXISTS idx_referral_commissions_referral ON public.referral_commissions(referral_id);
CREATE INDEX IF NOT EXISTS idx_referral_commissions_deleted_at ON public.referral_commissions(deleted_at);

CREATE INDEX IF NOT EXISTS idx_commission_settlements_company ON public.commission_settlements(company_id);
CREATE INDEX IF NOT EXISTS idx_commission_settlements_employee ON public.commission_settlements(employee_id);
CREATE INDEX IF NOT EXISTS idx_commission_settlements_deleted_at ON public.commission_settlements(deleted_at);

CREATE INDEX IF NOT EXISTS idx_expense_categories_company ON public.expense_categories(company_id);
CREATE INDEX IF NOT EXISTS idx_expense_categories_deleted_at ON public.expense_categories(deleted_at);

CREATE INDEX IF NOT EXISTS idx_fixed_expense_templates_company ON public.fixed_expense_templates(company_id);
CREATE INDEX IF NOT EXISTS idx_fixed_expense_templates_deleted_at ON public.fixed_expense_templates(deleted_at);

-- RLS
ALTER TABLE public.referrals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.referral_commissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.commission_settlements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.expense_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fixed_expense_templates ENABLE ROW LEVEL SECURITY;

DO $$ 
DECLARE 
    t TEXT;
BEGIN 
    FOR t IN 
        SELECT unnest(ARRAY[
            'referrals', 'referral_commissions', 'commission_settlements', 
            'expense_categories', 'fixed_expense_templates'
        ])
    LOOP
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
