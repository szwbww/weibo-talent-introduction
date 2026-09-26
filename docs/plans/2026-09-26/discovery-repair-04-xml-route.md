# 04：PMC 路由与结构化作者联系信息

状态：待评审、未实施。目标main；本次仅创建计划。对应 DD-07、DD-19、DD-20、DD-21、DD-22。

前置：[03：邮箱文字规范化](discovery-repair-03-email-text.md)完成机器验证后，以其产物为基线。

## 需求描述

已有可信PMC链接进入现有XML链；恢复数字脚注、多语言姓名容器及明确贡献者联系区；修正明确“姓, 名”的元数据拆分。

必须保持：

- M1：姓名/邮箱/作者ID必须有同一来源的唯一关系；歧义不绑定、同名不同邮箱不合并，同一人多个明确邮箱可保留。
- M2：邮箱验证、人才资格、create写入/重复不覆盖、原发送配置保持；不增加隐藏发送门禁。
- M3：人工暂停、既有额度/下载大小/运行时限保持；不回填、删除或重命名线上存量，不自动部署或定时验证。06只有明确列出的末页参数是有意变化。

范围外：不新增DOI/PMID远程映射请求，不下载付费OpenAlex Content，不自动音译/姓名猜测，不做通用正文作者识别。CORE与OpenAlex只修一个逗号且两侧非空的显式倒序，其他姓名维持现有行为。

## 关键不变量

### Invariant I-1：PMC编号可信且无冲突
- Rule：从ids.pmcid与结构化locations.landing_page_url搜集规范PMC[0-9]+；链接仅允许http/https、精确主机pmc.ncbi.nlm.nih.gov、www.ncbi.nlm.nih.gov、europepmc.org、www.europepmc.org，路径分别/articles/PMC...、/pmc/articles/PMC...；去尾斜杠和query后验证整个路径。单一不同编号才采用；冲突pmcId=null，不任取第一条。既有XML补OpenAlex作者ID仍仅凭唯一ORCID等值。
- Applies to：OpenAlex.parseResponse→extractAuthorEmails
- Violation consequence：错误论文被路由为证据
- 来源：original

### Invariant I-2：名字只做有据结构读取
- Rule：OpenAlex/CORE display_name只有一个逗号且两侧非空：左family/rightgiven；无逗号沿现行分拆；多逗号/空侧不新猜。JATS同一个contrib中单个直接name优先；否则单个name-alternatives：一个完整name可用，多项时只有唯一xml:lang=en的完整name可选；无唯一可选项保留邮箱为空身份。不同contrib从不按名字合并，ORCID多值冲突仍null。
- Applies to：metadata→PaperAuthor；contrib→Author
- Violation consequence：把格式变体合并成另一作者
- 来源：K-author-identity-needs-email-evidence

### Invariant I-3：XML联系范围有限
- Rule：numeric label只在corresp/fn目标的直接label节点剔除文本，不忽略普通正文数字；xref目标id唯一且owner唯一、无显式冲突才归属。额外只遍历本article中sec-type=contrib-info的Contributor Information段落：排除ref-list/sub-article，在独立p中要求一个完整作者姓名+email，唯一匹配front贡献者；与其他路径的冲突进入统一候选消解。
- Applies to：JATS候选生成→最终groupBy
- Violation consequence：共享脚注或引用邮箱被错绑
- 来源：K-author-identity-needs-email-evidence

### Invariant I-4：版本和外部ID边界
- Rule：EXTRACTION_VERSION=20260928；VERSION不变。JATS姓名为XML自身身份；不能凭同名把OA作者ID接上；只有现有唯一ORCID路径允许接ID。现存专家不重命名。
- Applies to：JATS→OA适配→cache→consumer
- Violation consequence：证据跨来源拼接
- 来源：K-identity-cache-version-and-admission

### Invariant I-5：既有业务边界
- Rule：M1～M3必须保持。未知身份不得调用邮箱验证/专家写入；明确身份仍过原验证和资格，重复不覆盖；不创建新发送拦截字段、黑名单或后台定时任务。
- Applies to：本计划列出的生产文件及其既有consumer/writer调用。
- Violation consequence：为提高覆盖而改变专家准入或发信配置。
- 来源：会话要求；K-author-identity-needs-email-evidence。

## 现状审计

OpenAlexDataSource.kt:227只读ids.pmcid，:243按首空格拆名；CoreDataSource.kt:196同样拆名。JatsXmlEmailParser.kt:43～46/:135要求直接name，:75只取front作者说明，:154～157未排除label。真实W3035965352 location=EuropePMC/PMC7759461而pmcId=null；真实XML贡献者区3对联系信息输出[]。数字label/name-alternatives为合成最小复现。交互 X2/X4/X8；审计附件C2/C3/C6。

[审计附件](discovery-repair-audit.md)列出的相关C表schema、写路径、读路径与交互点是本节组成部分；[原始检索回执](discovery-repair-evidence/)保留命令和逐行输出。非本计划文件只允许只读回归，不能借审计扩大改动范围。新增测试资源写于实施期，测试classloader只读；不新建线上文件存储。

缓存影响：抽取版本从前一子计划产物升级至20260928。当前pipeline非空旧缓存不会重抽，会以IDENTITY_EXTRACTION_VERSION_UNSUPPORTED失败；本计划保留并测试这项现有行为，不新增兼容白名单。上线前要只读统计旧版本活跃job；若存在需要保留的结果，先单独决定处置，不能以本计划授权清空队列。schema及payload_version不变。

## 实现方案

### T-1：导入证据并建立RED（I-1/I-2/I-3，I-5）

src/test/resources/discovery/xml-route-recall.zip：包含原PMC7759461.xml、openalex-pmc-work.json、W4385245566 metadata.json、数字label/name-alternatives控制；内含manifest.sha及真实/合成标签。src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt、src/test/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSourceTest.kt、src/test/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParserTest.kt使用入口实际解析，不mock作者归属。

### T-2：修路由和逗号分拆（I-1/I-2/I-4，I-5）

src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt：局部private PMC候选归一化；OA与src/main/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSource.kt各在现有name拆分处添加同样窄分支，不抽新名字服务，不增加PaperAuthor字段。

### T-3：补XML已证实结构（I-2/I-3/I-4，I-5）

src/main/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParser.kt：在现有Author/Candidate体系内读取name-alternatives、过滤目标直接label、追加contrib-info候选；节点身份仍按Element而非名字去重。

### T-4：校验整个入口（I-1/I-2/I-3/I-4，I-5）

src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt：真实OA metadata→请求PMC XML替身→实际parser→consume；src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt与src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt更新抽取版本并测旧缓存。

### T-5：生成可核验的验收输出（I-1～I-5）

在已列出的测试文件里用现有临时目录/JSON工具输出 `target/discovery-plan-acceptance/04.json`，包含输入fixture标识/哈希、实际解析或请求输出、checkpoint/终态及必要ES请求结果。验收数字来自实际函数调用和替身记录，不硬编码“passed=true”冒充证据。测试不访问外网和生产。人工看输出即可，不需要阅读测试实现。保存这些衍生报告不增加生产数据写路径；不在此时生成-acceptance.md。

## 变更文件清单

共10个文件，2个子系统（元数据适配、XML解析）；下表为穷尽清单。无新增共享存储字段；06新增的是应用配置属性，不是DB/ES字段。未列文件不可修改。

| # | 文件 |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSource.kt` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParser.kt` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSourceTest.kt` |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParserTest.kt` |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` |
| 10 | `src/test/resources/discovery/xml-route-recall.zip` |

## 验收标准

- I-1：W3035965352得到PMC7759461，首先请求XML；伪装主机europepmc.org.evil、query中的PMC、两个不同PMC均不采纳；无额外远程映射请求。
- I-2：Jakubův, Jan得到given=Jan、family=Jakubův；无逗号正例不变。数字label控制与name-alternatives单name均Jane/Doe；多语言无唯一英文项保留邮箱但空身份；重复同名contrib仍冲突。
- I-3：真实XML恰好恢复本次证明的3对：millman@berkeley.edu→K. Jarrod Millman；stefanv@berkeley.edu→Stéfan J. van der Walt；ralf.gommers@gmail.com→Ralf Gommers。引用、多人段落、重复id、前后标签冲突均不绑定。
- I-4：OA作者ID仅沿ORCID等值补充；consumer资格/验证替身通过时这3个独立邮箱RAW=3候选=3；旧版本拒绝，原有shared-note测试全过。
- I-5 / M1～M3：未知邮箱验证调用0/RAW写0；明确邮箱验证拒绝RAW=0；资格拒绝RAW=1候选=0；同邮箱重复新增0且原字段不变；同名不同邮箱各1条；一人两个明确邮箱各1条。暂停后新的消费写入0；源码diff不涉及发送配置、发送服务、迁移或线上数据脚本。
- 交互覆盖：每条人工A项中标明X路径；真实案例必须完整原文/metadata，允许mock外部HTTP/验证/资格/ES，不允许mock身份解析。

定向命令（JDK11）：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OpenAlexDataSourceTest,CoreDataSourceTest,JatsXmlEmailParserTest,EuropePmcDataSourceTest,PmcOaDataSourceTest,ExpertDiscoveryServiceTest,DiscoveryIdentityTest,DiscoveryPipelineServiceTest
```

定向通过后按fix-v独立机器验证；最终联合`mvn clean package`。若已有断言被本计划有意改变，只修改清单内的对应断言并保留旧场景反例；不靠删除测试通过。测试或文件范围不足先修订计划，不在执行中扩项。

## 人工验收清单

### A-1：真实PMC链
- 前置条件：使用归档真实OA metadata与XML作为HTTP响应；邮箱验证/资格替身通过、隔离ES为空。
- 操作步骤：1. 在仓库根执行上面的定向命令；2. 运行测试，打开 target/discovery-plan-acceptance/04.json；对照归档Contributor Information三个p段落。
- 预期结果：pmcId=PMC7759461；首请求XML；三对姓名邮箱与上述列表完全相符；RAW=3、候选=3；同论文不重复计算两份新增。
- 覆盖：I-1/I-3/I-4，X2/X4/X8，需求。

### A-2：格式与冲突
- 前置条件：输入数字label、name-alternatives、伪主机/多个PMC、重复contrib、姓逗号名控制夹具。
- 操作步骤：1. 在仓库根执行上面的定向命令；2. 对比报告对应案例的输入/结果/请求数。
- 预期结果：Jane/Doe两个正例通过；多义name-alternatives邮箱保留但身份null；伪主机不发PMC请求；重复contrib不绑；Jakubův, Jan转换为Jan Jakubův。
- 覆盖：I-1/I-2/I-3，X4/X8，需求。

### A-3：业务边界回归
- 前置条件：沿用ExpertDiscoveryServiceTest现有明确身份/未知/无效/资格拒绝/重复/同名/多邮箱/暂停场景；只补本计划缺失的断言或报告输出，不为每份子计划复制一套测试。外部验证与ES均为现有隔离替身。
- 操作步骤：1. 执行定向命令；2. 查看同编号JSON中的boundaryCases，按输入、验证调用、RAW/CANDIDATE请求及旧文档比较；3. 查看git diff --name-only，确认未出现发送配置、发送服务、迁移和线上数据脚本。
- 预期结果：未知与无效邮箱RAW=0；资格拒绝RAW=1候选=0；明确合格RAW=1候选=1；重复新增0、旧身份不变；同名不同邮箱2条、一人两明确邮箱2条；暂停后新增消费写0；上述禁止范围改动0个文件。
- 覆盖：I-5，M1/M2/M3，X4/X5/X8（本计划触及的入口）。

### A-4：队列新旧结果
- 前置条件：隔离pipeline fixture中各放一个前版和本版20260928的已缓存明确邮箱结果，metadata/payload_version合法；不操作生产队列。
- 操作步骤：1. 跑本计划定向命令；2. 看报告versionCases的消费与最终状态。
- 预期结果：本版正常经过原邮箱/资格判断；前版错误IDENTITY_EXTRACTION_VERSION_UNSUPPORTED且专家写入0，既有pipeline转FAILED，不会自动重抽；证据VERSION仍20260925。
- 覆盖：版本不变量，X2。

人工验收开始时才从本节导出同目录同前缀-acceptance.md，包含勾选框、验收人、日期、结果/备注。此刻不生成。

## 自查结论

已按create-p检查：具备不变量/审计/逐文件任务/机器及人工验收；每任务引用I编号；文件≤10、子系统≤2、共享存储新字段=0；前端仅08且S-1覆盖新增DOM；真实/合成证据分开。状态仍待评审，未执行测试、未实施代码。
