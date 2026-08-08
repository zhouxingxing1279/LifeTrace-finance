# DEFERRED_TO_EPIC06

等待获得真实、可脱敏账单后补做：

1. 微信账单文件 importer fixture；
2. 支付宝账单文件 importer fixture；
3. 银行 CSV/Excel importer fixture；
4. 同一文件重复导入幂等；
5. Android candidate 与正式账单强/模糊匹配；
6. 用户分类、备注保留；
7. 退款、红包、转账待收款等真实数据验证；
8. 对账准确率与误匹配率统计。

任何新增跨端实体/协议必须先在 `LifeTrace` 主仓 Contract 定版，再更新 `contract-snapshots/upstream.lock`。
