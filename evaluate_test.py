import subprocess
import json

def run_query(sql, as_json=True):
    with open("temp.sql", "w") as f:
        f.write(sql)
    cmd = ["npx", "supabase", "db", "query", "-f", "temp.sql", "--linked", "--output-format", "json"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        return res.stderr
    try:
        return json.loads(res.stdout) if res.stdout.strip() else []
    except:
        return res.stdout

UID_A = "11111111-1111-1111-1111-111111111111"
COMP_B = "COMP_B"

sql = f"""
BEGIN;
SET LOCAL role = authenticated;
SELECT set_config('request.jwt.claims', format('{{"sub": "%s", "role": "authenticated"}}', '{UID_A}'::text), true);
UPDATE workspaces SET center_name = 'Hack2' WHERE company_id = '{COMP_B}' RETURNING company_id;
COMMIT;
"""
print("Update output:", run_query(sql))

sql_verify = f"""
SELECT center_name FROM workspaces WHERE company_id = '{COMP_B}';
"""
print("Verify output:", run_query(sql_verify))

