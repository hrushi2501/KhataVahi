#!/usr/bin/env python3
"""Apply the reviewed initial schema only when none of its app tables exist."""
from pathlib import Path
import os
import subprocess
import sys
from urllib.parse import unquote, urlsplit

root = Path(__file__).resolve().parents[1]
values = {}
for line in (root / '.env').read_text().splitlines():
    if line.strip() and not line.lstrip().startswith('#') and '=' in line:
        name, value = line.split('=', 1)
        values[name.strip()] = value.strip().strip('"\'')
uri = urlsplit(values.get('DATABASE_URL', ''))
if uri.scheme not in ('postgres', 'postgresql') or not uri.hostname or not uri.password:
    sys.exit('Set a valid DATABASE_URL locally.')
env = os.environ.copy()
env.update(PGHOST=uri.hostname, PGPORT=str(uri.port or 5432), PGUSER=unquote(uri.username or ''),
           PGPASSWORD=unquote(uri.password), PGDATABASE=uri.path.lstrip('/') or 'postgres',
           PGSSLMODE='require', PGCONNECT_TIMEOUT='10', PGOPTIONS='', PSQL_HISTORY=os.devnull)
initial = (root / 'supabase/migrations/20261110000001_initial_schema.sql').read_text()
hardening = (root / 'supabase/migrations/20261111000001_security_and_contract_hardening.sql').read_text()
hardening = hardening.replace('BEGIN;', '', 1)
hardening = hardening.rsplit('COMMIT;', 1)[0]
guard = """
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relname IN ('customers','tractors','work_entries','payments','expenses','audit_logs'))
 THEN RAISE EXCEPTION 'App tables already exist: inspect migration state manually'; END IF;
END $$;
"""
sql = "BEGIN; SELECT pg_advisory_xact_lock(824187534);\n" + guard + initial + hardening + "\nNOTIFY pgrst, 'reload schema';\nCOMMIT;"
r = subprocess.run(['psql', '-X', '--no-password', '--set=ON_ERROR_STOP=1'], input=sql,
                   capture_output=True, text=True, env=env, timeout=60)
if r.returncode:
    # Do not expose connection errors or credentials. SQL rollback is automatic on disconnect.
    sys.exit('Schema application failed and transaction rolled back. Inspect the schema before retrying.')
print('Initial schema and hardening committed successfully. No existing app tables were overwritten.')
