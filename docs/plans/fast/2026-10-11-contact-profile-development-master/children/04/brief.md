# Child Brief — 04 (video-api)

- Child ID: `04`；执行 epoch 1。
- Authoritative child plan: `docs/plans/2026-10-11/contact-profile-04-video-api.md`（此 worktree 内已提交；**先完整阅读它，它是唯一权威合同**）。
- Master plan（全局合同）: `docs/plans/2026-10-11/contact-profile-development-master.md`（I-4 大文件真实边界、保持项、范围外、执行门槛 3）。
- child_base_sha: `89a23d351084debc54c7516898138320028fb5c1`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master`
- Branch: `fast/2026-10-11-contact-profile-development-master`
- 要求使用 `execute-p` 技能执行本子计划。

## Authorized files（精确 9 个；其余文件一律不得修改）

| # | 文件 | 操作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V154__create_expert_meeting_video.sql` | 新建：视频元数据与名称唯一约束 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/document/domain/ExpertMeetingVideo.kt` | 新建：DTO、固定异常/状态码 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMeetingVideoService.kt` | 新建：流式文件/元数据/命名单一写者 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/document/controller/ExpertMeetingVideoController.kt` | 新建：raw上传、列表、重命名、预览/下载 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMeetingVideoServiceTest.kt` | 新建：命名、流边界、失败清理 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMeetingVideoControllerTest.kt` | 新建：鉴权、归属、Range响应 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMeetingVideoStreamingIntegrationTest.kt` | 新建：真实HTTP/容器100MB以上流测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 修改：V154迁移、真实唯一键竞争 |
| 9 | `docs/deploy/2026-10-11-expert-meeting-video.md` | 新建：1GiB链路验证与回滚手册 |

## Required commands（全部必须真实运行并记录精确计数/退出码）

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertMeetingVideoServiceTest,ExpertMeetingVideoControllerTest,ExpertMeetingVideoStreamingIntegrationTest test`
2. `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test`（**必须带 DOCKER_HOST/api.version 前缀**；基线 B3r=39/0/0，01 后=42/0/0）
3. 大文件档（plan §3/命令要求的 release 档）：用小体积协议测试做默认门禁，另按你实现的专用开关（如 `-DvideoLargeIt=true`）在磁盘容量允许时运行 100MiB+1 与 1GiB 真实 HTTP 档并记录；**未运行时必须显式标 NOT RUN，不得声称已支持 1GiB**（人工验收 A-1 覆盖）。本机磁盘剩余 ~39GiB。

## 上游 child 01 已交付并验证的接口事实（04 必须按此对接）

- `ExpertProfileIdentityResolver.requireKnown(expertId: String?, level: ExpertIndexLevel): String` 返回规范化 expert_key；异常：`ExpertManualProfileInvalidRequestException`（400 语义）、`ExpertProfileNotFoundException`（404）、`ExpertProfileLookupUnavailableException`（503）。**video service 必须显式映射为同状态 VideoException（400/404/503），不能落入全局 500**；已有档案行时不访问 ES。
- V153 已建 `expert_manual_profile`（本 child 不修改）；FlywayMigrationIntegrationTest 的「迁移到最新」断言现为 **153，共 30 处**；历史 target 与 V152/V153 测试名保留，禁止全文件盲替换。

## 必须兑现的合同要点（以子计划正文为准，这里是标识索引）

- I-1 身份与鉴权：复用 01 canonical expertId；上传先 requireKnown；列表/重命名/播放/下载先验证登录，再以 `expert_key + video_id` 同时查元数据，归属不符 404；已有视频在 ES 临时故障时仍可播放（不逐个 Range 查询 ES）。
- I-2 单文件限额与流式：`MAX_BYTES=1073741824L`；expectedSize 必填且 1..MAX；已知 Content-Length 必须相等，未知长度按实际流计数，最终实际字节数必须等于 expectedSize；读取缓冲固定 64 KiB；禁止 MultipartFile/ByteArray/readBytes/base64；前置超限与实际流超限 413、零文件 400。
- I-3/I-4 名称：id 为服务端 UUID 不可变（磁盘只用 id）；`original_filename` 去路径 basename ≤255 code points 不截断；extension 由原始 basename 最后后缀小写派生，仅 mp4/mov/webm；`display_filename = NFC(trim(用户名称主体)) + 固定小写扩展名`，完整 1..100 code points，主体非空、无控制字符、无 `/\:*?"<>|`、无尾部句点；默认主体=原始 basename 去末扩展名，超长要求修改而非截断；重命名只改 display_filename/version/updated_by/updated_at，不搬文件、不改 URL。
- I-5 同名与并发：同一 expert_key 内 NFC 规范化后的完整 display_filename 精确且区分大小写唯一（唯一索引最终裁决 409 VIDEO_NAME_CONFLICT）；重命名 expectedVersion CAS，旧版本 409 VIDEO_VERSION_CONFLICT；成功 version+1，上传 version=1；跨专家允许同名。
- I-6 文件与事务一致性：root=`MailAttachmentStorageProperties.basePath/meeting-videos`（配置类已存在）；先在 `root/.tmp-UUID` 有界流写入并验证长度，close 后同目录 ATOMIC_MOVE 到 UUID，随后独立短事务 INSERT；数据库失败/唯一冲突删 final；IO 失败/中断删 tmp；finally 释放并发槽；不持有数据库事务传 1GiB；进程硬崩不保证自动清理，手册明确人工核对方法、禁止盲删。
- I-7/I-8 路径、响应与 Range：只接受元数据 UUID，不接受客户端路径；下载 realPath 必须在 realRoot 内且实际大小等于 byte_length，符号链接越界拒绝；返回 FileSystemResource（照 `ExpertDocumentBrowseController` 现有模式）+ 扩展名对应 `video/mp4|quicktime|webm`；inline/attachment 用 ContentDisposition UTF-8；`Cache-Control: no-store`、`X-Content-Type-Options: nosniff`；失败不返回绝对路径。**不实现自制 Range 解析器**，用 Spring 5.3.31 Resource/ResourceRegion 支持；Range 必须在鉴权和归属检查后生效；合法单段 206/Content-Range、越界 416。
- I-9 资源与未知结果：同实例最多 2 个视频上传并发（tryAcquire 失败 429），槽只在处理期持有；写文件前 usableSpace ≥ expectedSize+64MiB 否则 507；写中 ENOSPC 也 507 并清理；abort/取消不承诺服务端撤销；不自动重发不确定请求。
- I-10 独立存储与列表：视频不写 `manual_expert_material_upload`/`mail_attachment`/`expert_document`/`expert_material_status`；列表 GET 只读，page 从 0、size 默认 20 且 1..50，`uploaded_at DESC,id DESC`；无记录空数组；无自动任务、无 DELETE。
- 端点与错误码按 plan 表格逐字：POST `/api/experts/meeting-videos`（octet-stream raw，query expertId/level/originalName/nameStem/expectedSize）201 单 VideoView；GET 列表分页；PATCH `/{id}` `{nameStem,expectedVersion}`；GET `/{id}/preview` inline；GET `/{id}/download` attachment（下载名=displayFilename）；VideoView 字段集逐字（不返回磁盘路径）；错误 `{code,message,detail}`：400 INVALID_VIDEO_NAME/VIDEO_SIZE_MISMATCH/EMPTY_VIDEO、401、404、409、413、415、429、503、507、500 VIDEO_STORE_FAILED；业务错误不透传异常 message/路径。用户文件名参数经 URLSearchParams 编码、框架解码一次，不二次 URLDecoder；禁止从文件名拼路径。
- 测试：模型/流单测用生成式 InputStream（禁止构造 1GiB 内存数组）；小上限注入只用于单元测试，生产常量不可从请求覆盖；真实 HTTP 用流生成至临时文件/请求体验证 100MiB+1（及 1GiB 档按开关）；不要用 MockMultipartFile 假装大文件。Flyway IT：迁移 153→154、全新库→154、真实唯一键竞争（两请求只有一条记录）、历史 target 保留。
- 部署手册 `docs/deploy/2026-10-11-expert-meeting-video.md`：记录具体检查命令、上传时长/速度、可用磁盘前后、JVM RSS 前后、DB 行数、失败文件清理、Range 头、旧上传限额；不得上传真实敏感会议作测试。

## 保持项与范围外（不得触碰）

- 旧手动材料 100 MiB、旧回复通用附件 10 MiB、multipart 100MB/101MB 均不变；不把全局阈值改 1G；不新增分片/断点续传/转码/封面/对象存储/CDN/删除；不改 server.xml。
- 不修改 01/02/03 已交付文件（除本 brief 授权的 FlywayMigrationIntegrationTest）；前端、materials owner 链、会议/邮件状态零改动。
- 不修改 plan/审计/docks；不提交 `docs/plans/fast/**`（仅按报告路径写入 execution.md，不提交它）；不新增授权外文件；工作区不留未跟踪残留文件（大文件测试生成的临时数据放 /tmp 或 target 下并在结束时清理）。
- 迁移文件不得包含**有效**的 Flyway 占位符语法。事实更正（controller，2026-10-12）：生产 `application.yml` 已显式 `spring.flyway.placeholder-replacement: false`（含说明注释），Flyway IT 亦设 `placeholderReplacement(false)`；本仓现有 10 个迁移文件已含 `${` 文本（历史先例）。因此 `${...}` 仅出现在注释/说明文本中时**不构成生产风险、不视为违反本约束**；实现报告应使用「无有效占位符」这类准确表述。
- 本地提交恰好一次：`feat(fast-p): implement 04`（不得 push/merge/rebase/amend/squash）。

## Downstream interfaces（后续子计划依赖）

- 05 必须能按你交付的端点/参数/响应形制实现：raw XHR 上传（Content-Type application/octet-stream，非 FormData）、`expectedSize`、`originalName`、`nameStem`；409 同名/旧版本、413、429、507 错误码；`previewUrl`/`downloadUrl` 同源；列表分页 items/total/page/size。请把最终形制事实写入报告。

## Report

- 报告路径：`docs/plans/fast/2026-10-11-contact-profile-development-master/children/04/execution.md`（写入完整结果：改动文件、命令与结果、偏差、下游接口事实、未运行项、遗留风险；不提交）。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径（紧凑 JSON）。
