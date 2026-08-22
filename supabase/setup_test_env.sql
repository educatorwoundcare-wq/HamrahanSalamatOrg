INSERT INTO workspaces (company_id, sync_code, center_name) VALUES ('TEST_WORKSPACE_A', 'SYNC_A', 'Test A') ON CONFLICT DO NOTHING;
INSERT INTO workspaces (company_id, sync_code, center_name) VALUES ('TEST_WORKSPACE_B', 'SYNC_B', 'Test B') ON CONFLICT DO NOTHING;
INSERT INTO connected_devices (device_id, uid, company_id, role, status) VALUES ('dev1', '00000000-0000-0000-0000-000000000001', 'TEST_WORKSPACE_A', 'Nurse', 'Active') ON CONFLICT DO NOTHING;
INSERT INTO connected_devices (device_id, uid, company_id, role, status) VALUES ('dev2', '00000000-0000-0000-0000-000000000002', 'TEST_WORKSPACE_B', 'Nurse', 'Active') ON CONFLICT DO NOTHING;
INSERT INTO connected_devices (device_id, uid, company_id, role, status) VALUES ('dev3', '00000000-0000-0000-0000-000000000003', 'TEST_WORKSPACE_A', 'Nurse', 'Revoked') ON CONFLICT DO NOTHING;
