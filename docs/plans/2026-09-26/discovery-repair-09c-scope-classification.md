# 09c：专业事实补全与研发分类

状态：计划追加，尚未实施。2026-09-27；create-p；归属 [09](discovery-repair-09-subject-scope.md)。

## 需求描述

把已获取的 OpenAlex 作者结构化学科接入分类，避免纯艺术/商务研究仅凭科研分进入研发类型。前置：09b。

保持：六个现有类型、既有生产/科研分值与阈值、身份可信 ID 查询、三层按需更新、六类高校研究者保留。范围外：姓名模糊匹配、LLM 判专业、扩大目标行业、分类存量全量回填、增加 OpenAlex API 请求。

## 关键不变量

### Invariant I-1：专业事实只从可信作者响应取得
- Rule：parseAuthorBase 复用当前 count 降序取前五个 topics 的同一组节点，读取各节点 field.id；接受纯数字或精确 `https://openalex.org/fields/<数字>`，规范化为数字字符串、去重升序；topics缺失/空，或选定top5中任何一个field.id缺失/非法，整批专业IDs返回null；不以残缺非目标子集推断范围外。不要把 subfield.id、topic.id 或 domain.id 当 field.id。AuthorEnrichment 末尾新增同名默认字段；不从关键词/机构名/ORCID 命中词推断。
- Applies to：OpenAlexDataSource 单人/批量共同解析、ExpertDiscoveryService 三层补全和 profile.copy。
- Violation consequence：结构化学科被臆造，重现专业误纳。
- 来源：当前 parseAuthorBase、官方 Authors topics 契约；K-enrichment-write-three-layers。

### Invariant I-2：已确认六类“相关”，不发明主要专业占比
- Rule：针对 DiscoveryIdentity.isDiscovery(profile) 的记录，至少一个有效 field ID 属于六类即具备目标专业证据；非空有效 IDs 全在六类外是非目标；无 IDs 是未确认。交叉学科只要前五主题含目标类即可保留；不新增权重占比、主要学科阈值。该规则是此次明确设计口径，不宣称供应商保证专家工业适配性。
- Applies to：ExpertClassificationService。
- Violation consequence：擅自扩大或收窄用户确认的六类范围。
- 来源：用户口径 + 本计划确定的最小规则。

### Invariant I-3：分类先判范围，科研分不代替专业
- Rule：现有 clinical/medical 排除优先级保留；随后对 discovery 记录：无 IDs→UNKNOWN（RND_SCOPE_UNCONFIRMED），全非目标→OUT_OF_SCOPE（RND_SCOPE_OUTSIDE_TARGET）；有目标 IDs 才继续现有 production/research 阈值分型。无 IDs 时已有临床/医学强负例仍可被现有规则拒绝。分数保持原值，不因为范围外把科研分强制置0。非 discovery 旧导入沿用原分型逻辑，不借本次全库改资格。
- Applies to：实时补全、资格调用、分类回填共用 classify；fingerprint 加入规范化 fieldIds 与 discovery 标记。
- Violation consequence：科研分高仍误标研发，或数据缺失被说成确定不合格。
- 来源：K-classification-reflects-data-completeness。

### Invariant I-4：规则版本与兼容
- Rule：实际分类语义改变，VERSION 从 rnd-v2-2026 升为 rnd-v3-20260927；旧字符串仍能被读取，发送不比较版本、不恢复 sendable。非 discovery 按旧分型规则但写新规则版本。不得自动触发回填/启用调度；既有 onlyPending 按版本选人的影响须在发布前清点并报告。
- Applies to：classification JSON、管理校验、既有分类 scheduler/backfill。
- Violation consequence：同版本不同语义，或意外触发大规模重算。
- 来源：K-backfill-selects-by-version-not-fingerprint（本次已纠正旧“版本影响发信”描述）。

## 现状审计

以 [09 共享审计](discovery-repair-09-scope-audit.md)为本节组成部分，包含实际 mapping、DB 约束、全部读写入口、X9-1～X9-6 交互及源文件检索回执。行号为 2026-09-27 审计基线；执行按方法名重新定位，基于前置子计划完成后的代码，不覆盖 01～08 改动。

OpenAlexDataSource.parseAuthorBase 当前保留 top5 display_name，却丢掉 topics.field.id；enrichProfiles 已只按可信作者 ID/ORCID 查询。官方 https://help.openalex.org/data/authors/ 确认 topics 含 count、subfield、field、domain。ExpertClassificationService 当前只按自由文本科研字段/年份/论文数得分，分类不读领域 ID。生产 Gebeyehu 的现有研究字段与类型有真实快照；其结构化 fieldIds 未在旧快照保存，禁止给该实名样本编造作者 API 响应。

## 实现方案

1. **事实写入（I-1）**：OpenAlexDataSource.kt 扩展 AuthorEnrichment/parseAuthorBase；ExpertDiscoveryService.kt 更新 doc map 和 enrichedProfile.copy。有效新列表替换旧列表，null 不擦旧事实；RAW/CANDIDATE/APPLICATION 缺层不创建，姓名/邮箱/外部 ID 的 CAS 仍成立。OpenAlexDataSourceTest.kt、ExpertDiscoveryServiceTest.kt 覆盖 shared parser、错层 ID、空数据、三层失败、无额外外网请求。
2. **分类（I-2/I-3）**：ExpertClassificationService.kt 从 SubjectScopeCatalog 获取六类；新增稳定负面原因码，fingerprint 纳入新输入。当前字段未补齐的 discovery 记录不再只凭 80 分判 ACADEMIC_RND。专家分类仍为六类枚举，不新增 sendable 或隐藏首发开关。
3. **版本（I-4）**：改 VERSION；在 ExpertClassificationServiceTest.kt 保留非 discovery 旧规则回归。ExpertClassificationSchedulerTest.kt、ExpertClassificationAdminControllerTest.kt 仅对当前策略版本契约改用 VERSION，旧 JSON 的解析测试保留；不批量替换整个仓库所有 rnd-v2 字符串。
4. **证据资源**：新增 rnd-scope-evidence.json，分 real_snapshot 与 constructed 两部分。真实部分取 scope 审计中的 Gebeyehu/艺术/商务画像原字段并保留真实 ID/哈希；构造部分明确标注 field IDs 输入，用于六类全覆盖、单纯医/农/艺术/商务、目标+非目标混合、部分主题field缺失、无字段、临床强负例。不得给真实画像手工加 fieldIds 后声称线上原样复现。
5. **产物**：`09c.json` 输出实际输入证据类型、分数、type、negativeEvidence、fingerprint、层写结果和版本。

## 变更文件清单

共 9 个文件；清单外改动须先修订本计划。

| 文件 | 类别 |
| --- | --- |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationService.kt` | 生产 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationServiceTest.kt` | 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationSchedulerTest.kt` | 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt` | 测试 |
| `src/test/resources/discovery/rnd-scope-evidence.json` | 测试 |

## 验收标准

- I-1：top5 同组事实；三层显式写入、null 不覆盖；无身份字段变化；请求数不增加。
- I-2：每个六类 ID 独立正例通过范围，混合目标保留；纯非目标不为三类研发。
- I-3：足够科研证据的六类高校样本为 ACADEMIC_RND；纯艺术/商务结构化非目标样本为 OUT_OF_SCOPE；无 fieldIds 的高分 discovery 样本 UNKNOWN；Gebeyehu 原样仍 OUT_OF_SCOPE，生产分0。
- I-4：版本新、旧对象可读、发送版本守卫仍过；未调用生产回填。分类 scheduler 继续用 VERSION，不新启用它。
- 测试：上述五测试类加 ExpertClassificationVersionGateGuardTest（只跑不改）。

## 人工验收清单

### A-1：六类高校人员与非目标对照
- 前置条件：隔离测试生成 09c.json，使用明确标注的构造学科 IDs，科研分至少50；无临床强负例。
- 操作步骤：1. 查看六类各一条高校样本；2. 查看艺术/商务非目标及无结构化字段样本；3. 查看 Gebeyehu 真实画像。
- 预期结果：六类样本 ACADEMIC_RND；已知非目标 OUT_OF_SCOPE；无专业证据 UNKNOWN；Gebeyehu OUT_OF_SCOPE、productionScore=0；没有企业任职必选条件。
- 覆盖：需求、I-2/I-3、X9-3。

### A-2：补全与版本边界
- 前置条件：09c.json 含一人两邮箱三层补全、null 事实、ES 部分失败、旧分类 JSON。
- 操作步骤：1. 查看身份前后值；2. 查看三层字段和失败结果；3. 查看版本和发送配置差异。
- 预期结果：姓名邮箱外部ID原样；新列表一致、null不擦旧；失败非Success；新版本 rnd-v3-20260927；旧类型可读；新增请求/自动回填/发送配置改动均0。
- 覆盖：I-1/I-4、X9-2/X9-4。

人工验收时再从本节导出同名 `-acceptance.md`；本次不生成通过记录。测试与离线验收输出不得访问生产或发送邮件。
