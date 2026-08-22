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

# Let's see what the subquery actually evaluates to!
sql = f"""
BEGIN;
SET LOCAL role = authenticated;
SELECT set_config('request.jwt.claims', format('{{"sub": "%s", "role": "authenticated"}}', '{UID_A}'::text), true);

SELECT EXISTS (
   SELECT 1
   FROM connected_devices cd
   WHERE cd.company_id = '{COMP_B}' 
   AND cd.uid = ( SELECT (auth.uid())::text AS uid)
   AND cd.role = 'Mother Account'
   AND cd.status = 'Active'
) as should_be_false;

COMMIT;
"""
print(run_query(sql))

