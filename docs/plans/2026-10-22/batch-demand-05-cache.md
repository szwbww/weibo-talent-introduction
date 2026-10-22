# 05 全量参考统计缓存与完整模板指纹

状态：待批准。依赖：04。范围：8文件；只读统计缓存与模板只读指纹两个子系统。仅增加新端点；旧preview接口保持兼容，06再切UI。

## 需求描述

相同有效条件的全量参考统计计算后复用缓存，展示真实缓存完成时间；手动刷新才重算。缓存缺失、过期、失败均不影响按需发送。必须保持统计零投递/零业务写入、旧preview响应、模板启动令牌合同；不引入Redis、缓存DB表或后台自动全库刷新。

## 关键不变量

### Invariant I-1: 缓存是展示数据
- Rule: 缓存只能由Controller的statistics端点调用，不注入发信控制流。只保存PendingOutreachSummary及时间/状态，不保存可投递白名单。发送逻辑不能读取缓存总数或缓存profile。只读统计不调用SMTP、verify供应商、联系人创建/绑定、attempt或任务创建。
- Applies to: 新cache服务、Controller新端点、Manual只读统计入口。
- Violation consequence: 旧统计阻断发信或产生额外外发。
- 来源: original


### Invariant I-2: 键覆盖有效语义
- Rule: key=规范化effective RecipientScope和影响统计的snapshot字段+mailType+解析后模板ID+完整统计模板fingerprint+选人规则版本。集合去重排序，不包含UI requestSeq或roundSize/间隔等不影响统计的执行参数；不得漏掉门禁、验证策略、地区排除、研发类型、hasFields。指纹覆盖subject/block/引用内容/启用状态/全部可用变体和默认模板解析；不改变现有versionToken及启动比较。
- Applies to: Manual规范化上下文、MailComposeTemplateService统计fingerprint、缓存key构建。
- Violation consequence: 不同条件或片段改写错误复用旧人数。
- 来源: K-batch-send-launch-template-revalidation


### Invariant I-3: 有界与失败原子性
- Rule: 进程内LRU最多64个已完成key；in-flight最多2个不同key，同key单飞共用future。缓存时间只在完整目标统计成功提交时取Asia/Shanghai当前时间；24小时只标stale，不自动重算/删除。错误保留旧成功summary/cachedAt，不能存0或刷新时间；进程重启清空，明确MISS。首次取数失败返回ERROR而非READY0。
- Applies to: cache的load/refresh/lookup/eviction与异常收敛。
- Violation consequence: 无限内存、刷新风暴或错误数字伪装最新。
- 来源: original


### Invariant I-4: 明确接口状态
- Rule: 新envelope字段state只取MISS/READY/REFRESHING/ERROR，cacheKey、cachedAt、stale、summary可空，errorCode可空。不在MISS/ERROR制造summary；旧summary存在时ERROR/REFRESHING可同时带旧值。cacheOnly=true永不启动统计；refresh=true显式强制刷新；两者同true返回400。默认两者false命中复用、缺失计算。同key并发refresh合并；不同key超过2个返回503不排无界队列。
- Applies to: POST /api/mail/batch-send/recipients/statistics及DTO（定义在cache文件）。
- Violation consequence: UI把失败误读0或普通打开页面触发全扫。
- 来源: original


### Invariant I-5: 完整目标与有界诊断分离
- Rule: totalSendable为遍历完成的历史口径参考目标，不含即时可投递保证；baseline/excludedUnique/eligibleBeforeLiveVerification为原预检查诊断口径，达到20000限额必须partial=true并标注诊断范围。全量目标统计发生任何ES缺响应/超时/失败分片均不提交缓存；有意诊断限额不是系统失败。统计开始/结束模板指纹不同则不发布新结果。
- Applies to: 新严格统计入口、strict PIT读、诊断上限、结果提交。
- Violation consequence: 把截断统计当全量、把中途模板混合结果写成最新。
- 来源: original


 
## 现状审计

### 统计与缓存
- countBySnapshot:566分支首封/材料；首封:598 NEW全量、:604 countEsTargets、:628 preflight。旧scrollExpertsFiltered:749-810在body=null时return/break且未拒绝失败分片，因此不能直接把旧入口返回值当“成功完整缓存”。
- scanBaseCandidates:678预检查20000上限；旧代码在ES限额后还能并入NEW全量。新严格统计分支须让NEW也使用02 keyset并共享诊断上限；旧preview外部语义不改。此处严格分支不是执行母集白名单。
- 04已替换材料10000截断为按页只读累计，可供本步统计。
- 现有pom无Spring Cache/Caffeine/Redis及CacheManager配置；手写有界进程缓存是本计划设计选择，不宣称仓内没有其他缓存。（来源: K-no-cache-framework-in-repo）
- 新cache是私有进程store，唯一writer为成功refresh发布；lookup只读；无业务数据库缓存字段。
- X1：新端点→strict统计→成功提交→cacheOnly读取；X2：错误/重启→旧值或MISS；X3：缓存任意状态→04发送不依赖。

### 模板及引用内容
- MailComposeTemplateService:228-246 versionToken包含subject、subjectSnippetId、blockType/refId/customText，未包含ref所指内容；不能拿此令牌直接覆盖统计缓存失效。
- :318 renderTextPool读取subjectCandidates、possibleRenderTexts，后者覆盖replySnippet变体、QA规则等完整可渲染文本，是本步复用seam。统计指纹方法只读，不把私有pool开放成新写入接口。
- 模板/blocks/snippets/variants/qaRule写入口及DDL见证据账本“DB DDL”“受影响存储写入路径”及E-07。content_variant为id主键、owner_type/owner_id普通索引、enabled默认1，ContentVariantService:68/74/92替换/删除变体；qa_rule为id主键、category外键、enabled默认1，QaRuleManagementService:78/111/131/143创建/更新/删除/启停。原匹配/工作台/发信读取不改，下一次lookup通过内容fingerprint产生新key。
- X4：模板或引用片段编辑→fingerprint变→旧缓存不可套用；默认模板ID切换亦同。
- 配置snapshot写入方BatchSendTaskConfigService保持；新key必须取resolveScope之后的实际值，而非任意原始JSON字段顺序。

## 实现方案

1. 清单3新增statisticsFingerprint(templateId?/mailType)只读方法：以现有默认解析方式获得模板，读取root version及renderTextPool全部文本，以结构化规范JSON（不能简单无分隔拼接）SHA-256；区分列表层级/空文本/启用状态。不存在/禁用/类型不匹配按现有模板检查报错，不借用另一模板旧缓存（I-2）。
2. 清单2提供只读规范化统计context与严格统计入口；保留旧countBySnapshot签名/默认语义。严格模式中全量目标采用02 PIT完整遍历和NEW keyset，复用原countEsTargets筛选/人物去重/模板/历史不可达逻辑；不得复用会吞缺响应的旧scroll。诊断也采用strict PIT、NEW keyset、strict mget，保留20000及partial；明确计数和诊断可有独立遍历，不声称一次ES请求完成全量。所有cursor finally close。材料使用04只读source（I-1/5）。
3. 清单1实现有界LRU和single-flight；不依赖cache framework、不引入Bean循环：Controller→cache→Manual只读方法/模板指纹；Manual不依赖cache。lookup短锁只取状态，统计在锁外执行，最多2个计算槽；所有完成/异常都清in-flight并释放槽。同key首个refresh同步等待计算，cacheOnly可立即返回REFRESHING+旧summary；无后台定时重算（I-1/3/4）。
4. 清单1统计前后重新计算fingerprint，变化用errorCode=TEMPLATE_CHANGED_DURING_STATISTICS保留旧值，不自动无限重试。stats错误受控errorCode不暴露ES凭据/完整query。保存cachedAt为完成时刻，summary.asOf仍为既有采样时点，二者不得混称。规则version常量v1，未来选人语义变更须升版（I-2/3/5）。
5. 清单4新增POST端点，沿同Controller现有权限/异常规则，不扩写权限。cacheOnly语义200+MISS/READY/REFRESHING；统计执行失败503+ERROR和可用旧值，非法请求400；成功200+READY。不改变旧preview和diagnoseRecipients的路径/响应（I-4）。
6. 清单5-8单测/集测覆盖并发、LRU、时间、模板引用变体、default切换、strict partial failure、只读spy和旧controller构造桩（I-1至I-5）。

## 变更文件清单

| # | 文件（相对仓库根目录） |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientStatisticsCache.kt` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt` |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientStatisticsCacheTest.kt` |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt` |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigControllerTest.kt` |

## 验收标准

- I-1：SMTP/verify/DB业务writer调用次数全0；04运行不读缓存；旧preview合同测试继续通过。
- I-2：同集合不同顺序key相同；地区/类型/门禁/验证/模板/hasFields任一有效改变key不同；只改roundSize不重新统计；只改snippet正文、可用variant、QA正文、enabled、默认模板指向均变化。原versionToken测试期望不改。
- I-3：同key并发20次只计算1次；3个不同key同时计算时第3个503；已完成65个key仅保留64；refresh失败旧cachedAt逐字相同；fakeClock+24h仅stale变化、计算调用0。
- I-4：cacheOnly MISS计算0次；cacheOnly+refresh 400；命中默认请求计算0次；显式refresh计算1次；refresh期间lookup不等待全扫；错误有errorCode，禁止把异常转summary.totalSendable=0。
- I-5：null body/失败分片/超时/模板中途变更不写缓存；目标>20000全量totalSendable完成，而诊断partial=true、baseline不超过上限；同一人在重试与ES中只计一次。0匹配成功时可合法READY/0，须与失败区分。
- Java11：mvn test -Dtest=BatchRecipientStatisticsCacheTest,ManualInitialOutreachServiceTest,MailComposeTemplateServiceTest,BatchSendConfigControllerTest。
- 新接口的资源成本：只有显式统计的首次MISS或refresh全扫；UI打开cacheOnly仍需读轻量模板内容生成指纹，不承诺零DB查询。多进程各自缓存，不承诺跨实例一致。

## 人工验收清单

### A-1: 首次统计、复用、刷新
- 前置条件: 测试环境固定100名符合候选，缓存空，SMTP捕获箱；API body使用同一保存配置snapshot。
- 操作步骤: 1. POST statistics?cacheOnly=true；2. POST statistics；3. 再POST同样请求；4. POST statistics?refresh=true。
- 预期结果: 第1步MISS/无summary/0全扫；第2步READY且totalSendable100；第3步cachedAt逐字不变且0全扫；第4步重新计算并更新cachedAt。全程0邮件、0联系人/attempt新增。
- 覆盖: I-1/3/4、X1、参考缓存需求。

### A-2: 条件及引用片段变化
- 前置条件: A-1已缓存；模板引用测试片段，可通过模板后台编辑正文。
- 操作步骤: 1. 只改每轮数量30→50后cacheOnly；2. 修改地区后cacheOnly；3. 恢复地区，修改引用片段增加必填占位符后cacheOnly。
- 预期结果: 第1步复用原缓存；第2/3步MISS，不展示前key的人数；原启动模板令牌API仍按旧合同工作。
- 覆盖: I-2、X4、模板合同不变。

### A-3: 失败、陈旧、重启不阻断发送
- 前置条件: 已缓存100；测试代理可令ES读失败，fakeClock可前移24h；发送使用隔离捕获箱。
- 操作步骤: 1. 前移时间并cacheOnly；2. ES故障时refresh；3. 恢复ES，执行只发30；4. 重启服务再cacheOnly。
- 预期结果: 第1步stale=true、原时间保留；第2步503/ERROR、原100和时间保留；第3步成功30，不要求先刷新统计；第4步MISS而非READY0。
- 覆盖: I-1/3/4、X2/X3。

### A-4: 大名单与并发
- 前置条件: 测试夹具30001名符合候选，计算可暂停；新键至少3个。
- 操作步骤: 1. 20个请求并发刷新同key；2. 并发另两个key；3. 完成统计并查看结果。
- 预期结果: 同key只1次全量计算；最多2个不同key同时运行，额外key503；totalSendable30001，诊断partial=true且标明上限，不误称全部排除分析已完成。
- 覆盖: I-3/5、X1、大名单真实口径。

