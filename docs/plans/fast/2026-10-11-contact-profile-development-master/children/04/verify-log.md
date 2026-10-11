# Child 04 — Verify Log

_Epoch 1 · attempt 1 · 2026-10-12T01:39+08:00 · reviewed 89a23d..f248eb_

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 04 docs/plans/2026-10-11/contact-profile-04-video-api.md
Boundary: 89a23d351084debc54c7516898138320028fb5c1..f248ebef68a80d8794e81b8a4cc5ff5c53d7edf3
Verifier: Verify04

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Impl commit `f248ebe` (`git show --name-only`) touches exactly the 9 authorized files: V154 migration, document/domain+service+controller `ExpertMeetingVideo*`, 3 new test classes, `FlywayMigrationIntegrationTest.kt`, `docs/deploy/2026-10-11-expert-meeting-video.md`. Full range 89a23d..f248ebe adds only docs bookkeeping (children/03+04 `*.md`, `ledger.md`); no pom.xml / server.xml / frontend / 01–03 product files. |
| Plan and invariants | PASS | requireKnown reuse + 400/404/503 mapping (`ExpertMeetingVideoService.kt:284-294`); `MAX_BYTES=1073741824L` (`ExpertMeetingVideo.kt:15`); expectedSize 1..MAX, declaredLength equality, stream-bytes equality (`Service.kt:320-341,435-448`); 64 KiB buffer (`Service.kt:429`; test asserts maxRequestedBuffer=65536); no MultipartFile/`@RequestBody ByteArray` (controller:69 `request.inputStream`); UUID id, basename ≤255 cp, mp4/mov/webm whitelist (`Service.kt:346-368`); NFC/1..100 + case-sensitive unique + rename CAS (unit tests + real-MySQL race in Flyway IT); `.tmp`→ATOMIC_MOVE→short-tx INSERT + cleanup (`Service.kt:450,113-146`); root=basePath/meeting-videos (`Service.kt:398`); realRoot+size guard (`Service.kt:235-282`); FileSystemResource + UTF-8 disposition + no-store/nosniff (controller:132-141); Range only via Spring 5.3 converters, 206/416 after auth/ownership (controller test + real-Tomcat IT); semaphore(2)→429 + space→507 (`Service.kt:100,409`); list page 0/20/1..50, `ORDER BY uploaded_at DESC, id DESC`, empty array (`Service.kt:599`); SQL surface only `expert_meeting_video`. |
| Required commands | PASS | All freshly run in this worktree (JDK 11), exit 0. (1) video classes: `Tests run: 39, Failures: 0, Errors: 0, Skipped: 3` (Service 21, Controller 10, IT 8 w/ 3 large-gated) + node `tests 1616 / pass 1616 / fail 0`. (2) migrationIt: `Tests run: 44, 0, 0, 0` = 42 baseline + 2 new V154 (`now at version v154` in log) + node 1616. (3) `-DvideoLargeIt=true`: `Tests run: 39, 0, 0, 0`, `[it] 100MiB+1 upload: 175 ms`, `[it] 1GiB upload: 1476 ms (693 MiB/s), used heap 142 MiB → 154 MiB` + node 1616. Matches all baselines (42→44, node 1616, previously-absent classes now 39). |
| Downstream interfaces | PASS | Endpoints exact per plan: POST `/api/experts/meeting-videos` (octet-stream; query expertId/level/originalName/nameStem/expectedSize → 201 single VideoView), GET list `{items,total,page,size}`, PATCH `/{id}` `{nameStem,expectedVersion}`, GET `/{id}/preview` inline / `/{id}/download` attachment (controller:51-127). VideoView 13-field set (`ExpertMeetingVideo.kt:81-96`), dotless extension; previewUrl/downloadUrl same-origin with expertId encoded once (`Service.kt:535`; IT decode-once test); fixed `{code,message,detail}` incl. 409/413/429/507 (controller:145-176). Usable by 05 as required by the brief. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1: Child plan §2 requires “同名预检在读流前完成”, but `ExpertMeetingVideoService.upload` (`Service.kt:84-148`) performs identity/actor/size/name/capacity checks pre-stream and no duplicate-name SELECT — uniqueness rests solely on the INSERT unique key (DuplicateKey/PessimisticLockingFailure → 409, `Service.kt:113-119`). Outside the four gates: I-5’s index-as-final-arbiter and 409 semantics hold (proven by the real-MySQL race test); residual effect only that a same-name 1 GiB upload streams the full body before 409.
- O-2: `V154__create_expert_meeting_video.sql:8` comment carries literal `${...}` and the stale claim “生产未关闭 placeholder-replacement” while `application.yml:13` has had `placeholder-replacement: false` since child 01; per the corrected brief this is not a violation (Flyway IT sets `placeholderReplacement(false)`, `FlywayMigrationIntegrationTest.kt:3035`; no effective placeholder in V154). The execution report’s “无 `${` 占位符” wording is likewise imprecise vs the brief’s requested “无有效占位符”. Documentation accuracy only.

### Required Action
- COMPLETE_CHILD

### Controller evidence note — 2026-10-12

本报告由 Verify04 追加于 `57d67bf`（当时作为中间证据提交）。因 execution.md 更早被包含在 `f45b8f8`（brief 更正提交）中，controller 追加本真实备注，并以本次提交重新锚定 child 04 的 Evidence commit；verifier 的四门禁结论、命令计数与 RECORD_ONLY 内容均未改动。

