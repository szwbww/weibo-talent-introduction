package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.campaign.domain.ExpertMaterialStatusRecord
import org.springframework.data.repository.CrudRepository

/**
 * I1-2/I1-4：材料状态唯一读写入口。不暴露全表扫描、批量覆盖或按 label 查询；
 * save/deleteById 复用 CrudRepository，更新已有状态保留其 id 走 Spring Data JDBC update。
 */
interface ExpertMaterialStatusRepository : CrudRepository<ExpertMaterialStatusRecord, Long> {
    fun findAllByExpertContactId(expertContactId: Long): List<ExpertMaterialStatusRecord>

    fun findByExpertContactIdAndMaterialCode(
        expertContactId: Long,
        materialCode: String
    ): ExpertMaterialStatusRecord?
}
