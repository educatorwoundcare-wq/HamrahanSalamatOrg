-- ====================================================================================
-- 1. BASE SCHEMA & MULTI-TENANCY
-- ====================================================================================

-- Drop existing tables and functions to allow clean re-runs
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
DROP FUNCTION IF EXISTS public.handle_new_user() CASCADE;
DROP FUNCTION IF EXISTS public.get_tenant_id() CASCADE;
DROP FUNCTION IF EXISTS public.update_modified_column() CASCADE;
DROP FUNCTION IF EXISTS public.push_sync_batch(JSONB);

DROP TABLE IF EXISTS public.sync_queue CASCADE;
DROP TABLE IF EXISTS public.audit_logs CASCADE;
DROP TABLE IF EXISTS public.expenses CASCADE;
DROP TABLE IF EXISTS public.financial_transactions CASCADE;
DROP TABLE IF EXISTS public.services CASCADE;
DROP TABLE IF EXISTS public.employees CASCADE;
DROP TABLE IF EXISTS public.patients CASCADE;
DROP TABLE IF EXISTS public.user_profiles CASCADE;

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Base Tables
CREATE TABLE public.patients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    national_id VARCHAR(50),
    phone_number VARCHAR(50),
    address TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE public.employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(100),
    phone_number VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE public.services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    base_price DECIMAL(12, 2) DEFAULT 0.0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE public.financial_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    transaction_type VARCHAR(50), -- e.g., 'INCOME', 'EXPENSE'
    description TEXT,
    transaction_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE public.expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    category VARCHAR(100),
    title VARCHAR(255),
    expense_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE public.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID,
    device_id VARCHAR(255),
    action VARCHAR(100),
    affected_module VARCHAR(100),
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Sync Queue Table for Server-side buffering of CDC payloads
CREATE TABLE public.sync_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    table_name VARCHAR(100) NOT NULL,
    record_id UUID NOT NULL,
    operation_type VARCHAR(50) NOT NULL, -- 'INSERT', 'UPDATE', 'DELETE'
    payload JSONB,
    processed_status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE
);

-- Indexing for performance
CREATE INDEX idx_patients_tenant ON public.patients(tenant_id);
CREATE INDEX idx_employees_tenant ON public.employees(tenant_id);
CREATE INDEX idx_services_tenant ON public.services(tenant_id);
CREATE INDEX idx_financial_transactions_tenant ON public.financial_transactions(tenant_id);
CREATE INDEX idx_expenses_tenant ON public.expenses(tenant_id);
CREATE INDEX idx_audit_logs_tenant ON public.audit_logs(tenant_id);
CREATE INDEX idx_sync_queue_tenant ON public.sync_queue(tenant_id);

-- ====================================================================================
-- 2. SUPABASE ROW LEVEL SECURITY (RLS) & AUTH POLICIES
-- ====================================================================================

-- User Profiles for linking Auth Users to Tenants
CREATE TABLE public.user_profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    role VARCHAR(50) DEFAULT 'STAFF',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_user_profiles_tenant ON public.user_profiles(tenant_id);

-- Enable RLS on all tables
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.patients ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.employees ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.services ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.financial_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sync_queue ENABLE ROW LEVEL SECURITY;

-- Auth Trigger to auto-create user_profile on signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger AS $$
BEGIN
  -- Assuming tenant_id is passed in the raw_user_meta_data during signup
  -- Default to a new tenant if not provided
  INSERT INTO public.user_profiles (id, tenant_id, role)
  VALUES (
    NEW.id,
    COALESCE((NEW.raw_user_meta_data->>'tenant_id')::UUID, gen_random_uuid()),
    'ADMIN'
  );
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();

-- Helper function to get current user's tenant_id
CREATE OR REPLACE FUNCTION public.get_tenant_id() RETURNS UUID AS $$
  SELECT tenant_id FROM public.user_profiles WHERE id = auth.uid() LIMIT 1;
$$ LANGUAGE sql STABLE;

-- RLS Policies

-- user_profiles: users can only see profiles in their tenant
CREATE POLICY "Users view profiles in their tenant" ON public.user_profiles FOR SELECT USING (tenant_id = public.get_tenant_id());
CREATE POLICY "Users update their own profile" ON public.user_profiles FOR UPDATE USING (id = auth.uid());

-- Macro to create policies for tenant-isolated tables
DO $$
DECLARE
    table_name text;
    tables text[] := ARRAY['patients', 'employees', 'services', 'financial_transactions', 'expenses', 'audit_logs', 'sync_queue'];
BEGIN
    FOREACH table_name IN ARRAY tables LOOP
        EXECUTE format('CREATE POLICY "Tenant Isolation Policy for %I" ON public.%I FOR ALL USING (tenant_id = public.get_tenant_id()) WITH CHECK (tenant_id = public.get_tenant_id());', table_name, table_name);
    END LOOP;
END
$$;

-- ====================================================================================
-- 3. SYNC FUNCTIONS & CONFLICT RESOLUTION (LWW)
-- ====================================================================================

-- Trigger function to automatically update `updated_at` on modification
CREATE OR REPLACE FUNCTION public.update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to all tables
DO $$
DECLARE
    t text;
    tables text[] := ARRAY['patients', 'employees', 'services', 'financial_transactions', 'expenses'];
BEGIN
    FOREACH t IN ARRAY tables LOOP
        EXECUTE format('
            CREATE TRIGGER update_%I_modtime
            BEFORE UPDATE ON public.%I
            FOR EACH ROW
            EXECUTE PROCEDURE public.update_modified_column();
        ', t, t);
    END LOOP;
END
$$;

-- Function to process incoming sync batch from Android Client
CREATE OR REPLACE FUNCTION public.push_sync_batch(payload JSONB)
RETURNS JSONB AS $$
DECLARE
    item JSONB;
    t_name TEXT;
    r_id UUID;
    op_type TEXT;
    item_ts BIGINT;
    local_ts TIMESTAMP WITH TIME ZONE;
    server_ts TIMESTAMP WITH TIME ZONE;
    payload_data JSONB;
    success_count INT := 0;
    conflict_count INT := 0;
    current_tenant UUID;
BEGIN
    current_tenant := public.get_tenant_id();

    FOR item IN SELECT * FROM jsonb_array_elements(payload)
    LOOP
        t_name := item->>'table_name';
        r_id := (item->>'record_id')::UUID;
        op_type := item->>'operation_type';
        item_ts := (item->>'timestamp')::BIGINT;
        local_ts := to_timestamp(item_ts / 1000.0);
        payload_data := item->'payload';

        IF op_type = 'UPDATE' THEN
            -- Check Server timestamp dynamically
            EXECUTE format('SELECT updated_at FROM public.%I WHERE id = $1 AND tenant_id = $2', t_name)
            INTO server_ts
            USING r_id, current_tenant;

            IF server_ts IS NULL THEN
                -- Record not found on server
                conflict_count := conflict_count + 1;
                CONTINUE;
            END IF;

            -- Last-Write-Wins Check
            IF local_ts >= server_ts THEN
                success_count := success_count + 1;
            ELSE
                conflict_count := conflict_count + 1;
            END IF;
            
        ELSIF op_type = 'INSERT' THEN
            success_count := success_count + 1;
            
        ELSIF op_type = 'DELETE' THEN
            success_count := success_count + 1;
        END IF;

        -- Record the sync attempt in backend sync_queue for audit trail
        INSERT INTO public.sync_queue (tenant_id, table_name, record_id, operation_type, payload, processed_status, processed_at)
        VALUES (current_tenant, t_name, r_id, op_type, payload_data, CASE WHEN local_ts >= server_ts THEN 'COMPLETED' ELSE 'CONFLICT' END, NOW());
    END LOOP;

    RETURN jsonb_build_object('status', 'success', 'processed', success_count, 'conflicts', conflict_count);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
