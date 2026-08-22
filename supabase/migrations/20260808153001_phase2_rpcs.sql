-- PHASE 2E: Canonical Push RPC

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
BEGIN
    -- Verify input is an array
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

        -- 3. Tombstone Check (if UPDATE)
        IF v_op_type = 'UPDATE' THEN
            -- Check if record is tombstoned
            EXECUTE format('SELECT deleted_at FROM %I WHERE uuid = %L AND company_id = %L', 
                get_table_from_entity(v_table_name), v_record_uuid, v_company_id)
            INTO v_current_deleted_at;

            IF v_current_deleted_at IS NOT NULL THEN
                v_errors := array_append(v_errors, jsonb_build_object('operationUuid', v_op_uuid, 'error', 'Tombstone conflict'));
                CONTINUE;
            END IF;
        END IF;

        -- 4. Apply Mutation
        BEGIN
            IF v_op_type = 'INSERT' THEN
                EXECUTE format('
                    INSERT INTO %I (uuid, company_id, payload, created_at, server_updated_at)
                    VALUES (%L, %L, %L, now(), now())
                    ON CONFLICT (uuid) DO NOTHING
                ', get_table_from_entity(v_table_name), v_record_uuid, v_company_id, v_payload);

            ELSIF v_op_type = 'UPDATE' THEN
                EXECUTE format('
                    UPDATE %I
                    SET payload = %L, server_updated_at = now()
                    WHERE uuid = %L AND company_id = %L AND deleted_at IS NULL
                ', get_table_from_entity(v_table_name), v_payload, v_record_uuid, v_company_id);

            ELSIF v_op_type = 'DELETE' THEN
                EXECUTE format('
                    UPDATE %I
                    SET deleted_at = now(), server_updated_at = now()
                    WHERE uuid = %L AND company_id = %L
                ', get_table_from_entity(v_table_name), v_record_uuid, v_company_id);
            END IF;

            -- 5. Record Operation
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

-- Helper to map Kotlin Entity name to table name
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
        ELSE p_entity_type -- Fallback if same, though risky. In practice you'd map all.
    END;
END;
$$;

-- PHASE 2F: Canonical Pull RPC

CREATE OR REPLACE FUNCTION sync_pull(
    p_company_id TEXT,
    p_last_server_version BIGINT
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_results JSONB := '[]'::jsonb;
    t TEXT;
    v_rows JSONB;
BEGIN
    -- Authorization
    IF NOT public.is_device_member_of_workspace(p_company_id) THEN
        RAISE EXCEPTION 'Unauthorized for this workspace';
    END IF;

    FOR t IN 
        SELECT table_name FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name IN (
            'patients', 'employees', 'services', 'service_registrations',
            'financial_transactions', 'cashboxes', 'expenses', 'journal_entries',
            'financial_reports', 'system_settings'
        )
    LOOP
        EXECUTE format('
            SELECT coalesce(jsonb_agg(
                jsonb_build_object(
                    ''entityType'', get_entity_from_table(%L),
                    ''uuid'', uuid,
                    ''companyId'', company_id,
                    ''payload'', payload,
                    ''serverUpdatedAt'', extract(epoch from server_updated_at) * 1000,
                    ''deletedAt'', extract(epoch from deleted_at) * 1000,
                    ''serverVersion'', server_version
                )
            ), ''[]''::jsonb)
            FROM %I
            WHERE company_id = %L AND server_version > %L
        ', t, t, p_company_id, p_last_server_version) INTO v_rows;

        v_results := v_results || v_rows;
    END LOOP;

    RETURN v_results;
END;
$$;

CREATE OR REPLACE FUNCTION get_entity_from_table(p_table_name TEXT) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE AS $$
BEGIN
    RETURN CASE p_table_name
        WHEN 'patients' THEN 'Patient'
        WHEN 'employees' THEN 'Employee'
        WHEN 'services' THEN 'Service'
        WHEN 'service_registrations' THEN 'ServiceRegistration'
        WHEN 'financial_transactions' THEN 'FinancialTransaction'
        WHEN 'cashboxes' THEN 'Cashbox'
        WHEN 'expenses' THEN 'Expense'
        WHEN 'journal_entries' THEN 'JournalEntry'
        WHEN 'financial_reports' THEN 'FinancialReport'
        WHEN 'system_settings' THEN 'SystemSetting'
        ELSE p_table_name
    END;
END;
$$;
