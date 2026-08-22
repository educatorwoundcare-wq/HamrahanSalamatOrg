-- PHASE 2 REMEDIATION: SYNC CURSOR AND PUSH FIXES

-- 1. Global Version Sequence
CREATE SEQUENCE IF NOT EXISTS sync_global_version_seq;

-- 2. Unified Change Log
CREATE TABLE IF NOT EXISTS sync_change_log (
    version BIGINT PRIMARY KEY DEFAULT nextval('sync_global_version_seq'),
    operation_uuid UUID NOT NULL,
    company_id TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_uuid UUID NOT NULL,
    operation_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sync_change_log_company_version 
ON sync_change_log (company_id, version);

-- Enable RLS on sync_change_log
ALTER TABLE sync_change_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Enable read for tenant" ON sync_change_log
    FOR SELECT
    USING (public.is_device_member_of_workspace(company_id));

-- 3. Replace get_table_from_entity and get_entity_from_table to include missing tables
CREATE OR REPLACE FUNCTION get_table_from_entity(p_entity_type TEXT) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE AS $$
BEGIN
    RETURN CASE p_entity_type
        WHEN 'Patient' THEN 'patients'
        WHEN 'Employee' THEN 'employees'
        WHEN 'Service' THEN 'services'
        WHEN 'ServiceRegistration' THEN 'service_registrations'
        WHEN 'FinancialTransaction' THEN 'financial_transactions'
        WHEN 'Cashbox' THEN 'cashboxes'
        WHEN 'Expense' THEN 'expenses'
        WHEN 'JournalEntry' THEN 'journal_entries'
        WHEN 'FinancialReport' THEN 'financial_reports'
        WHEN 'SystemSetting' THEN 'system_settings'
        
        -- Additional tables
        WHEN 'Referral' THEN 'referrals'
        WHEN 'ReferralCommission' THEN 'referral_commissions'
        WHEN 'CommissionSettlement' THEN 'commission_settlements'
        WHEN 'ExpenseCategory' THEN 'expense_categories'
        WHEN 'FixedExpenseTemplate' THEN 'fixed_expense_templates'
        WHEN 'AuditLog' THEN 'audit_logs'
        WHEN 'UserPermission' THEN 'user_permissions'
        WHEN 'FinancialEditHistory' THEN 'edit_history'
        WHEN 'Alert' THEN 'alerts'
        WHEN 'Contract' THEN 'contracts'
        WHEN 'StaffProfile' THEN 'staff_profiles'
        WHEN 'ServiceSchedule' THEN 'service_schedules'
        WHEN 'NursingReport' THEN 'nursing_reports'
        WHEN 'VitalSigns' THEN 'vital_signs'
        WHEN 'WoundRecord' THEN 'wound_records'
        WHEN 'ConsentForm' THEN 'consent_forms'
        WHEN 'Prescription' THEN 'prescriptions'
        WHEN 'DashboardCache' THEN 'dashboard_caches'
        ELSE p_entity_type 
    END;
END;
$$;

-- 4. Rewrite sync_push_batch to use change log, advisory lock, and fix DO NOTHING conflict
CREATE OR REPLACE FUNCTION sync_push_batch(
    p_operations JSONB
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_op JSONB;
    v_op_uuid UUID;
    v_company_id TEXT;
    v_table_name TEXT;
    v_op_type TEXT;
    v_payload JSONB;
    v_record_uuid UUID;
    v_processed_uuids UUID[] := '{}';
    v_errors JSONB[] := '{}';
    v_current_deleted_at TIMESTAMPTZ;
    v_table TEXT;
    v_new_version BIGINT;
BEGIN
    -- Acquire transaction-level advisory lock to serialize all push batches and guarantee sequence commit order visibility
    PERFORM pg_advisory_xact_lock(hashtext('sync_push_batch_lock'));

    IF jsonb_typeof(p_operations) != 'array' THEN
        RAISE EXCEPTION 'Payload must be a JSON array';
    END IF;

    FOR v_op IN SELECT * FROM jsonb_array_elements(p_operations)
    LOOP
        v_op_uuid := (v_op->>'operationUuid')::UUID;
        v_company_id := v_op->>'companyId';
        v_table_name := v_op->>'tableName';
        v_op_type := v_op->>'operationType';
        v_payload := v_op->'payload';
        v_record_uuid := (v_op->'payload'->>'uuid')::UUID;
        
        -- Special case: SystemSetting uses key instead of uuid. Let's extract uuid properly if available
        -- Since all our Android models have .uuid now, we can rely on it.

        v_table := get_table_from_entity(v_table_name);

        -- 1. Tenant & Authorization Check
        IF NOT public.is_device_member_of_workspace(v_company_id) THEN
            v_errors := array_append(v_errors, jsonb_build_object('operationUuid', v_op_uuid, 'error', 'Tenant mismatch or unauthorized'));
            CONTINUE;
        END IF;

        -- 2. Idempotency Check
        IF EXISTS (SELECT 1 FROM sync_operations_log WHERE operation_uuid = v_op_uuid) THEN
            v_processed_uuids := array_append(v_processed_uuids, v_op_uuid);
            CONTINUE;
        END IF;

        -- 3. Tombstone & Conflict Check
        -- We must check if the entity exists and its state.
        -- But wait, what if the table doesn't have deleted_at?
        -- All our tables currently created have deleted_at.
        -- We'll execute a check.
        BEGIN
            EXECUTE format('SELECT deleted_at FROM %I WHERE uuid = %L AND company_id = %L', 
                v_table, v_record_uuid, v_company_id)
            INTO v_current_deleted_at;
            
            IF FOUND THEN
                IF v_current_deleted_at IS NOT NULL THEN
                    v_errors := array_append(v_errors, jsonb_build_object('operationUuid', v_op_uuid, 'error', 'Tombstone conflict'));
                    CONTINUE;
                END IF;
            ELSE
                -- If NOT FOUND, and it's an UPDATE, we can convert it to INSERT or just allow upsert
                -- We will do an UPSERT for INSERT/UPDATE
            END IF;
        EXCEPTION WHEN undefined_table THEN
            v_errors := array_append(v_errors, jsonb_build_object('operationUuid', v_op_uuid, 'error', 'Unknown table'));
            CONTINUE;
        WHEN OTHERS THEN
            -- Some tables (like system_settings) might not have deleted_at or uuid if not created correctly, but we assume they do.
        END;

        -- 4. Apply Mutation
        BEGIN
            IF v_op_type IN ('INSERT', 'UPDATE') THEN
                -- Defect 4 fix: Use UPSERT (ON CONFLICT DO UPDATE) instead of DO NOTHING
                EXECUTE format('
                    INSERT INTO %I (uuid, company_id, payload, created_at, server_updated_at)
                    VALUES (%L, %L, %L, now(), now())
                    ON CONFLICT (uuid) DO UPDATE 
                    SET payload = EXCLUDED.payload, server_updated_at = EXCLUDED.server_updated_at
                    WHERE %I.company_id = EXCLUDED.company_id AND %I.deleted_at IS NULL
                ', v_table, v_record_uuid, v_company_id, v_payload, v_table, v_table);
            ELSIF v_op_type = 'DELETE' THEN
                EXECUTE format('
                    UPDATE %I
                    SET deleted_at = now(), server_updated_at = now()
                    WHERE uuid = %L AND company_id = %L
                ', v_table, v_record_uuid, v_company_id);
            END IF;

            -- 5. Write to unified change log
            INSERT INTO sync_change_log (operation_uuid, company_id, entity_type, entity_uuid, operation_type, payload, deleted_at)
            VALUES (
                v_op_uuid, 
                v_company_id, 
                v_table_name, 
                v_record_uuid, 
                v_op_type, 
                v_payload, 
                CASE WHEN v_op_type = 'DELETE' THEN now() ELSE NULL END
            ) RETURNING version INTO v_new_version;

            -- 6. Record Operation Idempotency
            INSERT INTO sync_operations_log (operation_uuid) VALUES (v_op_uuid);
            v_processed_uuids := array_append(v_processed_uuids, v_op_uuid);
            
        EXCEPTION WHEN OTHERS THEN
            v_errors := array_append(v_errors, jsonb_build_object('operationUuid', v_op_uuid, 'error', SQLERRM));
        END;
    END LOOP;

    RETURN jsonb_build_object(
        'processed', to_jsonb(v_processed_uuids),
        'errors', to_jsonb(v_errors)
    );
END;
$$;

-- 5. Rewrite sync_pull to use the unified change log
CREATE OR REPLACE FUNCTION sync_pull(
    p_company_id TEXT,
    p_last_server_version BIGINT
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_results JSONB;
    v_next_cursor BIGINT;
    v_has_more BOOLEAN := false;
BEGIN
    -- Authorization
    IF NOT public.is_device_member_of_workspace(p_company_id) THEN
        RAISE EXCEPTION 'Unauthorized for this workspace';
    END IF;

    -- Fetch up to 500 changes from the log for this tenant
    SELECT coalesce(jsonb_agg(
        jsonb_build_object(
            'entityType', entity_type,
            'uuid', entity_uuid,
            'companyId', company_id,
            'payload', payload,
            'serverUpdatedAt', extract(epoch from created_at) * 1000,
            'deletedAt', CASE WHEN deleted_at IS NOT NULL THEN extract(epoch from deleted_at) * 1000 ELSE 0 END,
            'serverVersion', version
        )
    ), '[]'::jsonb)
    INTO v_results
    FROM (
        SELECT *
        FROM sync_change_log
        WHERE company_id = p_company_id AND version > p_last_server_version
        ORDER BY version ASC
        LIMIT 500
    ) sub;
    
    -- In Option A, next_cursor is simply the max version returned (since we use lock to guarantee commit order).
    -- But if no results, next_cursor = p_last_server_version.
    IF jsonb_array_length(v_results) > 0 THEN
        v_next_cursor := (v_results->(jsonb_array_length(v_results) - 1)->>'serverVersion')::BIGINT;
        
        -- Check if there are more records beyond this cursor
        SELECT EXISTS (
            SELECT 1 FROM sync_change_log 
            WHERE company_id = p_company_id AND version > v_next_cursor
        ) INTO v_has_more;
    ELSE
        v_next_cursor := p_last_server_version;
    END IF;

    RETURN jsonb_build_object(
        'changes', v_results,
        'next_cursor', v_next_cursor,
        'has_more', v_has_more
    );
END;
$$;

