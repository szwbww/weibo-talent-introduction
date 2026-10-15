# Repair Plan: discovery-identity-recovery-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-10-13/discovery-identity-recovery-master.md
Baseline SHA256: f69d8135ce0019ad36cc8939cf8101a6f584aac9983dec3dfc78eb2ae37ce238
Baseline recorded commit: 36075ec06c5e4382f13606c995e24a13ec4470ce
Verification report: docs/plans/review/2026-10-13-discovery-identity-recovery-master/machine-verification.md — Epoch 4, independent reviewer /root/aggregate_review_epoch4, FAIL / PROGRESSING
Verification payload identity: /tmp/aggregate-epoch4/verification-complete.md SHA256 550e01a7c5313a41e33719d208a3e82752c1117af16dfbb7b019e72af251961a (controller appends complete report to the exact destination; reviewer does not edit review evidence)
Implementation boundary reviewed: 9d353934b82ed3e36a193c70748e5efccb3d9e82..1ab288125b4e00b1237a69c48320d315741eaf76
Reviewed code / next repair base: 1ab288125b4e00b1237a69c48320d315741eaf76
Evidence HEAD at planning: e3169cfa3c6657a6b479f3ccbcaf91bdcfd007c6
Prior repair SHA256: 23d72492384dd6230800fd5efcbde74eb937ae1cf3c3b1b7e9e4b4624cb98424
Prior repair execution: docs/plans/review/2026-10-13-discovery-identity-recovery-master/repair-execution.md, SHA256 441918d8bb5fb424506695ac79d0d93fa97ebf0a13c06b8532369fa2d980dbdf, Epoch-three
Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master
Branch: fast/2026-10-13-discovery-identity-recovery-master

所有相对路径仅在上述worktree解析。旧repair执行批准不适用于本修订。规划前唯一既有dirty为controller review ledger；产品/index/HEAD未改。此次仅更新本文件，历史版本由Git保留。

## Objective

闭合原revision绑定、人物读取完整性、已知无效邮箱展示、部分恢复续跑、重验当前policy五个产品根因，以及已批准真实入口测试合同。共6个根因任务，解决6个P1及1个mandatory P2；保留本轮已解决行为。完成仅可送独立aggregate复审，不等于B-1/B-2或master验收通过。

## Authority and Convergence

- Master身份CONSISTENT；invoked hash相同、governing diff空；十child及A1–A14保持。精确身份/规则/原因/批准仍见 docs/plans/fast/2026-10-13-discovery-identity-recovery-master/ledger.md，SHA256 2bbeec0ca9bbaf2008aed29b4265862a089a84cdbc25f20160a3e6ef5fe361e2；human-review-handoff.md SHA256 ff9fa89083d24f9a3f6b37f798a8f201273df9c7b543d8a572316e7c4a390a64。
- Epoch3 FAIL/DIVERGING及随后人源「好的 你来更新repair吧」不同策略裁定原样保留。Epoch4独立判PROGRESSING：V-14/V-27/V-28根因已解决；V-3/V-5/V-12/V-13/V-29已实质收窄。新V-30来自累计旧Revalidation代码，不是本轮repair回归；V-31是现有明确验收合同未落实。判定不按finding数量。
- V-30属于原admission I-2/I-3及原Revalidation授权，不改变policy合同或引入新业务范围。V-31仅恢复已经批准的R-1/E01测试合同，不把B-2缺证转成产品修复。
- 本次授权候选收敛为18个现有文件，替代旧28文件表；Revalidation及其测试明确列入本修订。执行前仍须人源明确批准当前文件；旧表、旧hash及本规划调用都不是实施许可。

## Findings in Scope

| Finding | Severity / disposition | Requirement | Evidenced root cause |
|---|---|---|---|
| V-3 | P1 / REPAIRABLE / PERSISTENT | master I-4/I-8；04 I-2；旧repair R-2 | P4E：核验根成员之后才读expectedRevision，接受未核验新成员所属revision；旧P3J同类键冲突已解决。 |
| V-5 | P1 / REPAIRABLE / PERSISTENT | master I-4/I-7/I-8；08 I-1/I-3 | P4A/P4C：strict _mget只验证docs数组，条目error/缺条目当不存在，实际Recipient仍INCLUDED。 |
| V-13 | P1 / REPAIRABLE / PERSISTENT | master I-6/I-8；06 I-3 | P4G：Query仍用宽松_mget，把未知降级为关系失效，跨campaign两contact正常count1变2。必要聚合桶已修。与V-5合并一个读取根因任务。 |
| V-12 | P1 / REPAIRABLE / PERSISTENT | master I-5/I-7；06 I-2；07 I-1/S-2 | P4D：展示operatorStatus取contact或ES，但许可只检查contact；ES EMAIL_INVALID/no-contact仍ADMITTED。 |
| V-29 | P1 / REPAIRABLE / PERSISTENT | master I-3/I-8；01 I-3；02 I-3；旧repair R-7 | P4B：首次PARTIAL后全名齐，部分目标proof已存在仍ROUTE_TO_REPAIR，原申请不能闭合。 |
| V-30 | P1 / REPAIRABLE / NEW cumulative | master I-2；admission I-2/I-3 | P4F：同身份旧AUTO_PASSED跳过当前policy；机构凭证INVALID仍投影。 |
| V-31 | P2 mandatory / REPAIRABLE / NEW | 已批准repair R-1与E01明确测试合同 | GT admitted硬编码true、撤销断言缺失；三实际入口辨别测试未落实；E01两方法仅guard，竞争顺序模拟，healthyInitial把FK当健康通过。 |

## Findings Excluded

| Finding / item | Reason |
|---|---|
| V-1,V-2,V-4,V-6,V-7,V-8,V-9,V-10,V-11,V-14,V-15,V-16,V-17,V-18,V-19,V-20,V-22,V-25,V-26,V-27,V-28 | EXCLUDED：原根因RESOLVED；仅保留回归约束，不重开产品修复。 |
| V-21,V-23,V-24 | EXCLUDED：非mandatory P2，无可选清理授权。 |
| B-1 | EXCLUDED as repair：当前51,829真实形状p95≤2s/额外heap≤128MiB证据缺；仍BLOCKED，不伪造数据或降低门槛。 |
| B-2 | EXCLUDED as product repair：完整三入口健康/竞争/接受后失败运行证据仍BLOCKED。R-6恢复已明确批准的测试真实性并执行E01，不承诺消除所有前置障碍。 |
| Initial attemptId=0L → fk_mail_record_send_attempt | EXCLUDED / NEW_SCOPE_REQUIRED：base既有且未获修复授权；不改caller/helper/schema，不造id0行，不mock成功业务持久化。 |
| C02首次Node计时失败 | Observation；JUnit230通过但命令exit1，Node1679/1680。未改文件且聚焦27及全JS对照通过；不授权改无关计时测试。后续仍须如实记录全部失败/重跑并完成必需命令。 |
| P4H/P4I邻接风险 | 未获得有效运行证据，EXCLUDED；不据此扩大产品任务。 |
| 其余观察、环境差异、可选矩阵 | EXCLUDED：无已确认合同违约，不做附带清理或新设计。 |

## Unchanged Contract

- 只处理当前discovered；历史冻结范围仍需单独执行批准；10,686仅参考，03不执行，161待核组及两个已有根不自动合并，不承诺生产收益。
- 原docId/业务键/邮箱/contact/alias/mail及人工/运营/学术事实保持；不猜名、不伪人工认可、不造contact解锁、不删除历史；仅人物仓储与首发保护既有写入口负责其各自事实。
- 身份证明、准入、具体地址许可独立；有效同身份人工批准/历史认可例外保持；六个软资料原因继续作为hints；硬无效凭证、验证、抑制、原scope及完整组硬保护保持。
- 保留默认关闭旧行为、关闭不撤销历史保护、REQUIRES_NEW持久SENDING、UNKNOWN不自动重发、明确未发的受限SAFE_RETRY、原地址限制、合格primary及跨页person去重。
- 不改共享schema/迁移/ES mapping、classifier、policy规则、来源预算、cookie私有审计、DTO枚举、frontend/CSS/缓存键；不改非人物宽松批读的既有合同。保留全部已修count/hits/SQL/聚合结构失败显式不可用。
- 不新增文件。只在18个现有文件内做必要修改。SEAM仅授权源码移动导致的精确噪声path/line/context同步，不新增真实写入口、删断言或扩大排除。
- 无生产SMTP/apply/迁移/开关/回滚/删除/部署/push/merge/amend/历史改写。测试仅临时MySQL、假SMTP、合法夹具；master/fast/review报告只读，唯一额外执行证据路径见末尾handoff。

## Authorized Files

18个现有文件，未实际需要修改的保持不变。ID只作为下文精确路径别名；本表不授权同目录其他文件。

| ID | Exact file | Purpose |
|---|---|---|
| P | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt | R-1 原revision与完整根核验绑定 |
| PT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonServiceTest.kt | R-1 service交错/复读回归 |
| RT | src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepositoryTest.kt | R-1 实际service+真实事务仓储辨别回归 |
| ES | src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt | R-2 strict人物批读完整性 |
| EST | src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt | R-2 HTTP响应错误/缺失与合法不存在对照 |
| Q | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt | R-2 查询使用严格事实；R-3有效邮箱状态 |
| QT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt | R-2/R-3 查询完整性和状态回归 |
| SELT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientServiceTest.kt | R-2 实际Search→Recipient拒绝未知 |
| CCT | src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt | R-2 人物关系未知不伪确定计数 |
| SEAM | src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt | 仅Q源码移动必要精确噪声位置维护 |
| REC | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryService.kt | R-4 同目标PARTIAL继续proof/登记/准入 |
| RECT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryServiceTest.kt | R-4 mixed层首次部分后安全续跑 |
| REV | src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt | R-5 自动重验先当前policy后投影；原admission授权 |
| REVT | src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceTest.kt | R-5 当前硬凭证失败与软资料/人工对照 |
| GT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt | R-6 真实三入口/真库/资格撤销/精确失败与E01 |
| INITT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt | R-6 实际Initial入口辨别回归 |
| MIT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt | R-6 实际ManualInitial入口辨别回归 |
| MEMT | src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt | R-6 实际ManualExpert入口辨别回归 |

不授权Guard/Recipient/Repository/Writer/RepairService产品、三caller产品、ManualOutreachTxHelper、controller产品、CLI、domain/schema或旧28表的其他文件。现有实现有足够接缝完成本有界任务；若实际证明需要表外修改，保留证据并停在该项请求决定，不自扩表。

## Repair Tasks

按R-1→R-2→R-3→R-4→R-5→R-6顺序，共享文件接续修改；先建立区分当前失败与正向对照的回归，不能以删断言、全阻断或mock被验证决策使测试绿。各任务都受Unchanged Contract限制。

### R-1: 原根revision绑定完整核验事实

- Resolves: V-3。Root cause: PersonService先verifyRootGroup后读revision，P4E采纳较新revision掩盖未核验成员。
- Files: P, PT, RT。
- Change: 挂接条件必须对应被完整核验的原根revision与成员/键事实。核验期间SQL关系revision改变，不能将后读的新revision配旧核验结果直接提交；沿既有有限重试重新读取完整当前事实，失配明确拒绝/STALE且零部分写。
- Discriminating regression: 实际PersonService+真实MySQL repository/事务：A核验仅root后暂停；B同ORCID合法挂入，B当前discovered标签移除；A继续，不得未经B复验ATTACHED。相同调度但B仍当前有效为许可对照；既有P3J不同OpenAlex竞争仍拒冲突方。
- Existing verification: C07,C08,C10,C11,C18,C21。
- Must not change: 稳定UUID、同类键唯一、完整规范名/标签/指纹核验、两个根待核、锁顺序/有限重读。
- Prohibited: 全局锁、禁止全部挂接、修改schema/仓储产品、伪造锁或APPLIED结果。
- Completion: P4E无未核验新成员挂接；相容对照成功；失败成员/键均无半提交。

### R-2: 人物批读失败与合法不存在不可混淆

- Resolves: V-5,V-13。Root cause: strict接口只检查docs数组，Query又仍走宽松接口。
- Files: ES, EST, Q, QT, SELT, CCT；仅必要位置同步SEAM。
- Change: strict人物批读必须确认请求ID的完整响应，逐项error、缺项、重复/错ID、缺必要found或found=true却缺必要source均明确不可用；明确found=false才可当不存在。Query人物关系核验/详情使用该完整性语义；读取未知不能转成关系失效、独立contact或精确count。保留现有异常/响应契约，非人物宽松接口不改。
- Discriminating regression: 实际Search HTTP边界分别error与空docs，对照合法found=false；实际Search→Recipient有其他层可读时仍排除未知；实际Query→联系人controller跨campaign1/2同person两contact，完整读取count1、条目错误明确失败而非count2。合法不存在/当前真实失效仍按原合同处理。
- Existing verification: C12,C16,C17,C21,C22。
- Must not change: 深分页/同成员筛选/层范围/memberCount、合法空聚合、既有原始单位/旧链接、读操作零业务写。
- Prohibited: 全部异常吞成空、仅加日志、对未知给猜测total、改变所有非人物搜索调用行为。
- Completion: P4A/P4C/P4G反例不成立；正向与合法缺档对照成立；必需失败可观察且无伪count。

### R-3: 当前已知邮箱失效不得投影许可

- Resolves: V-12。Root cause: 输出operatorStatus有ES回退，而许可辅助函数只看contact。
- Files: Q, QT；仅必要精确位置同步SEAM。
- Change: 现有DTO对同一邮箱使用一致的当前有效状态依据。无contact但ES当前EMAIL_INVALID时，不能输出ADMITTED；沿用UNKNOWN及既有抑制/人工状态语义。
- Discriminating regression: P4D无contact、同指纹非stale、AUTO_PASSED、未抑制、ES EMAIL_INVALID→非ADMITTED；contact invalid、正常有效邮箱对照与contact真值不可读均覆盖同一边界。
- Existing verification: C12,C13,C14,C15,C21,C22。
- Must not change: 准入不等于发送许可、stale/抑制UNKNOWN保护、原links/DTO字段；frontend保持。
- Prohibited: 新状态枚举、改UI掩盖后端矛盾、把所有成员均置不可用。
- Completion: P4D负例不显示可发送，正常有效邮箱对照保持。

### R-4: 部分目标proof完成后仍可原恢复链续跑

- Resolves: V-29。Root cause: !anyBlank&&!allAtTarget一律ROUTE_TO_REPAIR。
- Files: REC, RECT。
- Change: 经当前重新回源/唯一身份/保护核验，部分现存层已是同一目标proof而其他层仍缺该proof的恢复申请，不能仅因姓名已齐被路由出02；继续现有按层CAS补齐，再完成登记/准入，最后才APPLIED。初始全名且没有本次目标proof的纯01场景、第三proof/姓名冲突仍保守按原合同处理。
- Discriminating regression: P4B RAW空名、Candidate全名缺proof；首次Candidate写500导致PARTIAL且RAW完成；解除失败重新preview/apply，剩余proof闭合，人员登记/准入正常结束。第三proof/来源或版本变动对照不得强写。
- Existing verification: C03,C04,C05,C06,C10,C21。
- Must not change: 原文locator/字节绑定、人工/学术/contact保护、真实ID、各层seq/term CAS、不创建缺失层。
- Prohibited: 名字齐当proof齐、略过失败层、允许第三proof、放宽初始02范围或绕过准入。
- Completion: P4B同恢复流程安全闭合；重跑幂等，非目标字段与缺层保持。

### R-5: 自动重验先形成当前policy结论

- Resolves: V-30。Root cause: 基础硬理由空时复用旧AUTO_PASSED并提前投影。
- Files: REV, REVT。
- Change: 非有效人工例外的发现重验必须先经现有DiscoveryReview/AdmissionPolicy形成当前结论，再决定既有候选投影；不能用同身份旧自动准入替代当前机构/身份等硬事实。按现有CAS/共享写接缝更新，不改变policy本身。
- Discriminating regression: 实际policy+Revalidation+Review解析，同身份旧AUTO_PASSED，当前机构凭证INVALID且基础eligibility通过：得到当前NEEDS_REVIEW/硬原因，不据旧AUTO_PASSED晋升。有效凭证、仅六软资料缺失、同身份人工批准/历史认可和HOLD/REJECTED对照保持。
- Existing verification: C01,C02,C03,C10,C21。
- Must not change: 原有效人工例外、身份变化规则、六软原因hints、APPLICATION/人工字段保护、classifier和基础资格SSOT。
- Prohibited: 仅修改测试期望、把INVALID降软、对全部旧批准强制人工、另建policy来源。
- Completion: P4F不再用旧AUTO_PASSED投影；当前结论/候选结果一致，正常及人工对照仍合法。

### R-6: 恢复已批准真实入口测试合同

- Resolves: V-31（mandatory P2）；不重开已解决V-27产品任务。
- Files: GT, INITT, MIT, MEMT。必要用例可集中在现有GT，其他授权测试未需要则不改。
- Change: 取消admitted恒true与撤销断言缺失造成的伪结论。持久回归使用实际Initial/ManualInitial/ManualExpert、实际guard/recipient/selector、真实共享MySQL/事务/相关仓储、假SMTP，保留当前准入DTO语义一致；只替代外部依赖，不mock被验证决策/业务持久化。
- Discriminating regression: reserve合格→本次NEW contact/PREPARED attempt→compose前后当前准入撤销，三个实际入口各SMTP0且非SENT；抑制/邮箱验证失效也不能因自身行豁免。实际recipient/guard未撤销对照可claim，实际入口许可对照SMTP恰1并分别记录真实业务结果，不能用全0代替。
- Preserve/test: 接受后业务失败/不明结果时持久保护仍在，另两个实际入口再次请求不能再发。已知Initial FK负向测试改准确名称并精确断言异常约束链、SMTP1、SENDING；删除宽泛catch掩盖未知异常的模式。SAFE_RETRY仍仅明确未发/原地址。
- E01 fidelity: 保留四个精确方法名和显式b2Acceptance门禁，但内容须各调用对应实际服务；前三项检查SMTP和成功业务持久化，第四项三实际入口共享同人真库并竞争。已知Initial FK应使完整健康实验如实失败，保存精确链/SMTP/保护证据；不可把expected FK或guard-only改称healthy PASS。R-6强制回归不能放在b2Acceptance条件后。
- Existing verification: C02,C16,C17,C21,C22；E01按下述非产品修复证据规则实际执行。
- Must not change: 原业务成功/事务合同、既有保护、不增加生产写权限。
- Prohibited: 修FK、造id0行、mock成功helper/事务、降低断言、以仅guard或顺序调用冒充三真实入口并发、放宽SMTP精确计数。
- Completion: 已批准负向/许可/失败保护回归真实且通过；E01真实执行并如实分类。已知FK造成健康失败不反向成为本任务越权修产品的许可。

## Verification Commands

下列命令均在指定worktree、最终可辨认产品边界新鲜执行，Maven串行。C01—C19/C21/C22共21条为必需PASS集合；全部失败、重跑、counts/skips完整保留。C02本次JUnit通过但Node失败不是下次豁免，不改无关测试强行绿。C20仅已批准旧Zulu11.0.15/tzdb例外；C21现有Zulu11.0.32.1执行完整JDK11 clean package。C21条件skip不能替代C07/C09/C11/C17真库执行。

E01为必需实际采集、非6任务无条件PASS门槛；四个测试必须按R-6真实实现。只因已知Initial FK使健康实验失败时保留B-2 BLOCKED，不能称通过。其他新失败须按具体证据分类，不能统一归FK，也不能抹去本6任务相关失败。

- C01: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewControllerTest,ExpertRevalidationServiceTest,ExpertRevalidationServiceBehaviorTest test`
- C02: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest test`
- C03: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryIdentityRepairServiceTest,ExpertIndexWriterServiceTest,DiscoveryPipelineServiceTest test`
- C04: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityTest,DiscoveryReviewServiceTest test`
- C05: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityRecoveryServiceTest,DiscoveryIdentityRecoveryControllerTest,OpenAlexDataSourceTest,OpenAlexRequestPolicyTest test`
- C06: `python3 -m unittest discover -s tools/discovery-identity-recovery -p 'test_*.py'`
- C07: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonRepositoryTest test`
- C08: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonServiceTest test`
- C09: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test`
- C10: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityRepairServiceTest,DiscoveryIdentityRecoveryServiceTest test`
- C11: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonServiceTest,DiscoveryPersonRepositoryTest test`
- C12: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonQueryServiceTest,ExpertSearchServiceTest,ExpertIndexControllerTest,ExpertContactManagementControllerTest test`
- C13: `node --check src/main/resources/static/app.js`
- C14: `node --test src/test/js/discoveryPersonEmails.test.js`
- C15: `node --test src/test/js/*.test.js`
- C16: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonRecipientServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,OutreachTargetIteratorTest test`
- C17: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonOutreachGuardServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,ManualExpertMailServiceTest test`
- C18: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonMaintenanceServiceTest,DiscoveryPersonMaintenanceControllerTest test`
- C19: `python3 -m unittest discover -s tools/discovery-person-recovery -p 'test_*.py'`
- C20: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
- C21: `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn clean package`
- C22: `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -Dtest=OperatorStatusWriteSeamGuardTest test`
- E01: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Db2Acceptance=true '-Dtest=DiscoveryPersonOutreachGuardServiceTest#b2HealthyInitial+b2HealthyManualInitial+b2HealthyManualExpert+b2SamePersonCompetition' test`

## Completion Criteria

- R-1—R-6全部完成；V-3/V-5/V-12/V-13/V-29/V-30及mandatory V-31有对应辨别证据，原已解决根因保持。五产品根因与一个测试合同任务不能互相替代。
- 21条必需PASS命令在最终产品边界通过；E01真实实现、执行并逐项记录。若仅已知FK/原B-2前置条件导致E01健康失败，而本6任务与21命令均通过，可以有界提交并输出READY_FOR_VERIFICATION，同时明确B-1/B-2 BLOCKED。不得称master PASS/完整端到端PASS/READY_TO_INTEGRATE。
- Diff为18文件子集，每hunk映射本任务；无新文件/schema/FK/caller产品/无关清理。执行前核对worktree/branch、master与amendments、1ab2881产品祖先及已有diff归属；证据提交可推进HEAD，必须记录实际SHA。不得reset/stash覆盖其他工作。
- B-1/B-2缺证和人工验收仍独立门禁；本草案不降低master阈值、不接受人工结果、不操作生产。没有自动下一轮、continuation、并行repair或默认拆分计划。

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
后续人源明确调用当前精确文件才批准其当时内容及下列有限交付。旧hash的execute-p与本次review-p规划均不构成执行批准：

`$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fix/discovery-identity-recovery-master/repair.md`

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fix/discovery-identity-recovery-master/repair.md` invocation authorizes:

1. Only the 18 Authorized Files and required verification/evidence commands in this plan.
2. After all six repair tasks and all 21 required PASS commands succeed, and E01 is honestly classified under Completion Criteria, exactly one local product commit before emitting READY_FOR_VERIFICATION, staging only Authorized Files. Product commit subject: `fix(discovery): bind current identity and admission evidence`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/review/2026-10-13-discovery-identity-recovery-master/repair-execution.md` with exact human approval source, current repair path/hash, prior/new product SHAs, evidence parent, authorized changed files, every command/exit/count/skip/failure/retry, E01 interpretation, B-1/B-2/FK status, deviations, executor identity when available, and clean product/index evidence. Disclose unrelated dirty files honestly. This is the only extra authorized execution-evidence file.
4. Exactly one docs-only evidence commit containing only that execution handoff. Evidence commit subject: `docs(review-fast-p): record current evidence repair execution`.
5. Returning to the already authorized review-fast-p aggregate re-review in the same task when the human invocation requests it; review full 9d353934b82ed3e36a193c70748e5efccb3d9e82..new committed product head, preserve prior epochs/IDs.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, FK/schema repair, or product repair beyond this plan. Executor does not edit master/fast/review ledger/machine-verification/manual-acceptance/final-decision. Drafting authorizes no implementation or commit.

## Self-Review

- 七个included findings均为已证合同内mandatory违约；V-5/V-13同根合为R-2，6任务均有精确文件、行为、辨别回归、现有命令、保持/禁止项与完成标准。
- 18个现有文件只覆盖上述根因。新增REV/REVT来自原admission合同；不借文件授权修旧FK。旧28表不继承。
- B-1/B-2、无关既有FK、可选P2、已解决项、瞬时计时观察及未证风险均排除；R-6仅落实既有必需实验真实性，E01已知前提失败不会被改写为绿或迫使越权产品修复。
- 本轮verify-p为FAIL/PROGRESSING，故一次repair-p规划eligible；未把Epoch3 DIVERGING追改或复用旧人源批准。只维护一个当前repair.md，无自动执行/修复循环。
