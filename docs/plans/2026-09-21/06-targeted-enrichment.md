# 定向补全与三层结果契约实施计划

> 执行：superpowers:executing-plans 按任务顺序实施；独立验证使用 fix-v。当前为待审阅方案，未开始代码修改。
**目标**：同一批量补全核心可处理原始库、候选库及申请库中的指定专家，支持 OpenAlex ID/ORCID 并返回逐人结果。
**依赖**：01、05。
**设计基线**：[主方案](00-discovery-enrichment-master.md)。
**技术栈与约束**：Java 11、Kotlin、Spring Boot 2.7；不新增外部依赖；只改清单文件；不触碰工作区已有无关改动。新增类中辅助DTO/枚举置于所属清单文件内，不暗增文件。
**审查重点**：身份歧义、半页/重启、外部限流、三层并发、旧接口回归；下方用例覆盖本子计划相关项。

## 需求描述

同一批量补全核心可处理原始库、候选库及申请库中的指定专家，支持 OpenAlex ID/ORCID 并返回逐人结果。

必须保持：主方案M-1至M-5全部适用，本子计划只改变下列实现项；不外发邮件、不扩大医学范围、不更改专家主键。
范围外：其他子计划的实现、前端样式改动、付费采购与全量无条件重跑。

## 关键不变量

### Invariant I-1：身份与批次
- Rule：enrichProfiles(profiles) 每批最多100个不同身份；先用可信 externalIds.openAlexAuthorId，缺失时有效ORCID；EMAIL-* 不能传作 ORCID；两者无则 NO_ID。返回以真实 esDocId 为键，API ID与ORCID不得作数据库文档定位的替代。
- Applies to：自动/手动补全共享核心。
- Violation consequence：丢掉无ORCID专家或更新错误文档。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-2：三层局部更新
- Rule：updateExpertAcademicFields 仍为学术字段唯一写入点，按真实 _id partial update 已存在层，404跳过，非404错误可重试；返回各层结果而非 candidateUpdated 布尔。null事实不覆盖已有值，重算分类；不更新姓名邮箱、署名机构、运营状态，不创建缺失APPLICATION。
- Applies to：RAW/CANDIDATE/APPLICATION。
- Violation consequence：原始层成功误报失败、覆盖运营数据。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-3：学术完成语义
- Rule：基础数据包括hIndex/引用数/论文数/研究方向/学科/最近发表年份；最近3篇论文标题单独可重试，空结果不等于请求失败。enrichedAt仅描述有来源的学术更新，其他导入写入同名字段不能证明OpenAlex已完成；专利保持关闭。
- Applies to：AuthorEnrichment 与写入判断。
- Violation consequence：假完成、重复昂贵查询。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-4：晋升保持门禁
- Rule：给RAW新增定向复评方法，复用当前邮箱、资格与分类门禁。仅当CANDIDATE和APPLICATION均不存在才创建候选，已申请者不能被重新建为候选；本轮不自动降级已存在专家。晋升使用最新RAW源，避免旧快照覆盖。
- Applies to：补全后复评、已有RAW扫描。
- Violation consequence：绕过门禁或重复外联。
- 来源：本次审计及本页现状审计列出的K条目。

## 现状审计

现 enrichExistingExperts 只扫 CANDIDATE 且排除 EMAIL-*；updateExpertAcademicFields 更新三层但返回 candidateUpdated。ExpertSearchService.findByOrcidId 是 term(orcidId)，不保证按真实 _id 获取；新增 findByDocumentIds(level, ids) 用 _mget 并复用 toExpertProfile。ExpertIndexWriterService.indexToRaw/晋升/降级/状态同步/分类bulk及外部导入均写三层；本计划仅学术doc map更新这些字段，不改变其他写入路径。晋升到APPLICATION会删除CANDIDATE，所以不能因候选不存在而重新晋升。读者包括列表/API DTO、CandidateEligibilityService、ExpertClassificationService、批量发信筛选、AiReplyContextBuilder；返回字段沿用。更新ExpertSearchService须机械同步NoiseSite行号，片段不改。(来源: K-enrichment-excludes-email-id-experts、K-enrichment-write-three-layers、K-openalex-institution-two-sources、K-line-number-guard-breaks-on-any-insertion)

所有跨模块存储读写总清单见主方案“存储与全部相关读写路径”；本页对应新增/改动点是其子集。新共享字段仅05的existing externalIds子键；07新建独立任务存储，其他共享表/索引不增列。前端不改动，无新增样式契约。

## 实现方案

### Task 1：补全共享核心
- 约束：I-1、I-3。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 保留旧 enrichExistingExperts 外层进度/筛选，抽出定向enrichProfiles；新增按OpenAlex ID批量作者查询；author ID/ORCID分组各<=100，映射回每个实际文档。统一单人和批量的论文/专利开关，取消单人路径无条件查专利行为。

```kotlin
fun enrichProfiles(profiles: List<ExpertProfile>, requestKind: RequestKind = RequestKind.HISTORY_ENRICHMENT): Map<String, ProfileEnrichmentOutcome>
fun findByDocumentIds(level: ExpertIndexLevel, ids: List<String>): List<ExpertProfile>
// outcome: Success, Partial, Deferred, NotFound, NoId, RetryableError
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

### Task 2：写入和复评
- 约束：I-2、I-4。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 写入函数返回 LayerUpdateResult；成功层不重复损坏、失败层重试；用最新RAW数据做单人复评，应用层存在时跳过候选创建。手动补全复用同一核心。

```kotlin
fun revalidateEnrichedRaw(docId: String): PromotionOutcome
// exists(APPLICATION) || exists(CANDIDATE) => AlreadyPresent
// evaluate latest RAW -> exact current gates -> create candidate
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

新写路径与读者：本页现状审计明确的消费者继续读取相同字段；新增job由08消费、身份子键由06消费、请求policy由全部OpenAlex调用消费。依赖尚未上线时，新入口默认关闭，既有业务仍运行。

## 变更文件清单

| 文件 | 作用 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt` | 对应生产实现 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 新增或更新本计划回归验证 |

共 9 个文件；不存在的文件标记为新增，已存在者原位修改；辅助类型放对应文件内。迁移号碰撞是唯一允许在实施前同步改名的路径变化，须同步本清单。

## 验收标准

- V-1：201个专家拆100/100/1，ORCID和A ID可混合；NO_ID不发作者查询；返回结果按esDocId对应。
- V-2：RAW-only更新成功；三层部分失败结果可识别；null不擦旧字段；APPLICATION存在且CANDIDATE缺失不重建候选。
- V-3：旧人工三种scope正常；最近论文开关对单人/批量一致；API404与网络失败分开；分类与详情读取更新事实。
- I-1：逐条检查规则“身份与批次”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-2：逐条检查规则“三层局部更新”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-3：逐条检查规则“学术完成语义”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-4：逐条检查规则“晋升保持门禁”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。

定向验证：
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,OpenAlexDataSourceTest,ExpertRevalidationServiceTest,ExpertSearchServiceTest,OperatorStatusWriteSeamGuardTest
```

各子计划定向用例通过后由集成阶段运行全量测试；无业务实现时不运行测试冒充修改已验证。

## 人工验收清单

### A-1：三层回填
- 前置条件：测试分别准备RAW-only、RAW+CANDIDATE、RAW+APPLICATION三人，有可靠ID。
- 操作步骤：调用定向补全并查询三层及专家详情。
- 预期结果：三人均显示真实指标；不存在层不会凭空生成；第三人候选库仍不存在。
- 覆盖：I-1、I-2、I-4。

### A-2：旧功能与门禁
- 前置条件：准备不满足现有资格规则的RAW专家和已申请专家；保留原筛选开关。
- 操作步骤：人工补充学术数据，再定向复评。
- 预期结果：不满足规则者不晋升；已申请者不重建候选；邮箱、机构、运营状态不变。
- 覆盖：I-2、I-4。

### A-3：论文附加数据
- 前置条件：测试作者指标成功、最近论文接口503。
- 操作步骤：执行补全后恢复论文接口重试。
- 预期结果：基础指标可读，首次为Partial，恢复后出现最近论文标题，不伪造专利。
- 覆盖：I-3。

### A-4：保持原业务
- 前置条件：测试环境保留原人工补全三种scope、原资格规则及一位已申请专家；邮件调度关闭。
- 操作步骤：执行本子计划新行为，再执行原人工入口；对比前后专家详情、候选/申请记录、请求范围和邮件记录。
- 预期结果：原入口仍可执行；专家主键、邮箱、署名机构及运营状态不变；已申请专家不会新建候选；默认排除的医学来源仍不参与；邮件新增0；没有付费调用或已应用迁移被改写。
- 覆盖：主方案M-1/M-2/M-3/M-4/M-5及本页跨路径回归。

