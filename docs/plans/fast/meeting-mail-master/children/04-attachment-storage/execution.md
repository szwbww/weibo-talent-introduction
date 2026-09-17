# Fast-P Execution — 04-attachment-storage

## Epoch 1 — IMPLEMENTATION

- Executor: isolated implementer `Implementer04`.
- Product base: `23b8fa1edf2edc8eb8977b682e95c1d4f941755a` (child 03 terminal code head); child 03 evidence commit precedes this implementation in Git ancestry.
- Plan: `docs/plans/fast/meeting-mail-master/children/04-attachment-storage/brief.md` (byte-identical copy of the approved `docs/plans/2026-09-16/meeting-mail-04-attachment-storage.md`).
- Dependency: child 01 (migration order); no business dependency on 02/03.
- New HTTP surface this child must expose for 06/07: `POST /api/mail/conversations/{contactId}/outbound-attachments` (single file per request, multipart field `file`) and `GET /api/mail/conversations/{contactId}/outbound-attachments/{id}/download` (draft download, uploader-only). Both owned by `OutboundAttachmentController.kt`.
- Required commands: `mvn test -Dtest=OutboundAttachmentServiceTest` (JDK 11); Flyway latest-version assertions advance to 126 with historical targets untouched. Docker/testcontainers is unavailable on this machine; local MySQL `127.0.0.1:3306/talent_introduction` is available.
- Dispatch: fresh implementer acquired through the Required Agent Gate.

### Epoch 1 result — READY_FOR_VERIFICATION (re-dispatch, second implementer)

- Executor: `Implementer04b`; epoch: NEW at the same path (first dispatch exited 1 without writing
  any product file; product tree was clean at `a7aaefb`).
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master`,
  branch `fast/meeting-mail-master`, git dir
  `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-meeting-mail-master`.
- Pre-execution HEAD: `a7aaefb6c3619767dfb296a0b597d1299dad979c` (docs-only commit on top of child-base code
  head `23b8fa1`).
- Implementation commit: `2540a0665cd1eff406bec460e56b75fced929cbf` — `feat(fast-p): implement 04-attachment-storage`,
  10 files, `git show --stat` = 10 files changed, 1639 insertions(+), 14 deletions(-). No push/merge/rebase/amend.
- Evidence files (`docs/plans/**`) deliberately **not** committed; `git status` after the commit shows only
  `docs/plans/fast/meeting-mail-master/children/04-attachment-storage/execution.md` and `.../ledger.md` modified.

#### Files changed (exactly the brief's 10 authorized files)

| # | File | Op | Content |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V126__create_outbound_mail_attachment.sql` | new | `outbound_mail_attachment` (id CHAR(36) PK, expert_contact_id FK RESTRICT, created_by VARCHAR(100), file_name/content_type VARCHAR(255), byte_length BIGINT, sha256 CHAR(64), created_at DATETIME(6)), index (expert_contact_id, created_at) |
| 2 | `src/main/kotlin/.../mail/domain/OutboundMailAttachment.kt` | new | immutable metadata row (no update/delete path) |
| 3 | `src/main/kotlin/.../mail/repository/OutboundMailAttachmentRepository.kt` | new | explicit `INSERT` + batch read by (expert_contact_id, id IN …); no UPDATE/DELETE statement exists |
| 4 | `src/main/kotlin/.../mail/service/OutboundAttachmentModels.kt` | new | `MAX_FILE_BYTES=10MiB` / `MAX_TOTAL_BYTES=20MiB` / `MAX_FILES=10` / schema 1, `OutboundAttachmentSnapshot`, `OutboundMailFile`, `OutboundAttachmentFileSet`, upload response, `OutboundAttachmentException`(400/404/409/413), `OutboundAttachmentSnapshotCodec`, name/MIME normalizers |
| 5 | `src/main/kotlin/.../mail/service/OutboundAttachmentService.kt` | new | bounded streaming upload → temp file → atomic move → metadata insert; ownership checks; verified original reads; `resolveForSend` / `loadSnapshots` / `resolveForMessageDownload` |
| 6 | `src/main/kotlin/.../mail/controller/OutboundAttachmentController.kt` | new | multipart upload (201) + draft download (attachment/nosniff/private,no-store) |
| 7 | `src/main/resources/application.yml` | modify | `spring.servlet.multipart.max-file-size: 10MB`, `max-request-size: 11MB` |
| 8 | `src/test/kotlin/.../mail/service/OutboundAttachmentServiceTest.kt` | new | 28 tests, real temp dir + MockMvc session + real advice |
| 9 | `src/test/kotlin/.../campaign/repository/FlywayMigrationIntegrationTest.kt` | modify | 14 latest-version assertions 125→126, `fresh database migrates through V126`, new `V126 creates outbound_mail_attachment with the persistence contract`; historical targets (`116`, `23`, `24`, `121`, …) untouched |
| 10 | `src/main/kotlin/.../common/controller/GlobalExceptionHandler.kt` | modify | added **only** `OutboundAttachmentException → its fixed 400/404/409/413` and `MaxUploadSizeExceededException → 413` (fixed message, no path/stack); all other handlers unchanged; no global `ResponseStatusException` mapping |

#### Commands (all run in this invocation, JDK 11 = zulu-11)

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test -Dtest=OutboundAttachmentServiceTest` | PASS | exit 0, `Tests run: 28, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS (run twice: pre-commit `Time elapsed: 1.968 s`, post-commit `Time elapsed: 2.509 s`) |
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | BLOCKED (not run — Docker absent) | Not attempted as a test run because `DockerClientFactory`/`MySQLContainer` cannot start: `docker info` → `failed to connect to the docker API at unix:///Users/lukai/.orbstack/run/docker.sock: no such file or directory` (OrbStack daemon not running; per environment note its bundled docker-java client is also rejected as `client version 1.32 is too old. Minimum supported API version is 1.40`). Recorded as 未验证 below. |
| Real-HTTP container verification (required by the brief for the multipart limit; `mvn spring-boot:run` on port 18080 + curl, MySQL 127.0.0.1:3307) | PASS on the exercised paths | see the HTTP table below |
| Migration chain as SQL against real MySQL (fallback for the blocked container test) | PASS | see 未验证 / partial evidence below |

#### Required command detail (post-commit fresh run)

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -o test -Dtest=OutboundAttachmentServiceTest
[INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.968 s
[INFO] BUILD SUCCESS
```
One real defect was found and fixed by this test before the final state: the upload response's
`downloadUrl` was missing the `{contactId}/outbound-attachments` segment (caught by
`upload stores original bytes…` and `http upload returns 201 …`).

#### Real-HTTP verification actually performed (Tomcat 9.0.83, app started with
`-Dspring-boot.run.arguments=--server.port=18080 … --talent-introduction.mail-attachment-storage.base-path=/tmp/outbound-http`,
auth enabled; MySQL 5.7.44 on 127.0.0.1:3307; `spring.flyway.enabled=false` because of the Flyway
blocker below; the whole 125-file migration chain was applied to that DB as raw SQL beforehand)

| Check | Result |
|---|---|
| anonymous upload / anonymous download | **401** (AuthInterceptor, before the controller) |
| upload `会议资料.zip` (4096 random bytes, `application/zip`) | **201**, `sha256` = ddecfe…daca = `shasum -a 256` of the local file, `byteLength=4096` = local size |
| draft download of that id | **200**, bytes identical (`cmp` clean), sha equal; headers `Content-Disposition: attachment; filename*=UTF-8''%E4%BC%9A…`, `Cache-Control: private,no-store`, `X-Content-Type-Options: nosniff`, `Content-Type: application/zip`, `Content-Length: 4096` |
| on-disk layout | single file `/tmp/outbound-http/outbound/<uuid>` (4096 bytes), no `.tmp-*` leftovers |
| 0-byte `empty.txt` | **201**, `byteLength=0`, sha = `e3b0c442…b855`; download **200** with 0 bytes |
| unknown expert contact `999999` | **404** `{"code":"NOT_FOUND","message":"专家不存在"}` |
| unknown/non-UUID attachment id download | **404** `{"code":"NOT_FOUND","message":"附件不存在或不属于当前会话"}` |
| exactly 10 MiB (10485760 bytes) | **201**, `byteLength=10485760` |
| 10 MiB + 1 byte (10485761) | **413** `{"code":"PAYLOAD_TOO_LARGE","message":"上传文件超出大小上限"}` — message produced only by the container-path handler ⇒ the **container's** `max-file-size: 10MB` rejected it before the service |
| missing multipart field | **400** `{"code":"BAD_REQUEST","message":"缺少 multipart 字段 file"}` (not 500) |
| original deleted from disk, then download | **404** `{"code":"NOT_FOUND","message":"附件原件缺失（id=…）"}`; after restoring the file, **200** with the same sha |
| I-1 row counts | `mail_attachment 0→0`, `expert_document 0→0`, `mail_record 0→0`, `meeting_calendar_event 0→0`, `outbound_mail_attachment 0→4` (4 = the 4 successful uploads) |
| DB row content | `created_by = admin` (session identity), `file_name = 会议资料.zip` (Chinese preserved), real `byte_length`/`sha256` |

Deviations in this step: (a) `curl -F "file=@…;type=text/plain"` cannot carry a CRLF-bearing declared
type, so the MIME fallback was **not** re-verified over HTTP (step 9 returned `text/plain`, correct
behaviour); the CRLF/param/garbage fallback is covered by the unit test
`upload falls back to octet stream for missing or malformed content types`. (b) Cross-operator
(second admin user) 404 was **not** re-verified over HTTP because `AuthController.login` accepts only
the single `admin` username; cross-user/cross-contact 404 is covered by MockMvc tests
(`http download returns 404 for another user`, `draft download rejects another user another contact and an unknown id with 404`).

#### Capacity / limit boundaries actually exercised

- 0 bytes → 201 (0-byte legal, download 0 bytes); 3 bytes; 4096 bytes; **exactly 10485760 → 201**; **10485761 → 413**.
- Service-side stream guard: unbounded `InputStream` → 413 with temp file deleted and no metadata inserted
  (the test would hang/OOM if the guard did not stop at the limit) and no leftover `.tmp-*`.
- Per-send: duplicate id → 400; 11 ids → 400; **exactly 20 MiB (10 MiB + 10 MiB) → accepted**; 20 MiB + 1 byte → 413.
- Codec: empty list and >10 entries rejected; per-item `byteLength = MAX_FILE_BYTES + 1` rejected; total > 20 MiB rejected.
- Filename: `C:\Users\x\会议 资料.zip` → `会议 资料.zip`; `/tmp/a\u0000b\u0007c.txt` → `abc.txt`; blank/`///`/`\u0000` → `attachment`;
  300-char name + `.pdf` → 255 chars with `.pdf` preserved.

#### Invariant coverage

- **I-1** upload writes only `outbound_mail_attachment` + `outbound/<uuid>`: proven by the live row-count
  delta (`mail_attachment`/`expert_document`/`mail_record`/`meeting_calendar_event` all unchanged) and by the
  repository having only `INSERT` + `SELECT` (no UPDATE/DELETE statement exists anywhere in the child), no SMTP /
  scheduling / expert-status write is present in any of the new files. UUID reuse impossible (PK) — live MySQL
  duplicate-id insert → `ERROR 1062`. `ON DELETE RESTRICT` FK → live `ERROR 1451`. Not-yet-referenced files are kept
  (no cleanup job, no lease/status columns), matching "removal only detaches the draft reference".
- **I-2** `contactId` must exist (live 404 for 999999; `ERROR 1452` for a directly inserted bad FK), uploader identity
  from session only (`created_by=admin`; no request-body operator field exists in the DTO/validated code path), draft
  download and send resolution both require same contact + same uploader (404 otherwise; unit + MockMvc), clients submit
  only ids (`resolveForSend` accepts an id list; no path/hash/size parameter exists in the API).
- **I-3** no extension/MIME allowlist (6 formats incl. no-extension and custom extension), 0-byte legal, streamed size with
  immediate 413 + temp cleanup, 10/per-send and 20 MiB/per-send bounds, duplicate ids 400, UTF-8 filename reduced to the
  last path segment with control chars stripped and 255-char cap, MIME restricted to a CRLF-free `type/subtype` else
  `application/octet-stream`, SHA-256/byteLength computed over the original bytes (live sha equality).
- **I-4** stored under `basePath/outbound/<UUID>` (user filename never a path), temp file → full validation → `ATOMIC_MOVE`
  → metadata insert, DB-failure deletes the file (unit test), client abort leaves nothing, reads verify realpath inside the
  outbound root + regular file + size + hash (missing 404, symlink escape/tamper 409; live 404 for a deleted original),
  `Content-Disposition: attachment` UTF-8 + `nosniff` + `private,no-store` verified on real HTTP.
- **I-5** `OutboundAttachmentSnapshot`(`schemaVersion=1,id,filename,contentType,byteLength,sha256`),
  no path/bytes/username in the JSON (asserted), selection order preserved and re-checked in tests, strict codec
  (NULL/blank → absent; corrupt/unknown schema/unknown field/illegal sha/illegal id/over-long/over-count/duplicate/trailing
  tokens → rejected; `parseOrThrow` fails closed with 409), no delete/update representation of "no generic attachments"
  was introduced in this child (the only stored form is 05's column, SQL NULL when empty), bytes never written to
  bodies/logs/audit JSON (no logging of bytes anywhere in the new files).

#### Downstream interface delivered for 05/06/07

- `POST /api/mail/conversations/{contactId}/outbound-attachments` (multipart `file`) → 201
  `{id,filename,contentType,byteLength,sha256,downloadUrl}`; `GET …/{id}/download` (draft, uploader-only).
- `OutboundAttachmentService.resolveForSend(contactId, attachmentIds, authenticatedUsername): OutboundAttachmentFileSet`
  (immutable bounded `files` + ordered `snapshots`, bounds enforced before bytes are read).
- Additionally exposed (06's already-planned needs, which 06's change list cannot add itself):
  `loadSnapshots(contactId, ids, username)` — metadata only, no bytes required, same ownership bounds, for the
  "already SENT" comparison; `resolveForMessageDownload(contactId, id)` — contact-scoped original read for
  sent-message downloads (deliberately no uploader check, matching 06's "已发跨操作员 200").
- `OutboundAttachmentSnapshotCodec.serialize/parseOrNull/parseOrThrow/validateSnapshot`,
  `MAX_FILE_BYTES`, `MAX_TOTAL_BYTES`, `MAX_FILES`, `OUTBOUND_ATTACHMENT_SCHEMA_VERSION` in `OutboundAttachmentModels.kt`.

#### 未验证 items (with the exact blocker)

1. **`FlywayMigrationIntegrationTest` (Docker/testcontainers, MySQL 8.0.36) — NOT executed.**
   `docker info` → `failed to connect to the docker API at unix:///Users/lukai/.orbstack/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/lukai/.orbstack/run/docker.sock: connect: no such file or directory`
   (OrbStack daemon not running; the environment note also records the bundled docker-java client being rejected with
   `client version 1.32 is too old. Minimum supported API version is 1.40`).
   Therefore `assertEquals("126", flyway.migrate().targetSchemaVersion)` and the new
   `V126 creates outbound_mail_attachment with the persistence contract` assertions are **written but not executed**.
2. **Local-MySQL substitute for the Flyway chain — also blocked, two exact errors:**
   - The app's Flyway against the only local server (MySQL 5.7.44, isolated instance on 127.0.0.1:3307):
     `org.flywaydb.core.api.FlywayException: Unsupported Database: MySQL 5.7` (surfaced through `flywayInitializer` at startup).
   - Running Flyway 8.5.13 with `flyway-mysql` on the test classpath (throwaway `/tmp/FlywayCheck04.java`):
     `org.flywaydb.core.internal.license.FlywayEditionUpgradeRequiredException: Flyway Teams Edition or MySQL upgrade required: MySQL 5.7 is no longer supported by Flyway Community Edition, but still supported by Flyway Teams Edition.`
     (`Flyway 8.5.13 Community`; `pom.xml` declares `org.flywaydb:flyway-mysql` with `<scope>test</scope>` — a pre-existing
     condition, not touched by this child.)
3. **Partial migration evidence obtained instead (real MySQL 5.7.44, raw SQL, same files/order as `classpath:db/migration`):**
   all 125 `V*.sql` files applied in `sort -V` order with the `mysql` client with **zero errors** and 58 tables created;
   `SHOW CREATE TABLE outbound_mail_attachment` produced exactly the intended DDL
   (`PRIMARY KEY (id)`, `KEY idx_outbound_mail_attachment_contact (expert_contact_id, created_at)`,
   `CONSTRAINT fk_outbound_mail_attachment_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact (id)`);
   the live information_schema queries taken verbatim from the new test's helpers return `id char/36`,
   index count 2 (`PRIMARY`, `idx_outbound_mail_attachment_contact`), FK count 1, and both `mail_attachment` /
   `expert_document` present; live inserts prove FK rejection (`ERROR 1452`), PK rejection (`ERROR 1062`) and
   `ON DELETE RESTRICT` (`ERROR 1451`). The MySQL *version* the integration test targets (8.0.36) remains unverified.
4. **Real-HTTP checks not performed** (covered by unit/MockMvc instead, see the deviations above): CRLF-bearing MIME
   fallback, and cross-operator 404 (single `admin` username in `AuthController.login`).
5. `OperatorStatusWriteSeamGuardTest` line-number noise (per `K-line-number-guard-breaks-on-any-insertion`) is **not**
   touched: none of the 10 authorized files is a file that test scans (it pins line numbers in `MailRecordRepository.kt`,
   `ExpertContactRepository.kt`, `ExpertSearchService.kt`), so no line-number correction was required.
