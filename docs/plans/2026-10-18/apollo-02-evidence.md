# 02 · Apollo搜索、公开证据读取与纯规则

日期2026-10-18；待实施。依赖按[总计划](apollo-verification-master.md)顺序。规范附录：[contracts](apollo-contracts.md)、[代码审计](apollo-code-audit.md)。实施前核对固定HEAD差异。

## 需求描述

O-1：把真实响应形态转换为可审计的候选/证据/资格结论；提供可重放的匹配输出。保持N-1既有源身份与邮箱不变，N-2不写准入/ES、不调用收费接口。首版补证范围固定为绑定ORCID公开记录；不追加泛搜索/通用爬虫。

## 关键不变量

### Invariant I-1: 候选不等于本人
- Rule：严格执行C-4四条件，已绑定ORCID、当前机构与完整查询名形成闭环；遮蔽姓单独命中只能PENDING。所有歧义保持证据，不打分越阈值放行。
- Applies to：candidate matcher、name variants。
- Violation consequence：误配同名。
- 来源：总I-1/I-5/I-12。

### Invariant I-2: 资格四态
- Rule：MET/UNKNOWN/NOT_MET/CONFLICT分别返回，包含字段级证据与缺失项；资格矩阵依C-4。只写线索不能升MET，博士生、历史教授不能误认。
- Applies to：evidence parser、rules。
- Violation consequence：虚假已明确。
- 来源：总I-2。

### Invariant I-3: 请求边界与分页
- Rule：只有Search POST及ORCID公开GET；query参数编码、成功schema校验、宽搜优先及并集合并按C-3/4；错误与不完整不变NO_MATCH。每次外部请求先由调用者提供已占位RequestPermit。
- Applies to：两个client。
- Violation consequence：越权收费/遗漏候选/不可对账。
- 来源：总I-9/I-12。

### Invariant I-4: 纯计算可回放
- Rule：rules.evaluate输入结构化快照+证据+Apollo候选，输出结论/理由/证据；Clock注入，固定评估日期，零外部IO；字段白名单脱敏留存。
- Applies to：rules、fixtures。
- Violation consequence：无法复核准确性。
- 来源：总I-6/I-12。



## 现状审计

audit§6及 Apollo官方文档已核验；本地实际7+6响应是遮蔽姓，无学历年龄邮箱。OrcidDataSource:30–38未有学历与任职；不得直接调用不存在的服务方法。CandidateEligibilityService.hasDoctoralDegree:68使用contains，不能用于新严格已完成博士判断。此片新增client不修改原ORCID搜索/抽取流程。IP-1证据输入→03gate、IP-2判定→04提交。

## 实现方案

1. **Apollo client（I-1/I-3）**：固定baseURL https://api.apollo.io/api/v1，POST /mixed_people/api_search，header X-Api-Key、Accept JSON；参数q_person_name/page/per_page及可证机构限定；不传email。client只做一次请求，自动HTTP重试关闭，返回typed Success/RateLimited/TransientFailure/PermanentFailure。分页编排由04调度，client不得藏重试。
2. **ORCID reader（I-2/I-3）**：仅接受通过现有身份凭证绑定且校验格式的ORCID；读/person、/employments、/educations及必要单条详情；限总请求12、总响应每次2MiB；来源/可见性/开始结束日期逐字段记录。ORCID端点固定allowlist，不能跟响应中的任意URL抓取；网页URL仅作为证据链接。没有公开email时，只有原有可信ORCID作者绑定可以继续，否则IDENTITY_EVIDENCE_MISSING。404/空字段是缺证，401/429/超时是接口状态。
3. **parser/rules（I-1/I-2/I-4）**：按C-4实现姓名规范化、机构“相同标准ID/可靠别名”比较（不模糊把不同大学合并）、当前职称/完成博士/可靠年龄，输出明确原因词汇；同源重复内容不算第二独立证据。ORCID自述来源保留标识不自动升级官方资格。原始已知profile字段没来源时UNKNOWN。
4. **回放测试（I-1～I-4）**：把实测响应结构匿名化为fixture，包含遮蔽姓/无email字段；另有完整候选可选字段fixture，仅为schema兼容不声称账号一定返回。标注正反例按C-6，禁止测试自动访问公网；记录每个案例期望与理由。

## 变更文件清单

共9个文件；一个发现核验子系统（与既有准入/前端的交互按文中说明）；无清单外授权。

|#|文件|动作|
|---|---|---|
|1|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/ApolloPeopleSearchClient.kt`|新增|
|2|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidVerificationEvidenceClient.kt`|新增|
|3|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/QualificationEvidenceParser.kt`|新增|
|4|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt`|新增|
|5|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/ApolloPeopleSearchClientTest.kt`|新增|
|6|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/OrcidVerificationEvidenceClientTest.kt`|新增|
|7|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRulesTest.kt`|新增|
|8|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/QualificationEvidenceParserTest.kt`|新增|
|9|`src/test/resources/discovery/apollo-verification-cases.json`|新增匿名回放数据|

## 验收标准

I-1：正/反identity闭环、同名冲突、mask单独不通过；I-2：教授类名单/当前历史/在读/39与40/年龄缺失/相反证据；I-3：MockRestServiceServer逐请求验证endpoint/headers/schema/分页，并断言未出现enrich、email参数；I-4：固定Clock重放结果一致，异常和缺证不同。运行四个新测试类；本片没有可实测成功率承诺。

## 人工验收清单

### A-1: 匿名回放明细
- 前置条件：加载src/test/resources/discovery/apollo-verification-cases.json；测试运行器输出caseId、route、qualification、reasons与证据字段。
- 操作步骤：运行回放报告，依次检查完整同人、遮蔽同名、历史教授、博士生、39/40岁案例。
- 预期结果：同人缺资格为UNKNOWN可走APOLLO_UNCLEAR；单mask、歧义及博士生不得MET；每一判断有来源或缺失项。
- 覆盖：O-1，I-1/I-2/I-4，IP-1

### A-2: 接口失败不作否定事实
- 前置条件：Mock返回宽搜候选后窄搜空、分页5仍有下一页、429、403、空对象{}各1例。
- 操作步骤：调用对应测试驱动，查看请求清单和typed结果。
- 预期结果：宽搜候选保留；未完整SEARCH_INCOMPLETE；429 RateLimited；403 PermanentFailure；{} RESPONSE_INVALID；0次enrich/解锁请求。
- 覆盖：I-3，N-2，IP-2

### A-3: 不改原档案
- 前置条件：保存隔离ES原profile快照；离线验证上述样本。
- 操作步骤：对比email/identityVerification/degree/age和admission行。
- 预期结果：所有字段字节不变；仅输出计算报告，未产生准入。
- 覆盖：N-1/N-2，I-4
