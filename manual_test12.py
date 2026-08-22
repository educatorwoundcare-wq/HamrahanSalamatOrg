import subprocess
import json

def run_query(sql):
    with open("temp.sql", "w") as f:
        f.write(sql)
    cmd = ["npx", "supabase", "db", "query", "-f", "temp.sql", "--linked", "--output-format", "json"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout, res.stderr

UID_A = "11111111-1111-1111-1111-111111111111"
COMP_A = "COMP_A"
SYNC_A = "SYNC_A"

print(run_query(f"""
BEGIN;
INSERT INTO auth.users (id) VALUES ('{UID_A}') ON CONFLICT DO NOTHING;
DELETE FROM workspaces WHERE company_id IN ('{COMP_A}', 'COMP_DUP');

INSERT INTO workspaces (company_id, center_name, creator_uid, sync_code) VALUES ('{COMP_A}', 'Center A', '{UID_A}', '{SYNC_A}');
INSERT INTO workspaces (company_id, center_name, creator_uid, sync_code) VALUES ('COMP_DUP', 'Dup', '{UID_A}', '{SYNC_A}');
COMMIT;
"""))
