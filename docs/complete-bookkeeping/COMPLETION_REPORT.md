# LifeTrace Finance — 完整记账能力补全完成报告

状态：COMPLETE

实现分支：`feature/complete-bookkeeping-ui-import`

PR：#6 `feat(finance): complete advanced bookkeeping and bill import`

## 1. 本轮完成内容

### 1.1 正式账单导入

- Android 端实现 `BillImportParser`，复用 LifeTrace 既有账单导入字段与映射语义；
- CSV：支持 quoted field、UTF-8/BOM、GB18030 回退；
- XLSX：读取 shared strings、sheet、日期样式并统一映射到同一 row parser；
- XLSX 增加 ZIP/XML 防护：条目数、单条目解压大小、总解压大小限制，并拒绝 DOCTYPE/ENTITY；
- 文件选择器与系统 Share Receiver 均可进入真实导入页；
- 导入前提供来源、总行数、可导入数、warning 与重复情况预览；
- `externalTransactionId` 优先去重，无外部 ID 时使用稳定指纹兜底；
- 正式导入账单可与通知/Vision candidate 按金额、时间和商户对账，避免双记；
- 正式数据仍通过 `FinanceRepository` 写入 Room + Sync Outbox，并继续使用 LifeTrace 现有 Cloud Sync。

### 1.2 多账本

- 增加当前账本选择与持久化；
- 主界面显示当前账本上下文；
- 支持创建、切换、归档账本；
- Transaction、Account、Category、Tag、Budget、RecurringTransaction 均按当前账本隔离；
- 兼容 v1 数据：默认账本补齐后继续使用现有同步模型。

### 1.3 高级记账管理

新增统一“记账管理”界面，覆盖：

- 高级账户设置；
- 一级/二级分类；
- 标签创建、归档与交易标签绑定；
- 总预算与分类预算；
- monthly/weekly/yearly 预算周期与使用进度；
- daily/weekly/monthly/yearly 周期记账规则；
- 周期规则启停与删除。

### 1.4 周期记账执行

- 增加 `RecurringWorker` 与调度器；
- App 启动和 WorkManager 周期执行到期检查；
- 每次发生实例使用 `recurring:<ruleId>:<occurrenceDate>` 作为确定性外部 ID；
- 重复执行不会重复生成同一天账单；
- 生成的普通 Transaction 保留 `recurringTransactionId` 与 `sourceType=recurring`。

## 2. 复用 LifeTrace 的部分

本轮没有创建第二套账单语义或第二套云同步协议。

Android importer 复用了 LifeTrace EPIC-13 导入语义中的：

- 表头归一化；
- 微信/支付宝/银行来源推断；
- 收支、状态、金额、时间、商户、商品、备注、交易单号字段别名；
- 金额转整数分；
- 日期标准化；
- 收入/退款/支出类型映射；
- confirmed/candidate 状态映射；
- 单行失败 warning、整文件继续处理的容错策略。

## 3. 验证结果

GitHub Actions run `31572036313` 已通过：

- Core unit tests：PASS；
- Android JVM unit tests：PASS；
- Android lint：PASS；
- Debug build：PASS；
- Release R8 build：PASS；
- API 34 connected instrumentation：PASS；
- Compose/快速记账设备回归：PASS。

设备回归期间发现旧 `syncedBillShowsImportedItemAndPaymentAccount` 测试夹具未填写 `ledgerId`。v2 多账本后明细按当前账本过滤，因此修复方式是让夹具写入默认账本，而不是放宽生产代码的数据隔离。修复后 API 34 instrumentation 全绿。

## 4. 兼容性与安全边界

- 正式支持 CSV、XLSX；
- `.xls` 仅兼容 CSV MIME 误标或实际 OOXML ZIP，旧 BIFF 二进制 XLS 明确拒绝并提示重新导出；
- 不新增 LifeTrace Cloud、认证、数据库服务或同步协议；
- TransactionAttachment 本轮保持 metadata 边界，不伪造云端文件对象上传；
- 导入文件在解析前执行基础大小与 XLSX ZIP/XML 安全检查；
- 多账本隔离属于产品层硬约束，测试和导入数据均必须绑定账本。

## 5. 合并检查

- [x] 执行方案先于实现提交；
- [x] 账单解析复用 LifeTrace 既有语义；
- [x] 高级记账 UI 与 Repository 接通；
- [x] 周期记账执行器实现幂等；
- [x] Core/JVM/lint/Debug/Release-R8/API34 instrumentation 全绿；
- [x] README 与完成报告更新；
- [x] 未新增 Cloud/Sync 协议；
- [ ] 最终文档 head CI 全绿后合并 `main`。