DROP FUNCTION IF EXISTS public.find_workspace_by_sync_code(TEXT);

CREATE OR REPLACE FUNCTION public.find_workspace_by_sync_code(p_sync_code TEXT)
RETURNS TABLE (
    company_id TEXT,
    sync_code TEXT,
    center_name TEXT
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    RETURN QUERY
    SELECT 
        w.company_id,
        w.sync_code,
        w.center_name
    FROM 
        public.workspaces w
    WHERE 
        w.sync_code = p_sync_code
    LIMIT 1;
END;
$$;

GRANT EXECUTE ON FUNCTION public.find_workspace_by_sync_code(TEXT) TO authenticated, anon;
