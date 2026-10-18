# 失败样本契约 v1

本文件是01～03的共同约束。下列限额为**本次设计的保守初值**，不是从线上已有配置推断出的值；它们不代表已测出的最优参数。

## C-1 配置与资源

新配置前缀 `talent-introduction.expert-discovery.samples`；在新配置文件内用独立@Configuration/@EnableConfigurationProperties注册，不改application.yml、RestTemplateConfig或Apollo配置。

|属性|默认值|语义|
|---|---:|---|
|enabled|false|关闭时不建立scope、不创建目录、不写诊断日志、不清理存量|
|directory|`/opt/talent/data/discovery-samples`|绝对路径，外部配置可覆盖；启用前确认非webroot、真实目录和服务用户权限|
|retentionDays|7|按capturedAt清理；开启且进程运行时启动/提交前/每小时清理，最长额外1小时；停机/关闭不承诺自动到期删除|
|maxTotalBytes|536870912|512MiB，所有样本目录及临时文件的逻辑字节数；不等于文件系统块占用（固定锁文件不计入样本预算）|
|maxTotalSamples|1000|全目录当前完整包总数上限；计数达到上限先淘汰最旧包，防止大量仅元数据包导致扫描膨胀|
|maxBodyBytes|10485760|每个完整正文最多10MiB；不能修改业务的下载大小限制|
|maxBodiesPerSample|2|按响应完成先后最多保留2个完整正文；超出记录BODY_LIMIT|
|maxMetadataBytes|1048576|单包JSON总长最多1MiB；作者或结果超限不截断造假，跳过整包METADATA_LIMIT|
|maxAttemptsPerSample|16|最多16条被观测全文尝试；之后attemptsTruncated=true且不再保留后续attempt的正文；不增加请求|
|maxSamplesPerBucketPerRun|20|每执行、每失败主桶当前留存数上限；清理后可再收，不是永久累计计数|
|maxSuccessControlsPerRun|10|每执行当前留存的SUCCESS_CONTROL上限|
|maxInFlightCaptures|2|拿不到许可立即跳过；单scope最多保留20MiB正文，额外滞留正文引用≤40MiB|

校验数字为正，body≤10MiB、bodies≤2、attempts≤16、metadata≤1MiB、inFlight≤2；允许调小，扩大硬上限需另评估。总容量必须≥一个最大包（正文限额×数量+metadata），否则配置无效。诊断配置无效时禁用采样并记CONFIG_ERROR，不让发现服务启动失败；不改变原发现配置校验。

只增加一个轻量store清理定时方法；不加独立任务队列、线程池、保活服务或归档任务。目录不可用/空间不足/锁忙均跳过，业务照常。文件IO有实际耗时；持锁时不做网络/解析，不保证绝对零延迟。按同一固定输入测试并记录开关两态耗时、请求数、额外内存；无实测前不得写性能收益。

## C-2 scope与选样

`DiscoverySampleContext(executionId:Long, source:String, entry:SYNC|QUEUE, sourceBatchNumber:Int?, queueJobId:Long?)` 不可变。执行ID必须>0；source必须严格OPENALEX；SYNC必须有正sourceBatchNumber、queueJobId=null；QUEUE必须有正queueJobId、sourceBatchNumber=null。sourceBatchNumber只是该来源分页编号，不冒充进度日志批次或Apollo每100档案的批次。

外层把context显式传到worker，在该worker内部进入ThreadLocal scope；普通ThreadLocal，不用InheritableThreadLocal。finally恢复前scope/释放许可；禁止跨线程复用可变容器。scope只包围`source.extractAuthorEmails(paper)`；返回后完成诊断，业务outcome原样返回；业务异常记录类名后原样重抛给既有catch。HostBusy不归EXTRACTION_EXCEPTION且不写失败包；delegate返回时若FulltextRequestGate.hostBusyInScope()为true也跳过，不能只检查抛出的异常；记录可限频的HOST_BUSY诊断。

主桶唯一，按顺序选择：业务异常→EXTRACTION_EXCEPTION；任一emails项givenNames或familyNames空白→IDENTITY_UNRESOLVED；failureReason=NO_PMC_ID→NO_FULLTEXT_ADDRESS；failureReason以NO_EMAIL_IN_开头→NO_EMAIL；PDF_TOO_LARGE→BODY_TOO_LARGE；PDF_DOWNLOAD_FAILED/FULLTEXT_FETCH_FAILED→DOWNLOAD_FAILED；PDF_PARSE_FAILED/XML_PARSE_FAILED→PARSE_FAILED；其它非空失败/空结果且无失败码→OTHER_FAILURE；非空emails、无失败码、全部姓名非空→SUCCESS_CONTROL。保留原failureReason、downloadFailureCategory和outcome，不以主桶替换事实。身份未明确可与下载/内容原因并存，主桶配额仅选一次。

SUCCESS_CONTROL只意味着当次抽取结构完整，不保证邮箱有效、属于本人、学历职称符合或成功准入。每包unresolvedAuthorEmailCount按上述空白谓词计数，不去重冒充自然人数。

采集是“先到且拿到许可”的限量诊断样本，存在并发/主桶偏差；非全量、非随机，不能估总体召回率。去重键为executionId+source+规范化输入SHA。同键已保留则不覆盖；后续重试可能已成功，旧包仍仅是首次被保留的观察；被清理后可再次留存。不得在旧包回写“最终结果”。

## C-3 文件结构、原子性与全部读写者

```text
<directory>/
  .writer.lock
  <positive-execution-id>/
    <sample-id-64hex>/
      sample.json
      body-01.bin
      body-02.bin       # 可没有
  .tmp-<uuid>/          # 未提交，不向reader展示
```

sampleId = SHA256(UTF-8 executionId+换行+source+换行+inputSha256)。inputSha256是脱敏后PaperMetadata规范JSON（固定属性顺序、递归排序对象key、保留数组顺序、UTF-8无多余空格）的SHA；明确叫“留存输入指纹”，不声称是OpenAlex原始API响应SHA。

保存PaperMetadata的全部字段及PaperAuthor全字段。只有URI的userinfo/query/fragment移除，另保存原URL UTF-8的SHA；downloadUrl/candidateDownloadUrls原顺序与数量不变。不能使用未脱敏URL/error.message构造日志、文件名或回放请求。作者/正文可能含公开邮箱，目录0700、文件0600，不放静态资源目录，不向现有前端自动暴露。路径只使用数字/64hex/固定文件名；不跟随符号链接，不读取/清理配置root以外路径。

Store是唯一writer/删除者。用进程内tryLock加root文件tryLock，拿不到立即LOCK_BUSY；不允许两个进程同时提交和清理。reader不抢锁，遇到清理导致文件消失输出EXPIRED_OR_REMOVED。加锁后先清过期及崩溃.tmp，再按capturedAt最旧优先淘汰到字节数与maxTotalSamples均可容纳本包，计入临时写入预算；空间预检失败跳过，写入仍须处理磁盘满。以已有目录+待写包预算保证逻辑字节上限，不只是提交后删。

所有文件在同文件系统临时目录写完，JSON引用的文件SHA/长度校验完，再ATOMIC_MOVE目录到最终名；不支持原子目录移动则IO_ERROR，不降级暴露半包。失败清理自己.tmp；崩溃残留由下一次持锁清理回收。未知子目录/损坏JSON不能无界枚举到根外，也不能靠盲删外部文件达标；异常目录计入容量并报STORE_INVALID，不能继续无限写。

清理与reader：list只认最终目录且JSON合法；replay先载入并核对包，在读取竞态消失时报告EXPIRED_OR_REMOVED，不把它当解析失败。报告输出必须位于样本root之外，不能覆盖原输入。停用后的清理由运维按runbook手动删除选定样本root，禁止通用rm变量脚本自动执行。

## C-4 sample.json schemaVersion=1

以下所有字段受01 I-1/I-2及总I-1/I-2约束；不向既有共享store新增字段。nullable必须写null，数组必须存在；未知schemaVersion拒绝回放。

|字段|类型/固定含义|
|---|---|
|schemaVersion / stage|整数1 / 字符串EXTRACTION|
|sampleId / inputSha256|64位小写hex；按C-3生成|
|capturedAt|UTC ISO-8601；抽取完成/异常观察时间，非业务提交时间|
|context|C-2完整对象|
|paper|C-3脱敏后的完整PaperMetadata；原authors无改排|
|urlFingerprints|按downloadUrl及候选位置保存原URL的SHA；不保存原query|
|bucket / unresolvedAuthorEmailCount|C-2主桶 / 非负记录数|
|outcome|原EmailExtractionOutcome完整结构；业务异常时null；不强行填新identityRuleVersion|
|extractionExceptionClass|仅异常类名，正常null；不得保存异常message/stack|
|identityExtractionVersion|捕获时DiscoveryIdentity.EXTRACTION_VERSION；仅身份规则版号|
|attempts|C-5数组，最多16|
|attemptsTruncated|布尔；发生被省略尝试才true|
|bodyBytesRetained|完整已保留正文总长度，必须等于body记录之和|

无 build git SHA 时不编造线上代码版本。原始生产outcome只用于查阅；03用重新编译出的加载类hash标识A/B版本，不能把当前源码当历史部署代码。

## C-5 尝试/正文契约

只观测两个全文钩子；不是“完整HTTP请求链”。每次准备调用实际全文executor时建立attempt，记录本钩子持续时间；业务在dispatch前判断deadline已过可无attempt。底层重定向/额度请求/Unpaywall查询不在计数内；outcome.httpRequests保持旧口径，不要求与attempts.size相等。

每attempt字段：`sequence`正递增、`kind`=PDF_URL或PMC_XML、`url`脱敏URI、`urlSha256`原URI指纹、`elapsedMs`非负、`httpStatus`可空、`contentType`可空、`exceptionClass`可空、`bodyStatus`、`bodyFile`可空、`bodySha256`可空、`bodyBytes`可空、`pdfSettings`可空。Pdf settings保存PdfExtractionProperties所有实际值，仅来自该解析器实例；XML为null。正文类型不能靠后缀猜测，回放PDF/HTML用原contentType+原bytes交给原extractor判别。

bodyStatus枚举：SAVED=完整字节及文件/长度/SHA全部存在；NOT_AVAILABLE=未取得完整body（失败/超限/中断），文件/SHA/长度null；BODY_LIMIT=完整body已到达但样本限额不保留，文件/SHA=null，长度写实际完整长度。SAVED原样保存，无文本化、HTML清洗或PDF重排；超过采样限额不截断保存成SAVED。

XML getForObject未暴露成功HTTP状态/Content-Type，所以两者null，kind=PMC_XML足以选择XML解析器；HTTP异常有status可记录实际值。Pdf headers存在时只取status/contentType，不保存Cookie/Authorization等全量headers。错误响应体不另读；超限、超时中断没有完整正文，不额外drain或重下载。payload为空但完整返回可按业务原空响应逻辑记NOT_AVAILABLE。

## C-6 可观察性与回放报告

store每次成功提交INFO：`event=DISCOVERY_SAMPLE_CAPTURE decision=SAVED executionId=<id> sampleId=<id> bucket=<bucket>`。诊断跳过使用固定decision：DISABLED不打日志；INVALID_CONTEXT、CONFIG_ERROR、CAPTURE_BUSY、HOST_BUSY、DUPLICATE、QUOTA、METADATA_LIMIT、LOCK_BUSY、IO_ERROR、STORE_INVALID。高频跳过合并限频（每分钟至多一条汇总），记录“进程启动后尝试/各decision数”，明确是非持久诊断计数，不填任务业务统计。正文BODY_LIMIT、attemptsTruncated在包内可见。日志不带作者、邮箱、URL、原异常message。

03 list结果计数只来自当前合法包；按executionId/bucket聚合，显示savedSamples、savedBodies、metadataOnly、truncatedSamples、retainedBytes；不能从剩余包反推“总失败/历史跳过”。损坏包单独列invalidSamples。

03 replay结果单位为一个完整body，不是论文或专家。保存sampleId/attempt.sequence/inputSha256/bodySha256/实际解析配置/解析绑定数组/parseFailure/耗时；run级保存samplesRoot规范路径（供输出隔离校验）、代码HEAD及dirtyDiffHash（仅本地源码差异，不输出内容）、实际target/classes全部class文件聚合SHA、Java版本、reportVersion=1、scope=PARSER_ONLY。PDF/HTML用原knownAuthors和pdfSettings；XML仅JATS输出，声明无OpenAlex补ID/机构。bindings比较所有AuthorEmail字段（顺序规范化但保留重复数量），姓名、邮箱、机构任一改变都要列出，不能只计邮箱总数。

A/B比较只接受同一reportVersion、同一成功回放键集合、同input/body SHA与配置；任一不同返回exit=2及INPUT_MISMATCH，不产生百分比。有效比较输出added/removed/changed/unchanged与逐项差异；changed按同一body内规范邮箱对齐，冲突/重复单独呈现，不能随意配对。

可选真值文件：reportVersion=1，按body SHA+input SHA列人工确认的email/givenNames/familyNames对应关系；未标注body不进入准确率分母；字段不得由模型猜测。无真值accuracy=null，labelledBodies=0；有真值按完整三元组精确集合匹配，分别在beforeMetrics/afterMetrics报告TP/FP/FN及precision/recall，分母0为null；同时单列姓名冲突、身份丢失，不能把空姓名新增邮箱当正确召回。此准确率仅限标注集合。

NO_BODY、BODY_LIMIT、UNSUPPORTED_SCHEMA、HASH_MISMATCH、EXPIRED_OR_REMOVED均独立skip/error状态。缺正文403不会自动获得“修复成功”。工具拒绝大于契约的包/正文及根外引用，先校验再解析，单个损坏包不妨碍输出其他样本，但退出码非0并列明错误。
