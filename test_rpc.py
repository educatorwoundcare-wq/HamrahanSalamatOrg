import urllib.request

URL = "https://qfbjkdhhgeomrbamkpnn.supabase.co"
ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFmYmprZGhoZ2VvbXJiYW1rcG5uIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYxMDUzOTEsImV4cCI6MjEwMTY4MTM5MX0.TBU2hyj3jBM7wvl2cK6MhAtjv1J5fiIcN-uKTBjBSAk"

req = urllib.request.Request(f"{URL}/rest/v1/", headers={"apikey": ANON_KEY})
try:
    with urllib.request.urlopen(req) as response:
        print(response.read().decode('utf-8')[:500])
except Exception as e:
    print(e)
