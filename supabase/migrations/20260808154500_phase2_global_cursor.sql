DROP TABLE IF EXISTS sync_change_log CASCADE;
-- Create sync_change_log
CREATE TABLE IF NOT EXISTS sync_change_log (
    change_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_uuid UUID NOT NULL,
    operation_type TEXT NOT NULL,
    payload JSONB,
    server_updated_at TIMESTAMPTZ DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

-- Index for pulling
CREATE INDEX IF NOT EXISTS idx_sync_change_log_pull ON sync_change_log(company_id, change_id);

-- Update sync_push_batch to write to change log
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
                -- DO UPDATE for idempotency to avoid DO NOTHING silently failing on conflicts
                EXECUTE format('
                    INSERT INTO %I (uuid, company_id, payload, created_at, server_updated_at)
                    VALUES (%L, %L, %L, now(), now())
                    ON CONFLICT (uuid) DO UPDATE 
                    SET payload = EXCLUDED.payload, server_updated_at = now()
                    WHERE %I.company_id = EXCLUDED.company_id AND %I.deleted_at IS NULL
                ', get_table_from_entity(v_table_name), v_record_uuid, v_company_id, v_payload, get_table_from_entity(v_table_name), get_table_from_entity(v_table_name));
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
            
            -- 6. Write to Change Log
            INSERT INTO sync_change_log (company_id, entity_type, entity_uuid, operation_type, payload, deleted_at)
            VALUES (v_company_id, v_table_name, v_record_uuid, v_op_type, CASE WHEN v_op_type = 'DELETE' THEN NULL ELSE v_payload END, CASE WHEN v_op_type = 'DELETE' THEN now() ELSE NULL END);

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

-- Rewrite sync_pull to use sync_change_log
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

    SELECT 
        coalesce(jsonb_agg(
            jsonb_build_object(
                'entityType', entity_type,
                'uuid', entity_uuid,
                'companyId', company_id,
                'payload', payload,
                'serverUpdatedAt', extract(epoch from server_updated_at) * 1000,
                'deletedAt', extract(epoch from deleted_at) * 1000,
                'serverVersion', change_id
            )
        ), '[]'::jsonb),
        coalesce(max(change_id), p_last_server_version)
    INTO v_results, v_next_cursor
    FROM (
        SELECT * FROM sync_change_log 
        WHERE company_id = p_company_id AND change_id > p_last_server_version 
        ORDER BY change_id ASC
        LIMIT 1000
    ) sub;
    
    SELECT EXISTS(
        SELECT 1 FROM sync_change_log 
        WHERE company_id = p_company_id AND change_id > v_next_cursor
    ) INTO v_has_more;

    RETURN jsonb_build_object(
        'changes', v_results,
        'next_cursor', v_next_cursor,
        'has_more', v_has_more
    );
END;
$$;

-- Apply RLS to sync_change_log
ALTER TABLE sync_change_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can read their workspace change log"
ON sync_change_log FOR SELECT
TO authenticated
USING (public.is_device_member_of_workspace(company_id));
