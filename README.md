# LifeTrace Finance

LifeTrace 的独立 Android 财务客户端（EPIC-07）。包名 `com.lifetrace.finance`，云端 App ID `lifetrace-finance-android`。

## 能力

- 本地优先支出/收入/转账，离线保存；
- Room 业务写入 + Sync Outbox 同事务；
- LifeTrace Auth v1 + Android Keystore Refresh Token；
- Push/Pull/Snapshot/Conflict/Tombstone 自动同步；
- NotificationListenerService 候选账单捕获与去重；
- CSV/XLSX 正式账单导入：复用 LifeTrace 现有导入语义，支持预览、逐行 warning、交易号/稳定指纹去重，以及与候选账单对账；
- 多账本创建、切换、归档与账本级数据隔离；
- 高级账户信息：币种、银行、尾号、信用额度、账单日、还款日、期初余额、备注、隐藏/归档；
- 一级/二级分类、标签及交易标签管理；
- 总预算/分类预算，支持 monthly/weekly/yearly 周期与本期使用进度；
- daily/weekly/monthly/yearly 周期记账规则，使用确定性幂等键防止重复入账；
- 待确认箱、账单编辑删除、搜索筛选、报表；
- Quick Settings Tile、App Shortcuts、Home Widget、Share Receiver；
- 脱敏诊断日志。

## 账单文件导入

Android 文件选择器与系统分享入口都可以进入账单导入。正式支持 CSV 与 XLSX；对于 `.xls`，会兼容“CSV 被标记为 Excel MIME”或实际 OOXML ZIP 的情况，旧版二进制 BIFF `.xls` 会提示重新导出为 CSV/XLSX。

导入后的正式账单仍使用现有 `Transaction`、Room 与 Sync Outbox，不创建新的云端实体或同步协议。

## 构建

推荐 Android Studio 或 JDK 17 + Android SDK 35 + Gradle 8.9：

```bash
gradle :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Release/R8 与 API 34 connected instrumentation 由 GitHub Actions `EPIC07 Android CI` 验证。

首次运行默认 Cloud URL 是不可路由占位地址，请在“设置”中填写实际 LifeTrace Cloud HTTPS 地址。Debug 构建允许本地 HTTP 联调，Release 禁止明文 HTTP。

基础 EPIC-07 实施/验收资料见 `docs/epic-07/`；本轮完整记账能力补全见 `docs/complete-bookkeeping/`。