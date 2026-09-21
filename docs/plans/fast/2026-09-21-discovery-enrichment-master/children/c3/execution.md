# Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/2026-09-21/03-crossref-arxiv.md
Plan SHA-256: 3fbb6068a0c03c08b8c5091fcf31808161e8127128c31c635d220d32c0abb76b
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/2026-09-21/03-crossref-arxiv.md@3fbb6068a0c03c08b8c5091fcf31808161e8127128c31c635d220d32c0abb76b
Execution epoch: NEW
Approval basis: child brief `docs/plans/fast/2026-09-21-discovery-enrichment-master/children/c3/brief.md`（child c3，plan identity `commit:831e6604cf97e7acba005d8f00827659b49ce010`，`child_base_sha=468df56bf69b4b2f9afc7b4f38ad4791d8331240`）+ 控制方「实现 c3」的调用
Executor: C3Implementer
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master
Target branch: fast/2026-09-21-discovery-enrichment-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master@fast/2026-09-21-discovery-enrichment-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master
Pre-execution code SHA: 69bdb7dcec2663df605f726279f0cefec5f6e685（执行开始时的分支 HEAD = c2 证据提交；`child_base_sha` = 468df56bf69b4b2f9afc7b4f38ad4791d8331240 是 c2 代码头）
Post-execution code SHA: fba6173efd061aef73ebbce1854f83decb52a84f
Evidence HEAD: N/A（单次 product/test 提交，brief 未要求独立证据提交）
Implementation boundary: 468df56bf69b4b2f9afc7b4f38ad4791d8331240..fba6173efd061aef73ebbce1854f83decb52a84f（提交内恰为 8 个授权文件；`docs/plans/fast/**` 未入提交）

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| Task 1 Crossref 请求修复（I-1）：query/filter/cursor/mailto/rows 每个组件编码恰好一次，最终只把 `URI` 对象交给 `RestTemplate`；首屏、续页（cursor）与关键词三类请求同一条 URI 构造路径 | IMPLEMENTED | `CrossrefDataSource.kt`, `CrossrefDataSourceTest.kt` | RED（基线复现）：同一断言在旧代码上解出 `from-pub-date%3A2020-01-01%2Cuntil-pub-date%3A2026-12-31%2Chas-full-text%3Atrue` —— 线上 400 的「远端收到字面 %3A/%2C」指纹。修复后 `searchPapers encodes each component exactly once at the request boundary`（真实 `RestTemplate` + `MockRestServiceServer`，断言 `ClientHttpRequest.uri` 的 `rawQuery` 经 form 解码后的值）：filter 逐字含 `:` 与 `,`、cursor（含 `+` `/` `=` `%`）逐字往返、中文关键词 `材料 科学` 解出原文、mailto 解出原文，且 `%253A` / `%252C` / `%25E6` 三种二次编码指纹均不存在 |
| Task 1 目录研发主题检索（I-3）：`SubjectScopeCatalog.crossrefQueries(scope)` 作为 Crossref 主题词单一来源，默认研发检索带主题词，手动关键词优先 | IMPLEMENTED | `SubjectScopeCatalog.kt`, `CrossrefDataSource.kt`, `SubjectScopeCatalogTest.kt`, `CrossrefDataSourceTest.kt` | RED：`expected: <engineering materials science computer science chemical engineering energy physics> but was: <null>`（旧代码无关键词时不下发 `query`）。修复后：`searchPapers uses the catalogue topic queries when no keyword is given`（scope=RND_TARGET）解出主题词串；`searchPapers lets the operator keyword win over the catalogue topic queries` 解出 `perovskite solar cell`；`searchPapers keeps the pre-change filter-only query when scope is unknown` 断言 null scope 时不出现 `query` 参数；`SubjectScopeCatalogTest` 5/5 锚定主题词字面量、未知/null 返回空列表并纳入 `ALLOWED covers every branch` 分支覆盖 |
| Task 2 arXiv 修复（I-2）：默认 HTTPS + 旧配置官方 http 入口规范化；301 跳转/空体、非 Atom 载荷、Atom error entry、解析错误全部显式失败 | IMPLEMENTED | `ArxivDataSource.kt`, `ArxivProperties.kt`, `application.yml`, `ArxivDataSourceTest.kt` | RED：`arXiv 出站必须走 HTTPS: http://export.arxiv.org/api/query?... ==> expected: <https> but was: <http>`；且 301 空体/空 200/非 Atom/非法 XML/error entry 五例均「nothing was thrown」（旧实现把故障当零结果）。修复后 `searchPapers issues HTTPS for both the default and the legacy http base url`（真实 `RestTemplate` + `MockRestServiceServer`，断言真正发出的 URI）：默认与 legacy `http://export.arxiv.org/api` 两种配置的 scheme 均为 `https`、host 为 `export.arxiv.org`；`searchPapers fails explicitly on an empty redirect response`（`ARXIV_EMPTY_OR_REDIRECT`）、`... on a blank body with ok status`、`... on a non-Atom payload`、`... on a malformed Atom payload`、`... on an arXiv error entry`（`ARXIV_ERROR_ENTRY`）逐条断言显式失败；`parseAtomResponse rejects an empty body` 覆盖直达解析器的空体 |
| Task 2 c2 契约：年份过滤清空整页时仍按原始条目数给出 `nextCursor`；搜索层失败是 FAILED 而非穷尽 | IMPLEMENTED | `ArxivDataSource.kt`, `ArxivDataSourceTest.kt` | `parseAtomResponse keeps the nextCursor of a year-filtered page`：totalResults=200、2 条 2024 年条目被 2025–2026 过滤后 `papers.isEmpty()` 且 `nextCursor == "2"`、`totalResults == 200`；`parseAtomResponse filters by publication year` 对 2/2 已到底的样例断言 `nextCursor == null`（到页尾才算穷尽）。失败路径全部抛 `IllegalStateException`，`ExpertDiscoveryService` 既有 `catch (e: Exception)` 记 `SEARCH_FAILED` 并保留进入该页的游标 |
| I-3 既有入口保持：arXiv `RND_TARGET` 分类查询、手动关键词、年份/游标参数、`all:*` 兜底逐字不变；Crossref 手动查询（无 scope）行为不变 | IMPLEMENTED | `ArxivDataSource.kt`, `CrossrefDataSource.kt`, `ArxivDataSourceTest.kt`, `CrossrefDataSourceTest.kt` | arXiv：`searchPapers uses all-star query when no keywords and scope null`、`builds OR-joined category query for RND_TARGET scope`、`prefers keywords over subjectScope`、`keywords branch is unaffected by scope`、`keeps year and cursor paging parameters`（`start=50`/`max_results=25`）逐条通过；Crossref：keyword 分支与 scope 无关、无 scope 时不引入 `query` 参数、响应解析（DOI/标题/年份/期刊/作者/ORCID/机构/next-cursor/total-results）逐字段断言通过 |
| 下游接口保持（brief）：沿用现有公开签名、`PaperSearchResult` 不变、未新增文件/依赖、未写数据库状态、未改已应用迁移 | IMPLEMENTED | 上述 5 个生产/配置文件 | `searchPapers`/`extractAuthorEmails`/`sourceName`/`emailExtractionMethod`/`maxPapersPerSource` 签名未改；`parseAtomResponse(xml, criteria)` 公开签名未改；`grep` 确认 `parseAtomResponse` 无其他生产调用方；提交内 8 个文件与 brief 授权清单逐字一致（`docs/plans/fast/**` 0 条） |

## Commands

| Command | Result | Evidence |
|---|---|---|
| RED：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -o test -Dtest=CrossrefDataSourceTest,ArxivDataSourceTest,SubjectScopeCatalogTest`（最终实现前，旧代码 + 新用例） | FAIL（预期） | surefire：`Tests run: 35, Failures: 9, Errors: 5`。关键失败原文：`CrossrefDataSourceTest…:78 expected: <from-pub-date:2020-01-01,until-pub-date:2026-12-31,has-full-text:true> but was: <from-pub-date%3A2020-01-01%2Cuntil-pub-date%3A2026-12-31%2Chas-full-text%3Atrue>`（二次编码复现）；`…:98 expected: <engineering materials science computer science chemical engineering energy physics> but was: <null>`；`ArxivDataSourceTest…:83 期望 <https> 实际 <http>`；301 空体/空 200/非 Atom/非法 XML/error entry 六例 `nothing was thrown`；arXiv mock 未被调用（`getForObject` vs `exchange`）产生的 NoSuchElement/IndexOutOfBounds 5 例 |
| 计划要求命令（最终实现状态后新鲜运行）：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -o test -Dtest=CrossrefDataSourceTest,ArxivDataSourceTest,SubjectScopeCatalogTest` | PASS | `MVN_EXIT=0`，`BUILD SUCCESS`；`Tests run: 35, Failures: 0, Errors: 0, Skipped: 0`（CrossrefDataSourceTest 12 / ArxivDataSourceTest 18 / SubjectScopeCatalogTest 5）；同一次 `mvn test` 的 test 阶段由 `exec-maven-plugin` 触发的 JS 回归 `tests 1035, pass 1035, fail 0` |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-09-21/03-crossref-arxiv.md` | PASS | `sha256=3fbb6068a0c03c08b8c5091fcf31808161e8127128c31c635d220d32c0abb76b`，执行开始与交付前一致（size_bytes 8240） |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-09-21/03-crossref-arxiv.md --worktree <worktree> --expect-root <worktree> --expect-branch fast/2026-09-21-discovery-enrichment-master --expect-git-dir …/worktrees/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master` | PASS | 与记录一致（root/branch/git-dir/worktree_id）；提交前 HEAD=69bdb7d |
| `git rev-parse HEAD` / `git show --stat --oneline HEAD` / `git merge-base --is-ancestor HEAD fast/2026-09-21-discovery-enrichment-master` / `git show --name-only HEAD \| grep -c docs/plans/fast` | PASS | `fba6173efd061aef73ebbce1854f83decb52a84f`；提交恰 8 个授权文件（491+/107-）；`reachable: YES`；`docs/plans/fast` 命中数 0 |
| 线上对照探针（只读、不改仓库，用于确认断言口径）：`curl` arXiv `http` vs `https`；`python3`+`urllib` 取真实 Crossref `next-cursor` 并回放；`jshell` 观察 Spring 5.3.31 的 URI 编码 | PASS（信息性） | arXiv：`http://export.arxiv.org/api/query?…` → 301、`https://…` → 200 有 entry；真实 cursor `MTA1Njg…JTJG…` 内含字面 `%2F`（20 个采样 cursor 均不含 `+`），原样与 `%252F` 两种写法均得同一续页（Crossref 侧容错）；arXiv 错误形态实测为 `<feed><entry><id>https://arxiv.org/api/errors#…`（本仓即按该形态识别）；`jshell` 确认 `.build().encode()` 在 query 值中保留字面 `+`/`,`/`:`、只编码非 ASCII 与 `=` |

## Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CrossrefDataSource.kt` — 用 `UriComponentsBuilder` + 单一组件编码点 `encodeComponent()`（`URLEncoder`，每个值一次）构造请求，`build(true).toUri()` 后以 `URI` 对象调 `getForObject`（首屏/续页/关键词同路径）；无关键词时改用 `SubjectScopeCatalog.crossrefQueries(scope)` 的主题词，操作端关键词优先；新增 `SubjectScopeCatalog`/`UriComponentsBuilder` 导入
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ArxivDataSource.kt` — 新增 `baseUrl` 规范化（`^http://` → `https://`）；搜索改用 `restTemplate.exchange(url, GET, null, String::class.java)` 并显式判定 `is3xxRedirection || body.isNullOrBlank()` → `IllegalStateException("ARXIV_EMPTY_OR_REDIRECT")`；`parseAtomResponse` 改为空体 / 解析失败（`ARXIV_INVALID_XML`）/ 非 Atom 根元素（`ARXIV_NON_ATOM_PAYLOAD`）/ arXiv error entry（`ARXIV_ERROR_ENTRY`）四类显式失败，年份过滤与 `nextCursor` 推导逻辑逐字保留
- `src/main/kotlin/com/weibo/talentintroduction/config/ArxivProperties.kt` — `baseUrl` 默认值 `http://export.arxiv.org/api` → `https://export.arxiv.org/api`（与 `application.yml` 同步，避免 yml 覆盖类型化默认值）
- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalog.kt` — 新增 `RND_TARGET_CROSSREF_QUERIES`（6 个研发主题词，逐源私有常量，沿用 OpenAlex/arXiv/CORE 既有目录写法）与 additive 函数 `crossrefQueries(scope)`
- `src/main/resources/application.yml` — `talent-introduction.expert-discovery.arxiv.base-url` 的环境变量默认值改为 `https://export.arxiv.org/api`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/CrossrefDataSourceTest.kt` — 搜索类用例改挂真实 `RestTemplate` + `MockRestServiceServer`，断言 HTTP 边界 URI 的 form 解码值（编码一次、filter 的 `:`/`,`、cursor 的 `+`/`=`/`%`、中文关键词、mailto、无二次编码指纹）；新增目录主题词接线、关键词优先、null scope 不加 `query`、blank mailto 省略、空页穷尽 5 例；解析与 `extractAuthorEmails`/`init` 用例保留
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ArxivDataSourceTest.kt` — 搜索桩改为 `exchange`；新增 HTTPS（默认 + legacy http）边界用例、301 空体/空 200/非 Atom/非法 XML/error entry 5 例、空体拒绝、年份过滤空页保留 `nextCursor`、年份/游标分页参数用例；替换旧的「空 XML 返回零结果」用例（它把缺陷写成了契约）
- `src/test/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalogTest.kt` — 未知/null scope 增加 `crossrefQueries` 空列表断言、主题词字面量锚定、`ALLOWED covers every branch` 纳入新函数

## Deviations

计划中未唯一确定的实现细节，全部落在授权文件内；未新增文件、未新增依赖、未改既有公开签名、未改任何已应用迁移、未写数据库状态：

- **Crossref URI 构造用「逐组件编码 + `build(true)`」而非计划 Task 1 代码片段的 `.build().encode().toUri()`**：计划片段（以及 `UriComponentsBuilder.encode()`）在 query 值中保留字面 `+`（`jshell` 实测 Spring 5.3.31：`, : +` 不编码、非 ASCII 与 `=` 编码），而 servlet 侧按 form 语义解码会把 `+` 变成空格，无法满足 V-1「cursor 内 `+`/`=` 正确保留」。因此改为对每个参数值调用一次 `URLEncoder.encode(…, UTF-8)`（空格→`+`、`+`→`%2B`、`%`→`%25`、`:`→`%3A`、`,`→`%2C`），再以 `build(true)` 声明「已编码」交给 Spring 组装，仍然只编码一次、仍然只把 `URI` 对象交给 `RestTemplate`。任务状态表与 Commands 表给出该选择的边界断言；线上回放（`jshell`+`HttpURLConnection` 单次编码后 GET Crossref）返回 200，与调查记录的「单次编码 200 / 双次编码 400」一致。
- **`crossrefQueries` 的主题词取值**：计划只规定「默认研发检索使用主题词」，未给字面量。按目录既有「每源各自私有常量」的写法声明 6 个研发主题词（`engineering` / `materials science` / `computer science` / `chemical engineering` / `energy` / `physics`），并在 `SubjectScopeCatalogTest` 逐字锚定；c4 仍可在同一目录追加 CORE/分片，API 保持 additive。
- **Crossref `rows` 也走同一编码点**（`criteria.pageSize.toString()`）：值等价（`100`），只是消除「部分参数编码、部分参数裸拼」的分叉。
- **arXiv 的 URL 仍是 String 模板、仅把 `getForObject` 换成 `exchange`**：I-1 的适用范围是 Crossref（计划原文），arXiv 的关键词/分类/年份/游标查询串必须保持逐字不变（I-3）；换成 `exchange` 只是为了拿到 HTTP 状态用于 I-2 判定，编码路径没有改动。
- **失败信号形态**：统一为 `IllegalStateException`，消息 `ARXIV_EMPTY_OR_REDIRECT` / `ARXIV_INVALID_XML` / `ARXIV_NON_ATOM_PAYLOAD` / `ARXIV_ERROR_ENTRY`（计划片段只给了前者的字面量）。上层 `ExpertDiscoveryService` 的既有 `catch (e: Exception)` 分支把它们记为 `SEARCH_FAILED` 并保留进入该页的游标，不需要新的异常类型。
- **error entry 识别口径**：以 entry 的 `<id>` 含 `/api/errors#` 判定。该形态为线上实测（`https://arxiv.org/api/errors#start_must_be_non-negative`、`…incorrect_id_format_for_…`），非计划内凭空定义；正常 feed 的 `<feed><id>` 不参与判定。
- **删除了一条既有测试的旧断言**（`parseAtomResponse returns empty for null xml` → `parseAtomResponse rejects an empty body`）：旧断言把「空响应当零结果」这一被 I-2 明令禁止的行为写成了契约，属授权测试文件内的替换。

## Observations（不影响本 child 判定，供控制方/后续 child 参考）

- `src/test/resources/application.yml:120` 仍写着 `arxiv.base-url: ${ARXIV_BASE_URL:http://export.arxiv.org/api}`，但该文件不在 brief 授权清单内，本次未改。影响面：任何以 `@SpringBootTest` 加载测试配置且真实使用 `ArxivProperties` 的场景仍会拿到 http 默认值（但 `ArxivDataSource` 的出站规范化会把 `^http://` 归一为 `https://`，故行为仍满足 I-2）。建议由控制方在合适的 child/收口提交里同步该文件。
- 编码断言口径提醒：真实 Crossref `next-cursor` 含字面 `%2F`（实测），正确单次编码后 rawQuery 里必然是 `%252F`（servlet 解码后仍是 `%2F`），因此「rawQuery 里出现 `%25` 即等于二次编码」这一常见判据对 cursor 不成立；本次测试改用按参数值判定的指纹（`%253A`/`%252C`/`%25E6`）与逐字 form 解码往返，避免把正确行为误判成缺陷。

## Freshness

- Plan identity rechecked: YES（`3fbb6068…b76b`，执行开始 = 交付前）
- Worktree identity rechecked: YES（root/branch/git-dir 与记录一致，提交前再次校验 `--expect-*`）
- Reported commits reachable from target branch: YES（`git merge-base --is-ancestor HEAD fast/2026-09-21-discovery-enrichment-master`）
- Required commands run this invocation: YES（RED 与最终 GREEN 均在本次调用内；实现文件最后一次修改时间为 11:53:32，其后未再编辑任何实现/测试文件，两次运行都在该状态或其之后）
- Historical evidence used only as baseline: YES（调查文档/主方案只用于理解缺陷，不含任何实施证据）

## Remaining Blocker

- None

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`
