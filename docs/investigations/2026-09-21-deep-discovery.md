# 深度发现低产出排查

排查时间：2026-09-21，Asia/Shanghai。目标服务器：150.158.92.103。
范围：只读 SSH、MySQL 查询、日志分析、少量外部检索请求。未修改生产配置、数据或代码，未触发发现任务或邮件发送。

## 结论

“通过 1 人”对应 **9 月 21 日 02:00–02:40 的任务 18602**；9 月 20 日任务 18435 实际为 0 人。
主因是外部连接故障触发分页进度丢失，恢复后重复抓取已有专家。当前漏斗的资格淘汰数为 0，无证据支持通过放宽资格门槛解决此问题。

## 生产证据

| 日期 | 任务 ID | 论文 | 新收录 / 晋升 | 关键情况 |
|---|---:|---:|---:|---|
| 09-15 | 17577 | 2600 | 411 / 411 | 新增均来自 OpenAlex |
| 09-16 | 17748 | 2600 | 451 / 451 | 新增均来自 OpenAlex |
| 09-17 | 17917 | 2600 | 391 / 391 | 新增均来自 OpenAlex |
| 09-18 | 18086 | 2600 | 368 / 368 | 新增均来自 OpenAlex |
| 09-19 | 18261 | 0 | 0 / 0 | TLS 握手失败，却记录 SUCCESS |
| 09-20 | 18435 | 0 | 0 / 0 | TLS 握手失败，却记录 SUCCESS |
| 09-21 | 18602 | 2600 | 1 / 1 | 有效邮箱 1148，重复 1147 |

9 月 21 日来源漏斗：

| 来源 | 论文 | 有效邮箱 | 重复 | 新增 / 晋升 | 问题 |
|---|---:|---:|---:|---:|---|
| OpenAlex | 2500 | 996 | 995 | 1 / 1 | 丢失进度后从头扫描 |
| Crossref | 0 | 0 | 0 | 0 / 0 | 参数重复编码，HTTP 400 |
| CORE | 100 | 152 | 152 | 0 / 0 | 无有效下一页游标，重复首批 |
| arXiv | 0 | 0 | 0 | 0 / 0 | HTTP→HTTPS 301 空响应 |
| ORCID | 0 | 0 | 0 | 0 / 0 | 定时任务关键词为空，代码直接返回 |

数据库证据来自 `task_execution`、`task_progress_log`、`discovery_source_cursor`；日志来自 `/opt/apache-tomcat-9.0.71/logs/catalina.out`。
线上 ExpertDiscoveryService、ArxivDataSource、CrossrefDataSource、CoreDataSource 四个 class 的 SHA-256 与本地 target/classes 一致。

## 已确认原因

### 1. 搜索失败被当成结束，覆盖原分页游标

9 月 19 日 02:00:00 日志显示 OpenAlex 读取了原游标；02:00:01 搜索报 `SSLHandshakeException: Remote host terminated the handshake`；随后保存 `cursor=null(已穷尽)`。9 月 20 日再次失败；9 月 21 日从第一页重新抓取。

`ExpertDiscoveryService.discoverFromSource()` 把 `lastNextCursor` 初始化为 null，首请求异常时直接 break，最终返回 null；上层无条件 `saveSourceCursor()`，覆盖旧值。TLS 异常没有搜索重试。故障当时 OpenAlex、Crossref、CORE 均经 mihomo 同一代理节点访问并出现握手失败；这是已确认的连接故障，未进一步断言是节点、远端还是 TLS 中间链路的责任。

当前 OpenAlex API 只读探测已返回 200，但数据库保存的是重头扫描后的游标。仅恢复网络不能自动恢复故障前的扫描位置。累计论文数是处理次数，含重扫，不能当作唯一论文数或直接据此跳页。

### 2. Crossref 查询重复 URL 编码

`CrossrefDataSource.kt:57` 先 URLEncoder 编码 filter，再以 String 传给 RestTemplate。线上错误正文显示远端收到字面 `%3A`、`%2C`，无法识别过滤条件。

同服务器对照：一次编码 HTTP 200、返回论文；重复编码 HTTP 400，错误与生产一致。Crossref 官方规定过滤器用冒号、逗号分隔：[官方过滤语法](https://www.crossref.org/documentation/retrieve-metadata/rest-api/rest-api-filters/)。这是请求构造问题。

### 3. arXiv 的 HTTP 入口变为空 301

配置 `ARXIV_BASE_URL` 默认 `http://export.arxiv.org/api`。用服务器 Java 11 和部署的 Spring 库实测同一查询：

```text
ARXIV_HTTP status=301 length=0 location=https://export.arxiv.org/api/...
ARXIV_HTTPS status=200 hasEntry=true
```

当前解析器把空响应当零结果，未记录异常。另以 HTTPS 请求 100 篇，返回 100 篇且 published 字段均存在。可通过 HTTPS 恢复入口；仍需跑邮箱提取和去重，才能衡量实际新增量。[arXiv API 手册](https://info.arxiv.org/help/api/user-manual.html)。

### 4. CORE 分页契约与实际响应不符

生产连续多日只处理首批 100 篇，未达到本源 500 篇上限。请求 `scroll:true` 的实际响应字段为 `totalHits, limit, offset, results, searchId`，没有代码读取的 `scrollId`，因此 `nextCursor=null`。CORE 又被显式排除在跨任务游标持久化之外，下一天重新搜索。

只读请求使用应用风格请求头后返回 200；Python 默认请求头曾收到 403，不据此判断生产不可用。实际修复应验证 offset/searchId 或当前支持的分页协议，并按稳定条件分片推进；不能直接将 searchId 当成 scrollId 替换。

### 5. 任务状态漏记搜索失败

`DiscoveryResult.taskFailureCount` 只统计邮箱拒绝、资格拒绝、写入/晋升/去重错误，未包含 source.failureReasons 的搜索失败。因此 9 月 19–20 日多源搜索失败、零论文仍写 SUCCESS。应区分失败、正常无结果和未参与的数据源。

## 优化优先级与收益边界

1. **先恢复稳定增量**：搜索失败保留原进度，加入有界重试与故障状态；从备份或可验证历史检查点恢复 OpenAlex 扫描位置。历史基线为每日 368–451 人，可作为恢复对照，不能承诺未来同量。
2. **修复闲置入口**：Crossref 单次编码、arXiv HTTPS、CORE 正确分页及跨日稳定分片。各源仍须保持研发方向与地域筛选口径；Crossref/CORE 目前未接入与 OpenAlex 等效的研发学科约束。
3. **提高全文获取率**：本次 OpenAlex 2500 篇只有 640 篇获全文；919 次下载失败、667 篇无 PMC ID 且无可用首选 PDF。当前只取 best_oa_location.pdf_url；可评估其他 OA locations、机构仓储、DOI/Unpaywall 合法开放版本回退。先记录 HTTP 状态/域名分布，再决定优化，不盲目加重试。
4. **有条件扩量**：先修复重复扫描，再评估提高 OpenAlex 2500 篇上限或按学科/年份分片；需配合 API 限流和完成时长观察。增加并发无法解决游标、编码、跳转和分页错误。
5. **ORCID 作为补充**：现定时任务无关键词，根本不发搜索请求；适合接入明确领域/机构种子。公开邮箱覆盖与新增率需实测，不预估人数。

EuropePMC、PMC OA 因 RND_TARGET 学科范围被明确排除，本次不是连接故障。恢复它们会改变业务范围，不作为默认增量措施。

本报告为诊断结果与优化建议，未执行生产修复，也未声称已获得额外专家。
