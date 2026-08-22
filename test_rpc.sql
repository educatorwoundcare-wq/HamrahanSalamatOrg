SELECT 
  p.proname,
  pg_get_functiondef(p.oid) as def
FROM pg_proc p
WHERE p.proname = 'find_workspace_by_sync_code';
