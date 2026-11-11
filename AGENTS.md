# Farmer Accounts — Project Rules

## Project Identity
Build a private, single-owner Android accounting
application for a farmer in Gujarat, India.

This is a practical utility application, not an ERP.

## Technology
- Native Android only.
- Kotlin.
- XML layouts and ViewBinding.
- Material Components for Android.
- MVVM + Repository architecture.
- Supabase PostgreSQL is authoritative for confirmed cloud records.
- Room + SQLite is the offline working projection with durable pending changes.
- Kotlin Coroutines and Flow.
- WorkManager for persistent sync.
- Supabase PostgreSQL and Auth.
- Supabase Kotlin SDK and Ktor.
- DataStore for preferences.
- Manual dependency injection.
- Gradle Kotlin DSL and version catalog.

Do not use:
- Jetpack Compose.
- Flutter.
- React Native.
- Rust or JNI.
- Firebase.
- Redis.
- Next.js.
- Unnecessary libraries or abstractions.

## Product and UX
- Support Gujarati and simple English through a persistent language toggle.
- Show only the selected language on each screen; do not combine translations.
- Keep English labels short and easy to understand.
- Default theme must be Light.
- Dark mode must be an explicit Settings toggle.
- Persist theme preference using DataStore.
- Theme changes must apply immediately.
- High contrast and excellent sunlight readability.
- Body text should generally be at least 18sp.
- Interactive controls should have at least 56dp
  touch height.
- Large readable numbers.
- Simple forms and minimal navigation.
- No unnecessary animations, gradients or clutter.
- No advertisements or analytics.

## Architecture
- Supabase is authoritative for confirmed records; Room drives the offline UI.
- Remote tombstones invalidate active local projections without discarding pending-change history.
- Never require network access to save a record.
- All local writes must be transactional.
- All cloud changes must use a durable outbox.
- UUIDs must be generated locally.
- Respect foreign key dependencies.
- Make synchronization idempotent.
- Never report cloud synchronization as complete
  before server confirmation.
- Support full cloud hydration on a new device.
- Use soft deletes and record versioning.
- Never use destructive Room migrations.

## Financial Integrity
- Represent money using Long integer paise.
- Never use Float or Double for currency.
- Represent land quantity using scaled integers.
- Perform deterministic rounded arithmetic.
- Payments may be customer-level or work-linked.
- Support partial payments and customer advances.
- Never silently delete financial records.
- Maintain an auditable history of corrections.

## Security
- Enable RLS on every exposed Supabase table.
- Every database row must be scoped to the owner.
- Never put service_role or secret keys in the APK.
- Never commit secrets.properties.
- Never execute unreviewed destructive SQL.
- Persist authentication sessions securely.
- Do not require authentication to view or save
  already available local records.

## Engineering Rules
- Keep modules small and responsibilities clear.
- Separate Room entities and network DTOs.
- Use @Serializable for network DTOs.
- Export Room database schemas.
- Keep migrations in version control.
- Pin compatible stable dependency versions.
- Do not upgrade dependencies unnecessarily.
- Avoid overengineering.
- Test business logic and database operations.

## Verification
After each implementation phase:
1. Run Gradle compilation.
2. Run unit tests.
3. Run Android lint.
4. Inspect compiler warnings and errors.
5. Verify important operations manually.
6. Fix all introduced errors before proceeding.

Never claim a feature works unless it has been
implemented and verified.

