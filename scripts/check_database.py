#!/usr/bin/env python3
"""Read-only PostgreSQL connectivity/schema check. Uses the ignored root .env."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]


def main():
    config = ROOT / '.env'
    if not config.exists():
        sys.exit('Missing .env. Copy .env.example and set DATABASE_URL locally.')
    values = {}
    for line in config.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        name, separator, value = line.partition('=')
        if separator:
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            values[name.strip()] = value
    connection = values.get('DATABASE_URL', '')
    if not connection:
        sys.exit('DATABASE_URL is empty. Add the Supabase PostgreSQL connection URI to .env locally.')
    try:
        uri = urlsplit(connection)
        if uri.scheme not in ('postgresql', 'postgres') or not uri.hostname or not uri.username or not uri.password:
            raise ValueError()
        if '[YOUR-PASSWORD]' in connection or 'sb_secret_' in connection or 'sb_publishable_' in connection:
            raise ValueError()
        port = str(uri.port or 5432)
    except ValueError:
        sys.exit('Invalid DATABASE_URL. Use a PostgreSQL URI with the database password, not an API key.')
    if not shutil.which('psql'):
        sys.exit('psql is required on the Mac.')
    environment = os.environ.copy()
    environment.update({
        'PGHOST': uri.hostname, 'PGPORT': port,
        'PGUSER': unquote(uri.username), 'PGPASSWORD': unquote(uri.password),
        'PGDATABASE': unquote(uri.path.lstrip('/')) or 'postgres',
        'PGSSLMODE': 'require', 'PGCONNECT_TIMEOUT': '10',
        'PGOPTIONS': '-c default_transaction_read_only=on',
        'PSQL_HISTORY': os.devnull,
    })
    # Credentials are passed through the child environment, never command arguments or logs.
    query = """
BEGIN READ ONLY;
SELECT current_database() AS database, current_user AS role,
       current_setting('transaction_read_only') AS read_only;
SELECT ssl AS pooler_backend_tls FROM pg_stat_ssl WHERE pid = pg_backend_pid();
WITH expected(name) AS (VALUES ('customers'),('tractors'),('work_entries'),('payments'),('expenses'),('audit_logs'),('sync_changes'),('deleted_logs'),('mutation_receipts'))
SELECT e.name, c.oid IS NOT NULL AS exists, coalesce(c.relrowsecurity,false) AS rls_enabled
FROM expected e LEFT JOIN pg_namespace n ON n.nspname='public'
LEFT JOIN pg_class c ON c.relnamespace=n.oid AND c.relname=e.name AND c.relkind='r'
ORDER BY e.name;
COMMIT;
"""
    result = subprocess.run(
        ['psql', '-X', '--no-password', '--set=ON_ERROR_STOP=1', '--command', query],
        env=environment, capture_output=True, text=True, timeout=30,
    )
    if result.returncode:
        # Do not print server error text: it can contain credentials or connection details.
        sys.exit('Database connection/check failed. Check the URI, password, network and project status. No credentials printed.')
    print(result.stdout, end='')


if __name__ == '__main__':
    try:
        main()
    except subprocess.TimeoutExpired:
        sys.exit('Database connection timed out. No credentials printed.')
