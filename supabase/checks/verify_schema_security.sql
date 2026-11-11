-- Read-only: run in the project's SQL Editor after applying both migrations.
-- Every expected table must exist and report rls_enabled = true.
WITH expected(name) AS (
 VALUES ('customers'), ('tractors'), ('work_entries'), ('payments'), ('expenses'), ('audit_logs')
)
SELECT e.name, c.oid IS NOT NULL AS exists,
       coalesce(c.relrowsecurity, false) AS rls_enabled
FROM expected e LEFT JOIN pg_namespace n ON n.nspname = 'public'
LEFT JOIN pg_class c ON c.relnamespace = n.oid AND c.relname = e.name AND c.relkind = 'r';

SELECT tablename, policyname, roles, cmd, qual, with_check
FROM pg_policies WHERE schemaname = 'public'
AND tablename IN ('customers','tractors','work_entries','payments','expenses','audit_logs')
ORDER BY tablename, policyname;

SELECT table_name, grantee, privilege_type FROM information_schema.role_table_grants
WHERE table_schema = 'public' AND grantee IN ('anon', 'authenticated', 'PUBLIC')
AND table_name IN ('customers','tractors','work_entries','payments','expenses','audit_logs')
ORDER BY table_name, grantee, privilege_type;

SELECT conrelid::regclass AS table_name, conname, pg_get_constraintdef(oid)
FROM pg_constraint WHERE connamespace = 'public'::regnamespace
AND conrelid IN ('public.customers'::regclass,'public.tractors'::regclass,
'public.work_entries'::regclass,'public.payments'::regclass,'public.expenses'::regclass)
ORDER BY table_name, conname;
