# KhataVahi

A private native Android account book for tractor work in Gujarat. Kotlin, XML/ViewBinding, Material Components, Room, coroutines, WorkManager and Supabase. Gujarati and simple English are separate selectable languages; Light is the default, with an explicit persistent Dark toggle.

## Current implementation

- Customers, spelling-tolerant local search, possible-duplicate warnings and identity-safe customer pickers.
- Tractor work with Big Tractor and Bigha defaults, partial/customer-level payments, advances, expenses and ledgers.
- Integer paise and scaled quantities, input validation, guarded submissions and local-save acknowledgements.
- Automatic bounded uploads, incremental downloads, durable request envelopes and server revision/receipt checks.
- Supabase is authoritative for confirmed records. Room is the offline working projection and holds pending proposals. A network connection is never required to save locally.
- Confirmed soft deletion with reason/snapshot history, visible record actions and deleted-record history. Remote tombstones cannot resurrect through stale uploads. Customers with active work/payments cannot be removed until those records are handled explicitly.
- Static skeleton/loading, empty/error/retry states; no ads, analytics or persistent foreground service.

This is an implementation under verification, not a certified Play Store release. Own-farm allocations/seasons and independent encrypted export/restore remain planned; see [OWN_FARM_PLAN.md](OWN_FARM_PLAN.md). Full remote conflict resolution, large clean-install restoration and long-offline refresh still need end-to-end qualification.

## Build

Use JDK 21 and Android SDK platform 36. Configure your own SDK path in ignored `local.properties` and select JDK 21 with `JAVA_HOME` or Android Studio. Compatible versions are pinned in `gradle/libs.versions.toml`; no machine-specific JDK path is committed.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew assembleRelease bundleRelease lintRelease
```

Release artifacts currently build unsigned. Configure a protected upload/signing key before distribution. Never commit a keystore or its passwords.

## Local configuration

Android reads public client configuration from `supabase.properties`, with environment variables and ignored `secrets.properties` taking precedence. These publishable values are intentionally included in APKs; access remains protected by owner authentication and RLS. Builds reject missing configuration and privileged keys. No database password or privileged API key belongs in BuildConfig/APK. Without cloud configuration/login, local accounts remain usable.

Mac-only database checks use ignored `.env`; copy `.env.example` and set `DATABASE_URL` locally. This file is never read by the Android build.

```sh
python3 scripts/check_database.py
python3 scripts/verify_schema_contract.py
```

The scripts print schema/security metadata, not credentials or financial records. `scripts/apply_schema.py` is only for an empty project's initial schema; it refuses existing app tables. Later migrations are reviewed and applied in filename order through controlled PostgreSQL administration. Never replay the initial schema over an existing ledger.

## Data safety and recovery

Clear cache does not target Room databases, DataStore preferences or durable outbox/envelope files. Force-stop suspends all background work until reopening; retained pending work resumes through WorkManager. Signing out removes authentication state, not the local ledger; signing back in requests a cloud refresh. Clear storage/uninstall destroys local-only unsynced data; only server-confirmed records can be restored from Supabase. Independent export/restore is not yet implemented.

The app requests network access/state and the normal wake/boot capabilities required by persistent jobs. It does not request contacts, storage, SMS, call, location, camera, notification or battery-optimization exemptions. AndroidX's signature-only receiver permission protects internal broadcasts.

## Verification and remaining release work

- [Deletion/cache/force-stop verification](docs/qa/2026-11-11-deletion-and-recovery.md)
- [Google Play readiness audit](docs/PLAY_READINESS.md)
- [Automatic synchronization](docs/auto-sync.md)
- [Customer matching](docs/qa/2026-11-11-customer-search.md)

SQL fixtures in `supabase/tests` are for isolated test databases only. Run local_bootstrap, then migrations `20261110000001_initial_schema.sql`, `20261111000001_security_and_contract_hardening.sql`, `20261111000002_incremental_sync_changes.sql`, then the change_feed test. Next apply `20261111000003_remote_deletion_tombstones.sql` and `20261111000005_confirmed_mutations.sql`, then the authority test. Do not run fake-identity fixtures against the owner project.
