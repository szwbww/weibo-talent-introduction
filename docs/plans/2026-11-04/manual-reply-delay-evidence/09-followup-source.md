# 收尾复核源码

当前工作树原文；不是执行结果。

## src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundPendingOwnerIT.kt:33

```text
33: 
34: /** Uses the explicitly supplied disposable MySQL, real JDBC repositories and real consumer methods. */
35: @EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
36: @DataJdbcTest
37: @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
38: @Transactional(propagation = Propagation.NOT_SUPPORTED)
39: @TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false"])
40: @Import(ExpertMailReadAccessService::class)
41: class InboundPendingOwnerIT {
42:     @Autowired lateinit var jdbc: JdbcTemplate
```

## src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundPendingOwnerIT.kt:416

```text
416: 
417:     @Test fun `V170 legacy rows upgrade through V172 with current FULL ownership`() {
418:         val previous = Flyway.configure().dataSource(dataSource).placeholderReplacement(false).cleanDisabled(false).target("170").load()
419:         previous.clean()
420:         previous.migrate()
421:         setup()
422:         jdbc.update("INSERT INTO expert_mail_read_access(username,expert_contact_id,granted_by,created_at,access_level) VALUES('user1',?,'admin',NOW(),'FULL')", contactId)
423:         jdbc.update("INSERT INTO inbound_mail_processing (sender_account_code,imap_uid,from_email,received_at,process_status,process_reason,expert_contact_id) VALUES ('owner-test',900,'expert@example.test','2099-01-01','MANUAL_REVIEW','LEGACY',9201),('owner-test',901,'expert@example.test','2000-01-01','PROCESSED','LEGACY',9201)")
424:         val result = Flyway.configure().dataSource(dataSource).placeholderReplacement(false).load().migrate()
425:         assertEquals("172", result.targetSchemaVersion)
426:         assertEquals(2, result.migrationsExecuted)
427:         assertEquals(listOf("user1", "admin"), jdbc.queryForList("SELECT pending_owner_username FROM inbound_mail_processing ORDER BY imap_uid", String::class.java))
428:         assertEquals("NO", jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inbound_mail_processing' AND column_name='pending_owner_username'", String::class.java))
429:         assertEquals("admin", jdbc.queryForObject("SELECT COLUMN_DEFAULT FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inbound_mail_processing' AND column_name='pending_owner_username'", String::class.java))
430:         assertEquals(listOf("pending_owner_username", "process_status", "expert_contact_id", "sender_account_code"), jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='inbound_mail_processing' AND index_name='idx_inbound_pending_owner' ORDER BY seq_in_index", String::class.java))
431:     }
432: }
```

## src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt:470

```text
470:     @Test
471:     fun `V169 grants upgrade to READ and new grants default to READ`() {
472:         val previous = Flyway.configure().dataSource(dataSource).placeholderReplacement(false)
473:             .cleanDisabled(false).target("169").load()
474:         previous.clean()
475:         assertEquals("169", previous.migrate().targetSchemaVersion)
476:         cleanup() // V16 seeds the simulator campaign with id=1.
477:         seedAccount("read-access-acc")
478:         seedContact(1, "Old Expert", "old@example.test")
479:         seedContact(2, "Second Expert", "second@example.test")
480:         jdbcTemplate.update("""
481:             INSERT INTO expert_mail_read_access (username, expert_contact_id, granted_by, created_at)
482:             VALUES ('user1', 1, 'admin', '2020-01-01'), ('user1', 2, 'admin', '2020-01-02')
483:         """.trimIndent())
484:         val result = Flyway.configure().dataSource(dataSource).placeholderReplacement(false).load().migrate()
485:         assertEquals("172", result.targetSchemaVersion)
486:         assertEquals(3, result.migrationsExecuted)
487:         assertEquals(2L, jdbcTemplate.queryForObject(
488:             "SELECT COUNT(*) FROM expert_mail_read_access WHERE access_level='READ'", Long::class.java))
489:         assertEquals("2020-01-01 00:00:00", createdAt(1))
490:         assertEquals("NO", jdbcTemplate.queryForObject("""
491:             SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE()
492:               AND table_name='expert_mail_read_access' AND column_name='access_level'
493:         """.trimIndent(), String::class.java))
494:         assertEquals("READ", jdbcTemplate.queryForObject("""
495:             SELECT COLUMN_DEFAULT FROM information_schema.columns WHERE table_schema=DATABASE()
496:               AND table_name='expert_mail_read_access' AND column_name='access_level'
497:         """.trimIndent(), String::class.java))
498:     }
499: 
500:     @Test
501:     fun `fresh database applies the complete prefix through V172`() {
502:         val flyway = Flyway.configure().dataSource(dataSource).placeholderReplacement(false)
503:             .cleanDisabled(false).load()
504:         flyway.clean()
505:         assertEquals("172", flyway.migrate().targetSchemaVersion)
506:         assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM expert_mail_read_access", Long::class.java))
507:     }
```

## src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:257

```text
257:     @PostMapping("/unmatched-inbound/{id}/qa-reply")
258:     fun sendQaReply(
259:         @PathVariable id: Long,
260:         @RequestBody request: PendingQaReplyRequest
261:     ): PendingMailSendResult =
262:         throw ResponseStatusException(
263:             HttpStatus.GONE,
264:             "Use trust workbench and manual-rich-reply"
265:         )
```

## src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt:364

```text
364:     @PostMapping("/unmatched-inbound/{id}/composed-reply")
365:     fun sendComposedReply(
366:         @PathVariable id: Long,
367:         @RequestBody request: ComposedReplyRequest
368:     ): PendingMailSendResult =
369:         throw ResponseStatusException(
370:             HttpStatus.GONE,
371:             "Use trust workbench and manual-rich-reply"
372:         )
```

## src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewDraftRepository.kt:121

```text
121:     fun sendProjection(row: MailReviewDraftRow): MailReviewSendProjection {
122:         val attempt = row.sendAttemptId ?: return MailReviewSendProjection("NOT_SENT", null)
123:         return jdbc.query("""SELECT a.status,m.id AS record_id FROM mail_send_attempt a
124:             LEFT JOIN mail_record m ON m.mail_send_attempt_id=a.id AND m.send_status='SENT'
125:                 AND m.direction='OUTBOUND' AND m.expert_contact_id=:contact AND m.sender_account_code=:account
126:                 AND m.subject=:subject AND m.body=:text AND m.message_id=a.message_id
127:             WHERE a.id=:attempt""", mapOf("attempt" to attempt, "contact" to row.contactId,
128:             "account" to row.accountCode, "subject" to row.subject, "text" to row.text),
129:             RowMapper { r, _ -> MailReviewSendProjection(r.getString("status"),
130:                 if (r.getString("status") == "SENT") (r.getObject("record_id") as? Number)?.toLong() else null) })
131:             .singleOrNull() ?: MailReviewSendProjection("NOT_SENT", null)
132:     }
133: 
134:     fun event(id: Long): MailReviewDraftEvent? = jdbc.query(
```
