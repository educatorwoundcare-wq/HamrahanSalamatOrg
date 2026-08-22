-- Create temp table to test jsonb_populate_record
CREATE TEMP TABLE test_pop (uuid UUID, name TEXT NOT NULL, payload JSONB);
-- This should fail if name is missing from jsonb, or succeed if it maps correctly.
INSERT INTO test_pop
SELECT * FROM jsonb_populate_record(null::test_pop, '{"uuid": "11111111-1111-1111-1111-111111111111", "name": "Test", "payload": {}}'::jsonb);
SELECT * FROM test_pop;
