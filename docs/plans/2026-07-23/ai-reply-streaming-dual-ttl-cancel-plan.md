# AI 回复流式请求、双层 TTL、实时进度与手动停止开发计划

- 日期：2026-07-23
- 最近修订：2026-07-24（补充实时进度）
- 状态：待执行
- 计划名：`ai-reply-streaming-dual-ttl-cancel-plan`
- 实施范围：可信回复工作台的浏览器流、服务端生成链和 DeepSeek 流式传输
- 预计子系统：2（后端生成/传输、可信回复前端）
- 预计实现文件：10

## 需求描述

### 可观察结果

1. 可信回复工作台的“生成模型”旁新增两个独立配置：
   - **单次 TTL**：限制每一次 DeepSeek provider 调用；默认 30 秒，预设 30/60/90/180 秒，支持 10～600 秒整数自定义。
   - **总 TTL**：限制一次工作台生成链的总时间；默认自动取单次 TTL 的 10 倍，支持 300/600/900/1800 秒预设及自定义，取值必须不小于单次 TTL且不大于 7200 秒。
2. 工作台改用 POST SSE 接口；连接建立后立即收到 `ready`，生成期间收到有界`progress`且每10秒收到`heartbeat`，最终内容只通过完整、已校验的`result`返回。
3. 用户可在生成期间点击“停止生成”；服务端必须取消排队/执行中的任务、关闭 DeepSeek流并停止 heartbeat，而不是只隐藏前端 loading。
4. 单次 TTL、总 TTL、模型和 generationId 均由请求明确传递；无需修改或重启基础配置。
5. 流式生成期间实时展示可验证的阶段、provider 活动、单次/总耗时、总 TTL 使用进度和最近活动；不把字符数、事件数或耗时伪装成模型完成百分比。

### 必须保持不变

1. Flash/Pro 使用稳定业务枚举，provider model id 仍只在服务端映射。
2. Grounded 输出仍先做严格 JSON materialize，再做事实、claim、action 和 readiness 校验。
3. LLM 失败 fallback 仍为 `BLOCKED`、不可采用、不可自动发送。
4. 用户手动停止不生成 fallback、不新增草稿、不覆盖已有草稿；停止发生在审核提交边界之前时不写初始草稿审核。
5. 人工自行编写邮件的最终发送不读取历史 LLM 失败或停止状态作为审批条件。
6. AI 训练模拟和 Grounded 自动回复不接收工作台双 TTL，不改现有调用协议和调用入口。
7. 现有 JSON 接口 `/ai-reply/turn` 保留，旧调用者不需要 generationId。
8. 日志和 SSE 不输出邮件正文、Prompt、最终草稿或 `reasoning_content`。
9. 进度事件不改变草稿可信结论、审核、采用或人工发送门禁。

### 不在范围内

1. 浏览器逐 token 展示 DeepSeek 内容。
2. Pro 自动切换 Flash。
3. 把 TTL 配置持久化到数据库、配置表或 `localStorage`。
4. 修改 `LLM_TIMEOUT_MS`、`application.yml` 或现有 `LlmProperties.timeoutMs`。
5. AI 训练模拟页面增加 TTL 或停止按钮。
6. 修改 Prompt、模型 temperature、事实规则或数据库 schema。
7. 断点续传、跨 Tomcat 实例取消、服务重启后恢复 generationId。
8. 基于 token、字符数或经验速率预测“还剩多久”或“已完成百分比”。

### 官方协议依据

- DeepSeek `POST /chat/completions` 支持 `stream:true`，以 data-only SSE 返回增量，并以 `data: [DONE]` 结束：
  https://api-docs.deepseek.com/zh-cn/api/create-chat-completion/
- 思考模式分别返回 `delta.reasoning_content` 与 `delta.content`；本计划只拼接 `content`：
  https://api-docs.deepseek.com/guides/thinking_mode
- DeepSeek流式等待期间可能发送 `: keep-alive`，客户端必须显式忽略：
  https://api-docs.deepseek.com/quick_start/rate_limit
- JSON Output 仍须携带 `response_format={"type":"json_object"}`，并拒绝 `finish_reason=length` 的截断结果：
  https://api-docs.deepseek.com/zh-cn/guides/json_mode/

## 关键不变量

### Invariant I-1：双 TTL 请求契约

- Rule：
  - 请求字段固定为 `llmAttemptTimeoutSeconds`、`llmTotalTimeoutSeconds`。
  - 单次 TTL 缺省为 30；允许 10～600 秒整数。
  - 总 TTL 为 null 时解析为 `单次 TTL × 10`；显式值必须在 `[单次 TTL, 7200]` 内。
  - 非法值返回 HTTP 400，禁止静默 clamp、交换或回退基础配置。
  - 响应和 `ready` 事件回显 `appliedLlmAttemptTimeoutSeconds` 与 `appliedLlmTotalTimeoutSeconds`。
- Applies to：JSON入口、SSE入口、Controller校验、前端请求构造。
- Violation consequence：页面显示值与真实 provider 时限不一致，或总 TTL 小于单次调用。
- 来源：original。

### Invariant I-2：单次 TTL 只约束一次 provider 调用

- Rule：
  - 每次 DeepSeek调用拥有独立 attempt deadline。
  - 第 N 次调用允许时间为 `min(单次 TTL, 当前总 TTL 剩余时间)`。
  - 收到 token、reasoning 或 keep-alive 不延长 attempt deadline。
  - 单次 TIMEOUT 可以按既有有界重试策略重试一次，但不得无限重试到耗尽总 TTL。
- Applies to：首轮自由文本、首轮 Grounded JSON、传输重试、可信修复、动作修复。
- Violation consequence：单次请求因 keep-alive 无限运行，或把总 TTL 误当单次时限。
- 来源：original。

### Invariant I-3：总 TTL 是生成链独立硬上限

- Rule：
  - 总 TTL 从 `AiReplyDraftService.generate()` 开始执行时启动，使用 `System.nanoTime()`。
  - 总 TTL覆盖 prompt 构造后的所有 provider 调用、重试、可信修复、动作修复和结果校验。
  - Controller查询邮件/联系人/历史的时间不计入 LLM 总 TTL；审核写入、preview 和 SSE结果序列化也不计入。
  - 总 TTL 到期后不得开始下一次 provider 调用；正在进行的调用必须按剩余总 TTL提前结束。
  - 总 TTL 到期映射新 warning `AI_REPLY_LLM_TOTAL_TIMEOUT`；单次调用重试耗尽继续使用 `AI_REPLY_LLM_TIMEOUT`。
- Applies to：AiReplyDraftService、所有回复专用 provider seam。
- Violation consequence：总 TTL 每次重试重置，用户选择 300 秒却等待多倍时间。
- 来源：original。

### Invariant I-4：总 TTL 不扩大重试次数

- Rule：
  - 初始传输失败最多重试一次。
  - Grounded结构/可信修复最多一次。
  - action correction 最多一次。
  - 总 TTL剩余充足也不得新增额外重试。
  - `CANCELLED`、`CLIENT_UNAVAILABLE`、总 TTL耗尽不得重试。
- Applies to：`executeWithRetry`、`generateGrounded`、`enforceActionPolicy`。
- Violation consequence：默认总 TTL=单次×10 被误解为允许10次 provider 调用。
- 来源：`K-llm-timeout-fallback`。

### Invariant I-5：回复专用流式窄 seam

- Rule：
  - 新增只由 TTL 工作台路径使用的 `chatWithModelObservedStream`。
  - 旧 `chat`、`stitchDraft`、`chatWithModel`、observed 方法签名和默认行为保持不变。
  - AiTrainingController 与 GroundedAutoReplyDecisionService 继续使用旧路径。
- Applies to：LlmDraftClient、HttpLlmDraftClient、AiReplyDraftService三个生产入口。
- Violation consequence：训练、自动回复和 fake client 被强制迁移，扩大回归面。
- 来源：`K-reply-model-stable-enum-mapping`、`K-ai-generate-single-freeform-seam`。

### Invariant I-6：DeepSeek SSE 完成条件

- Rule：仅同时满足以下条件才返回 SUCCESS：
  - HTTP 2xx。
  - 响应为 `text/event-stream`。
  - 收到 `data: [DONE]`。
  - 至少拼接一个非空 `delta.content`。
  - 最终 `finish_reason == stop`。
  - 拼接内容不超过 65536 字符。
  - Grounded模式最终内容能进入既有 JSON materialize。
  - `reasoning_content`、keep-alive、空行、usage-only chunk 只计活动指标，不拼接。
  - `length`、`content_filter`、`insufficient_system_resource`、非法 SSE、提前 EOF、缺少 DONE 全部失败。
- Applies to：DeepSeek流 parser、自由文本响应、Grounded JSON响应。
- Violation consequence：截断 JSON、推理内容或部分邮件穿过可信边界。
- 来源：DeepSeek官方协议、`K-grounded-json-materialize-before-policy`。

### Invariant I-7：手动停止是服务端真实取消

- Rule：
  - 浏览器每次 SSE生成前创建 UUID generationId。
  - generationId 同时写入生成请求和取消请求。
  - 点击停止必须先请求服务端 cancel，再 abort 当前 SSE fetch。
  - cancel 必须设置 cancellation token、取消 worker future、取消 heartbeat，并使 HttpClient future/response stream 关闭。
  - service/client 每个调用、重试、修复和提交边界前都检查 cancellation token。
  - CANCELLED 不转成 fallback，不产生 `AiReplyTurnResponse`，SSE只发送 `cancelled` 后完成。
- Applies to：前端 activeGeneration、取消接口、运行时注册表、service、HTTP client。
- Violation consequence：UI显示已停止但线上 Pro 请求仍占用连接和额度。
- 来源：original。

### Invariant I-8：取消线性化与审核边界

- Rule：
  - 运行状态固定为 `REGISTERED → RUNNING → COMMITTING → FINISHED`。
  - `REGISTERED/RUNNING → CANCEL_REQUESTED → FINISHED` 合法。
  - cancel 在 ACTIVE阶段返回 `CANCEL_REQUESTED`；进入 COMMITTING后返回 `TOO_LATE`。
  - worker 只有成功 CAS 到 COMMITTING后才允许写审核、构造最终 response。
  - cancel 先赢：不写审核、不新增草稿；commit 先赢：取消不得覆盖结果。
- Applies to：generation registry、Controller worker、审核写路径。
- Violation consequence：界面显示“已停止”但已经写审核，或停止与完成各输出一次。
- 来源：original。

### Invariant I-9：运行时取消注册表有界且必清理

- Rule：
  - 注册表为单实例进程内 `ConcurrentHashMap<generationId, GenerationControl>`，不持久化。
  - generationId 必须是规范 UUID；同一活动 generationId 重复注册返回409。
  - control 同时绑定 inboundProcessingId；取消路由 id 不匹配时返回 `NOT_ACTIVE`，禁止跨邮件取消。
  - `result/cancel/error/emitter timeout/client disconnect/executor reject` 均在 finally 删除注册项。
  - executor core=2、max=8、queue=32；注册表活动项不得长期超过40。
- Applies to：SSE入口、取消入口、executor、emitter callbacks。
- Violation consequence：内存泄漏、跨邮件误取消、任务队列无限增长。
- 来源：original。

### Invariant I-10：浏览器链只发有界状态和原子结果

- Rule：
  - POST SSE 建立后立即发送 `ready`。
  - 允许发送I-13/I-14规定的有界`progress`元数据；不得发送模型delta。
  - 每10秒发送 `heartbeat`；不得包含 prompt、邮件正文、token、reasoning或草稿。
  - 最终只发送一个 `result`、`cancelled` 或 `error`。
  - 响应头固定包含 `Cache-Control: no-cache, no-transform`、`X-Accel-Buffering: no`。
  - SseEmitter服务端超时固定为`总 TTL + 30秒`；浏览器保险abort时限固定为`总 TTL + 35秒`。
  - 浏览器 SSE parser 必须支持一个 frame 跨多个 TCP chunk及一个 chunk包含多个 frame。
  - 两个时限都基于total TTL，不得基于attempt TTL。
- Applies to：Controller SseEmitter、前端 parser、代理链路。
- Violation consequence：代理30/60秒断流，或浏览器把半帧当完整 JSON。
- 来源：original。

### Invariant I-11：前端状态与旧草稿隔离

- Rule：
  - 请求快照固定包含 `recordId/requestSeq/model/attemptTTL/totalTTL/generationId`。
  - 任一值不再匹配时，旧事件不得渲染。
  - 生成期间模型、两个 TTL、自定义输入和生成按钮禁用；停止按钮保持启用。
  - 手动停止后保留当前已完成草稿和其 raw/rendered/usedLlm/generationState，不新增草稿 entry。
  - 详情切换、reset、请求异常均调用模块级 cancel helper并在 finally恢复 loading。
- Applies to：aiReplyState、可信工作台事件处理、草稿采用状态。
- Violation consequence：停止生成误删旧草稿，或旧邮件流覆盖当前邮件。
- 来源：`K-ai-reply-loading-panel`、`K-ai-draft-review-state-per-draft`、`K-ai-reply-modal-helper-scope`。

### Invariant I-12：失败、可信与隐私边界不变

- Rule：
  - `AI_REPLY_LLM_TOTAL_TIMEOUT` 与 `AI_REPLY_LLM_TIMEOUT` 均产生 `FALLBACK_NO_RESPONSE`、`BLOCKED`、`usedLlm=false`。
  - FREE_FORM失败继续返回既有非空确定性参考，`qaRuleIds` 保持空。
  - 失败参考不可采用；人工独立编辑器发送路径不读取历史失败状态。
  - 日志只允许 model、attemptTTL、totalTTL、generationId前8位、事件数、首包、最大事件间隔、字符数、finishReason、耗时和 failureType。
- Applies to：service fallback、审核、前端反馈、日志。
- Violation consequence：失败伪装成功、人工发送被错误阻断或敏感正文泄露。
- 来源：`K-validation-exhaustion-must-block-readiness`、`K-ai-generation-observability-not-send-gate`、`K-free-form-fallback-nonempty`。

### Invariant I-13：实时进度只表达可验证状态

- Rule：
  - 进度快照字段固定为：

```json
{
  "generationId": "uuid",
  "progressSeq": 7,
  "phase": "CALLING",
  "providerActivity": "REASONING",
  "providerCallIndex": 1,
  "attemptElapsedSeconds": 12,
  "attemptTimeoutSeconds": 60,
  "totalElapsedSeconds": 42,
  "totalTimeoutSeconds": 600,
  "providerEventCount": 37,
  "contentChars": 0,
  "secondsSinceProviderActivity": 2
}
```

  - `phase` 只允许 `QUEUED/PREPARING/CALLING/VALIDATING/REPAIRING/FINALIZING`。
  - `providerActivity` 只允许 `IDLE/WAITING/REASONING/WRITING`。
  - phase 切换由服务端真实执行点驱动；providerActivity 由连接、reasoning chunk、content chunk驱动。
  - 前端进度条只表示 `totalElapsedSeconds / totalTimeoutSeconds`，名称固定为“总 TTL 使用进度”，不得称为完成率。
  - `providerEventCount`、`contentChars` 只作为活动指标，不用于推算完成比例或剩余时间。
- Applies to：service进度上报、HTTP stream活动聚合、Controller SSE、前端loading。
- Violation consequence：前端显示不可验证的99%或错误剩余时间，误导运营判断是否继续等待。
- 来源：`K-ai-stream-progress-no-fake-percent`。

### Invariant I-14：进度有界、保活且隔离陈旧请求

- Rule：
  - phase切换立即发送`progress`；provider chunk只更新内存快照，最多每1秒发送一次聚合`progress`，禁止逐token转发。
  - 每10秒`heartbeat`必须携带当时最新进度快照；provider无数据时仍更新总耗时与`secondsSinceProviderActivity`。
  - `progressSeq`在单个generation内严格递增；浏览器只接受generationId匹配且progressSeq更大的事件。
  - 数值字段必须为非负有界整数；eventCount、contentChars最多回显到`2147483647`，禁止数组或不受限字符串。
  - progress/heartbeat不得包含prompt、邮件正文、delta、reasoning、最终草稿或可还原正文的片段。
  - 进度发送失败不得阻塞provider读取；确认浏览器断开后触发既有取消与cleanup。
- Applies to：progress reporter、GenerationControl、SSE emitter、浏览器parser和状态guard。
- Violation consequence：SSE被token洪泛、敏感内容泄漏、旧请求覆盖新页面，或进度回调反向拖慢模型流。
- 来源：`K-ai-reply-loading-panel`、`K-ai-stream-progress-no-fake-percent`。

## 样式契约

### S-1：模型、单次 TTL、总 TTL 控件

- 复用：
  - `.ai-reply-model-row`：`src/main/resources/static/styles.css:5893`
  - `.ai-reply-model-row label`：`src/main/resources/static/styles.css:5901`
  - `.ai-reply-model-select`：`src/main/resources/static/styles.css:5911`
  - `.ai-reply-model-select:focus`：`src/main/resources/static/styles.css:5921`
  - `.ai-reply-model-select:disabled`：`src/main/resources/static/styles.css:5927`
- 现有 class 不就地修改；新增派生 class。
- 新增 CSS 必须逐字复制：

```css
.ai-reply-generation-controls {
    flex-wrap: wrap;
}

.ai-reply-timeout-select {
    min-width: 154px;
}

.ai-reply-timeout-custom-wrap[hidden] {
    display: none;
}

.ai-reply-timeout-custom-input {
    width: 84px;
    height: 32px;
    padding: 0 8px;
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-sm);
    background: #fff;
    color: var(--text-main);
    font: inherit;
}

.ai-reply-timeout-custom-input:focus {
    outline: none;
    border-color: var(--primary);
    box-shadow: 0 0 0 2px rgba(var(--primary-rgb), 0.12);
}

.ai-reply-timeout-custom-input:disabled {
    cursor: not-allowed;
    opacity: 0.65;
    background: #f8fafc;
}
```

- 最终 DOM 必须为：

```html
<div class="ai-reply-model-row ai-reply-generation-controls">
    <label>生成模型
        <select id="trustReplyModel" class="ai-reply-model-select">
            <option value="DEEPSEEK_V4_FLASH">DeepSeek V4 Flash</option>
            <option value="DEEPSEEK_V4_PRO">DeepSeek V4 Pro</option>
        </select>
    </label>

    <label>单次 TTL
        <select id="trustReplyAttemptTimeout"
                class="ai-reply-model-select ai-reply-timeout-select">
            <option value="30">30 秒（默认）</option>
            <option value="60">60 秒</option>
            <option value="90">90 秒</option>
            <option value="180">180 秒</option>
            <option value="custom">自定义</option>
        </select>
    </label>

    <label id="trustReplyAttemptTimeoutCustomWrap"
           class="ai-reply-timeout-custom-wrap"
           hidden>
        <input id="trustReplyAttemptTimeoutCustom"
               class="ai-reply-timeout-custom-input"
               type="number"
               min="10"
               max="600"
               step="1"
               value="30"
               aria-label="自定义单次生成超时秒数">
        <span>秒</span>
    </label>

    <label>总 TTL
        <select id="trustReplyTotalTimeout"
                class="ai-reply-model-select ai-reply-timeout-select">
            <option value="auto">自动（300 秒）</option>
            <option value="300">300 秒</option>
            <option value="600">600 秒</option>
            <option value="900">900 秒</option>
            <option value="1800">1800 秒</option>
            <option value="custom">自定义</option>
        </select>
    </label>

    <label id="trustReplyTotalTimeoutCustomWrap"
           class="ai-reply-timeout-custom-wrap"
           hidden>
        <input id="trustReplyTotalTimeoutCustom"
               class="ai-reply-timeout-custom-input"
               type="number"
               min="10"
               max="7200"
               step="1"
               value="300"
               aria-label="自定义生成总超时秒数">
        <span>秒</span>
    </label>
</div>
```

- 设计实值：
  - 主色 `#2563eb`
  - 正文 `#1e293b`
  - 弱文本 `#94a3b8`
  - 边框 `rgba(15, 23, 42, 0.08)`
  - 圆角 `7px`
  - 控件高度 `32px`
  - 标签字号 `12px`
  - 控件间距 `8px`
- 禁止项：inline style、修改AI训练模型框、修改现有 class规则、增加未声明 class。

### S-2：停止生成按钮

- 复用：
  - `.button`：`src/main/resources/static/styles.css:587`
  - `.button.secondary`：`src/main/resources/static/styles.css:637`
  - `.button.small`：`src/main/resources/static/styles.css:2244`
  - `.ai-reply-loading-overlay`：`src/main/resources/static/styles.css:5933`
- 新增 CSS 必须逐字复制：

```css
.ai-reply-stop-button {
    margin-top: 2px;
}

.ai-reply-stop-button:disabled {
    cursor: not-allowed;
    opacity: 0.65;
    transform: none;
    box-shadow: none;
}
```

- 仅可信工作台以`stoppable=true`进入loading时，overlay新增DOM必须为：

```html
<span class="ai-reply-loading-spinner" aria-hidden="true"></span>
<span class="ai-reply-loading-text"></span>
<div class="ai-reply-progress" role="group" aria-label="AI 生成进度">
    <div class="ai-reply-progress-phase">正在准备生成上下文</div>
    <progress class="ai-reply-progress-track"
              aria-label="总 TTL 使用进度"
              max="100"
              value="0"
              aria-valuenow="0"
              title="已使用总 TTL 0/300 秒"></progress>
    <div class="ai-reply-progress-detail">
        尚未调用模型 · 总计 0/300 秒
    </div>
    <div class="ai-reply-progress-activity">等待服务端活动…</div>
</div>
<button type="button"
        class="button secondary small ai-reply-stop-button"
        data-action="ai-reply-stop">
    停止生成
</button>
```

- 停止中按钮文案固定为 `正在停止…`。
- 禁止项：danger样式、inline style、把停止按钮放到已有“采用到人工回复”按钮组。

### S-3：实时进度面板

- 仅可信工作台以`stoppable=true`进入loading时显示；AI训练继续使用现有spinner和loading text，不出现进度面板。
- 新增 CSS 必须逐字复制：

```css
.ai-reply-progress {
    width: min(360px, calc(100% - 32px));
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.ai-reply-progress-phase {
    color: var(--text-main);
    font-size: 12px;
    font-weight: 600;
    text-align: center;
}

.ai-reply-progress-detail,
.ai-reply-progress-activity {
    color: var(--text-muted);
    font-size: 11px;
    font-weight: 500;
    text-align: center;
}

.ai-reply-progress-track {
    width: 100%;
    height: 6px;
    overflow: hidden;
    border: 0;
    border-radius: 999px;
    appearance: none;
    background: rgba(var(--primary-rgb), 0.12);
}

.ai-reply-progress-track::-webkit-progress-bar {
    border-radius: 999px;
    background: rgba(var(--primary-rgb), 0.12);
}

.ai-reply-progress-track::-webkit-progress-value {
    border-radius: 999px;
    background: var(--primary);
}

.ai-reply-progress-track::-moz-progress-bar {
    border-radius: 999px;
    background: var(--primary);
}
```

- 前端固定阶段文案：

```javascript
const AI_REPLY_PROGRESS_PHASE_LABELS = {
    QUEUED: "排队等待生成",
    PREPARING: "正在准备生成上下文",
    CALLING: "正在调用 DeepSeek",
    VALIDATING: "正在校验结构与事实",
    REPAIRING: "正在修复未通过的输出",
    FINALIZING: "正在生成最终结果"
};

const AI_REPLY_PROVIDER_ACTIVITY_LABELS = {
    IDLE: "等待服务端活动",
    WAITING: "等待 DeepSeek 数据",
    REASONING: "DeepSeek 思考中",
    WRITING: "DeepSeek 正在输出回复"
};
```

- 展示规则：
  - `providerCallIndex == 0`：`尚未调用模型 · 总计 0/300 秒`。
  - `providerCallIndex > 0`：`第 1 次模型调用 · 本次 12/60 秒 · 总计 42/600 秒`。
  - 活动行为：`DeepSeek 思考中 · 最近活动 2 秒前 · 已接收 37 个流事件`。
  - `providerActivity == WRITING`时追加：`· 已接收 842 字符`。
  - progress的`value`为`clamp(totalElapsedSeconds / totalTimeoutSeconds × 100, 0, 100)`。
  - progressbar的`aria-valuenow`同步value；`title`固定为`已使用总 TTL 42/600 秒`形式。
- 禁止项：显示“完成百分比”、预测剩余时间、逐token刷新DOM、展示delta/reasoning、增加未声明class。

## 现状审计

### DeepSeek HTTP transport

- Schema/mapping：无持久化 schema；当前唯一配置来源为 `LlmProperties`。
- Write paths：
  1. `HttpLlmDraftClient.executeChatObserved` 构造 model/messages/temperature。
  2. JSON场景增加 `response_format.type=json_object`。
- Read paths：
  1. `RestTemplate.postForEntity` 等待完整 JSON。
  2. 读取 `choices[0].message.content`。
  3. `ResourceAccessException(SocketTimeoutException)` 映射 `TIMEOUT`。
  4. HTTP 429/4xx/5xx 映射稳定 failure type。
- Interaction points：
  - 新 streaming seam 必须复用现有 model mapping和failure classification，但不能改变旧 RestTemplate seam。
  - `reasoning_content` 不得作为最终 content。
- 已确认问题：
  - 非流式调用必须等待完整响应。
  - 当前固定30秒读取超时。
  - 当前 `executeWithRetry` 会再调用同一模型一次。

### AI回复生成聚合服务

- Schema/mapping：`AiReplyDraftResult` 是三个生产入口共享结果。
- Write paths：
  1. `generate` 决定模型、QA模式、Prompt和fallback。
  2. `executeWithRetry` 执行初次调用和一次传输重试。
  3. `generateGrounded` 在materialize失败后执行一次可信修复。
  4. `enforceActionPolicy` 在动作违规时执行一次动作修复。
- Read paths：
  1. UnmatchedInboundMailController读取结果并审核/预览。
  2. AiTrainingController读取结果用于模拟，不落审核。
  3. GroundedAutoReplyDecisionService读取结果并执行 fail-closed 发送门禁。
- Interaction points：
  - 双 TTL和取消只能由工作台入口显式启用。
  - 三个入口仍必须共享 prompt/JSON/claim/action逻辑，不能复制生成实现。
  - FREE_FORM失败必须保持非空参考且不污染 `qaRuleIds`。
- 知识确认：`K-ai-generate-single-freeform-seam`、`K-free-form-fallback-nonempty`。

### 邮件 Controller 与审核写路径

- Schema/mapping：
  - `AiReplyTurnRequest` 当前含turns、qaRuleIds、operatorInstruction、operatorName、model。
  - `AiReplyTurnResponse` 当前含草稿、校验状态、模型和证据。
- Write paths：
  1. 首轮 `recordInitialDraft` 写操作日志审核。
  2. continuation 只调用 `buildSnapshot`，不写操作日志。
- Read paths：
  1. 工作台浏览器读取response。
  2. `generationState/usedLlm` 决定是否允许采用。
- Interaction points：
  - JSON和SSE入口必须共用单一私有生成函数，否则审核可能重复。
  - cancel与审核之间需要明确COMMITTING线性化点。
  - `AI_REPLY_LLM_TOTAL_TIMEOUT` 自动进入现有warning snapshot，不改变审核schema。

### 新运行时 cancellation registry

- Schema/mapping：
  - 进程内 `ConcurrentHashMap<String, GenerationControl>`。
  - key：规范 UUID generationId。
  - value：inboundProcessingId、状态、cancellation token、worker future、heartbeat future、emitter。
- Proposed write paths：
  1. SSE入口校验后register。
  2. worker启动时转RUNNING。
  3. cancel接口转CANCEL_REQUESTED。
  4. worker完成前转COMMITTING。
  5. 所有终态finally remove。
- Proposed read paths：
  1. cancel接口按generationId和inboundProcessingId查询。
  2. emitter completion/error/timeout回调查询control并取消。
  3. worker读取token和状态。
- Interaction points：
  - cancel写状态必须被service/client读取。
  - COMMITTING必须先于审核写入。
  - registry remove必须覆盖executor reject和浏览器断开。

### 浏览器工作台状态

- Schema/mapping：`aiReplyState` 当前保存recordId、turns、草稿、requestSeq、inFlight、selectedModel。
- Write paths：
  1. 可信工作台render输出模型select。
  2. 生成handler构造JSON请求。
  3. `setAiReplyLoading` 保存并恢复控件disabled。
  4. `resetAiReplyState` 在邮件切换时清理当前生成状态。
- Read paths：
  1. 模型快照和requestSeq防止旧响应覆盖。
  2. `appendAiChatDraftBubble` 为每个草稿保存raw/rendered/usedLlm/generationState。
  3. adopt handler读取草稿自身状态。
- 已确认缺口：
  - 无attempt/total TTL状态。
  - 无SSE parser与AbortController。
  - `setAiReplyLoading` 当前只禁用button/textarea/select，未禁用input。
  - 无服务器取消调用。
  - loading只显示静态文案，无法区分排队、模型调用、校验或修复阶段。
  - 没有provider最近活动、调用耗时、总TTL消耗或progressSeq。
- Interaction points：
  - stop/reset必须调用模块级共享helper，不能定义在局部action handler。
  - cancel不得清空已有草稿或adoptContext。
  - progress只更新loading子树，不得调用草稿render或修改draft entry。
  - phase/activity标签在前端固定映射；未知枚举拒绝更新、保留最后有效快照并记录有界诊断。
- 知识确认：`K-ai-reply-loading-panel`、`K-ai-stream-progress-no-fake-percent`、`K-ai-draft-review-state-per-draft`、`K-ai-reply-modal-helper-scope`。

### 前端样式盘点

- 可复用 class及全部相关使用点：
  1. `.ai-reply-model-row`：可信工作台动态DOM、AI训练静态DOM。
  2. `.ai-reply-model-select`：`trustReplyModel`、`aiTrainingReplyModel`。
  3. `.ai-reply-loading-overlay`：共享loading helper动态创建。
  4. `.button.secondary.small`：项目通用次级小按钮。
- 改动策略：
  - 现有 class全部保持不变。
  - TTL只给可信工作台增加派生 class。
  - 停止按钮只挂在稳定 `.ai-chat-panel` overlay。
  - 进度面板只插入可信工作台overlay；宽度、字体和颜色严格按S-3。
- 改动前基线：

```html
<div class="ai-reply-model-row">
    <label>生成模型
        <select id="trustReplyModel" class="ai-reply-model-select">
            <option value="DEEPSEEK_V4_FLASH">DeepSeek V4 Flash</option>
            <option value="DEEPSEEK_V4_PRO">DeepSeek V4 Pro</option>
        </select>
    </label>
</div>
```

### 持久化存储

- 数据库：无字段新增、无migration、无TTL写入。
- 操作审核：沿用现有schema；取消在COMMITTING之前不写审核。
- 浏览器：仅tab生命周期内存；刷新恢复单次30/总自动300。
- 服务重启：活动generation registry丢失，浏览器收到断流错误；不做恢复。

## 实现方案

### Stage 0：实现前研究检查点

1. 使用线上同型号Pro进行3次脱敏SSE探测，记录status、Content-Type、首包时间、最大事件间隔、reasoning/content分布、finishReason和DONE。
2. 用provider stub验证“持续keep-alive但超过attempt TTL”仍在attempt deadline停止。
3. 在测试部署通过真实访问域名执行 `curl -N`，确认`ready`立即出现、heartbeat每10秒可见且未被压缩/代理缓存。
4. 检查Tomcat异步请求支持；若SSE响应被基础设施强制缓冲，先解决缓冲，禁止通过放大TTL规避。
5. 对真实DeepSeek流确认reasoning/content chunk可观测但无官方total token或完成比例；只据此映射providerActivity，不设计完成率。
6. 用stub每秒发送100个chunk，确认服务端聚合后浏览器`progress`不超过每秒1个，provider读取不被SSE发送阻塞。

Research gate：

- provider不符合官方SSE协议：停止Stage 1，不实施自定义parser。
- public route 20秒仍看不到heartbeat：停止上线，定位代理缓冲。
- cancel后provider连接仍持续超过15秒：停止上线，修复cancel传播。
- progress包含正文/reasoning或每秒超过1个：停止上线，修复聚合与隐私边界。

### Stage 1：流式运行资源

文件：

- `src/main/kotlin/com/weibo/talentintroduction/config/AiReplyStreamingConfig.kt`

任务：

1. 新增 `aiReplyStreamExecutor`：
   - core=2
   - max=8
   - queue=32
   - thread prefix=`ai-reply-stream-`
   - shutdown不等待长任务
2. 新增 `aiReplyStreamScheduler`：
   - pool=2
   - thread prefix=`ai-reply-heartbeat-`
3. 新增 `aiReplySseHttpClient`：
   - JDK11 HttpClient
   - connect timeout=10秒
   - HTTP/1.1
4. 不新增Maven依赖，不修改现有`llmRestTemplate`。

约束：I-5、I-9、I-10、I-12。

### Stage 2：DeepSeek streaming seam

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClient.kt`

任务：

1. `LlmChatFailureType` 增加 `CANCELLED`；保留现有枚举值。
2. `LlmDraftClient` 增加有默认实现的窄接口：

```kotlin
fun chatWithModelObservedStream(
    messages: List<LlmChatMessage>,
    temperature: Double?,
    providerModel: String,
    timeoutMillis: Long,
    jsonOutput: Boolean,
    cancellationToken: AiReplyCancellationToken,
    progressSink: LlmStreamProgressSink = LlmStreamProgressSink.NOOP
): LlmChatResult
```

3. 同文件新增窄活动类型：

```kotlin
enum class LlmStreamActivity { WAITING, REASONING, WRITING }

fun interface LlmStreamProgressSink {
    fun onActivity(activity: LlmStreamActivity, eventCount: Int, contentChars: Int)
}
```

   - `NOOP`不得分配对象或改变旧调用行为。
   - eventCount/contentChars使用饱和递增，上限`Int.MAX_VALUE`。
4. 默认实现根据jsonOutput委托旧observed seam，保证fake client和非HTTP实现兼容；默认实现不产生伪progress。
5. HTTP实现使用JDK HttpClient发出：
   - `Accept: text/event-stream`
   - `Content-Type: application/json`
   - `stream:true`
   - `stream_options.include_usage:true`
   - Grounded时增加`response_format.type=json_object`
6. parser严格实现I-6：
   - 忽略空行和`: keep-alive`
   - 忽略reasoning
   - 拼接content
   - 忽略choices为空的usage chunk
   - 验证DONE、stop和64K上限
   - 连接建立后报告WAITING
   - reasoning chunk报告REASONING
   - 非空content chunk报告WRITING
   - keep-alive、usage-only和空delta只刷新最近活动与eventCount，不改变contentChars或当前activity
   - activity callback只携带枚举和有界计数，不携带chunk文本
7. attempt deadline：
   - HttpRequest timeout使用传入timeoutMillis。
   - scheduler到期同时cancel response future并close body stream。
8. cancellation：
   - token取消或线程中断时cancel future、close stream并返回CANCELLED。
9. progressSink异常必须隔离：token已取消时返回CANCELLED；否则只记录有界诊断，禁止中断provider解析。
10. 日志只输出I-12允许字段。

约束：I-2、I-5、I-6、I-7、I-12、I-13、I-14。

### Stage 3：双预算与取消传播

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

任务：

1. 增加纯运行时类型：

```text
AiReplyTimeoutPolicy
AiReplyGenerationBudget
AiReplyCancellationToken
AiReplyGenerationCancelledException
AiReplyProgressPhase
AiReplyProviderActivity
AiReplyProgressSnapshot
AiReplyProgressReporter
AiReplyProgressTracker
```

2. Policy固定规则：
   - attempt默认30，范围10～600。
   - total null=>attempt×10。
   - total范围attempt～7200。
3. Budget使用nanoTime：
   - 保存attemptMillis、totalDeadlineNanos。
   - `remainingTotalMillis(now)`。
   - `nextAttemptMillis(now)=min(attemptMillis, remainingTotalMillis)`。
4. `generate` 增加可选参数：

```kotlin
llmAttemptTimeoutSeconds: Int? = null
llmTotalTimeoutSeconds: Int? = null
cancellationToken: AiReplyCancellationToken? = null
progressReporter: AiReplyProgressReporter = AiReplyProgressReporter.NOOP
```

5. 语义：
   - 两个TTL与cancellationToken全null：旧调用路径不变。
   - Controller传入双TTL：创建budget并走stream seam。
   - NOOP reporter不分配tracker、不启动timer、不影响训练/自动回复。
6. `AiReplyProgressTracker`：
   - Controller在register后创建tracker并发布QUEUED；service创建总budget后调用`startBudget(...)`，此时总TTL才开始计时并切PREPARING。
   - 使用与budget相同的nanoTime源。
   - 保存generationId、严格递增progressSeq、当前phase/providerActivity、providerCallIndex、当前调用开始时间、最近provider活动时间、eventCount/contentChars。
   - `transition(phase)`立即发布快照。
   - `beginProviderCall(phase, timeoutMillis)`递增providerCallIndex并返回`LlmStreamProgressSink`。
   - `endProviderCall()`冻结本次调用耗时并把providerActivity切为IDLE；eventCount/contentChars为整个generation累计值。
   - `snapshotNow()`根据单调时钟刷新attempt/total elapsed和secondsSinceProviderActivity。
   - 全部计数饱和到`Int.MAX_VALUE`，无正文或chunk引用。
7. 所有provider调用收口到一个budget-aware helper：
   - 首次自由生成
   - 首次Grounded
   - transport retry
   - trust correction
   - action correction
8. helper接收业务phase；首轮/transport retry为CALLING，可信/动作修复为REPAIRING，并在调用前后检查token和total deadline。
9. provider返回后进入VALIDATING；可信/动作校验完成后进入FINALIZING；取消/失败终态仍走既有终态事件，不新增progress终态。
10. bounded retry保持I-4；attempt timeout仍最多重试一次。
11. total耗尽新增warning：

```text
AI_REPLY_LLM_TOTAL_TIMEOUT
```

12. CANCELLED立即抛`AiReplyGenerationCancelledException`，禁止进入fallback。
13. `failureTypeToWarning(CANCELLED)`返回null；cancel由异常通道处理，不进入warning/fallback通道。
14. FREE_FORM/grounded普通失败继续使用既有fallback和BLOCKED门禁。

读取方：

- Controller消费正常result或cancel exception。
- AiTrainingController、GroundedAutoReplyDecisionService继续传null，无需修改。

约束：I-1、I-2、I-3、I-4、I-5、I-7、I-12、I-13、I-14。

### Stage 4：SSE、运行时注册表与取消接口

文件：

- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`

任务：

1. 请求增加：

```kotlin
val generationId: String? = null
val llmAttemptTimeoutSeconds: Int? = null
val llmTotalTimeoutSeconds: Int? = null
```

2. 响应增加：

```kotlin
val appliedLlmAttemptTimeoutSeconds: Int = 30
val appliedLlmTotalTimeoutSeconds: Int = 300
```

3. 保留旧JSON接口；缺省双TTL解析为30/300，generationId可空。
4. 新增：

```text
POST /api/mail/unmatched-inbound/{id}/ai-reply/turn-stream
Produces: text/event-stream
```

5. 新增取消接口：

```text
POST /api/mail/unmatched-inbound/{id}/ai-reply/generations/{generationId}/cancel
```

返回：

```json
{"generationId":"...","status":"CANCEL_REQUESTED"}
{"generationId":"...","status":"TOO_LATE"}
{"generationId":"...","status":"NOT_ACTIVE"}
```

6. Controller内新增singleton runtime registry及GenerationControl：
   - UUID校验
   - inboundProcessingId绑定
   - 状态CAS
   - token/future/heartbeat/emitter引用
   - progress tracker、latestProgress、lastProgressSentNanos、progressFlush future、SSE send lock
   - finally remove
7. JSON和SSE入口共用唯一`executeAiReplyTurn`；邮件上下文、service调用、审核、preview、response构造只保留一份。
8. SSE流程：
   - 校验generationId和双TTL
   - register
   - emitter timeout设置为resolved total TTL+30秒
   - 立即发送ready并附QUEUED初始快照
   - service开始时切PREPARING；每个真实执行点按I-13切phase
   - phase切换立即发送`progress`
   - provider活动只更新latestProgress，并按单调时钟合并为最多每秒1个`progress`
   - 每10秒heartbeat调用`snapshotNow()`，payload附最新progress
   - executor执行生成
   - 正常结果在`tryBeginCommit`成功后才审核并发送result
   - cancel exception发送cancelled
   - error发送稳定error
9. cancel流程：
   - ACTIVE=>CANCEL_REQUESTED
   - token.cancel
   - future.cancel(true)
   - heartbeat.cancel
   - 尝试发送cancelled并complete
10. 所有SSE send经GenerationControl单一send lock串行化：
    - ACTIVE状态才允许progress/heartbeat。
    - result/cancelled/error赢得终态后禁止后续progress。
    - send失败标记客户端断开、取消token/future/heartbeat/progressFlush并cleanup。
11. progress事件使用I-13固定字段；heartbeat结构固定为：

```json
{
  "generationId": "uuid",
  "progress": {
    "generationId": "uuid",
    "progressSeq": 7,
    "phase": "CALLING",
    "providerActivity": "REASONING",
    "providerCallIndex": 1,
    "attemptElapsedSeconds": 12,
    "attemptTimeoutSeconds": 60,
    "totalElapsedSeconds": 42,
    "totalTimeoutSeconds": 600,
    "providerEventCount": 37,
    "contentChars": 0,
    "secondsSinceProviderActivity": 2
  }
}
```

12. emitter completion/error/timeout执行同一cleanup；progressFlush也必须取消。
13. 响应头设置no-cache/no-transform和X-Accel-Buffering=no。

约束：I-1、I-7、I-8、I-9、I-10、I-12、I-13、I-14。

### Stage 5：双 TTL UI、SSE parser与停止按钮

文件：

- `src/main/resources/static/app.js`
- `src/main/resources/static/styles.css`

任务：

1. `aiReplyState`增加：

```javascript
attemptTimeoutMode: "30",
attemptTimeoutSeconds: 30,
attemptCustomSeconds: 30,
totalTimeoutMode: "auto",
totalTimeoutSeconds: 300,
totalCustomSeconds: 300,
activeGeneration: null,
latestProgress: null,
lastProgressSeq: -1,
progressReceivedAt: 0,
progressTimerId: null
```

2. 新增模块级helper：

```text
resolveAiReplyTimeoutSelection
syncAiReplyTimeoutControls
createAiReplyGenerationId
parseAiReplySseFrames
postAiReplySse
requestAiReplyCancellation
cancelActiveAiReplyGeneration
updateAiReplyLoadingMessage
normalizeAiReplyProgressSnapshot
acceptAiReplyProgressSnapshot
renderAiReplyProgress
startAiReplyProgressTicker
stopAiReplyProgressTicker
```

3. 双TTL行为：
   - attempt切换时更新auto option文案，例如attempt60=>`自动（600 秒）`。
   - total auto始终发送null，由服务端按attempt×10解析；前端同时显示推导值。
   - total显式值小于attempt时禁止请求并显示：
     `总 TTL 必须大于或等于单次 TTL`
   - attempt非法显示：
     `自定义单次 TTL 需为 10–600 的整数秒`
   - total非法显示：
     `自定义总 TTL 需为单次 TTL 至 7200 秒的整数`
4. 每次生成创建UUID，将模型、双TTL、generationId写入请求。
5. `postAiReplySse`：
   - POST + `Accept:text/event-stream`
   - TextDecoder流式解码
   - 跨chunk组装frame
   - ready中的QUEUED快照、`progress` payload及heartbeat.progress全部进入同一`acceptAiReplyProgressSnapshot`
   - generationId不匹配、progressSeq不递增、字段非整数/越界、未知phase/activity的事件不写状态
   - `progress`只更新loading进度DOM，不进入草稿render
   - 只将result交给现有渲染
   - cancelled只清loading并显示`已停止生成`
   - EOF无终态时报错
   - AbortController保险时限=resolved total+35秒
6. `setAiReplyLoading`：
   - 增加可选`{stoppable=false, generationId=null, attemptTimeoutSeconds=30, totalTimeoutSeconds=300}`参数；AI训练调用保持默认，可信工作台传本次解析值
   - disabled selector改为`button, textarea, select, input`
   - 仅stoppable=true时overlay按S-2/S-3增加进度面板和停止按钮
   - S-2 DOM中的`0/300`和title按本次resolved total以textContent/属性设置，禁止拼接不可信HTML
   - 停止按钮创建于普通控件禁用后，保持可点击
7. 停止操作：
   - 按钮改为`正在停止…`并disabled
   - POST cancel
   - CANCEL_REQUESTED时abort stream、清activeGeneration、移除loading、显示`已停止生成`
   - TOO_LATE时保持stream并显示`生成已进入完成阶段，无法停止`
   - NOT_ACTIVE时abort本地stream并清理
8. reset/切换邮件：
   - best-effort调用cancel endpoint
   - abort当前stream
   - 不清空已完成草稿的per-draft采用边界
9. 响应guard增加attempt/total/generationId比较。
10. 实时显示：
    - 收到快照时记录`progressReceivedAt=performance.now()`并立即render。
    - 生成期间启动单一1秒ticker，用本地单调时间增量外推total elapsed及最近活动秒数；providerActivity非IDLE时才外推attempt elapsed；只外推时间，不增加eventCount/contentChars、不改变phase/activity。
    - 外推值分别clamp到attempt/total TTL；下一次服务端快照到达时重新校准。
    - detail、activity、TTL bar、ARIA/title严格执行S-3。
    - result/cancelled/error/abort/reset/finally全部停止ticker并清latestProgress。
11. CSS严格执行S-1、S-2、S-3。

约束：I-1、I-7、I-10、I-11、I-12、I-13、I-14、S-1、S-2、S-3。

### Stage 6：自动测试

文件：

- `src/test/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClientTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`
- `src/test/js/aiReplyLoadingFeedback.test.js`

HttpLlmDraftClientTest新增：

1. stream请求包含stream/usage，Grounded额外含response_format。
2. keep-alive/reasoning/usage不进入content。
3. DONE+stop+content才SUCCESS。
4. length/content_filter/资源不足/缺DONE/提前EOF分类正确。
5. content上限65536。
6. attempt deadline关闭future与body。
7. token取消返回CANCELLED且部分content为null。
8. 旧RestTemplate seam请求和分类不变。
9. WAITING/REASONING/WRITING活动映射正确，callback不含chunk文本。
10. eventCount/contentChars饱和且progressSink异常不破坏正常stream。

AiReplyDraftServiceTest新增：

1. attempt null=>30，total null=>attempt×10。
2. attempt 10/600合法，9/601非法。
3. total等于attempt合法，低于attempt非法，7200合法，7201非法。
4. 每次调用拿`min(attempt, remainingTotal)`。
5. attempt timeout只按原策略重试一次。
6. 总预算充足不扩大调用次数。
7. total耗尽不开始新调用并产生`AI_REPLY_LLM_TOTAL_TIMEOUT`。
8. cancel阻止retry/trust correction/action correction。
9. cancellation抛异常而不是fallback。
10. TTL参数全null时旧训练/自动调用seam不变。
11. FREE_FORM失败仍为非空参考且qaRuleIds为空。
12. phase按PREPARING→CALLING→VALIDATING→FINALIZING真实执行点变化。
13. retry递增providerCallIndex；可信/动作修复使用REPAIRING。
14. snapshot使用nanoTime计算attempt/total elapsed和最近活动，数值不会倒退或越界。
15. NOOP reporter不改变旧路径调用次数和结果。

Controller测试新增：

1. JSON缺省回显30/300。
2. SSE缺generationId、非法UUID、重复UUID拒绝。
3. ready回显generationId/attempt/total。
4. heartbeat无业务正文。
5. result只发送一次。
6. cancel active=>CANCEL_REQUESTED并取消future/heartbeat。
7. cancel COMMITTING=>TOO_LATE。
8. cancel id不匹配=>NOT_ACTIVE。
9. cancel先赢时recordInitialDraft为0次。
10. commit先赢时recordInitialDraft为1次。
11. completion/error/timeout/reject都删除registry。
12. JSON和SSE正常结果字段一致。
13. ready包含QUEUED快照，phase切换立即发送progress。
14. 高频provider活动被合并为每秒最多一个progress。
15. heartbeat携带最新快照；provider静默时elapsed和secondsSinceProviderActivity继续增长。
16. progress/heartbeat只含I-13白名单字段，不含prompt/content/reasoning/邮件正文。
17. generation终态后不再发送progress，cleanup取消progressFlush。

aiReplyLoadingFeedback.test.js新增：

1. DOM包含模型、attempt、total控件及精确options。
2. auto total随attempt×10变化。
3. 两个custom范围和错误文案精确。
4. SSE parser覆盖split frame、multi-frame、heartbeat/result/cancelled/error。
5. 请求payload含generationId和双TTL。
6. stop按钮使用S-2 class并调用cancel route。
7. reset和邮件切换调用模块级cancel helper。
8. loading禁用input但停止按钮保持启用。
9. AI训练调用不出现停止按钮。
10. cancel不修改已有draft entry/adoptContext。
11. progress仅接受匹配generationId且progressSeq递增的快照。
12. progress阶段、活动、detail、TTL bar、ARIA/title按S-3渲染。
13. 1秒ticker只外推时间并在所有终态清理。
14. 字符数/事件数不显示为完成率；源码和DOM不存在`完成百分比`。
15. CSS与S-1/S-2/S-3逐字一致。

约束：I-1～I-14、S-1、S-2、S-3。

## 变更文件清单

| # | 文件 | 操作 | 目的 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/config/AiReplyStreamingConfig.kt` | 新增 | executor、scheduler、JDK HttpClient |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClient.kt` | 修改 | DeepSeek SSE、attempt deadline、cancel、活动指标 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 修改 | 双预算、bounded retry、取消传播、进度tracker |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 修改 | DTO、SSE、registry、cancel、进度聚合、审核线性化 |
| 5 | `src/main/resources/static/app.js` | 修改 | 双TTL UI、SSE parser、实时进度、手动停止 |
| 6 | `src/main/resources/static/styles.css` | 修改 | S-1/S-2/S-3 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClientTest.kt` | 修改 | transport/cancel测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 修改 | budget/retry/cancel测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt` | 修改 | SSE/registry/progress/audit测试 |
| 10 | `src/test/js/aiReplyLoadingFeedback.test.js` | 修改 | TTL/SSE/progress/stop DOM与状态测试 |

文件数：10，符合上限。  
子系统数：2，符合上限。  
不允许执行阶段修改清单外文件。

## 验收标准

- I-1：
  - 单次缺省30；总缺省300。
  - attempt10/600、total=attempt/7200通过；越界返回400。
  - ready和result回显值一致。
- I-2：
  - stub证明一次调用超过attempt时终止，即使每5秒有keep-alive。
  - 第二次调用重新获得完整attempt，但不超过total剩余。
- I-3：
  - fake nanoTime证明总deadline只创建一次。
  - final attempt被`min(attempt, remainingTotal)`截短。
  - 总耗尽warning为`AI_REPLY_LLM_TOTAL_TIMEOUT`。
- I-4：
  - 任意总TTL下初始传输最多2次、可信修复最多1次、动作修复最多1次。
- I-5：
  - TTL工作台使用stream seam。
  - AiTrainingController与GroundedAutoReplyDecisionService的既有测试证明仍使用旧路径。
- I-6：
  - 只有DONE+stop+非空content成功。
  - reasoning、keep-alive、usage不进入输出。
  - 所有partial/非stop/超限结果content均为null。
- I-7：
  - cancel后worker、heartbeat、HttpClient future均取消。
  - 不产生fallback/result。
- I-8：
  - cancel先赢审核0次；commit先赢审核1次且cancel返回TOO_LATE。
- I-9：
  - completion/cancel/error/timeout/reject五条终态registry size回到0。
  - 跨inbound id取消返回NOT_ACTIVE。
- I-10：
  - SSE只含ready/progress/heartbeat/单一终态。
  - progress/heartbeat payload不含邮件、草稿、prompt、delta或reasoning。
  - parser通过frame切分测试。
- I-11：
  - request guard包含record/model/attempt/total/generationId。
  - 手动停止后已有draft entry和adoptContext逐字保持。
- I-12：
  - timeout fallback为BLOCKED、usedLlm=false、不可采用。
  - FREE_FORM fallback非空且qaRuleIds为空。
  - 日志测试/静态检查不出现content、prompt、reasoning。
- I-13：
  - phase/providerActivity只来自固定枚举和真实执行点。
  - 页面明确标注“总 TTL 使用进度”，不存在生成完成率或剩余时间预测。
  - providerCallIndex、调用/总耗时、事件数和字符数与stub观测一致。
- I-14：
  - 100 chunk/秒输入时progress输出不超过1个/秒；phase切换不延迟。
  - provider静默时heartbeat仍推进elapsed和最近活动时间。
  - 错generationId、重复/倒退progressSeq不更新DOM。
  - progress/heartbeat白名单检查不含正文或模型片段。
- S-1：
  - DOM与CSS契约逐字一致。
  - 无inline style、无未声明class、AI训练DOM无改动。
- S-2：
  - overlay停止按钮class/文案逐字一致。
  - disabled规则逐字一致。
- S-3：
  - 进度DOM、阶段/活动文案和CSS契约逐字一致。
  - TTL bar宽度、aria-valuenow、title与总TTL已用比例一致。
  - AI训练overlay不出现进度面板。

自动验证命令：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
mvn test -Dtest=HttpLlmDraftClientTest,AiReplyDraftServiceTest,UnmatchedInboundAiReplyTurnKnowledgeTest
```

```bash
node --test src/test/js/aiReplyLoadingFeedback.test.js
```

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

## 人工验收清单

### A-1：默认双 TTL

- 前置条件：打开任一存在可信工作台的待回复邮件。
- 操作步骤：
  1. 展开“可信回复工作台”。
  2. 不修改任何TTL。
- 预期结果：
  - 单次TTL显示`30 秒（默认）`。
  - 总TTL显示`自动（300 秒）`。
  - 两个自定义输入均隐藏。
  - 请求发送attempt=30、total=null；ready回显30/300。
- 覆盖：需求1、I-1、S-1。

### A-2：单次预设与自动总 TTL

- 前置条件：同A-1。
- 操作步骤：
  1. 依次选择单次60、90、180。
  2. 观察总TTL自动option。
- 预期结果：
  - 文案依次为`自动（600 秒）`、`自动（900 秒）`、`自动（1800 秒）`。
  - 无需修改服务器基础配置或重启。
- 覆盖：需求1、I-1。

### A-3：自定义单次与总 TTL

- 前置条件：同A-1。
- 操作步骤：
  1. 单次选择自定义，输入240。
  2. 总TTL选择自定义，输入2400。
  3. 点击生成。
- 预期结果：
  - 请求包含`llmAttemptTimeoutSeconds:240`和`llmTotalTimeoutSeconds:2400`。
  - ready回显240/2400。
- 覆盖：需求1、I-1、S-1。

### A-4：非法单次 TTL

- 前置条件：单次选择自定义。
- 操作步骤：
  1. 输入601。
  2. 点击生成。
- 预期结果：
  - 不产生网络请求。
  - 页面显示`自定义单次 TTL 需为 10–600 的整数秒`。
- 覆盖：I-1。

### A-5：非法总 TTL

- 前置条件：单次选择180；总TTL选择自定义。
- 操作步骤：
  1. 输入120。
  2. 点击生成。
- 预期结果：
  - 不产生网络请求。
  - 页面显示`总 TTL 必须大于或等于单次 TTL`。
- 覆盖：I-1。

### A-6：单次 TTL 与总 TTL 独立

- 前置条件：provider stub每次35秒才完成；单次30；总300。
- 操作步骤：点击生成。
- 预期结果：
  - 第一次调用约30秒单次超时。
  - 最多再调用一次，不循环10次。
  - 总TTL仍为300但调用次数不增加。
- 覆盖：I-2、I-4。

### A-7：总 TTL 截断最后一次调用

- 前置条件：stub可控制多阶段耗时；单次60；总90。
- 操作步骤：使前序步骤消耗70秒后触发修复调用。
- 预期结果：
  - 修复调用最大只获得约20秒。
  - 总耗尽显示`DeepSeek 生成总时限已用尽`。
  - 当前参考内容不可采用。
- 覆盖：I-3、I-12。

### A-8：端到端 heartbeat

- 前置条件：Pro；单次60；总600；真实生成超过30秒。
- 操作步骤：
  1. 打开浏览器Network。
  2. 点击生成。
  3. 观察SSE Response。
- 预期结果：
  - 立即出现ready。
  - 阶段切换或provider活动时出现有界progress。
  - 第10、20、30秒附近出现heartbeat。
  - 30秒时浏览器连接不因无响应断开。
  - progress/heartbeat不含邮件、草稿、delta或reasoning。
- 覆盖：需求2/5、I-10、I-14。

### A-9：流式Pro成功

- 前置条件：目标邮件；Pro；单次180；总自动1800。
- 操作步骤：生成可信草稿。
- 预期结果：
  - DeepSeek使用stream=true。
  - 最终页面只出现完整草稿。
  - 只有LLM_USED且可信校验通过时采用按钮可用。
- 覆盖：I-5、I-6、I-12。

### A-10：手动停止运行中任务

- 前置条件：stub持续生成；单次180；总1800。
- 操作步骤：
  1. 点击生成。
  2. heartbeat出现后点击`停止生成`。
- 预期结果：
  - 按钮立即显示`正在停止…`。
  - cancel接口返回`CANCEL_REQUESTED`。
  - 页面显示`已停止生成`。
  - heartbeat停止。
  - provider连接在15秒内关闭。
  - 不显示fallback或新草稿。
- 覆盖：需求3、I-7、S-2。

### A-11：停止不破坏已有草稿

- 前置条件：先成功生成草稿A，再开始重新生成B。
- 操作步骤：
  1. B生成期间点击停止。
  2. 查看草稿和采用按钮。
- 预期结果：
  - 草稿A仍原样显示。
  - 草稿A自身usedLlm/generationState采用边界保持。
  - 不新增草稿B条目。
- 覆盖：I-11、必须保持不变4。

### A-12：完成边界后停止

- 前置条件：生成即将完成。
- 操作步骤：在result提交阶段点击停止。
- 预期结果：
  - cancel返回`TOO_LATE`。
  - 页面显示`生成已进入完成阶段，无法停止`。
  - 最终只出现一个result。
  - 初始审核只有一条。
- 覆盖：I-8、I-9。

### A-13：切换邮件自动取消

- 前置条件：邮件A正在生成；存在邮件B。
- 操作步骤：从A切换到B。
- 预期结果：
  - A的cancel被best-effort调用。
  - A的fetch被abort。
  - A后续事件不显示到B。
  - B的模型与TTL控件可操作。
- 覆盖：I-7、I-11。

### A-14：不完整SSE/JSON

- 前置条件：stub发送半段JSON后断开。
- 操作步骤：生成Grounded回复。
- 预期结果：
  - 不显示半段JSON或半成品邮件。
  - 显示稳定LLM失败参考。
  - 采用按钮禁用。
- 覆盖：I-6、I-12。

### A-15：旧JSON API兼容

- 前置条件：直接调用原`/ai-reply/turn`。
- 操作步骤：
  1. 不传generationId和双TTL。
  2. 发起POST。
- 预期结果：
  - 返回普通JSON，不是SSE。
  - 回显attempt=30、total=300。
  - 原有字段全部存在。
- 覆盖：I-1、I-10、必须保持不变7。

### A-16：AI训练与自动回复回归

- 前置条件：存在可模拟邮件和可执行自动回复fixture。
- 操作步骤：
  1. 执行一次AI训练模拟。
  2. 执行一次Grounded自动回复decision。
- 预期结果：
  - 两个入口不要求generationId或双TTL。
  - 两个入口继续使用旧seam。
  - 结果和发送门禁不变。
- 覆盖：I-5、必须保持不变6。

### A-17：审核与人工发送回归

- 前置条件：一次成功生成、一次单次超时、一次手动停止。
- 操作步骤：
  1. 检查三次操作日志。
  2. 尝试采用超时fallback。
  3. 在人工编辑器独立填写正文并发送。
- 预期结果：
  - 成功生成有一条初始审核。
  - 超时fallback有一条BLOCKED审核且不可采用。
  - COMMITTING前停止无初始草稿审核。
  - 人工独立正文仍可通过既有复验发送。
- 覆盖：I-8、I-12、必须保持不变3/4/5。

### A-18：日志隐私与注册表清理

- 前置条件：分别完成成功、超时、取消、浏览器断开。
- 操作步骤：
  1. 检查服务日志。
  2. 查询测试诊断中的active generation数量。
- 预期结果：
  - 日志仅有model、双TTL、generationId前8位、事件/耗时/failureType。
  - 无邮件正文、Prompt、reasoning或最终草稿。
  - 四种终态后active generation均为0。
- 覆盖：I-9、I-12。

### A-19：实时阶段与 TTL 使用进度

- 前置条件：stub依次执行准备、首次调用、校验、修复、最终化；单次60，总600。
- 操作步骤：
  1. 点击生成。
  2. 观察阶段、detail、activity、进度条和无障碍属性。
  3. 让stub先发送reasoning，再发送content。
- 预期结果：
  - 阶段依次显示`正在准备生成上下文`、`正在调用 DeepSeek`、`正在校验结构与事实`、`正在修复未通过的输出`、`正在生成最终结果`。
  - provider活动依次显示等待、思考、输出回复；eventCount和contentChars只增不减。
  - detail显示`第 N 次模型调用 · 本次 x/60 秒 · 总计 y/600 秒`。
  - 进度条约每秒推进，明确标为`总 TTL 使用进度`；aria/title与y/600一致。
  - 页面不出现生成完成百分比或预测剩余时间。
- 覆盖：需求5、I-13、S-3。

### A-20：provider静默期间状态可见

- 前置条件：stub连接成功后25秒不发送chunk；单次30，总300。
- 操作步骤：点击生成并持续观察30秒。
- 预期结果：
  - 页面持续显示当前阶段和`等待 DeepSeek 数据`。
  - 最近活动秒数与调用/总耗时每秒增长，heartbeat到达后重新校准且不倒退。
  - 10秒、20秒仍收到heartbeat，页面不处于未知静态spinner状态。
  - 约30秒按单次TTL进入既有timeout/retry流程，不因进度显示延长deadline。
- 覆盖：需求1/2/5、I-2、I-13、I-14。

### A-21：高频、陈旧与隐私隔离

- 前置条件：stub每秒发送100个chunk；邮件A开始generation A，随后切换邮件B开始generation B。
- 操作步骤：
  1. 记录浏览器收到的progress频率。
  2. 在B页面注入A的迟到progress及B的重复/倒退progressSeq。
  3. 检查Network事件payload。
- 预期结果：
  - 常规provider活动progress不超过每秒1个，页面仍每秒平滑更新时间预算。
  - A事件、重复seq和倒退seq均不改变B页面。
  - payload只有I-13字段，不含邮件、prompt、delta、reasoning或草稿。
  - B停止/完成后ticker与progressFlush均清理，无后续DOM更新。
- 覆盖：I-11、I-14、S-3。
