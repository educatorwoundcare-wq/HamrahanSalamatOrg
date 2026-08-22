const { createClient } = require('@supabase/supabase-js');
const url = 'https://qfbjkdhhgeomrbamkpnn.supabase.co';
const key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFmYmprZGhoZ2VvbXJiYW1rcG5uIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYxMDUzOTEsImV4cCI6MjEwMTY4MTM5MX0.TBU2hyj3jBM7wvl2cK6MhAtjv1J5fiIcN-uKTBjBSAk';
const supabase = createClient(url, key);

async function create() {
  const { data, error } = await supabase.auth.signUp({
    email: 'testsync2@example.com',
    password: 'password123'
  });
  console.log("Sign up:", data, error);
}
create();
