import os
import psycopg2

def run_queries():
    db_url = os.environ.get("DATABASE_URL")
    if not db_url:
        print("DATABASE_URL not found")
        return
        
    conn = psycopg2.connect(db_url)
    cur = conn.cursor()
    
    print("--- 1. pg_policies ---")
    cur.execute("""
        SELECT
            schemaname,
            tablename,
            policyname,
            permissive,
            roles,
            cmd,
            qual,
            with_check
        FROM pg_policies
        WHERE schemaname = 'public'
        AND tablename = 'connected_devices'
        ORDER BY policyname;
    """)
    for row in cur.fetchall():
        print(row)
        
    print("\n--- 2. pg_class ---")
    cur.execute("""
        SELECT
            relrowsecurity,
            relforcerowsecurity
        FROM pg_class
        WHERE oid = 'public.connected_devices'::regclass;
    """)
    for row in cur.fetchall():
        print(row)
        
    print("\n--- 3. auth context ---")
    try:
        cur.execute("""
            SELECT
                auth.uid() AS current_uid,
                auth.role() AS current_role;
        """)
        for row in cur.fetchall():
            print(row)
    except Exception as e:
        print(f"Error evaluating auth.uid(): {e}")

    cur.close()
    conn.close()

if __name__ == "__main__":
    run_queries()
