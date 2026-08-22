SELECT proname, prosecdef, pg_get_functiondef(oid) as def
FROM pg_proc
WHERE proname IN ('is_device_member_of_workspace');
