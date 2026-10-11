# 联系详情开发：代码与运行环境审计

审计日期：2026-10-11。下文Kotlin简写路径均相对 `src/main/kotlin/com/weibo/talentintroduction/`；JS/CSS简写均相对 `src/main/resources/static/`；迁移文件相对 `src/main/resources/db/migration/`。此文是事实附件，不是执行白名单。事实、设计决定和未验证事项分开记录。附件中的命中/源码摘录来自本地工作区；线上仅进行只读基础设施核验，未把本地行号冒充线上源码行号。

## E-1 专家身份、列表和既有字段

- `expert/service/ExpertIdNormalizer.kt`：trim；EMAIL-前缀大写、后续小写；其它标识大写。`expert/service/ExpertSearchService.kt:465`：esDocId取hit._id，orcidId取source.orcidId/orcid/id，缺失为空；两者不是同一概念。
- `ExpertSearchService.kt:829` findByOrcidId查精确term orcidId；:857 findByDocumentIds才按真实_id mget。计划只保存用户页面已有的逻辑专家ID，不对ES文档写操作，也不把_id补作业务ID。
- `expert/controller/ExpertIndexController.kt:53`列表从ES查询，再按orcidIds查联系人，规范化分组选择updatedAt最新者；:374响应有displayName/nationality/country/keyword/employment/contactId、阶段与学术指标，没有本次19个独立人工字段。`/api/experts/profile`现只返回tags，不能当人工档案API复用。
- ES三mapping `src/main/resources/es/orcid_info_{raw,candidate,application}.json:7` 都dynamic=false；不能让新字段依赖动态mapping。原写者包括ExpertIndexWriterService的状态同步、分类bulk、晋升/降级、发现候选写入、原始索引写入；本次全部不改变。完整身份相关函数与原始source取值证据见 [expert-identity](contact-profile-evidence/expert-identity.txt)。
- 新档案和视频表当前检索无命中；证据 [new-store-absence](contact-profile-evidence/new-store-absence.txt)。19项独立字段是**本计划新增设计**，不是声称代码已经存在。

## E-2 expert_contact：schema和全路径范围

- `V1__create_business_tables.sql:79` 建表，orcid_id VARCHAR(64)，唯一(campaign_id,orcid_id)，不是orcid全局唯一。后续迁移和列级写存在，不能只审计构造函数。（来源 K-expert-contact-two-write-sites）
- `campaign/repository/ExpertContactRepository.kt` 是CrudRepository；显式列级更新五类：updateCountryById、updateBindingById、rebindSenderAccountById、migrateBindingByAccount、clearSenderChangeMarkById。其余find/exists方法也在同文件，不增加新读字段。
- 整行save命中：ExpertIndexController、InitialOutreachService、ManualInitialOutreachService、ExpertOperatorStatusService、ConversationStateService、ExpertContactManagementService、ExpertIndexLevelOperationService、AutomaticApplicationPromotionService、PendingMailOperationService、AutoMailReplyService、UnmatchedInboundMailService。
- 全部读写匹配（含find/exists/save、直接SQL、迁移及tools）保存于 [contact-paths](contact-profile-evidence/contact-paths.txt)，逐项带file:line和原代码；该完整清单而非上述简称列表是审计回执。名单/邮件/绑定/自动流程都消费该表，本次只新增一个存在性读取，不添加该表列/新写者。
- 交互：新档案若塞回ExpertContact实体，多条自动整行save会产生覆盖风险；新表单一所有者避免触碰这些写路径。不同campaign联系人备注不得合并。（来源 K-bound-expert-cross-campaign-read）

## E-3 expert_contact_note

- schema `V150__create_expert_contact_note.sql`：expert_contact_id PK+FK ON DELETE CASCADE；note VARCHAR(2000) NOT NULL；updated_by VARCHAR(64)；updated_at DATETIME(3)。
- 全部应用写路径：ExpertContactNoteController PUT→ExpertContactNoteService.save；空trim值DELETE，非空UPSERT。数据库另外有父contact删除级联路径。没有专家级独立note。
- 全部读路径：同controller GET→service.get；不存在contact先404，存在但没note返回空字符串；mailbox-chat.js loadContactNote消费note/updatedBy/updatedAt。测试与DDL/全部命中见 [notes-paths](contact-profile-evidence/notes-paths.txt)。
- 新路径只读同GET；不向旧service添加第二写入口、不更改表结构。与收发件箱编辑跨模块共享同一行。

## E-4 材料/文件链：保留而非扩展owner

- `document/service/ManualExpertMaterialUploadService.kt`：existsById(contactId)前置；100*1024*1024实际流计数；root/manual临时文件→原子移动UUID→事务按manual_expert_material_upload→mail_attachment→expert_document写；失败删除文件。它不创建邮件事件、不推进材料阶段。
- `V130__add_manual_expert_material_upload.sql` 增加第三owner；材料归属必须唯一，MySQL历史版本CHECK执行差异使服务也必须校验。新视频不接入任何owner表。（来源 K-expert-document-ownership-chain）
- `ExpertMaterialService`的列表/单项/获取/读取按expert_document→attachment→owner校验；下载路径realPath限制在basePath；`ExpertDocumentBrowseService.isPreviewable`只支持PDF/image/text。`ExpertDocumentBrowseController` 已使用FileSystemResource+UTF8 ContentDisposition，支持复用返回模式而非绕过归属。
- 原材料store所有SQL/服务/迁移命中见 [material-paths](contact-profile-evidence/material-paths.txt)。本计划对这些表新增字段/写路径/读取字段为0，不改变原材料完成度。
- `campaign/service/ExpertMaterialService` 与 `document/service/ExpertMaterialService` 同名但不是同一契约。app.js:11664当前读 `/material-requests` 状态目录；`/materials` 返回ExpertMaterialPage。已纠正知识条目的旧路由描述。（来源 K-material-status-cv-reader-and-route-collision）
- `mail/service/OutboundAttachmentModels.kt:237` normalizeOutboundFileName会剥离路径、控制字符并静默截断255。自定义视频文件名需要明确拒绝非法/超长，不能直接把此清理函数当验证器。
- 日历/会议邮件附件不属于expert材料owner；会议视频也不自动进入会议邀请或邮件附件。（来源 K-calendar-not-expert-material-owner）

## E-5 两个前端宿主与全部交互入口

- `app.js:11213 showExpertDetail`→:11243模板：无contact分支，默认academic；`app.js:11656 loadContactDetail`→:11794模板：已有contact分支，默认contact。
- `beginContactDetailPresentation:4977` / `isCurrentContactDetailPresentation:4993` 持有generation并检查state.view；返回列表递增generation。新请求必须同时绑定专家key，防止旧响应落新页。
- `activateDetailSubTab:11145` 与 `#contactDetail`委托click/change承接原标签；`unmountExpertMaterialsHosts:12086` / `mountExpertMaterialsInline:12093`是原材料生命周期。新模块不能替换它们。
- 原API封装 `app.js:4686` 使用fetch、contextPath、handleAuthResponse。超时为可选，不是所有请求统一短timeout；它没有上传进度事件。旧materials用FormData并覆盖headers:{}，视频不能照抄走multipart。
- 两处改动前DOM逐字摘录在 [app-baseline](contact-profile-evidence/app-baseline.txt)；所有宿主/缓存/备注命中在 [frontend-hooks](contact-profile-evidence/frontend-hooks.txt)。来源 K-expert-detail-two-panel-render-sites；旧知识行号已重新校正。
- index.html当前11项版本化资源，版本20261008-mailbox-server-drafts。meetingConfirmationAssets.test.js、trustReplyWorkbenchSharedMount.test.js固定11及资产顺序，新增脚本会影响它们；本计划选择只修改app.js，维持11项。来源 K-frontend-cache-key-triad。

## E-6 前端样式盘点及逐字基线

所有以下规则完整逐字保存于 [styles-baseline](contact-profile-evidence/styles-baseline.txt)，并带原行号；不是相似样式草图。新增CSS见02/05正文唯一契约。

| 组件 | 代码基准 | 实值 |
|---|---|---|
| 设计token | styles.css:1 | primary#1e40af、hover#1e3a8a、active#172554；正文#1e293b、次色#475569、muted#94a3b8；panel-border rgba(15,23,42,.08)，border rgba(15,23,42,.11)；radius7/10/18 |
| 全局字体 | styles.css:77 | Inter / 系统无衬线，具体fallback见回执 |
| 按钮 | :802–873 | 32px高，12px字号，左右12px；hover上移1px/阴影；active缩放.97/opacity.85；primary渐变#3b82f6→#1e40af |
| 原元数据 | :1652–1714 | grid最小160px/gap8px；card padding10px12px/radius7；header11px600；value13px500/1.4；长项span-all |
| 表单 | :1105–1170 | grid两列/gap12px；input34px高、13px；focus蓝边+3px浅蓝shadow；disabled surface背景/muted文字 |
| 弹窗 | :11604–11819 | max640px、实白#fff、radius14px、shadow0 24px 64px rgba(15,23,42,.2)、backdrop rgba(15,23,42,.35)；头/体16px18px；760px窄屏规则；focus2px#3b82f6；disabled opacity.45 |
| 原头像/标题 | :1605–1650 | 40px蓝头像，姓名16px700/1.3；不改变 |

`--panel-bg`为半透明rgba(255,255,255,.55)，不能直接用作新dialog底色；复用em-upload白底。没有修改任何既有共享class，故不需要传播共享样式改动到其它使用位置。来源 K-panel-bg-token-is-translucent。

## E-7 数据库迁移与验证基础

- 本地最高V152，线上部署目录也含V150/V151/V152；全量文件排序回执见 [runtime-migration-tail](contact-profile-evidence/runtime-migration-tail.txt)，不是只看V15前缀推断最高版本。具体线上schema history尚未查询，执行前必须核实，不能把文件存在当迁移成功。
- FlywayMigrationIntegrationTest使用mysql:8.0.36容器、migrationIt开关；目标版本断言命中完整保存于 [migration-pins](contact-profile-evidence/migration-pins.txt)。每个子计划只更新“latest”断言；历史target不得全替换。来源 K-flyway-version-follows-deploy-order、K-flyway-latest-version-test-pin。
- pom.xml:6–10 SpringBoot2.7.18，Java11；父BOM spring-framework.version=5.3.31。本地原源码Range证据 [spring-range-source](contact-profile-evidence/spring-range-source.txt)。
- 新增私有表各一个单一写服务；不会更改共享实体或ES mapping。十九字段类型、上限、空值和枚举是01的设计决策，不是从表格猜出的现存数据库约束。

## E-8 1 GiB运行环境证据与边界

- `application.yml:15–25` 明确共享multipart100MB/101MB；ManualExpertMaterialUploadService业务100MiB、旧回复附件10MiB仍各自验证。新raw上传绕开multipart解析但必须自己有界读取，不能仅把全局阈值改1G。
- 线上只读回执 [runtime-readonly](contact-profile-evidence/runtime-readonly.txt)：Java监听80/443；command -v nginx无输出；443 Connector为Http11NioProtocol、maxThreads150；80 connectionTimeout20000，443未显式设置读取/上传timeout。不推断公网无其它上游，只确认本机监听情况。
- 同机随版本Tomcat9.0.71自带文档：maxPostSize管FORM参数解析；connectionTimeout默认60000、disableUploadTimeout默认true，影响请求体读取等待；maxSwallowSize默认2MiB，早拒绝上传时客户端可能只看到断连。所以UI要处理status0，不将“固定413 JSON总能到达”当不变量。
- 审计时/opt/talent、/tmp同99G文件系统，26G已用/69G可用。只是采样事实，不能作为发布保证；上传前容量检查、发布前df和真实1GiB测试仍必需。
- 未实测事项：正式新接口不存在，故没有1GiB端到端吞吐、RSS、慢链路/Range上线结果。计划仅规定后续真实验收，不声称已经支持。

## E-9 鉴权与XHR适配证据

- `auth/config/AuthWebConfig.kt` 注册/api/**拦截，排除login/me；AuthInterceptor未登录401、强制改密403，用户名来自会话。
- `mail/controller/ExpertContactNoteController.kt` 还显式检查会话，作为新controller边界样例。无新增RBAC或用户私有视频概念。
- `static/task-modal-runtime.js:311 handleAuthResponse` 401停已认证app并开登录遮罩；403 clone().json()读取PASSWORD_CHANGE_REQUIRED。因此新XHR用小JSON Response适配器而不是传裸xhr对象。HTTP状态0不能构造Response，走网络未知态。

## 证据可复核性

- [source-sha256](contact-profile-evidence/source-sha256.json)记录审计时src/main、src/test、pom.xml哈希；[revision](contact-profile-evidence/revision.txt)为当前HEAD。工作区含既有未提交文档，不假定HEAD等于所有工作树内容。
- grep回执保存完整命令、退出码、file:line；“新表不存在”exit1属于零匹配，不是检索成功发现表。
- 实施前若关键源文件哈希变化，重跑受影响审计并修正计划，不照旧行号机械修改。所有业务改动文件仅由子计划白名单授权，本附件不是额外写权限。
