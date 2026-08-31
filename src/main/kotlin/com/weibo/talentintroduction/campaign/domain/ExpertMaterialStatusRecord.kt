package com.weibo.talentintroduction.campaign.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * I1-2/I1-4：expert_material_status 稀疏行，仅保存 PROVIDED/DECLINED；
 * 某联系人某材料缺行唯一解释为 PENDING。字段严格对应 V111。
 */
@Table("expert_material_status")
data class ExpertMaterialStatusRecord(
    @Id
    val id: Long? = null,
    val expertContactId: Long,
    val materialCode: String,
    val materialStatus: String,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
