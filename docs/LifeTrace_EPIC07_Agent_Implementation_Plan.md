# LifeTrace EPIC-07：LifeTrace Finance Android App —— Agent 具体实施方案

> 目标仓库：`zhouxingxing1279/LifeTrace-finance`  
> 目标分支基线：`main`  
> Android 包名：`com.lifetrace.finance`  
> 云端 App ID：`lifetrace-finance-android`  
> 上游主仓库：`zhouxingxing1279/LifeTrace`  
> 上游路线图：`docs/LifeTrace_Complete_Roadmap_v2.md`（文件正文当前为 v3）  
> **硬前置依赖：EPIC-04 账号/认证/设备管理、EPIC-05 同步协议与云端同步能力。**  
> **非阻塞依赖：EPIC-06 手工记账/自动记账/正式账单导入与对账。EPIC-06 仅影响正式账单导入、对账、合并和真实账单验证，不阻塞 EPIC-07 Android 主体开发与完成。**  
> 当前现实约束：**目前没有可用于 EPIC-06 真实账单导入与对账验证的微信/支付宝/银行卡账单样本，因此 EPIC-06 的真实数据验收延后；不得因为缺少账单样本阻塞 EPIC-07。**  
> 核心原则：**本地优先、离线可写、金额整数分、Android 原生、统一领域语义、与主仓库契约严格一致、通知最小采集、同步可恢复、凭据不落明文、3 秒快速记账、不可静默丢账。**

---

# 1. EPIC-07 最终目标

EPIC-07 完成后，`LifeTrace Finance` 应成为一个可以独立安装、独立登录、独立离线运行、独立同步和独立发布的 Android 财务客户端。

完整主链路：

```text
用户手工记账 / 通知捕获 / 快捷入口
        ↓
Android Domain Service
        ↓
Room 本地数据库
        +
同一事务写入 Sync Outbox
        ↓
界面立即更新，不等待网络
        ↓
WorkManager 驱动 Push / Pull / Snapshot
        ↓
LifeTrace Cloud
        ↓
Windows / Web / 其他已授权客户端
```

EPIC-07 必须做到：

1. 普通手工支出、收入和转账可以在约 3 秒内完成。
2. 无网络时仍可记账、修改、删除和查看本地数据。
3. Android 本地业务写入与 Sync Outbox 原子提交。
4. 网络恢复后自动同步，无需手动点击同步。
5. Access Token 过期自动刷新，Refresh Token 受 Android Keystore 保护。
6. Finance App 只能访问 Finance 权限范围内的实体。
7. 通知监听仅采集记账所需最少信息，可关闭、可清理、可诊断。
8. 通知识别产生 Candidate/Provisional，不允许解析误判直接形成不可逆正式账目。
9. Android 新增/修改的基础财务数据可以同步到 Windows/Web，反向修改也可以同步回来。
10. 冲突不会静默覆盖，删除不会被旧离线设备重新复活。
11. 快速记账、最近账单、账户、分类、待确认箱、报表、同步状态均有明确入口。
12. Quick Settings Tile、App Shortcuts、Home Screen Widget、Share Receiver 提供快速输入能力。
13. Release 构建可重复，签名密钥和生产凭据不进入 Git。
14. 请求前异常、HTTP 异常、DTO 解析异常、Room 异常、WorkManager 异常可以通过诊断日志区分。

以下能力**不再作为 EPIC-07 完成的硬门禁**：

```text
微信正式账单文件导入
支付宝正式账单文件导入
银行卡流水文件导入
正式账单与手机暂记账自动匹配
真实账单去重率验证
真实账单对账准确率验证
退款/红包/待收款等依赖真实账单样本的端到端验证
```

这些能力属于 EPIC-06 的正式账单与对账闭环。EPIC-07 只需要预留清晰的集成边界。

---

# 2. 依赖重新定义

## 2.1 硬依赖：EPIC-04

EPIC-04 提供：

- Native 登录、Refresh、Logout；
- App Grant + Scope；
- Device / Session / Token；
- Refresh Token 轮换与重放检测；
- `lifetrace-finance-android` 独立 App 身份；
- 服务端 Scope 校验。

EPIC-07 必须直接消费真实 EPIC-04 接口，不重新设计认证协议。

## 2.2 硬依赖：EPIC-05

EPIC-05 已验证同步语义：

- Outbox；
- Push；
- Cursor Pull；
- Snapshot；
- Conflict；
- Tombstone；
- Retry；
- 401 Refresh；
- 429 Retry-After；
- 413 拆批；
- LocalProfileId 与 CloudUserId 分离。

EPIC-05 没有实现 Android/Kotlin/Room/WorkManager，因此 EPIC-07 负责实现 Android 版本 Sync Client，但行为必须与 EPIC-05 协议一致。

## 2.3 EPIC-06 改为“延迟集成依赖”

EPIC-06 不再作为 EPIC-07 开工或完成的前置条件。

两者职责拆分如下：

```text
EPIC-06：财务业务与对账能力
├── 正式账单文件解析
├── 微信/支付宝/银行卡账单导入
├── 候选账与正式账单匹配
├── 去重评分
├── 对账与合并
├── 特殊交易语义
├── 用户分类/备注保留规则
└── 真实账单数据验证

EPIC-07：Android Finance 客户端
├── Kotlin / Compose App
├── Room 本地数据库
├── Android Auth
├── Android Sync Engine
├── 手工记账
├── NotificationListenerService
├── 本地 Candidate Pipeline
├── 待确认箱
├── Tile / Shortcut / Widget
├── 报表
└── EPIC-06 后续集成接口
```

因此：

```text
EPIC-07 可以先完成
        ↓
以后获得真实账单
        ↓
完成 EPIC-06 账单解析/对账验证
        ↓
通过稳定 Contract 接入 EPIC-07
```

不得采用：

```text
没有真实账单
→ EPIC-06 未完成
→ EPIC-07 停止开发
```

---

# 3. 当前“没有真实账单样本”的处理原则

目前无法取得用于 EPIC-06 真实验收的账单文件，因此采用三层测试策略。

## 3.1 EPIC-07 可以使用合成 Fixture

允许为了验证 Android 自身逻辑创建明确标记为 synthetic 的固定样本，例如：

```text
微信支付通知：支付成功 25.80 元
支付宝支付通知：付款 36.00 元
银行卡消费通知：尾号 1234 消费 108.50 元
退款通知
重复通知
缺失金额通知
营销通知
聊天通知
```

这些 fixture 只能验证：

- Notification normalization；
- Parser pipeline；
- 金额解析；
- Candidate 创建；
- Confidence；
- Dedup；
- 正/负样本不会误入账；
- UI 与 Room 状态流转。

## 3.2 合成 Fixture 不能证明 EPIC-06 已完成

禁止用 synthetic fixture 声称：

```text
微信真实账单文件已兼容
支付宝真实账单文件已兼容
银行真实 CSV/Excel 已兼容
真实账单自动匹配准确率已达标
真实账单去重已通过生产验证
```

这些结论必须等待真实、脱敏的账单样本。

## 3.3 后续获得账单时补做 EPIC-06

未来拿到真实账单后：

1. 脱敏后建立受控测试 Fixture；
2. 解析字段和格式；
3. 建立 importer regression tests；
4. 测试重复导入；
5. 测试手机暂记账与正式账单匹配；
6. 测试退款、转账、红包等特殊场景；
7. 完成 EPIC-06 真实数据验收；
8. 如需新 Contract，在 `LifeTrace` 主仓定版；
9. EPIC-07 更新 upstream snapshot 后接入。

---

# 4. 当前 Finance 上游契约

Agent 开工必须从 `LifeTrace` 主仓重新审计，不得凭本文假定最新状态。

当前至少已有：

```text
finance.account
finance.category
finance.transaction
finance.transaction_evidence
```

当前 `finance.transaction` 至少包含：

```text
transactionType
amountCents
currency
accountId
toAccountId
categoryId
counterparty
merchant
item
note
occurredAt
localDate
status
sourceType
externalTransactionId
```

类型语义：

```text
TransactionType:
expense / income / transfer / refund / fee

TransactionStatus:
candidate / provisional / confirmed / ignored
```

金额硬规则：

```text
Long amountCents
```

禁止在真实金额模型中使用：

```text
Double amount
Float price
SQLite REAL amount
```

---

# 5. 范围边界

## 5.1 EPIC-07 必须实现

### App 基础

- `com.lifetrace.finance`；
- Kotlin Android 原生工程；
- Jetpack Compose；
- 独立登录；
- 独立 Room 数据库；
- 独立 Android Sync Client；
- 独立版本和发布流程；
- Finance Scope；
- Light/Dark Theme；
- 大字体和基础无障碍适配。

### 基础财务能力

- 快速支出；
- 快速收入；
- 转账；
- 最近账单；
- 账单详情；
- 编辑/删除；
- 账户；
- 分类；
- Candidate 待确认箱；
- 本地统计和报表。

### Android 原生能力

- `NotificationListenerService`；
- `WorkManager`；
- `Quick Settings Tile`；
- `App Shortcuts`；
- Home Screen Widget；
- `Share Receiver`；
- Android Keystore；
- 可选 AccessibilityService，但首版默认禁用。

### 工程能力

- Contract Snapshot；
- Upstream Lock；
- CI；
- Room migration test；
- Sync contract test；
- Notification parser fixtures；
- 本地诊断日志；
- Release APK/AAB。

## 5.2 EPIC-07 只预留、不阻塞完成的能力

以下标记为：

```text
DEFERRED_TO_EPIC06
```

包括：

- 微信账单文件正式 importer；
- 支付宝账单文件正式 importer；
- 银行 CSV/Excel 正式 importer；
- 正式账单批次 rollback；
- 手机 Candidate 与正式账单自动匹配；
- 正式账单模糊匹配确认；
- 真实账单去重和合并；
- 依赖真实账单才能验证的特殊交易。

它们不得出现在 EPIC-07 的阻塞列表中。

## 5.3 EPIC-07 不做

- 不直接访问 PostgreSQL；
- 不通过 JNI/UniFFI 复用桌面 Rust Sync Runtime；
- 不实现 Notes/English/Habits；
- 不自行新增服务端未知的 `entityType`；
- 不把 Refresh Token 明文放 SharedPreferences/DataStore/SQLite；
- 不上传完整通知原文；
- 不把 AccessibilityService 作为记账必需权限；
- 不因云端失败禁止本地记账；
- 不为了模拟 EPIC-06 而伪造“真实账单验证通过”。

---

# 6. 技术栈

默认：

```text
Language              Kotlin
UI                    Jetpack Compose + Material 3
Navigation            Navigation Compose
Async                  Coroutines + Flow
Database               Room / SQLite
DI                     Hilt
Network                OkHttp + Retrofit
Serialization          kotlinx.serialization
Background             WorkManager
Preferences            DataStore
Secrets                Android Keystore-backed SecureTokenStore
Widget                 AndroidX Glance / AppWidget
Testing                JUnit + Turbine + Room + Compose UI Tests
Static Analysis        Android Lint + ktlint/Spotless + Detekt
Build                   Gradle Kotlin DSL + Version Catalog
```

建议 `minSdk = 26`，最终版本由实施时 Android 稳定生态确定并固定，不允许 `+` 动态版本。

Android 与桌面：

```text
共享协议
不共享运行时
```

必须共享：

```text
EntityType
SchemaVersion
JSON 字段
Money
Time / LocalDate
ChangeId
BaseServerVersion
ServerVersion
Cursor
Tombstone
Conflict
Snapshot
Auth Token Lifecycle
App Grant / Scope
```

---

# 7. 建议仓库结构

```text
LifeTrace-finance/
├── app/
├── core/
│   ├── common/
│   ├── model/
│   ├── database/
│   ├── network/
│   ├── auth/
│   ├── sync/
│   └── ui/
├── feature/
│   ├── quick-entry/
│   ├── transactions/
│   ├── inbox/
│   ├── accounts/
│   ├── categories/
│   ├── reports/
│   └── settings/
├── platform/
│   ├── notifications/
│   ├── shortcuts/
│   ├── tile/
│   ├── widget/
│   └── share/
├── integration/
│   └── epic06/
├── contract-snapshots/
├── docs/
│   ├── LifeTrace_EPIC07_Agent_Implementation_Plan.md
│   └── epic-07/
│       ├── precondition-report.md
│       ├── architecture.md
│       ├── database-schema.md
│       ├── sync-state-machine.md
│       ├── notification-parser.md
│       ├── privacy-model.md
│       ├── deferred-epic06.md
│       ├── test-matrix.md
│       └── completion-report.md
├── tools/
├── .github/workflows/
├── gradle/libs.versions.toml
├── settings.gradle.kts
└── README.md
```

`integration/epic06` 只放适配层，不允许 EPIC-06 业务侵入 Android Core。

---

# 8. Phase 0：前置审计

第一个正式动作：

```text
docs/epic-07/precondition-report.md
```

必须读取：

```text
LifeTrace/docs/LifeTrace_Complete_Roadmap_v2.md
LifeTrace/docs/epic-04/**
LifeTrace/docs/epic-05/**
LifeTrace/crates/lifetrace-contracts/src/domain/finance.rs
LifeTrace/crates/lifetrace-contracts/src/domain/enums.rs
LifeTrace/crates/lifetrace-contracts/src/registry.rs
LifeTrace/crates/lifetrace-contracts/src/sync/v1/**
LifeTrace/crates/lifetrace-contracts/src/auth/v1/**
LifeTrace/services/lifetrace-cloud/src/**
```

如果主仓已有 EPIC-06 内容，也读取，但 **EPIC-06 不通过不停止 EPIC-07**。

报告必须回答：

1. Cloud Base URL 与 API Version；
2. Login/Refresh/Logout/Me/Device 路径；
3. Finance Android App ID 和 Scope；
4. Push/Pull/Snapshot/Capability DTO；
5. Batch/Page 限制；
6. 401/413/429 语义；
7. Cursor Expired 语义；
8. Conflict Payload；
9. 当前 `finance.*` Entity Registry；
10. Schema Version；
11. UUID/UUIDv7 规则；
12. Time/LocalDate 规则；
13. Budget/Subscription 是否已有 Contract；
14. EPIC-06 当前有什么、缺什么；
15. 哪些能力因缺少真实账单样本暂时无法做生产级验证。

Gate 只使用：

```text
READY
可接真实 Cloud

LOCAL_READY
本地能力可以完整实现，Cloud Contract 后补

DEFERRED_TO_EPIC06
属于正式账单/对账能力，延后集成，不阻塞 EPIC-07
```

**不再使用 `BLOCKED_BY_EPIC06` 作为 EPIC-07 总体状态。**

---

# 9. Contract 固定与防漂移

新增：

```text
contract-snapshots/upstream.lock
```

至少记录：

```text
repository = zhouxingxing1279/LifeTrace
commit = <audited-main-commit>
contract_schema_version = <version>
updated_at = <UTC>
```

同步保存/生成：

- Auth Schema；
- Sync DTO Schema；
- Finance Schema；
- Entity Registry Snapshot；
- Golden JSON Fixtures。

Kotlin DTO 规则：

- `camelCase`；
- unknown string enum 向前兼容；
- unknown JSON field 可忽略；
- 金额 `Long`；
- `localDate` 独立字段；
- `serverVersion` nullable；
- Tombstone 不等于物理删除。

CI 至少验证：

```text
Finance Account Fixture
Finance Category Fixture
Finance Transaction Fixture
Transaction Evidence Fixture
Push Request/Response Fixture
Pull Fixture
Conflict Fixture
Snapshot Fixture
Auth Login/Refresh Fixture
```

---

# 10. 本地 Profile 与身份

严格区分：

```text
LocalProfileId
CloudUserId
AppId
DeviceId
```

Room 建议：

```text
local_profiles
- id
- profile_type
- cloud_user_id
- display_name
- created_at
- updated_at
```

首次使用：

```text
打开 App
→ 创建 Local Profile
→ 无登录也可本地记账
→ 用户选择登录
→ 绑定 Cloud User
→ Snapshot/Pull/Push 合并
```

登录不得清空本地已有记录。

---

# 11. Room 数据库

必须包含：

```text
local_profiles
active_profile
finance_accounts
finance_categories
finance_transactions
finance_transaction_evidence
sync_outbox
sync_state
sync_metadata
sync_conflicts
snapshot_staging
```

Android Device-Local：

```text
notification_events
notification_parser_state
transaction_drafts
quick_entry_preferences
local_diagnostics
```

Transaction 至少：

```text
id
local_profile_id
transaction_type
amount_cents
currency
account_id
to_account_id
category_id
counterparty
merchant
item
note
occurred_at
local_date
status
source_type
external_transaction_id
created_at
updated_at
deleted_at
local_version
server_version
modified_by_device
```

至少评估索引：

```text
(local_profile_id, local_date DESC)
(local_profile_id, occurred_at DESC)
(local_profile_id, account_id, occurred_at DESC)
(local_profile_id, category_id, local_date DESC)
(local_profile_id, status, occurred_at DESC)
(local_profile_id, external_transaction_id)
```

开启 Room Schema Export，每次 Migration 都要有 migration test。

---

# 12. Domain Service：所有写入的唯一入口

统一 UseCase：

```text
CreateExpenseUseCase
CreateIncomeUseCase
CreateTransferUseCase
UpdateTransactionUseCase
DeleteTransactionUseCase
ConfirmCandidateUseCase
IgnoreCandidateUseCase
ChangeCategoryUseCase
CreateAccountUseCase
UpdateAccountUseCase
CreateCategoryUseCase
```

本地业务 + Outbox：

```kotlin
roomDatabase.withTransaction {
    writeBusinessEntity()
    writeOutboxChange()
}
```

Pull/Snapshot 使用专门 Remote Apply 路径，禁止再次产生 Outbox。

删除使用 Tombstone/`deletedAt`，不能简单物理删除。

---

# 13. Auth 与安全凭据

App ID：

```text
lifetrace-finance-android
```

Token：

```text
Access Token
→ 仅内存

Refresh Token
→ Android Keystore 加密保护

Password
→ 仅登录请求生命周期
```

并发 401 必须 single-flight refresh，避免旋转 Refresh Token 被并发使用造成 replay。

Logout：

- 调服务端 logout；
- 清 Refresh Token；
- 清内存 Access Token；
- 取消账号同步 Work；
- 默认保留本地业务数据。

日志严禁保存密码、Token、完整 Authorization Header。

---

# 14. Android Sync Core

建议：

```text
core/sync/
├── SyncCoordinator
├── PushEngine
├── PullEngine
├── SnapshotEngine
├── ConflictStore
├── SyncScheduler
├── SyncTransport
├── SyncRepository
└── SyncDiagnostics
```

## Push

必须支持：

- changeId 幂等；
- batch；
- baseServerVersion；
- 部分成功；
- conflict；
- Retry；
- 401 Refresh；
- 429 Retry-After；
- 413 缩小 batch；
- 指数退避 + jitter；
- 重启恢复。

## Pull

```text
cursor
→ pull page
→ Room transaction apply
→ 成功后更新 cursor
```

页应用失败：

```text
cursor 不推进
```

## Snapshot

用于新设备、本地重建、Cursor Expired。

必须：分页、Staging、Resume、完成校验、保护未上传本地记录。

## Conflict

保存：

```text
entityType
entityId
localPayload
remotePayload
baseServerVersion
remoteServerVersion
createdAt
resolutionState
```

UI：保留本地 / 使用云端 / 稍后处理。

## WorkManager

触发：

- App Start；
- Local Write Debounce；
- Network Restore；
- Periodic Fallback；
- Manual Sync；
- Refresh Success。

使用 Unique Work，避免 Worker 风暴。

---

# 15. 3 秒快速记账

## 快速支出

默认：

```text
金额输入（自动聚焦）
常用分类 Chips
默认账户
备注/商户（折叠）
保存
```

保存：

```text
Room commit
→ UI 成功
→ 后台 Sync
```

绝不等待网络再显示成功。

## 快速收入

复用金额组件和 Domain Service。

## 转账

```text
fromAccount
→ toAccount
→ amount
```

使用真实 `transfer` 语义，不用“支出 + 收入”拼接模拟。

## 性能验收

- Tile/Shortcut 直接到金额输入；
- 键盘立即可用；
- 默认账户自动选中；
- 常用分类一屏可点；
- 保存不等网络；
- 输入金额后不超过 2 个额外交互动作；
- Macrobenchmark 测 cold/warm start；
- 人工 20 次中位完成时间目标约 3 秒。

---

# 16. 最近账单与详情

列表由 Room Flow 驱动。

筛选：

- 今天/本周/本月；
- 支出/收入/转账；
- 账户；
- 分类；
- Candidate/Provisional/Confirmed；
- 商户/备注搜索。

详情展示：

```text
金额
类型
账户
分类
商户/对方
时间
localDate
来源
状态
备注
证据摘要
同步状态
```

支持编辑、删除、改分类、确认候选、忽略候选、查看冲突。

---

# 17. NotificationListenerService

Android 通知捕获属于 EPIC-07，本身**不依赖真实账单文件**。

设置页显示：

```text
通知读取权限
支持来源
最近捕获时间
最近解析结果
解析失败数
清空通知缓存
```

默认最小采集：

```text
packageName
postTime
title
text
bigText
subText
notification key/hash
```

只处理明确 Package Allowlist。

Pipeline：

```text
Notification
→ Normalize
→ Source Detector
→ Rule Match
→ Amount Extractor
→ Merchant/Counterparty Extractor
→ Account/Channel Hint
→ Confidence
→ Candidate
→ Dedup
→ Persist
```

Parser 必须是纯 Kotlin 可测试代码，不能把正则散在 Service 中。

每次解析记录：

```text
parserId
parserVersion
sourcePackage
confidence
```

状态：

```text
candidate
provisional
confirmed
ignored
```

在 EPIC-06 正式规则尚未定版前：

- 可以本地 Candidate；
- 可以本地一键确认；
- 可以测试 dedup；
- 可以测试通知解析；
- 不实现远程规则下发私有协议；
- 不声称通知 Candidate 与正式账单对账已完成。

完整通知原文默认 Device Local，短期保留，可立即清除。

---

# 18. 待确认箱

聚合：

```text
candidate
provisional
parser failed but recoverable
```

卡片：

```text
金额
商户/对方
来源
时间
建议分类
置信度等级
```

操作：

```text
确认
改分类并确认
忽略
撤销
```

`reconciliation ambiguous` 和正式账单合并操作属于 `DEFERRED_TO_EPIC06`，不阻塞待确认箱基础能力。

---

# 19. Android 快捷入口

## Quick Settings Tile

```text
Tile
→ Quick Expense
→ 金额自动聚焦
```

## App Shortcuts

```text
记支出
记收入
转账
待确认箱
```

## Widget

首版：

```text
+ 支出
+ 收入
待确认数量
今日支出（可选）
```

## Share Receiver

支持文本、截图以及未来账单文件入口。

账单文件在 EPIC-06 Importer 未完成前：

```text
可以接收 URI
→ 保存到私有临时目录
→ 显示“正式账单导入能力尚未启用”
```

不得写一个临时格式冒充正式 importer。

---

# 20. 账户、分类与报表

账户支持：现金、银行卡、微信、支付宝、投资、其他。

分类支持：

- income/expense；
- parent/child；
- system/custom；
- archived。

报表直接从 Room 交易派生：

```text
月支出
月收入
净现金流
分类占比
账户趋势
日/周/月趋势
Top Merchants
```

不需要等待 EPIC-06。

金额聚合始终使用整数分。

---

# 21. Budget / Subscription

Budget/Subscription 是否同步，取决于上游是否已有正式 Contract，**但与 EPIC-06 是否完成无直接绑定**。

若 Contract 已存在：按 Contract 实现。

若 Contract 不存在：

- 可以做 Device Local 草稿/只读推导；
- 不 Push 未注册 EntityType；
- 单独建立上游 Contract Issue；
- 不阻塞 EPIC-07 基础版本发布。

---

# 22. EPIC-06 适配层

提前定义 Android 侧稳定接口，但不实现虚假服务端逻辑。

建议：

```kotlin
interface ReconciliationGateway {
    suspend fun getPendingReconciliations(): List<ReconciliationItem>
    suspend fun confirmMatch(id: String, action: MatchAction): Result<Unit>
}

interface BillImportGateway {
    suspend fun inspect(input: ImportInput): ImportInspection
    suspend fun importConfirmed(input: ConfirmedImport): ImportResult
}
```

在 EPIC-06 未完成时使用：

```text
UnavailableReconciliationGateway
UnavailableBillImportGateway
```

它们只返回明确的 `FEATURE_NOT_AVAILABLE`，不伪造成功。

未来 EPIC-06 定版后替换 Adapter，不修改 Android Core Domain。

---

# 23. 日志与可观测性

统一事件：

```text
DiagnosticEvent
- timestamp
- level
- component
- eventCode
- message
- correlationId
- entityType nullable
- entityId hashed/nullable
- throwableClass nullable
- metadata redacted
```

组件：

```text
AUTH
NETWORK
SYNC_PUSH
SYNC_PULL
SYNC_SNAPSHOT
DATABASE
NOTIFICATION_CAPTURE
NOTIFICATION_PARSE
QUICK_ENTRY
WORK_MANAGER
UI
EPIC06_INTEGRATION
```

例如 Push：

```text
SYNC_PUSH_ENQUEUED
SYNC_PUSH_START
SYNC_PUSH_BATCH_BUILT
HTTP_REQUEST_START
HTTP_RESPONSE
SYNC_PUSH_APPLY_RESULT
SYNC_PUSH_SUCCESS / FAILED
```

这样必须可以区分：

```text
请求构造前失败
DNS/TLS
401
500
DTO Parse
Room Apply
Worker Failure
```

日志环形保存，本地优先，用户主动导出，二次脱敏。

---

# 24. 隐私与安全

- 完整通知原文默认不上传；
- Refresh Token 不进入 Auto Backup；
- Release 禁止 Cleartext HTTP；
- Debug CA 配置不得进入 Release；
- Prod/Staging/Dev Base URL 分离；
- 不在 APK 中硬编码长期 Secret；
- 支付通知数据遵循最小采集；
- 支持清空本地通知缓存。

AccessibilityService：

- 第一版不是必需权限；
- 默认关闭；
- 只有明确场景再实验；
- 不自动点击第三方支付 App；
- 不执行任何资金操作。

---

# 25. 测试策略

所有验收映射到：

```text
docs/epic-07/test-matrix.md
```

## Unit

- Money；
- LocalDate；
- Domain Validation；
- Outbox；
- Notification Normalize；
- Synthetic Notification Fixtures；
- Confidence；
- Dedup；
- Backoff；
- Refresh Single Flight；
- Conflict Mapping。

## Room

- Business + Outbox Atomicity；
- Remote Apply 无 Outbox；
- Migration；
- Tombstone；
- Profile Isolation；
- Cursor Atomicity；
- Snapshot Staging。

## Sync Contract

- Push Success；
- Partial Failure；
- Duplicate ChangeId；
- Conflict；
- 401 + Refresh；
- 429；
- 413；
- Timeout；
- Multi-page Pull；
- Apply Failure；
- Cursor Expired；
- Snapshot Resume；
- Tombstone；
- Unknown Enum；
- Unknown Field。

## Compose / Instrumentation

- Quick Expense；
- Quick Income；
- Transfer；
- Candidate Inbox；
- Permission Guide；
- Offline Write；
- Restart Persistence；
- Login/Logout；
- Sync Status；
- Large Font。

## Notification Parser

当前阶段使用 synthetic fixtures：

```text
模拟微信支付通知
模拟支付宝支付通知
模拟银行卡消费通知
非支付通知
重复通知
退款通知
缺金额
异常 Unicode
```

明确写入测试说明：

```text
这些 fixture 用于 Android Parser 行为回归，
不代表真实账单文件兼容性和真实支付通知覆盖率已经验证。
```

## 性能

- Cold Start；
- Warm Start；
- Quick Entry；
- 1 万笔查询；
- 5 万笔聚合。

---

# 26. EPIC-06 后续真实账单测试计划

当前不执行，但先写明未来步骤。

真实账单获取后：

## 微信

测试：

- 原始导出格式；
- 编码；
- 日期；
- 金额；
- 收/支；
- 商户；
- 交易单号；
- 支付方式；
- 退款；
- 重复导入。

## 支付宝

同样建立 importer fixture 和重复导入测试。

## 银行

按银行分别维护格式，不假定所有 CSV/Excel 一致。

## 对账

至少验证：

```text
externalTransactionId 强匹配
amount + time window
payment channel
merchant similarity
candidate ↔ official bill
用户分类保留
用户备注保留
退款关联
转账不计收支
```

真实数据验收记录属于 EPIC-06，不回写为 EPIC-07 的发布阻塞条件。

---

# 27. CI/CD

PR/Push：

```text
format/ktlint
Detekt
Android Lint
Unit Tests
Contract Fixture Tests
Room Schema Verification
assembleDebug
```

Release：

- signed APK；
- AAB；
- monotonic versionCode；
- SHA256；
- mapping；
- Release Notes；
- signing secret 不入仓库。

Build Flavor：

```text
dev
staging
prod
```

Prod 禁止 cleartext/debug endpoint。

---

# 28. Agent 正式执行顺序

## Phase 0：审计

交付：

```text
docs/epic-07/precondition-report.md
contract-snapshots/upstream.lock
docs/epic-07/deferred-epic06.md
```

`deferred-epic06.md` 明确记录：

```text
当前缺少真实账单样本
哪些 EPIC-06 能力延后
未来需要哪些样本
哪些 Android 集成点已经预留
```

**审计完 EPIC-04/05 即可进入 Phase 1；不得等待 EPIC-06。**

## Phase 1：Android 工程骨架

- Gradle Kotlin DSL；
- Compose；
- Hilt；
- Version Catalog；
- CI；
- Flavors；
- Logging。

Gate：clean checkout 可 test/lint/assemble。

## Phase 2：Contract + Domain

- Auth DTO；
- Sync DTO；
- Finance DTO；
- Money/Time/ID；
- Golden Fixtures。

## Phase 3：Room + Local Domain

- Schema；
- Migration；
- Repository；
- UseCases；
- Outbox Atomicity；
- Recent Queries。

Gate：飞行模式可以完成支出/收入/转账。

## Phase 4：Auth + Device

- Login；
- Refresh；
- SecureTokenStore；
- Logout；
- App Scope；
- Device；
- Profile Binding。

## Phase 5：Android Sync

- Push；
- Pull；
- Snapshot；
- Conflict；
- Tombstone；
- WorkManager；
- Sync UI；
- Diagnostics。

Gate：Android ↔ Cloud ↔ Windows/Web 基础 Finance Entity 往返。

## Phase 6：核心 UI

- Quick Expense；
- Quick Income；
- Transfer；
- Recent；
- Detail；
- Accounts；
- Categories。

Gate：约 3 秒普通支出路径。

## Phase 7：Notification Capture

- Permission；
- Listener Service；
- Allowlist；
- Parser；
- Synthetic Fixtures；
- Candidate；
- Dedup；
- Diagnostics。

**不需要真实账单文件。**

## Phase 8：Candidate Inbox

- Confirm；
- Reclassify；
- Ignore；
- Undo；
- Local Evidence。

正式账单 Match UI 暂不实现或 Feature Gate。

## Phase 9：快捷能力

- Tile；
- Shortcuts；
- Widget；
- Share Receiver。

## Phase 10：Reports + Optional Contract Features

- Reports 完成；
- Budget/Subscription 看上游 Contract；
- 不存在则 Feature Gate。

## Phase 11：EPIC-06 Integration Stub

- `BillImportGateway`；
- `ReconciliationGateway`；
- Feature Availability；
- `DEFERRED_TO_EPIC06` UI 状态；
- 不实现伪数据成功链路。

## Phase 12：Hardening + Release

- Privacy；
- Security；
- Backup；
- Benchmark；
- Test Matrix；
- Release Workflow；
- Completion Report。

---

# 29. 建议提交序列

```text
chore: bootstrap Android project and CI
chore: pin LifeTrace upstream contract snapshot
docs: record deferred EPIC-06 bill validation
feat: add finance domain and Room schema
feat: add local-first transaction services and outbox
feat: add native auth and secure token storage
feat: add Android sync push and pull
feat: add snapshot conflict and tombstone handling
feat: add quick expense income and transfer
feat: add accounts categories and recent transactions
feat: add notification capture and synthetic parser fixtures
feat: add candidate inbox
feat: add quick settings shortcuts and widget
feat: add share receiver
feat: add local finance reports
feat: add EPIC-06 integration gateways and feature gates
chore: harden privacy diagnostics and release pipeline
docs: add EPIC-07 completion evidence
```

以后真实账单可用时，在 EPIC-06 单独提交 importer/reconciliation，不重写 EPIC-07 Core。

---

# 30. Agent 每阶段工作纪律

每个 Phase：

```text
1. 读现有代码和 Contract
2. 更新设计文档
3. 写失败测试 / Fixture
4. 实现最小闭环
5. 局部测试
6. 完整 Gate
7. 检查 Diff
8. 提交
9. 更新 Test Matrix
```

禁止：

- 用 TODO 假完成；
- 用 Mock 长期留生产路径；
- 把金额改成 Double；
- 把 Token 明文落盘；
- 用 Last-Write-Wins 吞所有冲突；
- 网络失败静默吞异常；
- 为“完成 EPIC06”编造真实账单测试结果；
- 因 EPIC-06 缺少样本停止 EPIC-07。

---

# 31. EPIC-07 最终验收矩阵

## A. 快速记账

- [ ] Tile 直接进入金额输入
- [ ] Shortcut 支持支出/收入/转账
- [ ] 普通支出约 3 秒
- [ ] 保存不等待网络
- [ ] 重启数据仍在

## B. 离线

- [ ] 飞行模式支出
- [ ] 飞行模式收入
- [ ] 飞行模式转账
- [ ] 飞行模式编辑/删除
- [ ] 网络恢复自动同步

## C. 同步

- [ ] Business + Outbox 原子
- [ ] Duplicate Push 幂等
- [ ] Pull 分页正确
- [ ] Pull Apply 失败 Cursor 不推进
- [ ] 429 退避
- [ ] 413 拆 Batch
- [ ] 401 Single-Flight Refresh
- [ ] Tombstone
- [ ] Snapshot
- [ ] Conflict 不静默覆盖

## D. Auth/Security

- [ ] App ID 正确
- [ ] Finance App 无 Notes Scope
- [ ] Access Token 不落盘
- [ ] Refresh Token Keystore
- [ ] Logout 清理 Token
- [ ] Grant Revoked 正确处理

## E. Notification

- [ ] 未授权不崩溃
- [ ] Permission 可诊断
- [ ] Package Allowlist
- [ ] Synthetic 支付通知可产生 Candidate
- [ ] 非支付通知不产生 Candidate
- [ ] Duplicate 不重复
- [ ] Candidate 可确认/忽略
- [ ] 原始通知不默认同步

## F. 多端基础 Finance

- [ ] Android 新增交易 → Windows/Web
- [ ] Windows/Web 修改 → Android
- [ ] Account/Category 同步
- [ ] Transaction Evidence 基础同步

## G. UI

- [ ] Dark Mode
- [ ] Large Font
- [ ] Small Screen
- [ ] Long Merchant
- [ ] Large Amount
- [ ] Sync Error State

## H. Diagnostics

- [ ] Request 前错误可定位
- [ ] HTTP/DTO/DB/Worker 错误可区分
- [ ] 可查看 WorkManager 最近状态
- [ ] 可导出脱敏日志
- [ ] 日志无 Token/密码

## I. Release

- [ ] CI Green
- [ ] Debug APK
- [ ] Signed Release APK/AAB
- [ ] versionCode
- [ ] SHA256
- [ ] Secret 不入仓库
- [ ] Completion Report

## J. EPIC-06 延迟项

以下不是 EPIC-07 失败：

- [ ] `DEFERRED_TO_EPIC06` 微信正式账单 importer
- [ ] `DEFERRED_TO_EPIC06` 支付宝正式账单 importer
- [ ] `DEFERRED_TO_EPIC06` 银行流水 importer
- [ ] `DEFERRED_TO_EPIC06` 正式账单自动匹配
- [ ] `DEFERRED_TO_EPIC06` 真实账单去重验证
- [ ] `DEFERRED_TO_EPIC06` 真实账单对账准确率验证

这些 checkbox 用于跟踪未来工作，不计入 EPIC-07 DoD。

---

# 32. EPIC-07 Definition of Done

只有以下 EPIC-07 自身项目全部满足，才能声明完成：

```text
[ ] Clean checkout 可构建
[ ] 包名 com.lifetrace.finance
[ ] App ID lifetrace-finance-android
[ ] Room 本地数据层完整
[ ] Business + Outbox 原子
[ ] Auth/Refresh/Logout 接真实 Cloud
[ ] Push/Pull/Snapshot 接真实 Cloud
[ ] WorkManager 自动同步
[ ] 离线记账
[ ] 3 秒快速记账
[ ] NotificationListenerService
[ ] Synthetic Parser 正/负 Fixture
[ ] Candidate 可确认/忽略
[ ] 基础 Finance 多端同步往返
[ ] Conflict/Tombstone
[ ] Tile/Shortcut/Widget/Share Receiver
[ ] Reports 使用真实本地数据
[ ] EPIC-06 Integration Gateway 已预留并 Feature Gate
[ ] 日志可区分请求前/中/后错误
[ ] Refresh Token 不明文落盘
[ ] CI/Lint/Test/Build 全通过
[ ] Release 可重复构建
[ ] docs/epic-07/test-matrix.md
[ ] docs/epic-07/deferred-epic06.md
[ ] docs/epic-07/completion-report.md
```

**EPIC-06 尚未完成、当前没有真实账单样本，不影响 EPIC-07 被标记为 COMPLETE。**

EPIC-07 Completion Report 应写：

```text
Status: COMPLETE

Deferred integrations:
- EPIC-06 official bill import
- EPIC-06 reconciliation
- EPIC-06 real bill validation

Reason:
Real bill samples are currently unavailable. Android integration boundaries are reserved and feature-gated.
```

不得再写：

```text
PARTIALLY COMPLETE — BLOCKED BY EPIC-06 CONTRACT
```

---

# 33. Agent 开工指令

```text
1. 拉取 LifeTrace-finance main
2. 读取本方案
3. 读取 LifeTrace main
4. 记录上游 Commit SHA
5. 审计 EPIC-04 / EPIC-05
6. 记录 EPIC-06 当前状态，但不把它作为启动门禁
7. 创建 docs/epic-07/precondition-report.md
8. 创建 docs/epic-07/deferred-epic06.md
9. 创建 contract-snapshots/upstream.lock
10. 立即进入 Android 工程 Phase 1
11. 按 Phase 逐步实现到 Release
```

如果发现 EPIC-06 尚未实现：

```text
记录 deferred
→ 保留 Gateway / Feature Gate
→ 继续 EPIC-07
```

如果以后得到真实账单：

```text
回到 EPIC-06
→ 建真实脱敏 Fixture
→ 完成 Import/Reconciliation
→ 主仓 Contract 定版
→ 更新 EPIC-07 upstream.lock
→ 接入 Android Adapter
```

---

# 34. 核心结论

EPIC-07 的主路径是：

```text
Android Native
+
Room Local First
+
Finance Domain Service
+
Kotlin Sync Engine
+
EPIC-04 Auth
+
EPIC-05 Sync Contract
+
Notification Capture
+
Quick Entry
+
Diagnostics
+
Independent Release
```

EPIC-06 改为：

```text
未来正式账单与对账能力
        ↓
有真实账单样本后完成验证
        ↓
通过稳定 Contract 接入 EPIC-07
```

最终工程红线：

1. **任何记账操作都不能依赖网络成功才能保存。**
2. **Android 端不能自行发明与主仓不一致的同步协议。**
3. **EPIC-06 缺少真实账单样本不得阻塞 EPIC-07。**
4. **Synthetic Fixture 可以验证 Android Parser，但不能冒充真实账单生产验证。**
5. **通知、同步、认证任一环节失败必须可诊断。**
