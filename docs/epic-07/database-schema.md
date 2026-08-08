# Room 数据模型

核心表：`local_profiles`、`active_profile`、`finance_accounts`、`finance_categories`、`finance_transactions`、`finance_transaction_evidence`、`sync_outbox`、`sync_state`、`sync_conflicts`。

Device-local 表：`notification_events`、`diagnostic_events`。

关键规则：

- 金额 `amount_cents INTEGER/Long`，禁止 Double 作为账务真值。
- `finance_transactions` 对 profile/date/account/category/status 建索引。
- 所有可同步本地写入通过 `RoomDatabase.withTransaction` 同时写业务实体和 Outbox。
- Remote Apply 直接走 DAO，不产生 Outbox。
- 删除使用 `deleted_at` + delete Outbox，不做立即物理删除。
- Room schema export 已开启，CI 编译时输出到 `app/schemas`。
