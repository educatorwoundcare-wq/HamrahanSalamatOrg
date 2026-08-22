-- Set auth context
SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);

SELECT sync_push_batch('[
  {
    "operationUuid": "11111111-1111-1111-1111-111111111111",
    "companyId": "TEST_WORKSPACE_A",
    "tableName": "patients",
    "operationType": "INSERT",
    "payload": {"uuid": "22222222-2222-2222-2222-222222222222", "company_id": "TEST_WORKSPACE_A", "full_name": "Test Patient", "gender": "M", "payload": {}}
  }
]'::jsonb);
