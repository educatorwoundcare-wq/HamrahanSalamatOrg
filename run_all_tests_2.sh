#!/bin/bash

run_query() {
  local query="$1"
  npx supabase db query "$query" --linked | tail -n +2
}

echo "TEST 6 & 7 - GLOBAL CURSOR & CONTINUITY"
# Create changes in different tables
run_query "
SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);
SELECT sync_push_batch('[
  {
    \"operationUuid\": \"22222222-1111-1111-1111-111111111111\",
    \"companyId\": \"TEST_WORKSPACE_A\",
    \"tableName\": \"expenses\",
    \"operationType\": \"INSERT\",
    \"payload\": {\"uuid\": \"33333333-3333-3333-3333-333333333331\", \"company_id\": \"TEST_WORKSPACE_A\", \"amount\": 100, \"category\": \"Test\", \"date\": 0, \"payload\": {}}
  },
  {
    \"operationUuid\": \"22222222-1111-1111-1111-111111111112\",
    \"companyId\": \"TEST_WORKSPACE_A\",
    \"tableName\": \"financial_transactions\",
    \"operationType\": \"INSERT\",
    \"payload\": {\"uuid\": \"33333333-3333-3333-3333-333333333332\", \"company_id\": \"TEST_WORKSPACE_A\", \"amount\": 200, \"transaction_type\": \"income\", \"category\": \"Test\", \"date\": 0, \"payload\": {}}
  }
]'::jsonb);
"
# Pull changes
RES6=$(run_query "
SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);
SELECT sync_pull('TEST_WORKSPACE_A', 0::bigint);
")
echo "Pull Output: $RES6"

echo "TEST 10 - TENANT ISOLATION"
# Try to push to WORKSPACE_B with DEVICE_A
RES10=$(run_query "
SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);
SELECT sync_push_batch('[
  {
    \"operationUuid\": \"33333333-1111-1111-1111-111111111111\",
    \"companyId\": \"TEST_WORKSPACE_B\",
    \"tableName\": \"patients\",
    \"operationType\": \"INSERT\",
    \"payload\": {\"uuid\": \"44444444-4444-4444-4444-444444444444\", \"company_id\": \"TEST_WORKSPACE_B\", \"full_name\": \"Hacker\", \"gender\": \"M\", \"payload\": {}}
  }
]'::jsonb);
")
echo "$RES10"

echo "TEST 11 - OUTSIDER ACCESS"
RES11=$(run_query "
SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-999999999999', true);
SELECT sync_push_batch('[
  {
    \"operationUuid\": \"44444444-1111-1111-1111-111111111111\",
    \"companyId\": \"TEST_WORKSPACE_A\",
    \"tableName\": \"patients\",
    \"operationType\": \"INSERT\",
    \"payload\": {\"uuid\": \"55555555-5555-5555-5555-555555555555\", \"company_id\": \"TEST_WORKSPACE_A\", \"full_name\": \"Hacker\", \"gender\": \"M\", \"payload\": {}}
  }
]'::jsonb);
")
echo "$RES11"

echo "TEST 12 - DEVICE REVOCATION"
RES12=$(run_query "
SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
SELECT sync_push_batch('[
  {
    \"operationUuid\": \"55555555-1111-1111-1111-111111111111\",
    \"companyId\": \"TEST_WORKSPACE_A\",
    \"tableName\": \"patients\",
    \"operationType\": \"INSERT\",
    \"payload\": {\"uuid\": \"66666666-6666-6666-6666-666666666666\", \"company_id\": \"TEST_WORKSPACE_A\", \"full_name\": \"Revoked User\", \"gender\": \"M\", \"payload\": {}}
  }
]'::jsonb);
")
echo "$RES12"

