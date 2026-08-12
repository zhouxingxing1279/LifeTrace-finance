# BeeCount full feature parity matrix

Reference baseline: `TNT-Likely/BeeCount` public repository, re-audited 2026-08-13; keep this matrix synchronized with upstream public user-facing features.

LifeTrace Finance uses an independent Kotlin/Jetpack Compose implementation and the existing
LifeTrace Cloud protocol. The reference repository is used to identify user-visible behavior,
not as a source folder copied into this project.

## Already available

- Local-first transaction storage, expense/income/transfer editing and search.
- Multiple ledgers and accounts, hierarchical category data, tags and attachments.
- Monthly/category analytics, budgets and recurring transactions.
- WeChat/Alipay/bank CSV and XLSX import.
- Notification capture, screenshot monitoring and Vision extraction.
- LifeTrace authentication, offline outbox, snapshot/push/pull sync and conflict handling.
- Quick Settings entry point and Android home-screen widget.

## Phase 1 — navigation and reliability

- [x] Server settings opens from the Mine page.
- [x] Login is available after reinstall and triggers cloud sync.
- [x] Sync explains the unauthenticated state instead of silently doing nothing.
- [x] Category management and About entries no longer use empty callbacks.
- [ ] Audit every visible control with UI/navigation tests.
- [ ] Show active sync progress, success time and actionable failure details.

## Phase 2 — complete local bookkeeping surfaces

- [x] Account details, balance correction, six-month account trend and current-ledger net-worth trend with internal transfers cancelled.
- [~] Parent/child category create/edit/archive, same-level ordering and icon picker are complete; category migration remains.
- [x] Calendar/month/day view and advanced date/type/category/account filtering.
- [x] Budget overview/create/edit/delete, usage progress and threshold/overrun notifications.
- [x] Recurring transaction list/create/edit/delete, enable/disable and daily/weekly/monthly/yearly generation.
- [x] Tag create/edit/color/archive, transaction assignment and tag filtering.
- [x] Local transaction attachment add/preview/delete with cloud metadata sync.
- [x] Monthly/year/all-time income/expense category ranking and annual monthly trend.
- [x] Product scope intentionally uses CNY only; all creation/editing paths reject non-CNY values per user requirement.
- [~] Current-ledger CSV export and full local database/attachment backup/validated restore are complete; selective YAML configuration portability remains.

## Phase 3 — smart capture

- [ ] Camera/gallery OCR with local and configured remote engines.
- [ ] Voice bookkeeping with review-before-save.
- [ ] Conversational AI bookkeeping and editable prompt/provider/model management.
- [ ] Android automatic screenshot capture permission and status workflow.
- [~] BeeCount-style candidate review is wired into the active UI, with local classification suggestions and import/manual duplicate merge; explicit candidate-to-candidate merge remains.

## Phase 4 — personalization, privacy and platform

- [~] Light/dark/system modes and four theme colors are complete; custom header skins remain.
- [ ] Simplified Chinese, Traditional Chinese and English resources.
- [ ] Font size preferences and localized date/number formats.
- [~] Screenshot/recording/recent-task privacy protection is complete; PIN/biometric lock remains.
- [x] Configurable daily bookkeeping reminder, shortcut guide, searchable/filterable/exportable log center and detailed safe storage management.
- [ ] Six widget content families and responsive widget sizes.

## Phase 5 — LifeTrace Cloud extensions

- [ ] Near-real-time multi-device updates.
- [ ] Shared ledgers, invite/join, owner/editor roles and member statistics.
- [ ] Device/session management.
- [ ] Encrypted export/backup destinations supported by LifeTrace Cloud.
- [ ] Web/API parity for new entities and operations.

## Acceptance

Each checkbox requires a working UI entry, persisted behavior, unit/integration coverage where
appropriate, successful Android build/lint, and a real-device smoke test. A decorative screen or
an empty click handler does not count as implemented.
