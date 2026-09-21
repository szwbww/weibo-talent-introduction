# 保留可信作者身份实施计划

> 执行：superpowers:executing-plans 按任务顺序实施；独立验证使用 fix-v。当前为待审阅方案，未开始代码修改。
**目标**：发现来源中的 OpenAlex 作者 ID 可随专家入库，使没有 ORCID 但有可靠作者关联的人也能补全。
**依赖**：01。
**设计基线**：[主方案](00-discovery-enrichment-master.md)。
**技术栈与约束**：Java 11、Kotlin、Spring Boot 2.7；不新增外部依赖；只改清单文件；不触碰工作区已有无关改动。新增类中辅助DTO/枚举置于所属清单文件内，不暗增文件。
**审查重点**：身份歧义、半页/重启、外部限流、三层并发、旧接口回归；下方用例覆盖本子计划相关项。

## 需求描述

发现来源中的 OpenAlex 作者 ID 可随专家入库，使没有 ORCID 但有可靠作者关联的人也能补全。

必须保持：主方案M-1至M-5全部适用，本子计划只改变下列实现项；不外发邮件、不扩大医学范围、不更改专家主键。
范围外：其他子计划的实现、前端样式改动、付费采购与全量无条件重跑。

## 关键不变量

### Invariant I-1：作者ID不是专家主键
- Rule：新增可空 DTO 字段 openAlexAuthorId，规范为 A+数字，最终存入既有 externalIds.openAlexAuthorId；ES _id 与 orcidId 的历史语义保持原样。
- Applies to：OpenAlex parseResponse→AuthorEmail→buildExternalIds。
- Violation consequence：EMAIL专家变更主键、重复联系人。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-2：弱匹配不能带学术身份
- Rule：PDF/CORE 当前姓名首字母命中不可用于绑定作者ID或ORCID。只接受原始结构化邮箱归属、唯一完整姓名组合命中，或唯一作者且唯一邮箱；歧义结果保留邮箱但不携带作者身份。PMC提取结果只有明确 ORCID 等值匹配可补接 OpenAlex ID。
- Applies to：PDF、CORE、OpenAlex XML/PDF 回退。
- Violation consequence：把甲的邮箱绑定乙的学术指标。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-3：外部ID仅源读取
- Rule：externalIds mapping 为 enabled:false，可从 _source 读取，不能直接 exists/term 查询其子字段；写入合并保留 doi/pmid/orcid 和其他导入ID；无可靠关联不猜。
- Applies to：新入库及后续补全读取。
- Violation consequence：过滤永远命不中或覆盖其他来源信息。
- 来源：本次审计及本页现状审计列出的K条目。

## 现状审计

三层 ES 都 dynamic:false，externalIds 为 object/enabled:false；ExpertProfile.externalIds 是 JSON String，ExpertSearchService.sourceFields/toExpertProfile 已完整读取，无需改 mapping/DTO。生产写入：发现 toIndexMap/buildExternalIds；跨层晋升复制完整 _source；ContactOut/Apollo/SBIR 导入脚本也写 externalIds。读取：ExpertSearchService、导入去重脚本、企业发现工作流。新子键仅由 OpenAlex 发现链写，其他写者无需变更。PdfEmailExtractor 与 CoreDataSource 均存在首字母宽松匹配，必须先收紧身份传播。(来源: K-expert-profile-source-sync、K-expert-classification-one-object-three-layers)

所有跨模块存储读写总清单见主方案“存储与全部相关读写路径”；本页对应新增/改动点是其子集。新共享字段仅05的existing externalIds子键；07新建独立任务存储，其他共享表/索引不增列。前端不改动，无新增样式契约。

## 实现方案

### Task 1：携带身份
- 约束：I-1、I-3。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 两个 DTO 增可空默认字段；parseResponse 保留作者 id；生成 profileMap 时写 externalIds 子键而不是覆盖整个对象；保持构造兼容。

```kotlin
val openAlexAuthorId: String? = null
// externalIds["openAlexAuthorId"] = verifiedAuthorId; never replace esDocId
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

### Task 2：消除关联歧义
- 约束：I-2。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 删除首字母兜底作为身份依据，严格匹配必须唯一；无强证据不保留从弱匹配获得的 ORCID/作者ID。对 EuropePMC 返回值在 OpenAlex 提取包装层用 ORCID 精确对齐，不改其独立来源行为。

```kotlin
val matches = authors.filter { hasStrongEmailNameEvidence(email, it) }
val verified = matches.singleOrNull() ?: soleAuthorAndSoleEmail
// multiple matches => identity=null
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

新写路径与读者：本页现状审计明确的消费者继续读取相同字段；新增job由08消费、身份子键由06消费、请求policy由全部OpenAlex调用消费。依赖尚未上线时，新入口默认关闭，既有业务仍运行。

## 变更文件清单

| 文件 | 作用 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperAuthor.kt` | 数据契约 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/AuthorEmail.kt` | 数据契约 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSource.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 对应生产实现 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractorTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSourceTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 新增或更新本计划回归验证 |

共 10 个文件；不存在的文件标记为新增，已存在者原位修改；辅助类型放对应文件内。迁移号碰撞是唯一允许在实施前同步改名的路径变化，须同步本清单。

## 验收标准

- V-1：有作者ID无ORCID仍保存 EMAIL-* 主键且 externalIds 有 A...；三层晋升后子键存在。
- V-2：两位作者共享首字母、相同姓名或姓氏子串时不绑定；唯一完整姓名组合可绑定。
- V-3：ORCID精确对齐和冲突均测试；外部导入 existing externalIds 不丢其他键。
- I-1：逐条检查规则“作者ID不是专家主键”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-2：逐条检查规则“弱匹配不能带学术身份”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-3：逐条检查规则“外部ID仅源读取”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。

定向验证：
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OpenAlexDataSourceTest,PdfEmailExtractorTest,CoreDataSourceTest,ExpertDiscoveryServiceTest
```

各子计划定向用例通过后由集成阶段运行全量测试；无业务实现时不运行测试冒充修改已验证。

## 人工验收清单

### A-1：无ORCID专家
- 前置条件：测试论文含唯一作者、唯一邮箱、OpenAlex A ID，无ORCID。
- 操作步骤：运行发现，查看原始/候选专家数据。
- 预期结果：专家主键保持 EMAIL-*；externalIds.openAlexAuthorId=A...；邮箱和原有ID均保留。
- 覆盖：I-1、I-3。

### A-2：歧义作者
- 前置条件：测试论文含两位相同首字母作者和不能唯一归属的邮箱。
- 操作步骤：运行发现。
- 预期结果：可以保存邮箱线索，但不写任一作者 OpenAlex ID/ORCID，不产生冒名学术数据。
- 覆盖：I-2。

### A-3：保持原业务
- 前置条件：测试环境保留原人工补全三种scope、原资格规则及一位已申请专家；邮件调度关闭。
- 操作步骤：执行本子计划新行为，再执行原人工入口；对比前后专家详情、候选/申请记录、请求范围和邮件记录。
- 预期结果：原入口仍可执行；专家主键、邮箱、署名机构及运营状态不变；已申请专家不会新建候选；默认排除的医学来源仍不参与；邮件新增0；没有付费调用或已应用迁移被改写。
- 覆盖：主方案M-1/M-2/M-3/M-4/M-5及本页跨路径回归。

