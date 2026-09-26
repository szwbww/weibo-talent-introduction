# 深度发现身份修复 05-outreach

授权：用户2026-09-25“你直接来修复…你修改吧”，执行已接受路线的必要子阶段。目标工作树：/Users/lukai/IdeaProjects/weibo-talent-introduction。按阶段顺序实施，不提交或推送。本文细化既定路线，不涉及UI或恢复发送。

## 需求描述

首发ES查询、统计分页、重试联系人内存过滤及两个实际首发service在创建联系人前统一身份门禁。发送前重新读取档案再次验身份，缺失拒绝。其他来源既有规则和材料提醒/回复保持，不开启任何发送配置。查询过滤匹配来源和VERIFIED版本，最终内存校验证据绑定与删除名单。

保持：已有业务关联键、其他来源的既有资格策略、发送暂停及历史邮件；本阶段不改前端。

## 关键不变量

### I-1：身份归属必须有证据
- 规则：深度发现身份缺证/冲突不得用于新专家准入、晋升、首发或学术身份推断；身份验证与邮箱有效性分开。
- 适用：本阶段所有读写和旁路；违反会重现错绑。
### I-2：兼容与不可覆盖
- 规则：不改旧业务键，不修改历史信件，不改变其他来源资格；新验证状态缺失不是VERIFIED，删除名单命中不自动解除。
- 适用：本阶段所有存储及消费者；违反会损坏历史关联或重新导入已清理数据。
### I-3：版本与可追溯
- 规则：规则版本显式为20260925；身份事实为一个顶层对象，来源、证据摘要与绑定邮箱/姓名同时保存；旧任务不能伪造新证据；失败有明确原因。
- 适用：抽取、存储、消费、补全；违反会让旧结果继续传播。

## 现状审计

InitialOutreachService.searchExpertsByTypesWithEmail、ManualInitialOutreachService.buildEsFiltersForLevel被计数分页共用；RecipientScope.matchesExpert读重试联系人最新profile；发送前两个循环均需挡旁路。来源：K-initial-outreach-four-gate-paths。expert_contact/mail_record仅通过原有写入路径写，本阶段不迁移、不改历史信件。

无新增联系人的独立写入路径。数据写入与读取都复用当前入口，具体修改点限定下表。共享类型阶段必须先完成，其余阶段依次消费它。

## 实现方案

首发ES查询、统计分页、重试联系人内存过滤及两个实际首发service在创建联系人前统一身份门禁。发送前重新读取档案再次验身份，缺失拒绝。其他来源既有规则和材料提醒/回复保持，不开启任何发送配置。查询过滤匹配来源和VERIFIED版本，最终内存校验证据绑定与删除名单。

实施下表各文件时均遵循I-1/I-2/I-3，新增测试覆盖真实行为与失败路径；不是放宽旧断言。JDK11执行测试；最终整套构建运行全部回归。旧测试中的已验证来源stub应明确附证据，新增无证据stub必须被拒绝。

## 变更文件清单

| 文件 | 动作 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt | 实现本阶段契约或对应回归；守卫测试仅机械行号修正 |
| src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt | 实现本阶段契约或对应回归；守卫测试仅机械行号修正 |
| src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt | 实现本阶段契约或对应回归；守卫测试仅机械行号修正 |
| src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt | 实现本阶段契约或对应回归；守卫测试仅机械行号修正 |
| src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt | 实现本阶段契约或对应回归；守卫测试仅机械行号修正 |
| src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt | 实现本阶段契约或对应回归；守卫测试仅机械行号修正 |
| src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt | 实现本阶段契约或对应回归；守卫测试仅机械行号修正 |
| src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt | 实现本阶段契约或对应回归；守卫测试仅机械行号修正 |

## 验收标准

- I-1：未知/冲突/删除名单均拒绝；已确认样本允许；真实同名不自动合并。
- I-2：非发现来源回归、业务键不变、历史邮件不写；首发设置不变。
- I-3：旧结果拒绝，新版本可重复读取；证据与当前邮箱/姓名不一致拒绝。
- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=InitialOutreachServiceTest,ManualInitialOutreachServiceTest,ExpertSearchServiceTest,OperatorStatusWriteSeamGuardTest -Dexec.skip=true`。
- 最终发布前：JDK11 `mvn clean package`；不使用跳过测试的包冒充通过。

## 人工验收清单

### A-1：明确与不明确来源
- 前置：隔离测试环境关闭发送，使用今天已确认的XML和多人无明确标签XML各一份，清空这两份测试任务的旧抽取结果。
- 操作：执行发现，按邮箱查询RAW/CANDIDATE并查看任务原因。
- 预期：明确邮箱姓名与原文相同；不明确新增0、晋升0；下游首次联系资格为拒绝。
- 覆盖：I-1/I-3；来源→入库→消费。
### A-2：历史保护
- 前置：隔离环境加载删除名单中1条邮箱和1条非发现来源正常专家，以及1条已有历史邮件。
- 操作：重放已删除邮箱的发现，预览非发现来源首发并查询历史邮件。
- 预期：删除邮箱新增0，非发现来源资格不受身份新增规则影响，历史邮件正文及发送时间不变，发送仍暂停。
- 覆盖：I-2；准入→晋升→发送资格。
### A-3：旧结果与身份变化
- 前置：隔离环境保存1份无规则版本抽取结果及1份当前版本明确结果；将后者绑定姓名改成不同作者。
- 操作：分别消费旧结果、原正确新结果和被篡改身份的档案资格预览。
- 预期：旧结果被隔离，正确结果通过身份门禁，姓名不一致档案被拒绝；不实际发信。
- 覆盖：I-1/I-3；队列→专家→资格。
