# 09b：结构化专业字段契约

状态：计划追加，尚未实施。2026-09-27；create-p；归属 [09](discovery-repair-09-subject-scope.md)。

## 需求描述

为专业判断保留机器可识别的 OpenAlex field IDs，避免从自由文本猜专业。前置：09a。

保持：三个 ES 层、STEM/HUMANITIES 原含义、专家 ID/身份字段、既有 API 展示与发送配置。范围外：新增索引/数据库表/前端控件、触发回填、字段在页面新增展示。

## 关键不变量

### Invariant I-1：只加一个事实字段
- Rule：ExpertProfile 末尾增加 `researchFieldIds: List<String>? = null`；RAW/CANDIDATE/APPLICATION 同名 keyword。列表为去重、升序的 OpenAlex field ID 字符串；null/缺失/空列表均表示无可用结构化专业证据，不能解释为范围外。不得复用 disciplineCategory 或拼接 researchFields 伪造结构化证据。
- Applies to：三层 mapping、ExpertProfile、ExpertSearchService 全部画像读取和 sourceFields。
- Violation consequence：dynamic=false 下字段不参与读写契约，或旧文档被误判。
- 来源：K-expert-classification-one-object-three-layers。

### Invariant I-2：沿用现有六类目录
- Rule：SubjectScopeCatalog 提供目标 field ID 的只读访问/判断函数，唯一集合仍为 22/31/17/25/21/15。目录不新增行业；保留这些领域的高校科研人员，不要求企业任职或必须持有专利。
- Applies to：后续 09c 分类，现有 OpenAlex 检索。
- Violation consequence：检索、分类两份词表分叉或擅自收窄到企业专家。
- 来源：用户 2026-09-27 确认。

### Invariant I-3：局部更新与全量透传
- Rule：既有晋升整份 _source 透传新字段；事实更新仅由 09c 的 updateExpertAcademicFields 写入三层。此片只添加契约，不改晋升或发送行为。OperatorStatusWriteSeamGuardTest 只允许因 ExpertSearchService 行号移动作机械更新，不扩大白名单。
- Applies to：画像读取、映射启动补齐、层级转换。
- Violation consequence：字段丢失或借机打开运营状态写入口。
- 来源：K-promotion-source-passthrough、K-enrichment-write-three-layers。

## 现状审计

以 [09 共享审计](discovery-repair-09-scope-audit.md)为本节组成部分，包含实际 mapping、DB 约束、全部读写入口、X9-1～X9-6 交互及源文件检索回执。行号为 2026-09-27 审计基线；执行按方法名重新定位，基于前置子计划完成后的代码，不覆盖 01～08 改动。

三份 mapping 已实读，根 dynamic=false；ExpertIndexService.bootstrapIndices 对三层调用 updateMappingIfNeeded，并按字段处理冲突。ExpertSearchService:toExpertProfile/sourceFields 需要显式读取。现有 promoteToCandidate/promoteToApplication 复制完整 _source，无需修改生产 writer。

## 实现方案

1. **模型/读取（I-1）**：ExpertProfile.kt 末尾字段；ExpertSearchService.kt 的 toExpertProfile/sourceFields 纳入字段，缺失保持 null，不用 researchFields/机构推断。ExpertSearchServiceTest.kt 覆盖三层读取、缺失、空数组、保留真实 ES _id。
2. **目录（I-2）**：SubjectScopeCatalog.kt 暴露六类只读集合或判定函数；SubjectScopeCatalogTest.kt 钉死六值及原 source 查询片段不变。
3. **mapping（I-1/I-3）**：三份 es JSON 顶层追加 keyword 字段；ExpertIndexServiceTest.kt 覆盖已有索引补齐、新建 APPLICATION、某层冲突继续报告失败，禁止改变 dynamic。需要时仅机械调整 OperatorStatusWriteSeamGuardTest.kt 的 ExpertSearchService 行号。
4. **产物**：隔离测试输出 `09b.json`：三层 mapping 类型、读回字段、旧字段缺失行为。后续 09d 验证全量透传，不在本片改写专家。

## 变更文件清单

共 10 个文件；清单外改动须先修订本计划。

| 文件 | 类别 |
| --- | --- |
| `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalog.kt` | 生产 |
| `src/main/resources/es/orcid_info_raw.json` | 生产 |
| `src/main/resources/es/orcid_info_candidate.json` | 生产 |
| `src/main/resources/es/orcid_info_application.json` | 生产 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexServiceTest.kt` | 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalogTest.kt` | 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 测试 |

## 验收标准

- I-1：三层字段同为 keyword；null/empty 不被转换成“非目标”。旧 profile 构造与搜索不报错。
- I-2：目标 ID 集合恰好六项，旧检索条件不变。
- I-3：生产 writer/发信/DB migration diff=0；运营写入守卫白名单片段不变。
- 测试：ExpertSearchServiceTest、ExpertIndexServiceTest、SubjectScopeCatalogTest、OperatorStatusWriteSeamGuardTest。

## 人工验收清单

### A-1：三层契约
- 前置条件：隔离索引/请求替身中准备 fieldIds=["17","22"] 和缺失字段两份记录，运行 09b 测试。
- 操作步骤：1. 查看 09b.json 三层 mapping 和画像；2. 查看目标 ID 集合；3. 对照旧发送配置快照。
- 预期结果：三层 keyword，读回 ["17","22"]；旧记录 null；目标集合仅 15/17/21/22/25/31；发送配置变更数=0。
- 覆盖：需求、I-1～I-3、X9-2。

人工验收时再从本节导出同名 `-acceptance.md`；本次不生成通过记录。测试与离线验收输出不得访问生产或发送邮件。
