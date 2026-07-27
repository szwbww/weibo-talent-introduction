# 学科筛选与批量发送过滤配置(Plan B)

> 系列计划 2/2。**依赖 Plan A(`discipline-category-data.md`)已执行并完成存量回填**;本计划不写 ES 数据,只消费 `disciplineCategory` 字段。

## 需求描述

**Observable outcome:**
1. 专家列表(漏斗视图)新增"学科分类"筛选下拉:全部 / 理工科(STEM)/ 文社科(HUMANITIES)/ 未分类,与既有地区、邮箱服务商筛选并列生效,可组合。
2. 批量发送配置面板新增"学科分类"下拉(全部 / 仅理工科 / 仅文社科),保存进 `batch_send_setting`,重启/重开面板后保留;待发送数(pending-count)与实际批量发送范围同步受该配置约束。
3. "按当前筛选批量发送"收集联系人时,学科筛选条件同样生效。

**What must NOT change:**
- 既有筛选(tag / operatorStatus / emailDomain / region / hIndexMin / citationCountMin / recentYears / hasField)的行为与组合语义。
- 批量发送引擎的轮次/限额/预热/自检逻辑(`runScheduledBatch` 主循环零逻辑改动,仅过滤条件来源变化)。
- 旧自动外联路径 `InitialOutreachService.sendInitialBatch()`:它从不读 `BatchSendConfig`(emailDomain 同样不作用于它),本计划保持该现状,不得顺手接入。
- `batch_send_setting` 既有 key 的读写。

**Out of scope(显式推迟):**
- 专家列表增加"学科"展示列或徽章(字段已在响应中,展示另行迭代)。
- 学科数量聚合接口(选项为封闭枚举,无需聚合;学术数值筛选 hIndexMin 等同样不做聚合,循此先例)。
- 批量发送配置支持"仅未分类"(发送目标应为已确认人群;运营可用列表筛选查看未分类)。
- tag 选项聚合接口(`aggregateTags`)接入 discipline 约束(既有 hIndexMin 等学术筛选也未接入,循此先例;记录为观察项)。

## 关键不变量

### Invariant I-1: 筛选语义三态统一构造
- Rule: `discipline` 参数语义:`"STEM"` / `"HUMANITIES"` → `term { disciplineCategory: <值> }`;`"UNCLASSIFIED"` → `bool { must_not [exists disciplineCategory] }`;空/缺省 → 不加过滤。该构造逻辑只实现一份(`ExpertSearchService` 私有函数 `disciplineFilter(discipline)`),`buildExpertFilters` 与 `notContactedWithEmailFilters` 共用,禁止在调用方复制 ES DSL。
- Applies to: `ExpertSearchService.buildExpertFilters()`、`ExpertSearchService.notContactedWithEmailFilters()`。
- Violation consequence: 列表所见与批量发送实际目标语义漂移。
- 来源: original(UNCLASSIFIED 语义依赖 Plan A I-1"双值或缺失")

### Invariant I-2: 待发送数与发送范围同源
- Rule: `countPending()` 与 `runScheduledBatch()` 必须使用同一份 `getConfig().discipline` 经同一个 `notContactedWithEmailFilters(emailDomain, discipline)` 构造过滤;不允许一处带 discipline 一处不带。
- Applies to: `ManualInitialOutreachService.countPending()` L94-99、`runScheduledBatch()` L134-141。
- Violation consequence: UI 显示待发送 N 人、实际发送 M 人,运营无法核对。
- 来源: original

### Invariant I-3: KV 配置无迁移 + 读取侧白名单
- Rule: 新配置项走 `batch_send_setting` KV 表(`setting_key` UNIQUE),只加 KEY 常量 + 默认值 + upsert,**不写 Flyway 迁移**。读取侧对缺失/非法值回退默认 `""`;`validate()` 白名单校验 `discipline ∈ {"", "STEM", "HUMANITIES"}`(批量发送不允许 UNCLASSIFIED,见 out of scope)。
- Applies to: `BatchSendSettingService`(唯一读写点)。
- Violation consequence: 无 NOT NULL 保护的 KV 表读到脏值直接进 ES DSL,导致查询报错或静默全空。
- 来源: K-batch-send-setting-kv

### Invariant I-4: 批量收集与列表查询参数集一致
- Rule: "按当前筛选批量发送"的 `collectBatchMailContactIds()` 构造 `/api/experts` 参数时必须包含 discipline(与 `loadContacts` 一致);任何一侧新增筛选参数,另一侧必须同步。
- Applies to: `app.js` `collectBatchMailContactIds()`(L3511 起)、`loadContacts()` 参数构造(L3780 起)。
- Violation consequence: UI 命中 N 人、批量实际发给筛选前的集合,静默错发(P1 前科,见知识条目)。
- 来源: K-bulk-actions-must-cover-full-filter-set

### Invariant I-5: 旧自动外联路径隔离
- Rule: `InitialOutreachService.sendInitialBatch()` 及 `MailAutomationScheduler` 不接入 discipline 配置,保持零改动(与 emailDomain 的既有精度一致)。
- Applies to: `InitialOutreachService.kt`(禁改)。
- Violation consequence: 双发送路径行为分叉引入回归面,超出本计划验证范围。
- 来源: K-dual-outreach-paths

### Invariant I-6: 前端筛选注册点全集
- Rule: 新筛选控件必须同步注册 app.js 的全部消费点:① `loadContacts` 参数构造 ② `collectBatchMailContactIds` 参数构造 ③ 筛选摘要文案函数(L3495 起 `parts.push` 系列)④ `updateFilterBadge` 活跃计数(L10061 起)⑤ change 监听注册数组(L10086-10090)。缺任一点表现为:筛选不生效 / 批量漏筛 / 徽章数不亮 / 改选不刷新。
- Applies to: `app.js` 上述五处。
- Violation consequence: 见各点;均为隐蔽性缺陷。
- 来源: original(模式同 K-mail-body-display-sites:分散消费点须按全集逐点改)

## 样式契约

> 原则:本计划**零新增 CSS**,全部复用既有 class;禁止 inline style(既有 number input 的 `style="width: 70px"` 先例不援引);禁止未在本契约声明的新 class。

### S-1: 专家列表"学科分类"筛选下拉
- 复用:`<label class="toolbar-label">`(styles.css:353 — font-size 11px、uppercase、gap 6px、color var(--text-muted));内部 `<select>` 无 class,与 `#expertRegionFilter` 等兄弟控件完全一致。
- 新增:无。
- DOM 结构(插入 `index.html` `#contactsFilterGroup` 内,紧跟"地区"筛选 label 之后、"H-Index ≥"之前):

```html
<label class="toolbar-label">
    学科分类:
    <select id="expertDisciplineFilter">
        <option value="">全部学科</option>
        <option value="STEM">理工科</option>
        <option value="HUMANITIES">文社科</option>
        <option value="UNCLASSIFIED">未分类</option>
    </select>
</label>
```

- 禁止项:inline style;新 class;修改 `.toolbar-label` 既有规则块。

### S-2: 批量发送配置面板"学科分类"下拉
- 复用:`.bsc-field`(styles.css:4851)、`.bsc-field-label`(4856)、`.bsc-input`(4861)、`.bsc-select`(4872)、既有 `.bsc-row-2col`(4902)。
- 新增:无。
- DOM 结构(放入 `index.html` L1057 起"节流控制" fieldset 内既有的 `.bsc-row-2col`,作为"邮箱服务商"字段的第二列——该行当前只有一个 `.bsc-field`,天然留有第二格):

```html
<div class="bsc-field">
    <span class="bsc-field-label">学科分类</span>
    <select id="batchSendDiscipline" class="bsc-input bsc-select">
        <option value="">全部</option>
        <option value="STEM">仅理工科</option>
        <option value="HUMANITIES">仅文社科</option>
    </select>
</div>
```

- 既有 class 使用点说明:`.bsc-*` 系列被批量发送配置面板多个字段共用,本计划**只新增使用点、不修改规则块**(无就地修改)。
- 禁止项:同 S-1。

## 现状审计

### `/api/experts` 查询链(CANDIDATE/RAW/APPLICATION ES 索引,只读)
- `ExpertIndexController.listExperts` L48-66:`@RequestParam` 平铺(tag/operatorStatus/emailDomain/region/hIndexMin/citationCountMin/recentYears/hasField)→ `ExpertSearchService.searchExperts()`。
- `ExpertSearchService.buildExpertFilters()` L708-755:所有筛选的唯一 DSL 组装点;`searchExperts` / `aggregateTags` / `aggregateRegions` / `aggregateEmailDomains` 共用(后三者签名不含学术数值筛选——本计划 discipline 同样不进聚合,循此先例)。
- `notContactedWithEmailFilters(emailDomain)` L26-40(companion):批量发送目标过滤器;`buildExpertFilters` 的 `NOT_CONTACTED` 分支(L727)也调用它 → **列表按"未联系"筛选与批量发送天然同源,discipline 加入后此分支不额外传值(列表侧 discipline 由独立 filter 追加,避免双重过滤)**。
- 调用方(grep 全量):`ManualInitialOutreachService` L98、L136;`ExpertSearchService` L727。

### 批量发送配置链(`batch_send_setting` KV 表)
- 写路径:`BatchSendSettingService.upsert()` — 唯一写入点(来源: K-batch-send-setting-kv)。
- 读路径:`loadAll()` → `getConfig()` L19-32;消费方 `ManualInitialOutreachService.countPending()` L94 / `runScheduledBatch()` L134、`BatchSendScheduler`、`BatchSendControlService`(后两者只读 cron/autoEnabled 等,不触碰过滤字段)。
- API:`BatchSendConfigController` `GET/PUT /api/mail/batch-send/config`,DTO 为 `BatchSendConfig` / `BatchSendConfigUpdateRequest`(与 Service 同文件)——新字段带默认值 `""`,Controller 文件零改动。
- `validate()` L72-74:数值校验所在地,白名单校验加这里(I-3)。
- Interaction points:PUT 保存 discipline → `countPending`(pending-count 显示)与 `runScheduledBatch`(实际发送)消费(I-2;A-3/A-4 验证)。

### 前端(`static/index.html` / `static/app.js`)
- 专家筛选条:`index.html` L454-556 `#contactsFilterGroup`,每个筛选一个 `<label class="toolbar-label">`;地区筛选 select 为 `#expertRegionFilter`(L521-526)。
- app.js 筛选消费点(grep `expertRegionFilter` 全量,discipline 需逐点对齐):
  1. L3780 起 `loadContacts` 参数构造(`if (region) params.set(...)` 系列);
  2. L3511 起 `collectBatchMailContactIds`(批量群发按筛选收集,同样的 params 构造);
  3. L3495 起筛选摘要文案(`parts.push(\`地区: ${region}\`)` 系列);
  4. L10061 起 `updateFilterBadge` 活跃筛选计数数组;
  5. L10086-10090 change 监听 id 数组(触发 `reloadContactsFromStart`);
  6. L3443-3448 `loadExpertTagOptions` filters(只传 operatorStatus/emailDomain/region 给 tag 聚合——**不加 discipline**,循学术筛选先例,见 out of scope)。
- 批量发送配置面板:`index.html` L1035 起,"邮箱服务商"字段在 `.bsc-row-2col`(L1062-1069);app.js `readBatchSendConfigForm()`(L5040 区域,`emailDomain: val("batchSendEmailDomain") || ""`)与 `fillBatchSendConfigForm()`(setVal 系列)为表单读写对;保存走 `saveBatchSendConfig()` L5084 → PUT → `refreshOutreachPendingCount()`(保存后待发送数自动刷新,I-2 的 UI 闭环已存在)。

### 前端样式盘点
- 可复用 class:`.toolbar-label` — styles.css:353(11px/500/uppercase/gap 6px/var(--text-muted));`.bsc-field` — 4851(flex column gap 4px);`.bsc-field-label` — 4856(11px/600/var(--text-muted));`.bsc-input` — 4861(padding 7px 10px、border var(--panel-border)、radius var(--radius-sm)、13px);`.bsc-select` — 4872(自绘箭头、padding-right 28px);`.bsc-row-2col` — 4902(grid 1fr 1fr gap 10px)。
- 设计基准 token:文字弱色 `var(--text-muted)`;面板边框 `var(--panel-border)`;焦点态 `.bsc-input:focus`(4880)border var(--primary) + 3px rgba 光晕 — select 复用自动获得。
- DOM 结构约定:筛选控件 = `label.toolbar-label > 文案 + select(无class)`;批量面板字段 = `div.bsc-field > span.bsc-field-label + 控件.bsc-input`。
- 改动前基线:`index.html` L1062-1069 当前为单字段 `.bsc-row-2col`(仅"邮箱服务商"),见 S-2;`#contactsFilterGroup` 地区 label 见 S-1 插入点描述。

## 实现方案

### Task 1: 后端筛选参数(遵守 I-1)
文件:`expert/service/ExpertSearchService.kt`、`expert/controller/ExpertIndexController.kt`
- `ExpertSearchService` 新增私有 `disciplineFilter(discipline: String): Map<String, Any>`,实现 I-1 三态;非法值 `require` 拒绝(白名单 STEM/HUMANITIES/UNCLASSIFIED)。
- `buildExpertFilters` 增加 `discipline: String? = null` 参数,非空时 `filters.add(disciplineFilter(it))`;`searchExperts` 签名透传。`aggregateTags/aggregateRegions/aggregateEmailDomains` 调用 `buildExpertFilters` 处按命名参数补默认值,行为不变。
- `notContactedWithEmailFilters(emailDomain: String? = null, discipline: String? = null)`:非空时追加 `disciplineFilter`。L727 `NOT_CONTACTED` 分支调用保持单参(discipline 由列表侧独立追加,不双重过滤)。
- `ExpertIndexController.listExperts` 增加 `@RequestParam(required = false) discipline: String?` 透传。

### Task 2: 批量发送配置(遵守 I-2、I-3、I-5)
文件:`campaign/service/BatchSendSettingService.kt`、`campaign/service/ManualInitialOutreachService.kt`
- `BatchSendSettingService`:`KEY_DISCIPLINE = "batchSend.discipline"`、`DEFAULT_DISCIPLINE = ""`;`getConfig()` 读取(缺失回退默认);`updateConfig()` upsert;`validate()` 白名单 `{"", "STEM", "HUMANITIES"}`;`BatchSendConfig` / `BatchSendConfigUpdateRequest` 各加 `val discipline: String = ""`。
- `ManualInitialOutreachService`:L94-98 与 L134-136 两处改为 `notContactedWithEmailFilters(config.emailDomain.ifBlank { null }, config.discipline.ifBlank { null })`(countPending 处同样先取整份 config,替代现在只取 emailDomain 的写法)。
- `InitialOutreachService.kt` 零改动(I-5)。

### Task 3: 前端筛选下拉(遵守 I-4、I-6、S-1)
文件:`static/index.html`、`static/app.js`
- index.html 按 S-1 插入 `#expertDisciplineFilter`。
- app.js 五个注册点(I-6):① `loadContacts` 读值 + `if (discipline) params.set("discipline", discipline)`;② `collectBatchMailContactIds` 同款读值+传参(I-4);③ 摘要文案 `parts.push(\`学科: ${label}\`)`(label 映射 STEM→理工科 / HUMANITIES→文社科 / UNCLASSIFIED→未分类);④ `updateFilterBadge` 数组加 `$("#expertDisciplineFilter")?.value !== ""`;⑤ change 监听数组加 `"expertDisciplineFilter"`。
- `useDbContactPath`(人工干预/回复模式走 DB 路径)时:与 region/emailDomain 现状一致——ES 参数不适用于 DB 路径的部分不传;discipline 循 region 在该路径下的既有处理方式,不做额外禁用逻辑。

### Task 4: 前端批量配置表单(遵守 I-2 UI 闭环、S-2)
文件:`static/index.html`、`static/app.js`(与 Task 3 同文件,一并执行)
- index.html 按 S-2 在 `.bsc-row-2col` 补第二列 `#batchSendDiscipline`。
- app.js:`readBatchSendConfigForm()` payload 加 `discipline: val("batchSendDiscipline") || ""`;`fillBatchSendConfigForm()` 加 `setVal("batchSendDiscipline", config.discipline ?? "")`。保存后 `refreshOutreachPendingCount()` 已有,无需新增。

## 变更文件清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `src/main/kotlin/.../expert/service/ExpertSearchService.kt` | disciplineFilter + buildExpertFilters/searchExperts/notContactedWithEmailFilters 参数 |
| 2 | `src/main/kotlin/.../expert/controller/ExpertIndexController.kt` | listExperts 加 discipline 参数 |
| 3 | `src/main/kotlin/.../campaign/service/BatchSendSettingService.kt` | KEY/默认值/validate/DTO 字段 |
| 4 | `src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt` | 两处过滤调用传 discipline |
| 5 | `src/main/resources/static/index.html` | S-1、S-2 两处 DOM |
| 6 | `src/main/resources/static/app.js` | I-6 五点 + 表单读写对 |

共 6 文件,2 子系统(后端筛选/配置链 / 前端)。新增共享存储字段:0(ES 无新字段;KV 表新 key 不算 schema 字段)。styles.css 零改动。

## 验收标准

- I-1: 单测断言 `disciplineFilter("STEM")` == term DSL、`disciplineFilter("UNCLASSIFIED")` == must_not exists DSL、非法值抛 `IllegalArgumentException`;grep 确认 `disciplineCategory` 的 ES DSL 仅出现在 `disciplineFilter` 一处。
- I-2: grep `notContactedWithEmailFilters(` 调用点(L98/L136 对应处)均传 `config.discipline`;无一处只传 emailDomain。
- I-3: diff 确认无新增 Flyway 迁移文件;`validate()` 含 discipline 白名单断言;单测:`getConfig()` 在 key 缺失时返回 `""`。
- I-4: grep `app.js` 中 `params.set("discipline"` 出现于 `loadContacts` 与 `collectBatchMailContactIds` 两个函数体内。
- I-5: diff 断言 `InitialOutreachService.kt` 与 `MailAutomationScheduler.kt` 零改动。
- I-6: grep `expertDisciplineFilter` 在 app.js 命中 ≥5 处且分布于审计列出的五个函数/数组。
- S-1/S-2: diff 断言落地 HTML 与契约代码块逐字一致(id、class、option 值序);grep 断言本次 diff 无 `style=` 新增、无 styles.css 改动、无契约外新 class。
- 集成:`mvn test` 全绿。

## 人工验收清单

### A-1: 列表按理工科筛选
- 前置条件: Plan A 回填完成;CANDIDATE 层同时存在 `disciplineCategory=STEM`、`=HUMANITIES`、字段缺失的专家各 ≥1(可用 ES `_update` 构造)。
- 操作步骤: ① 打开专家漏斗视图 → 展开筛选;② "学科分类"选"理工科"。
- 预期结果: 列表仅剩 STEM 专家;总数与 ES `_count`(term disciplineCategory=STEM + 该层)一致;筛选徽章计数 +1;摘要文案含"学科: 理工科"。
- 覆盖: 需求 1、I-1、I-6③④

### A-2: 未分类筛选与组合筛选
- 前置条件: 同 A-1;另使某 HUMANITIES 专家邮箱为 gmail.com。
- 操作步骤: ① 学科选"未分类" → 观察列表;② 学科选"文社科" + 邮箱服务商选 gmail.com。
- 预期结果: ①仅显示无 `disciplineCategory` 字段的专家;②仅显示文社科且 @gmail.com 的专家(组合 AND 生效)。
- 覆盖: 需求 1、I-1(UNCLASSIFIED 态)、must-NOT-change(既有筛选组合)

### A-3: 批量发送配置保存与待发送数联动
- 前置条件: CANDIDATE 层未联系(无 operatorStatus)且有邮箱的专家中,STEM x 人、非 STEM y 人(x、y ≥1,记录实际值)。
- 操作步骤: ① 打开批量发送配置面板,记录当前待发送数(应为 x+y+重试数);② "学科分类"选"仅理工科" → 保存配置;③ 观察待发送数;④ 关闭面板重新打开 / 重启应用后再看配置。
- 预期结果: ②保存 toast "配置已保存";③待发送数变为 x+重试数;④下拉仍为"仅理工科"(KV 持久化)。
- 覆盖: 需求 2、I-2、I-3、交互点(PUT→countPending)

### A-4: 批量发送实际范围受配置约束
- 前置条件: 同 A-3,配置"仅理工科";发送账号用模拟器账号(SIMULATOR)避免真实外发。
- 操作步骤: ① 手动单轮发送;② 任务完成后查 `mail_record` 本轮 OUTBOUND INTRODUCTION 对应的专家 ORCID;③ 逐一核对这些 ORCID 的 ES 文档。
- 预期结果: 本轮发送的专家全部 `disciplineCategory=STEM`;无 HUMANITIES/未分类专家收信。
- 覆盖: 需求 2、I-1、I-2、交互点(config→runScheduledBatch)

### A-5: 按当前筛选批量发送覆盖学科条件
- 前置条件: 列表学科筛选"文社科",命中数 >1 页(构造 ≥2 条)。
- 操作步骤: ① 按 A-1 方式筛出文社科;② 触发"按当前筛选批量发送"(模拟器账号);③ 核对实际收集/发送的联系人。
- 预期结果: 收集数 = 筛选命中总数(非当前页数);全部为 HUMANITIES 专家。
- 覆盖: 需求 3、I-4

### A-6: 旧自动外联路径回归
- 前置条件: 配置"仅理工科"已保存;`talent-introduction.scheduling.enabled` 环境按现状。
- 操作步骤: 代码走查 + 触发一次 `InitialOutreachService.sendInitialBatch()`(测试环境)。
- 预期结果: 该路径行为与改动前一致(不因 discipline 配置改变目标集);`InitialOutreachService.kt` 无 diff。
- 覆盖: must-NOT-change、I-5

### A-7: UI 目测(样式契约)
- 前置条件: 部署改动后的前端。
- 操作步骤: ① 对比"学科分类"筛选与相邻"地区"筛选:label 字号(11px)、大写转换、间距、下拉外观;② 对比批量面板"学科分类"与"邮箱服务商"字段:label 样式、控件边框/圆角/自绘箭头、焦点光晕;③ 检查两行布局(`.bsc-row-2col` 两列并排)。
- 预期结果: 逐项与相邻既有控件无肉眼差异;DevTools 检查两个新控件均无 inline style、class 与契约一致。
- 覆盖: S-1、S-2

### A-8: 既有筛选回归
- 前置条件: 学科筛选保持"全部学科"。
- 操作步骤: 逐个使用 tag、跟进状态、邮箱服务商、地区、H-Index≥、近 N 年发表筛选各做一次查询。
- 预期结果: 各筛选结果与改动前一致(抽样与 ES 手工查询核对);待发送数在 discipline="" 时与改动前口径一致。
- 覆盖: must-NOT-change(既有筛选)

## 修正记录

| 日期 | 修正 | 原因 |
|---|---|---|
| 2026-07-11 | I-2 的“发送范围”与 A-3/A-4 的“重试数”口径修正为：`discipline` 同时约束 ES 新目标与 `NEW` 状态、未成功发送的重试联系人；`countPending()` 与 `runScheduledBatch()` 必须复用同一份已过滤重试目标。 | 原计划只规定 `notContactedWithEmailFilters()` 接入 discipline，而重试联系人由 `buildRetryableTargets()` 独立读取，导致配置“仅理工科”时仍可能重发非 STEM 联系人。 |
