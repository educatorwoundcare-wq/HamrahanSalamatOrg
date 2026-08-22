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
COMP_A = "COMP_A"
COMP_B = "COMP_B"

# Recreate the exact test state
setup_sql = f"""
DELETE FROM auth.users WHERE id IN ('{UID_A}');
INSERT INTO auth.users (id) VALUES ('{UID_A}');
DELETE FROM workspaces;
DELETE FROM connected_devices;

INSERT INTO workspaces (company_id, center_name, creator_uid, sync_code) VALUES ('{COMP_A}', 'Center A', '{UID_A}', 'SYNC_A');
INSERT INTO workspaces (company_id, center_name, creator_uid, sync_code) VALUES ('{COMP_B}', 'Center B', '{UID_A}', 'SYNC_B');
"""

run_query(setup_sql)

def test_status(status, expected, test_name):
    run_query(f"DELETE FROM connected_devices;")
    if status is None:
        pass # revoked / no device
    else:
        run_query(f"INSERT INTO connected_devices (company_id, device_id, uid, role, status) VALUES ('{COMP_A}', 'DEV_A', '{UID_A}', 'Mother Account', '{status}');")
    
    # Check if we can read workspace A
    sql = as_role(UID_A, f"SELECT count(*) as c FROM workspaces WHERE company_id = '{COMP_A}';")
    ok, res = run_query(sql)
    c = res[0]['c'] if (ok and isinstance(res, list) and len(res) > 0 and 'c' in res[0]) else 0
    if (c == 1 and expected == "ALLOWED") or (c == 0 and expected == "DENIED"):
        print(f"{test_name:30} | VERIFIED | {expected} (count: {c})")
    else:
        print(f"{test_name:30} | FAILED   | Expected {expected}, got count: {c} | {res}")

# E. Cross-tenant Active device
def test_cross_tenant():
    run_query(f"DELETE FROM connected_devices;")
    run_query(f"INSERT INTO connected_devices (company_id, device_id, uid, role, status) VALUES ('{COMP_A}', 'DEV_A', '{UID_A}', 'Mother Account', 'Active');")
    
    # Check if we can read workspace B
    sql = as_role(UID_A, f"SELECT count(*) as c FROM workspaces WHERE company_id = '{COMP_B}';")
    ok, res = run_query(sql)
    c = res[0]['c'] if (ok and isinstance(res, list) and len(res) > 0 and 'c' in res[0]) else 0
    if c == 0:
        print(f"{'Cross-tenant Active test':30} | VERIFIED | DENIED (count: {c})")
    else:
        print(f"{'Cross-tenant Active test':30} | FAILED   | Expected DENIED, got count: {c} | {res}")

test_status('Active', 'ALLOWED', 'Active test')
test_status('Pending', 'ALLOWED', 'Pending Staff test')
test_status('Inactive', 'DENIED', 'Inactive test')
test_status(None, 'DENIED', 'Revoked device test')
test_cross_tenant()

# Cleanup
run_query(setup_sql.replace("INSERT", "--"))
