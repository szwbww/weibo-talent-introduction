package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BounceBackfillService(
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val bounceDetector: BounceDetector,
    private val bounceCollectionService: BounceCollectionService
) {
    private val log = LoggerFactory.getLogger(BounceBackfillService::class.java)

    @Transactional
    fun run(batchSize: Int = 200): BounceBackfillResult {
        var offset = 0
        var scanned = 0
        var ingested = 0
        var duplicates = 0

        val total = inboundMailProcessingRepository.countAll()
        while (offset < total) {
            val batch = inboundMailProcessingRepository.findAllPagedOrderByReceivedAtAsc(batchSize, offset)
            if (batch.isEmpty()) break

            for (row in batch) {
                scanned++
                val signal = bounceDetector.detect(row.fromEmail, row.subject, row.body) ?: continue
                when (
                    bounceCollectionService.ingest(
                        signal = signal,
                        senderAccountCode = row.senderAccountCode,
                        bounceMessageId = row.messageId,
                        from = row.fromEmail,
                        subject = row.subject,
                        receivedAt = row.receivedAt
                    )
                ) {
                    BounceIngestResult.INGESTED -> ingested++
                    BounceIngestResult.DUPLICATE -> duplicates++
                }
            }
            offset += batch.size
        }

        log.info(
            "Bounce backfill complete: scanned={}, ingested={}, duplicates={}",
            scanned,
            ingested,
            duplicates
        )
        return BounceBackfillResult(
            scanned = scanned,
            ingested = ingested,
            duplicates = duplicates
        )
    }
}

data class BounceBackfillResult(
    val scanned: Int,
    val ingested: Int,
    val duplicates: Int
)
