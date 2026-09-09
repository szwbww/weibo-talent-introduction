# fast-p child 01 execution — 专用模板、时区目录与日历预览

## Execution Result: READY_FOR_VERIFICATION

- Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-confirmation/docs/plans/2026-09-09/01-meeting-confirmation-preview-api.md
- Plan SHA-256: `f8a8926882cdc06ca170b40bdd25e5efa2b1808dc7eb563f23046f72b87d097b`（执行前与执行后一致，未变更）
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-confirmation/docs/plans/2026-09-09/01-meeting-confirmation-preview-api.md@f8a8926882cdc06ca170b40bdd25e5efa2b1808dc7eb563f23046f72b87d097b`
- Execution epoch: NEW（本分支无先前 01 执行证据）
- Approval basis: current invocation（brief + child plan + master，均在 commit 6d05499）
- Executor: ImplementChild01
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-confirmation
- Target branch: fast/meeting-confirmation
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-confirmation@fast/meeting-confirmation@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-meeting-confirmation`
- Pre-execution code SHA: `4e3613a3b59f287b3f9efa92d6aa673293d9a83e`（child base；HEAD 6d05499 仅含 docs/plans 种子提交，src 树与 base 逐字节一致）
- Post-execution code SHA: 见下方提交
- Implementation boundary: working tree（相对 child base HEAD 的 9 个授权文件改动）

## 源码哈希核对（child base 4e3613a）

以 `docs/plans/2026-09-09/meeting-confirmation-evidence/source-sha256.json` 为基准，对当前工作树全部列出文件计算 SHA-256：

- OK: 121（全部匹配，MISMATCH: 0）
- MISSING: 2（`artifacts/meeting-confirmation-preview/meeting.css/js` —— 计划研究期 artifact，不在 src 树；非本阶段源码，不构成冲突）
- 结论：无 PLAN_CONFLICT，且 `git diff 4e3613a..HEAD -- src/` 为空、工作树在改动前无 dirty 文件。

## Task Status

| 计划任务 | 状态 | 文件 | 证据 |
|---|---|---|---|
| T1 类型与公开协议（Models + Codec） | IMPLEMENTED | `mail/service/MeetingConfirmationModels.kt` | MeetingConfirmationServiceTest 35 用例全绿（Codec roundtrip/坏 JSON/hash/schema/contentType/filename/>64KiB/超长 token 断言） |
| T2 只读服务、3 接口、时区目录 | IMPLEMENTED | `MeetingConfirmationService.kt`、`MeetingConfirmationController.kt` | ControllerTest 12 用例全绿；ServiceTest 身份/账号/目录用例 |
| T3 邮件/日历唯一渲染 | IMPLEMENTED | `MeetingConfirmationService.kt` | ServiceTest：Istanbul 07:00/07:30Z、中国 15:00–15:30、meetingTime Friday+UTC+3、ICS 结构/折行/摘要/UID、同配置字节稳定 |
| T4 V122 seed + 普通单发门禁 | IMPLEMENTED | `V122__seed_manual_meeting_confirmation_template.sql`、`ManualExpertMailService.kt`、FlywayMigrationIntegrationTest | ManualExpertMailServiceTest 29 用例全绿（含 2 个新增隔离回归）；Flyway IT 18 用例全绿（含 2 个新增 V122 用例） |

## 验收标准逐项

| 验收项 | 结果 | 证据 |
|---|---|---|
| I-1 只读/身份/账号/401 | PASS | ServiceTest：`preview is served purely from read collaborators…`（服务只注入只读协作者 + verifyNoMoreInteractions 严格无额外交互）、contact 不匹配 IllegalArgument、不存在 NoSuchElement（→404）、账号默认与 Pending 同式 `getManualSendAccount(requested ?: inbound)`；ControllerTest：三个端点未登录 401 `UNAUTHORIZED` |
| I-2 专用模板域隔离 | PASS | ServiceTest options 仅启用+type；ManualExpertMailServiceTest 新增 `listSendOptions excludes…` 与 `sendManualMail rejects … before SMTP`（mailDeliveryService.never/mailRecordRepository.never）；旧 ${...} 全量回归通过（ManualExpertMailServiceTest/GateTest 全绿） |
| I-3 同源时间/DST | PASS | ServiceTest：Istanbul 07:00/07:30Z + `2026/09/11 周五 15:00 – 15:30` + meetingTime 精确文案；Shanghai/Kolkata(04:30Z,UTC+5:30)/Sydney 跨日两端完整日期/London `(UTC+0 → UTC+1)`；NY gap 2026-03-08T02:30 与 overlap 2026-11-01T01:30 固定文案 400；目录 offset 值（+3/+5:30/+1/-4/+8） |
| I-4 无默认猜测 | PASS | options 默认称呼=contact.expertName 原文、签名=账号字段拼接（空 title/team 不伪造 "Customer Care Officer"）；defaultZoneId=Asia/Shanghai；新表单日期/Zoom 为空由请求输入承载；无硬编码示例值 |
| I-5 受限内容/HTML 锚 | PASS | ServiceTest：XSS 称呼只被 escape；`zoom.us.evil.example`、`zoom.us@evil.example`、fragment、http、非会议 path、`{{` 注入全部拒绝；query `&tk=` 在 HTML 锚（`&amp;`）与 ICS URL/LOCATION 原样完整；模板未知/不闭合/缺变量/`${` 残留 → 固定文案 |
| I-6 可重算日历 | PASS | ServiceTest：unfold 后每物理行 ≤75 UTF-8 字节、中文/emoji 跨边界不断码（roundtrip 逐字还原）、TEXT 转义反斜线/逗号/换行还原、同配置字节相等、generatedAt 只改 sha 不改 semanticSha、无 METHOD/ATTENDEE/ORGANIZER、仅 1 VEVENT、UID=语义摘要前 32 hex+`@qingfei-calendar`、filename 规则与 expert 兜底 |
| IP-1/IP-2 控制器 JSON + 隔离 MySQL | PASS | ControllerTest（WebMvcTest + 真实 AuthInterceptor）12 全绿；FlywayMigrationIntegrationTest（Testcontainers MySQL 8.0.36 全新库迁移）18 全绿，含「V122 不碰旧 MEETING_CONFIRMATION」「V122 不覆盖已有人工同 code 配置」 |
| 人工验收 A-1..A-3 | NOT_RUN（人工） | 需真实来信/账号/日历客户端导入，由人工验收执行；其自动化等价断言已覆盖（A-2 全部数值 = ServiceTest Istanbul 样例；A-3 固定文案 = ServiceTest/ControllerTest） |

## Commands（全部在本轮最终状态下全新执行）

| 命令 | 结果 | 输出 |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MeetingConfirmationServiceTest,MeetingConfirmationControllerTest,ManualExpertMailServiceTest` | PASS（exit 0） | Tests run: 76, Failures: 0, Errors: 0, Skipped: 0（Service 35 / Controller 12 / Manual 29）；BUILD SUCCESS |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=… mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40` | PASS（exit 0） | Tests run: 18, Failures: 0, Errors: 0, Skipped: 0（143.9s；docker-java 对 OrbStack daemon 需 `-Dapi.version=1.40`，DOCKER_HOST 指向 OrbStack socket；Testcontainers 使用自有临时 MySQL，未触碰 127.0.0.1:3306 的 mailbox-refinement-mysql）；BUILD SUCCESS |
| `JAVA_HOME=… mvn test`（全量） | PASS（exit 0） | 239 个 surefire 类合计 Tests run: 3283, Failures: 0, Errors: 0, Skipped: 9（9 skipped 为 opt-in 门禁类，如 mysqlIt/其他系统属性门控，与基线一致）；node `--test` 766 pass / 141 suites；BUILD SUCCESS |

NOT_RUN：无（三条必跑命令全部执行；人工验收 A-1..A-3 属人工清单，非本机命令）。

## Changed Files（9/9 授权文件）

- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationModels.kt` — NEW：MeetingInput/PreviewRequest/PreviewResponse/Options/Zone/Template/Attachment DTO + `CalendarAttachmentSnapshot` + `object CalendarAttachmentCodec` + `MeetingConfirmationDomain` 常量
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt` — NEW：只读生成器（options/timeZones/preview/validateAndBuild）、校验固定文案、时区目录（冻结中文表逐字移入）、四变量渲染、HTML 锚规则、ICS 折行/转义、语义摘要/UID、文件名
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationController.kt` — NEW：3 端点（GET options、GET time-zones、POST preview），仅路由绑定
- `src/main/resources/db/migration/V122__seed_manual_meeting_confirmation_template.sql` — NEW：code 不存在才插头、仅对本次插入的头建单 CUSTOM_TEXT 块（LAST_INSERT_ID 守卫），不写 mail_template
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` — 两处门禁：listSendOptions 过滤 MANUAL_MEETING_CONFIRMATION；composeComposeTemplate 直传 id 先于 enabled/SMTP 拒绝
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt` — +2 隔离回归
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationServiceTest.kt` — NEW：35 用例（I-1..I-6 + Codec）
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt` — NEW：12 用例（JSON 契约/401/400/404）
- `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` — 10 处最新版本断言 121→122、3 个用例名更新、+2 V122 用例

## 明确决策/偏差（均在计划授权范围内）

- `targetKey` 计划未给具体格式，采用确定性 `"$contactId:$resolvedAccountCode"`（options 与 preview 同源，供 04 草稿作用域复用）。
- 时区排序：17 个固定常用区先行（与冻结 zoneChinese/zoneAliases 同序），其余含 `UTC` 按 id 字典序（计划字面规则）。
- 跨日/偏移变化的 meetingTime 文案按计划要素组合确定（同日样例与 A-2 逐字一致：`Friday, September 11, 2026, from 10:00 AM to 10:30 AM Türkiye Time (UTC+3)`）。
- 签名校验允许空串通过 options（账号全空字段时返回空签名、不伪造），preview 按 1..2000 强制非空（弹窗 required 契约一致）。
- `generatedAt` 缺失时服务端以 `Instant.now().truncatedTo(SECONDS)` 冻结并回传冻结值；其余输入回传原值（trim 后）。
- 过去时间的「会议时间已过去，请核对」为 UI 提示（04 职责），服务端不禁止、响应 DTO 不加字段（计划 DTO 字段固定）。
- ICS 内容 ≤64KiB 与 codec 校验一致；错误映射全部走既有 GlobalExceptionHandler（IllegalArgument→400 / NoSuchElement→404），控制器不抛 500 类 ResponseStatusException。

## Freshness

- Plan identity rechecked: YES（sha 一致 f8a89268…）
- Worktree identity rechecked: YES（root/branch/git-dir 一致）
- Reported commits reachable from target branch: 提交前核对见下
- Required commands run this invocation: YES（三条全部在本轮最终状态执行）
- Historical evidence used only as baseline: YES

## Remaining Blocker

- None。

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`（计划验收 + 人工 A-1..A-3 由人工/验证侧执行）
