# 深度发现零入库修复 01：原文姓名邮箱解析

状态：修复建议，尚未实施。目标工作树 `/Users/lukai/IdeaProjects/weibo-talent-introduction`；本地 main 基线 `406d63b`，相关产品代码 `2bcfdf8`。与 02 配套验收后再发布；本文件不授权线上重跑或恢复发送。

## 需求描述

PDF、HTML、CORE 正文中明确对应的姓名和邮箱应能被解析；不依赖邮箱拼写猜人。保持 JATS 共享脚注防错绑修复、合法同名/一人多邮箱、既有抓取预算与任务暂停。范围外：UI、批量发送资格、黑名单、存量全库清洗、扩大来源或修改 API 额度。

## 关键不变量

### I-1：归属来自原文
- 规则：仅接受作者自己的结构化联系节点，或有明确边界的“完整姓名—邮箱”联系条目；姓名匹配必须在当前论文作者集合内唯一。同行/邻近出现、邮箱包含姓名、唯一作者且唯一邮箱均不能独立证明归属；同名多个作者节点仍视为歧义。
- 适用：PDF/HTML/CORE 及 Europe PMC SEARCH_FIELD；违反会重现错绑。来源：K-author-identity-needs-email-evidence。
### I-2：身份整体传播
- 规则：姓名、机构、真实 ORCID/OpenAlex ID 必须来自同一已匹配作者。无明确对应时保留邮箱线索，姓名和作者 ID 留空；真实同名不跨论文合并，同一作者的两个明确邮箱都保留。
- 适用：全部 AuthorEmail 生产者；违反会产生混合身份。来源：K-historical-identity-orcid-fallback。
### I-3：适配必须穿过真实解析路径
- 规则：正例必须输入真实来源片段，不能在测试中预先给 AuthorEmail 填“已验证”。保留现有 HTTP 超时、大小限制和计量策略。
- 适用：PDF/HTML/CORE/JATS 来源测试；违反会再次出现测试通过但整源产出 0。

## 现状审计

AuthorEmail 为内存/抽取 JSON 契约；identityEvidence 已存在，不新增存储字段。本阶段不写 ES/MySQL。PdfEmailExtractor 的 PDF 与 HTML 均进入 associateEmailsWithAuthors；HTML 当前去标签并压缩全部空白，需保留联系条目边界。CoreDataSource.fullText 分支调用 associateEmails，PDF 回退复用 PdfEmailExtractor。两者复用 verifiedAuthorFor，仅用邮箱姓名字符串或单作者单邮箱推断。EuropePmcDataSource 在无 PMC ID 时直接返回 SEARCH_FIELD 的 author/email，去掉消费者证明门槛前必须取消该分支未经原文确认的姓名/ID投射。

JATS/PMC/OpenAlex 的 XML 分支复用 JatsXmlEmailParser；ORCID 公开记录同人姓名邮箱由 OrcidDataSource 提供，保持。消费者有同步 ORCID、论文直接消费和队列消费三路，统一适配见 02。来源知识均经代码复核，不把旧“强字符串匹配”等同于来源证明。

## 实现方案

1. 新建 SourceAuthorEmailResolver，共享原文条目解析。明确接受完整姓名紧邻邮箱的有界联系条目、结构化作者专属联系节点；先解析条目再确定唯一作者，禁止全篇“最近姓名”匹配。身份歧义返回空姓名/ID线索（I-1/I-2）。
2. PDF 保留文本换行/联系段边界，HTML 保留原 DOM 的作者/联系节点及段落边界；CORE 传入原全文而非仅邮箱集合。删除邮箱拼写匹配、单作者单邮箱兜底（I-1/I-3）。
3. 仅在原文明确解析成功后，用已有 identityEvidence 字段留来源摘要 `SOURCE_SHA256:<64hex>`；哈希用于审计，不能替代原文对应关系，也不新增准入状态。02 不以该前缀作为入库资格（I-1）。
4. Europe PMC SEARCH_FIELD 无全文支持时仅返回空姓名/作者 ID 的线索；不恢复旧广播或猜名（I-2）。
5. 固定来源样本：使用上一轮已留存的三篇公开 arXiv 原文中 Edward Raff 两邮箱、两篇 Hang Zhao 各一邮箱的联系条目；记录 URL、版本、原文摘要及人工预期。增加共享脚注、同名节点、姓名只有邮箱中出现、单作者但第三方联系邮箱等反例。原文确认与程序预期分别记录，避免解析器给自己出真值（I-3）。

## 变更文件清单

以下路径前缀：生产 `src/main/kotlin/com/weibo/talentintroduction/discovery/service/`，测试 `src/test/kotlin/com/weibo/talentintroduction/discovery/service/`。共 10 个文件。

| 文件 | 动作 |
|---|---|
| 生产 SourceAuthorEmailResolver.kt | 新增共享原文对应解析 |
| 生产 PdfEmailExtractor.kt | PDF/HTML 接入，保留原文边界 |
| 生产 CoreDataSource.kt | 传原全文，移除字符串猜名 |
| 生产 EuropePmcDataSource.kt | 搜索字段不直接授予身份 |
| 测试 SourceAuthorEmailResolverTest.kt | 明确/歧义/多邮箱真值测试 |
| 测试 PdfEmailExtractorTest.kt | 真实 PDF/HTML 路径回归 |
| 测试 CoreDataSourceTest.kt | CORE 正文和 PDF 回退回归 |
| 测试 EuropePmcDataSourceTest.kt | 搜索元数据不可冒充对应证明 |
| src/test/resources/discovery/source-email-ownership-cases.json | 有界原文片段及预期 |
| src/test/resources/discovery/source-email-ownership-cases.md | URL、版本、摘要及人工核定记录 |

## 验收标准

- I-1：真实明确样本输出正确作者；所有歧义样本无姓名/ID，不能以全部拒绝通过。
- I-2：一人两邮箱输出 2 条；不同论文两个 Hang Zhao 不按姓名合并；无姓名不传播机构或作者 ID。
- I-3：使用真实 extractor 输入及公开原文片段；不改生产默认值满足 mock。既有 JATS 错绑回归全部通过。
- 命令：JDK11 `mvn test -Dtest=SourceAuthorEmailResolverTest,PdfEmailExtractorTest,CoreDataSourceTest,EuropePmcDataSourceTest,JatsXmlEmailParserTest -Dexec.skip=true`。与 02 最终共同执行 `mvn clean package`。

## 人工验收清单

### A-1：四条已确认来源
- 前置：离线测试环境关闭发送，载入上述三篇公开原文。
- 操作：经对应真实解析入口运行，查看邮箱与姓名输出。
- 预期：Edward Raff 两条、Hang Zhao 两条，共 4 条对应正确；无跨论文合并。覆盖 I-1/I-2/I-3。
### A-2：歧义与旧问题
- 前置：载入共享通讯脚注、同名作者、邮箱拼写相似、单作者但第三方邮箱样本。
- 操作：运行解析并检查姓名/作者 ID。
- 预期：不明确条目只保留邮箱；姓名、作者 ID 均空；既有 JATS 明确条目仍正确。覆盖 I-1/I-2。
### A-3：抓取限制
- 前置：沿用超大 PDF 和超时服务测试夹具。
- 操作：运行 PDF/HTML 抽取。
- 预期：保持原超时、下载上限及失败分类，无新增 OpenAlex 计量全文下载。覆盖 I-3。
