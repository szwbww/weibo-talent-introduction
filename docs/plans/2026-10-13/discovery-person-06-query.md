# 06 人物列表、精确计数与详情读接口

状态：设计，未实施。依赖 04/05；API 与 [07 UI](discovery-person-07-ui.md) 联合开启。上级：[总计划](discovery-identity-recovery-master.md)。

## 需求描述

新发现的同一人只显示一次，人数、分页、筛选一致，详情能列出邮箱及原历史入口。保持原 docId/contactId 接口可读、非 discovered 行为和所有写入语义；不改邮件箱会话分组，不复制学术或准入真值。

## 关键不变量

### Invariant I-1：完整匹配集先归人
- Rule：在当前层级执行完整原筛选，再按有效人物键聚合、排序、分页；整套条件必须有同一成员满足。范围外 key 仍用原 docId 或原 contactId，不根据姓名合并。默认排序取命中成员中原排序最前者，最后以 personId/docId 保证稳定；不以主档缺失于本层为由隐藏整个人。
- Applies to：ES 列表、DB 联系人分支、总数及标签/地区/域名聚合。
- Violation consequence：重复跨页、漏人或拼接条件产生假匹配。
- 来源：K-group-before-pagination、本轮双列表分支审计。

### Invariant I-2：详情与操作的引用不丢
- Rule：响应新增只读 person 对象，含 personId/revision、代表 docId、邮箱成员、各成员原层级/contactId/状态及证据来源；不会把 personId 填进旧 orcidId 参数。每个邮箱资格来自自身；人工资料、运营状态冲突展示“存在差异”，不覆盖任何成员。旧链接定位人物并标出原邮箱。编辑/发送/材料操作仍明确指向选中的原成员，不暗中群改。
- Applies to：两个控制器响应、详情查询、07 消费。
- Violation consequence：历史或人工资料被代表档案吞掉，旧操作指向错误对象。
- 来源：K-expert-detail-two-panel-render-sites、总计划 I-5。

### Invariant I-3：精确性与时效
- Rule：流式查询必须完整结束才返回精确 total；超预算返回可重试的查询不可用，不返回部分数或近似数。人物关系批量加载并核验当前标签/证据指纹；失效关系暂按独立档案显示并标记待核，发送另外阻断。GET 不登记、不修复、不创建联系人。不声称 ES 与 SQL 是事务快照。
- Applies to：查询服务、聚合、旧链接解析。
- Violation consequence：假人数、过期聚合或预览写数据。
- 来源：现有 ES track_total_hits=true 契约、总计划 I-8。

## 现状审计

- `ExpertSearchService`：searchExperts 直接 ES from/size；scrollExperts/scrollExpertsFiltered/searchAfterExpertsFiltered；countExperts；findByOrcidId/findByDocumentIds；aggregateTags/aggregateRegions/aggregateEmailDomains。现有发送也用低层搜索方法，不能全局把 ExpertProfile 的单邮箱行为改变。
- `ExpertIndexController.listExperts` 从当页 docId 查 contact；标签/地区/域名各接口直接用 ES 桶数。其 ExpertIndexResponse 定义在同一文件。
- `ExpertContactManagementController.listContacts` 调 service.listContacts 返回完整联系人数组，totalCount=contacts.size；前端另行域名筛选和切页。其 response DTO 同在该文件，详细历史用 getContactDetail(contactId)。
- 存储写者仍是现有发现/补全/标签、候选迁移、operator 同步、联系人服务、邮件流程；本子计划只读 ES/SQL，不新增投影写路径。原 admission 和邮箱状态只读；04 是人物关系唯一来源。
- 交互：同人分散于不同 ES 页；RAW/CANDIDATE 成员不同；联系人分支绕过 ES 列表；合并成员存在不同状态和不同人工资料；原层标签/身份被另一写路径更新。

## 实现方案

1. **人物查询服务（I-1/I-3）**：新增 DiscoveryPersonQueryService。复用 ExpertSearchService 原筛选构造，将混合查询分成 discovered 与其他两部分：前者流式读取、按 500 条批量取人物关系、收集最小投影后精确归组；后者保持原 ES 精确 count，仅取合并排序所需的前 from+size 条，不能为了归并 5 万条去全量扫描其他专家。以同一排序键归并前缀后截取最终页，total=其他原始匹配数+discovered 人物数；各聚合桶同样分区相加。关闭 scroll。人物分区每次最多读取 100,000 条、10 秒，超限明确失败。先在 51,829 条真实形状脱敏数据测量：目标 p95≤2 秒、额外堆内存≤128 MiB；未达则先修订专用物化投影子计划，不把未证实性能当已通过。首版不建跨请求缓存，避免关系/标签失效未处理。
2. **ES API（I-1— I-3）**：ExpertIndexController 的专家列表、筛选计数接口调用新服务，返回 `totalHits` 人物口径；原 raw 文档搜索 API 仍给后台任务使用。聚合各桶内按人去重，不要求各桶相加等于总人数，因为同人可命中多个域名。新增只读人物详情 `GET /api/experts/person?docId=...&level=...`，读取全部有效邮箱成员并明确每条层级，原 docId 输入可直接定位。
3. **DB API（I-1— I-3）**：ExpertContactManagementController 增加可选 `groupDiscoveredPersons=true`，默认保持旧客户端契约；此分支在成员级完成既有状态/域名条件后聚合。返回总人数、person 信息及所有原 contactId；07 去掉此分支的邮箱域名前端二次过滤，避免漏掉非代表邮箱。没有请求新模式的旧客户端响应保持。详情保留当前 contact 原历史，同时可通过人物接口发现其他成员历史入口。
4. **四份定向测试（I-1— I-3）**：两控制器、SearchService 和新查询服务，覆盖混合范围、跨页、跨层、过滤不拼接、精确桶数、超时失败及只读。范围外任何业务行不写。

## 变更文件清单

|文件|动作|
|---|---|
|src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt|新增，含只读响应 DTO|
|src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt|修改流式查询支持，保持旧低层方法单位|
|src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt|修改|
|src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt|修改|
|src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt|新增|
|src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt|修改|
|src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt|修改|
|src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt|修改|

共 8 文件；专家读取与联系人读取两个子系统，共享存储增字段 0。

## 验收标准

- I-1：同人三档案跨三页，聚合后 total=1；混入异人后 total=2；RAW 独有邮箱不增加候选数；同一组 A 国家、B 领域各满足一半时不命中合取筛选。
- I-2：三个旧 docId 均返回同一 personId，分别能找到原邮箱/contactId；字段差异不复制或覆盖，原详情接口可访问。
- I-3：只读写入 0；过期关系不继续聚合；超过预算明确失败，未返回部分 total；性能门禁有实测记录。

## 人工验收清单

### A-1：两条列表与分页
- 前置条件：导入同人三个不同邮箱档案及两个对应联系人，另有异人和范围外各一条，page size=1；其中一邮箱只在 RAW。
- 操作步骤：请求 ES 和 DB 人物列表，翻所有页；查询域名、国家、领域联合筛选及各桶数。
- 预期结果：每条分支同人只出现 1 次；成员层级限制有效；通过非代表邮箱域名也能找到此人；条件分散在不同成员不能拼出命中。
- 覆盖：I-1、两列表/分页/层级交互。

### A-2：旧链接、只读与失败
- 前置条件：一组三个原 docId 各有不同资料/邮件；另准备过期成员指纹和超预算查询。
- 操作步骤：用每个旧 docId 访问人物详情并跟随原 contactId；执行过期/超预算请求，对比数据库审计。
- 预期结果：旧邮件和资料均可定位且数值未变；人物详情显示差异；过期成员不误并；超预算为查询不可用；业务写入 0。
- 覆盖：I-2/I-3、历史保护与失效关系。
