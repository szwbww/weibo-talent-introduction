package com.weibo.talentintroduction.document.repository

import com.weibo.talentintroduction.document.domain.ManualExpertMaterialUpload
import org.springframework.data.repository.CrudRepository

/**
 * 手动材料上传来源仓库（V130）。
 *
 * 只服务两处只读需求：owner 解析（`mail_attachment.manual_upload_id` → 上传行 →
 * `expert_contact_id`）与材料列表的来源/上传时间投影（列表走 SQL LEFT JOIN，不经本仓库）。
 * 新增行只由 [com.weibo.talentintroduction.document.service.ManualExpertMaterialUploadService]
 * 在上传事务里写入。删除/替换不在本功能范围（外键不设级联），故不新增任何删除 API。
 */
interface ManualExpertMaterialUploadRepository : CrudRepository<ManualExpertMaterialUpload, Long>
