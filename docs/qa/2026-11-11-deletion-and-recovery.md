# Deletion, cloud authority, cache and force-stop verification

Supabase is authoritative for confirmed records. Room v4 is the offline working projection, with durable pending outbox/envelopes, server-version tracking and provisional deletion snapshots. No network is needed for a local accounting operation.

## Implemented

- Explicit reason/confirmation for customer, work, payment and expense soft deletion; visible row Actions and Delete customer control. Customers with active work/payments are protected; work deletion explicitly retains payments.
- Immutable deleted-record history with full prior snapshots. Settings exposes Expense history and Deleted records. Remote tombstones invalidate active Room records, retaining conflicting local proposals in audit history and requiring review instead of resurrection.
- Ordinary DELETE in Supabase on business rows becomes a tombstone UPDATE; its original row and pre-delete snapshot are retained. Direct SQL edits increment record versions. Audit/deleted history cannot be updated/deleted through ordinary operations. Administrative schema drops/purges outside this controlled path are not supported synchronization operations.
- Revision-checked mutation RPC, stable request receipts, durable frozen upload envelopes and owner binding. Replay uses the same payload/UUID; acknowledgement is transactional with server-version storage. API clients cannot bypass this path with raw table writes.
- Reactive Room invalidation refreshes open customer balances/ledgers and work lists. Static nonanimated skeletons distinguish loading from empty/error states. Reopening/sign-in requests cloud refresh; healthy paged hydration continues without exponential failure backoff.
- Target SDK36/system-and-keyboard insets; no foreground service or unrelated runtime permissions.

## Database verification

Both deletion/history and RPC migrations applied in one live transaction after isolated PostgreSQL tests. Live checks confirm RLS on all9 exposed tables, zero anonymous table grants, zero client hard-delete grants, one owner and no Room v4 column/nullability differences. No synthetic financial records were written to the owner project.

Isolated SQL assertions passed: insert/update receipts, exact replay without extra change events, stale overwrite rejection, snapshot capture, resurrection refusal, direct-table bypass denial, ordinary DELETE retaining its row/history. Tests roll back fake records. SQL fixtures are committed under supabase/tests for isolated use only.

Automated Room tests cover v3->v4 migration/schema preservation, precise snapshots, required reasons, no double deletion, transactional rollback, work/payment retention, customer dependency guards and canonical receipt replacement. A full v2->v4 upgrade test enables foreign keys and checks relationships/amounts/audit links, fixing deferred UUID-reference migration checks. Frozen request replay test proves a lost response replays before a new local edit with the correct new base version.

## Emulator/cache/force-stop

Installed the debug update on emulator-5554 only (ARM64/API37); physical Redmi was not used. Force-stop/reopen works. A cache-directory wipe simulation while stopped preserved cryptographic hashes of all8 persistent database/DataStore/preferences files and the app process resumed. It never clears app storage. Android's native `pm clear --cache-only` callback timed out on this emulator twice; that particular platform command is not certified. The script distinguishes native operation from the explicit cache-directory simulation and refuses physical-device serials.

Room stores records/outbox/frozen requests in databases/, with preferences/cursors in files/shared_prefs, not regenerable cache. Signing out does not clear these. Clear storage/uninstall is destructive to unsynced local-only entries; only cloud-confirmed records can be restored. Android suspends background work after force-stop until user reopens; the app does not attempt to circumvent this.

## Limits

Automated results and release sizes are reported in the final task response. Whole-ledger authenticated emulator restoration, large ledger performance, multi-device reconciliation, encrypted export/restore, older/low-RAM device QA and public Play approval remain unfinished. Do not infer them from migrations or successful compilation. See PLAY_READINESS.md for the release blockers.

Final checks: 100 unit tests, zero failures/errors/skips; debug and release lint zero errors. assembleDebug, assembleRelease and bundleRelease pass on JDK21/API36. Release APK approximately2.8MB and AAB approximately5.1MB, both unsigned. APK ZIP/native ELF 16KB static checks pass.
