package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

/**
 * 计划 03b (V113, T2): 一封已发出的 RAG 回信的事实存证行 —— 对应
 * `mail_record_rag_fact` 表（与 `mail_record_qa_rule` 并列，一封信只会写其中一张，
 * I-39）。
 *
 * 不变量：
 * - I-41: [corpusFingerprint] 记录发出时的语料版本，用于复盘「当时的原文是哪一版」。
 * - I-42: [ordinal] 保存请求中 `ragFactCodes` 的**原始顺序**，不排序、不去重
 *   （去重在生成侧已完成）；查询恒走
 *   `MailRecordRagFactRepository.findByMailRecordIdOrderByOrdinalAsc`。
 * - G-1: [factCode] 是全链路唯一业务键（`KB-<AREA>-<NNN>`）；自增 id 绝不进入
 *   任何前端响应/审计记录。
 */
@Table("mail_record_rag_fact")
data class MailRecordRagFact(
    @Id
    val id: Long? = null,
    val mailRecordId: Long,
    val factCode: String,
    val ordinal: Int,
    val corpusFingerprint: String
)
