# 06a — Apollo 两类接口权限的持久设置

状态：待执行。提供独立设置API；06b接入任务运行，07展示保存界面。此切片可以单独部署，但在06b完成前不能宣称新任务已采用保存值。

## 需求描述

在数据库分别保存免费搜索和付费详情开关，提供可读、可保存、有并发保护的设置API。Key沿用服务器配置，不存入新表、不回传页面。

必须保持：现有任务执行与准入规则、本地核验、国家补全、原样本、邮件不变；保存不调用Apollo、不重启、不补跑任务。范围外：执行worker改造（06b）、前端（07）、Key编辑、购买额度、准入条件放宽。

## 关键不变量

### Invariant I-1: 两个独立布尔值
- Rule: singleton id=1保存searchEnabled/detailEnabled；关闭search不清空detail；有效详情权限=服务可用且searchEnabled且detailEnabled。首次无行detailEnabled=false；search默认沿用properties.enabled/key/apolloDefault判断，不生成“已保存”假行。
- Applies to: Repository.find/save、SettingsService.resolve、GET/PUT。
- Violation consequence: 免费连带付费，刷新后设置丢失。
- 来源: original。

### Invariant I-2: 原子保存与版本
- Rule: PUT必须携带revision；同值保存不改revision；不同值CAS+1；无行revision=0。并发冲突409、存量字段非法/DB故障503，不回退部署默认。设置更新时间UTC DATETIME(3)。
- Applies to: 首次写入、更新、回读及重启。
- Violation consequence: 覆盖他人设置、故障时意外启用。
- 来源: K-task-launch-config-registration。

### Invariant I-3: 只有存储副作用
- Rule: 保存动作只写discovery_apollo_setting，不触碰run/item/attempt、专家、邮件、配置文件；不发外部请求。所有API复用/api登录拦截。只返回keyConfigured布尔值。
- Applies to: controller/service/错误响应/日志。
- Violation consequence: 保存产生扣费或改变执行中的任务。
- 来源: original。

### Invariant I-4: 冻结格式只有一处定义
- Rule: 本服务定义FrozenApolloAccess schemaVersion=1、settingRevision、searchEnabled、detailEnabled、source；新快照source为SAVED/DEPLOYMENT_DEFAULT；DTO额外允许LEGACY仅供06b服务端解释旧run，PUT不接受source字段。格式非法不能按false或默认值掩盖。设置不包含资格规则或系统请求总配额。
- Applies to: 06b写读settings_json的公共解析/构造函数。
- Violation consequence: worker与registration默认值不一致、恢复变策略。
- 来源: K-discovery-reentry-keeps-run-accounting。

## 现状审计

- 当前DiscoveryVerificationProperties有apolloDefault和部署Key，没有DB保存开关；Registration.resolveOptions从properties及criteria取值，代码快照已收录。
- singleton范例：DiscoveryScheduleSettingRepository.find/save，id=1，UTC时间、无行与读失败严格区分；本计划只复用模式，不写调度表。
- 新表由本计划创建，无存量读写者；唯一SQL写口是新Repository，读者新SettingsService/Controller，后续06b Registration调用同一service。
- AuthWebConfig.addInterceptors覆盖/api/**，新controller无需加白名单。QueryService/旧VerificationController保持不变，本切片不会造成它们现有单元测试构造器变化。
- V159已分配01原文库，V160用于此表；执行前检查编号。官方API分类与当前客户端确切endpoint见06b现状审计。

## 实现方案

### T-1 schema与唯一写口（I-1/I-2/I-3）

新增迁移完整DDL：

```sql
CREATE TABLE discovery_apollo_setting (
  id INT NOT NULL,
  search_enabled TINYINT(1) NOT NULL,
  detail_enabled TINYINT(1) NOT NULL,
  revision BIGINT NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT chk_discovery_apollo_setting_id CHECK (id = 1),
  CONSTRAINT chk_discovery_apollo_setting_values CHECK (
    search_enabled IN (0,1) AND detail_enabled IN (0,1) AND revision > 0
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

Repository只暴露find/save；应用读写也验证范围，不依赖CHECK一定执行。save(expectedRevision,search,detail,now)在事务内SELECT FOR UPDATE；无行且expectedRevision=0插入revision1；竞争插入转换为409；已有行版本不等409；同值返回原行；变更时CAS写revision+1并回读。不得先返回成功再异步写DB。

### T-2 服务、协议及API（I-1～I-4）

`DiscoveryApolloSettingsService.kt`包含设置DTO与FrozenApolloAccess，依赖Repository/Properties/Clock，不能依赖Worker以免循环注入。无行有效默认：searchEnabled=properties.enabled && key非空 && apolloDefault；detailEnabled=false；revision0/source=DEPLOYMENT_DEFAULT/updatedAt=null。

GET `/api/expert-discovery/verification/apollo-settings`：

```json
{"revision":0,"source":"DEPLOYMENT_DEFAULT","searchEnabled":true,"detailEnabled":false,"effectiveSearch":true,"effectiveDetails":false,"keyConfigured":true,"serviceEnabled":true,"workerEnabled":true,"updatedAt":null}
```

示例中的布尔值是可用部署下的例值，必须实际计算。effectiveSearch=searchEnabled&&serviceEnabled&&keyConfigured；effectiveDetails=effectiveSearch&&detailEnabled。workerEnabled单列，worker关闭表示等待执行，不伪装为Key缺失。鉴权熔断状态仍由原capabilities提供，不在此服务再缓存。

PUT同路径body严格为`{revision,searchEnabled,detailEnabled}`；正整数/0 revision，两布尔值必填，未知字段400；保存允许服务/key暂不可用，回读明确effective=false。响应不包括key明文/前后缀，不在异常中序列化Properties。

用独立 `DiscoveryApolloSettingsController.kt` 承载两个端点；不重复声明既有VerificationController的其他映射。关闭免费不会改 paid 已保存值，也不把“恢复默认”隐含为删行；本版不提供删设置API。

### T-3 持久化测试（I-1～I-4）

新增SettingsIT同时覆盖真实MySQL CAS、Controller HTTP响应、重启service读取、无行/坏行/DB异常。使用本地假HTTP观测器断言保存期间外部请求0。测试fixture可显式seed四组合，提供06b复用的设置构造方式；不能在生产代码加入“测试模式跳过DB”的分支。

## 变更文件清单

5文件；子系统1；既有共享store新字段0（新表完整建模）。

| 文件 | 动作 |
|---|---|
| src/main/resources/db/migration/V160__create_discovery_apollo_setting.sql | 新singleton表 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryApolloSettingRepository.kt | 唯一SQL写口 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryApolloSettingsService.kt | DTO/默认/快照协议 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryApolloSettingsController.kt | 独立GET/PUT映射 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryApolloSettingsIT.kt | 数据库/API/CAS/副作用 |

## 验收标准

- I-1：四组合保存回读一致；false/true时detail仍true，effectiveDetails=false。无行默认paid=false。
- I-2：两个revision0并发首次保存仅1成功、另1为409；重启读相同值；同值保存revision不动；DB失败503不返回默认。
- I-3：未登录401；返回/日志不含测试Key；所有非设置表行数及run快照不变；请求观测器0次。
- I-4：FrozenApolloAccess roundtrip一致、非法schema/字段拒绝；没有系统500次请求配额字段。
- JDK11执行SettingsIT及现有VerificationControllerTest；不能用mock替代真实SQL竞争测试。旧运行代码此时不受影响。

## 人工验收清单

### A-1: 保存、独立开关、重启
- 前置条件：隔离验收实例应用V160，表空，服务/key配置可用；登录获得会话。
- 操作步骤：1. GET设置。2. PUT revision0/search=true/detail=false。3. 重启实例再GET。4. 按返回revision PUT search=false/detail=true。
- 预期结果：初始source=DEPLOYMENT_DEFAULT、detail=false；首次保存revision1，重启仍true/false；最后false/true且effectiveDetails=false；没有请求Apollo。
- 覆盖：I-1/I-2/I-3；HTTP→DB→重启回读。

### A-2: 冲突与故障
- 前置条件：两个会话读取同revision；测试Key值为合成字符串；可临时停止隔离MySQL。
- 操作步骤：1. A/B依次用旧revision保存不同组合。2. 停MySQL再GET/PUT。3. 查响应与日志。4. 恢复数据库并查正在运行任务快照。
- 预期结果：B409；DB故障503，不回退默认；没有Key；任务快照和所有准入状态不变。
- 覆盖：I-2/I-3/I-4；must-NOT-change。
