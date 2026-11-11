# Farmer Accounts: development through private release

Scope extension (11 November 2026): [OWN_FARM_PLAN.md](OWN_FARM_PLAN.md) defines the finalized own-farm spending feature. Its expense/payment/allocation model supersedes the original single-amount expense assumptions below. Implement the expanded local ledger and backup format before freezing Supabase SQL. Farm costs must remain separate from customer dues, and cash-flow reports must use actual expense payments once unpaid bills are supported. Crop sales/receipts remain an optional separate slice.

Plan date: 10 November 2026. Scope: planning and baseline verification only. No application, database, dependency, emulator, or backend configuration changes made in this planning pass.

Build a dependable Gujarati + simple English accounting utility for one owner. Preserve Kotlin, XML, ViewBinding, Material Components, MVVM, manual DI, Room, integer money, and offline operation. Keep the Redmi disconnected throughout development and QA. Default delivery is a privately distributed, signed APK; public Play Store distribution is a separate decision.

## Evidence from the current checkout

| Area | Observed implementation | Remaining evidence or work |
| --- | --- | --- |
| Build | Kotlin 2.0.21, KSP 2.0.21-1.0.28, AGP 8.7.2, Gradle 8.11.1, JDK target 21; minSdk 26, compile/targetSdk 34 | SDK/SDK-client compatibility assessment before any upgrades |
| Local data | Room v2, seven entities, exported schemas v1/v2, explicit `MIGRATION_1_2`, transactional accounting repositories | Actual upgrade tests; owner binding; sync metadata; seed migration |
| Accounting | Scaled quantities, Long paise, partial and customer-level payments, advances, balances, ledger, dashboard totals | Boundary validation, correction invariants, consistent aggregate reads, failure/rollback coverage |
| UI | Home, customer search/add/edit/detail, work list/add, payment entry, expense entry, settings; ViewModels use repositories/use cases | Runtime QA, corrections, payment/expense history, tractor management, backup/restore, cloud status/auth |
| Theme | Light default; DataStore preference; immediate AppCompat toggle implementation | Restart, accessibility, large fonts, rotation and startup-flash checks |
| Sharing | Bilingual text formatter and Android share chooser; statement includes last ten entries | Clearly disclose statement truncation; full statement/export; actual WhatsApp handoff on emulator if available |
| Cloud | `supabase/migrations/` contains only `.gitkeep` | No SQL schema, SDK, DTOs, auth, WorkManager, upload, pull, or hydration implementation |
| Outbox | Sequence, entity ID/type, operation, retry count, status; repository writes enqueue transactionally | Immutable mutations, base versions, acknowledgements, leases, failures, audit upload, dependency recovery |
| Release | Debug APK generation exists | R8 disabled; signing and release verification not configured |
| Mac | Android Studio installed; SDK at `/opt/homebrew/share/android-commandlinetools`; platform/build-tools 34 installed | Emulator package/system images absent in configured SDK; no AVDs found |
| Git | Project files currently appear untracked | Review exclusions and establish a recoverable source baseline before implementation |

Fresh baseline command: `./gradlew assembleDebug testDebugUnitTest lintDebug --console=plain` — **passed**.

- 40 tests, zero failures/errors/skips across seven test classes.
- Includes four Robolectric Room integration tests; no existing migration test or emulator instrumentation suite found.
- Fresh lint report: **99 warnings, zero errors**. Includes 45 dependency/tooling advisories, 10 missing-label warnings, 12 `SetTextI18n`, four hardcoded strings, plus resource/layout warnings. A successful lint task is not a warning-free app.
- Compiler warning: deprecated `getParcelableExtra` use in `LedgerShareFormatterTest`; Gradle launcher also emits a Java native-access warning. Investigate the launcher JDK rather than masking warnings globally.
- No app runtime, emulator, real Supabase, RLS, release signing, or restore result verified in this pass.

Source anchors: `AppDatabase.kt`, `OutboxEntity.kt`, `OutboxDao.kt`, accounting repositories/use cases, presentation fragments/ViewModels, `AppContainer.kt`, `app/build.gradle.kts`, version catalog, manifest, schemas, tests and generated reports. README's statement that the Room migration is verified is not supported by the current test suite.

## Sequence and completion gates

Every implementation phase ends with `assembleDebug`, `testDebugUnitTest`, `lintDebug`, warning review, and the phase's relevant manual emulator checks. Record command output, test counts, device/API, screenshots where useful, and unresolved issues in `docs/qa/`. Compilation alone never closes a feature.

### Phase 0 — Reproducible Mac testing environment

1. Review Git exclusions, including signing files/passwords, auth tokens, test credentials and `secrets.properties`. Preserve all current work. Establish a source-control checkpoint when implementation begins.
2. Use the existing Android Studio installation and one consistent SDK path. Install the Android Emulator and ARM64 images through its SDK Manager. Record exact emulator/image revisions and AVD configuration; do not assume the system image API equals the app's targetSdk.
3. Create two primary AVDs using the built-in emulator:

| Profile | Configuration | Purpose |
| --- | --- | --- |
| `Farmer_LowMemory` | API 35 initially; ARM64; 1080 × 2400; about 6.4-inch; 2 GB RAM; two cores; 256 MB heap | Everyday offline flows and constrained-resource QA |
| `Farmer_Modern` | Latest stable ARM64 image verified in SDK Manager at setup; 4 GB RAM; four cores; standard phone profile | Current Android behavior and compatibility |

4. Confirm density, usable dp dimensions, actual guest RAM and successful cold boot. If the low-memory image refuses the requested RAM, record the constraint and use a compatible stable image rather than silently raising RAM. Recheck claims about API 37 minimum RAM against the installed emulator release; this audit did not establish that claim.
5. Run one emulator at a time. Use explicit emulator serials for all install, test, log and failure-injection commands. Add safeguards in QA scripts to reject physical device serials.
6. Install/debug the current app and capture a baseline for every existing screen, empty database behavior and Logcat crashes. No physical phone is involved.
7. Add a temporary older-API AVD for minimum-supported API 26 smoke tests if a usable ARM64 image is available. Otherwise record the coverage gap and add the nearest supported image; API 35/modern testing does not validate minSdk 26.

**Gate:** both primary AVDs boot; current APK installs/launches; baseline screenshots and crash checks recorded. No claim of MIUI or physical sunlight qualification.

### Phase 1 — Protect local financial integrity before extending UI

1. Harden input parsing. Current normalizer drops unknown characters, decimal parsing truncates extra precision, and multiplication during parsing is unchecked. Define accepted formats, enforce maximum values, reject malformed/overflowing inputs, and explain excess precision. Support Gujarati/English digits; retain deterministic half-up amount rounding.
2. Validate every financial write in use cases/repositories, including correction paths. Recompute work totals from quantity/rate, reject invalid amounts, validate referenced active records, and ensure a work-linked payment belongs to the same customer. An FK on work ID alone does not enforce this relationship.
3. Add an in-flight save guard to every form plus a stable draft operation ID where needed. A fast double tap must not create two jobs/payments. Persist selected customer, tractor, date, work link and entered values across rotation/process recreation with `SavedStateHandle` or equivalent small state.
4. Fix dropdown selection mapping: current callbacks index the full backing list, while dropdown labels may be filtered. Resolve the selected item by a stable ID; prevent stale customer-work loads from linking the wrong work entry.
5. Make corrections auditable: required reason, immutable before/after snapshots, time and mutation ID, with business row + audit + outbox in one Room transaction. Existing reason-only audit strings cannot reconstruct prior values.
6. Define deletion semantics. Archive customers/tractors without hiding unpaid obligations. Soft-deleting work must explicitly address its linked payments; never erase payments implicitly. Correcting a payment changes collections and balance exactly once.
7. Replace separate/N+1 balance reads with Room queries or a transactionally consistent aggregate snapshot. Customer list currently observes customer changes but fetches balances separately, so work/payment-only changes may not refresh balances. Remove silent failure paths that present stale totals as current.
8. Replace asynchronous, direct-DAO seed creation with a deterministic transactional initializer. Seed rows must enter the outbox, including audit events. Avoid SQLite `REPLACE` on referenced rows because its delete/insert semantics can affect FKs.
9. Prepare an additive Room v3 migration: generate local UUIDs for legacy tractor IDs; preserve a durable mapping; update work/expense references and pending outbox references transactionally. Check row counts, amounts, FK integrity and rollback. Do not edit already-shipped v1/v2 migration history or clear the database.

**Gate:** boundary/property examples and repository tests prove amount precision, FK/customer consistency, corrections, no duplicate submission and transaction rollback on injected failure. Real v1→latest and v2→latest database upgrades preserve the ledger.

### Phase 2 — Finish and verify the complete local application

Keep four top-level destinations: **Home, Work, Customers, Settings**. Payment/expense entry remains reachable through clear actions; avoid expanding into an ERP.

| Surface | Required final behavior |
| --- | --- |
| Home | Live totals, clear dues versus advances, collections/expenses with an explicit period, recent activity, new-work/payment shortcuts |
| Work | Search/filter history, details, tractor/unit/date, preview total and initial payment, correction/archive with reason |
| Customers | Search by identifying fields, readable balances, add/edit, chronological ledger, outstanding/advance explanation |
| Payments | History and correction details, general or work-linked receipts, partial payment and advance support |
| Expenses | History, category/tractor/date filters, entry and auditable correction |
| Tractors | Manage actual tractor identity and model, archive safely, never invent the unknown mini-tractor model |
| Settings | Immediate persistent theme, bilingual display preference if needed, local backup/restore, app version; truthful cloud status once available |
| Sharing | Preview ledger, clear period/entry count, Android chooser with fallback when WhatsApp is absent |

Use real Room data for all totals/lists. Test/demo fixtures belong only in test or debug-only paths. Never show zero as a substitute for a failed query. Add loading/empty/error states and actionable bilingual validation.

Review XML on emulator: body text generally ≥18sp, important controls ≥56dp, 200% fonts, enlarged display size, small usable width, Gujarati long names, keyboard overlap, scrolling, contrast, TalkBack labels, gesture/three-button navigation and theme restart. Several current captions are 14sp/16sp; assess their importance rather than assuming shared dimensions guarantee readability.

**Gate:** offline customer → fractional work → partial payment → correction → expense → share flow completes; navigation/rotation/process recreation preserve intended state; all visible data agrees with Room and the ledger. UI refinement precedes live cloud integration.

### Phase 3 — Independent backup and restore

Implement this before using real financial data so recovery does not depend on cloud integration.

1. CSV is a human-readable export, not the sole restore format. Define a versioned full backup archive containing customers, tractors, work, payments, expenses, tombstones, audit history, identifiers, integer units and ownership metadata. Include a manifest, counts and checksums; never export auth tokens or signing material.
2. Export a consistent Room snapshot through the Storage Access Framework. Use authenticated encryption for full financial backup, with a user-held recovery password and a documented standard format; never bind its only decryption key to one phone's Keystore.
3. Restore into staging: validate format/schema version, limits, integer ranges, checksum/authentication, duplicate IDs, relationships and ownership before touching live records. Reject unsupported/newer schemas and malformed archives.
4. First release supports restore into an empty ledger as the simplest safe path. If the ledger is non-empty, block automatic overwrite/merge and explain the recovery path. Do not introduce a destructive reset flow to make restore convenient.
5. Preserve mutation identities/base revisions for unconfirmed changes. Exclude expired worker leases and credentials. Verify restored records against cloud receipts before upload; never blindly treat restored pending data as new mutations or mark it cloud-confirmed.
6. Show destination and last successful export time; prepare a bilingual recovery guide. An export succeeds only after the file is closed successfully and can be validated.

**Gate:** restore into a clean emulator installation reproduces all entity counts, ledger totals, audit events and relationships. Wrong password, truncated archive and incompatible format leave the existing ledger untouched. CSV escaping handles commas, newlines, Gujarati and spreadsheet formula injection.

### Phase 4 — Freeze the cloud contract and test database security

**Prerequisites:** Supabase project in Mumbai (`ap-south-1`); separate test project or local Supabase stack with isolated test accounts/data. App configuration needs only project URL and publishable key in ignored `secrets.properties`. No privileged backend credential belongs in the app or a repository.

1. Audit the Kotlin/KSP/AGP/Gradle/JDK graph against the selected supabase-kt, serialization and Ktor release requirements. Pin a tested compatible set. Keep Kotlin 2.0.21 if supported; if an upgrade is necessary, do the smallest coordinated change and rerun the complete baseline. Quickstart example versions are not proof of compatibility.
2. Add Auth/PostgREST, the Supabase BOM, serialization plugin matching Kotlin and Ktor Android engine; add WorkManager later with the sync worker. Add required network permissions. Keep network DTOs separate from Room entities.
3. Create version-controlled, reviewable SQL for the six business/audit tables, plus minimal mutation receipts/change tracking needed by the protocol. Use UUIDs, integer paise/scaled quantities, owner IDs, timestamps, server revisions and tombstones. Store business dates consistently in Asia/Kolkata; do not use device clocks to decide conflict winners.
4. Use owner-aware composite foreign keys, for example `(owner_id, customer_id)` to `(owner_id, id)`. Enforce payment-work-customer consistency in both local validation and server constraints. Index owner, reference and synchronization lookup columns; reject invalid quantities/amounts/enums.
5. Enable RLS on every exposed table, including supporting sync tables. Scope to `authenticated`, validate `auth.uid() = owner_id`, and use both `USING` and `WITH CHECK` for updates. Grant the least privilege. Revoke client hard-delete access; business deletion is a versioned soft delete. A DELETE policy is not a reason to grant financial destruction.
6. Audit rows are append-only (version 1 with common metadata where required); reject client updates/deletes. Persist audit events through the outbox too. Do not allow a later sync to rewrite correction history.
7. Define a small transactional mutation RPC: authenticated owner check, immutable request ID, base-revision compare, row mutation + audit insertion + receipt in one PostgreSQL transaction. Prefer `SECURITY INVOKER`; any privileged function requires a justified review, fixed search path and restricted execution grants.
8. UUID upsert alone does not prevent stale overwrites. The server must return an existing receipt for the same mutation/payload and explicitly reject a conflicting reuse or base version. Direct table write permissions must not bypass these invariants; enforce them with constraints/triggers or route writes through the reviewed function.
9. Apply/rehearse SQL on the test environment first. Review all SQL before production application. Provision the owner account through the Dashboard/authorized management surface, disable public signups, verify recovery access, and avoid app signup flows.

**Gate:** SQL replay/migration tests pass; anonymous and second-user sessions cannot read, insert, change ownership, update or delete the owner's rows or attach cross-owner FKs. Verify denied updates leave the target unchanged, not merely that the HTTP call returned no error. Production schema application remains a distinct recorded release step.

### Phase 5 — Owner authentication and durable upload

1. Use a dedicated owner login; allow viewing and writing local data while signed out/offline. Store session secrets through an SDK-compatible Android Keystore-backed encrypted session store. Test restore/refresh with the pinned SDK instead of assuming default persistence is encrypted.
2. Bind the local ledger to one immutable owner after first verified authentication. Previously offline-created records remain an unbound local ledger until this binding. If cloud data already exists, pull it into staging and reconcile explicitly before attaching/uploading local records. Never silently upload one owner's ledger to a different account; signing out never clears Room.
3. Evolve outbox metadata additively: immutable mutation UUID/payload, local entity revision, expected server revision, owner binding, attempts, next retry, lease and sanitized error category. Existing legacy entries cannot reconstruct old snapshots; preserve audit history and backfill a documented current-state mutation per entity without inventing past values.
4. Separate local revision from acknowledged server revision. Acknowledge only the submitted mutation/revision transactionally. If revision 3 is saved while revision 2 uploads, its acknowledgement must not mark revision 3 synced.
5. Process sequentially by outbox sequence within dependency constraints: customers/tractors before work, work before linked payments, tractors before expenses. Audits travel atomically with their related mutation. Resolve missing parent mutations, including migrated seed tractors, before child upload; don't globally reshuffle updates to the same record.
6. Implement one shared sync coordinator with unique WorkManager jobs, connected-network constraints, exponential retry and worker lease recovery. Trigger after local commits, login, foreground resume and manual retry; add a periodic safety sweep. Recheck the queue on completion to avoid a save-during-worker-exit scheduling gap.
7. Treat timeouts, lost responses, 429 and 5xx as retryable with bounded attempts per run; respect Retry-After. Retry an expired token once through the auth layer; invalid/revoked refresh credentials require login. FK, policy, invalid payload and conflict errors need inspection/reconciliation rather than endless retries.
8. Keep persistent failures/dead letters and their descendants visible without deleting data. Continue independent valid work where dependency ordering permits. Recover abandoned `IN_PROGRESS` records after process death.
9. Report separate upload/download/hydration health, queued/conflicted/failed counts and last confirmed success. “Saved on this device” follows Room commit; “backed up” follows the server receipt. Do not promise immediate WorkManager execution or background work after Android force-stop; sync resumes after reopening.

**Gate:** real test backend proves authentication/refresh, parent order, duplicate replay and interrupted uploads. Kill the app after server commit but before local acknowledgement: replay returns the receipt, creates no duplicate, and leaves later local edits pending.

### Phase 6 — Initial hydration and bidirectional reconciliation

1. Model `NOT_STARTED`, `IN_PROGRESS`, `COMPLETE` and `FAILED` hydration states per owner. A fresh installation must say cloud history is still downloading until all required pages are committed. Local new entries remain possible and clearly separate from a complete restored ledger.
2. Pull in dependency order: customers/tractors → work → payments/expenses → audit. Include tombstones. Use bounded pages and a server-controlled checkpoint protocol, not client timestamps or a single unpaginated request.
3. Define a stable initial snapshot/change boundary. Stage pages with persisted checkpoints, then catch up changes from that boundary. A timestamp-only cursor or bare sequence is insufficient unless commit-order behavior is proven. Test equal timestamps, writes during paging and late commits so changes cannot fall between checkpoints.
4. Apply each page and its checkpoint transactionally in Room with FK integrity; mark complete only after every dependency and catch-up page is committed. Stage the initial dataset before publication where needed to prevent partial totals appearing complete.
5. On restart, replay pages safely. Download must not create outbound mutations. Preserve pending local work and retain conflicting remote versions separately. No SQLite `REPLACE` on parent rows.
6. Prevent duplicate tractor seeds during reinstall: hydrate known cloud tractors before publishing new default seeds to that owner. Reconcile only verified seed identities via the legacy mapping; do not merge real tractors merely because their types match.

| Situation | Required rule |
| --- | --- |
| New local record, no cloud row | Create through immutable mutation; await receipt |
| Local edit against unchanged remote base | Conditional server update; increment server revision |
| Remote change, no pending local edit | Apply transactionally, retain remote revision |
| Local and remote both changed | Preserve both; require an explicit bilingual resolution with audit; no clock-based last-write-wins |
| Remote soft delete versus pending local edit | Keep local edit and tombstone as a visible conflict; no automatic resurrection |
| Duplicate mutation | Return original receipt; do not apply twice |
| Interrupted page/request | Resume from committed checkpoint or replay mutation |
| Expired incremental history | Rehydrate a fresh snapshot while preserving pending local mutations |

**Gate:** second clean installation restores the complete cloud ledger, including audit/tombstones; more than one server page is exercised. Concurrent edits, interrupted hydration, clock skew and pending local edits do not lose data.

### Phase 7 — Emulator QA and release hardening

Run the following against an isolated backend. Store the test dataset, steps, expected outcomes, actual outcomes and defects. All scenarios start unchecked; the 40 baseline tests do not constitute a full QA pass.

| # | Acceptance scenario | Minimum evidence |
| --- | --- | --- |
| 1 | All screens, navigation, empty/error/loading states | Screenshots on both primary AVDs |
| 2 | Gujarati/English, long names, digit input | Visual check + parser assertions |
| 3 | First-launch Light; Dark immediate and persistent | Restart/rotation recording |
| 4 | 56dp targets, 200% fonts, keyboard, TalkBack | Layout/accessibility check |
| 5 | Fractional bigha/hour/trip, rounding, upper bounds | Exact expected paise assertions |
| 6 | Multiple jobs, linked/general partial receipts, advances | Ledger and dashboard reconciliation |
| 7 | Corrections, soft deletes, audit snapshots | Before/after amounts + immutable history |
| 8 | Invalid/cross-customer links and duplicate taps | Rejected write/no duplicate proof |
| 9 | Fresh startup offline; offline saves | Persisted rows after relaunch |
| 10 | Process death, reboot, low memory, force-stop/reopen | Queue recovery and no missing rows |
| 11 | Parent ordering and seeded tractor upload | Server FK/receipt evidence |
| 12 | Server commit with lost response; duplicate replay | One remote mutation and row |
| 13 | Mid-upload disconnect, 429/5xx/timeouts | Retained queue and eventual confirmation |
| 14 | Wrong password, expired/revoked tokens, long offline | Truthful auth state; local writes unaffected |
| 15 | Two-user/anonymous RLS and cross-owner FK attacks | SQL/API assertions |
| 16 | Empty-device hydration, multiple pages, restart mid-pull | Counts, totals, FK/audit comparison |
| 17 | Both-device edit and delete conflicts, clock skew | Both versions retained; audited resolution |
| 18 | v1/v2 upgrade to latest schema | Migration validation + totals/FK checks |
| 19 | Export/restore, corrupt file/wrong password | Clean-install recovery and atomic rejection |
| 20 | CSV and ledger sharing; WhatsApp absent/present | Correct content and chooser handoff; sending not automated |
| 21 | Low storage, failed local transaction, slow network | No partial accounting write; actionable error |
| 22 | Large representative ledger, cold start/list scrolling | Memory/timing capture; no ANR or main-thread DB work |
| 23 | R8-enabled signed release fresh install and upgrade | Same release artifact tested on both AVDs |
| 24 | APK/Git inspection | No privileged keys/tokens; not debuggable; config verified |

Set an initial stress fixture of 1,000 customers, 20,000 work entries, 20,000 payments and 5,000 expenses, generated only in QA. Capture cold start, dashboard refresh, local save latency and peak memory on the low-memory AVD; document measured thresholds before acceptance. Exact financial correctness, no data loss and no ANRs are mandatory; optimize measured bottlenecks rather than inventing performance claims.

Use unit/repository tests for arithmetic and transactions; instrumentation/Espresso for lifecycle/UI and real SQLite migrations; test-backend integration tests for receipts, RLS, auth and hydration. Use a small controllable test transport for deterministic failure timing, then repeat critical failure scenarios against the real isolated Supabase project.

**Gate:** all required scenarios pass with evidence; no open data-loss/security/accounting defects. Resolve product/code lint warnings; document narrowly reviewed dependency advisories without blanket upgrades or blanket suppression. Emulator coverage does not certify MIUI power management or physical sunlight visibility.

### Phase 8 — Backend deployment and private Android release

1. Review/test production migrations and RPC/policies; make a backup of any existing backend data and record the schema version. Apply reviewed migrations through Dashboard or an explicitly authorized Supabase management connection. Application URL/publishable key alone cannot administer the database.
2. Provision/verify the real owner, disable public registration, configure recovery, verify real RLS and confirm Mumbai region. Run a controlled end-to-end production smoke test with clearly identified records; retain financial history rather than cleaning it with hard deletes.
3. Choose a production availability/backup policy using current Supabase plan terms. Do not treat a cron job inside the same paused project as a recovery guarantee. Offline accounting remains available during an outage, with honest pending status.
4. Configure release signing using a protected keystore outside Git and locally supplied passwords. Back up the signing key securely; future updates need the same identity. Enable R8 and resource shrinking; keep only necessary SDK/serialization rules and inspect warnings.
5. Build/test `assembleRelease`, release unit tests and `lintRelease`, plus release instrumentation where configured. Install the signed, minified APK on the two AVDs; verify fresh install and same-key upgrade from the previous signed candidate without clearing data. Verify migrations, login, sync, share and restore in the minified build.
6. Inspect the exact final APK: signature via `apksigner verify`, package/version/minSdk, `debuggable=false`, production endpoint, and no privileged credentials. Publishable URL/key are intentionally extractable and protected by RLS. Archive the APK SHA-256, signing certificate fingerprint, mapping file, source revision, dependency versions, SQL versions and QA report.
7. Deliver the signed APK privately with a short Gujarati + English installation/backup/recovery guide and release notes. Installation on the Redmi happens only as the owner's eventual handoff, not as a development/QA prerequisite. Public Store release, if requested later, adds its then-current target API, bundle, policy and listing requirements.
8. Keep a recovery runbook for revoked sessions, stalled queues, conflicts, unavailable Supabase and lost phone. Prefer a forward-fix release; never downgrade/delete Room to recover from a bad release. Retest recovery on Mac before suggesting it to the owner.

**Gate:** the delivered signed artifact, production schema and tested source revision are traceable; clean-install cloud restore and an independent backup restore both pass; no release claims exceed the recorded evidence.

## Work organization and decisions

Implement in small reviewable slices: financial safeguards/migration → existing UI fixes → missing local workflows → backup/restore → cloud schema/protocol → auth/upload → hydration/conflicts → QA/release. Keep an evidence tracker with `planned`, `implemented`, `automated verified`, `emulator verified`, `backend verified`, and `blocked` states; an unchecked runtime check stays unchecked.

Recommended responsibility boundaries within this single app module: retain `data/local`, `data/repository`, `domain` and `presentation`; add focused `data/remote/{auth,dto}`, `data/sync`, `data/backup` and corresponding interfaces/use cases only as needed. No new custom backend service, generic sync framework or multi-module rewrite is required.

Default decisions: private APK; one bound owner; explicit conflict resolution; no client hard delete; full encrypted backup plus CSV; one emulator at a time; no Redmi development connection. Inputs needed later: real/test Supabase configuration and owner provisioning, protected signing identity, backup recovery password handling, and eventual confirmation of the owner's business conventions. Record bigha as the owner's entered unit without inventing a universal area conversion; specify whether zero-rate jobs are allowed and how business date/period totals are presented before finalizing forms.

The first implementation slice is **Phase 0, followed by local integrity fixes**. A new screen or live cloud connection should not conceal the existing gaps.

## Official implementation references

- [Android AVD configuration](https://developer.android.com/studio/run/managing-avds): profile, RAM, heap and system-image configuration; confirm the installed versions during setup.
- [Android emulator acceleration](https://developer.android.com/studio/run/emulator-acceleration): host-compatible image/acceleration setup.
- [Supabase Kotlin quickstart](https://supabase.com/docs/guides/getting-started/quickstarts/kotlin): dependency/serialization/network setup. The current example lists Kotlin 2.2.21, Supabase BOM 3.5.0 and Ktor 3.0.3; validate the selected release requirements rather than copying them into this Kotlin 2.0.21 project.
- [Supabase API keys](https://supabase.com/docs/guides/getting-started/api-keys): publishable client credentials versus privileged keys.
- [Supabase database security](https://supabase.com/docs/guides/database/secure-data) and [RLS](https://supabase.com/docs/guides/database/postgres/row-level-security): grants, owner policies and meaningful isolation tests.
- [WorkManager constraints and backoff](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work): persistent scheduling with system-controlled execution timing.
