#!/bin/bash
url="https://qfbjkdhhgeomrbamkpnn.supabase.co"
key="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFmYmprZGhoZ2VvbXJiYW1rcG5uIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYxMDUzOTEsImV4cCI6MjEwMTY4MTM5MX0.TBU2hyj3jBM7wvl2cK6MhAtjv1J5fiIcN-uKTBjBSAk"
JSON=$(curl -s -X POST "$url/auth/v1/token?grant_type=password" \
-H "apikey: $key" \
-H "Content-Type: application/json" \
-d '{"email":"testsync3@gmail.com","password":"password123"}')
TOKEN=$(echo "$JSON" | grep -o '"access_token":"[^"]*' | grep -o '[^"]*$')

echo "TOKEN: ${TOKEN:0:20}..."

curl -s -X POST "$url/rest/v1/rpc/sync_pull" \
-H "apikey: $key" \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-H "X-Tenant-ID: TEST_WORKSPACE_3C" \
-d '{"p_company_id":"TEST_WORKSPACE_3C", "p_last_server_version":0}'
