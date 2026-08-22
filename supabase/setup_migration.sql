-- ====================================================================================
-- SUPABASE MIGRATION SCHEMA (NOSQL TO RELATIONAL)
-- ====================================================================================

-- 1. WORKSPACES
CREATE TABLE IF NOT EXISTS public.workspaces (
    company_id TEXT PRIMARY KEY,
    sync_code TEXT UNIQUE NOT NULL,
    center_name TEXT,
    national_code TEXT,
    support_phone TEXT,
    center_address TEXT,
    created_timestamp BIGINT
);

-- 2. CONNECTED DEVICES
CREATE TABLE IF NOT EXISTS public.connected_devices (
    device_id TEXT PRIMARY KEY,
    company_id TEXT NOT NULL REFERENCES public.workspaces(company_id) ON DELETE CASCADE,
    device_name TEXT,
    device_type TEXT,
    app_version TEXT,
    last_online_time BIGINT,
    last_successful_sync BIGINT,
    status TEXT,
    uid TEXT,
    role TEXT,
    last_seen BIGINT,
    requested_role TEXT
);

-- 3. CLOUD RECORDS (Generic Sync Table)
CREATE TABLE IF NOT EXISTS public.cloud_records (
    id TEXT PRIMARY KEY,
    company_id TEXT NOT NULL REFERENCES public.workspaces(company_id) ON DELETE CASCADE,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    data_json TEXT NOT NULL,
    updated_timestamp BIGINT NOT NULL,
    last_modified_device_id TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL
);

-- Indexing for fast sync pulls
CREATE INDEX IF NOT EXISTS idx_cloud_records_company ON public.cloud_records(company_id);
CREATE INDEX IF NOT EXISTS idx_connected_devices_company ON public.connected_devices(company_id);

-- Disable RLS for now to ensure smooth migration (can be re-enabled later once Auth is fully mapped)
ALTER TABLE public.workspaces DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.connected_devices DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.cloud_records DISABLE ROW LEVEL SECURITY;

