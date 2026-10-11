# Child 04 — Execution Log

(待实现；由 implementer 追加完整结果。)

---

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master/docs/plans/2026-10-11/contact-profile-04-video-api.md`
- Plan SHA-256: `e144ca0be7672878252f34676c4eb2372cb9aee82699efe984497151292c753a`
- Execution ID: `docs/plans/2026-10-11/contact-profile-04-video-api.md@e144ca0be7672878252f34676c4eb2372cb9aee82699efe984497151292c753a`
- Execution epoch: NEW（brief 指定 epoch 1；本 worktree 内无同 plan-hash 的既有执行证据）
- Approval basis: fast-p child 04 brief（`children/04/brief.md`）+ 本 worktree 内已提交的权威 child 计划与 master 计划
- Executor: Impl04
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master`
- Target branch: `fast/2026-10-11-contact-profile-development-master`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master@fast/2026-10-11-contact-profile-development-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master`
- Pre-execution code SHA: `bcd036f524d96c72de6beff8692dce50d1a66213`（child 04 base `89a23d351084debc54c7516898138320028fb5c1` 之后的准备提交）
- Post-execution code SHA / Evidence HEAD: `f248ebef68a80d8794e81b8a4cc5ff5c53d7edf3`（`feat(fast-p): implement 04`，9 文件，+3431/−30）
- Implementation boundary: `bcd036f..f248ebe`（唯一实现提交；无其它提交/无 amend/rebase）
- 报告本身不提交（`docs/plans/fast/**` 由 controller 单独处理）。

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| I-1 身份与鉴权（01 canonical key；播放不依赖 ES） | IMPLEMENTED | service/controller/domain | 服务单测 `identity failures map to 400 404 and 503…`、`list and playback never consult the identity resolver…`（解析器 `verifyNoInteractions`）；controller 单测 5 端点 401/403；IT 未登录 401、改 expertId 404 |
| I-2 单文件上限与流式（MAX=1073741824L；64 KiB 缓冲；无 Multipart/ByteArray） | IMPLEMENTED | service | 单测 `expectedSize declaredLength and stream counters…`、`upload streams one gibibyte plus one byte with a fixed 64 KiB buffer and aborts with 413`（生成式流、记录单次读取请求=65536）；IT 真实 HTTP 100MiB+1/1GiB/1GiB+1 |
| I-3 文件标识与原始名（服务端 UUID、磁盘只用 id、basename ≤255 不截断、扩展名白名单） | IMPLEMENTED | migration/domain/service | 单测 `upload stores basename extension and default stem…`、非法/超长名 400、415；Flyway IT V154 列契约 |
| I-4 自定义文件名（NFC+trim、1..100 code point、非法字符、尾部句点、默认主体、重命名不改 URL/文件） | IMPLEMENTED | service/controller | 单测 NFC/边界 100 与 101/非法字符/尾部句点；重命名单测断言文件未搬动、URL 不变；controller 单测 UTF-8 Content-Disposition |
| I-5 同名与并发（同专家唯一、跨专家允许、唯一索引裁决 409、CAS 旧版本 409） | IMPLEMENTED | migration/service | Flyway IT（真实 MySQL）`real meeting video service settles concurrent same name uploads and stale renames on V154`：两并发同名恰好 1 成功 + 1 `VIDEO_NAME_CONFLICT`、只落一行只留一文件；同版本并发重命名恰好 1 成功 + 1 `VIDEO_VERSION_CONFLICT`；跨专家同名与大小写差异允许 |
| I-6 文件与事务一致性（.tmp→ATOMIC_MOVE→短事务 INSERT；失败删 final/tmp；finally 放槽） | IMPLEMENTED | service | 单测 DuplicateKey/DataAccess/IO/不可写目录/容量各分支断言「目录无文件、无 DB 行为」；Flyway IT 输家文件被删；`mvn` 全程不持长事务（INSERT/rename 各一个 `TransactionTemplate`） |
| I-7 路径与响应（realPath 在真实根内、大小等于 byte_length、扩展名→Content-Type、UTF-8 Disposition、no-store/nosniff、失败不含路径） | IMPLEMENTED | service/controller | 单测 `resolveFile validates ownership real path and byte length`（含符号链接越界 500）、错误文案不含路径；controller 单测头集合逐项 |
| I-8 播放与 Range（不自制解析器；鉴权后 206/416） | IMPLEMENTED | controller | controller 单测 206/`Content-Range`/1024 字节、末尾与后缀范围、416 `bytes */N`、无 Range 200；IT 真实 HTTP 同上 + 1GiB 中途分片与磁盘逐字节比对 |
| I-9 资源与未知结果（2 并发槽、429、usableSpace≥expected+64MiB→507、写中 ENOSPC→507、断连不承诺撤销） | IMPLEMENTED | service | 单测 `two uploads hold the slots…`（第 3 个 429、释放后可续）、`low usable space…`（507）、`io failure with no space left maps to 507`；IT 1GiB+1 超限不留文件/行（断连路径被显式标记为「未拿到响应」） |
| I-10 独立存储与列表（不写旧表、只读分页 page/size、空数组、无删除/定时任务） | IMPLEMENTED | migration/service | V154 新表唯一写者；单测列表分页/边界/空结果；`docs/deploy/2026-10-11-expert-meeting-video.md` 第 6 节核对方法；无 DELETE/无调度 |
| V154 迁移 + 真实唯一键竞争 + 历史 target 保留 | IMPLEMENTED | `V154__create_expert_meeting_video.sql`、FlywayMigrationIntegrationTest | 30 处「迁移到最新」断言 153→154；历史 target（151/152）与 V152/V153 测试名原样保留；新增 2 个测试（新表契约、真实竞争） |
| 部署手册（1GiB 链路验证与回滚） | IMPLEMENTED | `docs/deploy/2026-10-11-expert-meeting-video.md` | 前置检查/上传前基线/验收步骤/结果表/失败与未知结果/孤儿核对（禁止盲删）/回滚/范围外 |

### Commands（全部在本 worktree、最终代码状态下新鲜运行）

| # | 命令（逐字） | 结果 | 证据 |
|---|---|---|---|
| 1 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertMeetingVideoServiceTest,ExpertMeetingVideoControllerTest,ExpertMeetingVideoStreamingIntegrationTest test` | PASS（exit 0） | `Tests run: 39, Failures: 0, Errors: 0, Skipped: 3`（Service 21/Controller 10/Streaming IT 8，3 个为 `videoLargeIt` 门控）；exec 插件 node 套件 `tests 1616 / pass 1616 / fail 0`；`BUILD SUCCESS`，2:19 min。完整输出：`/tmp/video-cmd1-v2.log` |
| 2 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` | PASS（exit 0） | `Tests run: 44, Failures: 0, Errors: 0, Skipped: 0`（child 01 后基线 42 + 本次 V154 两例；含 `V154 creates the expert meeting video table…` 与 `real meeting video service settles concurrent same name uploads and stale renames on V154`）；node 1616 pass；`BUILD SUCCESS`，9:15 min。完整输出：`/tmp/video-cmd2-v2.log` |
| 3 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DvideoLargeIt=true -Dtest=ExpertMeetingVideoServiceTest,ExpertMeetingVideoControllerTest,ExpertMeetingVideoStreamingIntegrationTest test` | PASS（exit 0） | `Tests run: 39, Failures: 0, Errors: 0, Skipped: 0`（门控档全部执行）；`[it] 100MiB+1 upload: 169 ms (605917 KiB/s)`；`[it] 1GiB upload: 1465 ms (698 MiB/s), used heap 149 MiB → 162 MiB`；node 1616 pass；`BUILD SUCCESS`，2:22 min。完整输出：`/tmp/video-cmd3-v2.log` |

补充口径：
- 命令 3 的开关用 `-DvideoLargeIt=true` 生效（pom 未列该属性也确认传进了 surefire fork：Skipped 从 3 变 0）；同时支持环境变量 `VIDEO_LARGE_IT=true`（`@EnabledIf("videoLargeTierEnabled")` 二者取或），不依赖修改 `pom.xml`（非授权文件）。
- 磁盘：本机可用约 39 GiB；大文件档的数据全部落在系统临时目录（`meeting-video-it-*`），测试结束递归删除；工作区无残留（`git status` 仅 9 个授权文件）。

### Changed Files（9 个，全部在 brief 授权清单内）

| 文件 | 作用 |
|---|---|
| `src/main/resources/db/migration/V154__create_expert_meeting_video.sql` | 新表 `expert_meeting_video`（10 列、PK、`uk_expert_meeting_video_name`、`idx_expert_meeting_video_list`、InnoDB/utf8mb4）；无 `${` 占位符 |
| `src/main/kotlin/com/weibo/talentintroduction/document/domain/ExpertMeetingVideo.kt` | `MeetingVideoLimits` 常量、`MeetingVideoView`/`MeetingVideoPage`/上传与重命名 DTO、固定 `MeetingVideoException`（状态+码） |
| `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMeetingVideoService.kt` | 唯一写者：raw 流式上传（64 KiB、`.tmp-`→ATOMIC_MOVE、短事务 INSERT）、列表、重命名 CAS、播放解析、并发槽/容量/ENOSPC |
| `src/main/kotlin/com/weibo/talentintroduction/document/controller/ExpertMeetingVideoController.kt` | 5 个端点、octet-stream 上传（`request.inputStream`）、局部 ExceptionHandler（固定 `{code,message,detail}`）、Resource/Range |
| `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMeetingVideoServiceTest.kt` | 21 例：命名/边界/清理/并发槽/列表/播放/重命名/身份 |
| `src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMeetingVideoControllerTest.kt` | 10 例：鉴权、上传协议与命令透传、415、错误码全集、列表/重命名、Range 206/416 与响应头 |
| `src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMeetingVideoStreamingIntegrationTest.kt` | 8 例：真实 Tomcat 真实 HTTP（小体积协议 + 门控 100MiB+1/1GiB/1GiB+1） |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 30 处最新版本断言 153→154、新增 V154 表契约与真实同名竞争/CAS 测试 |
| `docs/deploy/2026-10-11-expert-meeting-video.md` | 1 GiB 链路验证与回滚手册（检查命令、基线、结果表、失败/未知结果、孤儿核对、回滚） |

### Deviations / 设计取舍（均已在代码注释与本文标注）

1. **400 的码只用计划固定的三个**：计划把 400 固定为 `INVALID_VIDEO_NAME` / `VIDEO_SIZE_MISMATCH` / `EMPTY_VIDEO`，因此请求形状类问题（`expertId` 缺失/非法、分页参数越界、`expectedVersion`/`nameStem` 缺失、审计身份列超宽）统一落在 `INVALID_VIDEO_NAME`，具体原因写 message。01 解析器的 400 显式映射为 400 `INVALID_VIDEO_NAME`（"专家标识无效"）、404 → `VIDEO_NOT_FOUND`（"专家不存在"）、503 → `EXPERT_LOOKUP_UNAVAILABLE`，绝不落全局 500。
2. **非 octet-stream 的 415 用第二个 POST 映射兜底**：若只给主方法加 `consumes`，映射期抛出的 415 在无 handler method 时不会走本 controller 的局部 `@ExceptionHandler`（会落到 `@ControllerAdvice`，在本仓会被全局 catch-all 变成 500）。因此增加同路径、无 `consumes` 的 `rejectUnsupportedBody`（只取 `HttpServletRequest`，不解析请求体/不接受 parts），使 `application/json`、multipart、urlencoded、无 Content-Type 的 POST 都稳定得到 415 `UNSUPPORTED_VIDEO_TYPE`。
3. **重命名请求体的必填校验在 controller 边界重复一次**（`nameStem` 非空 + `expectedVersion` 存在 → 400），service 侧仍保留同一语义的校验作为兜底；这样 MVC 契约测试不依赖真实 service 也能断言 400。
4. **`usableSpaceProbe` 构造参数（默认真实 FileStore）**：仅为「低磁盘 507」提供可注入读数，生产不传参即真实磁盘；其余行为不可注入。
5. **`extension` 响应字段不带点**（`mp4`/`mov`/`webm`）；完整展示名 `displayFilename` 含 `.mp4` 等后缀；`previewUrl`/`downloadUrl` 是 contextPath 下的同源路径。
6. **命名细节**：上传时 `nameStem` 缺省/空白取「原始 basename 去末扩展名」；重命名必须给非空 `nameStem`（没有默认值语义）；`.mp4` 这类无主体名 → 415（无有效后缀/主体）；100 code point 的「完整名」包含扩展名。
7. **416 响应体**：Spring 5.3 的 Resource 分支在 416 时仍会写出资源体（审计证据 `spring-range-source.txt` 同源）；IT 客户端对响应体做 8 MiB 上限保护读取，未自制 Range 解析器（I-8）。
8. **并发同名 INSERT 的锁失败映射**：InnoDB 在唯一索引竞争上可能先报锁等待/死锁（01 同款事实），此时按 `PessimisticLockingFailureException` → 409 `VIDEO_NAME_CONFLICT`；真实竞争测试两种结果都落在「恰好一条成功」上。
9. **大文件档开关**：`-DvideoLargeIt=true` 已验证可传入 surefire fork；因 `pom.xml` 非授权文件，未把该属性写入 `<systemPropertyVariables>`（保留环境变量回退路径）。

### 测试设计事实（供 verify-p 复核）

- 单测/IT 全部使用**生成式 InputStream**，不构造 1 GiB 数组；单测断言单次 `read` 请求缓冲 = 65536 字节；IT 用 fixed-length 与 chunked 两种真实 HTTP 模式。
- 服务单测与 Streaming IT 的 JDBC 边界是 Mockito 替身（内存行台账，真实 `RowMapper` 在脚本化 `ResultSet` 上运行）；**真实唯一键/CAS/迁移**在 Flyway IT 的真实 MySQL 上验证（Docker + Testcontainers）。
- Streaming IT 用**真实 Tomcat 9 + 真实 DispatcherServlet**（`AnnotationConfigWebApplicationContext` + `@EnableWebMvc`，默认转换器顺序即生产顺序），因此 206/`Content-Range`/416 是框架真实产物；未注册 AuthWebConfig，401 来自 controller 自身的会话检查（interceptor 路径另由 `@WebMvcTest` 的 controller 单测覆盖）。
- Flyway IT 的「迁移到最新」断言 30 处已全部改为 `154`（`grep -c 'assertEquals("154"'` = 30）；历史 `fromVersion("151"/"152")` 与 V152/V153 测试名未改。

### Downstream interfaces（child 05 可直接依赖）

端点（全部要求现有登录会话；未登录 JSON 401 `UNAUTHORIZED`）：

| HTTP | 路径 / 参数 | 成功 | 说明 |
|---|---|---|---|
| POST | `/api/experts/meeting-videos?expertId=&level=&originalName=&nameStem=&expectedSize=`；`Content-Type: application/octet-stream`；body=原始字节 | 201 单个 VideoView | `level` ∈ RAW/CANDIDATE/APPLICATION（缺省 RAW）；`originalName` 必填（URLSearchParams 编码一次，服务端只解码一次）；`nameStem` 可选（缺省=原始名去末扩展名）；`expectedSize` 必填十进制字符串，1..1073741824；已知 `Content-Length` 必须等于 `expectedSize`；**必须用流式 XHR（不得 FormData）** |
| GET | `/api/experts/meeting-videos?expertId=&page=&size=` | 200 `{items,total,page,size}` | `page` 从 0（默认 0），`size` 默认 20 且 1..50；`uploaded_at DESC, id DESC`；无记录 `items=[]` |
| PATCH | `/api/experts/meeting-videos/{id}?expertId=`，JSON `{nameStem, expectedVersion}` | 200 最新 VideoView | `{nameStem,expectedVersion}` 都必填；名字不变也必须带当前 version；成功 `version+1`；不搬文件、URL 不变 |
| GET | `/api/experts/meeting-videos/{id}/preview?expertId=` | 200/206/416 `Resource` | `Content-Disposition: inline`；`Accept-Ranges: bytes`；无 Range→200 全量 |
| GET | `/api/experts/meeting-videos/{id}/download?expertId=` | 200/206/416 `Resource` | `Content-Disposition: attachment; filename*=UTF-8''…`（下载名=displayFilename） |

VideoView（逐字，13 键）：`{id, expertId, originalFilename, displayFilename, extension, byteLength, uploadedBy, uploadedAt, updatedBy, updatedAt, version, previewUrl, downloadUrl}`。
- `id`：服务端 UUID 字符串（不可变）；`expertId`：规范化后的 expert_key；
- `extension`：不带点的小写扩展名；`byteLength`：Long；
- `uploadedAt`/`updatedAt`：`yyyy-MM-dd'T'HH:mm:ss.SSS+08:00`（Asia/Shanghai，服务端生成）；
- `previewUrl`/`downloadUrl`：同源相对路径 `/api/experts/meeting-videos/<id>/preview?expertId=<encoded>`（可直接用于 `<video src>`/XHR）；
- 不返回磁盘路径、临时文件名、真实存储根。

错误（固定 `{code,message,detail}`，`detail` = HTTP reason phrase）：
`400 INVALID_VIDEO_NAME`（名称/标识/分页/请求体形状）、`400 VIDEO_SIZE_MISMATCH`（expectedSize 缺失或非数字、Content-Length 不符、实际字节数不符）、`400 EMPTY_VIDEO`（expectedSize=0 或流 0 字节）、`401 UNAUTHORIZED`、`404 VIDEO_NOT_FOUND`（专家不存在/视频不存在或不属于该专家/文件缺失）、`409 VIDEO_NAME_CONFLICT`、`409 VIDEO_VERSION_CONFLICT`、`413 VIDEO_TOO_LARGE`（预检或流中超限）、`415 UNSUPPORTED_VIDEO_TYPE`（非 octet-stream 或扩展名不在 mp4/mov/webm）、`429 VIDEO_UPLOAD_BUSY`、`503 EXPERT_LOOKUP_UNAVAILABLE`、`507 VIDEO_STORAGE_FULL`、`500 VIDEO_STORE_FAILED`（含未预期异常，固定文案，不外泄内部信息）。

05 交互约束：单实例同时最多 2 个上传（第 3 个 429，需串行排队重试）；单文件 ≤ 1073741824 字节；名字主体 ≤100 code point（含扩展名，`.mp4` 时主体 ≤96）、不得含 `/\:*?"<>|`、无控制字符、不以句点结尾；**断网/取消不承诺服务端撤销**：未拿到响应时必须提示「结果未确认，请查看列表」，不得自动重传、不得直接显示成功。

### NOT RUN / 遗留

- **真实生产 1 GiB 人工验收（A-1/A-3 的线上部分）**：NOT RUN（本机只做了开发环境真实 HTTP 1 GiB 档）。生产验收必须在生产/测试环境按部署手册第 3–4 节执行并填写结果表。
- **生产环境指标**（上传时长/速率、可用磁盘前后、JVM RSS 前后、DB 行数前后）：NOT RUN（手册表格待填；本地自动档仅为量级参考）。
- **真实磁盘写满（ENOSPC）**：无法在本机安全制造；单测覆盖「ENOSPC 形态 IOException → 507 + 清理」与「预检 507」，真实 ENOSPC 属发布环境验收项。
- **浏览器断网取消 / 转码兼容性 / 拖动手感**：人工验收项（A-3/A-4），本子计划不包含前端。
- **1 GiB+1 真实流超限的响应形态**：容器可能按 `maxSwallowSize` 提前断连而拿不到 JSON（计划的「结果未确认」路径）；IT 对两种结果都接受，并**强制断言**不落库、不留文件。
- 观察（不改动）：Spring 5.3 的 416 会写出资源体；大文件 416 会浪费一次连接带宽，属框架行为，I-8 禁止自制解析器，已在手册记录客户端应立即停止读取。

### Controller closing record — 2026-10-12

本文件在 fast-p child 04 的 brief 更正提交 `f45b8f8` 中被一并提交（controller 的 `git add` 范围过宽），导致首版证据提交 `57d67bf` 未包含 execution.md。为满足「child 证据提交必须同时记录 execution.md 与 verify-log.md」的 artifact 契约，controller 追加本真实记录，并以本次提交作为 child 04 的正式 Evidence commit；上文全部内容未做任何修改，verifier 结论与命令证据不受影响。

