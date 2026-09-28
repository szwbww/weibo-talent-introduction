# 新发现机构错误修复总计划

## 需求描述

可观察结果：新发现专家的机构、机构所在地国家、机构类型只来自同一作者的明确来源；缺证据者不进入首发名单。本计划的执行范围仅为**代码、测试及 ES mapping 配置文件**。

不得改变：非新发现专家的现有发送口径；已发邮件、联系人身份及业务主键；ORCID、SBIR 的来源身份；多机构记录的真实多重署名。范围外：从邮箱域名推断机构/国籍、凭名称识别现任职位、自动改写历史邮件。

按 create-p 的每子计划 ≤10 文件、≤2 子系统约束，依序实施以下四份代码子计划；每份独立部署/验证，后一步依赖前一步：

1. [01 来源提取](discovered-institution-repair-01-source.md)：停止新数据原文错填和异源类型覆盖。
2. [01b ORCID](discovered-institution-repair-01-orcid.md)：不任选第一家机构，不把机构当现任职。
3. [02 证据字段](discovered-institution-repair-02-evidence.md)：给同一作者的机构事实写单个证据字段。
4. [03 发送门禁](discovered-institution-repair-03-outreach.md)：ES 新目标、联系人重试、旧首发同口径拦截无证据者。

线上存量核证与修复由本会话执行并另留审计记录，不属于本计划。

## 关键不变量

### Invariant I-1: 不凭文本猜机构事实
- Rule: `institution` 只写唯一且与已确认邮箱作者对应的来源机构；`country` 只写同一机构明确国家；`employment` 只写当前任职来源，论文署名不能复制成任职。来源缺失/多义时这些展示字段置空；原始署名仅留在提取过程和来源证据中待核，绝不以原文回填展示字段或推断国籍。
- Applies to: 新发现建档、机构证据写入。
- Violation consequence: 再产生虚假高校/国家/职位。
- 来源: original；代码 `ExpertDiscoveryService.kt:1918-1929,2922-2926`。

### Invariant I-2: 身份与机构证据同时成立
- Rule: 新发现首发必须同时满足作者邮箱身份校验、机构来源证据、`filterResult=PASSED`；缺字段不等于通过。已发历史只审计，不补发/撤回。
- Applies to: ES 新目标、MySQL NEW 重试、旧首发、预估。
- Violation consequence: 历史脏候选绕过修复继续发送。
- 来源: K-batch-send-filter-retry-parity；`DiscoveryIdentity.kt:46-60`。

### Invariant I-3: 新字段分层一致
- Rule: `institutionEvidence` 必须显式映射三层 ES，RAW 产生后由现有晋升透传；学术补全不能把证据挂到另一家机构，也不修改运营状态、历史邮件、根级 `updatedAt`。
- Applies to: 新发现写入、晋升透传、学术补全。
- Violation consequence: 证据丢失或指向错误机构。
- 来源: K-enrichment-write-three-layers、K-promotion-source-passthrough。

## 现状审计

### ES 三层与 MySQL 联系人
- Mapping：仓库的 `src/main/resources/es/orcid_info_{raw,candidate,application}.json` 均声明根 `dynamic:false`；`institution` 为 text，`country`/`institutionType` 为 keyword，已有 `identityVerification`/`filterResult`。线上存量索引不能由仓库 JSON 推断同型；部署时 `ExpertIndexService.updateMappingIfNeeded` 会向现有三层执行 `PUT _mapping`，02 验收须逐层 `GET _mapping` 确认新增字段，失败则不得把证据能力视为上线。（来源: K-es-dynamic-false、K-es-mapping-single-declaration-source）
- 写入：`ExpertDiscoveryService.buildProfile/toIndexMap` 建 RAW、晋升复制候选；`buildOrcidProfile` 写 ORCID；`updateExpertAcademicFields` 写学术字段及原有异源类型；`ExpertIndexWriterService` 晋升 `_source` 透传、复评写 `filterResult`。独立脚本写路径：`scripts/build_sbir_expert_import.py:bulk_document` 写 RAW/候选的 SBIR 公司、任职与 `discovered` 标签；`scripts/update_sbir_employment.py:bulk_body` 局部改 SBIR 任职；`scripts/build_contactout_enterprise_es_import.py`、`scripts/import_contactout_visible_candidates.py`、`scripts/expert_discovery/enterprise_batch/import_candidate_es.py`、`import_es_documents.py`、`import_personal_email_candidates.py` 向共享候选索引导入其他来源，`build_sbir_research_fields_update.py`、`update_es_research_fields.py` 局部改研究字段。后两类非本次 `discovered/待确认` 自动改写对象。联系人创建在 `InitialOutreachService`、`ManualInitialOutreachService`，缺国家回填在 `ContactCountryBackfillService`，SQL 更新在 `ExpertContactRepository`。（来源: K-enrichment-write-three-layers、K-promotion-source-passthrough）
- 读取：`ExpertSearchService.toExpertProfile/sourceFields` 供搜索、复评、发送；`ExpertClassificationService` 用机构/任职算分类；`CandidateEligibilityService` 用 `nationality ?: country`；`MailVariableService` 用机构/任职/国家填模板；`SenderAccountAssignmentService` 与联系人国家用于账号分配；`ManualInitialOutreachService` ES 查询与 DB 重试两路；`InitialOutreachService` 旧首发。各交叉路径分别由子计划处理。（来源: K-batch-send-filter-retry-parity）

### 线上边界
- 修复前快照：新发现 RAW 32,118 人，机构非空 28,114 人；候选机构非空 27,677 人；另有待确认 RAW 机构非空 3,924 人。[全量审计](../../audits/2026-09-28-discovered-institution-comprehensive/README.md)。
- 修复前快照中 14,588 人命中机构格式或国家异常规则，**不是**事实错误总数；20,513 人缺结构化身份记录，缺记录不等于身份错误。719 名命中规则者曾发信；候选机构非空者 499 人 `filterResult=REJECTED`。进一步对 32,038 条非空机构做格式扫描，16,039 条命中更宽的疑似污染规则；两次规则口径不同，均不能用作批量清空清单。
- 今日线上已将 1,675 人国家别名规范化、4,535 人明显非国家值清空，三层及 586 个关联联系人回读一致；另有 335 名 OpenAlex、24 名 Europe PMC 来源专家的机构按唯一作者和来源机构修复并回读，累计 359 人。其余历史机构/身份来源核证由本会话单独执行，不属于代码计划。

## 实现方案

1. 按 01→01b→02→03 执行；01/01b 保守提取减少新增错误，02 写可验证机构证据，03 发信双路径 fail-closed。（I-1、I-2、I-3）
2. 每一步以子计划的代码/测试/mapping 文件清单为唯一修改范围；03 的预估与实际发送共用最终判定。（I-1、I-2）

## 变更文件清单

本主计划不直接修改生产文件；01、01b、02、03 的各自清单只含代码、测试及 mapping 配置。

## 验收标准

- I-1：01/01b 的来源反例测试覆盖脚注/ROR/邮箱/地址/多机构/无结构国家及 ORCID 多机构。
- I-2：03 的 ES、重试、旧首发及预估对同一新发现档案给出相同可发送判定；旧非新发现样本结果不变。
- I-3：02 的 mapping/投影/晋升透传测试保证证据三层一致，学术补全不覆盖机构类型或证据。

## 人工验收清单

### A-1: 来源展示
- 前置条件: 测试环境导入一个 JATS 脚注/ROR/邮箱混合机构样本及一个多机构样本。
- 操作步骤: 运行发现任务，打开两名专家资料。
- 预期结果: 前者只显示来源明确的机构名；后者机构为空且不显示猜选的第一家；两者任职均为空。
- 覆盖: I-1。

### A-2: 首发与预估
- 前置条件: 候选层放入一名有机构但缺证据的新发现专家，以及一名来源证实且 `filterResult=PASSED` 的新发现专家；配置 INTRODUCTION 范围含二者。
- 操作步骤: 打开发送预估并运行一次测试发送；再将第一名作为 NEW 重试联系人重复。
- 预期结果: 预估和两条路径均仅包含第二名，第一名无新邮件记录。
- 覆盖: I-2、ES→发送、MySQL→发送。

### A-3: 非新发现回归
- 前置条件: 准备一名原本满足首发配置的非新发现候选专家。
- 操作步骤: 分别查看预估和执行一次测试首发。
- 预期结果: 该专家仍计入预估且发送行为与修复前一致。
- 覆盖: I-2、不得改变非新发现口径。
