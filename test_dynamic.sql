DO $$
DECLARE v_cols TEXT;
BEGIN
    SELECT string_agg(quote_ident(column_name) || ' = EXCLUDED.' || quote_ident(column_name), ', ')
    INTO v_cols
    FROM information_schema.columns
    WHERE table_name = 'patients'
      AND column_name NOT IN ('uuid', 'company_id', 'created_at', 'server_version');
      
    RAISE NOTICE 'Cols: %', v_cols;
END;
$$;
