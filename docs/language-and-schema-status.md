# Language and schema verification — 11 November 2026

Implemented: persistent AppCompat English/Gujarati selector in Settings; 118 separately translated resource keys; locale-specific labels, enum names, currency and shared ledger text. Preserve stored user names/notes. Startup no longer overrides restored app locale. Updated AGENTS language requirement to the latest user instruction. Physical phone was not used.

API configuration: project URL and publishable key are stored in ignored secrets.properties. INTERNET and ACCESS_NETWORK_STATE permissions are declared. Supabase is accessed through SDK Auth/PostgREST APIs; Android never connects directly with a PostgreSQL admin credential.

Live observation: supplied project's REST customers endpoint responds HTTP 404, PGRST205 (public.customers absent from schema cache). Connectivity succeeds, but cloud schema/data synchronization cannot be confirmed. No live SQL executed; no admin/management credentials requested. Database policy changes require authenticated Dashboard or an authorized management connector; publishable credentials cannot administer policies.

SQL reviewed and exercised in a temporary local PostgreSQL 18 instance with minimal Supabase auth roles/functions: both migrations applied successfully. Verified all six tables enable RLS, owner can insert/read own row, another owner sees zero rows and updates zero rows, owner spoofing insert fails, client hard DELETE fails. Fixtures rolled back and temporary server stopped. These tests do not establish the live project's RLS state.

App safeguards: repository checks reject payment/work/customer mismatches and work totals inconsistent with scaled quantity/rate; a Room regression test proves a rejected payment leaves neither payment nor outbox row. Hydration validates owner identity/response scope, protects all unconfirmed statuses, uses non-destructive upserts and rejects malformed timestamps rather than substituting current time.

Verification: assembleDebug, testDebugUnitTest and lintDebug passed after the final startup locale fix: 55 tests, zero failures, 81 lint warnings and zero lint errors. Manual emulator language switching/restart remains to be verified. Lint still has existing warnings; passing the task is not a warning-free result.

Deployment order: review/apply initial schema then hardening SQL in the target project's SQL Editor; run supabase/checks/verify_schema_security.sql; verify owner and second-user API behavior against isolated test identities. Do not use production financial data until conflict/receipt and hydration recovery checks pass.

Outstanding integrity boundaries: outbox still lacks immutable mutation payloads and server receipts/base-version checks; hydration is not yet paginated/resumable; local ledger ownership binding and secure auth persistence require further work. Farm allocation/settlement tables are planned but not implemented. No guarantee of zero anomalies or production readiness is claimed.

## Live deployment update — 11 November 2026

Direct PostgreSQL connection through the supplied Mumbai session pooler succeeded using locally ignored .env (0600 permissions). libpq requires TLS to the pooler; pg_stat_ssl reports the pooler-to-database internal leg, not the client leg. Read-only checks use explicit BEGIN READ ONLY because pooler session options are not sufficient.

Applied initial_schema and security_and_contract_hardening in one transaction, guarded against existing app tables and serialized with an advisory lock. Removed the proposed auth.users registration trigger from the not-yet-deployed initial migration before deployment. No existing app data was overwritten. PostgREST schema reload requested.

Live verification: all six app tables exist with RLS enabled. Publishable-key anonymous requests to all six endpoints return HTTP401 / PostgreSQL42501 (table access denied). Anonymous/PUBLIC table grants: zero; authenticated hard-delete grants: zero. Live columns and nullability match the six Room v3 entities with the intentional cloud-only owner_id field; no differences found. Data-type validation covers integer mappings; this is not a full behavioral/precision/concurrency proof.

Auth account count is zero. Owner provisioning, authenticated API writes, two-account RLS isolation and app sync/hydration still require verification. Existing outbox receipt/conflict/pagination limitations above still apply. Do not infer production readiness from schema deployment. Run scripts/check_database.py and scripts/verify_schema_contract.py for repeatable read-only checks. Exposed credentials should be rotated in Supabase and updated locally.
