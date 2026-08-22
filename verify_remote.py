import urllib.request
import urllib.error
import json

URL = "https://qfbjkdhhgeomrbamkpnn.supabase.co"
ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFmYmprZGhoZ2VvbXJiYW1rcG5uIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYxMDUzOTEsImV4cCI6MjEwMTY4MTM5MX0.TBU2hyj3jBM7wvl2cK6MhAtjv1J5fiIcN-uKTBjBSAk"

def request(method, path, data=None):
    headers = {"apikey": ANON_KEY}
    if data:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(f"{URL}{path}", headers=headers, data=data, method=method)
    try:
        with urllib.request.urlopen(req) as res:
            return res.getcode(), res.read().decode('utf-8')
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8')

print("1. Testing anon workspace read...")
code, res = request("GET", "/rest/v1/workspaces?select=*")
print("Anon GET workspaces:", code, res)

print("2. Testing anon workspace insert...")
code, res = request("POST", "/rest/v1/workspaces", b'{"company_id": "test", "sync_code": "test"}')
print("Anon POST workspaces:", code, res)

print("3. Testing RPC with invalid sync code (anon)...")
code, res = request("POST", "/rest/v1/rpc/resolve_workspace_by_sync_code", b'{"p_sync_code": "invalid_code"}')
print("Anon RPC (invalid):", code, res)
