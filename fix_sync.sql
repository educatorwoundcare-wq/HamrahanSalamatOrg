CREATE OR REPLACE FUNCTION convert_camel_to_snake(j jsonb) RETURNS jsonb AS $$
DECLARE
  k text;
  v jsonb;
  res jsonb := '{}'::jsonb;
BEGIN
  IF j IS NULL THEN RETURN NULL; END IF;
  FOR k, v IN SELECT * FROM jsonb_each(j) LOOP
    res := jsonb_set(res, ARRAY[lower(regexp_replace(k, '([a-z])([A-Z])', '\1_\2', 'g'))], v);
  END LOOP;
  RETURN res;
END;
$$ LANGUAGE plpgsql;

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
    v_payload_snake JSONB;
    v_record_uuid UUID;
    v_processed_uuids UUID[] := '{}';
    v_errors JSONB[] := '{}';
    v_current_deleted_at TIMESTAMPTZ;
    v_cols TEXT;
BEGIN
    IF jsonb_typeof(p_operations) != 'array' THEN
        RETURN jsonb_build_object('error', 'Input must be a JSON array of operations');
    END IF;

    FOR v_op IN SELECT * FROM jsonb_array_elements(p_operations) LOOP
        BEGIN
            v_op_uuid := (v_op->>'operationUuid')::UUID;
            v_company_id := v_op->>'companyId';
            v_table_name := v_op->>'tableName';
            v_op_type := v_op->>'operationType';
            v_payload := v_op->'payload';
            v_record_uuid := (v_payload->>'uuid')::UUID;

            -- Authorization Check
            IF NOT is_device_member_of_workspace(v_company_id) THEN
                v_errors := array_append(v_errors, jsonb_build_object('operationUuid', v_op_uuid, 'error', 'Tenant mismatch or unauthorized'));
                CONTINUE;
            END IF;

            -- Idempotency Check
            IF EXISTS (SELECT 1 FROM sync_operations_log WHERE operation_uuid = v_op_uuid) THEN
                v_processed_uuids := array_append(v_processed_uuids, v_op_uuid);
                CONTINUE;
            END IF;

            -- Tombstone check
            EXECUTE format('SELECT deleted_at FROM %I WHERE uuid = %L AND company_id = %L', get_table_from_entity(v_table_name), v_record_uuid, v_company_id) INTO v_current_deleted_at;
            IF v_current_deleted_at IS NOT NULL AND v_op_type != 'DELETE' THEN
                v_errors := array_append(v_errors, jsonb_build_object('operationUuid', v_op_uuid, 'error', 'Entity already deleted (tombstone)'));
                CONTINUE;
            END IF;

            -- Generate dynamic columns string
            SELECT string_agg(quote_ident(column_name) || ' = EXCLUDED.' || quote_ident(column_name), ', ')
            INTO v_cols
            FROM information_schema.columns
            WHERE table_name = get_table_from_entity(v_table_name)
              AND column_name NOT IN ('uuid', 'company_id', 'created_at', 'server_version');
              
            -- Fallback if v_cols is null (table not found or no cols)
            IF v_cols IS NULL THEN
                v_cols := 'payload = EXCLUDED.payload, server_updated_at = EXCLUDED.server_updated_at';
            END IF;

            v_payload_snake := convert_camel_to_snake(v_payload);
            v_payload_snake := v_payload_snake || jsonb_build_object('company_id', v_company_id, 'created_at', now(), 'server_updated_at', now(), 'deleted_at', null, 'payload', v_payload);

            -- Apply Domain Mutation
            IF v_op_type = 'INSERT' THEN
                EXECUTE format('
                    INSERT INTO %I
                    SELECT (jsonb_populate_record(null::%I, %L::jsonb)).*
                    ON CONFLICT (uuid) DO UPDATE 
                    SET %s
                    WHERE %I.company_id = EXCLUDED.company_id AND %I.deleted_at IS NULL
                ', get_table_from_entity(v_table_name), get_table_from_entity(v_table_name), v_payload_snake, v_cols, get_table_from_entity(v_table_name), get_table_from_entity(v_table_name));
            ELSIF v_op_type = 'UPDATE' THEN
                EXECUTE format('
                    UPDATE %I AS t
                    SET %s
                    FROM (SELECT * FROM jsonb_populate_record(null::%I, %L::jsonb)) AS EXCLUDED
                    WHERE t.uuid = %L AND t.company_id = %L AND t.deleted_at IS NULL
                ', get_table_from_entity(v_table_name), v_cols, get_table_from_entity(v_table_name), v_payload_snake, v_record_uuid, v_company_id);
            ELSIF v_op_type = 'DELETE' THEN
                EXECUTE format('
                    UPDATE %I
                    SET deleted_at = now(), server_updated_at = now()
                    WHERE uuid = %L AND company_id = %L
                ', get_table_from_entity(v_table_name), v_record_uuid, v_company_id);
            END IF;

            -- Record Operation
            INSERT INTO sync_operations_log (operation_uuid) VALUES (v_op_uuid);
            
            -- Write to Change Log
            INSERT INTO sync_change_log (company_id, entity_type, entity_uuid, operation_type, payload, deleted_at)
            VALUES (v_company_id, v_table_name, v_record_uuid, v_op_type, CASE WHEN v_op_type = 'DELETE' THEN NULL ELSE v_payload END, CASE WHEN v_op_type = 'DELETE' THEN now() ELSE NULL END);

            v_processed_uuids := array_append(v_processed_uuids, v_op_uuid);

        EXCEPTION WHEN OTHERS THEN
            v_errors := array_append(v_errors, jsonb_build_object('operationUuid', v_op_uuid, 'error', SQLERRM));
        END;
    END LOOP;

    RETURN jsonb_build_object(
        'processed', v_processed_uuids,
        'errors', v_errors
    );
END;
$$;
