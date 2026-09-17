package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertMaterialStatusRecord
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.ExpertMaterialStatusRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.NoSuchElementException

/**
 * I1-1：唯一目录及顺序，数据库不保存目录正文。目录严格限定为下方 7 项，不含任何其他材料项。
 */
enum class ExpertMaterialCode(val label: String, val requestText: String) {
    CV(
        "简历",
        "Your latest English CV, including education, employment, publications, patents, projects, awards, and honors."
    ),
    PASSPORT(
        "护照",
        "A copy of the personal information page of your valid passport."
    ),
    DEGREE(
        "学位",
        "Your PhD degree certificate. Master’s and bachelor’s degree certificates may also be required."
    ),
    EMPLOYMENT(
        "工作",
        "Proof of your current position and recent employment, such as employment letters, contracts, appointment letters, or official institutional documents."
    ),
    PUBLICATIONS(
        "出版",
        "A list of your recent publications, patents, projects, awards, and other professional achievements."
    ),
    PATENTS(
        "专利",
        "Supporting certificates for important patents, awards, qualifications, or editorial/reviewer roles, if available."
    ),
    RESEARCH(
        "研究",
        "A brief description of your recent research achievements and proposed research topic."
    )
}

/**
 * 02（I-1）：材料索取独立目录，严格 5 项并固定此顺序。
 * 与旧 [ExpertMaterialCode] 共用 expert_material_status 稀疏表但互不推断状态：
 * 本目录只读写 `REQ_*` 行，旧 7 项、`${pendingExpertMaterials}` 与 RAG 的 `CV` 读取只读写旧代码行。
 */
enum class ExpertMaterialRequestCode(val label: String, val requestText: String) {
    REQ_PUBLICATIONS(
        "代表性论文",
        "Copies of your representative publications"
    ),
    REQ_PROJECTS(
        "科研项目",
        "Supporting documents for research projects"
    ),
    REQ_PATENTS(
        "专利",
        "Patent certificates"
    ),
    REQ_AWARDS(
        "荣誉奖项",
        "Certificates of honors and awards"
    ),
    REQ_DEGREES(
        "学位",
        "Bachelor’s, master’s, and doctoral degree certificates"
    )
}

/**
 * I1-3：API 状态域严格三值；其中 PENDING 不落库（I1-2 缺行即 PENDING）。
 */
enum class ExpertMaterialProvisionStatus { PENDING, PROVIDED, DECLINED }

/** API 可返回的材料项：code/status 为字符串便于 JSON 直传。 */
data class ExpertMaterialItem(
    val code: String,
    val label: String,
    val status: String
)

/**
 * 02（I-3）：材料索取 API 项。`requestText` 是服务端目录的英文正文副本，
 * 供 GET/PUT 直接返回，前端不得再抄写。字段顺序即 JSON 契约顺序。
 */
data class ExpertMaterialRequestItem(
    val code: String,
    val label: String,
    val status: String,
    val requestText: String
)

/**
 * 材料状态唯一业务入口：固定目录、状态转换与 I1-6 英文正文的唯一真源。
 * controller 与前端不得复制英文正文。
 */
@Service
class ExpertMaterialService(
    private val expertMaterialStatusRepository: ExpertMaterialStatusRepository,
    private val expertContactRepository: ExpertContactRepository
) {
    /**
     * 校验联系人存在后，读取全部稀疏行并按目录顺序返回完整 7 项；
     * 缺行解析为 PENDING（I1-2）。
     */
    fun listMaterials(contactId: Long): List<ExpertMaterialItem> {
        requireContact(contactId)
        val byCode = expertMaterialStatusRepository.findAllByExpertContactId(contactId)
            .associateBy { it.materialCode }
        return ExpertMaterialCode.entries.map { code ->
            ExpertMaterialItem(
                code = code.name,
                label = code.label,
                status = byCode[code.name]?.materialStatus
                    ?: ExpertMaterialProvisionStatus.PENDING.name
            )
        }
    }

    /**
     * I1-3/I1-4：未知 code/status 在写入前以 [IllegalArgumentException] 拒绝，不得落库。
     * PENDING 删除已有行（I1-2）；PROVIDED/DECLINED 更新已有行（保留 id）或新增行。
     */
    @Transactional
    fun updateStatus(contactId: Long, rawCode: String, rawStatus: String): List<ExpertMaterialItem> {
        val code = parseCode(rawCode)
        val status = parseStatus(rawStatus)
        requireContact(contactId)
        persistStatus(contactId, code.name, status)
        return listMaterials(contactId)
    }

    /**
     * 02（I-2/I-3）：材料索取 GET。缺行解析为 PENDING，按 [ExpertMaterialRequestCode] 固定顺序
     * 返回完整 5 项并附服务端英文正文；只读，不写库、不触发文件识别或发信。
     */
    fun listMaterialRequests(contactId: Long): List<ExpertMaterialRequestItem> {
        requireContact(contactId)
        val byCode = expertMaterialStatusRepository.findAllByExpertContactId(contactId)
            .associateBy { it.materialCode }
        return ExpertMaterialRequestCode.entries.map { code ->
            ExpertMaterialRequestItem(
                code = code.name,
                label = code.label,
                status = byCode[code.name]?.materialStatus
                    ?: ExpertMaterialProvisionStatus.PENDING.name,
                requestText = code.requestText
            )
        }
    }

    /**
     * 02（I-2/I-3）：材料索取 PUT。未知 code/status 在写入前拒绝；持久化语义与旧目录完全一致
     * （共用 [persistStatus]，PENDING 删除行），响应与 GET 同形。
     * 只按新目录代码校验，绝不把旧 7 项状态换算为新 5 项（I-1）。
     */
    @Transactional
    fun updateMaterialRequestStatus(
        contactId: Long,
        rawCode: String,
        rawStatus: String
    ): List<ExpertMaterialRequestItem> {
        val code = parseRequestCode(rawCode)
        val status = parseStatus(rawStatus)
        requireContact(contactId)
        persistStatus(contactId, code.name, status)
        return listMaterialRequests(contactId)
    }

    /**
     * I1-5：只取 PENDING 项，按 I1-1 目录顺序过滤后从 1 连续编号，
     * 以 `\n` 连接输出英文编号行；空集合返回 `""`。
     * 调用方已有真实 contact，不重复查询联系人。
     */
    fun renderPendingMaterials(contactId: Long): String {
        val byCode = expertMaterialStatusRepository.findAllByExpertContactId(contactId)
            .associateBy { it.materialCode }
        return ExpertMaterialCode.entries
            .filter { byCode[it.name] == null }
            .mapIndexed { index, code -> "${index + 1}. ${code.requestText}" }
            .joinToString("\n")
    }

    /**
     * 旧 7 项与新 5 项共用的唯一持久化分支（I-2）：PENDING 删行，PROVIDED/DECLINED 更新或新增。
     * 调用方已完成 code/status 校验与联系人存在性校验。
     */
    private fun persistStatus(contactId: Long, materialCode: String, status: ExpertMaterialProvisionStatus) {
        val existing = expertMaterialStatusRepository
            .findByExpertContactIdAndMaterialCode(contactId, materialCode)
        when (status) {
            ExpertMaterialProvisionStatus.PENDING -> {
                if (existing != null) {
                    val id = existing.id ?: error("Expert material status id is required")
                    expertMaterialStatusRepository.deleteById(id)
                }
            }
            ExpertMaterialProvisionStatus.PROVIDED,
            ExpertMaterialProvisionStatus.DECLINED -> {
                val now = LocalDateTime.now()
                if (existing != null) {
                    expertMaterialStatusRepository.save(
                        existing.copy(materialStatus = status.name, updatedAt = now)
                    )
                } else {
                    expertMaterialStatusRepository.save(
                        ExpertMaterialStatusRecord(
                            expertContactId = contactId,
                            materialCode = materialCode,
                            materialStatus = status.name,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }
        }
    }

    private fun requireContact(contactId: Long) {
        expertContactRepository.findById(contactId)
            .orElseThrow { NoSuchElementException("Expert contact not found: $contactId") }
    }

    private fun parseCode(raw: String): ExpertMaterialCode =
        ExpertMaterialCode.entries.firstOrNull { it.name == raw }
            ?: throw IllegalArgumentException("Unknown material code: $raw")

    private fun parseRequestCode(raw: String): ExpertMaterialRequestCode =
        ExpertMaterialRequestCode.entries.firstOrNull { it.name == raw }
            ?: throw IllegalArgumentException("Unknown material request code: $raw")

    private fun parseStatus(raw: String): ExpertMaterialProvisionStatus =
        ExpertMaterialProvisionStatus.entries.firstOrNull { it.name == raw }
            ?: throw IllegalArgumentException("Unknown material status: $raw")
}
