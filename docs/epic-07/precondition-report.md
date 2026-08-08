# EPIC-07 前置审计报告

审计基线：`zhouxingxing1279/LifeTrace@3e358256252631fa6b03fdcfa785d85dd9208293`。

## READY

- Android App ID：`lifetrace-finance-android`；平台：`android`。
- Finance Android 服务端授权范围：`account:read`、`devices:read`、`sync:read`、`sync:write`、`finance:read`、`finance:write`。
- Native Auth：`/api/v1/auth/login`、`refresh`、`logout`、`me` 已存在。
- Sync v1：`capabilities`、`push`、`pull`、`snapshot` 已存在；服务端校验请求 App ID 与 Token App ID 一致。
- 当前 Finance 同步实体：account、category、transaction、transaction_evidence。
- Wire 金额为整数分；Cursor/serverVersion/baseServerVersion 为字符串；时间 RFC3339 UTC；localDate 独立为 YYYY-MM-DD。
- Transaction type：expense/income/transfer/refund/fee；status：candidate/provisional/confirmed/ignored。

## Android 侧需要补齐（本仓实现）

- Room 本地数据库和 LocalProfile；
- 业务写入 + Sync Outbox 同事务；
- Kotlin Push/Pull/Snapshot/Conflict/Tombstone；
- WorkManager 自动同步；
- Android Keystore Refresh Token；
- NotificationListenerService、Tile、Shortcut、Widget、Share Receiver；
- Compose 财务 UI、报表、诊断。

## DEFERRED_TO_EPIC06

当前没有真实微信/支付宝/银行卡账单样本，因此正式账单文件解析、真实账单去重率、正式对账和特殊账单端到端验证不作为 EPIC-07 门禁。Android 端只保留稳定集成边界，不创建不存在的 `finance.budget`、`finance.subscription` 或 Android 私有云端协议。

## 风险结论

最关键的实现约束是本地 `LocalProfileId` 与云端 `CloudUserId` 分离。Pull/Snapshot 时绝不能把 payload 的 `meta.userId` 当成本地 Profile 主键；服务端所有权来自认证 Principal。本仓同步映射已按该规则实现。
