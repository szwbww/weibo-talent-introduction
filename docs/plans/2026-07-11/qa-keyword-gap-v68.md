# 计划 1:QA 关键词缺口修复 + 合同/IP 新规则(V68 迁移)

> 日期:2026-07-11
> 背景:真实专家来信(概览型多问信,含公司注册地/职责/IP/下一步等 6 个 bullet 问题)经 `QaMatchService` 模拟仅命中 id=9(裸词 `process` 撞中)与 id=33(裸词 `provide` 误命中),id=24 总览、id=18 资质、id=5 职责、id=23 匹配全部漏配,gap 检测(6 bullet > 2 类目)转人工。
> 后续计划:`ai-training-real-reply-integration.md`(依赖本计划先行合并,使训练模拟能命中新关键词)。

## 需求描述

Observable outcomes:

1. 概览型多问来信(如含 "further information" / "purpose and structure")命中 id=24 总览规则,走 supersede 兜底,`gapDetected=false`,不再误转人工。
2. 问公司注册名/注册地的来信命中 id=18,且 id=18 回复正文包含公司全称「Jiangsu Qingfei Talent Technology Co., Ltd.(江苏清飞人才科技有限公司)」与注册地 Nanjing。
3. 问 responsibilities/deliverables(复数)命中 id=5;问 "within the scope" / 匹配方式命中 id=23;问 "next stages/steps" 命中 id=9。
4. 问合同/IP(intellectual property / contract terms 等)命中新增规则「Contract and IP arrangements」,自动回复口径为:入选后与企业签劳动合同、IP 条款在协议中约定、签约前可审阅。
5. "could you provide further information" 类来信不再误命中 id=33 轻问材料。

What must NOT change:

- 资金数字(3-12M RMB)只出现在 id=8 Funding support 一处(V65 结论)。
- id=33 对真实材料问询("what documents" / "what should i send")的命中能力。
- id=9 对 "application process" / "timeline" 的命中能力。
- 所有规则正文 ASCII-only 惯例(V44/V45/V57);中文字符一律走 `CONVERT(UNHEX(..) USING utf8mb4)`。
- 已应用迁移 V1..V67 一字不改。

Out of scope:

- AI 生成回复链路改动(计划 2)。
- 前端任何改动。
- QA 规则管理 UI。
- prompt-config constraints 配置(计划 2 验收步骤)。

## 关键不变量

### Invariant I-1: 迁移只新增
- Rule: 变更仅通过新文件 `V68__*.sql` 落库;不修改任何已存在迁移。
- Applies to: 本计划唯一写路径 V68 迁移。
- Violation consequence: Flyway checksum 校验失败,启动崩溃。
- 来源: original(CLAUDE.md 迁移纪律)

### Invariant I-2: sendable 关键词只加短语、不加裸词
- Rule: 本次新增关键词全部为 ≥2 词的短语;同时删除裸词 `provide`(id=33);id=9 保留 `application process`/`the process`/`procedure`/`timeline`,不保留裸词 `process`。
- Applies to: V68 中所有 `UPDATE qa_rule SET keywords`。
- Violation consequence: 裸词子串误命中(本次 id=33 的 `provide` 即实例),错拼段落进外发正文。
- 来源: K-overview-gap-supersede + V65 §7 碰撞教训

### Invariant I-3: 资金数字唯一出现点
- Rule: V68 不得在任何 reply_body 中引入具体资金数字;3-12M 数字仅存在于 id=8。新增合同/IP 规则正文不含金额。
- Applies to: V68 的 INSERT 新规则、id=18 正文追加。
- Violation consequence: 多处金额随规则组合重复/互相矛盾(V65 修过一轮)。
- 来源: K-composed-reply-order-contract 相邻结论 + V65 §1-3

### Invariant I-4: ASCII-only 迁移正文
- Rule: V68 文件字面量仅 ASCII;中文(公司中文全称、display_name)以 UTF-8 hex 经 `CONVERT(UNHEX('...') USING utf8mb4)` 写入。
- Applies to: id=18 正文追加、新规则 display_name。
- Violation consequence: 历史上 V44/V45 修复过的乱码问题复发。
- 来源: K-qa-replybody-outbound-sites 同域惯例(V44/V45/V57)

### Invariant I-5: 幂等防重
- Rule: 对 reply_body 的 CONCAT 追加必须带 `AND reply_body NOT LIKE` 防重条件;INSERT 新规则前置 `NOT EXISTS`(按 reply_subject)。
- Applies to: V68 全部语句。
- Violation consequence: 环境重放/手工执行时正文重复段落。
- 来源: original(V65 §5 同款写法)

## 现状审计

### MySQL `qa_rule` 表
- Schema: V1 建表 + V14(display_name)+ V40(section_title)+ V41(supersedes_children)。规则以 id 定位(V65 惯例,id=export 的 rule_id)。
- Write paths:
  1. Flyway 迁移 V3/V17/V18/V38/V41/V44/V45/V46/V52/V57/V63/V65 —— 种子与批量修订(本计划同类)。
  2. `QaRuleManagementService.kt:70/89/122` —— 运营 UI 运行时改 keywords/正文/enabled。**注意:V68 的 UPDATE 会覆盖运营运行时改动**,上线前需与运营确认目标规则近期无手工调整(验收 A-6)。
- Read paths:
  1. `QaMatchService.kt:19/51/58` `findAllEnabledOrdered()` —— 关键词匹配(normalize 后子串,ANY 模式任一命中;normalize 将 details→information)。
  2. `QaReplyComposer` —— 组装正文(compose_order:OVERVIEW=0,PROGRAM=10,ROLE=20,FUNDING=30,TRUST=40,PROCESS=50,COMM=60)。
  3. `AiReplyDraftService.buildMatchedUserContent()` —— QA_MATCHED prompt SEGMENT 来源。
- Interaction points:
  - 新增关键词 → `QaMatchService.matchRule` 子串匹配:短语须按 normalize 后形态设计(全小写、空白折叠;含 "details" 的短语写成 "information" 形态,如 `more details` 实际存储命中形态为 `more information`,V63 已示范)。
  - id=24 命中 → `applySupersede` 抑制子规则 + `detectGap` 直接返回 false —— 本计划 outcome 1 正是依赖此路径(K-overview-gap-supersede)。

### 当前关键词基线(grep 迁移链核实,非记忆)
| id | 规则 | 当前 keywords(最后修订) |
|---|---|---|
| 24 | Program overview | V52 全量 + V63 追加 more details 族 |
| 18 | Agency credentials | V65 §7d |
| 5 | Responsibilities and benefits | V65 §7b:`duty,my rights,responsibility,benefit,benefits,what will i get,what do i get` |
| 23 | Partner company information | V38 种子:`which company,partner company,company profile,is it a good match` |
| 9 | Application process | V3 种子:`process,procedure,application process,timeline` |
| 33 | Getting started materials | V57 §e:`what documents,materials needed,cv,what to send,provide,what do you need,send my documents,what should i send` |

## 实现方案

### 任务 T1:新建 `V68__qa_keyword_gap_and_contract_ip_rule.sql`(I-1..I-5)

按序包含以下语句(短语均为 normalize 后可命中形态):

1. **id=24 总览关键词追加**(I-2;outcome 1)
```sql
UPDATE qa_rule
   SET keywords = CONCAT(keywords, ',further information,purpose and structure,structure of the program,more about the program,know more about')
 WHERE id = 24 AND keywords NOT LIKE '%further information%';
```

2. **id=18 资质关键词追加 + 正文追加公司注册信息**(I-2/I-4/I-5;outcome 2)
```sql
UPDATE qa_rule
   SET keywords = CONCAT(keywords, ',registered location,registered address,company registration,name of your company,your company name,full name and registered,where is your company,where are you based')
 WHERE id = 18 AND keywords NOT LIKE '%registered location%';

UPDATE qa_rule
   SET reply_body = CONCAT(reply_body, '

Our full registered name is Jiangsu Qingfei Talent Technology Co., Ltd. (', CONVERT(UNHEX('E6B19FE88B8FE6B885E9A39EE4BABAE6898DE7A791E68A80E69C89E99990E585ACE58FB8') USING utf8mb4), '), registered in Nanjing, China.')
 WHERE id = 18 AND reply_body NOT LIKE '%Jiangsu Qingfei Talent Technology%';
```

3. **id=5 职责复数补齐**(I-2;outcome 3)
```sql
UPDATE qa_rule
   SET keywords = 'duty,my rights,responsibility,responsibilities,benefit,benefits,what will i get,what do i get,deliverables,my duties,expected responsibilities'
 WHERE id = 5;
```

4. **id=23 匹配/对口问法**(I-2;outcome 3)
```sql
UPDATE qa_rule
   SET keywords = CONCAT(keywords, ',within the scope,selected and matched,how do you match,matching process,enterprise projects')
 WHERE id = 23 AND keywords NOT LIKE '%within the scope%';
```

5. **id=9 精确短语补齐 + 收窄裸词 process**(I-2;outcome 3 + must-NOT-change 3)
```sql
UPDATE qa_rule
   SET keywords = 'application process,the process,procedure,timeline,next stages,next steps,what happens next,stages of the application,selection process,how are researchers selected'
 WHERE id = 9;
```

6. **id=33 去掉裸词 provide**(I-2;outcome 5 + must-NOT-change 2)
```sql
UPDATE qa_rule
   SET keywords = 'what documents,materials needed,cv,what to send,what do you need,send my documents,what should i send,provide my cv,what should i provide'
 WHERE id = 33;
```

7. **新增合同/IP 规则**(I-2/I-3/I-4/I-5;outcome 4)
```sql
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    display_name, section_title, auto_reply_enabled, handoff_required, enabled, supersedes_children
)
SELECT (SELECT id FROM qa_category WHERE category_code = 'FUNDING_AND_TIMELINE'),
       'intellectual property,ip rights,ip arrangements,contractual,contract terms,patent ownership,who owns the',
       'ANY', 120, 'Contract and IP arrangements',
       'After selection, you will sign a labor contract directly with the matched enterprise; intellectual-property and compensation terms are set out in that agreement, and you may review the full terms before making any commitment.

Until then, nothing you share with us transfers any rights -- your materials are used only for enterprise matching and application preparation.',
       CONVERT(UNHEX('E59088E5908CE4B88EE79FA5E8AF86E4BAA7E69D83') USING utf8mb4),
       'Funding & timeline', 1, 0, 1, 0
 WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Contract and IP arrangements');
```

### 任务 T2:`QaMatchServiceTest.kt` 增补样例断言(I-2)

用本次真实专家来信全文作 fixture(概览多问信),在内存规则集(按 V68 后关键词构造)上断言:

- `suggestComposition` 命中集含 id=24(supersede 生效,`gapDetected=false`)。
- id=33 不在 rawMatches(`provide further information` 不再命中)。
- 单句 fixture:"what are the intellectual property arrangements?" 命中新规则;"the next stages of the application" 命中 id=9;"registered location of your company" 命中 id=18;"expected responsibilities and deliverables" 命中 id=5。
- 回归:"what should i send you?" 仍命中 id=33;"what is the application process?" 仍命中 id=9。

## 变更文件清单

| # | 文件 | 操作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V68__qa_keyword_gap_and_contract_ip_rule.sql` | 新增 |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt` | 修改(增测试) |

## 验收标准

- I-1: `git diff --name-only` 仅含上表 2 文件;V1..V67 无 diff。
- I-2: grep V68 中所有新增关键词,断言无新增单词裸词(逐条人审关键词列表);T2 测试通过(`mvn test -Dtest=QaMatchServiceTest`)。
- I-3: grep V68 无 `million|RMB|3-12|12,000,000` 字样。
- I-4: `LC_ALL=C grep -n '[^\x00-\x7F]' V68__*.sql` 返回空(纯 ASCII)。
- I-5: V68 对本地库连续执行两次(第二次手工重放语句),qa_rule 行数与 id=18 正文长度不变。

## 人工验收清单

### A-1: 概览多问信不再转人工
- 前置条件: 本地起服务(Flyway 已跑 V68);找一封含 "provide further information regarding" + 多个 bullet 问题的测试来信(可用本计划背景中的真实专家信)。
- 操作步骤: ① 打开未匹配工作台对应来信详情(或 QA 预览接口)贴入正文;② 查看建议命中与 gap 状态。
- 预期结果: 建议命中含「项目总览」(id=24)且仅总览一条(supersede);界面无「缺口/转人工」提示;自动回复预览正文以 "Two tracks:" 开头。
- 覆盖: outcome 1 / I-2

### A-2: 公司注册信息进入资质回复
- 前置条件: 同 A-1。
- 操作步骤: 贴入单句来信 "Could you tell me the full name and registered location of your company?" 查看命中与预览。
- 预期结果: 命中「代理资质·政府合作证明」(id=18);预览正文末段包含 "Jiangsu Qingfei Talent Technology Co., Ltd. (江苏清飞人才科技有限公司), registered in Nanjing, China."(中文不乱码)。
- 覆盖: outcome 2 / I-4

### A-3: 合同/IP 新规则生效
- 前置条件: 同 A-1。
- 操作步骤: 贴入 "What are the contractual and intellectual-property arrangements?" 查看命中与预览。
- 预期结果: 命中「合同与知识产权」;预览正文含 "sign a labor contract directly with the matched enterprise";正文不含任何金额数字。
- 覆盖: outcome 4 / I-3

### A-4: 误命中回归(provide)
- 前置条件: 同 A-1。
- 操作步骤: 贴入 "Could you provide further information about the program?" 查看命中列表。
- 预期结果: 命中列表不含「轻问材料」(id=33);含「项目总览」(id=24)。
- 覆盖: outcome 5 / must-NOT-change 2

### A-5: 既有命中回归
- 前置条件: 同 A-1。
- 操作步骤: 依次贴入 "What documents do you need from me?" 与 "What is the application process and timeline?" 查看命中。
- 预期结果: 前者命中「轻问材料」(id=33),后者命中「申报流程」(id=9)。
- 覆盖: must-NOT-change 2/3

### A-6: 运营改动核对(上线前)
- 前置条件: 生产库读权限。
- 操作步骤: 导出线上 qa_rule 中 id∈{5,9,18,23,24,33} 的 keywords 与 updated 时间,与本计划「当前关键词基线」逐条比对。
- 预期结果: 与基线一致;若运营已手工改过,先把差异并入 V68 再上线。
- 覆盖: 现状审计写路径 2 的交互点

## 修正记录

(执行/复验期间的决策在此追加)
