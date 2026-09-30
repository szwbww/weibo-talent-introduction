# Fast-P Child Brief — 01（PDF 作者标记与邮箱完整性修复）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-09-30/discovery-pdf-contact-integrity.md`，identity `commit:<seed>`。
- 本 child 批准计划（完整合同，必须先通读）：同一路径 `docs/plans/2026-09-30/discovery-pdf-contact-integrity.md`（单子计划 run：master 计划即本 child 计划；「实现方案」阶段 1～5、「关键不变量」I-1～I-6、「验收标准」、「变更文件清单」逐条生效）。
- Worktree / branch / `child_base_sha`：见派发消息。
- 依赖：none。无下游 child；本 child 之后另有独立人工验收，不在本 run 内。

## 全局约束

1. JDK 11 固定：所有 Maven 命令必须 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`。
2. 只允许修改「Authorized Files」表内 10 个文件；不得新建白名单外文件（含测试资源）。`PdfEmailExtractor.kt`、`SourceAuthorEmailResolver.kt`、业务 consumer/repository/config/migration、前端文件均为只读（计划「变更文件清单」尾注）。
3. 不得修改 `docs/plans/**`（fast-p 证据与计划由控制方提交）；不得 push、merge、rebase、squash、amend、reset；不得读写本 worktree 之外的仓库工作区（唯一例外：只读复制下方 fixture 源 ZIP）。
4. 产品代码提交格式：`feat(fast-p): implement 01`；把 fast-p 报告/日志（`docs/plans/fast/**`）排除在该提交之外，报告写完留在工作树由控制方提交。
5. 若计划与代码冲突、需要白名单外文件、需要新行为或需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`，不要自行扩范围或改计划。
6. 禁止联网、连线上 MySQL/ES、发信；测试传输替身与内存存储；不得新增依赖；不得新增 DB 字段/迁移、ES mapping 或外联规则；不得处理存量数据。
7. 计划明确不做：作者姓名模糊匹配、邮箱拼写猜作者、按名单屏蔽、OCR/字体修复/PDFBox 升级、PDF 链接坐标归属系统、广义空白拼邮箱、`?→*` 猜测替换、lun 特判、后缀关系推断冲突。
8. 已有修复语义「不兼容缓存 → FAILED、不自动重抽取」保持；不得扩成缓存迁移工程。

## Authorized Files（10）

| # | 精确路径 | 改动 |
| --- | --- | --- |
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt` | 标记组、损坏署名区、保留脚注标记上下文 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractor.kt` | 裸标记残缺邮箱窄过滤 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` | 仅 `EXTRACTION_VERSION` 常量 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` | 标记回归、fixture 读取辅助、独立证据正向控制 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractorTest.kt` | 残缺/完整文本边界 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt` | 三篇原文、mailto、实际 extractor 报告 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` | 新缓存版本/旧 proof 保持 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 原文 parser→consumer→writer 隔离测试及报告 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt` | 缓存版本生产/消费边界回归及报告 |
| 10 | `src/test/resources/discovery/ownership-20260930.zip` | 逐字复制已归档原文 ZIP |

## 原文 fixture（唯一来源，禁止重生成）

- 源文件（本机只读路径，可复制、不可修改）：`/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/audits/2026-09-30-discovery-three-case-replay/original-inputs.zip`
- 归档 SHA256：`9f5802b0d0862126c570c81f15a0e6053d26fa3abc99ab25ce69aaa81fec6a27`（复制前后各验一次）。
- 成员：`chee.pdf`、`chee.openalex.json`、`lyderic.pdf`、`lyderic.openalex.json`、`lun.pdf`、`lun.openalex.json`、`manifest.json`。
- PDF SHA256：chee `0daf96634f750d280bb1330d9b95d87f4ea04e54bee813db5c372092028fdb99`；lyderic `7459f1af390be438463bbd1b97d5cc79a183fd2ffb09310e2448d12736fb8f9b`；lun `7e2468ed9ad9ada830f943a1a5b1bc423704c0a6b51ea672fe250b5aa74805ba`。
- 复用 `SourceAuthorEmailResolverTest.kt` 现有 `zipMembers`、`fixtureSha256`、`openAlexPaperAuthors`；PDF 走 `extractOwnershipContent`（真实 PDFBox、真实 extractor，仅替换 HTTP）。不得改排 PDF 为「姓名:邮箱」，不得手工补作者证据。

## 关键不变量（计划 I-1～I-6，冲突时以计划文本为准）

- I-1 作者连续标记组整体计数：组 = 数字/逗号/空白 + 受支持符号组成的后缀段，至少一个支持符号；只为组首找前置署名，组内每个符号共享该署名；跨姓名边界不继承；未知署名登记 null，不得 `continue` 让其他作者看似独占。
- I-2 损坏作者区不出独占证明：仅 `authorArea` 签名区检测 `?` 与 U+FFFD，命中则本页标记→作者归属不可用（返回不可提供独占证据的空映射）；不 `?→*` 猜测；不阻断独立明确姓名联系记录、其他页、其他论文、文末名单。
- I-3 裸标记残缺邮箱过滤：邮箱匹配前为「行首空白 + 一个支持标记 `*∗†‡§` + 空白 + 一个 local-part 字符词片段 + 水平空白」→ 丢弃；`* x@host.edu` 完整地址不丢弃；保留既有 `splitLocalPartBeforeMailbox`（Email 标签）规则；横向空白用 `[ \t]` 等价字符类，禁止 `\s` 跨段；`markerSegments` 消费处把片段自己的 marker 传给 extractor（如 `"$marker$segment"`），Contact/evidenceText 仍存原始行。
- I-4 独立证明与线索语义保持：完整邮箱线索保留、身份字段为空；mailto 仍为无身份线索；合法一人多邮箱仍各有证据；不改 resolver/consumer 生产代码。
- I-5 缓存兼容版本分离：`DiscoveryIdentity.EXTRACTION_VERSION` `20261003` → `20261004`；`VERSION` 保持 `20260925`；旧值/null 按现有消费者返回 `IDENTITY_EXTRACTION_VERSION_UNSUPPORTED`，队列 FAILED、不下载、不写专家；不新增每来源版本字段。
- I-6 证据来自同一原文：修改源码后重新编译再测试；不得靠旧 target/classes；三篇 PDF 与 OpenAlex JSON 用归档原字节 + SHA256。

## 阶段要点（计划阶段 1～5 摘要）

1. 先写失败测试（负例在修复前必须红，保留失败输出）；三篇原文断言：Chee/Lydéric 完整邮箱保留且身份全空、Lun 后缀缺席且完整 mailto 保留身份全空；额外抓取 layout contacts。
2. `markerOwners` 连续组解析 + 损坏署名区空映射（仅文件 1）。
3. `PlainTextEmailExtractor.extract` 窄过滤 + marker 上下文回填（文件 1、2）。
4. `EXTRACTION_VERSION=20261004`；producer 序列化带新版本；direct consumer 与 pipeline 拒绝 `20261003`/`20261002`/null；正向控制（真实正常 PDF + 合成明确一人两邮箱）通过真实 consumer/writer。
5. 全量重编译后跑必需命令；查看 Surefire 汇总与三份运行报告。

## 必需命令（fresh 运行，逐条记录 exit code 与计数）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=SourceAuthorEmailResolverTest,PlainTextEmailExtractorTest,PdfEmailExtractorTest,DiscoveryIdentityTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,CoreDataSourceTest,JatsXmlEmailParserTest test
```

- 不启动应用、不连线上服务。若出现基线已有的失败，按 `children/01/baseline.md` 对比归类，不得修改范围外文件。
- 报告文件（构建产物，不入库）：`target/discovery-plan-acceptance/pdf-contact-integrity.json`、`pdf-contact-consumer.json`、`pdf-contact-cache.json`；测试失败也必须输出已观测数据。

## 交付物

- 一个本地实现提交：`feat(fast-p): implement 01`。
- 执行报告：`docs/plans/fast/2026-09-30-discovery-pdf-contact-integrity/children/01/execution.md`（不进入实现提交）。

返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
