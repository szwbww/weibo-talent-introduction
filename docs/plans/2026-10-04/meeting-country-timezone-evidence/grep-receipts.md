# Grep receipts

exit=1 means no matches; not command failure.

```sh
rg -n calendarAttachmentJson|calendar_attachment_json src/main
```
exit=0
```text
src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecord.kt:35:    val calendarAttachmentJson: String? = null,
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:69:        require(CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson) != null) {
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:404:        val snapshot = CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson) ?: return null
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:372:                calendarAttachmentJson = snapshotJson,
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:393:                calendarAttachmentJson = snapshotJson,
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:470:                calendarAttachmentJson = snapshotJson,
src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt:492:                calendarAttachmentJson = snapshotJson,
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationModels.kt:13: * mail_record.calendar_attachment_json；客户端不能直传任意 downloadUrl。
src/main/kotlin/com/weibo/talentintroduction/mail/controller/CalendarAttachmentController.kt:59:            CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson)
src/main/resources/db/migration/V123__add_mail_record_calendar_attachment.sql:4:-- 只新增一列：calendar_attachment_json LONGTEXT NULL。
src/main/resources/db/migration/V123__add_mail_record_calendar_attachment.sql:14:    ADD COLUMN calendar_attachment_json LONGTEXT NULL;

```

```sh
rg -n calendarAttachment|snapshot.filename|calendar.filename src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt src/main/kotlin/com/weibo/talentintroduction/mail/controller/CalendarAttachmentController.kt src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt
```
exit=0
```text
src/main/kotlin/com/weibo/talentintroduction/mail/controller/CalendarAttachmentController.kt:59:            CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson)
src/main/kotlin/com/weibo/talentintroduction/mail/controller/CalendarAttachmentController.kt:75:                .filename(snapshot.filename, StandardCharsets.UTF_8)
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:292:                calendarAttachment = calendarAttachmentOf(row, mailRecordRowsById[row.id]),
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:391:    private fun calendarAttachmentOf(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:404:        val snapshot = CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson) ?: return null
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:407:            filename = snapshot.filename,
src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:454:                filename = snapshot.filename,
src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt:74:        val calendar = mail.calendarAttachment
src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt:111:                    fileName = calendar.filename
src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt:120:                    fileName = file.snapshot.filename

```

```sh
rg -n time-zones|timeZones\(|MeetingTimeZoneOption|filterZones src/main src/test
```
exit=0
```text
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationModels.kt:77:data class MeetingTimeZoneOption(
src/test/js/mailboxChatBehavior.test.js:729:    sourceUrl: "https://data.iana.org/time-zones/tzdb/zone.tab",
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:63:    fun timeZones(date: LocalDate): List<MeetingTimeZoneOption> {
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:72:            MeetingTimeZoneOption(
src/test/js/mailboxSuspension.test.js:720:    sourceUrl: "https://data.iana.org/time-zones/tzdb/zone.tab",
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationController.kt:7:import com.weibo.talentintroduction.mail.service.MeetingTimeZoneOption
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationController.kt:19: * 专家会议确认 · 只读接口（fast-p 01）：options / time-zones / preview。
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationController.kt:42:    @GetMapping("/meeting-confirmation/time-zones")
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationController.kt:43:    fun timeZones(
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationController.kt:45:    ): List<MeetingTimeZoneOption> {
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationController.kt:47:        return meetingConfirmationService.timeZones(date)
src/test/js/meetingConfirmation.test.js:4:// （filterZones / normalizeMeetingText / sanitizeDraftHtml / planMeetingInsertion）
src/test/js/meetingConfirmation.test.js:659:        if (/\/meeting-confirmation\/time-zones/.test(url)) {
src/test/js/meetingConfirmation.test.js:740:describe("fast-p 04 纯导出：filterZones（I-5 搜索语义）", () => {
src/test/js/meetingConfirmation.test.js:750:        const all = Meeting.filterZones(many, "");
src/test/js/meetingConfirmation.test.js:755:        assert.ok(Meeting.filterZones(ZONES, "土耳其").some((z) => z.id === "Europe/Istanbul"));
src/test/js/meetingConfirmation.test.js:756:        assert.ok(Meeting.filterZones(ZONES, "Türkiye").some((z) => z.id === "Europe/Istanbul"));
src/test/js/meetingConfirmation.test.js:757:        assert.ok(Meeting.filterZones(ZONES, "istanbul").some((z) => z.id === "Europe/Istanbul"));
src/test/js/meetingConfirmation.test.js:758:        assert.ok(Meeting.filterZones(ZONES, "Europe/Istanbul").some((z) => z.id === "Europe/Istanbul"));
src/test/js/meetingConfirmation.test.js:759:        assert.strictEqual(Meeting.filterZones(ZONES, "zzzz-no-zone").length, 0);
src/test/js/meetingConfirmation.test.js:763:        assert.ok(Meeting.filterZones(ZONES, "UTC+03:00").some((z) => z.id === "Europe/Istanbul"));
src/test/js/meetingConfirmation.test.js:764:        assert.ok(Meeting.filterZones(ZONES, "utc+3").some((z) => z.id === "Europe/Istanbul"));
src/test/js/meetingConfirmation.test.js:765:        assert.strictEqual(Meeting.filterZones(ZONES, "UTC+03:00").length, 1, "偏移秒数相等才算命中");
src/test/js/meetingConfirmation.test.js:766:        assert.ok(Meeting.filterZones(ZONES, "UTC\u22125").some((z) => z.id === "America/New_York"), "Unicode 减号归一");
src/test/js/meetingConfirmation.test.js:767:        assert.ok(Meeting.filterZones(ZONES, "UTC+5:30").some((z) => z.id === "Asia/Kolkata"), "半小时间隔命中");
src/test/js/meetingConfirmation.test.js:768:        assert.ok(Meeting.filterZones(ZONES, "UTC+5").some((z) => z.id === "Asia/Kolkata"), "整小时查询命中 +5:30 偏移");
src/test/js/meetingConfirmation.test.js:769:        assert.ok(!Meeting.filterZones(ZONES, "UTC+9").some((z) => z.id === "Asia/Kolkata"));
src/test/js/meetingConfirmation.test.js:773:        assert.strictEqual(Meeting.filterZones(ZONES, "  EUROPE/ISTANBUL  ").length, 1);
src/test/js/meetingConfirmation.test.js:774:        assert.strictEqual(Meeting.filterZones(ZONES, "ＩＳＴＡＮＢＵＬ").length, 1, "全角字母 NFKC");
src/test/js/meetingConfirmation.test.js:775:        assert.ok(Meeting.filterZones(ZONES, "utc+8").length >= 1);
src/test/js/meetingConfirmation.test.js:929:        assert.strictEqual(mount.api.requests.filter((r) => /time-zones/.test(r.url)).length, 1);
src/test/js/meetingConfirmation.test.js:1129:        const zoneCalls = () => mount.api.requests.filter((r) => /time-zones/.test(r.url));
src/test/js/mailboxOutboundAttachments.test.js:881:        if (/\/meeting-confirmation\/time-zones/.test(url)) {
src/test/js/worldClock.test.js:6:// meeting-confirmation.js 复用其 filterZones；注入最小 DOM / observer / timer / 时钟环境，
src/test/js/worldClock.test.js:801:        filterZones: config.filterZones === undefined ? Meeting.filterZones : config.filterZones
src/test/js/worldClock.test.js:1161:    it("缺少宿主依赖（api/filterZones/side/logout）时不挂载且不加 header class", () => {
src/test/js/worldClock.test.js:1163:            header: environment.header, api: null, filterZones: Meeting.filterZones
src/test/js/worldClock.test.js:1167:            header: environment.header, api: () => {}, filterZones: null
src/test/js/worldClock.test.js:1172:            header: bare, api: () => {}, filterZones: Meeting.filterZones
src/test/js/worldClock.test.js:1175:            header: null, api: () => {}, filterZones: Meeting.filterZones
src/test/js/worldClock.test.js:1254:        assert.ok(transport.calls[0].url.startsWith("/api/mail/meeting-confirmation/time-zones?date="));
src/test/js/worldClock.test.js:1335:        assert.deepStrictEqual(namesOf(), ["奥克兰"], "别名文本经宿主 filterZones 命中");
src/test/js/worldClock.test.js:1395:    it("UTC 偏移串由组件自身精确筛选，不依赖宿主 filterZones 的数值分支（I-5/R-3）", async () => {
src/test/js/worldClock.test.js:1404:        const { handle, transport } = mountComponent({ filterZones: substringOnly });
src/test/js/worldClock.test.js:1740:        assert.strictEqual(call.url.startsWith("/api/mail/meeting-confirmation/time-zones?date="), true);
src/test/js/worldClock.test.js:1836:            MailboxMeeting: { filterZones: (zones) => zones },
src/test/js/meetingConfirmationIntegration.test.js:855:        if (/\/meeting-confirmation\/time-zones/.test(url)) {
src/test/js/meetingConfirmationIntegration.test.js:1461:        const zoneCalls = ctx.calls.api.filter((e) => /\/meeting-confirmation\/time-zones/.test(e.url));
src/test/js/meetingConfirmationIntegration.test.js:1463:        assert.ok(zoneCalls.length >= 1, "time-zones 请求发生");
src/test/js/meetingConfirmationIntegration.test.js:1494:                if (fail && /\/meeting-confirmation\/(options|time-zones)/.test(url)) {
src/test/js/meetingConfirmationIntegration.test.js:1610:        const zonesBefore = ctx.calls.api.filter((e) => /\/time-zones/.test(e.url)).length;
src/test/js/meetingConfirmationIntegration.test.js:1612:        const zoneCalls = ctx.calls.api.filter((e) => /\/time-zones/.test(e.url));
src/main/resources/contact-country-timezones.json:3:  "sourceUrl": "https://data.iana.org/time-zones/tzdb/zone.tab",
src/main/resources/static/world-clock.js:4: * 筛选复用宿主 MailboxMeeting.filterZones，DOM 模板逐字取自计划 S-4，动态文本一律
src/main/resources/static/world-clock.js:10:    var CATALOG_PATH = "/api/mail/meeting-confirmation/time-zones?date=";
src/main/resources/static/world-clock.js:344:    function createInstance(header, api, filterZones) {
src/main/resources/static/world-clock.js:888:            var matched = filterZones(list, query);
src/main/resources/static/world-clock.js:1032:        if (typeof config.api !== "function" || typeof config.filterZones !== "function") return null;
src/main/resources/static/world-clock.js:1034:        var handle = createInstance(config.header, config.api, config.filterZones);
src/main/resources/static/world-clock.js:1055:        var filterZones = mailbox && typeof mailbox.filterZones === "function" ? mailbox.filterZones : null;
src/main/resources/static/world-clock.js:1056:        if (!header || !api || !filterZones) return null;
src/main/resources/static/world-clock.js:1057:        return mount({ header: header, api: api, filterZones: filterZones });
src/main/resources/static/meeting-confirmation.js:5: * 无 npm/构建依赖。本文件只做只读 API 调用（options / time-zones / preview）与
src/main/resources/static/meeting-confirmation.js:11: * 纯导出：filterZones / normalizeMeetingText / sanitizeDraftHtml /
src/main/resources/static/meeting-confirmation.js:104:    function filterZones(zones, query) {
src/main/resources/static/meeting-confirmation.js:413:            var zones = filterZones(state.zones, state.query);
src/main/resources/static/meeting-confirmation.js:831:        // ---- 配置加载（options / time-zones 并行；seq 保护） ----
src/main/resources/static/meeting-confirmation.js:837:            var url = "/api/mail/meeting-confirmation/time-zones?date=" + encodeURIComponent(zoneDate);
src/main/resources/static/meeting-confirmation.js:874:            var zonesUrl = "/api/mail/meeting-confirmation/time-zones?date=" + encodeURIComponent(zoneDate);
src/main/resources/static/meeting-confirmation.js:1454:        filterZones: filterZones,
src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationServiceTest.kt:517:        val zones = service.timeZones(LocalDate.of(2026, 9, 11))
src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationServiceTest.kt:557:        val zones = service.timeZones(LocalDate.of(2026, 9, 11))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactLocationControllerTest.kt:145:                sourceUrl = "https://data.iana.org/time-zones/tzdb/zone.tab",
src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactLocationControllerTest.kt:164:                  "sourceUrl": "https://data.iana.org/time-zones/tzdb/zone.tab",
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt:13:import com.weibo.talentintroduction.mail.service.MeetingTimeZoneOption
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt:73:        MeetingTimeZoneOption(
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt:80:        MeetingTimeZoneOption(
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt:130:    fun `time-zones requires authentication`() {
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt:132:        mockMvc.perform(get("/api/mail/meeting-confirmation/time-zones").param("date", "2026-09-11"))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt:199:    fun `time-zones list requires iso date param`() {
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt:202:            get("/api/mail/meeting-confirmation/time-zones").session(sessionOf()).param("date", "2026-09-11")
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt:206:            .timeZones(java.time.LocalDate.of(2026, 9, 11))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt:209:        mockMvc.perform(get("/api/mail/meeting-confirmation/time-zones").session(sessionOf()))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt:216:    fun `time-zones returns catalog json shape`() {
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt:218:        Mockito.`when`(meetingConfirmationService.timeZones(java.time.LocalDate.of(2026, 9, 11)))
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt:222:            get("/api/mail/meeting-confirmation/time-zones").session(sessionOf()).param("date", "2026-09-11")

```

```sh
rg -n MeetingConfirmationService\( src/main src/test
```
exit=0
```text
src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt:171:        meetingConfirmationService = MeetingConfirmationService(
src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt:436:        val generator = MeetingConfirmationService(
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt:109:    private val meetingConfirmationService = MeetingConfirmationService(
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:42:class MeetingConfirmationService(
src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt:213:        val generator = MeetingConfirmationService(
src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationServiceTest.kt:72:    private val service = MeetingConfirmationService(
src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt:1867:        val service = MeetingConfirmationService(

```

```sh
rg -n buildCalendarFilename|semanticSha256|zoneLabel src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt
```
exit=0
```text
134:        val semanticSha256 = semanticSha256(
146:        val uid = semanticSha256.take(32) + "@qingfei-calendar"
163:        val filename = buildCalendarFilename(startZoned.toLocalDate(), salutation)
187:                semanticSha256 = semanticSha256
392:        val label = zoneLabel(zone, zoneIdRaw)
404:    private fun zoneLabel(zone: ZoneId, zoneIdRaw: String): String = when (zoneIdRaw) {
529:    private fun buildCalendarFilename(date: LocalDate, salutation: String): String {
544:    private fun semanticSha256(

```

```sh
rg -n meeting_calendar_event|repository\.|Repository\. src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MeetingCalendarEventRepository.kt
```
exit=0
```text
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MeetingCalendarEventRepository.kt:126:        UPDATE meeting_calendar_event
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MeetingCalendarEventRepository.kt:151:        UPDATE meeting_calendar_event
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MeetingCalendarEventRepository.kt:183:              FROM meeting_calendar_event e
src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MeetingCalendarEventRepository.kt:188:            INSERT INTO meeting_calendar_event
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:5:import com.weibo.talentintroduction.campaign.repository.MeetingCalendarEventRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:28:    private val expertContactRepository: com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:30:    data class Page(val items: List<MeetingCalendarEventRepository.EventRow>, val nextCursor: String?)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:39:    ): MeetingCalendarEventRepository.EventRow {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:48:        val id = repository.insert(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:60:        return repository.findById(id) ?: error("Inserted meeting calendar event not found: $id")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:64:    fun createFromSentMail(record: MailRecord, input: MeetingCalendarInput): MeetingCalendarEventRepository.EventRow {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:75:        repository.findBySourceMailRecordId(recordId)?.let { existing ->
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:91:            val id = repository.insert(event)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:92:            repository.findById(id) ?: error("Inserted meeting calendar event not found: $id")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:94:            repository.findBySourceMailRecordIdForUpdate(recordId)?.let { existingSourceFor(record, it) } ?: throw ex
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:100:        existing: MeetingCalendarEventRepository.EventRow
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:101:    ): MeetingCalendarEventRepository.EventRow {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:108:    fun get(id: Long): MeetingCalendarEventRepository.EventRow =
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:109:        repository.findById(id) ?: throw NoSuchElementException("Meeting calendar event not found: $id")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:136:        val rows = repository.list(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:159:        val rows = repository.findActiveByContactIds(contactIds)
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:177:    ): MeetingCalendarEventRepository.EventRow {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:191:        val changed = repository.updateMutable(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:200:    fun cancel(id: Long, expectedUpdatedAt: String, reason: String?): MeetingCalendarEventRepository.EventRow {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:206:        val changed = repository.cancel(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:214:        repository.findByIdForUpdate(id) ?: throw NoSuchElementException("Meeting calendar event not found: $id")
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:218:        if (!expertContactRepository.existsById(contactId)) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:263:    private fun requireVersion(row: MeetingCalendarEventRepository.EventRow, expected: Instant) {
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:307:        val next: MeetingCalendarEventRepository.EventRow?
src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt:318:        private val EVENT_ORDER = compareBy<MeetingCalendarEventRepository.EventRow> { it.event.startsAtUtc }

```

```sh
rg -n fun |repository\. src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt
```
exit=0
```text
5:import com.weibo.talentintroduction.campaign.repository.MeetingCalendarEventRepository
28:    private val expertContactRepository: com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
33:    fun createManual(
48:        val id = repository.insert(
60:        return repository.findById(id) ?: error("Inserted meeting calendar event not found: $id")
64:    fun createFromSentMail(record: MailRecord, input: MeetingCalendarInput): MeetingCalendarEventRepository.EventRow {
75:        repository.findBySourceMailRecordId(recordId)?.let { existing ->
91:            val id = repository.insert(event)
92:            repository.findById(id) ?: error("Inserted meeting calendar event not found: $id")
94:            repository.findBySourceMailRecordIdForUpdate(recordId)?.let { existingSourceFor(record, it) } ?: throw ex
98:    private fun existingSourceFor(
108:    fun get(id: Long): MeetingCalendarEventRepository.EventRow =
109:        repository.findById(id) ?: throw NoSuchElementException("Meeting calendar event not found: $id")
111:    fun list(
136:        val rows = repository.list(
154:    fun summaries(contactIds: List<Long>): List<MeetingCalendarSummary> {
159:        val rows = repository.findActiveByContactIds(contactIds)
170:    fun update(
191:        val changed = repository.updateMutable(
200:    fun cancel(id: Long, expectedUpdatedAt: String, reason: String?): MeetingCalendarEventRepository.EventRow {
206:        val changed = repository.cancel(
213:    private fun locked(id: Long) =
214:        repository.findByIdForUpdate(id) ?: throw NoSuchElementException("Meeting calendar event not found: $id")
216:    private fun requireContact(contactId: Long) {
223:    private fun validateInput(input: MeetingCalendarInput) {
228:    private fun validateMeetingLink(value: String?) {
241:    private fun normalizeMeetingLink(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
242:    private fun normalizeNote(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }?.also {
245:    private fun normalizeCancelReason(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }?.also {
249:    private fun parseBeijing(value: String): Instant = try {
255:    private fun parseInstant(value: String): Instant = try {
261:    private fun parseVersion(value: String): Instant = parseInstant(value)
263:    private fun requireVersion(row: MeetingCalendarEventRepository.EventRow, expected: Instant) {
267:    private fun nextVersion(previous: Instant): Instant {
278:    private fun encodeCursor(filter: CursorFilter, startsAtUtc: Instant, id: Long): String {
286:    private fun decodeCursor(value: String): Cursor {
323:private fun Instant.toUtcLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)

```

```sh
rg -n contact-country-timezones|meeting-timezones-zh.properties src/main scripts
```
exit=0
```text
scripts/tzdb_catalog_probe.py:8:    1. `contact-country-timezones.json` 的全部目录 id 在该 JVM 上可解析；
scripts/tzdb_catalog_probe.py:9:    2. `meeting-timezones-zh.properties` 覆盖该 JVM 的全部可选取 id
scripts/tzdb_catalog_probe.py:28:LOCATION_CATALOG = REPO_ROOT / "src/main/resources/contact-country-timezones.json"
scripts/tzdb_catalog_probe.py:29:MEETING_CATALOG = REPO_ROOT / "src/main/resources/meeting-timezones-zh.properties"
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationCatalog.kt:12: * 唯一数据来源是 classpath 上的 `contact-country-timezones.json` 快照（证据目录
src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationCatalog.kt:76:        const val RESOURCE_PATH = "contact-country-timezones.json"
src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt:646:                .getResourceAsStream("meeting-timezones-zh.properties")

```

```sh
rg -n meeting-zone-field|meeting-zone-control|meeting-zone-options|meeting-form src/main/resources/static src/test/js/meetingConfirmationStyle.test.js
```
exit=0
```text
src/main/resources/static/meeting-confirmation.js:1388:        '<div class="meeting-form">' +
src/main/resources/static/meeting-confirmation.js:1393:        '<div class="meeting-zone-field">' +
src/main/resources/static/meeting-confirmation.js:1395:        '<div class="meeting-zone-control">' +
src/main/resources/static/meeting-confirmation.js:1399:        '<div id="meetingZoneOptions" class="meeting-zone-options" role="listbox" aria-label="会议时区选项" hidden></div>' +
src/main/resources/static/meeting-confirmation.css:18:.meeting-form{padding:18px 24px;border-right:1px solid #e2e8f0;min-width:0}
src/main/resources/static/meeting-confirmation.css:20:.meeting-form label{display:flex;flex-direction:column;gap:6px;font-size:12px;line-height:18px;color:#52647e;margin-bottom:12px;font-weight:500;letter-spacing:0;text-transform:none;min-width:0}
src/main/resources/static/meeting-confirmation.css:21:.meeting-form input,.meeting-form select,.meeting-form textarea{min-width:0;width:100%;height:36px;min-height:36px;margin:0;padding:7px 10px;font:inherit;font-weight:400;color:#334155;border:1px solid #d7e0ed;border-radius:7px;background:#fff;box-shadow:none}
src/main/resources/static/meeting-confirmation.css:22:.meeting-form textarea{height:70px;resize:vertical;line-height:1.6}
src/main/resources/static/meeting-confirmation.css:23:.meeting-form input::placeholder,.meeting-form textarea::placeholder{color:#94a3b8;opacity:1}
src/main/resources/static/meeting-confirmation.css:24:.meeting-form small{font-size:11px;font-weight:400;color:#76859b;line-height:16px}
src/main/resources/static/meeting-confirmation.css:25:.meeting-form :is(input,textarea,select):hover:not(:disabled){border-color:#93b4ec}
src/main/resources/static/meeting-confirmation.css:26:.meeting-form :is(input,textarea,select)[aria-invalid=true]{border-color:#e11d48;background:#fff8fa}
src/main/resources/static/meeting-confirmation.css:27:.meeting-form :is(input,textarea):read-only{background:#f8faff}
src/main/resources/static/meeting-confirmation.css:28:.meeting-form :is(input,textarea,select):disabled{opacity:.55;cursor:not-allowed;background:#f1f5f9}
src/main/resources/static/meeting-confirmation.css:63:.meeting-form .meeting-template textarea{height:245px;margin:12px 0 8px;font-family:ui-monospace,monospace;font-size:11px}
src/main/resources/static/meeting-confirmation.css:76:.meeting-zone-field{position:relative;margin-bottom:14px}
src/main/resources/static/meeting-confirmation.css:77:.meeting-zone-field>label{margin-bottom:6px}
src/main/resources/static/meeting-confirmation.css:78:.meeting-zone-control{display:flex;position:relative}
src/main/resources/static/meeting-confirmation.css:79:.meeting-zone-control input{padding-right:40px}
src/main/resources/static/meeting-confirmation.css:80:.meeting-zone-control>button{position:absolute;right:1px;top:1px;width:34px;height:34px;padding:0;border:0;background:#f7faff;color:#627ca5;border-radius:0 6px 6px 0;font:inherit;cursor:pointer}
src/main/resources/static/meeting-confirmation.css:81:.meeting-zone-control>button:hover{background:#edf3ff;color:#1e40af}
src/main/resources/static/meeting-confirmation.css:82:.meeting-zone-control>button:active{background:#dbeafe}
src/main/resources/static/meeting-confirmation.css:83:.meeting-zone-field>small{display:block;margin-top:6px}
src/main/resources/static/meeting-confirmation.css:84:.meeting-zone-options{position:absolute;top:64px;left:0;right:0;max-height:252px;overflow:auto;overscroll-behavior:contain;z-index:10;border:1px solid #cbd9ed;border-radius:8px;background:#fff;box-shadow:0 10px 25px #223c6226;padding:5px}
src/main/resources/static/meeting-confirmation.css:85:.meeting-zone-options>button{width:100%;display:flex;align-items:center;justify-content:space-between;gap:10px;padding:9px 10px;background:#fff;border:0;border-radius:5px;text-align:left;color:#334155;font:inherit;font-size:12px;line-height:1.6;cursor:pointer}
src/main/resources/static/meeting-confirmation.css:86:.meeting-zone-options>button:hover,.meeting-zone-options>button.focused{background:#eff5ff}
src/main/resources/static/meeting-confirmation.css:87:.meeting-zone-options>button:active{background:#dbeafe}
src/main/resources/static/meeting-confirmation.css:88:.meeting-zone-options>button[aria-selected=true]{background:#eaf1ff;color:#1e40af}
src/main/resources/static/meeting-confirmation.css:89:.meeting-zone-options small{display:block;margin-top:2px;font-size:11px;color:#7b8ba2;line-height:1.5}
src/main/resources/static/meeting-confirmation.css:90:.meeting-zone-options>button>span:last-child{white-space:nowrap;color:#5b769e}
src/main/resources/static/meeting-confirmation.css:101:@media(max-width:800px){.meeting-grid{grid-template-columns:minmax(0,1fr)}.meeting-form{padding:18px;border-right:0;border-bottom:1px solid #e2e8f0}.meeting-preview{padding:18px}.meeting-dialog{width:calc(100vw - 20px);max-height:calc(100dvh - 20px)}.meeting-bottom{flex-wrap:wrap;padding:12px 18px}.meeting-head{padding:16px 18px}.meeting-head h2{font-size:17px}.meeting-bottom>div{margin-left:auto}.meeting-fields{gap:9px}.meeting-file{flex-wrap:wrap}}
src/main/resources/static/meeting-confirmation.css:102:@media(max-width:420px){.meeting-head,.meeting-form,.meeting-preview{padding:14px}.meeting-paper{padding:16px}.meeting-bottom{padding:12px 14px}.meeting-bottom>div{width:100%;justify-content:flex-end}.meeting-bottom .button{padding:0 10px}.meeting-zone-options{max-height:220px}}
src/main/resources/static/styles.css:12585:    body .meeting-dialog .meeting-form :is(input, select, textarea),

```

```sh
rg -n filename|Türkiye Time|China Standard Time|Kolkata Time src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationServiceTest.kt
```
exit=0
```text
292:            "Friday, September 11, 2026, from 10:00 AM to 10:30 AM Türkiye Time (UTC+3)",
297:        assertTrue(response.textBody.contains("Friday, September 11, 2026, from 10:00 AM to 10:30 AM Türkiye Time (UTC+3)"))
300:        assertEquals("meeting-2026-09-11-Professor-Basdogan.ics", response.attachment.filename)
360:        assertEquals("meeting-2026-09-11-Professor-Basdogan.ics", response.attachment.filename)
599:        assertTrue(response.meetingTime.contains("China Standard Time (UTC+8)"))
610:        assertTrue(response.meetingTime.contains("Kolkata Time (UTC+5:30)"))
631:        assertEquals("meeting-2026-09-11-Professor-Basdogan.ics", response.attachment.filename)
889:            "Friday\\, September 11\\, 2026\\, from 10:00 AM to 10:30 AM Türkiye Time (UTC+3)\\n\\n" +
894:            "Friday, September 11, 2026, from 10:00 AM to 10:30 AM Türkiye Time (UTC+3)\n\n" +
963:    fun `filename falls back to expert for non-ascii salutation`() {
970:        assertEquals("meeting-2026-09-11-expert.ics", response.attachment.filename)
971:        assertTrue(MeetingConfirmationDomain.CALENDAR_FILENAME_REGEX.matches(response.attachment.filename))
975:        assertEquals("meeting-2026-09-11-Dr-Anne-Marie-O-Brien-x.ics", punctuationName.attachment.filename)
1011:        filename = "meeting-2026-09-11-Professor-Basdogan.ics",
1062:        // 非法 filename
1065:                json.replace(snapshot.filename, "meeting-2026-09-11-x.ics.ics")
1070:                json.replace(snapshot.filename, "../meeting-2026-09-11-x.ics")
1076:                json.replace(snapshot.filename, "meeting-2026-09-11-" + "A".repeat(61) + ".ics")
1081:        val oversizedJson = """{"schemaVersion":1,"filename":"meeting-2026-09-11-x.ics",""" +
1097:            CalendarAttachmentCodec.serialize(base.copy(filename = "evil.ics"))

```

```sh
rg -n \?v= src/main/resources/static/index.html
```
exit=0
```text
11:    <link rel="stylesheet" href="styles.css?v=20261004-mailbox-suspension-followup">
12:    <link rel="stylesheet" href="expert-materials.css?v=20261004-mailbox-suspension-followup">
13:    <link rel="stylesheet" href="mailbox-chat.css?v=20261004-mailbox-suspension-followup">
14:    <link rel="stylesheet" href="meeting-confirmation.css?v=20261004-mailbox-suspension-followup">
15:    <link rel="stylesheet" href="world-clock.css?v=20261004-mailbox-suspension-followup">
2352:<script src="trust-reply-workbench.js?v=20261004-mailbox-suspension-followup"></script>
2353:<script src="expert-materials.js?v=20261004-mailbox-suspension-followup"></script>
2354:<script src="meeting-confirmation.js?v=20261004-mailbox-suspension-followup"></script>
2355:<script src="mailbox-chat.js?v=20261004-mailbox-suspension-followup"></script>
2356:<script src="app.js?v=20261004-mailbox-suspension-followup"></script>
2357:<script src="world-clock.js?v=20261004-mailbox-suspension-followup"></script>

```

```sh
rg -n -F 20261004-mailbox-suspension-followup src/test
```
exit=1
```text


```
