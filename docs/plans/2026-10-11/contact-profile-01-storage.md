# 01｜人工档案存储与 API

状态：待评审；只规划，未实现。依赖：无。执行顺序见 [总计划](contact-profile-development-master.md)。

## 需求描述

在未联系和已联系专家上读写 19 项人工资料；分组保存后刷新可读。姓名、国籍、关键词仍读现有数据。

保持：不创建联系人、不发邮件、不推进状态；不修改 ES、原材料与收发件箱数据。范围外：Excel 导入、数据纠错、企业库、全文搜索、AI 自动填充、身份合并。

## 关键不变量

### Invariant I-1: 稳定身份
- Rule: expert_key 为 ExpertIdNormalizer.normalize(trim(expertId))，1–64 字符。EMAIL-* 是系统标识而非 ORCID 链接。不得使用邮箱、姓名、contactId 或猜测的 ES _id 替代；所有接口均需现有登录会话。
- Applies to: GET/PATCH、身份解析、后续视频创建
- Violation consequence: 串人、未联系专家无法保存
- 来源: K-expert-contact-two-write-sites；K-bound-expert-cross-campaign-read

### Invariant I-2: 人工数据唯一所有者
- Rule: 仅 ExpertManualProfileService 写新表；现有共享 expert_contact、ES 三层索引新增字段数均为 0。新表没有 contact 外键，自动邮件整行 save 不触及它。
- Applies to: 全部保存与后续读取
- Violation consequence: 自动更新覆盖人工资料
- 来源: 本次需求与代码审计

### Invariant I-3: 分组与版本
- Rule: GET 无行返回默认 DTO 与 version=0，不插入。PATCH 只更新指定组全部字段；组字段必须齐全，拒绝额外键，NULL/空白=清空。expectedVersion 必须匹配；成功 version+1。首次插入冲突与旧版本统一409，失败事务不改变任何字段。
- Applies to: GET/PATCH/并发插入
- Violation consequence: 旧页面覆盖他人修改
- 来源: 本次需求与代码审计

### Invariant I-4: 文本、审计与敏感值
- Rule: 字符串按 Unicode code point 计长，统一 CRLF/CR 为 LF、trim；不静默截断。单行列拒绝换行。updated_by 取 session 用户（≤64），updated_at 服务端 Asia/Shanghai 毫秒；不接受客户端审计字段，不记录证件值到日志。
- Applies to: 全部字段验证、SQL参数化、响应
- Violation consequence: 截断或伪造审计
- 来源: 本次需求与代码审计

### Invariant I-5: 人才编号
- Rule: `talentCode`：保留前缀、前导零；不充当专家主键。类型/长度见下表；普通文本缺省 NULL。
- Applies to: identity 分组 PATCH、GET、后续页面编辑
- Violation consequence: 人才编号语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-6: 中文译名
- Rule: `chineseName`：不自动覆盖原文姓名。类型/长度见下表；普通文本缺省 NULL。
- Applies to: identity 分组 PATCH、GET、后续页面编辑
- Violation consequence: 中文译名语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-7: 出生日期 / 年份
- Rule: `birthDateText`：只接受 YYYY 或真实 YYYY-MM-DD，年份 0001–当前年，完整日期不得晚于当天；年份不补成 1 月 1 日。类型/长度见下表；普通文本缺省 NULL。
- Applies to: identity 分组 PATCH、GET、后续页面编辑
- Violation consequence: 出生日期 / 年份语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-8: 护照 / 证件号
- Rule: `documentNumber`：字符串，保留前导零；非编辑态仅显示末四位，其余以 • 遮罩；≤4 位全遮罩。类型/长度见下表；普通文本缺省 NULL。
- Applies to: identity 分组 PATCH、GET、后续页面编辑
- Violation consequence: 护照 / 证件号语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-9: 来源国家
- Rule: `sourceCountry`：不得从 country 或 nationality 自动复制。类型/长度见下表；普通文本缺省 NULL。
- Applies to: identity 分组 PATCH、GET、后续页面编辑
- Violation consequence: 来源国家语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-10: 毕业学校
- Rule: `graduatedSchool`：允许多行多学校，不推定学位。类型/长度见下表；普通文本缺省 NULL。
- Applies to: education 分组 PATCH、GET、后续页面编辑
- Violation consequence: 毕业学校语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-11: 学校排名
- Rule: `schoolRanking`：QS 与描述文字原样保存，不数值化。类型/长度见下表；普通文本缺省 NULL。
- Applies to: education 分组 PATCH、GET、后续页面编辑
- Violation consequence: 学校排名语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-12: 来华前单位
- Rule: `preChinaOrganization`：与当前 employment 独立，不自动回填。类型/长度见下表；普通文本缺省 NULL。
- Applies to: education 分组 PATCH、GET、后续页面编辑
- Violation consequence: 来华前单位语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-13: 单位排名
- Rule: `organizationRanking`：保留排名描述，不数值化。类型/长度见下表；普通文本缺省 NULL。
- Applies to: education 分组 PATCH、GET、后续页面编辑
- Violation consequence: 单位排名语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-14: 博士后单位
- Rule: `postdocOrganization`：保留多单位多行。类型/长度见下表；普通文本缺省 NULL。
- Applies to: education 分组 PATCH、GET、后续页面编辑
- Violation consequence: 博士后单位语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-15: 博士后年限
- Rule: `postdocPeriod`：可填 2018–2020 或 2年，不推断、不转整数。类型/长度见下表；普通文本缺省 NULL。
- Applies to: education 分组 PATCH、GET、后续页面编辑
- Violation consequence: 博士后年限语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-16: 职称
- Rule: `professionalTitle`：不与行政职务合并。类型/长度见下表；普通文本缺省 NULL。
- Applies to: education 分组 PATCH、GET、后续页面编辑
- Violation consequence: 职称语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-17: 行政职务 / 头衔
- Rule: `administrativeTitles`：保留多行头衔，与职称独立。类型/长度见下表；普通文本缺省 NULL。
- Applies to: education 分组 PATCH、GET、后续页面编辑
- Violation consequence: 行政职务 / 头衔语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-18: 产业领域（表格 P 列暂定名）
- Rule: `industry`：不等同学科分类，保持文本；P1 确实为空，不自动导入。类型/长度见下表；普通文本缺省 NULL。
- Applies to: technology 分组 PATCH、GET、后续页面编辑
- Violation consequence: 产业领域（表格 P 列暂定名）语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-19: 技术专长
- Rule: `technicalExpertise`：不替代 keyword 或 researchFields，保留换行。类型/长度见下表；普通文本缺省 NULL。
- Applies to: technology 分组 PATCH、GET、后续页面编辑
- Violation consequence: 技术专长语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-20: 申报类别（QM/BS/BSH/HJ）
- Rule: `programCategories`：自由文本，允许 QM/BS/工程师/RJ；不猜缩写含义、不做封闭枚举。类型/长度见下表；普通文本缺省 NULL。
- Applies to: technology 分组 PATCH、GET、后续页面编辑
- Violation consequence: 申报类别（QM/BS/BSH/HJ）语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-21: 科研能力
- Rule: `researchAbility`：NULL=未评估；VERY_STRONG=很强、STRONG=强、ABOVE_AVERAGE=中上、AVERAGE=中等、FAIR=一般、LOW=低；不自动评分。类型/长度见下表；普通文本缺省 NULL。
- Applies to: matching 分组 PATCH、GET、后续页面编辑
- Violation consequence: 科研能力语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-22: 匹配企业进度
- Rule: `companyMatchStatus`：NOT_MATCHED=未匹配、MATCHING=匹配中、MATCHED=匹配上；默认 NOT_MATCHED，不修改联系阶段。类型/长度见下表；普通文本缺省 NULL。
- Applies to: matching 分组 PATCH、GET、后续页面编辑
- Violation consequence: 匹配企业进度语义或显示丢失
- 来源: 本次需求与代码审计

### Invariant I-23: 匹配到的企业
- Rule: `matchedCompanies`：API 为字符串数组；去每项首尾空白、去空行、精确去重、保留顺序；数据库用换行连接；每项不得含换行；总文本≤2000；MATCHED 至少一家；改变进度不得隐式清空。类型/长度见下表；普通文本缺省 NULL。
- Applies to: matching 分组 PATCH、GET、后续页面编辑
- Violation consequence: 匹配到的企业语义或显示丢失
- 来源: 本次需求与代码审计

## 现状审计

- 存储、全部命中清单、身份与两宿主证据见 [审计](contact-profile-audit.md) E-1/E-2/E-3/E-7；本计划不改这些旧表或 ES。新表检索零命中见 `contact-profile-evidence/new-store-absence.txt`。
- 当前 expert_contact 仅 `(campaign_id,orcid_id)` 唯一；备注主键是 contactId。新档案不能按 contactId 建模。（来源: K-expert-contact-two-write-sites、K-bound-expert-cross-campaign-read）
- 新表全部预定写路径：本服务首次 INSERT、分组 CAS UPDATE；无 DELETE、定时任务、ES 同步、回填。全部读路径：本服务 GET、身份解析器对 key 的存在性查询、02 页面 GET、04 新视频的身份核验。
- 交互 X1：API 写→页面刷新读；X2：邮件更新 / ES 晋降层→原档案仍按同 key 读；X3：两个操作者→版本冲突；X4：档案存在性→视频身份核验。
- Flyway 本地及线上迁移文件最高 V152；这不等于已经查询过线上 schema history。开始实施/部署前查实际 history 和分支最新版本。来源 K-flyway-version-follows-deploy-order、K-flyway-latest-version-test-pin。

## 实现方案

### 1. 模型与初始 schema（I-1–I-23）

修改白名单中的 `V153__create_expert_manual_profile.sql`、`ExpertManualProfile.kt`。新表 `expert_manual_profile` 使用 InnoDB/utf8mb4；`expert_key VARCHAR(64) COLLATE utf8mb4_bin PRIMARY KEY`；下表为真实独立列，禁止 JSON/EAV 压成一个字段。除匹配进度 NOT NULL DEFAULT 'NOT_MATCHED' 外，业务列可空；`version BIGINT NOT NULL DEFAULT 0`、`updated_by VARCHAR(64) NOT NULL`、`updated_at DATETIME(3) NOT NULL`。不加无请求的全局人才编号唯一约束。

| API 字段 | 中文 | SQL 类型 / 上限 | 分组 |
|---|---|---|---|
| `talentCode` → `talent_code` | 人才编号 | VARCHAR(64)；64 | identity |
| `chineseName` → `chinese_name` | 中文译名 | VARCHAR(200)；200 | identity |
| `birthDateText` → `birth_date_text` | 出生日期 / 年份 | VARCHAR(10)；10 | identity |
| `documentNumber` → `document_number` | 护照 / 证件号 | VARCHAR(100)；100 | identity |
| `sourceCountry` → `source_country` | 来源国家 | VARCHAR(100)；100 | identity |
| `graduatedSchool` → `graduated_school` | 毕业学校 | TEXT；2000 | education |
| `schoolRanking` → `school_ranking` | 学校排名 | VARCHAR(500)；500 | education |
| `preChinaOrganization` → `pre_china_organization` | 来华前单位 | TEXT；2000 | education |
| `organizationRanking` → `organization_ranking` | 单位排名 | VARCHAR(500)；500 | education |
| `postdocOrganization` → `postdoc_organization` | 博士后单位 | TEXT；2000 | education |
| `postdocPeriod` → `postdoc_period` | 博士后年限 | VARCHAR(200)；200 | education |
| `professionalTitle` → `professional_title` | 职称 | VARCHAR(200)；200 | education |
| `administrativeTitles` → `administrative_titles` | 行政职务 / 头衔 | TEXT；4000 | education |
| `industry` → `industry` | 产业领域（表格 P 列暂定名） | VARCHAR(500)；500 | technology |
| `technicalExpertise` → `technical_expertise` | 技术专长 | TEXT；4000 | technology |
| `programCategories` → `program_categories` | 申报类别（QM/BS/BSH/HJ） | VARCHAR(500)；500 | technology |
| `researchAbility` → `research_ability` | 科研能力 | VARCHAR(16)；枚举 | matching |
| `companyMatchStatus` → `company_match_status` | 匹配企业进度 | VARCHAR(16)；枚举 | matching |
| `matchedCompanies` → `matched_companies` | 匹配到的企业 | TEXT；2000 | matching |

**范围解释**：这是首次建立、单一写服务拥有的私有业务聚合，不是给现有多写者共享表一次加 19 列；对既有共享存储新增业务字段数为 0。避免为同一表拆 19 次虚假迁移，也不为规避限制藏入 JSON。

### 2. 身份核验（I-1/I-2）

`ExpertProfileIdentityResolver.kt` 提供 `requireKnown(expertId, level): String`：先做规范化和长度校验；已有新档案行或 `ExpertContactRepository.existsByOrcidId`（原始 trim 与规范值去重）可证实存在；否则调用现有 `ExpertSearchService.findByOrcidId`，依次选中层、其余 RAW/CANDIDATE/APPLICATION，按原始 trim / 规范值去重查询。返回记录必须规范化后等于请求 key；不以 _id 猜测回退。全层未命中404；查询服务失败503，不能伪装404或新建空专家。level 只接受现有三种枚举。空 orcidId 专家显示“缺少专家标识，无法保存”，不补假 ID。已有档案记录在 ES 临时失联时仍可读写；不自动清除历史记录。

### 3. 读写接口（I-1–I-23）

`ExpertManualProfileService.kt` 使用现有 NamedParameterJdbcTemplate + TransactionTemplate/事务，固定组 SQL，不拼接客户端列名。`ExpertManualProfileController.kt`：

- `GET /api/experts/manual-profile?expertId=...&level=RAW` → `{expertId,version,identity,education,technology,matching,updatedBy,updatedAt}`。各组包含上表对应键；空 companies 返回 []，对应数据库 NULL；不同时使用空字符串作为第二空值表示。不混入读不到的 ES 字段，现有三字段仍由既有专家响应提供。
- `PATCH /api/experts/manual-profile` JSON `{expertId,level,group,expectedVersion,values}`。group 为 identity/education/technology/matching；成功200返回完整最新 DTO。
- existing row：`UPDATE ... SET 本组各列=?,version=version+1,updated_by=?,updated_at=? WHERE expert_key=? AND version=?`。影响0行409；首次 expectedVersion=0 时 INSERT 完整默认值及本组、version=1，重复键409。参数验证在事务写入前，成功响应从同事务读取。
- 私有 ProfileException 在本 controller 的 `@ExceptionHandler` 映射固定 `{code,message,detail}`：401未登录、400无效参数、404专家不存在、409 PROFILE_CONFLICT、503 EXPERT_LOOKUP_UNAVAILABLE、500 PROFILE_STORE_FAILED；数据库失败由service转换固定业务异常，内部SQL/路径不能透给客户端。不改全局异常处理器。
- 匹配进度和 companies 同组提交验证；其它组保存绝不能重写这两列。

### 4. 测试与迁移（I-1–I-23）

修改清单中三个单测与 Flyway IT。参数化覆盖 19 字段的样例、空值、上限、超限；真实 MySQL8 两连接同时 version0 插入与同version更新。更新所有“迁移到最新”152断言为153，保留有意的历史 target 与 V152 测试名。禁止全文件盲替换152。测试迁移152→153、全新库→153；不中断现有 notes/drafts 外键与数据。

## 变更文件清单

| 文件 | 操作 / 职责 |
|---|---|
| `src/main/resources/db/migration/V153__create_expert_manual_profile.sql` | 新建：独立人工档案表 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertManualProfile.kt` | 新建：DTO、分组白名单、枚举、业务异常 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertProfileIdentityResolver.kt` | 新建：已存在专家身份校验 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertManualProfileService.kt` | 新建：单一 SQL 读写所有者 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileController.kt` | 新建：GET/PATCH 与局部异常映射 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertProfileIdentityResolverTest.kt` | 新建：身份、大小写、晋升、失败语义 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertManualProfileServiceTest.kt` | 新建：19 字段、分组与冲突 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileControllerTest.kt` | 新建：鉴权与 API 契约 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 修改：V153 真库迁移与并发持久化验证 |

共 **9 个文件**；未列文件禁止顺手修改。测试证据为执行产物，不扩张产品修改白名单。

## 验收标准

- I-1：原始/大小写 EMAIL key、真 ORCID、空/超长/不存在、跨层、ES异常、无contact、两个campaign同专家、未登录均有测试；不依赖 `_id==orcidId`。
- I-2：新表唯一写者 grep；邮件更新前后全部档案字段不变；源文件 diff 无旧表/ES修改。
- I-3：四组键集、空行GET无写、两次并发一个200一个409、组间冲突、未知字段400；失败后再GET原值。
- I-4：CRLF/Unicode/控制审计字段/超长、证件值未进入日志。
- I-5–I-23：逐行按规则做往返、清空和无效输入断言，不能只测 DTO 构造。I-7另测闰日/未来日；I-21测六档；I-22/I-23测 MATCHED 空企业400及进度变化保留企业。
- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertProfileIdentityResolverTest,ExpertManualProfileServiceTest,ExpertManualProfileControllerTest test`；Docker可用时 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test`。依赖未运行必须报告未验证，不能记 PASS。

## 人工验收清单

### A-1: 19项真实持久化
- 前置条件: 在测试环境通过专家列表选一个无contact的真实专家，记录 expertId/level；使用同会话 API 客户端。
- 操作步骤: 1. GET记录version。2. 对四组按字段表填入 `field-contract.json` 最后一列样例（companies用["测试企业甲","测试企业乙"]）。3. 每次以返回version提交。4. 刷新GET。
- 预期结果: 19项逐项返回提交值，birthDateText仍为1980；version连续+1；联系人数量不增加。
- 覆盖: I-1–I-23；X1

### A-2: 并发与清空
- 前置条件: A-1专家；两窗口同时GET同version。
- 操作步骤: 1. 窗口甲改中文译名。2. 窗口乙持旧version改职称。3. 乙重新读取后再保存。4. 清空中文译名。
- 预期结果: 第一次200；旧版本409 PROFILE_CONFLICT且甲值保留；重读后可保存；清空后NULL，其他组不变。
- 覆盖: I-3/I-4；X3

### A-3: 自动流程与跨层保持
- 前置条件: 测试专家已有档案；测试环境邮件发送指向测试收件箱。
- 操作步骤: 1. 记录19字段。2. 使用既有“晋升/降级”操作改变层级。3. 已有联系人执行一次原状态操作。4. 重新GET档案。
- 预期结果: 19字段与version不变；专家阶段只按原操作变化；未触发新邮件/新材料/备注写入。
- 覆盖: I-1/I-2；X2；保持项

### A-4: 校验与鉴权
- 前置条件: 同A-1。
- 操作步骤: 1. 提交1980-02-30。2. 提交非法科研枚举。3. 提交MATCHED和空数组。4. 注销后GET。5. 提交不存在expertId。
- 预期结果: 前三个400且未保存；未登录401；不存在404。
- 覆盖: I-1/I-7/I-21/I-22/I-23

### A-5: 已存档案的身份持续可读
- 前置条件: 无contact专家已通过本接口保存；仅在隔离测试环境临时阻断ES连接。
- 操作步骤: 1. GET该expertId。2. 用返回version更新中文译名。3. 恢复ES。
- 预期结果: GET与PATCH都200，不增加联系人；只有新且无法查证的专家受ES不可用503影响。后续视频使用同身份核验的跨路径验收归04 A-1。
- 覆盖: I-1/I-2；X4的存在性读取基础，不依赖04发布

