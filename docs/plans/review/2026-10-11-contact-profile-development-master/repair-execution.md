# Repair Execution Handoff — Contact Profile Development Master

Master: `docs/plans/2026-10-11/contact-profile-development-master.md`

## Epoch 1 — 2026-10-12T13:08:00+0800

- Evidence mode: DURABLE_HANDOFF
- Human approval: HUMAN 批准执行（2026-10-12，批准 `$execute-p docs/plans/fix/contact-profile-development-master/repair.md`）
- Repair artifact: `docs/plans/fix/contact-profile-development-master/repair.md`（sha256 `bcafe7e37a7902a9bb0f7078dd7e1616435c5fc45d6993204a11b26cdd8671c9`，8628 bytes；执行前后两次重算一致，未变更）
- Prior code head: `0e4b5a89507e03350cddb883f7266c494317e25f`
- Repair code head: `9453e3f1db24c1f83aa189b9d8c134136533bfa4`
- Evidence parent before handoff commit: `9453e3f1db24c1f83aa189b9d8c134136533bfa4`
- Candidate code boundary for re-review: `0e4b5a89507e03350cddb883f7266c494317e25f..9453e3f1db24c1f83aa189b9d8c134136533bfa4`
- Executor: `RepairExec01`
- Executor result: `READY_FOR_VERIFICATION`
- Worktree/index: `CLEAN`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master`
- Branch: `fast/2026-10-11-contact-profile-development-master`
- Product commit: `9453e3f fix(fast-p): repair contact profile upload lifecycle`（仅 Authorized 5 文件，无 docs/）
- Evidence commit: `docs(fast-p): record contact profile repair verification`（仅本文件）

### Changed Product/Test Files
| File | Authorized | Evidence |
|---|---:|---|
| `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMeetingVideoService.kt` | yes | R-1/F-1+F-2：`displayNameExists` 预检（`SELECT_NAME_SQL`，canonical expert_key + NFC display_filename，读请求体/占并发槽之前，唯一索引仍为并发最终裁决）；`deleteQuietly` 清理只吞 IOException 且 finally 段 `try { deleteQuietly(tempPath) } finally { uploadSlots.release() }` 无条件释放；新增 `deleteArtifact` 注入 seam（默认真实 `Files.deleteIfExists`） |
| `src/main/resources/static/app.js` | yes | R-2/F-3：`expertMeetingVideoFinishInterrupted` 删除 `uploadedBytes>0`/`processing` 推断，abort/network/timeout 一律 `uncertain`（暂停队列+固定文案+刷新列表+显式重试）；删除失去消费者的 `cancelRequested` 与 `notStarted` 文案。R-3/F-4：`expertManualProfileEditorTemplate` 不再输出原生 `maxlength`，code-point 校验与展示元数据保留 |
| `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMeetingVideoServiceTest.kt` | yes | 新增：读禁流同名预检用例（0 次读体、无 INSERT、无文件、随后两并发上传仍各持一槽）；清理失败 seam 两用例（写失败 temp 清理失败仍返回固定 400、两并发仍各持一槽、第三个 429；唯一冲突 final 清理失败仍返回固定 409）；`upload(...)` helper 增加 `target` 参数 |
| `src/test/js/expertMeetingVideo.test.js` | yes | 新增 send 后零进度 abort/断网/超时回归用例（uncertain + 队列暂停 + 列表刷新 + 无自动重传 + 排队项保持 pending）；取消用例改为「已发送取消按未确认」；Escape/关闭用例断言 `uncertain` |
| `src/test/js/expertManualProfile.test.js` | yes | 结构断言改为「控件无原生 maxlength + field.maxLength 由 `expertManualProfileCodePointLength` 消费」；新增星平面边界用例（64 code point 星平面值接受并 PATCH，超 1 code point 报既有错误且不 PATCH） |

### Executor Commands
| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertMeetingVideoServiceTest,ExpertMeetingVideoControllerTest,ExpertMeetingVideoStreamingIntegrationTest test` | PASS | exit 0；Tests run 42, Failures 0, Errors 0, Skipped 3（大体积档按 `-DvideoLargeIt` 门禁跳过；service 24 / controller 10 / streaming IT 8 含 3 skipped）；同进程 JS exec 阶段 1642/1642 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DvideoLargeIt=true -Dtest=ExpertMeetingVideoServiceTest,ExpertMeetingVideoControllerTest,ExpertMeetingVideoStreamingIntegrationTest test` | PASS | exit 0；Tests run 42, Failures 0, Errors 0, Skipped 0（真实 1 GiB 路径执行）；JS exec 阶段 1642/1642 |
| `node --test src/test/js/expertManualProfile.test.js` | PASS | exit 0；tests 25, pass 25, fail 0, suites 6 |
| `node --test src/test/js/expertMeetingVideo.test.js` | PASS | exit 0；tests 25, pass 25, fail 0, suites 5 |
| `node --test src/test/js/expertContactSharedNote.test.js src/test/js/expertManualProfile.test.js` | PASS | exit 0；tests 49, pass 49, fail 0, suites 12 |
| `node --test src/test/js/*.test.js` | PASS | exit 0；tests 1642, pass 1642, fail 0, suites 315 |
| `node --check src/main/resources/static/app.js` | PASS | exit 0；无输出 |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` | PASS | exit 0；Tests run 44, Failures 0, Errors 0, Skipped 0 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertProfileIdentityResolverTest,ExpertManualProfileServiceTest,ExpertManualProfileControllerTest test` | PASS | exit 0；Tests run 37, Failures 0, Errors 0, Skipped 0（resolver 11 / service 17 / controller 9） |

Failing-first evidence（非必需命令，仅为 R-1..R-3 定向红态证据）：

- R-1：新增 seam 用例在实现前以 `Too many arguments for public constructor ExpertMeetingVideoService` test-compile 失败（`ExpertMeetingVideoServiceTest.kt:365`）；同名预检用例在旧实现下不会抛 409。
- R-2/R-3：将 `src/main/resources/static/app.js` 临时换回 `356ea56` 版本后运行本次新增/改写的 JS 用例：5 个失败恰为修复目标（`S-1 细节…maxlength`、`01 上限按 Unicode code point…`、`send 之后的 abort/断网/超时…`、`取消：活动项 abort…`、`活动上传期间关闭被禁用…`）；换回修复版后 50/50（两文件）通过，恢复后 sha256 与替换前一致（`8f2b7265…`）。

### Deviations
- 功能无偏差。实现说明（均为 R-1..R-3 的直接结果，不扩大范围）：
  - `cancelled` 仍保留在七态词表（`EXPERT_MEETING_VIDEO_STATES`、文案与渲染），因为它属于 05/主计划固定的状态集合；按 R-2 它不再由中断推断产生——`send` 之前移除 pending 队列项是唯一的「安全取消」路径。随之删除失去消费者的 `entry.cancelRequested` 标记与 `notStarted` 文案。
  - R-1.3「保留已定型结果」由两处断言覆盖：写失败路径的 temp 清理失败仍返回固定 `400 VIDEO_SIZE_MISMATCH`；唯一冲突路径的 final 清理失败仍返回固定 `409 VIDEO_NAME_CONFLICT`。清理失败会留下已知残留文件（04 要求人工核对、禁止盲删），测试显式断言该残留。
  - 预检 SELECT 的 `DataAccessException` 按既有读取失败语义映射为 `500 VIDEO_STORE_FAILED`，不放行成「无同名」。
- 未 push / merge / rebase / amend / reset；未改动 Authorized 5 文件之外的任何文件；产品提交不含 `docs/`。
