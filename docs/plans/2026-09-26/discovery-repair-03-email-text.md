# 03：邮箱文字规范化

状态：待评审、未实施。目标main；本次仅创建计划。对应 DD-06、DD-12、DD-14。

前置：[02：OpenAlex 同页有限重试](discovery-repair-02-search-retry.md)完成机器验证后，以其产物为基线。

## 需求描述

补出已验证的花括号邮箱与邮箱断行线索；明确姓名联系行中的(at)写法可得到与@相同的归属结果。补邮箱不自动推断作者。

必须保持：

- M1：姓名/邮箱/作者ID必须有同一来源的唯一关系；歧义不绑定、同名不同邮箱不合并，同一人多个明确邮箱可保留。
- M2：邮箱验证、人才资格、create写入/重复不覆盖、原发送配置保持；不增加隐藏发送门禁。
- M3：人工暂停、既有额度/下载大小/运行时限保持；不回填、删除或重命名线上存量，不自动部署或定时验证。06只有明确列出的末页参数是有意变化。

范围外：不做通用OCR、任意空白拼邮箱、任意分隔符修复；不改变运营邮箱黑名单；不修PDF版面、姓名逗号格式或JATS。

## 关键不变量

### Invariant I-1：只恢复明确邮箱语法
- Rule：brace只接受非嵌套{local1,local2}@domain，local-part逐项满足现有邮箱字符集，分隔符逗号或分号，可带空白；非法/空local项不生成半截地址。不按展开顺序绑定作者。断行仅修 local@ 后一个换行及紧邻合法domain，不能跨空行/段落/其他字词；保留原文和规范化文本供证据哈希。
- Applies to：PlainTextEmailExtractor与resolver输入
- Violation consequence：拼出不存在的邮箱或按顺序错绑
- 来源：original

### Invariant I-2：规范化同源且不抹平段落
- Rule：由PlainTextEmailExtractor暴露internal的纯规范化函数，extract与resolver在同一条有界联系记录上调用；不全局把换行替换空格。对(at)/(dot)的既有规则只统一应用，不扩大作者姓名匹配范围。SOURCE_SHA256仍基于原始联系记录，而非人工改写串。
- Applies to：resolveText、resolveHtml既有textClaims、CORE全文
- Violation consequence：邮箱能抽到却不能核验同一原文归属
- 来源：K-author-identity-needs-email-evidence

### Invariant I-3：版本与未知处理
- Rule：EXTRACTION_VERSION=20260927，证据VERSION=20260925不变；没有明确归属的新增邮箱仍为空身份、消费拒绝IDENTITY_UNRESOLVED。沿用旧缓存显式拒绝，不清库或自动重抽。
- Applies to：extractQueuedItem→consumeQueuedItem
- Violation consequence：旧抽取冒充新规则或新线索变无名专家
- 来源：K-identity-cache-version-and-admission

### Invariant I-4：既有业务边界
- Rule：M1～M3必须保持。未知身份不得调用邮箱验证/专家写入；明确身份仍过原验证和资格，重复不覆盖；不创建新发送拦截字段、黑名单或后台定时任务。
- Applies to：本计划列出的生产文件及其既有consumer/writer调用。
- Violation consequence：为提高覆盖而改变专家准入或发信配置。
- 来源：会话要求；K-author-identity-needs-email-evidence。

## 现状审计

PlainTextEmailExtractor.kt:23/39～43 先cleanObfuscation后brace展开，brace只支持字母/逗号；SourceAuthorEmailResolver.kt:58～84 用未规范化行做mailbox匹配。证据 more-probes.json中7条brace表达式共30个可展开字符串；W2995022099原始文本中 kairouz@ 换行 google.com。DD-14 Jane Doe 为明确标注的合成案例。交互 X2/X4/X8；存储约束引入审计附件C2/C3/C6。

[审计附件](discovery-repair-audit.md)列出的相关C表schema、写路径、读路径与交互点是本节组成部分；[原始检索回执](discovery-repair-evidence/)保留命令和逐行输出。非本计划文件只允许只读回归，不能借审计扩大改动范围。新增测试资源写于实施期，测试classloader只读；不新建线上文件存储。

缓存影响：抽取版本从前一子计划产物升级至20260927。当前pipeline非空旧缓存不会重抽，会以IDENTITY_EXTRACTION_VERSION_UNSUPPORTED失败；本计划保留并测试这项现有行为，不新增兼容白名单。上线前要只读统计旧版本活跃job；若存在需要保留的结果，先单独决定处置，不能以本计划授权清空队列。schema及payload_version不变。

## 实现方案

### T-1：固化逐字输入（I-1/I-2，I-4）

src/test/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractorTest.kt、src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt、src/test/resources/discovery/email-text-recall.json：从more-probes及原文取7条brace、断行上下文；记录来源路径/sha和人工预期，合成(at)/跨段/非法项反例独立标synthetic。

### T-2：最小规范化改动（I-1/I-2/I-3，I-4）

src/main/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractor.kt、src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt：提取内部纯normalizeContactText；合并发现邮箱与已证明claims，仍按邮箱去重/全候选冲突消解。原文entry随规范化保留，不增领域字段。

### T-3：验证消费边界（I-1/I-2/I-3，I-4）

src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt、src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt、src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt：版本按I-3；从真实extract输出消费，brace无归属不调用邮箱验证，不写RAW；合成明确Jane Doe: opaque(at)uni.edu通过既有邮箱/资格替身时写1条。

### T-4：生成可核验的验收输出（I-1～I-4）

在已列出的测试文件里用现有临时目录/JSON工具输出 `target/discovery-plan-acceptance/03.json`，包含输入fixture标识/哈希、实际解析或请求输出、checkpoint/终态及必要ES请求结果。验收数字来自实际函数调用和替身记录，不硬编码“passed=true”冒充证据。测试不访问外网和生产。人工看输出即可，不需要阅读测试实现。保存这些衍生报告不增加生产数据写路径；不在此时生成-acceptance.md。

## 变更文件清单

共8个文件，1个解析子系统及直接版本/测试边界；下表为穷尽清单。无新增共享存储字段；06新增的是应用配置属性，不是DB/ES字段。未列文件不可修改。

| # | 文件 |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractor.kt` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractorTest.kt` |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` |
| 8 | `src/test/resources/discovery/email-text-recall.json` |

## 验收标准

- I-1：7条原表达式产生与夹具完全相等的30个局部展开字符串（逐表达式比较，不保证30个新专家）；断行得到kairouz@google.com但不补姓名；跨空行不拼。
- I-2：Jane Doe两种写法结果同名同邮箱，保留各自原文哈希；共享列表、同名、跨段和运营邮箱负例仍拒绝。
- I-3：版本20260927新结果可消费、旧20260926拒绝；已存专家证据VERSION不变；新增未知线索RAW=0。
- I-4 / M1～M3：未知邮箱验证调用0/RAW写0；明确邮箱验证拒绝RAW=0；资格拒绝RAW=1候选=0；同邮箱重复新增0且原字段不变；同名不同邮箱各1条；一人两个明确邮箱各1条。暂停后新的消费写入0；源码diff不涉及发送配置、发送服务、迁移或线上数据脚本。
- 交互覆盖：每条人工A项中标明X路径；真实案例必须完整原文/metadata，允许mock外部HTTP/验证/资格/ES，不允许mock身份解析。

定向命令（JDK11）：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=PlainTextEmailExtractorTest,SourceAuthorEmailResolverTest,ExpertDiscoveryServiceTest,DiscoveryIdentityTest,CoreDataSourceTest,PdfEmailExtractorTest,DiscoveryPipelineServiceTest
```

定向通过后按fix-v独立机器验证；最终联合`mvn clean package`。若已有断言被本计划有意改变，只修改清单内的对应断言并保留旧场景反例；不靠删除测试通过。测试或文件范围不足先修订计划，不在执行中扩项。

## 人工验收清单

### A-1：真实邮箱线索
- 前置条件：fixture由7条原brace表达式与W2995022099原文断行生成，附路径/sha。
- 操作步骤：1. 在仓库根执行上面的定向命令；2. 运行测试；打开 target/discovery-plan-acceptance/03.json，逐表达式对照输入/输出。
- 预期结果：7组输出分别等于夹具预期，总30个展开项；断行邮箱出现；无独立作者证据的记录givenNames/familyNames均null，RAW新增0。
- 覆盖：I-1/I-3，X4，需求。

### A-2：反混淆和共享入口
- 前置条件：fixture含明确Jane Doe两种写法、共享作者列表、CORE同文本；验证与资格替身通过。
- 操作步骤：1. 在仓库根执行上面的定向命令；2. 对比报告解析结果和consumer请求。
- 预期结果：明确Jane两种写法均opaque@uni.edu/Jane/Doe；共享列表身份为空；CORE同样不靠邮箱拼写认人；一个新的明确正例RAW=1、候选=1。
- 覆盖：I-2/I-3，X2/X4/X8，需求。

### A-3：业务边界回归
- 前置条件：沿用ExpertDiscoveryServiceTest现有明确身份/未知/无效/资格拒绝/重复/同名/多邮箱/暂停场景；只补本计划缺失的断言或报告输出，不为每份子计划复制一套测试。外部验证与ES均为现有隔离替身。
- 操作步骤：1. 执行定向命令；2. 查看同编号JSON中的boundaryCases，按输入、验证调用、RAW/CANDIDATE请求及旧文档比较；3. 查看git diff --name-only，确认未出现发送配置、发送服务、迁移和线上数据脚本。
- 预期结果：未知与无效邮箱RAW=0；资格拒绝RAW=1候选=0；明确合格RAW=1候选=1；重复新增0、旧身份不变；同名不同邮箱2条、一人两明确邮箱2条；暂停后新增消费写0；上述禁止范围改动0个文件。
- 覆盖：I-4，M1/M2/M3，X4/X5/X8（本计划触及的入口）。

### A-4：队列新旧结果
- 前置条件：隔离pipeline fixture中各放一个前版和本版20260927的已缓存明确邮箱结果，metadata/payload_version合法；不操作生产队列。
- 操作步骤：1. 跑本计划定向命令；2. 看报告versionCases的消费与最终状态。
- 预期结果：本版正常经过原邮箱/资格判断；前版错误IDENTITY_EXTRACTION_VERSION_UNSUPPORTED且专家写入0，既有pipeline转FAILED，不会自动重抽；证据VERSION仍20260925。
- 覆盖：版本不变量，X2。

人工验收开始时才从本节导出同目录同前缀-acceptance.md，包含勾选框、验收人、日期、结果/备注。此刻不生成。

## 自查结论

已按create-p检查：具备不变量/审计/逐文件任务/机器及人工验收；每任务引用I编号；文件≤10、子系统≤2、共享存储新字段=0；前端仅08且S-1覆盖新增DOM；真实/合成证据分开。状态仍待评审，未执行测试、未实施代码。
