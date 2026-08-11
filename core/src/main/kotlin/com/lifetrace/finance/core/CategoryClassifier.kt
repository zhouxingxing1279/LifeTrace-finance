package com.lifetrace.finance.core

import java.util.Locale

data class ClassificationCategory(
    val id: String,
    val name: String,
    val type: String,
)

data class ClassificationHistory(
    val merchant: String? = null,
    val counterparty: String? = null,
    val item: String? = null,
    val categoryId: String,
)

data class CategorySuggestion(
    val categoryId: String,
    val categoryName: String,
    val confidence: Double,
    val reason: String,
)

data class StandardCategory(val name: String, val type: TransactionType)

object StandardCategories {
    val ALL = listOf(
        StandardCategory("餐饮", TransactionType.EXPENSE),
        StandardCategory("交通", TransactionType.EXPENSE),
        StandardCategory("购物", TransactionType.EXPENSE),
        StandardCategory("居住", TransactionType.EXPENSE),
        StandardCategory("娱乐", TransactionType.EXPENSE),
        StandardCategory("医疗", TransactionType.EXPENSE),
        StandardCategory("教育", TransactionType.EXPENSE),
        StandardCategory("通讯", TransactionType.EXPENSE),
        StandardCategory("人情", TransactionType.EXPENSE),
        StandardCategory("其他", TransactionType.EXPENSE),
        StandardCategory("工资", TransactionType.INCOME),
        StandardCategory("退款", TransactionType.INCOME),
        StandardCategory("理财", TransactionType.INCOME),
        StandardCategory("其他收入", TransactionType.INCOME),
    )
}

/**
 * Local-only, explainable category suggestions. It never invents a category that the profile
 * does not already own and never sends merchant or bill text over the network.
 */
object CategoryClassifier {
    private data class Rule(val categoryNames: Set<String>, val keywords: Set<String>)

    private val expenseRules = listOf(
        Rule(setOf("餐饮", "吃喝"), setOf("餐厅", "饭店", "美食", "外卖", "美团", "饿了么", "星巴克", "starbucks", "瑞幸", "luckin", "咖啡", "奶茶", "茶百道", "蜜雪冰城", "肯德基", "kfc", "麦当劳", "便利店")),
        Rule(setOf("交通", "出行"), setOf("滴滴", "打车", "地铁", "公交", "铁路", "12306", "高铁", "机票", "航空", "加油", "停车", "高速", "充电桩")),
        Rule(setOf("购物", "日用"), setOf("淘宝", "天猫", "京东", "拼多多", "唯品会", "商城", "超市", "盒马", "山姆", "costco", "名创优品")),
        Rule(setOf("居住", "住房"), setOf("房租", "租金", "物业", "水费", "电费", "燃气", "宽带", "维修")),
        Rule(setOf("娱乐", "休闲"), setOf("电影", "影院", "游戏", "音乐", "视频", "会员", "ktv", "演出", "门票", "旅游", "酒店")),
        Rule(setOf("医疗", "健康"), setOf("医院", "诊所", "药房", "药店", "体检", "挂号", "医疗", "医保")),
        Rule(setOf("教育", "学习"), setOf("学校", "学费", "课程", "培训", "书店", "图书", "考试", "教育")),
        Rule(setOf("通讯", "通信"), setOf("话费", "流量", "中国移动", "中国联通", "中国电信", "手机充值")),
        Rule(setOf("人情", "红包"), setOf("红包", "礼金", "份子", "随礼")),
    )

    private val incomeRules = listOf(
        Rule(setOf("工资", "薪资"), setOf("工资", "薪资", "薪酬", "奖金", "绩效")),
        Rule(setOf("退款", "报销"), setOf("退款", "退货", "报销", "返还")),
        Rule(setOf("理财", "投资"), setOf("利息", "分红", "理财", "基金", "投资收益")),
    )

    fun suggest(
        transactionType: String,
        merchant: String?,
        counterparty: String?,
        item: String?,
        note: String?,
        categories: List<ClassificationCategory>,
        history: List<ClassificationHistory> = emptyList(),
    ): CategorySuggestion? {
        if (transactionType == TransactionType.TRANSFER.wire) return null
        val available = categories.filter { it.type == transactionType }
        if (available.isEmpty()) return null

        val identity = firstIdentity(merchant, counterparty, item)
        if (identity.isNotBlank()) {
            val learned = history.asReversed().firstOrNull { row ->
                row.categoryId in available.map { it.id }.toSet() &&
                    firstIdentity(row.merchant, row.counterparty, row.item) == identity
            }
            if (learned != null) {
                val category = available.first { it.id == learned.categoryId }
                return CategorySuggestion(category.id, category.name, 0.98, "沿用该商户的历史分类")
            }
        }

        val text = normalize(listOfNotNull(merchant, counterparty, item, note).joinToString(" "))
        if (text.isBlank()) return null

        available.firstOrNull { text.contains(normalize(it.name)) }?.let { category ->
            return CategorySuggestion(category.id, category.name, 0.92, "账单文字包含分类名称")
        }

        val rules = if (transactionType == TransactionType.INCOME.wire) incomeRules else expenseRules
        rules.forEach { rule ->
            val keyword = rule.keywords.firstOrNull { text.contains(normalize(it)) } ?: return@forEach
            val category = available.firstOrNull { candidate ->
                rule.categoryNames.any { target -> normalize(candidate.name).contains(normalize(target)) || normalize(target).contains(normalize(candidate.name)) }
            } ?: return@forEach
            return CategorySuggestion(category.id, category.name, 0.86, "匹配关键词“$keyword”")
        }
        return null
    }

    private fun firstIdentity(vararg values: String?): String = values.asSequence()
        .mapNotNull { it?.takeIf(String::isNotBlank) }
        .map(::normalize)
        .firstOrNull()
        .orEmpty()

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("(有限责任公司|股份有限公司|有限公司|旗舰店|官方店|支付|收款|付款)"), "")
        .replace(Regex("[^\\p{L}\\p{N}]"), "")
}
