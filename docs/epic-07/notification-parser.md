# 通知捕获与解析

`NotificationListenerService` 只提取 packageName、postTime、title/text/bigText/subText 和 notification key。完整 extras 与完整通知原文不写入数据库或同步 payload。

纯 Kotlin parser 流程：allowlist → normalize → payment/ignore signal → amount → merchant/account hint → confidence → candidate/provisional → dedup。

本地 `notification_events` 仅保存结构化摘要、SHA-256 evidence hash、parser id/version 和关联 transaction id；默认 7 天清理。同步 `finance.transaction_evidence` 只保存脱敏结构化证据。

现有测试样本全部明确为 synthetic，只证明 Android parser pipeline，不宣称真实账单或所有银行通知格式已兼容。
