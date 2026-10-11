# 04｜会议视频后端：1 GiB、命名、播放

状态：待评审；依赖01的身份解析器；执行顺序在03后。未执行上传或生产配置修改。

## 需求描述

每个专家可上传多个会议视频，单文件上限 **1 GiB = 1024 MiB = 1,073,741,824 字节**；支持上传前指定文件名、上传后重命名、鉴权播放/下载。无contact专家同样可用。

保持：旧手动材料100 MiB、旧回复通用附件10 MiB、multipart100MB/101MB；视频不计入专家简历/材料完成度，不触发会议状态或邮件；现有登录访问规则不变。范围外：分片/断点续传、转码、封面、对象存储、CDN、视频删除、企业附件、定时清理平台。断网后由用户检查列表并从头重传，不承诺跨关闭续传。

## 关键不变量

### Invariant I-1: 身份与鉴权
- Rule: 复用01 canonical expertId；上传先 requireKnown。列表/重命名/播放/下载先验证登录，再以 expert_key + video_id 同时查元数据，归属不符404。已有视频可在ES临时故障时播放，不逐个Range查询ES。全体现有登录用户沿用当前系统权限，不发明用户私有隔离。
- Applies to: 所有视频接口
- Violation consequence: 串专家、匿名访问或播放依赖ES
- 来源: K-expert-document-ownership-chain

### Invariant I-2: 单文件限额与流式处理
- Rule: MAX_BYTES=1073741824L；expectedSize必填且1..MAX；已知Content-Length必须相等，未知长度仍允许但按实际流计数，实际最终字节数必须等于expectedSize。读取缓冲固定64 KiB；禁止MultipartFile、ByteArray全文件、readBytes、base64。前置超限与实际流超限413，零文件400。
- Applies to: raw上传、HTTP集成测试
- Violation consequence: 内存耗尽或错误放宽材料上限
- 来源: 本次需求与代码审计

### Invariant I-3: 文件标识与原始名称
- Rule: id为服务端UUID，不可变；磁盘只用id。original_filename保存去路径后的原始basename，≤255 code points，不截断；extension由原始basename最后后缀小写派生，仅mp4/mov/webm。原始名、扩展名、byte_length、uploaded_by/at上传成功后不可修改。
- Applies to: 上传与所有读路
- Violation consequence: 名称被当路径或历史来源丢失
- 来源: 本次需求与代码审计

### Invariant I-4: 自定义文件名
- Rule: display_filename=NFC(trim(用户名称主体))+固定小写扩展名；完整名称1..100 code points，主体必填；主体不接受控制字符、/\:*?"<>|及尾部句点；默认主体取原始basename去末扩展名，超长时要求修改而非截断。重命名仅改display_filename/version/updated_by/updated_at，不搬磁盘文件、不改变URL；下载按新完整名。
- Applies to: 上传/重命名/Content-Disposition
- Violation consequence: 改名破坏链接或非法下载名
- 来源: 本次需求与代码审计

### Invariant I-5: 同名与并发
- Rule: 同一expert_key内NFC规范化后的完整display_filename精确且区分大小写唯一，跨专家允许同名。唯一索引最终裁决409 VIDEO_NAME_CONFLICT；重命名用expectedVersion CAS，旧版本409 VIDEO_VERSION_CONFLICT；成功version+1，上传version=1。
- Applies to: INSERT/重命名SQL
- Violation consequence: 同名重复或覆盖别人文件
- 来源: 本次需求与代码审计

### Invariant I-6: 文件与事务一致性
- Rule: 仅本service写新目录、新表；先在root/.tmp-UUID有界流写入并验证长度，close后同目录ATOMIC_MOVE到UUID，随后独立短事务INSERT。数据库失败/唯一冲突删final；IO失败/中断删tmp；finally释放并发槽。没有持有数据库事务传1GiB。正常失败不可留下可见记录。进程硬崩溃不保证自动清理孤儿，部署手册明确列举人工核对方法，禁止盲删。
- Applies to: 上传生命周期
- Violation consequence: 半文件可播放、事务长占用或磁盘泄漏
- 来源: K-expert-document-ownership-chain

### Invariant I-7: 路径与响应
- Rule: root=MailAttachmentStorageProperties.basePath/meeting-videos；只接受元数据UUID，不接受客户端路径；下载realPath必须在realRoot内且实际大小等于byte_length，符号链接越界拒绝。返回FileSystemResource+明确扩展名对应video/mp4、video/quicktime、video/webm；inline/attachment用ContentDisposition UTF-8；Cache-Control:no-store、X-Content-Type-Options:nosniff。失败不返回绝对路径。
- Applies to: 播放/下载
- Violation consequence: 越权读文件或信息泄漏
- 来源: K-document-file-read-via-storage-path

### Invariant I-8: 播放与Range
- Rule: 不实现自制Range解析器；使用已验证Spring5.3.31 Resource/ResourceRegion支持。Range必须在鉴权和归属检查后生效；合法单段206/Content-Range，越界416。浏览器编码不支持不代表存储失败，仍可下载原字节；不声称后端验证了编解码兼容。
- Applies to: preview/download
- Violation consequence: 视频不能拖动、鉴权被Range绕过
- 来源: 本次需求与代码审计

### Invariant I-9: 资源占用与未知结果
- Rule: 同一应用实例最多2个视频上传并发，tryAcquire失败429，槽只在处理期持有；客户端串行队列。写文件前usableSpace至少expectedSize+64MiB，否则507；写中ENOSPC也507并清理。连接关闭或浏览器取消不能被承诺为服务端撤销：如果已提交，列表仍显示成功项；不自动重发不确定请求。
- Applies to: 上传、清理、前端错误协议
- Violation consequence: 大文件耗尽线程/空间或重复上传
- 来源: 本次需求与代码审计

### Invariant I-10: 独立存储与列表
- Rule: 视频不写manual_expert_material_upload/mail_attachment/expert_document/expert_material_status。列表GET只读，page从0、size默认20且1..50，按uploaded_at DESC,id DESC；返回items/total/page/size；无记录空数组。无自动任务、无DELETE。
- Applies to: 全部SQL/列表
- Violation consequence: 视频污染简历流程
- 来源: K-material-status-cv-reader-and-route-collision；K-calendar-not-expert-material-owner

## 现状审计

- [审计](contact-profile-audit.md) E-4/E-7/E-8含现有材料上传所有者链、已有下载Resource模式、100MB parser限制与线上连接器事实。复用存储basePath，不改已有owner链，所有旧表写路径保持。
- 本计划新表/目录现存命中为0；全部未来写路径：service.upload临时文件/原子改名/INSERT及失败删除，service.rename CAS UPDATE；全部读路径：service.list、rename归属读取、browse元数据与文件；接口控制器与05页面消费。没有其它同步/回填/删除写者。
- 1GiB不能复用现有multipart入口：请求进入controller前会被100MB parser拒绝。为此新增原始二进制请求体接口，保持其它入口限额，不改全局multipart。Tomcat maxPostSize文档限定FORM参数解析，不是raw流的总大小上限。
- 线上Java直接监听80/443，未发现nginx；443 Connector maxThreads150，未显式配置upload timeout；服务自带Tomcat文档表明connectionTimeout也用于请求体读取，默认60秒是读取等待，不能误判为总上传60秒。不凭空添加nginx配置、不把maxSwallowSize设无限。
- Spring Boot2.7.18依赖Spring5.3.31；本地源码 AbstractMessageConverterMethodProcessor.java:186–201明确Resource→206/416转换，证据`spring-range-source.txt`。
- 交互 X1：新HTTP raw上传→文件→DB→列表；X2：rename→下载名称/播放URL；X3：Range→鉴权/归属；X4：>100MiB视频→旧multipart限额隔离；X5：取消/IO/事务失败→清理；X6：01未联系专家→视频存在性。

## 实现方案

### 1. 新表与DTO（I-1/I-3–I-5/I-10）

修改 `V154__create_expert_meeting_video.sql`、`ExpertMeetingVideo.kt`。表 `expert_meeting_video`，InnoDB/utf8mb4：

| 列 | 类型与约束 | 所属不变量 |
|---|---|---|
| id | CHAR(36) ASCII PRIMARY KEY，服务端UUID | I-3 |
| expert_key | VARCHAR(64) utf8mb4_bin NOT NULL；不建contact FK | I-1 |
| original_filename | VARCHAR(255) NOT NULL，不可变 | I-3 |
| display_filename | VARCHAR(100) utf8mb4_bin NOT NULL | I-4/I-5 |
| byte_length | BIGINT NOT NULL，1..1073741824服务校验 | I-2/I-3 |
| uploaded_by / uploaded_at | VARCHAR(64) / DATETIME(3) NOT NULL，session /服务端Asia/Shanghai毫秒 | I-3 |
| updated_by / updated_at | 同上；上传初始化同uploaded，rename更新 | I-4 |
| version | BIGINT NOT NULL，初始1 | I-5 |

唯一索引 `(expert_key,display_filename)`；列表索引 `(expert_key,uploaded_at,id)`。extension/contentType从不可变original_filename派生，不加冗余storagePath/状态表/上传session/哈希列。这是独立单写者新表；对既有共享表新增列0。

### 2. 服务与接口（I-1–I-10）

`ExpertMeetingVideoService.kt` 包含固定参数SQL、文件流程、名称校验和局部异常；无通用存储框架抽象。`ExpertMeetingVideoController.kt` 接口如下（expertId URL编码；level为01枚举）：

| HTTP | 路径 / 参数 | 结果 |
|---|---|---|
| POST | `/api/experts/meeting-videos?expertId=...&level=RAW&originalName=...&nameStem=...&expectedSize=...`，Content-Type:application/octet-stream，body=File原始字节 | 201单个VideoView |
| GET | `/api/experts/meeting-videos?expertId=...&page=0&size=20` | 200分页；只校验key格式/登录，无行空列表 |
| PATCH | `/api/experts/meeting-videos/{id}?expertId=...`，JSON `{nameStem,expectedVersion}` | 200最新VideoView |
| GET | `/api/experts/meeting-videos/{id}/preview?expertId=...` | inline Resource，200/206/416 |
| GET | `/api/experts/meeting-videos/{id}/download?expertId=...` | attachment Resource，下载名是displayFilename |

VideoView仅 `{id,expertId,originalFilename,displayFilename,extension,byteLength,uploadedBy,uploadedAt,updatedBy,updatedAt,version,previewUrl,downloadUrl}`，不返回磁盘路径；URL由当前contextPath构造、同源。请求仅接受octet-stream，不调用getParts/MultipartFile，不使用@RequestBody ByteArray。接收HttpServletRequest.inputStream，在请求线程流式写；进度由05 XHR上传事件读取。expectedSize/filename长度/身份/容量/同名预检在读流前完成；数据库唯一键仍为最后裁决。单实例2并发及64KiB缓冲不新建线程池。

extension白名单只保证格式声明范围，不用扩展名冒充内容解码验证；类型异常在播放器提示下载。用户文件名参数通过URLSearchParams编码，由框架解码一次，不二次URLDecoder。禁止从文件名拼路径。重命名保留原始扩展名，NFC主体校验由服务端兜底。

身份解析器的ProfileException必须在video service显式映射为同状态VideoException（400/404/503），不能落入全局500。上传/rename用户名须非空且≤64 code points后才写审计列。

Controller局部 `@ExceptionHandler(VideoException::class)` 返回固定 ApiErrorResponse：400 INVALID_VIDEO_NAME/VIDEO_SIZE_MISMATCH/EMPTY_VIDEO、401 UNAUTHORIZED、404 VIDEO_NOT_FOUND、409 VIDEO_NAME_CONFLICT/VIDEO_VERSION_CONFLICT、413 VIDEO_TOO_LARGE、415 UNSUPPORTED_VIDEO_TYPE、429 VIDEO_UPLOAD_BUSY、503 EXPERT_LOOKUP_UNAVAILABLE、507 VIDEO_STORAGE_FULL、500 VIDEO_STORE_FAILED；业务错误不透传异常message/路径。上传Content-Length或实际字节超限都拒绝；容器提前断连接可能无JSON，05必须支持“结果未确认”路径。

`FileSystemResource`读文件模式照现有ExpertDocumentBrowseController；Range响应不要手动设置错误的整文件Content-Length或自行截流，交给转换器；通过真实MVC测试确认结果。

### 3. 验证与发布事实（I-1–I-10）

修改三份测试与Flyway IT：模型/流单测使用生成式InputStream，禁止构造1GiB内存数组；小上限注入只能用于单元测试，生产常量不可从请求覆盖。真实HTTP用流生成至临时文件/请求体，验证100MiB+1与1GiB边界。发布手册记录具体检查命令、上传时长/速度、可用磁盘前后、JVM RSS前后、DB行数、失败文件清理、Range头、旧上传限额；实际线上需受控验收数据，不能上传真实敏感会议作测试。

部署前重读线上connector和schema history；当前证据不要求更改server.xml。先测试真实1GiB慢速上传与短暂断流；如果发现代理或容器必须调整配置，暂停该子计划发布，基于测得配置修订本计划，不能偷偷把限制全局放开。这是一项环境验收门槛，不是本次计划未确定架构。V154只在V153通过后使用；迁移最新断言更新至154，历史target不变。

## 变更文件清单

| 文件 | 操作 / 职责 |
|---|---|
| `src/main/resources/db/migration/V154__create_expert_meeting_video.sql` | 新建：视频元数据与名称唯一约束 |
| `src/main/kotlin/com/weibo/talentintroduction/document/domain/ExpertMeetingVideo.kt` | 新建：DTO、固定异常/状态码 |
| `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMeetingVideoService.kt` | 新建：流式文件/元数据/命名单一写者 |
| `src/main/kotlin/com/weibo/talentintroduction/document/controller/ExpertMeetingVideoController.kt` | 新建：raw上传、列表、重命名、预览/下载 |
| `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMeetingVideoServiceTest.kt` | 新建：命名、流边界、失败清理 |
| `src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMeetingVideoControllerTest.kt` | 新建：鉴权、归属、Range响应 |
| `src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMeetingVideoStreamingIntegrationTest.kt` | 新建：真实HTTP/容器100MB以上流测试 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 修改：V154迁移、真实唯一键竞争 |
| `docs/deploy/2026-10-11-expert-meeting-video.md` | 新建：1GiB链路验证与回滚手册，非新增基础设施 |

共 **9 个文件**；未列文件禁止顺手修改。测试证据为执行产物，不扩张产品修改白名单。

## 验收标准

- I-1：匿名请求全部401；甲视频以乙key访问、改名404；无contact上传成功；无ES依赖的已存视频Range播放。
- I-2：1字节、100MiB+1、1GiB接受；1GiB+1/伪造长度/流中超限/零文件拒绝且无DB行；真实HTTP测试不得用MockMultipartFile假装测试1GiB。常量为Long。
- I-3/I-4：中文、空格、组合Unicode、非法字符、全长100与101、扩展名伪改、original不变；rename后磁盘同id且URL相同、下载Content-Disposition解码为新名。
- I-5：MySQL实际唯一键竞争，两请求只有一条记录；旧版本409，跨专家同名允许，同专家大小写区别明确。
- I-6：断流、move失败、INSERT失败/commit失败、唯一冲突、权限失败每个已处理分支清理临时与final；事务不包住上传循环。硬崩溃孤儿只能按手册核对，不写虚假“零孤儿”保证。
- I-7/I-8：路径越界、缺文件、尺寸不符、Range bytes=0-1023→206且1024字节、末尾Range、非法范围416、无Range200；未登录带Range401。中文filename响应头安全、无CRLF注入。
- I-9：3个并发请求第三个429，前两个完成/失败释放槽；低磁盘507；资源占用不随文件大小线性增长，应用读写缓冲64KiB。观察RSS用于回归判断，不捏造固定环境指标。
- I-10：手动材料页/简历状态/邮件事件/会议状态无新增视频记录；list分页稳定，无读写副作用。
- Java11运行 `mvn -Dtest=ExpertMeetingVideoServiceTest,ExpertMeetingVideoControllerTest,ExpertMeetingVideoStreamingIntegrationTest test`；再 `mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test`。真实大文件测试应专用tag/系统参数例如videoLargeIt，默认普通测试可跑小体积协议测试，发布验收必须显式运行完整1GiB档。没有该档证据不能说已支持1GiB。

## 人工验收清单

### A-1: 真实1GiB上传
- 前置条件: 01/04测试环境；磁盘预留至少3GiB；准备有效MP4和精确1GiB验收文件（可对小MP4追加合法ISO-BMFF free box补齐至1073741824字节，先验证仍能播放，禁止用稀疏零文件假装视频），记录本地SHA256。
- 操作步骤: 1. 用同会话API raw上传1GiB，指定nameStem“企业对接会”。2. GET列表。3. 下载并算SHA256。4. 上传1GiB+1字节文件。
- 预期结果: 1GiB返回201，列表大小1073741824，下载哈希相同；超限413或连接拒绝且列表无超限记录。后者容器无JSON时页面必须说明上限/结果需检查，不能显示成功。
- 覆盖: I-1/I-2/I-6；X1/X4/X6

### A-2: 名字不影响视频
- 前置条件: 已上传 企业对接会.mp4。
- 操作步骤: 1. 记录previewUrl。2. PATCH名字为“企业对接会-第二次”。3. 用原URL播放/下载。4. 尝试同专家重复名、空名、含/名、旧version。
- 预期结果: URL不变；下载名企业对接会-第二次.mp4；originalFilename不变；同名409、空名/非法名400、旧version409；已有视频未被覆盖。
- 覆盖: I-3/I-4/I-5；X2

### A-3: 鉴权与拖动
- 前置条件: 两位测试专家甲乙，甲已有视频。
- 操作步骤: 1. 对甲preview发Range bytes=0-1023。2. 把expertId改乙。3. 注销再请求原URL。4. 登录在浏览器拖动到视频中部。
- 预期结果: 依次206且Content-Range和1024字节、404、401；兼容编码视频能拖动，不重新下载整个文件才播放。
- 覆盖: I-1/I-7/I-8；X3

### A-4: 取消与失败恢复
- 前置条件: 测试上传客户端可断网；运维可只在测试环境模拟目录满/DB提交失败。
- 操作步骤: 1. 大文件上传中断网。2. 恢复后查看列表。3. 模拟磁盘不足和数据库失败。4. 检查同一次上传的tmp/final。
- 预期结果: 中断未提交时无记录、文件被清理；若已提交可见1条且不误报撤销；磁盘不足507、DB失败500固定文案，失败无可见视频；无绝对路径。
- 覆盖: I-6/I-9；X5

### A-5: 旧材料和邮件保持
- 前置条件: 既有测试联系人，记录材料/简历状态/会议状态/邮件条数。
- 操作步骤: 1. 上传一个101MiB旧手动材料。2. 上传一个11MiB回复通用附件。3. 新视频上传同101MiB。4. 比较原状态和条数。
- 预期结果: 旧材料/回复附件仍拒绝，视频可成功；视频只出现在视频列表，简历/会议/邮件数量及状态不变。
- 覆盖: I-2/I-10；X4；全部保持项

### A-6: 并发及分页
- 前置条件: 同会话起3个慢上传；已准备21个小测试视频。
- 操作步骤: 1. 同时提交3次。2. 完成前两个再重试第三个。3. GET第一页size20及第二页。
- 预期结果: 第三个初次429，空槽后允许；第一页20项、第二页1项、total21，无重复。
- 覆盖: I-9/I-10

