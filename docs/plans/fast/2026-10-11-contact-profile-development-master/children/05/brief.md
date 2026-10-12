# Child Brief — 05 (video-ui)

- Child ID: `05`；执行 epoch 1。
- Authoritative child plan: `docs/plans/2026-10-11/contact-profile-05-video-ui.md`（此 worktree 内已提交；**先完整阅读它**；注意 amendments A2/A3 已落地在计划正文：§实现方案 5 与 S-2 的落位=「逐字插入到 02 的 S-2 块之前」，缓存键目标=`20261011-contact-profile-05`）。
- Master plan（全局合同）: `docs/plans/2026-10-11/contact-profile-development-master.md`。
- child_base_sha: `f248ebef68a80d8794e81b8a4cc5ff5c53d7edf3`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master`
- Branch: `fast/2026-10-11-contact-profile-development-master`
- 要求使用 `execute-p` 技能执行本子计划。

## Authorized files（精确 4 个；其余文件一律不得修改）

| # | 文件 | 操作 |
|---|---|---|
| 1 | `src/main/resources/static/app.js` | 修改：视频列表、文件名、串行上传/进度、播放/改名/销毁 |
| 2 | `src/main/resources/static/styles.css` | 修改：只新增视频契约 CSS（逐字插入于 02 S-2 块之前） |
| 3 | `src/main/resources/static/index.html` | 修改：原 11 项资源缓存键统一 `20261011-contact-profile-05` |
| 4 | `src/test/js/expertMeetingVideo.test.js` | 新建：真实挂载及 XHR 状态/异常测试 |

## Required commands（全部必须真实运行并记录精确计数/退出码）

- `node --test src/test/js/expertMeetingVideo.test.js`
- `node --test src/test/js/*.test.js`（当前基线 tests 1616 / pass 1616 / fail 0；02/03 的静态契约测试必须保持全绿）
- `node --check src/main/resources/static/app.js`

## 跨计划约束（A1/A2/A3 已人工批准并落地，必须遵守）

- `src/test/js/expertManualProfile.test.js`（02 静态测试，**禁改**；不在你的授权内）固定：在 02 测试的切片沙箱内恰好 4×`.ec-section`、4×`.ec-editor`、nav 恰好 4 个按钮，且 **02 的 S-2 块是 `styles.css` 的末尾 CSS**。
  - → 你的新增（视频 `ec-section`、nav「会议视频」按钮、上传/重命名 dialog）必须沿用既有 **typeof 守卫惯例**接入 02 区域的调用点（先例：02 的 `unmountExpertMaterialsHosts`/`mountExpertMaterialsInline` 守卫、03 的三处守卫），使 02 沙箱内这些新节点不出现（计数保持 4）；你的代码区域整体落在 `/* contact-profile-02-ui:begin…end */` 之外。
  - → `styles.css`：把你的 S-2 视频 CSS 块**逐字插入到 02 的 S-2 块之前**，保持 02 块为文件末尾 CSS（amendment A3）；不修改任何既有规则。
  - → 缓存键：11 键统一为 `20261011-contact-profile-05`；A2 已把 02 计划字面量同步为 -05，你完成 bump 后 02 的键断言即通过。
- 03 已交付：备注 section 是 `ec-profile` 首子节点（`nav.ec-nav` 之前）；你的视频 section 位于**四组档案 section 之后**（与 03 不争位），不改动 03 的前置拼接顺序；视频请求/队列的销毁应接入既有 unmount 生命周期。
- 03 事实：`mountExpertContactSharedNote(session)` / `unmountExpertContactSharedNote(session)`；`session.sharedNote`；03 区域标记 `/* contact-profile-03-notes:begin…end */`。
- 02 事实：共用挂载入口 `mountExpertManualProfile(rootEl, context)`；**context 键集固定**（02 测试断言恰为 `contactId/expertId/generation/keyword/level/name/nationality`，不得新增键——视频所需 expertId/contactId 已在 context/session）；`ec-profile` 结构为 [03 备注 section] → `nav.ec-nav` → 4 个 `section.ec-section` → 4 个 `dialog.em-upload-dialog.ec-editor`；`.ec-*` 类与 `.em-upload-*` 骨架见 02 S-1/S-2 与 05 S-1。

## 上游 child 04 已交付并验证的 API 事实（05 直接消费）

| HTTP | 路径 / 参数 | 成功响应 | 说明 |
|---|---|---|---|
| POST | `/api/experts/meeting-videos?expertId=&level=&originalName=&nameStem=&expectedSize=`；`Content-Type: application/octet-stream`；body=原始字节 | 201 单个 VideoView | `level` ∈ RAW/CANDIDATE/APPLICATION（缺省 RAW，可不传）；`originalName` 必填（URLSearchParams 编码一次，服务端只解码一次）；`nameStem` 可选（缺省=原始名去末扩展名）；`expectedSize` 必填十进制字符串，1..1073741824；已知 `Content-Length` 必须等于 `expectedSize`；**必须用流式 XHR（不得 FormData）** |
| GET | `/api/experts/meeting-videos?expertId=&page=&size=` | 200 `{items,total,page,size}` | `page` 从 0（默认 0）；`size` 默认 20 且 1..50；`uploaded_at DESC, id DESC`；无记录 `items=[]` |
| PATCH | `/api/experts/meeting-videos/{id}?expertId=`，JSON `{nameStem, expectedVersion}` | 200 最新 VideoView | 两者都必填；成功 `version+1`；不搬文件、URL 不变 |
| GET | `/api/experts/meeting-videos/{id}/preview?expertId=` | 200/206/416 `Resource` | `Content-Disposition: inline`；`Accept-Ranges: bytes` |
| GET | `/api/experts/meeting-videos/{id}/download?expertId=` | 200/206/416 `Resource` | `attachment`，下载名=displayFilename |

- VideoView（逐字 13 键）：`{id, expertId, originalFilename, displayFilename, extension, byteLength, uploadedBy, uploadedAt, updatedBy, updatedAt, version, previewUrl, downloadUrl}`；`extension` **不带点**（mp4/mov/webm）；`uploadedAt/updatedAt` 为 Asia/Shanghai `yyyy-MM-dd'T'HH:mm:ss.SSS+08:00`；`previewUrl`/`downloadUrl` 为同源相对路径 `/api/experts/meeting-videos/<id>/{preview|download}?expertId=<encoded>`；不返回磁盘路径。
- 错误（固定 `{code,message,detail}`）：`400 INVALID_VIDEO_NAME`、`400 VIDEO_SIZE_MISMATCH`、`400 EMPTY_VIDEO`、`401 UNAUTHORIZED`、`404 VIDEO_NOT_FOUND`、`409 VIDEO_NAME_CONFLICT`、`409 VIDEO_VERSION_CONFLICT`、`413 VIDEO_TOO_LARGE`、`415 UNSUPPORTED_VIDEO_TYPE`、`429 VIDEO_UPLOAD_BUSY`、`503 EXPERT_LOOKUP_UNAVAILABLE`、`507 VIDEO_STORAGE_FULL`、`500 VIDEO_STORE_FAILED`。
- 交互约束：单实例同时最多 2 个上传（第 3 个 429 → 串行排队重试）；单文件 ≤ 1073741824 字节；名字主体 ≤100 code point（含扩展名，`.mp4` 时主体 ≤96）、禁 `/\:*?"<>|`、无控制字符、不以句点结尾；**断网/取消不承诺服务端撤销**：未拿到响应必须提示「结果未确认，请查看列表」，不得自动重传、不得直接显示成功。

## 必须兑现的合同要点（以子计划正文为准，这里是标识索引）

- I-1 列表与名字：绑定当前 canonical expertId；GET 分页 20；每项显示自定义完整名、原始名、大小、上传人/时间；名称主体可改、扩展名只读；前后端同 NFC/code point/非法字符规则；不同文件逐个命名；超长默认名必须人工改后才能上传。
- I-2 原始文件与进度：每次 `XHR.send(file)` 原始 File、`Content-Type: application/octet-stream`，不用 FormData/FileReader/base64；串行上传；`File.size > 1073741824` 网络前拦截；`upload.onprogress` 可计量才显示 loaded/total；100% 显示「上传完成，正在保存」；仅 201 显示「已上传」。
- I-3 状态与重试：队列状态仅 `pending/uploading/processing/success/failed/uncertain/cancelled`；pending 可改名移除，活动项可取消；success 禁止重发/移除（列表没有删除）；失败允许显式重试、不自动重传；abort/network/timeout 提交结果未知则 `uncertain`：「结果未确认，请刷新视频列表」；刷新发现同名提示检查已上传记录，用户确认无记录后从头重传。
- I-4 编辑与生命周期：rename 使用 version，409 保留输入并提示刷新、不自动覆盖；上传捕获原 expertId；切专家/退出时 abort 当前 XHR 并停止队列、关 dialog、移除监听/播放器 src 并 load 释放；晚响应不得写当前专家 DOM。
- I-5 鉴权与播放：同源 URL 与会话；401/403 转 `Response` 交既有 `handleAuthResponse`（`new Response(xhr.responseText,{status:xhr.status,headers:{"Content-Type":"application/json"}})`），并停队列；status 0 不构造非法 Response；只从 API 白名单同源路径设 `video.src` 与下载 href；`controls preload="metadata"`；解码失败提示「浏览器不支持此视频编码，请下载观看」，下载仍保留。
- S-1：视频 section 插在四组档案之后；nav 增加「会议视频」按钮（沿用 `ec-nav`/`.button`）；逐字 DOM 骨架（`data-role="expert-meeting-videos"`、`data-ec-video-*`、上传 dialog `.em-upload-dialog.ec-editor`、队列 row、progress 等）；重命名复用 02 编辑 dialog 骨架；label 用唯一 input id/for 关联；空态「暂无会议视频」；活动上传期间关闭按钮禁用、Escape 阻止关闭并提示先取消；不新增 class/tag 种类以外内容。
- S-2：视频 CSS 逐字（含 media 查询）；**插入位置按 A3**；高度上限桌面 420px/小屏 260px；上传条进度复用 5px 高蓝 #3b82f6；无虚构封面。
- §实现方案 3：`XHR.timeout=0`；onload 先检查状态/解析 JSON 再确认；status0/network/error/abort 区分未开始与未知提交；401/403 走 handleAuthResponse。
- 测试 `expertMeetingVideo.test.js`：覆盖全部状态与真实挂载、XHR 事件/异常、File 参数严格性（非 File/FormData）、超限网络前拦截、100% 未 201 不显示成功、rename 409、销毁清理等；**测试不得固化缓存键字面量**（`taskActivityCenter.test.js` 扫描源文件与测试文件的键字面量；应从 index.html 派生、从 05 计划正文解析目标键）。
- 保持项：19 项档案与共享备注、旧材料、邮件/会议安排、四标签保持；播放链接不随名称变更；未联系专家可用；不新增资源文件、11 项资源数量与顺序不变。

## Global constraints

- 不改 01–04 已交付文件（除本 brief 授权 4 件）；不修改 `expertManualProfile.test.js`/`expertContactSharedNote.test.js`；不改 pom/migrations/后端。
- 不修改 plan/审计/docs（除授权 3 个静态资源与 1 个新测试文件）；不提交 `docs/plans/fast/**`（仅按报告路径写入 execution.md，不提交它）；不新增授权外文件；工作区不留未跟踪残留文件。
- 本地提交恰好一次：`feat(fast-p): implement 05`（不得 push/merge/rebase/amend/squash）。

## Report

- 报告路径：`docs/plans/fast/2026-10-11-contact-profile-development-master/children/05/execution.md`（写入完整结果：改动文件、命令与结果、偏差、下游事实、遗留风险；不提交）。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径（紧凑 JSON）。
