import subprocess
import json

def run_query(sql, as_json=True):
    with open("temp.sql", "w") as f:
        f.write(sql)
    cmd = ["npx", "supabase", "db", "query", "-f", "temp.sql", "--linked", "--output-format", "json"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        return False, res.stderr
    try:
        return True, json.loads(res.stdout) if res.stdout.strip() else []
    except:
        return True, res.stdout

def as_role(uid, sql):
    return f"""
    BEGIN;
    SET LOCAL role = authenticated;
    SELECT set_config('request.jwt.claims', format('{{"sub": "%s", "role": "authenticated"}}', '{uid}'::text), true);
    {sql}
    COMMIT;
    """

UID_A = "11111111-1111-1111-1111-111111111111"
UID_B = "22222222-2222-2222-2222-222222222222"
COMP_A = "COMP_A"
COMP_B = "COMP_B"

# Recreate the exact test state that caused the failure
setup_sql = f"""
DELETE FROM auth.users WHERE id IN ('{UID_A}', '{UID_B}');
INSERT INTO auth.users (id) VALUES ('{UID_A}'), ('{UID_B}');
DELETE FROM workspaces;
DELETE FROM connected_devices;

-- Setup A
INSERT INTO workspaces (company_id, center_name, creator_uid, sync_code) VALUES ('{COMP_A}', 'Center A', '{UID_A}', 'SYNC_A');
INSERT INTO connected_devices (company_id, device_id, uid, role, status) VALUES ('{COMP_A}', 'DEV_A', '{UID_A}', 'Mother Account', 'Active');

-- Setup B
SET LOCAL role = authenticated;
SELECT set_config('request.jwt.claims', format('{{"sub": "%s", "role": "authenticated"}}', '{UID_B}'::text), true);
INSERT INTO workspaces (company_id, center_name, creator_uid, sync_code) VALUES ('{COMP_B}', 'Center B', '{UID_B}', 'SYNC_B');
-- Wait, the original test did this as postgres:
RESET role;
INSERT INTO connected_devices (company_id, device_id, uid, role, status) VALUES ('{COMP_B}', 'DEV_B', '{UID_B}', 'Mother Account', 'Active');
"""

run_query(setup_sql)

print("--- Data setup ---")
ok, data = run_query("SELECT * FROM connected_devices;")
print("connected_devices:", data)

print("--- Test 4 UPDATE ---")
t4_upd = as_role(UID_A, f"UPDATE workspaces SET center_name = 'Hack' WHERE company_id = '{COMP_B}' RETURNING company_id;")
ok, res = run_query(t4_upd)
print("t4_upd res:", res)

