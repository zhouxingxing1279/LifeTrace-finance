# LifeTrace Finance — 完整记账能力补全执行方案

状态：COMPLETE

分支：`feature/complete-bookkeeping-ui-import`

目标：在现有 `LifeTrace-finance` Android 客户端和现有 LifeTrace Cloud/Sync v1 上补齐 BeeCount 风格的高级记账产品能力，不建立新的云端、认证、数据库服务或同步协议。

## 1. 既有基线

当前 Android 已具备：支出/收入/转账、账单编辑删除、搜索筛选、账户/分类、月报、通知候选账单、Vision 截图记账、待确认、Room/Outbox、LifeTrace Push/Pull/Snapshot/Conflict、Tile/Shortcut/Widget。

Room v2 与现有 Cloud 已支持：Ledger、Account、Category、Transaction、RecurringTransaction、Tag、TransactionTag、Budget、TransactionAttachment、TransactionEvidence。

本轮只把这些已存在的高级模型接成完整产品能力，并补正式账单文件导入。

## 2. 账单导入权威规则

Android 导入语义复用 LifeTrace EPIC-13 `web-client/src/importer.ts` 的字段与映射语义，不重新定义一套平台规则。

复用以下规则：

- 表头归一化：trim + lowercase + 去空白；
- 字段别名：
  - 收支：`收/支` / `收支类型` / `交易类型` / `type`
  - 状态：`当前状态` / `交易状态` / `status`
  - 金额：`金额(元)` / `金额` / `交易金额` / `amount`
  - 时间：`交易时间` / `时间` / `日期` / `occurredAt`
  - 商户：`交易对方` / `商户名称` / `交易对象` / `merchant`
  - 商品：`商品` / `商品说明` / `交易内容` / `item`
  - 备注：`备注` / `note`
  - 外部 ID：`交易单号` / `订单号` / `流水号` / `transactionId`
- 来源推断：文件名 + 表头包含微信/Wechat、支付宝/Alipay、银行/Bank；
- 金额去除 `￥/¥/逗号/空白/前导 +` 后转整数分；
- 日期支持常见文本时间并统一为 Instant；
- 类型：收入/收款→income，退款/refund→refund，其余→expense；
- 状态：成功/完成/confirmed→confirmed，其余→candidate；
- 单行失败产生 warning，不让整文件失败。

Android 文件层正式支持 CSV 与 XLSX。`.xls` 入口会兼容被错误标记为 Excel MIME 的 CSV、或实际为 OOXML ZIP 的文件；旧版二进制 BIFF `.xls` 会明确提示用户改导出 CSV/XLSX。XLSX 读取后统一进入相同 row mapper。正式交易全部通过 `FinanceRepository` 写 Room + Outbox。

## 3. 导入与对账

- ACTION_OPEN_DOCUMENT 与系统 Share Receiver 均可导入；
- 先生成预览：可导入行、警告、来源、重复数；
- externalTransactionId 为第一去重键；
- 无 external ID 时使用来源 + 金额 + 时间 + 标准化商户的稳定指纹兜底；
- 与通知/Vision candidate 在金额一致、时间邻近时对账，保留正式导入记录并忽略候选记录；
- 用户已编辑的分类/备注不被低信息导入覆盖；
- 导入成功后触发现有 SyncScheduler。

## 4. 多账本与二级分类

- 增加当前账本选择状态；
- UI 支持创建、切换、归档账本；
- 账单、账户、分类、预算、标签、周期规则按账本过滤；
- 分类管理支持一级/二级创建和层级展示；
- 新记账只允许选择当前账本中的账户/分类。

## 5. 标签

- 创建/归档标签；
- 交易详情增加标签选择；
- TransactionTag 添加和软删除都经 Outbox；
- 明细页展示标签。

## 6. 预算

- 总预算与分类预算；
- monthly/weekly/yearly；
- 计算本期起止、已用、剩余和进度；
- `excludeFromBudget` 生效；
- 可启用/停用预算。

## 7. 周期记账

- daily/weekly/monthly/yearly 规则管理；
- 规则启停；
- 由确定性执行器生成普通 Transaction；
- 幂等键：`recurring:<ruleId>:<occurrenceDate>`；
- 生成账单写 `recurringTransactionId`、`sourceType=recurring`；
- 成功后更新 `lastGeneratedDate`；
- App 启动和 WorkManager 周期均执行到期检查，重复运行不重复入账。

## 8. 高级账户设置

补齐 UI/Repository：currency、bankName、last4、creditLimit、billingDay、paymentDueDay、openingBalance、note、hidden/archive。

## 9. 附件边界

保留并接入现有 TransactionAttachment metadata 能力。当前轮次不伪造文件对象上传能力；如果 LifeTrace Cloud 后续提供正式文件对象 API，再接字节上传与下载。

## 10. 测试门禁

已覆盖：

- LifeTrace importer 表头别名、CSV quote、金额、日期、类型、状态、warning；
- XLSX 行读取与 XML/ZIP 安全防护；
- external ID / fallback fingerprint 去重；
- candidate 对账；
- 多账本隔离；
- 二级分类；
- 标签增删；
- 预算期间/usage；
- 周期规则跨日/月/年和幂等；
- Room/Outbox 同事务；
- Compose 关键入口可达；
- lint、Debug、Release/R8、API 34 instrumentation。

实现代码在 GitHub Actions run `31572036313` 验证：`verify` 与 `instrumentation` 均通过。该 run 覆盖 Core unit、Android JVM unit、lint、Debug build、Release R8 build、API 34 connected instrumentation。

## 11. 合并门禁

1. 先提交本执行文档；
2. 再实现代码；
3. 更新完成报告和 README；
4. PR 最终 head CI 全绿；
5. 最终 diff 不包含新 Cloud/新同步协议；
6. 合并 `main`。

## 12. 完成结论

本轮已完成 Android 高级记账产品层与正式账单导入闭环，并保持 LifeTrace 现有 Room、Outbox、Auth、Cloud Sync 契约不变。账单文件导入、通知/Vision 候选、手工记账最终统一落到既有 Transaction 模型；多账本、账户、分类、标签、预算与周期规则均在同一账本上下文下工作。