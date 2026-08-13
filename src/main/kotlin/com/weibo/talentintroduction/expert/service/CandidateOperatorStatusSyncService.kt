package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import org.springframework.stereotype.Service

@Service
class CandidateOperatorStatusSyncService(
    private val expertIndexService: ExpertIndexService,
    private val expertContactRepository: ExpertContactRepository,
    private val expertIndexWriterService: ExpertIndexWriterService
) {
    fun reconcileAll(): BulkSyncResult {
        if (!expertIndexService.checkOperatorStatusMapping()) {
            throw IllegalStateException(
                "RAW/CANDIDATE/APPLICATION 索引缺少 keyword 类型的 operatorStatus mapping 声明，请先更新 mapping"
            )
        }
        val latestUpdates = expertContactRepository.findAllByOrderByUpdatedAtDesc()
            .filter { !it.orcidId.isNullOrBlank() }
            .map { it.orcidId!!.trim() to (it.operatorStatus ?: "NOT_CONTACTED") }
            .distinctBy { it.first.lowercase() }
        return expertIndexWriterService.syncOperatorStatusBatch(latestUpdates)
    }
}
