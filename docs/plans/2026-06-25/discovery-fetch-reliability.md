# 深度发现：全文/PDF 抓取可靠性（重试退避 + HTML 落地页兜底）

> 计划日期：2026-06-25
> 目标：减少因瞬时网络错误与"PDF 链接其实是 HTML 落地页"造成的可恢复失败。

## 需求描述

**可观测产出**
1. 全文/PDF 抓取遇到瞬时错误（连接/读超时、429、503）时按退避重试若干次，真失败率下降。
2. Unpaywall 给出的"PDF 链接"实为 HTML 落地页时，走 HTML 文本兜底抽取邮箱，而非一律记 `PDF_DOWNLOAD_FAILED`。

**不可改变**
- 搜索阶段已有的限流重试+熔断逻辑（`discoverFromSource` :338-350）不变。
- 失败原因语义与计数（依赖 `discovery-failure-reason-logging.md` 已落地的 `FULLTEXT_FETCH_FAILED` 区分）。
- 共享 `restTemplate`（ES 等使用）行为绝不改动。

**不在本次范围**
- 批次号乱序、调度幂等、抓取效率（EPMC 复用/并行）。
- PmcOa 的全文抓取重试（其用共享 `restTemplate`；纳入需另给专用模板，本计划不动共享模板，见 I-3）。

---

## 关键不变量

### Invariant I-1: 仅对可重试错误退避重试，且有上限
- 规则：重试仅针对 `IOException`/`SocketTimeoutException`/HTTP 429/503；最多 `maxRetries` 次（默认 2），指数退避（如 500ms、1000ms）。4xx（除 429）等不可重试错误立即失败，不重试。
- 适用写路径：挂在 `europePmcRestTemplate`、`pdfDownloadRestTemplate` 上的重试拦截器。
- 违反后果：对永久错误盲目重试会拖慢任务、放大配额消耗。

### Invariant I-2: PDF 抓取对 HTML 落地页走文本兜底
- 规则：`PdfEmailExtractor` 下载内容非 PDF（content-type 非 pdf 且无 PDF 魔数）但为 HTML 时，提取 HTML 可见文本→`PlainTextEmailExtractor` 抽邮箱；有邮箱则成功，无则 `failureReason=NO_EMAIL_IN_HTML`；仅当既非 PDF 也非 HTML（或体积超限/网络失败）才记 `PDF_DOWNLOAD_FAILED`/`PDF_TOO_LARGE`。
- 适用写路径：`PdfEmailExtractor.downloadWithStreamLimit` / `extract`。
- 违反后果：大量本可抽到邮箱的 OA 落地页被误判为下载失败。

### Invariant I-3: 重试拦截器隔离，绝不影响共享 RestTemplate
- 规则：重试拦截器只注册到 `europePmcRestTemplate` 与 `pdfDownloadRestTemplate` 两个专用 Bean，不得加到 `restTemplate()`（:37，ES/Unpaywall/OpenAlex/Crossref/PmcOa 共享）。
- 适用写路径：`RestTemplateConfig`。
- 违反后果：给 ES 调用引入意外重试/延迟，影响事务与一致性。

---

## 现状审计

### RestTemplate 配置：`RestTemplateConfig.kt`
- `restTemplate()`（:37）：裸 `RestTemplate()`，被 `PmcOaDataSource`、`UnpaywallClient`、`OpenAlexDataSource`、`CrossrefDataSource`、ES 调用等共享。**不可改。**
- `europePmcRestTemplate`（:39-48）：仅设超时，**无重试拦截器**。用于 EPMC search 与 `fetchFullTextXml`。
- `pdfDownloadRestTemplate`（:50-59）：仅设超时，无重试。用于 `PdfEmailExtractor`。

### 全文/PDF 抓取路径
- `EuropePmcDataSource.fetchFullTextXml`（:62-77）：一次性 `getForObject`，异常→null（修复后映射 `FULLTEXT_FETCH_FAILED`）。**无重试。**
- `PdfEmailExtractor.extract`/`downloadWithStreamLimit`（:28-102）：流式下载，限大小（`maxPdfSizeBytes`）；`!isPdfContentType && !hasMagic` → 抛 `Not a PDF`（:96-98）→ 调用方记 `PDF_DOWNLOAD_FAILED`（:50-52）。**无重试、无 HTML 兜底。**
- `CrossrefDataSource.extractAuthorEmails`（:76-89）：`unpaywallClient.findPdfUrl(doi)` 拿 `url_for_pdf` → `pdfEmailExtractor.extract`。Unpaywall 的 `url_for_pdf` 常指向 HTML 落地页 → 命中上面误判。
- `PlainTextEmailExtractor`（已存在）：从纯文本抽邮箱+作者关联，可复用于 HTML 文本。

### 配置项
- `PdfExtractionProperties`：`maxPdfSizeBytes/downloadTimeoutMs/maxPages/blacklistPrefixes`，**无重试/HTML 配置**。
- `EuropePmcProperties`：`connectTimeoutMs/readTimeoutMs`，无重试。

### 交互点
- IP-1：重试拦截器 ↔ 两个专用模板 ↔ 调用方异常处理（拦截器耗尽后仍抛原异常，`fetchFullTextXml`/`extract` 的 catch 不变，失败原因语义保持）。
- IP-2：HTML 兜底 ↔ `failureReason` 词表（新增 `NO_EMAIL_IN_HTML`）↔ `processPaper`（:599-609 的 else 分支已能容纳新原因，仅进 failureReasons map，不加 fulltextObtained —— 与现状一致，无需改 processPaper）。

---

## 实现方案

### 阶段 1：退避重试（I-1、I-3）

**Task 1.1** 新建 `config/RetryingClientHttpRequestInterceptor.kt`：实现 `ClientHttpRequestInterceptor`，对 `IOException`/读超时与响应码 429/503 重试，参数 `maxRetries`、`initialBackoffMs`，指数退避；其它响应/异常透传。

**Task 1.2** `RestTemplateConfig.kt`：给 `europePmcRestTemplate`、`pdfDownloadRestTemplate` 两个 Bean `.additionalInterceptors(retrying...)`（或 `interceptors = listOf(...)`）。**不**动 `restTemplate()`（I-3）。重试参数来源：分别复用/扩展 `EuropePmcProperties`、`PdfExtractionProperties` 新增字段（见 1.3）。

**Task 1.3** 配置项：
- `EuropePmcProperties` 增 `maxRetries: Int = 2`、`retryBackoffMs: Long = 500`。
- `PdfExtractionProperties` 增 `maxRetries: Int = 2`、`retryBackoffMs: Long = 500`。
- `application.yml` 对应默认值（可选，有默认值即可）。

### 阶段 2：HTML 落地页兜底（I-2）

**Task 2.1** `PdfEmailExtractor.kt`：改 `downloadWithStreamLimit` 返回"内容+类型判定"，或在 `extract` 中：当下载到非 PDF 字节但 content-type 为 `text/html`（或起始为 `<!doctype html`/`<html`）时，调用新私有 `extractFromHtml(bytes, knownAuthors)`：用 jsoup（若已在依赖）或简单标签剥离取可见文本 → `plainTextExtractor.extract(...)` → `associateEmailsWithAuthors`。
- 有邮箱：`EmailExtractionOutcome(emails, "HTML_FALLBACK", null, httpRequests=1)`。
- 无邮箱：`failureReason="NO_EMAIL_IN_HTML"`。
- 既非 PDF 也非 HTML：维持 `PDF_DOWNLOAD_FAILED`。
- 先检查 `org.jsoup` 是否在 `pom.xml`；不在则用无依赖的正则/简单剥离实现（不引新依赖，避免扩大范围）。

**Task 2.2** `PdfExtractionProperties` 增 `htmlFallbackEnabled: Boolean = true`，`extract` 按开关启用兜底。

---

## 变更文件清单

| # | 文件 | 改动 | 不变量 |
|---|------|------|--------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/config/RetryingClientHttpRequestInterceptor.kt` | 新增重试拦截器 | I-1 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/config/RestTemplateConfig.kt` | 两个专用模板挂拦截器 | I-1/I-3 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/config/EuropePmcProperties.kt` | 加 maxRetries/retryBackoffMs | I-1 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/config/PdfExtractionProperties.kt` | 加重试 + htmlFallbackEnabled | I-1/I-2 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` | HTML 落地页兜底 | I-2 |
| 6 | `src/main/resources/application.yml` | 重试/兜底默认值（可选） | I-1/I-2 |

文件数 6（≤10 ✅）；子系统 2（重试 / HTML 兜底，≤2 ✅）；新增共享存储字段 0。

---

## 验收标准

- **I-1**：单测拦截器——mock 连续 429 两次后 200，断言最终成功且重试 2 次、退避被调用；mock 404，断言不重试直接返回。
- **I-3**：断言 `restTemplate()` Bean 的 interceptors 为空；仅 `europePmcRestTemplate`/`pdfDownloadRestTemplate` 含重试拦截器。
- **I-2**：单测 `PdfEmailExtractor`——喂 `Content-Type: text/html` 且含邮箱的 HTML → 返回 emails 且 `methodUsed=HTML_FALLBACK`；HTML 无邮箱 → `NO_EMAIL_IN_HTML`；真 PDF → 走原 PDF 路径；非 HTML 非 PDF → `PDF_DOWNLOAD_FAILED`。
- **集成**：跑一次启用 CROSSREF 的发现，确认此前大量 `PDF_DOWNLOAD_FAILED` 中一部分转为成功或 `NO_EMAIL_IN_HTML`（在失败原因明细可见）。
- **回归**：`mvn clean package` + `mvn test` 通过（JDK 11）；无新增第三方依赖（除非 jsoup 已存在）。

## 自检清单
- [x] 每个新行为有不变量（重试 I-1、兜底 I-2、隔离 I-3）
- [x] 现状审计列全 RestTemplate 共享关系（确认不碰共享模板）
- [x] 文件数 6 ≤ 10；子系统 2 ≤ 2；新增共享字段 0
- [x] 任务标注不变量编号
- [x] 验收每条不变量有检查
- [x] 文件逐一具名；新增依赖有前置检查（jsoup）
- [x] 明确推迟 PmcOa 重试/其它问题域
- [x] 依赖前置：需先合入失败原因计划（FULLTEXT_FETCH_FAILED 区分），否则 I-2 的原因展示不完整
- [x] 保存至 docs/plans/2026-06-25/

## 修正记录

| 日期 | 来源 | 修正 |
|---|---|---|
| 2026-06-25 | fix-v `docs/plans/fix/discovery-fetch-reliability.md/fix-1.md` | I-1 的“读超时重试”不能只依赖 `ClientHttpRequestInterceptor`；响应体读取/转换阶段需要在 `EuropePmcDataSource.fetchFullTextXml` 与 `PdfEmailExtractor.downloadWithStreamLimit` 调用边界增加显式重试。 |
