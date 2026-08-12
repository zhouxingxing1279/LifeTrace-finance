# LifeTrace Finance Android — BeeCount-inspired bookkeeping domain

Status: execution plan
Branch: `feature/beecount-domain-model`
Cloud counterpart: `zhouxingxing1279/LifeTrace` branch `feature/beecount-domain-model`
Reference: `TNT-Likely/BeeCount`

## Non-negotiable backend rule

The Android app synchronizes to the **existing LifeTrace Cloud** only. There is no new backend, database service, auth flow, sync API or BeeCount Cloud dependency.

Flow remains:

`Room -> sync_outbox -> existing LifeTrace /api/v1/sync/push -> existing LifeTrace Cloud -> pull/snapshot -> Room`

## Scope

Reproduce BeeCount's useful single-user bookkeeping core:

- ledgers
- richer accounts
- two-level/hierarchical categories
- expense/income/transfer/refund transactions
- recurring transactions
- tags + transaction-tag relation
- budgets
- attachment metadata
- multi-currency transaction snapshots
- exclude-from-stats / exclude-from-budget flags
- hidden accounts
- existing smart screenshot/notification/import evidence

Not included in this pass: shared-ledger/member collaboration tables, BeeCount Cloud, BeeCount AI chat history, cloud exchange-rate cache.

## Room v2 target

### New entities

- `LedgerEntity` -> `finance_ledgers`
- `RecurringTransactionEntity` -> `finance_recurring_transactions`
- `TagEntity` -> `finance_tags`
- `TransactionTagEntity` -> `finance_transaction_tags`
- `BudgetEntity` -> `finance_budgets`
- `TransactionAttachmentEntity` -> `finance_transaction_attachments`

### Existing entity extensions

`AccountEntity`:
- `ledger_id`
- `sort_order`
- `credit_limit_cents`
- `billing_day`
- `payment_due_day`
- `bank_name`
- `note`
- `is_hidden`

`CategoryEntity`:
- `ledger_id`
- `sort_order`
- `level`
- `icon_type`
- `custom_icon_file_id`

`TransactionEntity`:
- `ledger_id`
- `recurring_transaction_id`
- `exclude_from_stats`
- `exclude_from_budget`
- `native_amount_cents`
- `native_currency`
- `exchange_rate`

All new synced entities carry the same local/cloud metadata pattern already used by current entities: `id`, `local_profile_id`, timestamps, delete marker, local version, server version; where relevant `modified_by_device` is retained.

## Migration v1 -> v2

- Preserve all current rows.
- Insert one deterministic/default personal ledger per local profile when a profile has financial data but no ledger.
- Backfill existing accounts/categories/transactions to that profile's default ledger.
- Preserve stable current string IDs; do not convert to auto-increment IDs.
- Add indexes for ledger scope, hierarchy, recurrence, budget/category lookup and transaction-tag uniqueness.
- Keep the existing v1 schema export and generate v2 schema export through Room/KSP in CI/build.

## Sync mapping

| Room | LifeTrace entity type |
| --- | --- |
| `finance_ledgers` | `finance.ledger` |
| `finance_accounts` | `finance.account` |
| `finance_categories` | `finance.category` |
| `finance_transactions` | `finance.transaction` |
| `finance_recurring_transactions` | `finance.recurring_transaction` |
| `finance_tags` | `finance.tag` |
| `finance_transaction_tags` | `finance.transaction_tag` |
| `finance_budgets` | `finance.budget` |
| `finance_transaction_attachments` | `finance.transaction_attachment` |
| `finance_transaction_evidence` | `finance.transaction_evidence` |

`LifeTraceContract.FINANCE_ENTITY_TYPES`, `SyncEngine.setServerVersion`, remote deletion and `RemoteMapper` will be extended to cover every row above. Pull/snapshot remain the existing LifeTrace APIs.

## Money semantics

LifeTrace keeps integer cents. BeeCount's `double` money fields are not copied literally.

- `amount_cents`: transaction amount in transaction/account currency
- `currency`: transaction currency
- `native_amount_cents`: frozen converted amount in ledger base currency
- `native_currency`: ledger base currency used for the snapshot
- `exchange_rate`: decimal string snapshot when conversion occurred

This avoids floating-point money on the wire and preserves the existing LifeTrace contract invariant.

## Feature semantics

- Transfer: one transaction with `transactionType=transfer`, `accountId` and `toAccountId`.
- Category hierarchy: `parentId` + `level`; no device-local parent integer IDs.
- Tags: normalized many-to-many relation; not embedded as mutable JSON on a transaction.
- Budgets: total/category budgets; monthly/weekly/yearly period and start day.
- `excludeFromStats`: excluded from income/expense statistics only.
- `excludeFromBudget`: excluded from budget consumption independently.
- Hidden account: excluded from ordinary picker/list but still available for balance/net-worth calculations and historical records.
- Evidence remains separate from attachments: evidence describes capture/import provenance; attachment describes a file linked to a transaction.

## Execution order

1. Cloud contract branch first: add/validate the entity types while preserving existing sync protocol.
2. Room v2 entities + migration + DAO.
3. Repository CRUD and outbox generation.
4. Sync mapper/version/delete handling.
5. Domain statistics/budget/tag/recurrence behavior.
6. Minimal UI wiring needed to make the features usable.
7. Unit tests + migration/contract tests + Android CI.
8. Update completion report; merge only after both repo branches pass tests.

## Tests required

- Room v1->v2 migration preserves existing accounts/categories/transactions/evidence.
- default ledger backfill is deterministic per local profile.
- nested category round-trip.
- transfer round-trip with both accounts.
- tags have unique transaction/tag relation and round-trip.
- budget consumption honors `excludeFromBudget`.
- statistics honor `excludeFromStats`.
- recurring rule serialization/round-trip.
- attachment metadata round-trip without creating a new storage backend.
- all new entity types are included in pull and snapshot filters.
- conflicts/deletes/serverVersion updates work for every new entity.

## Merge gate

No merge to `main` until Android CI and LifeTrace Cloud/contracts CI are green. If either side is not deployable/compatible, keep both changes on the feature branches.
