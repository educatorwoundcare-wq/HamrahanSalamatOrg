import subprocess, json

def run_query(sql):
    with open("temp.sql", "w") as f:
        f.write(sql)
    cmd = ["npx", "supabase", "db", "query", "-f", "temp.sql", "--linked", "--output-format", "json"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        return False, res.stderr
    try:
        return True, json.loads(res.stdout) if res.stdout.strip() else []
    except Exception as e:
        return True, res.stdout

fix_sql = """
CREATE OR REPLACE FUNCTION public.prevent_device_self_elevation()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
BEGIN
    -- Prevent moving across workspaces or assigning to a different auth user
    IF NEW.company_id != OLD.company_id OR NEW.uid != OLD.uid THEN
        RAISE EXCEPTION 'Cannot change company_id or uid';
    END IF;

    -- If the user is NOT an Active Mother Account for this company, prevent role or status changes
    IF NOT EXISTS (
        SELECT 1 FROM public.connected_devices 
        WHERE company_id = NEW.company_id 
        AND uid = (SELECT auth.uid()::text) 
        AND role = 'Mother Account' 
        AND status = 'Active'
    ) THEN
        IF NEW.role != OLD.role OR NEW.status != OLD.status THEN
            RAISE EXCEPTION 'Unauthorized device authorization change';
        END IF;
    END IF;
    RETURN NEW;
END;
$function$;
"""

print("Applying trigger fix to DB...")
ok, res = run_query(fix_sql)
print("Apply result:", ok, res)

