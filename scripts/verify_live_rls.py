#!/usr/bin/env python3
"""Read-only isolation probe: impersonate an unrelated JWT subject; never create users or rows."""
import os
from pathlib import Path
import subprocess
from urllib.parse import urlsplit, unquote
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
TABLES = ('customers', 'tractors', 'work_entries', 'payments', 'expenses',
          'audit_logs', 'sync_changes', 'deleted_logs', 'mutation_receipts')


def main():
    values = {}
    for line in (ROOT / '.env').read_text().splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            values[key.strip()] = value.strip().strip('\"\'')
    uri = urlsplit(values['DATABASE_URL'])
    env = os.environ.copy()
    env.update(PGHOST=uri.hostname, PGPORT=str(uri.port or 5432),
               PGUSER=unquote(uri.username), PGPASSWORD=unquote(uri.password),
               PGDATABASE=uri.path.lstrip('/') or 'postgres', PGSSLMODE='require',
               PGCONNECT_TIMEOUT='10', PGOPTIONS='-c default_transaction_read_only=on',
               PSQL_HISTORY=os.devnull)
    subject = str(uuid4())
    # The transaction is read-only. set_config changes only transaction-local JWT context.
    sql = 'BEGIN READ ONLY;\n'
    sql += "SELECT 'baseline|' || count(*) FROM public.customers;\n"
    sql += f"SELECT set_config('request.jwt.claim.sub','{subject}',true);\n"
    sql += "SET LOCAL ROLE authenticated;\n"
    sql += '\n'.join(f"SELECT '{table}|' || count(*) FROM public.{table};" for table in TABLES)
    sql += '\nROLLBACK;'
    result = subprocess.run(['psql', '-X', '-tA', '--no-password', '-v', 'ON_ERROR_STOP=1'],
                            input=sql, text=True, capture_output=True, env=env, timeout=30)
    if result.returncode:
        raise SystemExit('RLS probe failed; no server response or credentials printed.')
    counts = dict(line.split('|', 1) for line in result.stdout.splitlines() if '|' in line)
    assert int(counts.pop('baseline')) > 0, 'No customer rows available to establish isolation baseline'
    assert set(counts) == set(TABLES)
    assert all(int(value) == 0 for value in counts.values()), 'Unrelated subject can see protected rows'
    print('PASS: unrelated authenticated subject sees zero rows across all 9 RLS tables; no data changed.')


if __name__ == '__main__':
    main()
