package com.weibo.talentintroduction.qa.service

object QaCoverageKeyCatalog {
    data class Entry(
        val key: String,
        val label: String,
        val description: String,
        val group: String
    )

    data class ControlledCoverageGroup(
        val id: String,
        val name: String,
        val keys: Set<String>,
        val canonicalAnswerBody: String
    )

    /** V82 atomic fact groups: coverage set -> its exact canonical answer body. */
    private val controlledCoverageGroups: List<ControlledCoverageGroup> = listOf(
        ControlledCoverageGroup(
            id = "G1",
            name = "材料保密",
            keys = setOf("confidentiality.materials"),
            canonicalAnswerBody = "Your materials are kept strictly confidential and used only for application purposes. Technical details you prefer not to disclose can be handled with appropriate redaction."
        ),
        ControlledCoverageGroup(
            id = "G2",
            name = "费用政策",
            keys = setOf("fees.policy"),
            canonicalAnswerBody = "We never charge any fees throughout the entire process."
        ),
        ControlledCoverageGroup(
            id = "G3",
            name = "合同安排",
            keys = setOf("contract.party", "contract.terms"),
            canonicalAnswerBody = "After selection, you will sign a labor contract directly with the matched enterprise, and you may review the full terms before making any commitment."
        ),
        ControlledCoverageGroup(
            id = "G4",
            name = "签约前 IP 边界",
            keys = setOf("ip.arrangements"),
            canonicalAnswerBody = "Until a contract is signed, nothing you share with us transfers any rights; any final intellectual-property arrangements will be set out in the future written agreement."
        )
    )

    fun controlledGroups(): List<ControlledCoverageGroup> = controlledCoverageGroups

    fun groupIdOf(key: String): String? =
        controlledCoverageGroups.firstOrNull { key in it.keys }?.id

    fun isControlled(key: String): Boolean = groupIdOf(key) != null

    fun validateControlledBody(coverageKeys: List<String>, answerBody: String) {
        val parsed = coverageKeys.toSet()
        val group = controlledCoverageGroups.firstOrNull { it.keys == parsed }
            ?: return
        if (answerBody.trim() != group.canonicalAnswerBody) {
            throw IllegalArgumentException(
                "Answer body must match the V82 canonical body for coverage ${group.keys.sorted().joinToString(",")}"
            )
        }
    }

    private val catalog: Map<String, Entry> = listOf(
        Entry("general.answer", "通用回答", "通用/兜底回答", "通用"),

        Entry("company.legal_name", "公司法定名称", "公司法定注册名称", "公司信息"),
        Entry("company.registered_location", "公司注册地点", "公司注册所在城市/地区", "公司信息"),
        Entry("company.verification_evidence", "可验证的公司证明", "官网/LinkedIn/证书等可验证材料", "公司信息"),

        Entry("programme.purpose", "项目目的", "项目设立的目的与目标", "项目概况"),
        Entry("programme.structure", "项目结构", "项目的组织/运作结构", "项目概况"),
        Entry("programme.tracks", "项目路径", "创新型/创业型等不同路径说明", "项目概况"),
        Entry("programme.scope", "项目范围", "项目覆盖的领域与范围", "项目概况"),

        Entry("researcher.selection", "专家筛选", "专家筛选标准与流程", "专家匹配"),
        Entry("enterprise.matching", "企业匹配", "企业匹配研究方向的流程", "专家匹配"),
        Entry("enterprise.project_types", "企业项目类型", "合作企业的项目类型/行业", "专家匹配"),

        Entry("role.responsibilities", "角色职责", "专家的研究顾问职责", "角色与产出"),
        Entry("role.deliverables", "产出交付物", "专家需交付的产出物说明", "角色与产出"),

        Entry("contract.party", "签约主体", "与谁签合同", "合同与IP"),
        Entry("contract.terms", "合同条款", "合同主要条款与条件", "合同与IP"),
        Entry("ip.arrangements", "知识产权安排", "知识产权归属与安排", "合同与IP"),
        Entry("publication.authorship", "发表署名权", "论文/成果的发表署名安排", "合同与IP"),

        Entry("finance.government_funding", "政府资金", "政府科研经费额度与范围", "资金"),
        Entry("finance.enterprise_compensation", "企业报酬", "企业提供的个人薪酬/补贴", "资金"),
        Entry("finance.compensation_structure", "薪酬结构", "薪酬构成与结构明细", "资金"),

        Entry("application.required_materials", "申请所需材料", "申请需要提交的材料清单", "申请流程"),
        Entry("application.steps", "申请步骤", "申请的主要步骤与流程", "申请流程"),
        Entry("application.timeline", "申请时间线", "申请/评审/结果时间点", "申请流程"),

        Entry("work.remote_arrangement", "远程工作安排", "远程参与项目的安排", "工作安排"),
        Entry("work.travel_arrangement", "出差安排", "赴华出差次数与安排", "工作安排"),
        Entry("work.relocation", "搬迁要求", "是否需要/如何搬迁", "工作安排"),

        Entry("fees.policy", "费用政策", "申请/项目各阶段是否收费", "费用与保密"),
        Entry("confidentiality.materials", "材料保密", "申请材料的保密处理", "费用与保密"),
        Entry("confidentiality.research", "研究保密", "研究数据/研究过程保密制度", "费用与保密"),

        // P1 (plan 01-fact-and-catalog, A-1): programme identity facts.
        // Appended at the END of the list on purpose: normalizeAndValidate returns
        // keys in catalog declaration order, so inserting mid-list would reorder
        // existing rules' serialized coverage_keys (plan A-1 note).
        Entry("programme.name", "项目名称与可见性", "对外可用的计划名称与项目是否公开", "项目概况"),
        Entry("governance.sponsor_level", "背书层级与组织方", "项目的政府背书层级与具体组织申报的机构层级", "公司信息")
    ).associateBy { it.key }

    fun all(): List<Entry> = catalog.values.toList()

    fun isValid(key: String): Boolean = catalog.containsKey(key)

    fun normalizeAndValidate(keys: List<String>?): List<String> {
        if (keys == null) return emptyList()
        val blankItems = keys.filter { it.isBlank() }
        require(blankItems.isEmpty()) { "Coverage keys must not be blank" }
        val trimmed = keys.map { it.trim() }.filter { it.isNotEmpty() }
        val duplicates = trimmed.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate coverage keys: ${duplicates.sorted().joinToString(", ")}" }
        val unknown = trimmed.filterNot { catalog.containsKey(it) }.distinct()
        require(unknown.isEmpty()) { "Unknown coverage keys: ${unknown.sorted().joinToString(", ")}" }
        require(trimmed.all { it == it.lowercase() && !it.contains(Regex("\\s")) }) {
            "Coverage keys must be lowercase dot-separated without whitespace"
        }
        if (trimmed.isNotEmpty()) {
            require(trimmed.joinToString(",").length <= 2000) {
                "Coverage keys total length exceeds 2000 characters"
            }
        }
        return all().map { it.key }.filter { it in trimmed }
    }

    fun parseStored(stored: String): List<String> {
        val tokens = stored.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val canonicalOrder = all().map { it.key }
        return tokens.filter { it in canonicalOrder }
            .sortedBy { canonicalOrder.indexOf(it) }
    }

    fun serialize(keys: List<String>): String =
        keys.joinToString(",")
}
