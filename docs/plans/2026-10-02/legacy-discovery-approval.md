# 历史发现人工认可兼容

## 需求描述
依据用户本轮指令实施：认可当前审计的 16,029 条旧记录，并明确同意同时兼容身份和机构来源凭证，保留机构、国家等已有条件。只有这批记录及同身份副本获得人工认可；不把人工认可当作来源身份验证，不修改自动抽取、学术作者绑定、机构、国家、分类、联系状态、模板或发送配置。不发送测试邮件；不提交其他工作区改动。

## 关键不变量
### I-1：有界认可
只处理服务器冻结清单的 16,029 个候选主键；同主键其他层须姓名、规范邮箱、业务身份相等；已有凭证不覆盖。使用现有 identityVerification 对象，status=LEGACY_APPROVED、source=LEGACY_USER_APPROVED_20261002、version=20260925。新发现无凭证继续失败。
### I-2：人工认可绑定
认可摘要固定 SHA256(JSON 紧凑 UTF-8 数组[source,orcidId,normalizedEmail,givenNames,familyNames,institution,country,institutionType])；null 保留为 null。姓名邮箱/业务主键/机构国家改变即失效。记录实际字段，允许原有姓名缺项，不补造姓名、学术 ID 或原始来源证据。DiscoveryIdentity.allowed 保持来源验证语义，人工认可不能用于学术作者绑定。
### I-3：发送口径一致
统一 RecipientScope.matchesDiscoveryOutreach 同时消费来源验证与有效人工认可。人工认可替代身份和机构来源 token 两项；机构非空、国家可映射、filterResult=PASSED 原规则不变。预估、执行、NEW 重试、旧首发共用。来源：K-batch-send-filter-retry-parity。
### I-4：可回滚与并发
迁移先备份服务器上的各层旧凭证与版本，再以 seq_no/primary_term CAS 仅更新 identityVerification；冲突报告，不覆盖；回滚仅在当前凭证仍等于本次凭证时还原旧值/缺失。数据不导出原服务器；输出聚合数量。副本晋升透传，来源：K-promotion-source-passthrough。

## 现状审计
- 三层 mapping 文件 es/orcid_info_{raw,candidate,application}.json 均 dynamic=false；identityVerification 已声明全部所需子字段，无 schema 新增。
- 写：ExpertDiscoveryService.toIndexMap/proofFor 创建来源凭证，学术更新 CAS 比较姓名邮箱凭证与 externalIds；ExpertIndexWriterService.promoteToCandidate/promoteToApplication 全字段透传（有身份对象时 create-only）；ExpertRevalidationService 经 allowedMap 做来源校验再调用 reconcileDiscoveryCandidate；现有临时审计修复脚本不自动运行。本次新增离线有界迁移写入认可对象。其他局部字段更新不写该对象。
- 读：ExpertSearchService.mapToExpertProfile 与发送 _source 已包含凭证；ExpertIndexWriterService.discoveryProfile 已读取凭证；DiscoveryIdentity.allowedSource/allowedMap、institutionEvidence 和发现服务作者 ID 解析需要保持真实来源语义。RecipientScope 的 ES、重试谓词及 InitialOutreachService 两处都共用 matchesDiscoveryOutreach。
- 交互：三层迁移→搜索→预估/执行/重试；晋升→目标层凭证；学术补全→不能把认可当作作者 ID 证明。
- 工作树 main；已有无关 docs/tools 变更保留。线上与本地部分编译类不同，部署前核对目标类，最小替换，仅备份后重启；不部署其他本地差异。

## 实现方案
1. I-1/I-2：DiscoveryIdentity 新增人工认可验证，保留 allowed 行为；现有字段容纳认可来源。
2. I-3：RecipientScope 统一门禁接受有效认可，仍执行机构国家/PASSED 检查。
3. I-1/I-4：脚本读取固定服务器清单，先 dry-run 及备份各层，再 CAS 回填；支持 verify/rollback，零个人数据输出。
4. I-1—4：增加针对认可、篡改、未知来源、发送/重试一致、迁移边界的测试。验证后仅部署受影响类及备份；执行迁移并核实人数。

## 变更文件清单
| 文件 | 用途 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt | 认可验签 |
| src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt | 统一门禁 |
| src/test/kotlin/com/weibo/talentintroduction/expert/domain/LegacyDiscoveryApprovalTest.kt | 认可及门禁回归 |
| scripts/apply_legacy_discovery_approval.py | 有界迁移/回滚 |
| scripts/test_apply_legacy_discovery_approval.py | 迁移边界测试 |
| docs/plans/2026-10-02/legacy-discovery-approval.md | 合同 |
| docs/plans/2026-10-02/legacy-discovery-approval-result.md | 实施证据 |
| docs/knowledge/es-index/K-promotion-source-passthrough.md | 使用计数 |
| docs/knowledge/campaign/K-batch-send-filter-retry-parity.md | 使用计数与知识补充 |

## 验收标准
- I-1：冻结候选数量=16029，迁移不生成新业务文档，不覆盖已有凭证；新增缺凭证负例不通过。
- I-2：认可不通过 academic allowed，不签发原始机构证据；任意绑定字段更改、版本/来源/摘要错误均失败；Unicode/null 跨 Python/Kotlin 摘要一致。
- I-3：机构/国家/PASSED 条件负例仍失败；ES与重试认可正例通过；原有来源验证用例不变。
- I-4：Python 测试验证定界、跳过已有凭证、CAS/回滚语义；线上聚合核实写入及剩余过滤分布。备份与 hash 保留原服务器。
- 命令：JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=LegacyDiscoveryApprovalTest,DiscoveryIdentityTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest -DskipNodeTests=true；python3 -m unittest discover -s scripts -p test_apply_legacy_discovery_approval.py；git diff --check（本次文件）。

## 人工验收清单
### A-1：预估兼容
前置：迁移完成；Dia 候选层/新发现/未联系/五类/无方向限制/模板门禁关。操作：重新预估。预期：显示实施证据中的线上核实数；未满足机构国家条件的仍排除。覆盖 I-1/I-3。
### A-2：条件保留
操作：勾选无研究方向或只选某研发类型后重新预估。预期：结果应用所选条件，空类型=0；不因认可绕过配置。覆盖 I-3。
### A-3：来源及副本
前置：服务器 verify。操作：运行脚本 --mode verify。预期：报告候选16029认可，其他层只同身份副本，无来源 VERIFIED 冒充；新无凭证档案不认可。覆盖 I-1/I-2/I-4。
### A-4：回滚
前置：需要撤销时保留服务器备份。操作：运行脚本 --mode rollback。预期：只撤销本次未被改动的认可；其他数据、学术身份、邮件状态、配置不变；冲突明确计数。覆盖 I-2/I-4。
