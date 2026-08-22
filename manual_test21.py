import subprocess
import json

def run_query(sql):
    with open("temp.sql", "w") as f:
        f.write(sql)
    cmd = ["npx", "supabase", "db", "query", "-f", "temp.sql", "--linked", "--output-format", "json"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout

UID_A = "11111111-1111-1111-1111-111111111111"
COMP_A = "COMP_A"

print(run_query(f"""
BEGIN;
INSERT INTO auth.users (id) VALUES ('{UID_A}') ON CONFLICT DO NOTHING;
DELETE FROM workspaces WHERE company_id = '{COMP_A}';
DELETE FROM connected_devices WHERE company_id = '{COMP_A}';

INSERT INTO workspaces (company_id, center_name, creator_uid, sync_code) VALUES ('{COMP_A}', 'Center A', '{UID_A}', 'SYNC_A_TMP');
INSERT INTO connected_devices (company_id, device_id, uid, role, status) VALUES ('{COMP_A}', 'DEV_A', '{UID_A}', 'Mother Account', 'Inactive');
COMMIT;
"""))

def as_role(uid, sql):
    return f"""
    BEGIN;
    SET LOCAL role = authenticated;
    SELECT set_config('request.jwt.claims', format('{{"sub": "%s", "role": "authenticated"}}', '{uid}'::text), true);
    {sql}
    COMMIT;
    """

print("SELECT count as UID_A:")
print(run_query(as_role(UID_A, f"SELECT count(*) FROM workspaces WHERE company_id = '{COMP_A}';")))

print("Workspaces table policies:")
print(run_query("SELECT polname, polcmd, pg_get_expr(polqual, polrelid) as qual, pg_get_expr(polwithcheck, polrelid) as withcheck FROM pg_policy WHERE polrelid = 'workspaces'::regclass;"))

