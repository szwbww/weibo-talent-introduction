# 专家材料手动上传：主计划

> 作用：本文件是两份子计划的唯一执行入口和跨计划契约，不直接授权修改业务源文件。任何执行、验证、修复都必须先定位到对应子计划；禁止把 16 个源文件合并成一个无边界实施批次。权威性分工：本文件管执行顺序、跨层契约、联合门禁和发布；子计划管各自文件、实现细节、样式及独立验收。发生冲突时停止并修订计划，不自行选择其一。

## 需求描述

交付一个完整、可发布的专家材料手动上传能力：运营人员在材料管理中多选本地文件，系统逐个上传；单文件不超过 104857600 字节；成功后文件已在服务器，材料立即为 `STORED / 已存服务器`、`PENDING_REVIEW / 待审核`，无需再次获取。

不得改变：

- 邮件附件的登记、按需获取、重试和状态机。
- 人工回复通用附件的 10 MiB 单文件业务上限。
- 邮箱消息附件计数、自动回复附件意图和运营状态反推。
- 现有材料下载、预览、AI 分析的归属与路径安全校验。
- `ExpertMaterials` 单 contactId 共享 store、selectionOnly 行为和静态资源统一缓存键契约。
- 当前工作树中与本功能无关的 SharePoint 文件卡及其它用户改动。

范围外：外链抓取、分片/断点续传、并行上传、伪百分比、上传取消、材料删除/替换/重命名、人工选择材料类型、病毒扫描、对象存储、审核流程和运营状态规则改造。

## 关键不变量

### Invariant I-1: 执行顺序不可交换
- Rule: 必须先执行并验证后端子计划，再执行前端子计划。前端开始前，后端迁移、上传接口、列表来源和错误响应必须已由机器验证通过。两个子计划不得并行修改或合并验证。
- Applies to: `manual-expert-material-upload-backend.md`、`manual-expert-material-upload-frontend.md` 的全部任务。
- Violation consequence: 前端依赖未冻结的 multipart/JSON 契约，产生返工或上线后 404/字段错配。
- 来源: K-master-plan-shared-file-sequential-gates

### Invariant I-2: 跨计划 HTTP 契约冻结
- Rule: 后端公开契约固定为 `POST /api/expert-contacts/{contactId}/materials/uploads`，请求为单个 multipart 字段 `file`，成功 HTTP 201；前端每个文件发一次请求且显式 `headers:{}`。错误固定：匿名 401、缺文件/非法请求 400、专家不存在 404、超限 413 + `code=PAYLOAD_TOO_LARGE`。任何字段、路径或状态码变化必须先同时修订本主计划和两份子计划，不得执行中临时适配。
- Applies to: 后端 controller/service/flow test，前端 request queue/FakeServer test。
- Violation consequence: 两端各自测试通过但联调失败。
- 来源: K-global-multipart-parser-ceiling

### Invariant I-3: 跨计划列表契约冻结
- Rule: 成功上传后，GET `/api/expert-contacts/{contactId}/materials` 返回的新项必须满足：`storageState=STORED`、`documentStatus=PENDING_REVIEW`、`canFetch=false`、真实 `actualSize`，source 为 `type=MANUAL_UPLOAD`、`id=contactId`、`subject=手动上传`、`uploadedBy=会话用户名`、`receivedAt=上传时间`、`accountCode=null`。前端只从该 GET 刷新共享 store，不直接拼材料行。
- Applies to: 后端材料 SQL/DTO/source resolver，前端 fetchPage/source filter/source label。
- Violation consequence: 上传成功但列表显示来源待核对、仍要求获取，或多 host 产生重复/不一致行。
- 来源: K-expert-document-ownership-chain

### Invariant I-4: 容量单位全链路唯一
- Rule: 业务上限唯一实值为 `104857600` 字节；后端流式计数是最终裁决，前端 `file.size` 仅预检。UI 可显示“100 MB”，测试必须使用精确字节。容器 parser 为 100MB/101MB；旧 outbound service 仍用 10 MiB 常量二次拒绝。
- Applies to: 后端配置/服务/测试，前端常量/队列/测试，联合人工验收。
- Violation consequence: 边界一端接受、一端拒绝，或误放宽旧发信附件。
- 来源: K-global-multipart-parser-ceiling

### Invariant I-5: 数据所有权与邮件隔离
- Rule: 手动材料必须是第三类 owner：`manual_upload_id → manual_expert_material_upload.expert_contact_id`；`mail_record_id/inbound_processing_id` 均为空，不创建 transfer。下载仍同时校验 expert_document 与 manual owner。手动上传只能增加 expert_document 材料计数，不能成为邮件消息附件或推进 MATERIALS_RECEIVED。
- Applies to: V130、新上传服务、材料 resolver、邮箱/状态回归测试。
- Violation consequence: 串专家、污染邮箱时间线或错误推进运营状态。
- 来源: K-mail-attachment-write-paths, K-expert-document-ownership-chain

### Invariant I-6: 子计划范围是硬边界
- Rule: 后端子计划最多修改其清单 10 个文件，前端子计划最多修改其清单 6 个文件。执行中发现必须增加文件、字段、状态或子系统时立即停止，先用 create-p 修订相应子计划与本主计划；不得用“顺手修复”扩张范围。`mail_attachment` 只新增 `manual_upload_id` 一个共享字段。
- Applies to: 所有执行、repair、verify 回合。
- Violation consequence: 超过 create-p 可验证范围，修复轮次失控并覆盖用户现有改动。
- 来源: K-master-plan-shared-file-sequential-gates

### Invariant I-7: 发布与回滚顺序固定
- Rule: 后端是向后兼容的第一阶段发布，旧前端无需新接口也能继续工作；前端只能在后端验证通过后发布。前端可独立回滚到旧静态资源；V130 为加法迁移，不在生产做破坏性 down migration。若后端发布失败，禁止发布前端；若前端失败，保留后端并回滚前端资源。
- Applies to: 发布、故障处理、人工验收。
- Violation consequence: UI 暴露不可用入口，或用破坏性数据库回退扩大事故。
- 来源: K-master-plan-shared-file-sequential-gates

### Invariant I-8: 验证门禁不得互相替代
- Rule: 每份子计划先完成自己的定向测试、全量回归和独立 verify；只有两份均 PASS 后才跑主计划联合验证。机器验证不替代人工 A-n；人工验收文件只能在开始验收时从本计划导出。
- Applies to: child verify、combined verify、人工验收。
- Violation consequence: 单元测试通过被误报为整功能交付，跨计划断点未被验证。
- 来源: K-master-plan-shared-file-sequential-gates

## 现状审计

### 子计划与范围
- Schema/mapping:
  - 后端子计划：`manual-expert-material-upload-backend.md`，10 个文件，覆盖 V130、三表写入、磁盘、HTTP、统一材料读模型。
  - 前端子计划：`manual-expert-material-upload-frontend.md`，6 个文件，覆盖材料组件、逐字 CSS、缓存激活和 JS 测试。
- Write paths:
  1. 后端子计划新增唯一业务写入口 `ManualExpertMaterialUploadService.upload`，写 manual source、mail_attachment、expert_document 与 manual 文件目录。
  2. 前端子计划只 POST 上传接口；材料 store 仍由 GET 写入。
- Read paths:
  1. 后端统一材料 GET、下载/预览、文本提取/AI 消费新数据。
  2. 前端 inline/drawer/selectionOnly 共享 GET 数据；上传入口只在前两种模式。
- Interaction points: POST 201 → GET 新 item → shared store → 行展示/下载/AI；manual owner → owner resolver；全局 multipart ceiling → 旧 outbound 10 MiB 服务；expert_document 新行 → 材料计数但不进入邮箱/状态事件。

### 跨计划 API 契约
- Schema/mapping: 当前代码只有 GET `/materials`、POST `/transfers`、POST `/reconcile`（`ExpertMaterialController.kt:26-74`）；`app.js:1541-1556` 的 `api` 支持 `headers:{}` 覆盖默认 JSON header；`mailbox-chat.js:3640-3683` 已有串行 FormData 证据。
- Write paths: 后端新增 `/uploads`；前端只调用该路径，不新增 app.js 网络封装。
- Read paths: 前端 POST 后仍调用现有 GET；201 响应仅用于判定单项成功，不作为列表权威数据。
- Interaction points: URL、part name、状态码、100 MiB、source DTO 任一点漂移都要求双计划同步修订。

### 共享数据与文件存储
- Schema/mapping: `mail_attachment` 当前是 mail record/inbound processing 二选一 owner；`expert_document` 是专家材料归属；本地读取统一经 storagePath realpath 校验。后端子计划只新增 manual owner 与来源表。（来源: K-mail-attachment-write-paths, K-expert-document-ownership-chain）
- Write paths: 既有三条 attachment INSERT、transfer worker UPDATE；新手动上传为第四条 INSERT 写路径。详细全量清单以 backend 子计划 `## 现状审计` 为准。
- Read paths: 材料统一服务、旧文档浏览、下载/预览、文本提取/AI、邮箱、自动回复、运营状态、专家详情。详细字段依赖以 backend 子计划为准。
- Interaction points: 新 owner 必须被材料读链识别，又必须被邮件事件读链忽略。

### 前端 store、样式与缓存
- Schema/mapping: 同 contactId 单 store；`expert-materials.css` 有既有逐字锁；新增 CSS 只能按 frontend 子计划 S-1/S-2 逐字追加到 styles.css；S-3 只改既有 DOM 文案。缓存当前有 11 个版本化资源且工作树存在并行 WIP 分裂。（来源: K-frontend-cache-key-triad）
- Write paths: 上传 dialog 只写瞬时 queue；GET applyPage 写共享 store；index.html 是缓存键唯一注册点。
- Read paths: 三种材料 view、AI bridge、浏览器静态缓存和多份 Node 契约测试。
- Interaction points: 上传完成刷新必须保留 selection 与筛选；缓存激活必须保留 SharePoint WIP 并统一全部 11 个键。

## 实现方案

### 阶段 0：冻结主契约（I-1～I-8）

1. 执行者先读取本主计划和两份子计划，记录三个文件的当前内容摘要；不得只读主计划后自行补实现细节。
2. 逐项核对 I-2/I-3/I-4 的 endpoint、multipart 字段、状态码、DTO、字节上限在两份子计划完全一致。发现冲突即停止，用 create-p 修订计划，不进入代码阶段。
3. 记录执行前 `git status --short`，并对两份子计划 `## 变更文件清单` 列出的 16 条源码/测试路径逐一保存 diff 基线；用户已有改动不是清理对象。

### 阶段 1：执行并验证后端子计划（I-1～I-7）

1. 唯一执行输入：`docs/plans/2026-09-20/manual-expert-material-upload-backend.md`。
2. 只修改其 `## 变更文件清单` 中 10 个文件；按迁移/实体 → 原子上传 → HTTP/读模型顺序实施。
3. 跑该计划全部定向、迁移和全量门禁；使用独立 verify 测 I-1～I-8。结果不是 PASS 时不得进入阶段 2。
4. 后端 PASS 后冻结可供前端使用的契约证据：一个成功 201 响应样例、一个 413 响应样例、一个 GET manual item 样例；样例不得含 storagePath。

### 阶段 2：执行并验证前端子计划（I-1～I-4、I-6）

1. 前置条件：阶段 1 PASS，且冻结样例与 frontend 计划一致。
2. 唯一执行输入：`docs/plans/2026-09-20/manual-expert-material-upload-frontend.md`。
3. 只修改其 6 个清单文件；严格复制 S-1/S-2 CSS，按 S-3 修改现有 DOM；保留当前 SharePoint 文件卡改动。
4. 跑该计划全部 Node、缓存及 Maven 回归；使用独立 verify 测 I-1～I-7、S-1～S-3。结果不是 PASS 时不得进入阶段 3。

### 阶段 3：联合验证与发布准备（I-2～I-8）

1. 对最终 diff 做集合校验：业务文件必须属于两个子计划清单的并集；任何额外文件都视为范围突破。计划/knowledge/验证记录不计业务文件，但必须单列。
2. 使用真实后端与真实静态页面完成：多文件串行上传、100 MiB 边界、POST→GET→下载/预览/AI、manual 来源筛选、邮件/状态/旧 outbound 回归。
3. 跑两份子计划的全量门禁并执行 `git diff --check`。不得用某一子计划的 PASS 替代联合门禁。
4. 先发布后端并做小文件/413 smoke；通过后发布前端。开始人工验收时，才从本计划 `## 人工验收清单` 导出 `00-manual-expert-material-upload-main-acceptance.md`。

## 变更文件清单

本主计划是编排层，不直接授权修改源码；受控计划文件只有以下 2 个。源码清单分别由子计划管理，避免把 16 个文件突破性合并为单计划。

| # | 受控文件 | 作用 |
|---|---|---|
| 1 | `docs/plans/2026-09-20/manual-expert-material-upload-backend.md` | 后端/存储实施与独立验证权威清单 |
| 2 | `docs/plans/2026-09-20/manual-expert-material-upload-frontend.md` | 前端/UI实施与独立验证权威清单 |

主计划文件数：2；实现子系统由两个子计划隔离，各自不超过 2 个。共享 store 新字段总数：`mail_attachment.manual_upload_id` 1 个。

## 验收标准

- I-1：执行记录显示 backend execute→backend verify PASS→frontend execute→frontend verify PASS，时间顺序严格递增，无并行实施。
- I-2：真实 MockMvc/联调测试逐字断言 endpoint、`file` part、201/400/401/404/413 与 `PAYLOAD_TOO_LARGE`；前端请求记录断言 `headers={}`。
- I-3：联合测试从 POST 成功后重新 GET，逐字段断言 STORED/PENDING_REVIEW/canFetch=false/MANUAL_UPLOAD/contactId/uploadedBy/receivedAt；前端无直接插行代码。
- I-4：后端与前端测试共同断言 104857600 接受、104857601 拒绝；旧 `OutboundAttachmentServiceTest` 10 MiB+1 仍拒绝。
- I-5：迁移/flow/reconcile/mailbox 测试证明 manual 三选一 owner、跨专家拒绝、材料计数 +1、邮箱附件数与 operatorStatus 不变。
- I-6：最终业务 diff 路径集合严格等于/包含于两个子计划文件清单并集；`mail_attachment` 只有一个新增字段；无额外状态/表/接口。
- I-7：发布清单明确后端先、前端后；回滚说明只回滚前端静态资源，不执行 V130 破坏性 down migration。
- I-8：两个独立 verify 均 PASS，随后联合 `mvn test`、`node --test src/test/js/*.test.js`、migration IT、`git diff --check` 全部退出码 0；人工清单另行记录，不以机器 PASS 代替。
- 权威联合命令：
  ```bash
  JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ManualExpertMaterialUploadFlowTest,ExpertMaterialServiceTest,ExpertDocumentBrowseServiceTest,DocumentTextExtractorTest,OperatorStatusReconcileServiceTest,OutboundAttachmentServiceTest test
  JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test
  node --check src/main/resources/static/expert-materials.js
  node --test src/test/js/expertMaterialsShared.test.js src/test/js/expertMaterialsStyle.test.js src/test/js/sharepointFileCardDisplay.test.js
  node --test src/test/js/*.test.js
  JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
  git diff --check
  ```

## 人工验收清单

### A-1: 完整上传闭环
- 前置条件: 后端与前端两个 verify 均 PASS；测试专家已有一份邮件附件；登录运营账号；准备 1 MiB PDF。
- 操作步骤: 1. 在材料管理点击“手动上传”；2. 选择 PDF 并开始；3. 等待成功；4. 刷新页面；5. 下载、预览并加入 AI 分析。
- 预期结果: 仅一次 `/uploads` 返回 201；列表新增 1 项，显示“已存服务器”“待审核”“手动上传 · 当前用户名 · 时间”，无“获取到服务器”；下载字节一致、预览 200、AI 可读取文本。
- 覆盖: I-2、I-3、I-5、需求可观察结果

### A-2: 多文件串行与部分失败
- 前置条件: 准备 3 个小文件；令第 2 个请求首次返回 500，随后恢复。
- 操作步骤: 1. 一次选择 3 个文件；2. 开始上传并观察 Network；3. 全部落定后点击“重试失败项”。
- 预期结果: 同时最多 1 个请求；第 1/3 个成功且进入列表，第 2 个失败不阻塞第 3 个；重试只发送第 2 个；最终 3 项均 STORED。
- 覆盖: I-1、I-2、I-3

### A-3: 精确容量边界与旧上传回归
- 前置条件: 准备 104857600、104857601 字节文件和一个 11 MiB 人工回复附件。
- 操作步骤: 1. 从材料管理上传前两个；2. 从人工回复入口上传 11 MiB 文件。
- 预期结果: 104857600 成功；104857601 在前端不发请求，直接显示“超过 100 MB，未上传”；绕过前端直调时后端仍为 413；人工回复 11 MiB 仍为 413。
- 覆盖: I-4、不得改变旧 outbound 限制

### A-4: 邮件附件链路隔离
- 前置条件: 专家有一份 METADATA_ONLY 邮件附件，operatorStatus 不是 MATERIALS_RECEIVED；记录邮箱附件数和材料数。
- 操作步骤: 1. 手动上传一个文件；2. 刷新邮箱并执行 status reconcile；3. 对原邮件附件点击“获取到服务器”。
- 预期结果: 材料数 +1；邮箱消息附件数不变；operatorStatus 不因手动上传变为 MATERIALS_RECEIVED；原邮件附件仍经历排队中→获取中→已存服务器。
- 覆盖: I-5、不得改变邮件/状态链路

### A-5: 跨专家安全
- 前置条件: 专家 A、B；向 A 上传一个 PDF并取得 attachmentId。
- 操作步骤: 1. 用 A 的下载/预览 URL访问；2. 把 URL 中 contactId 改为 B；3. 检查响应正文。
- 预期结果: A 为 200；B 被拒绝且无文件字节；响应不含 storagePath、manual 目录或临时文件名。
- 覆盖: I-5、不得改变下载安全

### A-6: 三种材料视图与样式
- 前置条件: 桌面 1440px、移动 390px；可打开 inline、drawer、selectionOnly。
- 操作步骤: 1. 依次打开三种视图；2. 在前两种打开上传 dialog；3. 用键盘 Tab；4. 切换移动宽度。
- 预期结果: inline/drawer 有“手动上传”，selectionOnly 无；dialog 白底 `#fff`、桌面宽≤640px、移动宽=视口减16px、圆角14px、焦点轮廓2px `#3b82f6`；上传中 Esc 不关闭且无伪百分比。
- 覆盖: I-3、前端子计划 S-1～S-3

### A-7: 缓存与既有 WIP 回归
- 前置条件: 部署前后各访问一次；准备 SharePoint 文件卡邮件。
- 操作步骤: 1. 普通刷新新版本；2. 检查 11 个静态资源 query；3. 打开文件卡邮件。
- 预期结果: 11 个资源全部为 `?v=20260920-manual-material-upload`；无需强刷即看到新入口；SharePoint 文件卡提取、链接与正文断词修复保持不变。
- 覆盖: I-6、I-7、不得覆盖用户现有改动

### A-8: 发布门禁与回滚演练
- 前置条件: 预发环境可分别部署后端/前端。
- 操作步骤: 1. 仅部署后端并用旧前端操作材料；2. smoke 上传接口；3. 部署前端；4. 模拟前端故障并回滚静态资源。
- 预期结果: 仅后端时旧页面无回归且新 API 可用；前端发布后入口可用；回滚前端后旧页面恢复，后端和 V130 数据保留，不执行数据库 down migration。
- 覆盖: I-1、I-7、I-8
