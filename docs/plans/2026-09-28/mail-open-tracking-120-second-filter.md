# 邮件图片请求 120 秒过滤开发计划

日期：2026-09-28。目标：按用户已确认的 120 秒口径，宁可漏掉真实打开，也减少“刚投递即请求图片”被展示为打开的情况。**本文件是开发计划，尚未实施。**

## 需求描述

对已成功发送且已关联跟踪像素的邮件，发送时间起 **120 秒内（含第 120 秒）**的图片请求只保留为原始请求记录；只有在 120 秒之后**又收到新的图片请求**，列表、状态筛选及汇总才显示“疑似打开（120 秒后请求）”。用户在列表和详情仍能查看原始首次/最近图片请求时间，且页面明确说明此信号不能确认本人阅读。

必须保持：① 跟踪开关、像素 GET/HEAD、token、SMTP 植入、邮件发送记录及既有首次/最近请求的写入规则；② 发送日期/账号、分页、关键词、详情的查询范围；③ 未跟踪、失败发送、入站邮件、孤儿 token 的排除行为；④ 页面已有结构与样式。

范围外：识别真人/代理的 UA/IP 规则、点击/回复归因、逐次请求事件表、追溯“首次有效请求”的精确时间、新状态枚举、可配置阈值、生产历史数据回写和上线部署。120 秒仅为保守显示门槛，**不能证明超过门槛的请求来自真人**。

## 关键不变量

### Invariant I-1：120 秒严格边界
- Rule：以 `mail_record.sent_at` 为起点；`last_open_at > DATE_ADD(sent_at, INTERVAL 120 SECOND)` 才是候选信号。恰好 120 秒仍按预加载处理。`last_open_at` 为 NULL、早于发送、或只在 120 秒内的请求均不候选。内部 API 状态键沿用 `OPENED`/`NO_SIGNAL`/`NOT_TRACKED`，但 `OPENED` 的外部解释只能是“疑似打开（120 秒后请求）”。
- Applies to：`MailOpenTrackingRepository.readPage` 的列表、状态过滤、总数、汇总和 `detail`。
- Violation consequence：列表与汇总冲突，或早期图片请求被误报。
- 来源：本次用户确认；`MailOpenTrackingRepository.kt:88-160`。

### Invariant I-2：没有计时器自动转状态
- Rule：早期信号即使经过 120 秒，也保持 `NO_SIGNAL`；必须再次收到像素 GET，且 `last_open_at` 更新到阈值之后，才变为 `OPENED`。关闭跟踪后不会写新请求；HEAD 不记请求。历史记录按现有首次/最近时间重新计算，不补造事件。
- Applies to：既有 `Controller.pixel/pixelHead` → `Service.recordSignal` → `Repository.recordSignal`；`Repository.readPage/detail`。
- Violation consequence：仅时间流逝就把未打开邮件显示为打开，违背用户优先避免误报的要求。
- 来源：`MailOpenTrackingController.kt:44-51`、`MailOpenTrackingRepository.kt:78-85`。

### Invariant I-3：原始时间与推断状态分离
- Rule：`firstOpenAt`/`lastOpenAt` 字段和值仍是**所有有效像素 GET 的最早/最晚时间**，不伪称“首次/最近真人打开”；新状态只由最近请求是否越过阈值推导。列表、筛选、汇总、详情用同一 SQL 阈值表达式。`NO_SIGNAL` 允许展示早期请求时间。
- Applies to：`mail_open_tracking` 两个时间字段的既有写入，`OpenTrackingRow` 映射，监控页面。
- Violation consequence：前后端字段语义不一致；早期请求被隐藏或错称为真人行为。
- 来源：`V141__create_mail_open_tracking.sql:1-15`、`MailOpenTrackingRepository.kt:78-160`。

### Invariant I-4：统计口径与历史不变
- Rule：统计分母仍是所选发送日期和账号内已关联跟踪的成功外发邮件；分子仅是其中 `last_open_at` 晚于 120 秒的邮件。状态/关键词仅影响列表与 `totalCount`，不影响汇总。未跟踪仍 `NOT_TRACKED`；失败、入站和孤儿跟踪行不进入记录及汇总。无需数据库迁移。
- Applies to：`readPage`、`detail`、既有 `mail_record` 写入路径。
- Violation consequence：打开率变化来源不清，或包含未发/未跟踪邮件。
- 来源：`MailOpenTrackingRepository.kt:88-129`；`K-failed-mail-record-has-null-sent-at` 已按代码复核。

### Invariant I-5：页面不宣称已读
- Rule：页面状态文案 `OPENED → 疑似打开（120秒后请求）`、`NO_SIGNAL → 无120秒后请求`、`NOT_TRACKED → 未跟踪`；指标文案为“120秒后请求”“120秒后请求率”；原始时间标为“首次图片请求”“最近图片请求”。说明文字逐字为“发送后120秒内的图片请求按预加载处理；之后的新请求仅表示疑似打开，不等于本人已读。图片可能被缓存，重复打开不一定产生新请求。指标按发送日期和发件账号统计；状态与搜索只影响列表。”
- Applies to：`index.html` 静态文案、`app.js` 动态表格/指标/详情文案。
- Violation consequence：UI 把代理加载展示成已确认真人打开。
- 来源：用户要求；`index.html:274-283`、`app.js:14265-14382`。

## 样式契约

本计划只改**现有元素的文字和既有 badge 色类**，不增删 DOM 节点、class、CSS 属性或 inline style。下列样式事实均来自 `src/main/resources/static/styles.css`；所有修改的 DOM 对应 S-1 或 S-2。

### S-1：静态说明、指标、筛选与表头
- 复用：`#monitoringOpenTracking.mot-root` (`styles.css:11816-11820`：`padding:0 24px 24px`，hidden 为 `display:none`)，说明段 `.muted` (`:3084-3087`：`color:var(--text-muted);font-size:12px`)，指标 `.card-grid` (`:2970-2976`：grid/160px/8px/12px)、`.metric-card` (`:2978-3023`：14px 16px 内边距、24px 数值、hover 上移 2px)，筛选 `.toolbar` (`:355-364`：flex、8px gap、10px 14px padding)，表格 `.table-wrap` (`:987-991`：水平滚动)。文案所在元素就地改文本，不改上述全局 class 或规则；因此这些全局 class 的其他使用点不用改。
- DOM 骨架：
  ```html
  <div id="motMetrics" class="card-grid" aria-live="polite"><div class="metric-card"><div class="metric-label">跟踪发出</div><div class="metric-value">—</div></div><div class="metric-card"><div class="metric-label">120秒后请求</div><div class="metric-value">—</div></div><div class="metric-card"><div class="metric-label">120秒后请求率</div><div class="metric-value">—</div></div></div>
  <p class="muted">发送后120秒内的图片请求按预加载处理；之后的新请求仅表示疑似打开，不等于本人已读。图片可能被缓存，重复打开不一定产生新请求。指标按发送日期和发件账号统计；状态与搜索只影响列表。</p>
  <label>状态 <select id="motStatus"><option value="ALL">全部</option><option value="OPENED">疑似打开（120秒后请求）</option><option value="NO_SIGNAL">无120秒后请求</option><option value="NOT_TRACKED">未跟踪</option></select></label>
  <div class="table-wrap"><table id="motTable"><thead><tr><th>发送时间</th><th>专家</th><th>收件邮箱</th><th>发件账号</th><th>主题</th><th>跟踪状态</th><th>首次图片请求</th><th>最近图片请求</th><th>操作</th></tr></thead><tbody></tbody></table></div>
  ```
- 禁止项：新增 class、inline style、改动 CSS、改变表格 9 列或 option value。

### S-2：动态 badge、指标与详情
- 复用：`.badge` (`styles.css:1054-1066`：inline-flex、2px 8px、11px、圆角999px)，`OPENED` 复用 `.badge.info` (`:1086-1091`：`var(--info-bg)`/`var(--info)`/`var(--info-border)`；`--info=#0ea5e9`，见 `:46-49`)，`NO_SIGNAL` 复用 `.badge.warn` (`:1074-1079`：`--warning=#d97706`)，`NOT_TRACKED` 仍基础 badge；详情 `.mot-detail` (`:11843-11849`：16px padding、10px 圆角、`#f8fafc` 背景)，`.mot-detail-grid` (`:11850-11862`：100px/minmax(0,1fr)、8px 16px gap)。均不改既有规则，其他使用点不变。
- DOM 骨架：表格状态 `<td><span class="badge info">疑似打开（120秒后请求）</span></td>`；动态指标继续用 `<div class="metric-card"><div class="metric-label">120秒后请求</div><div class="metric-value">…</div></div>`；详情继续 `<section id="motDetail" class="mot-detail"><div class="panel-head">…</div><dl class="mot-detail-grid"><dt>首次图片请求</dt><dd>…</dd><dt>最近图片请求</dt><dd>…</dd></dl></section>`；`NO_SIGNAL` 行使用 `<span class="badge warn">无120秒后请求</span>`。
- 禁止项：新增/改动 class、CSS、inline style；不把 `info` 改成代表确认成功的 `ok`。

## 现状审计

### `mail_open_tracking` 与 `batch_send_setting`
- Schema：`V141__create_mail_open_tracking.sql:1-15` 定义 `token CHAR(43) ascii_bin UNIQUE`、`recipient`、`created_at DATETIME(6)`、`first_open_at DATETIME(6)`、`last_open_at DATETIME(6)`；`mail_record.open_tracking_id` 唯一且外键。`V27__create_batch_send_setting.sql:1-6` 定义 `setting_key UNIQUE`，`setting_value` 字符串。无逐次请求表。
- 写路径（按生产代码 `rg -n 'mail_open_tracking|recordSignal\(|openTrackingId\s*=' src/main/kotlin src/main/resources/db/migration` 复核）：`MailOpenTrackingRepository.reserve:61-76` 插入 token；`recordSignal:78-85` 在开关为精确小写 `true` 时原子更新最早/最晚请求；`setEnabled:52-58` UPSERT 追踪开关。`MailOpenTrackingService.reserve:49-63` 调前者；`recordSignal:66-73` 调后者；`MailOpenTrackingController.pixel:44-47` 是 GET 入口，`pixelHead:50-51` 不写。`SmtpMailDeliveryService.kt:25-45,139` 生成像素、传出跟踪 id。迁移 V141 是建表，不得修改。
- 读路径：`MailOpenTrackingRepository.isEnabled:47-50`/`reserve:63` 读开关；`recordSignal:82-85` 原子校验开关；`readPage:88-104` 读列表/汇总；`detail:106-109` 读单封。`MailOpenTrackingService.readPage/detail:75-92` 和 `MailOpenTrackingController.records/detail:30-42` 暴露 API。`index.html:266-285`、`app.js:14265-14399` 展示。`K-batch-send-setting-kv` 已复核且发现旧条目“BatchSendSettingService.upsert 是唯一写入点”过时：跟踪开关也由本仓储直接 UPSERT；Phase 6 修正知识条目。
- 交互点：像素 GET 写 `last_open_at` → 列表/筛选/汇总/详情读；`batch_send_setting` 关闭 → 后续 GET 不更新 → 页面状态不变；SMTP 保留 token → `mail_record.open_tracking_id` → 查询 JOIN。

### `mail_record`
- Schema：`V1__create_business_tables.sql:97-115` 定义 `direction`、`send_status`、`sent_at DATETIME`；`V141:11-15` 加唯一 `open_tracking_id` 外键；`V15:10-14` 建发送时间相关索引。阈值用表内持久化 `sent_at`，**不是** token 创建时间或邮件接收时间。
- 写路径：`rg -n 'MailRecord\(|openTrackingId\s*=' src/main/kotlin/com/weibo/talentintroduction/{mail,campaign}` 回执如下：`ManualOutreachTxHelper.kt:60-79` 成功首次外发、`:112-132` 失败；`MeetingScheduleService.kt:145-163`；`ManualExpertMailService.kt:70-89`；`ManualReplySendAttemptService.kt:376-397,474-496`；`AutoMailReplyService.kt:419-420,760-761,1042-1048,1246-1247`。其中**显式传入 `openTrackingId`** 的生产点由 `rg -n 'openTrackingId\s*=' src/main/kotlin` 定位为 `ManualOutreachTxHelper.kt:79`、`MeetingScheduleService.kt:162`、`ManualExpertMailService.kt:88`；`InitialOutreachService.kt:114` 与 `ManualInitialOutreachService.kt:965` 是转交 `ManualOutreachTxHelper` 的调用点；`SmtpMailDeliveryService.kt:38,44,139` 为发送结果生产点。其余构造沿用 `MailRecord.openTrackingId = null` (`MailRecord.kt:42`)。本计划不改这些写点。（来源：`K-failed-mail-record-has-null-sent-at`，并由当前代码复核。）
- 读路径：本需求读取 `sent_at/open_tracking_id` 并推导打开状态的代码在 `MailOpenTrackingRepository.kt:88-160`；原有 `readPage` 要求 `OUTBOUND/SENT/sent_at != NULL` 并 JOIN 跟踪 id，`detail` 同门槛。`rg -n 'openTrackingId|open_tracking_id' src/main/kotlin` 的生产命中已在上段列出；本计划不改邮件正文/发送链。
- 交互点：所有成功发送写路径产生的 `sent_at` 与跟踪 id 被查询阈值消费；失败或未跟踪不能混入分母。

### 前端样式盘点
- 可复用 class 完整规则块：S-1、S-2 已给 `styles.css` 的逐行位置和完整属性事实；本计划无新增样式。颜色/字号/间距/圆角/hover 基准：`--primary=#1e40af`、`--info=#0ea5e9`、`--warning=#d97706`、`--radius-md=10px`、`--panel-border=rgba(15,23,42,.08)`、`.muted=12px`、`.badge=11px/2px 8px/999px`、`.metric-card .metric-value=24px`、`.metric-card:hover=translateY(-2px)`（`styles.css:1-100,1054-1079,2978-3023`）。
- DOM 结构约定：既有监控子页 `#monitoringOpenTracking > .toolbar/#motMetrics/.muted/.toolbar/.table-wrap/#motPagination/#motDetail`，动态列表和详情由 `app.js:14272-14383` 渲染。无新视图注册。
- 改动前基线（逐字摘自 `index.html:274-283` 与 `app.js:14265-14269,14334-14339,14376-14379`）：
  ```html
  <div id="motMetrics" class="card-grid" aria-live="polite"><div class="metric-card"><div class="metric-label">跟踪发出</div><div class="metric-value">—</div></div><div class="metric-card"><div class="metric-label">收到打开信号</div><div class="metric-value">—</div></div><div class="metric-card"><div class="metric-label">打开信号率</div><div class="metric-value">—</div></div></div>
  <p class="muted">打开信号表示图片被加载，不等于本人已读。指标按发送日期和发件账号统计；下方状态与搜索只影响列表。</p>
  <label>状态 <select id="motStatus"><option value="ALL">全部</option><option value="OPENED">已收到打开信号</option><option value="NO_SIGNAL">暂无打开信号</option><option value="NOT_TRACKED">未跟踪</option></select></label>
  <div class="table-wrap"><table id="motTable"><thead><tr><th>发送时间</th><th>专家</th><th>收件邮箱</th><th>发件账号</th><th>主题</th><th>跟踪状态</th><th>首次信号</th><th>最近信号</th><th>操作</th></tr></thead><tbody></tbody></table></div>
  ```
  ```js
  OPENED: ["已收到打开信号", "ok"],
  NO_SIGNAL: ["暂无打开信号", "warn"],
  ["收到打开信号", snapshot.summary.opened],
  ["打开信号率", snapshot.summary.trackedSent ? formatPercent(snapshot.summary.openSignalRate) : "—"]
  ["首次信号", row.firstOpenAt || "—"], ["最近信号", row.lastOpenAt || "—"]
  ```
- CSS 改动前基线：`.badge` / `.badge.info` / `.badge.warn` 逐字规则见 `styles.css:1054-1091`；`.card-grid` / `.metric-card` / `.metric-card .metric-label` / `.metric-card .metric-value` 见 `styles.css:2970-3023`；`.mot-root` / `.mot-detail-grid` 见 `styles.css:11816-11862`。本计划**不修改这些规则**，同名 class 的其他使用点不受影响。
- 本区域会复用的规则块逐字基线（均不改）：
  ```css
  .muted {
      color: var(--text-muted);
      font-size: 12px;
  }
  .badge {
      display: inline-flex;
      align-items: center;
      padding: 2px 8px;
      border-radius: 999px;
      font-size: 11px;
      font-weight: 600;
      font-family: var(--font-body);
      line-height: 1;
      background-color: var(--surface);
      color: var(--text-muted);
      border: 1px solid transparent;
  }
  .badge.info {
      background-color: var(--info-bg);
      color: var(--info);
      border-color: var(--info-border);
  }
  .badge.warn {
      background-color: var(--warning-bg);
      color: var(--warning);
      border-color: var(--warning-border);
  }
  .card-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
      gap: 8px;
      margin: 8px 0;
      padding: 12px;
  }
  .metric-card {
      border: 1px solid var(--panel-border);
      border-radius: var(--radius-md);
      padding: 14px 16px;
      background: linear-gradient(180deg, rgba(var(--primary-rgb), 0.035), rgba(var(--primary-rgb), 0) 70%), var(--panel-bg);
      position: relative;
      overflow: hidden;
      transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
  }
  .metric-card .metric-label {
      color: var(--text-muted);
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      margin-bottom: 6px;
      font-family: var(--font-body);
  }
  .metric-card .metric-value {
      color: var(--text-main);
      font-size: 24px;
      font-weight: 700;
      font-family: var(--font-mono);
      line-height: 1.1;
      letter-spacing: -0.5px;
  }
  .mot-root {
      padding: 0 24px 24px;
  }
  .mot-detail-grid {
      display: grid;
      grid-template-columns: 100px minmax(0, 1fr);
      gap: 8px 16px;
      margin: 0;
  }
  ```
- 缓存键回执：`rg -n -F '20260926-discovery-repair' src/test` 无匹配（exit 1）；`rg -n -F '20260926-discovery-repair' src/main/resources/static/index.html` 命中 `:11-15,:2310-2315` 共 11 项。执行时必须重查实际键再统一 bump，仍须验证 `src/test` 是否出现新的固定键。（来源：`K-frontend-cache-key-triad`。）
- SQL 函数仓库先例：`MailRecordRepository.kt:372,412` 已使用 MySQL `DATE_ADD(..., INTERVAL 7 DAY)`；本计划仅把同类时间比较用于秒级边界，实际边界正确性由 MySQL IT 验证，**不把未经运行的表达式写成已验证事实**。
- 本次试图补做线上只读 MySQL 边界查询时，自动审批拒绝了从运行进程读取 `DB_PASSWORD` 的操作，原因是凭据探查风险。未绕过审批，也未执行该查询；120 秒边界的验收依赖阶段 3 的隔离 MySQL IT。

## 实现方案

### 阶段 1：后端查询口径（I-1～I-4）
1. 修改 `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepository.kt`：定义单一私有 SQL 截止表达式，例如 `DATE_ADD(m.sent_at, INTERVAL 120 SECOND)`，以及候选表达式 `t.last_open_at > <截止表达式>`。`SELECT` 输出 `CASE WHEN <候选> THEN 1 ELSE 0 END AS qualified_signal`，`row()` 读此列定 `OPENED`，避免 Kotlin 自行计算另一套时间口径。
2. `OPENED` 列表条件用同一候选表达式；`NO_SIGNAL` 明确覆盖 `t.id IS NOT NULL AND (t.last_open_at IS NULL OR t.last_open_at <= <截止表达式>)`；`NOT_TRACKED` 及发送范围不变。汇总 `opened` 改 `SUM(CASE WHEN <候选> THEN 1 ELSE 0 END)`。`detail` 沿用同一个 `SELECT` 和 `row()`。`firstOpenAt/lastOpenAt` 原样映射，不增字段、不改 `recordSignal`。
3. 修改 `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepositoryIT.kt`：在现有 MySQL 集成用例上加边界 fixture：无请求、119.999999 秒、恰 120 秒、120.000001 秒、先 10 秒后 121 秒、未跟踪、失败/入站/孤儿。逐一断言 ALL/OPENED/NO_SIGNAL/NOT_TRACKED、`totalCount`、汇总分子分母/比率、详情与原始时间；验证只过时不新增请求时仍 NO_SIGNAL，后续 GET 写入后变 OPENED；保留现有并发最早/最晚写入测试。此测试必须实际连接 MySQL 跑，默认 Maven 测试会因 `@EnabledIfSystemProperty(mysqlIt=true)` 跳过，不能把“编译通过”当作 SQL 边界验证。

### 阶段 2：页面文案（I-3、I-5；S-1、S-2）
4. 修改 `src/main/resources/static/index.html` 的 `:274-283`：按 S-1 逐字替换静态指标、说明、筛选项、表头文字；保留 id、option value、9 列和布局。统一 bump 11 项资源缓存键；执行时从当前 `styles.css?v=` 读键并 `rg -n -F '<当前键>' src/test`，若出现新的字面量测试，先停在本计划范围内核对文件数上限，不擅自扩文件。
5. 修改 `src/main/resources/static/app.js` 的 `:14265-14382`：`OPENED` 改为 S-2 的 `badge info` 与准确文案；`NO_SIGNAL` 改准确文案；两项动态指标和详情两个时间字段标题与 S-1 保持逐字一致。保留 API 字段名、参数值、转义、详情请求竞态控制。
6. 修改 `src/test/js/mailOpenTracking.test.js`：测试框架中的 `openTrackingStatusLabels` 固定值同步实际文案/色类；断言初始 HTML、动态指标、列表 badge、详情标题与说明逐字匹配，同时保留既有竞态、分页、转义、配置测试。（来源：`K-js-test-invocation-surface`，单跑 `node --test src/test/js/mailOpenTracking.test.js`。）

### 阶段 3：验证（I-1～I-5；S-1、S-2）
7. 在有 Docker/MySQL 测试环境运行 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailOpenTrackingRepositoryIT -DmysqlIt=true`；再跑 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailOpenTrackingServiceTest,MailOpenTrackingControllerTest,SmtpMailDeliveryServiceTest`、`node --test src/test/js/mailOpenTracking.test.js`、`node --check src/main/resources/static/app.js`，最后按需要跑全量 `mvn test`。验证 `index.html` 的 11 项缓存键一致、旧键在引用处不残留；确认 `git diff` 无邮件发送/像素写入/迁移/CSS 改动。本次计划阶段本机 `docker info` 返回对 OrbStack socket 的 `permission denied`，所以执行阶段须先确认可用 Docker；若不可用，明确记录 MySQL IT 未验证，不声称 SQL 已通过。

## 变更文件清单

| 文件 | 改动 |
| --- | --- |
| `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepository.kt` | 共享 120 秒谓词及列表/汇总/详情状态 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepositoryIT.kt` | MySQL 边界和跨路径测试 |
| `src/main/resources/static/index.html` | 静态文案、缓存键 |
| `src/main/resources/static/app.js` | 动态文案和 badge 色类 |
| `src/test/js/mailOpenTracking.test.js` | 页面文案/结构测试 |

执行变更共 **5 文件、2 个子系统（后端查询、监控 UI）**；不含本计划及知识更新。代码改动以此清单为边界。

## 验收标准

- I-1：MySQL IT 对 `sent_at +119.999999s`、`+120.000000s`、`+120.000001s` 的状态分别断言 `NO_SIGNAL`、`NO_SIGNAL`、`OPENED`；`last_open_at` NULL 与早于 `sent_at` 均为 `NO_SIGNAL`。`OPENED` 筛选结果、列表状态、详情状态与 summary 分子完全一致。
- I-2：同一行仅写早期请求，再查询时间自然流逝后仍 `NO_SIGNAL`；真正再次调用 `recordSignal` 写入晚期时间后变 `OPENED`。现有 HEAD 不写、关闭开关不写、无效 token 不写的测试继续通过。
- I-3：先早后晚的行保持 `firstOpenAt=早期原值`、`lastOpenAt=晚期原值`；仅早期的行可以 `NO_SIGNAL` 但仍展示首次/最近请求。数据库列不变。
- I-4：状态/关键词变化前后 summary 不变；分母排除未跟踪/失败/入站/孤儿，日期和账号过滤仍生效；比率为分子/分母，分母零时 `null`。
- I-5：`index.html`、`app.js` 不再出现“已收到打开信号”“打开信号率”“首次信号”“最近信号”；既有 API 字段名仍 `OPENED/NO_SIGNAL/NOT_TRACKED`、`firstOpenAt/lastOpenAt`。测试断言页面新文案和 9 列结构。说明必须写明“疑似”和图片缓存限制。
- S-1：对比 `index.html` 的 `#motMetrics`、说明、`#motStatus`、表头与 S-1 逐字相同；未新增/删除元素、class、inline style；11 项版本键一致且执行时按实际键反查测试。
- S-2：`app.js` 渲染的 badge 类及文字、指标标签、详情 `dt` 与 S-2 一致；`styles.css` 零差异；`node --test src/test/js/mailOpenTracking.test.js` 通过。
- 集成：`GET /t/mail-open/{token}.gif` 的原始记录 → `GET /api/mail-open-tracking/records` 的列表/过滤/汇总 → `GET /api/mail-open-tracking/records/{id}` 的详情形成一致链路；不开启跟踪、HEAD、失败发送的旧行为不变。

## 人工验收清单

在**独立、干净的验收库**操作，禁止在生产库插入 fixture。先通过后台创建一个测试专家联系人，然后运行下列完整 fixture SQL；如果 `@cid` 为 NULL，先完成该前置条件。数据仅模拟已发送记录，不会调用真实 SMTP。账号行的必填列来自 `V1__create_business_tables.sql:1-24`，跟踪和记录行的必填列来自 V141/V1。打开监控页时选 **2026-09-28** 和发件账号 `tracking-acceptance`，避免其他邮件干扰指标。

```sql
SET @cid = (SELECT id FROM expert_contact ORDER BY id DESC LIMIT 1);
SET @sent = CAST('2026-09-28 10:00:00' AS DATETIME);
INSERT INTO mail_sender_account
  (account_code,sender_email,sender_name,smtp_host,smtp_port,smtp_username,smtp_password,imap_host,imap_port,imap_username,imap_password)
VALUES
  ('tracking-acceptance','tracking@example.test','Tracking Test','smtp.test',465,'tracking@example.test','test','imap.test',993,'tracking@example.test','test');
INSERT INTO mail_open_tracking (token,recipient,created_at,first_open_at,last_open_at) VALUES
  (REPEAT('A',43),'a@example.test',@sent,DATE_ADD(@sent,INTERVAL 119999999 MICROSECOND),DATE_ADD(@sent,INTERVAL 119999999 MICROSECOND)),
  (REPEAT('B',43),'b@example.test',@sent,DATE_ADD(@sent,INTERVAL 120 SECOND),DATE_ADD(@sent,INTERVAL 120 SECOND)),
  (REPEAT('C',43),'c@example.test',@sent,DATE_ADD(@sent,INTERVAL 120000001 MICROSECOND),DATE_ADD(@sent,INTERVAL 120000001 MICROSECOND)),
  (REPEAT('D',43),'d@example.test',@sent,DATE_ADD(@sent,INTERVAL 10 SECOND),DATE_ADD(@sent,INTERVAL 10 SECOND));
INSERT INTO mail_record
  (expert_contact_id,direction,mail_type,sender_account_code,subject,send_status,sent_at,open_tracking_id)
VALUES
  (@cid,'OUTBOUND','INTRODUCTION','tracking-acceptance','TRACK120-EARLY','SENT',@sent,(SELECT id FROM mail_open_tracking WHERE token=REPEAT('A',43))),
  (@cid,'OUTBOUND','INTRODUCTION','tracking-acceptance','TRACK120-EXACT','SENT',@sent,(SELECT id FROM mail_open_tracking WHERE token=REPEAT('B',43))),
  (@cid,'OUTBOUND','INTRODUCTION','tracking-acceptance','TRACK120-BOUNDARY','SENT',@sent,(SELECT id FROM mail_open_tracking WHERE token=REPEAT('C',43))),
  (@cid,'OUTBOUND','INTRODUCTION','tracking-acceptance','TRACK120-LATE','SENT',@sent,(SELECT id FROM mail_open_tracking WHERE token=REPEAT('D',43))),
  (@cid,'OUTBOUND','INTRODUCTION','tracking-acceptance','TRACK120-UNTRACKED','SENT',@sent,NULL);
```

执行后确认 `SELECT @cid;` 非 NULL，且 `SELECT COUNT(*) FROM mail_record WHERE subject LIKE 'TRACK120-%';` 返回 **5**。若测试库已有这些 token/主题，换干净的验收库，不覆盖旧数据。

### A-1：早期或恰好 120 秒的请求
- 前置条件：已运行公共 fixture；`TRACK120-EARLY` 与 `TRACK120-EXACT` 已有原始请求时间。
- 操作步骤：1. 在“监控→打开跟踪”选 2026-09-28 和 `tracking-acceptance`，状态“全部”，分别搜索 `TRACK120-EARLY`、`TRACK120-EXACT`。2. 查看各行状态与时间。3. 分别在“疑似打开（120秒后请求）”和“无120秒后请求”筛选下搜索这两个主题。
- 预期结果：两行均为“无120秒后请求”，首次/最近图片请求仍分别显示其原始时间；`OPENED` 筛选无这两行，`NO_SIGNAL` 筛选有两行。静置 120 秒刷新仍不变。
- 覆盖：I-1、I-2、I-3、S-1、S-2。

### A-2：新请求跨过阈值
- 前置条件：已运行公共 fixture；`TRACK120-LATE` 初始请求在 10 秒，`TRACK120-BOUNDARY` 在 120.000001 秒。
- 操作步骤：1. 查询两行；2. 对 `TRACK120-LATE` 的跟踪行把 `last_open_at` 更新为 `DATE_ADD(@sent, INTERVAL 121 SECOND)`（模拟后续像素 GET，**不改 `first_open_at`**）；3. 刷新列表并点详情。
- 预期结果：两行都为“疑似打开（120秒后请求）”；`TRACK120-LATE` 详情“首次图片请求”仍是 10 秒，“最近图片请求”为 121 秒；`TRACK120-BOUNDARY` 在 120.000001 秒即计入。筛选 `OPENED` 包含两行。
- 覆盖：I-1、I-2、I-3、S-2；跟踪时间写入→读出交互点。

### A-3：汇总、筛选与未跟踪
- 前置条件：已运行公共 fixture 和 A-2 的更新；此项须在 A-4 插入新跟踪邮件之前执行。验收日期与 `tracking-acceptance` 账号范围内只有公共 fixture 的 5 封邮件。
- 操作步骤：1. 选 2026-09-28 和测试账号、状态“全部”；2. 看指标；3. 切换“无120秒后请求”、搜索 `TRACK120-EARLY`，再看指标；4. 切换“未跟踪”。
- 预期结果：“跟踪发出”=4、“120秒后请求”=2、“120秒后请求率”=50.0%；状态/关键词改变后指标仍为这三个值；未跟踪筛选显示 `TRACK120-UNTRACKED` 且其原始请求时间为“—”。
- 覆盖：I-4、I-5、S-1、S-2；发送记录/关联跟踪行→汇总交互点。

### A-4：真实像素 GET 与 HEAD 回归
- 前置条件：已运行公共 fixture；跟踪开关开启。执行如下 SQL 创建专用记录，确认当前时间晚于 `@sent+120秒`，URL 使用验收环境实际 context path：
  ```sql
  INSERT INTO mail_open_tracking(token,recipient,created_at) VALUES (REPEAT('G',43),'g@example.test',@sent);
  INSERT INTO mail_record(expert_contact_id,direction,mail_type,sender_account_code,subject,send_status,sent_at,open_tracking_id)
  VALUES (@cid,'OUTBOUND','INTRODUCTION','tracking-acceptance','TRACK120-GET','SENT',@sent,(SELECT id FROM mail_open_tracking WHERE token=REPEAT('G',43)));
  ```
- 操作步骤：1. 先在列表查询，确认“无120秒后请求”；2. 对该 token 的 `/t/mail-open/{token}.gif` 发 HEAD，刷新；3. 发 GET，刷新并查看详情；4. 关闭跟踪开关，再 GET 一次并刷新。
- 预期结果：HEAD 后仍“无120秒后请求”，两个时间仍为“—”；首次 GET 后变“疑似打开（120秒后请求）”且首次/最近图片请求出现时间；关闭后再 GET 不改变最近图片请求。GET/HEAD 均返回 GIF。
- 覆盖：I-2、I-3、I-4；像素写入→列表/详情跨路径交互点；开关回归。

### A-5：失败、入站与页面样式回归
- 前置条件：已运行公共 fixture；在验收库运行以下 SQL：
  ```sql
  INSERT INTO mail_open_tracking(token,recipient,created_at,first_open_at,last_open_at) VALUES
    (REPEAT('H',43),'h@example.test',@sent,DATE_ADD(@sent,INTERVAL 121 SECOND),DATE_ADD(@sent,INTERVAL 121 SECOND)),
    (REPEAT('I',43),'i@example.test',@sent,DATE_ADD(@sent,INTERVAL 121 SECOND),DATE_ADD(@sent,INTERVAL 121 SECOND));
  INSERT INTO mail_record(expert_contact_id,direction,mail_type,sender_account_code,subject,send_status,sent_at,open_tracking_id) VALUES
    (@cid,'OUTBOUND','INTRODUCTION','tracking-acceptance','TRACK120-FAILED','FAILED',NULL,(SELECT id FROM mail_open_tracking WHERE token=REPEAT('H',43))),
    (@cid,'INBOUND','INTRODUCTION','tracking-acceptance','TRACK120-INBOUND','SENT',@sent,(SELECT id FROM mail_open_tracking WHERE token=REPEAT('I',43)));
  ```
- 操作步骤：1. 在同一天、同账号打开跟踪页并搜索这些邮件主题；2. 查看列表、指标、筛选；3. 对照旧页面的指标卡、badge、详情格线与分页外观。
- 预期结果：失败/入站邮件均不出现在列表和分母；监控页仍为 3 张指标卡、9 列表格、原有蓝色信息 badge/琥珀无信号 badge、10px 指标卡圆角、详情 100px 标签列；页面说明完整出现 I-5 的原句，未出现“已收到打开信号”。
- 覆盖：I-4、I-5、S-1、S-2；失败/入站排除与样式回归。

### A-6：分页、关键词与发送日期回归
- 前置条件：独立验收库已运行公共 fixture；执行下面 SQL，仅生成未跟踪的测试记录，不发送邮件：
  ```sql
  INSERT INTO mail_record(expert_contact_id,direction,mail_type,sender_account_code,subject,send_status,sent_at)
  SELECT @cid,'OUTBOUND','INTRODUCTION','tracking-acceptance',CONCAT('TRACK120-PAGE-',tens.n*10+ones.n),'SENT',@sent
  FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2) tens
  WHERE tens.n*10+ones.n < 21;
  INSERT INTO mail_record(expert_contact_id,direction,mail_type,sender_account_code,subject,send_status,sent_at)
  VALUES (@cid,'OUTBOUND','INTRODUCTION','tracking-acceptance','TRACK120-OTHER-DAY','SENT',DATE_SUB(@sent,INTERVAL 1 DAY));
  ```
- 操作步骤：1. 选日期 2026-09-28、账号 `tracking-acceptance`、状态“全部”，搜索 `TRACK120-PAGE-`；2. 点击“下一页”再“上一页”；3. 清空搜索并改日期为 2026-09-27，搜索 `TRACK120-OTHER-DAY`。
- 预期结果：第一步显示“共 21 条”，第 1 页 20 行、第 2 页 1 行，翻页后搜索条件不丢；2026-09-27 查询可见 `TRACK120-OTHER-DAY`，2026-09-28 查询不可见它；这 22 封未跟踪邮件不改变当时的已跟踪分母/晚期请求分子。若按 A-1→A-6 顺序执行，2026-09-28 的分母为 5、分子为 3（A-4 已新增一封并触发晚期 GET）。
- 覆盖：I-4；既有日期/账号/关键词/分页路径回归。
