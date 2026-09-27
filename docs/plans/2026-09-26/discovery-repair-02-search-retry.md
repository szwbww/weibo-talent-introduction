# 02：OpenAlex 同页有限重试

状态：待评审、未实施。目标main；本次仅创建计划。对应 DD-11。

前置：[01：去重失败保留失败页](discovery-repair-01-page-replay.md)完成机器验证后，以其产物为基线。

## 需求描述

同步发现遇到已确认的暂时网络故障时，先对同一OpenAlex页有限重试；成功后继续OpenAlex，失败上限后保留位置并切换其他来源。

必须保持：

- M1：姓名/邮箱/作者ID必须有同一来源的唯一关系；歧义不绑定、同名不同邮箱不合并，同一人多个明确邮箱可保留。
- M2：邮箱验证、人才资格、create写入/重复不覆盖、原发送配置保持；不增加隐藏发送门禁。
- M3：人工暂停、既有额度/下载大小/运行时限保持；不回填、删除或重命名线上存量，不自动部署或定时验证。06只有明确列出的末页参数是有意变化。

范围外：只改同步OpenAlex元数据循环。不为PDF下载再加重试，不改其他来源的现有429/503五次熔断，不改队列已有5分钟延期，不加数据库/配置项/通用重试框架。

## 关键不变量

### Invariant I-1：同页总共三次
- Rule：同步OpenAlex每页最多3次实际搜索尝试（首次+2次重试）。只重试连接/读取超时、连接重置/EOF、HTTP500/502/503/504，以及已观察到的远端握手中断；遍历cause链若出现CertificateException、SSLPeerUnverifiedException或证书校验/协议不兼容则不得重试。普通解析异常、HTTP400/401/403/404不得重试。
- Applies to：同步OpenAlex搜索catch分支
- Violation consequence：盲重试证书或永久错误，消耗额度
- 来源：original

### Invariant I-2：暂停预算先于重试
- Rule：等待采用1秒、2秒退避，各可加0～200ms抖动；每不超过100ms检查人工取消与运行deadline。到点不发下一次请求。InterruptedException恢复线程中断并退出；OpenAlexBudgetDeferredException原样走BUDGET_DEFERRED，不按SEARCH_FAILED重试。OA429不加入网络重试范围：保留cursor，以BUDGET_DEFERRED结束本源；不等待长Retry-After，不记SEARCH_FAILED。响应冷却仍由现有policy记录。
- Applies to：等待与每次source.searchPapers调用
- Violation consequence：暂停失效或绕过额度
- 来源：K-discovery-budget-backpressure

### Invariant I-3：尝试不等于消费
- Rule：每次重试重新searchPapers→getJson→reserve；apiRequests沿用service调用适配器次数的现有口径（被policy提前延期的调用也可能计入），实际出站HTTP次数用HTTP替身核对，不把apiRequests冒充计费数；此前UNKNOWN credit不得退款。成功前不累计papersSearched、不推进cursor；成功即清零该页连续失败次数（包括有nextCursor的空页）。用尽3次后只记一次终止SEARCH_FAILED；日志给attempt/3及异常类型，不打印带key的URL。
- Applies to：请求→policy→统计→checkpoint
- Violation consequence：重复累计论文或虚报消耗
- 来源：original

### Invariant I-4：既有业务边界
- Rule：M1～M3必须保持。未知身份不得调用邮箱验证/专家写入；明确身份仍过原验证和资格，重复不覆盖；不创建新发送拦截字段、黑名单或后台定时任务。
- Applies to：本计划列出的生产文件及其既有consumer/writer调用。
- Violation consequence：为提高覆盖而改变专家准入或发信配置。
- 来源：会话要求；K-author-identity-needs-email-evidence。

## 现状审计

ExpertDiscoveryService.kt:654～695：apiRequests++，一次searchPapers异常直接SEARCH_FAILED；429/503已有5次循环。OpenAlexDataSource.kt:74～88每次请求走reserve与结算。DiscoveryPipelineService.kt:853/:1434已给队列采集错误5分钟延期。FetchRetry.kt:10～17把IOException均视可恢复，不能原样用于本计划的TLS判别。现有sleepInterruptible(:1980)按1秒切片且无deadline，供学术补全使用；本计划不改该共享补全行为，仅加发现重试所需的局部有界等待。交互 X1/X3/X6/X7。

[审计附件](discovery-repair-audit.md)列出的相关C表schema、写路径、读路径与交互点是本节组成部分；[原始检索回执](discovery-repair-evidence/)保留命令和逐行输出。非本计划文件只允许只读回归，不能借审计扩大改动范围。新增测试资源写于实施期，测试classloader只读；不新建线上文件存储。

## 实现方案

### T-1：建立故障序列测试（I-1/I-2/I-3，I-4）

src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt：同cursor先抛远端握手中断、再成功；三次超时；503/502/500/504；证书错误403单次；预算延期；重试等待中暂停。保存请求次数、cursor、计数与终态。

### T-2：只修现有循环（I-1/I-2/I-3，I-4）

src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt：在discoverFromSource现有catch路径内加OpenAlex专用可恢复判别及本页attempt计数；小型private等待方法，使用不超过100ms的分段sleep并检查现有取消/deadline；不新增注入接口或Spring服务。OA429直接保留位置，以BUDGET_DEFERRED结束本源，响应已由现有policy记录；非OA分支保持现有五次熔断。

### T-3：防止叠加重试（I-2/I-3，I-4）

src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt：测同期队列collectQueuePage仍单次返回errorReason，随后由pipeline延期；本计划不修改DiscoveryPipelineService。沿用OpenAlexRequestPolicyTest验证reserve与UNKNOWN语义。

### T-4：生成可核验的验收输出（I-1～I-4）

在已列出的测试文件里用现有临时目录/JSON工具输出 `target/discovery-plan-acceptance/02.json`，包含输入fixture标识/哈希、实际解析或请求输出、checkpoint/终态及必要ES请求结果。验收数字来自实际函数调用和替身记录，不硬编码“passed=true”冒充证据。测试不访问外网和生产。人工看输出即可，不需要阅读测试实现。保存这些衍生报告不增加生产数据写路径；不在此时生成-acceptance.md。

## 变更文件清单

共2个文件，1个生产子系统（发现编排）；下表为穷尽清单。无新增共享存储字段；06新增的是应用配置属性，不是DB/ES字段。未列文件不可修改。

| # | 文件 |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` |

## 验收标准

- I-1：暂时失败后成功调用2次且cursor完全相等；连续失败实际调用3次；证书/403调用1次。
- I-2：暂停/截止后额外调用0次；预算延期不记SEARCH_FAILED；OA429不睡完长冷却；非OA429/503现有五次测试通过。
- I-3：失败两次成功一次的100篇页papersSearched=100，不是300；仅用尽重试才sourceFailureCount+1。真实policy每次独立reserve，UNKNOWN无退款。队列错误仍延期5分钟。
- I-4 / M1～M3：未知邮箱验证调用0/RAW写0；明确邮箱验证拒绝RAW=0；资格拒绝RAW=1候选=0；同邮箱重复新增0且原字段不变；同名不同邮箱各1条；一人两个明确邮箱各1条。暂停后新的消费写入0；源码diff不涉及发送配置、发送服务、迁移或线上数据脚本。
- 交互覆盖：每条人工A项中标明X路径；真实案例必须完整原文/metadata，允许mock外部HTTP/验证/资格/ES，不允许mock身份解析。

定向命令（JDK11）：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,OpenAlexDataSourceTest,OpenAlexRequestPolicyTest,DiscoveryPipelineServiceTest
```

定向通过后按fix-v独立机器验证；最终联合`mvn clean package`。若已有断言被本计划有意改变，只修改清单内的对应断言并保留旧场景反例；不靠删除测试通过。测试或文件范围不足先修订计划，不在执行中扩项。

## 人工验收清单

### A-1：同页暂时故障
- 前置条件：测试fixture：OA当前cursor=C1，第一次远端握手中断，第二次成功返回1篇；下一来源为CROSSREF。
- 操作步骤：1. 在仓库根执行上面的定向命令；2. 运行测试并查看 target/discovery-plan-acceptance/02.json 的请求时间序列。
- 预期结果：前2次均OA/C1；成功后才继续OA下一页；apiRequests中这两次=2，论文计数=1。
- 覆盖：I-1/I-3，X1/X6/X7，需求。

### A-2：无法恢复与停止
- 前置条件：fixture含3次超时、1次证书失败、等待期间取消、budget Deferred四组；队列使用现有stream替身。
- 操作步骤：1. 在仓库根执行上面的定向命令；2. 检查每组调用次数、停止原因、cursor；查看队列下一次时间。
- 预期结果：超时3次后SEARCH_FAILED且保留cursor；证书失败1次；取消后调用0次且CANCELLED；预算=BUDGET_DEFERRED；队列next_attempt_at=失败时刻+5分钟。
- 覆盖：I-1/I-2/I-3，X1/X3/X6。

### A-3：业务边界回归
- 前置条件：沿用ExpertDiscoveryServiceTest现有明确身份/未知/无效/资格拒绝/重复/同名/多邮箱/暂停场景；只补本计划缺失的断言或报告输出，不为每份子计划复制一套测试。外部验证与ES均为现有隔离替身。
- 操作步骤：1. 执行定向命令；2. 查看同编号JSON中的boundaryCases，按输入、验证调用、RAW/CANDIDATE请求及旧文档比较；3. 查看git diff --name-only，确认未出现发送配置、发送服务、迁移和线上数据脚本。
- 预期结果：未知与无效邮箱RAW=0；资格拒绝RAW=1候选=0；明确合格RAW=1候选=1；重复新增0、旧身份不变；同名不同邮箱2条、一人两明确邮箱2条；暂停后新增消费写0；上述禁止范围改动0个文件。
- 覆盖：I-4，M1/M2/M3，X4/X5/X8（本计划触及的入口）。

人工验收开始时才从本节导出同目录同前缀-acceptance.md，包含勾选框、验收人、日期、结果/备注。此刻不生成。

## 自查结论

已按create-p检查：具备不变量/审计/逐文件任务/机器及人工验收；每任务引用I编号；文件≤10、子系统≤2、共享存储新字段=0；前端仅08且S-1覆盖新增DOM；真实/合成证据分开。状态仍待评审，未执行测试、未实施代码。
