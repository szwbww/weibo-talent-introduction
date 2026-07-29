# 可信回复工作台无依据回答 V1－03：ES 索引与训练只读列表

日期：2026-07-29
状态：待批准、未执行
前置：[01 后端逐项语义与版本合同](./trust-reply-unsupported-answer-v1-01-backend-item-semantics.md) 已通过
后续：[04 训练评估合格后入索引](./trust-reply-unsupported-answer-v1-04-training-qualified-archive.md)、[05 正式发送成功后入索引](./trust-reply-unsupported-answer-v1-05-live-send-qualified-archive.md)

## 需求描述

建立独立 ES V1 索引与访问网关，并在“AI 训练”增加“无依据回答索引”只读 Tab。此阶段完成索引 bootstrap、幂等 create 能力、分页读取 API 和前端列表，但不接训练评估/正式发送写入触发器，因此新环境中的列表初始为空是预期行为。

V1 明确不做复用、推荐、相似搜索、向量、编辑、删除或 QA 晋升。
未来若批准复用，使用新版本索引和受控 reindex；不在 V1 mapping 上原地把 `index:false` 改为可检索。

必须不改变：

1. 三层专家索引名、mapping、bootstrap、writer/search/promotion/discovery 读写行为。
2. AI 训练现有 QA、对话、提示词、历史模拟四个 Tab 的加载和操作。
3. ES 凭据只在服务端；浏览器不能提交 index 名或原始 DSL。
4. 既有翻译 API 与其他列表/翻译入口。

明确不纳入：生产资格写触发器、历史回填、复用/推荐/搜索、编辑/删除、outbox/retry、CSS 改造。

## 关键不变量

### Invariant I-1: 新索引与三层专家索引完全隔离

- Rule: 只新增一个带默认值的 index-name 配置和独立 service/mapping；不改变 ExpertIndexLevel 或现有索引。
- Applies to: configuration binding、bootstrap、create/list URL、既有 ES services 回归。
- Violation consequence: 专家搜索/晋升数据被错误写入或既有构造器启动失败。
- 来源: K-es-dynamic-false。

- 新配置 `unsupportedAnswerIndexName` 默认 `trust_reply_unsupported_answer_v1`，环境变量 `ES_UNSUPPORTED_ANSWER_INDEX_NAME`。
- 不修改 `orcid_info`、`orcid_info_candidate`、`orcid_info_application` mapping 或 `ExpertIndexLevel`。
- `ElasticsearchProperties` 新字段带默认值，既有五处测试构造器无需机械修改。

### Invariant I-2: mapping 严格且内容字段不可检索

- Rule: dynamic strict、字段集精确；三个正文类字段只存 `_source` 且 `index:false`，禁止隐式字段。
- Applies to: mapping JSON、document serialization、list `_source` whitelist。
- Violation consequence: 意外字段/敏感内容被索引或 V1 偷渡成搜索/复用功能。
- 来源: original；K-es-dynamic-false。

- mapping 固定 `dynamic:"strict"`；所有字段显式声明。
- `requestText/operatorInstruction/answerText` 保存在 `_source`，但设置 `index:false`；V1 只能列表读取，不能 keyword/full-text/similarity 搜索。
- 不保存整封来信、邮件 history、QA answerBody、claims、译文、prompt、LLM 原始 response 或异常堆栈。（来源：K-es-dynamic-false 的显式字段原则；本索引有意采用更严格的 `strict`）

### Invariant I-3: bootstrap 只把 404 当作不存在

- Rule: 2xx 不创建；明确 404 才 PUT；其他 HTTP/网络/解析错误不创建；专用 client connect timeout=2 秒、read timeout=5 秒。
- Applies to: `@PostConstruct` bootstrap 与专用 HTTP client。
- Violation consequence: 凭据错误或 ES 故障被误判为缺索引并发出破坏性请求。
- 来源: K-es-bootstrap-create-only-on-404。

- 启动时 `HEAD /{index}`：2xx 表示存在；404 才 `PUT /{index}` 加载 mapping。
- 401/403/429/5xx、网络超时、JSON 读取失败均只记录清晰日志，不误判为“不存在”，不得继续 PUT。
- 不复制 `ExpertIndexService.bootstrapMappings:36-64` 当前“捕获任意异常后创建”的宽泛行为。（来源：新增知识 K-es-bootstrap-create-only-on-404）
- gateway 使用独立 connect 2 秒/read 5 秒超时；ES 不可用不能无限阻塞应用启动或请求线程。

### Invariant I-4: 内部 create 只接受完整 canonical 文档

- Rule: document 本地验证、hash 一致、handling/kind 固定、deterministic ID、create-only；409 仅表示幂等成功。
- Applies to: service document factory、create transport、未来训练/live 调用方。
- Violation consequence: 不合格/被篡改文档入库或重复记录。
- 来源: original。

- `UnsupportedAnswerIndexService.create(document)` 是内部服务方法，不暴露浏览器 POST。
- 调用前验证 schemaVersion、source/status/qualification 枚举、正 ID、非空 request/version/说明/回答、handling=`ANSWER_FROM_OPERATOR_INPUT`、generationKind=`AI_GENERATED`、两个 hash 与正文一致。
- `_id=sha256(sourceType|sourceId|requestKey|versionId)`；`PUT /{index}/_create/{id}` 或等价 `op_type=create`。
- 201 为 CREATED，409 为 ALREADY_EXISTS（幂等成功）；其他结果为 FAILED 并保留安全错误分类，不返回 ES 凭据/body 给前端。

### Invariant I-5: 列表 API 只读、固定查询且有界

- Rule: 只允许 page/size/sourceMode，固定 source/filter/sort/total；非法参数 400，ES 不可用 503。
- Applies to: controller、service query builder、response mapper。
- Violation consequence: 任意 DSL 注入、全量扫描、内存放大或错误泄密。
- 来源: original。

- `GET /api/ai-training/unsupported-answers?page=0&size=20&sourceMode=TRAINING|LIVE`。
- page 必须 `>=0`；size 默认 20，限制 1～100；非法 sourceMode 返回 400。
- 查询固定 `track_total_hits=true`，可选 term filter `sourceMode`，排序 `createdAt desc, _id asc`。
- `_source` 只取公开文档字段；响应为 `{items,total,page,size}`。
- ES 不可用返回 503 稳定 code `UNSUPPORTED_ANSWER_INDEX_UNAVAILABLE`；空索引返回正常空页。

### Invariant I-6: 可选 ES Tab 懒加载且失败隔离

- Rule: 只在首次激活/刷新/筛选/翻页加载；不得加入主 load Promise.all；错误只属于本 panel。
- Applies to: state、switchAiTrainingTab、loadAiTraining、event handlers、render status。
- Violation consequence: ES 故障导致整个 AI 训练页面不可用。
- 来源: K-ai-subtab-whitelist-mapping。

- `loadAiTraining()` 的既有 Promise.all 不加入 ES 列表请求。
- 只有用户第一次切到 `unsupportedAnswers` Tab 或点击刷新/翻页/筛选时加载。
- ES 列表失败只在该 panel 内显示错误和重试；QA、对话、提示词、模拟回复 Tab 继续可用。（来源：K-ai-subtab-whitelist-mapping）

### Invariant I-7: V1 列表无任何复用或修改动作

- Rule: 只读六列和局部翻译；DOM/API 不提供采用、复用、搜索、编辑、删除、晋升。
- Applies to: index.html、render row、event delegation、controller routes。
- Violation consequence: 超出 V1 授权或把未经治理的数据投入生产。
- 来源: original。

- 表格只显示：状态/来源、原问题、操作员描述、AI 回答、模型、创建时间。
- 原问题与 AI 回答可手动调用既有 `/api/translate`；译文不写回 ES。
- DOM 和 API 都不得出现“采用、复用、推荐、搜索相似、编辑、删除、晋升 QA”动作。

## 样式契约

### S-1: 复用现有 AI Training Tab、panel、table、pager

- 复用: `.ai-training-tabs/.ai-tab/.ai-tab-content`（`styles.css:6186-6222`）、`.panel/.panel-head/.toolbar-inline`（733-755、6726-6748）、table（765-826）、badge（829-872）、pager/button.small（1035-1049、2244-2249）、translation（1664-1694）。
- 新增: 无新 CSS class、无 CSS 规则、无 `styles.css` 修改。
- DOM 结构: 必须使用 S-2 骨架；新增仅为唯一 ID 和 data-tab 值。
- 禁止项: inline style；新 class；修改现有 class；为列表另造卡片/分页/按钮视觉。

- Tab 使用 `.ai-training-tabs > .ai-tab` 与 `.ai-tab-content`（`styles.css:6186-6222`）。
- Panel 使用 `.panel.ai-training-panel`、`.panel-head`、`.toolbar-inline`（733～755、6726～6748 行）。
- 表格使用 `.table-wrap > table`、现有 `th/td`（765～826 行）；状态使用 `.badge.ok/.warn/.primary`（829～872 行）。
- 分页使用 `.list-pager`、`.button.small`、`.list-pager-info`（1035～1049、2244～2249 行）。
- 长文本单元格使用 `.muted-cell` 和 `.pre`；翻译使用 `.translatable-body-block/.btn-translate/.translation-text`。
- 本计划不修改 `styles.css`，不引入新色值、阴影、圆角或 inline style。

### S-2: 目标 Tab DOM 骨架

- 复用: 按钮、panel、toolbar、feedback、table、pager 均使用 S-1 class。
- 新增: 只新增 `data-tab="unsupportedAnswers"` 与 `aiTabUnsupportedAnswers/aiTrainingUnsupportedAnswer*` ID；无 CSS。
- DOM 结构: 必须逐层符合下列骨架，tbody 六列、pager 三个子节点。
- 禁止项: 把 panel 放到 `#view-ai-training` 外；省略 whitelist mapping；在 `<th>` 写 inline 对齐；新增操作列。

在 `index.html:776-893` 的现有四个 Tab 后加入：

```html
<button type="button" class="ai-tab" data-tab="unsupportedAnswers">无依据回答索引</button>

<div class="ai-tab-content" id="aiTabUnsupportedAnswers">
  <section class="panel ai-training-panel">
    <div class="panel-head">
      <h2>无依据回答索引</h2>
      <div class="toolbar-inline">
        <select id="aiTrainingUnsupportedAnswerSourceFilter">…</select>
        <button class="button" id="reloadAiTrainingUnsupportedAnswersBtn">刷新</button>
      </div>
    </div>
    <div class="ai-reply-feedback" id="aiTrainingUnsupportedAnswerStatus" role="status" aria-live="polite"></div>
    <div class="table-wrap"><table>…<tbody id="aiTrainingUnsupportedAnswerTable"></tbody></table></div>
    <div id="aiTrainingUnsupportedAnswerPager" class="list-pager" hidden>…</div>
  </section>
</div>
```

- 空态为单个 `<tr><td colspan="6" class="muted-cell">暂无已确认的无依据回答</td></tr>`。
- 错误态不删除筛选/刷新；status 使用现有 `.ai-reply-error`，不弹全局 alert。
- TRAINING 显示 `CANDIDATE/训练`，LIVE 显示 `ACTIVE/正式发送`；不得把内部 enum 裸露为唯一中文信息。

### S-3: 内容、空态、错误态与翻译

- 复用: `.muted-cell/.pre/.ai-reply-feedback/.ai-reply-error/.translatable-body-block/.btn-translate/.translation-text` 现有规则。
- 新增: 无 class/CSS；状态和内容只用转义文本与既有 class。
- DOM 结构: 问题和回答各自一个 translation block；操作员描述只读 `.pre`；空态固定一个 colspan=6 row。
- 禁止项: 未 escape 的 innerHTML；页面打开即批量翻译；全局 alert；操作/复用按钮。

- 原问题、操作员描述、AI 回答都先 `escapeHtml`。
- 问题与回答使用既有可翻译块；默认不批量自动翻译，避免打开列表时产生 40 次外部请求。
- 每个按钮只翻译所在 cell；分页/筛选后旧节点和旧翻译结果销毁，不串页。

## 现状审计

### 新 `trust_reply_unsupported_answer_v1` store

- Schema/mapping: 当前不存在 mapping/resource/config/property；这是新物理索引，不做 migration 或 alias。
- Current write paths: 0；仓库搜索无该索引名、无 unsupported-answer ES DTO/transport。
- Planned write paths:
  1. 本阶段 `UnsupportedAnswerIndexService.create` — canonical create-only gateway；03 阶段无生产 caller。
  2. 04 阶段 `AiTrainingEvaluationService.save` — 评估合格后调用同一 gateway。
  3. 05 阶段 `PendingMailOperationService.sendManualRichReply` — 明确发送成功后调用同一 gateway。
- Current read paths: 0。
- Planned read paths: `UnsupportedAnswerIndexService.list` → `UnsupportedAnswerIndexController.list` → AI Training lazy Tab。
- Interaction points: mapping 必须覆盖两个未来 writer 的全部字段；两个 writer 的文档必须被同一个 list mapper/Tab 完整读取；翻译读取 `_source` 但不写回。

### 既有三层专家 ES store

- Schema/mapping: 三个 mapping 为显式字段、`dynamic:false`；配置由 `raw/candidate/applicationIndexName` 提供。
- Write paths: `ExpertIndexWriterService` 的 update、update_by_query、bulk、put/delete/promote/demote；`ExpertDiscoveryService` 的 candidate/raw put/update；`ExpertIndexService.bootstrapMappings` 的 index/mapping PUT。
- Read paths: `ExpertSearchService` 全部 search/scroll/count/list；`ExpertDiscoveryService` 的 HEAD/search/get；`ExpertIndexWriterService` 写前 search；mapping health check。
- Interaction points: 新 property 追加默认值后，上述调用仍只通过 `ExpertIndexService.indexName(ExpertIndexLevel)` 取得旧三名；新 gateway 不复用 ExpertIndexLevel/Writer/Search。

### ES 配置与 bootstrap

- `ElasticsearchProperties.kt:8-15` 当前只有 base URL、凭据和三个专家索引名。
- `application.yml:29-35` 对应三个 index env 配置，没有无依据回答索引。
- `ExpertIndexService.kt:36-64` 当前应用索引 bootstrap 在 GET 任意异常时 PUT；新服务不能复用该错误判定。
- 当前 mapping（如 `es/orcid_info_application.json`）使用 1 shard、1 replica 和显式 properties；新 mapping 沿用部署参数，但使用 `dynamic:"strict"`。
- 仓库目前没有无依据回答的 ES read/write path；`reply_snippet` 只适合静态问候/结尾/ACK，不适合作为问答回答存储。

### AI 训练 Tab

- `index.html:776-780` 当前四个 Tab；`aiTabQa/Dialogues/Prompts/Simulate` panel 位于 783～893 行。
- `app.js:2906-2919` 的 `switchAiTrainingTab` 是显式白名单映射；只加 HTML 不加映射会出现按钮 active 但 panel 不显示。
- `app.js:3373-3380` 的 `loadAiTraining` 并发加载现有所有资源；把可选 ES Tab 加入会让 ES 故障拖垮整个训练页。
- 现有 table/pager/badge/translation helper 可复用，无需新增 CSS。

### 前端样式盘点与改动前基线

- 可复用 class/token: S-1 列出的 class；主色 `#2563eb`、文字 `#1e293b/#94a3b8`、状态色 `#059669/#d97706/#e11d48`、圆角 `7/10/14px`、table cell `8px 14px`、button `12px/32px`。
- DOM 注册约定: `index.html:776-780` 按钮与 `783-893` panel 为同级；`switchAiTrainingTab` 显式匹配 data-tab 与 panel ID。
- 改动前按钮片段逐字为：

```html
<div class="ai-training-tabs">
    <button type="button" class="ai-tab" data-tab="qa">QA 知识库</button>
    <button type="button" class="ai-tab" data-tab="dialogues">对话范例</button>
    <button type="button" class="ai-tab" data-tab="prompts">AI 提示词与约束</button>
    <button type="button" class="ai-tab active" data-tab="simulate">历史邮件模拟回复</button>
</div>
```

- 改动前 panel 结构为 `.ai-tab-content > section.panel.ai-training-panel > .panel-head + .table-wrap + .list-pager`；本计划复制此层级，不修改 class 规则。

### 安全与认证

- 浏览器统一通过既有 `api()` 调用，沿用会话认证与 401 处理。
- ES Basic Auth 只存在服务端；controller 不接受 index name、raw query DSL 或任意 `_source` 参数。

## 实现方案

### T0：执行前研究检查点

- Governs：I-1～I-7、S-1～S-3。
- Exact files: 本计划清单 1～10。
- 重新 `rg` 所有 `ElasticsearchProperties(` 构造、三个 index-name reader、ES PUT/POST/DELETE、AI Training tab mapping/load/event、asset-version 断言。
- 对目标 ES 集群用只读 HEAD（或 mock contract）确认版本支持 `dynamic:strict`、`text index:false`、`_create` 和 `track_total_hits`；若版本不支持，停止并修订 mapping/API，不静默降级。
- 若发现现有同名索引、额外 writer/reader、需要第 11 个文件或新 CSS，停止并拆计划。

### T1：先写 mapping/gateway 失败测试

- Governs：I-1～I-5。
- Exact files: `src/test/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexApiTest.kt`。
- 新 `UnsupportedAnswerIndexApiTest.kt` 同时覆盖 service 与 controller：
  - mapping 为 strict，字段集精确，三个正文类字段 `index:false`，无 vector/embedding/translations。
  - HEAD 2xx 不 PUT；HEAD 404 才 PUT；401/500/网络异常不 PUT。
  - create 校验、确定性 ID、201、409 幂等、其他失败。
  - search body 的 filter/sort/track_total_hits/source whitelist、分页边界和 ES hit 映射。
  - controller 400/503/空页/正常页。

### T2：增加配置和 mapping

- Governs：I-1～I-3。
- Exact files: `src/main/kotlin/com/weibo/talentintroduction/config/ElasticsearchProperties.kt`、`src/main/resources/application.yml`、`src/main/resources/es/trust_reply_unsupported_answer_v1.json`。
- `ElasticsearchProperties` 追加：

```kotlin
val unsupportedAnswerIndexName: String = "trust_reply_unsupported_answer_v1"
```

- application.yml 增加 env 占位。
- 新建 `src/main/resources/es/trust_reply_unsupported_answer_v1.json`：1 shard、1 replica、dynamic strict、仅总计划字段。

### T3：实现专用 ES gateway

- Governs：I-2～I-5。
- Exact files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexService.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexApiTest.kt`。
- 新 `UnsupportedAnswerIndexService` 定义：document DTO、write result、page DTO、source/status/qualification enum、bootstrap/create/list。
- 通过 `RestTemplateBuilder` 构造专用 connect 2 秒/read 5 秒 client，不共享无限超时的通用 RestTemplate；沿用 Basic Auth header 构造，日志不得打印密码或完整正文。
- bootstrap 只将 `HttpClientErrorException.NotFound` 识别为创建条件。
- create 前做本地 schema/hash/enum/长度校验；使用 `_create`。
- list 构造固定 DSL，拒绝浏览器自定义 query；解析 malformed hit 时记录并跳过该 hit，同时 total 保持 ES total，并在日志标识文档 ID。

### T4：新增只读 controller

- Governs：I-5、I-7。
- Exact files: `src/main/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexController.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexApiTest.kt`。
- 新 controller 只提供 GET list；不提供 POST/PUT/PATCH/DELETE/reuse/search endpoint。
- 将内部 ES 异常映射 503 稳定 code；不返回 ES URL、index name、DSL、credential 或 response body。

### T5：先写前端 Tab 失败测试

- Governs：I-6、I-7、S-1～S-3。
- Exact files: `src/test/js/aiTrainingUnsupportedAnswers.test.js`、`src/test/js/batchSendTaskConsoleVisualFix.test.js`。
- 新 `aiTrainingUnsupportedAnswers.test.js` 用抽取/VM 或现有 sandbox 模式覆盖：
  - whitelist 能显示新 panel；其他 panel 隐藏。
  - `loadAiTraining()` 不请求新 API；首次切 Tab 才请求一次。
  - 筛选/刷新/翻页请求参数正确；失败只渲染局部错误。
  - 文本 escape；无 action buttons；翻译按 cell 请求。
- `batchSendTaskConsoleVisualFix.test.js` 同步 asset version 断言。

### T6：实现 Tab、状态与 lazy load

- Governs：I-5～I-7、S-1～S-3。
- Exact files: `src/main/resources/static/index.html`、`src/main/resources/static/app.js`、`src/test/js/aiTrainingUnsupportedAnswers.test.js`。
- 在 `state.aiTraining` 增加独立 `unsupportedAnswers/items/total/page/size/sourceMode/loaded/loading/error/requestToken`。
- `switchAiTrainingTab` 显式映射 `unsupportedAnswers -> aiTabUnsupportedAnswers`，激活时调用 lazy loader；重复进入使用已加载数据，刷新强制请求。
- page 改变前 clamp；筛选改变后 page 归零。
- 请求 token 防止快速筛选时旧响应覆盖新结果。
- 翻译调用既有页面 helper，不写 ES、不改 item state。

### T7：静态资源与回归

- Governs：I-1～I-7、S-1～S-3。
- Exact files: `src/main/resources/static/index.html`、`src/test/js/batchSendTaskConsoleVisualFix.test.js`，以及本计划其余清单文件用于测试执行。

- 更新 `index.html` 中 styles/workbench/app 统一 cache-buster；本计划虽不改 styles/workbench 内容，保持发布资源版本一致。
- 执行：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=UnsupportedAnswerIndexApiTest test
node --check src/main/resources/static/app.js
node --test src/test/js/aiTrainingUnsupportedAnswers.test.js
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
node --test src/test/js/*.test.js
git diff --check
```

## 变更文件清单

| # | 文件 | 动作 | 目的 |
|---:|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/config/ElasticsearchProperties.kt` | 修改 | 新索引名配置 |
| 2 | `src/main/resources/application.yml` | 修改 | 新 ES index env |
| 3 | `src/main/resources/es/trust_reply_unsupported_answer_v1.json` | 新增 | strict mapping |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexService.kt` | 新增 | bootstrap、canonical create、分页 list gateway |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexController.kt` | 新增 | AI 训练只读分页 API |
| 6 | `src/main/resources/static/index.html` | 修改 | 新 Tab/panel 与 cache-buster |
| 7 | `src/main/resources/static/app.js` | 修改 | Tab whitelist、state、lazy load、表格/翻译/分页 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexApiTest.kt` | 新增 | mapping/gateway/controller 合同测试 |
| 9 | `src/test/js/aiTrainingUnsupportedAnswers.test.js` | 新增 | 新 Tab 行为测试 |
| 10 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改 | 静态资源版本合同 |

文件数：10；子系统：ES/API、AI 训练前端，共 2 个。不得顺带重构 `ExpertIndexService`。

## 验收标准

- I-1: property binding 默认/环境覆盖测试通过；既有五个构造器编译，旧 indexName/mapping/writer/search 测试无 diff。
- I-2: 解析 mapping JSON 精确断言 dynamic strict、总计划字段集、三个 `index:false`，并断言无 translation/vector/embedding/未知字段。
- I-3: mock HTTP 顺序断言 2xx=HEAD only、404=HEAD+PUT、401/403/429/500/timeout=HEAD only；client connect/read timeout 精确为 2/5 秒且日志无 credential/body。
- I-4: document validation 测试覆盖 hash/enum/blank/handling/kind；相同 canonical version 的 URL ID 相同，201/409 映射为成功。
- I-5: controller/service 测试断言 page/size/filter/source/sort/track_total_hits、source whitelist、400/503/空页和 response 字段。
- I-6: JS 网络计数断言主 load 为 0、首次切 Tab 为 1、重复切回不增加、刷新/筛选/翻页按预期增加；失败只写 panel status。
- I-7: 路由表与 DOM/source grep 不含 POST/PUT/PATCH/DELETE/reuse/recommend/similar/vector/edit/delete/promote actions；翻译只 POST `/api/translate`。
- S-1: `git diff -- styles.css` 为空；新增 class 集完全来自 S-1，无 inline style。
- S-2: DOM fixture 精确断言 Tab/button/panel/table 六列/pager 层级及 whitelist 映射。
- S-3: HTML 注入 fixture 被 escape；空态 colspan=6；错误态保留筛选/刷新；问题/回答翻译局限所在 cell。
- Integration: 后端全测与 JS 全测通过；ES 503 时四个既有 Tab 的 loader/result 不变。

## 人工验收清单

### A-1: 首次 bootstrap 创建独立索引
- 前置条件: 测试 ES 可访问且目标索引不存在；三个专家索引已有测试数据。
- 操作步骤: 1. 启动应用；2. 查看 ES indices/mapping；3. 查询三个专家索引文档数。
- 预期结果: 只新增 `trust_reply_unsupported_answer_v1`；1 shard/1 replica、dynamic strict、字段精确；三个专家索引名、mapping、文档数不变。
- 覆盖: I-1、I-2、must-not-change 1。

### A-2: 非 404 bootstrap 失败隔离
- 前置条件: 分别准备错误密码和返回 500 的 ES endpoint；记录请求方法。
- 操作步骤: 各启动一次应用并观察请求与日志。
- 预期结果: 每次只有 HEAD，没有 PUT；应用其余模块可启动；日志含 401 或 500 分类但不含密码、requestText、answerText。
- 覆盖: I-3、must-not-change 3。

### A-3: 只读列表分页与过滤
- 前置条件: 通过服务端测试工具写入 21 条合法 fixture，其中 TRAINING 12、LIVE 9；时间各不相同。
- 操作步骤: 1. 打开 AI 训练但不切新 Tab；2. 查看 Network；3. 切“无依据回答索引”；4. 选 TRAINING；5. 下一页、上一页、刷新。
- 预期结果: 第 1 步无新 API；首次切入显示 20 条且总数 21，按 createdAt 降序；TRAINING 总数 12；分页按钮/页码准确；请求只有 GET list。
- 覆盖: I-5、I-6、S-1、S-2。

### A-4: 内容安全与局部翻译
- 前置条件: 一条 fixture 的问题包含 `<img onerror=alert(1)>`，回答为英文。
- 操作步骤: 打开列表，分别点击问题和回答的“翻译为中文”。
- 预期结果: 标签以普通文本显示且无弹窗/执行；两个译文只在各自 cell 下显示；刷新 ES `_source` 后没有译文字段。
- 覆盖: I-2、I-7、S-3、must-not-change 4。

### A-5: ES 故障只影响新 Tab
- 前置条件: 页面已登录；临时让新索引 search 返回 503。
- 操作步骤: 1. 打开新 Tab；2. 再切 QA、对话、提示词、历史模拟并执行各自刷新/选择；3. 回新 Tab点击刷新。
- 预期结果: 新 Tab 显示 `UNSUPPORTED_ANSWER_INDEX_UNAVAILABLE` 对应错误和刷新按钮；其他四 Tab 数据/操作正常；主页面不白屏。
- 覆盖: I-5、I-6、S-3、must-not-change 2。

### A-6: 无复用和修改能力
- 前置条件: 新 Tab 有至少一条记录。
- 操作步骤: 检查表头、每行按钮和浏览器 Network/API route。
- 预期结果: 只有状态/来源、原问题、操作员描述、AI回答、模型、创建时间六列及翻译按钮；没有采用、复用、推荐、搜索、编辑、删除、晋升 QA；后端无对应 mutation route。
- 覆盖: I-7、需求 V1 范围。

### A-7: 目测样式
- 前置条件: 浏览器 1440px 和 390px，各打开新 Tab。
- 操作步骤: 对比现有 QA Tab 的 Tab、panel、table、badge、按钮、pager。
- 预期结果: 使用同一主色 `#2563eb`、文字色 `#1e293b/#94a3b8`、状态色、7/10/14px 圆角和 32px 按钮；无新增卡片风格、inline 对齐或窄屏遮挡。
- 覆盖: S-1～S-3。
