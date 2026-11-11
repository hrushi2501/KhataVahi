from pathlib import Path
from urllib.parse import urlsplit,unquote
import os,subprocess,json
v={}
for l in Path('.env').read_text().splitlines():
 if '=' in l and not l.lstrip().startswith('#'):
  k,x=l.split('=',1);v[k.strip()]=x.strip().strip('"\'')
u=urlsplit(v['DATABASE_URL']);e=os.environ.copy();e.update(PGHOST=u.hostname,PGPORT=str(u.port or 5432),PGUSER=unquote(u.username),PGPASSWORD=unquote(u.password),PGDATABASE='postgres',PGSSLMODE='require',PGCONNECT_TIMEOUT='10',PGOPTIONS='')
q="""BEGIN READ ONLY;
SELECT coalesce(json_agg(row_to_json(x)), '[]'::json) FROM (SELECT table_name,column_name,data_type,is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('customers','tractors','work_entries','payments','expenses','audit_logs','deleted_logs') ORDER BY table_name,ordinal_position) x;
SELECT json_build_object('owner_accounts',(SELECT count(*) FROM auth.users),'anonymous_table_grants',(SELECT count(*) FROM information_schema.role_table_grants WHERE table_schema='public' AND grantee IN ('anon','PUBLIC') AND table_name IN ('customers','tractors','work_entries','payments','expenses','audit_logs','deleted_logs')),'client_delete_grants',(SELECT count(*) FROM information_schema.role_table_grants WHERE table_schema='public' AND grantee='authenticated' AND privilege_type='DELETE' AND table_name IN ('customers','tractors','work_entries','payments','expenses','audit_logs','deleted_logs')));
COMMIT;"""
r=subprocess.run(['psql','-X','-At','-v','ON_ERROR_STOP=1','-c',q],env=e,capture_output=True,text=True,timeout=30)
if r.returncode:raise SystemExit('Read-only contract check failed; no credentials printed')
rows=[json.loads(x) for x in r.stdout.splitlines() if x.startswith(('[','{'))]; cols=rows[0]
schema_dir=Path('schemas/com.farmeraccounts.data.local.db.AppDatabase')
latest_schema=max(schema_dir.glob('*.json'),key=lambda path:int(path.stem))
room=json.loads(latest_schema.read_text())['database']['entities']
errors=[]
for ent in room:
 t=ent['tableName']
 if t in ('outbox','sync_versions','upload_attempts'):continue
 actual={x['column_name']:x for x in cols if x['table_name']==t}
 expected={f['columnName']:f for f in ent['fields']}
 if set(actual)!=set(expected)|{'owner_id'}:errors.append(t+' column mismatch')
 for name,f in expected.items():
  a=actual.get(name)
  if not a:continue
  if f['notNull']!=(a['is_nullable']=='NO'):errors.append(t+'.'+name+' nullability mismatch')
  if f['affinity']=='INTEGER' and a['data_type'] not in ('bigint','boolean','timestamp with time zone'):errors.append(t+'.'+name+' incompatible integer mapping')
 print(t,'column/nullability comparison checked')
print('Contract differences:', errors)
print('Access checks:',rows[1])
if errors:raise SystemExit(1)
