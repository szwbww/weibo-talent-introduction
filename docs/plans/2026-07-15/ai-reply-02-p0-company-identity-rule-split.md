# P0-2：公司身份事实与信任说明规则拆分

## 需求描述

让“full name and registered location”只得到公司法定名称与注册地，不再自动拼入项目保密、无公开网站、政府合作证明和信任安抚长文。只使用现有已审核事实，不新增地址、证书编号或政府承诺。

Out of scope：coverage key 列、外部工商查询、公司完整注册地址、证书上传、其他 QA 内容重写。

## 关键不变量

### I-1：事实不扩张
- 新规则正文只能复用当前 V68 已存在的两项事实：法定名称、registered in Nanjing, China。
- 不把“registered in Nanjing”改写成未审核的街道地址。

### I-2：问题相关性
- 公司名称/注册地址关键词仅属于新公司身份规则。
- “is this legitimate / government cooperation / verify”仍属于 Agency credentials 规则。

### I-3：迁移安全
- 新迁移为 V75；不得改 V52/V65/V68/V70。
- INSERT 用 `NOT EXISTS(reply_subject)`；更新线上正文/关键词前必须导出并合并运营修改。（K-qa-rule-runtime-vs-migration-writes）

### I-4：自动/人工匹配一致
- 新规则参与现有 `QaMatchService`，不新增专用 controller 分支。
- 不改变 category compose order 与 supersede 逻辑。

## 现状审计

- V52 建立 Agency credentials 长正文。
- V65 收窄其 trust 关键词。
- V68 再向同一 id=18 追加公司 registration 关键词与公司名称段，导致精确公司问题命中整段 trust 内容。
- QA 规则存在 Flyway 与运营 UI 两类写路径；本迁移必须执行上线基线核对。

## 实现任务

### T1：新增 V75 拆分迁移
文件：`src/main/resources/db/migration/V75__split_company_identity_from_agency_credentials.sql`

1. 将 id=18/company credentials 的 keywords 恢复为 trust-only 短语，移除：`registered location`、`registered address`、`company registration`、`name of your company`、`your company name`、`full name and registered`、`where is your company`、`where are you based`。
2. 将其正文恢复为 credentials/verification 内容，不再附加公司名称/注册地段；保留现有 website、LinkedIn、证书/峰会说明。
3. 幂等插入新规则：
   - subject：`Company registered identity and location`
   - category：`TRUST_AND_COMPLIANCE`
   - priority：高于普通 trust 长文但不改 category order
   - keywords：只放公司法定名称、registration、registered location/address 等完整短语
   - body：两句内回答法定名称与 `registered in Nanjing, China`
   - auto reply enabled=true，handoff=false，enabled=true，supersedes=false
4. SQL literal 沿用 ASCII/UNHEX 中文 display_name 约定。

### T2：匹配回归测试
文件：`src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt`

- fixture 增加新公司身份规则，Agency credentials 使用 V75 trust-only keywords。
- `full name and registered location` 只进入新规则 candidate/suggested 集合，不包含 credentials 长文。
- `is this legitimate and can I verify your company` 仍命中 credentials。
- 同一邮件同时问 registration + legitimacy 时允许两条分别命中，顺序由现有 category/priority 契约决定。

## 上线前研究检查点

执行迁移前只读导出：

```sql
SELECT id, keywords, reply_subject, reply_body, updated_at
FROM qa_rule
WHERE id = 18 OR reply_subject = 'Company registered identity and location';
```

若 id=18 已被运营修改，先把有效修改合并到 V75；不得直接用计划基线覆盖。确认 V74 已存在且 Flyway 顺序为 74→75。

## 变更文件清单（2）

1. `V75__split_company_identity_from_agency_credentials.sql`
2. `QaMatchServiceTest.kt`

## 验收标准

- 精确公司身份问题的事实正文不含 `confidential`、`government`、`talent-office certificates`、`trust step by step`。
- trust 问题仍能返回 verification 内容。
- 不新增未审核地址/证件事实。
- 定向测试：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=QaMatchServiceTest test
```

## 人工验收清单

### A-1：只问公司身份
- 输入：`Please provide the full legal name and registered location of your company.`
- 预期：只匹配公司身份规则；AI 公司段不出现信任/政府合作段。

### A-2：只问信任证明
- 输入：`How can I verify that your agency is legitimate?`
- 预期：命中 Agency credentials；website/LinkedIn/证明说明仍可用。

### A-3：组合问题
- 输入同时包含 company identity 与 legitimacy。
- 预期：两类事实分开进入各自 request/intent，不相互替代。
