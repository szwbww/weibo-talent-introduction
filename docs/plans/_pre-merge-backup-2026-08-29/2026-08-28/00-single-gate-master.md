# 主计划：单一收口点 —— 研发类型成为唯一发信过滤维度

> 状态：待评审
> 前置：05A（`2026-08-25/05a-institution-type-collection.md`）、05A-2（同目录）均已上线
> 数据依据：`docs/plans/2026-08-25/institution-type-distribution-output-2026-08-29.txt`
> 拆分原因：全量改动面 30+ 文件、4 个子系统，超出 create-p 的硬上限（≤10 文件 / ≤2 子系统），
> 必须拆成可独立发布、独立验证的子计划。本文件只承载**跨子计划共享**的事实与不变量。

---

## 需求描述

**Observable outcome**

1. 发信目标的判定**只由「研发类型」一个维度收口**。运营在页面上勾了哪几类，就发给哪几类；
   代码中不存在任何第二个隐式门禁。
2. `lastPublicationYear` 缺失导致的误判被修复：约 4.1 万名专家补上真实的最后发表年份后重新分类。

**What must NOT change**

1. 非 INTRODUCTION 邮件（MATERIAL_REMINDER）的目标构建逻辑逐字不变。
2. 医学越界（`OUT_OF_SCOPE`）与临床/服务岗（`SERVICE_ONLY`）的判定规则不变
   ——本主计划不改 `ExpertClassificationService` 的任何打分或判定分支。
3. `institution`、`institutionType`、`operatorStatus`、标签、地区、邮箱服务商等既有筛选维度的
   语义与实现不变。
4. 分类策略版本号 `ExpertClassificationService.VERSION` 不变（仍为 `rnd-v2-2026`）。

**Out of scope**

- 调整 `PRODUCTION_THRESHOLD` / `RESEARCH_THRESHOLD`（50/50）或任何打分项分值。
- 把 `RECENT_PAPER_CUTOFF_YEAR = 2021` 改成滚动窗口（见「遗留」）。
- 打开 `OPENALEX_FETCH_WORKS_ENABLED`。
- 用 `institutionType` 参与分类打分（实测无区分度，见下方 F-3，已放弃）。
- 新增任何数据源（SBIR / PatentsView 仍 PARKED）。

---

## 跨子计划不变量

### Invariant M-1: 唯一收口点（本主计划的硬性要求）
- Rule: INTRODUCTION 发信目标的判定，**有且只有**「研发类型集合」一个过滤条件。
  任何基于 `expertClassification` 的其他判定（`sendable`、策略版本、类型硬编码名单）
  一律删除，不得以任何形式保留为隐式门禁。
- Applies to: 子计划 02、03、04 的全部改动。
- Violation consequence: 运营在页面上看到的口径与实际发送口径不一致——正是本次改造要消除的问题。
- 来源: original（需求方 2026-08-28 明确要求「最后收口的过滤点只有一个研发类型，不要加任何黑盒过滤」）

### Invariant M-2: 类型集合空集 = 拒绝保存，不是「不限」
- Rule: 收口点成为唯一门禁后，`expertTypes` 空集必须在**保存配置时被拒绝**，
  不得在运行时被解释成「不限」。
- Applies to: 子计划 03（`BatchSendTaskConfigService` 校验 + V109 存量迁移）。
- Violation consequence: 空集在删除硬门禁后等价于「发给所有人（含医学越界、纯服务）」，
  是本次改造唯一会造成线上事故的路径。
- 来源: original

### Invariant M-3: 分类规则零改动
- Rule: 本主计划的四份子计划都**不得**修改 `ExpertClassificationService` 的
  `classify()` 判定链、`productionScore()`、`researchScore()` 或任何阈值/词表常量。
  唯一允许的改动是删除 `ACCEPTED_CLASSIFICATION_VERSIONS`（子计划 03）。
- Applies to: 子计划 01/02/03/04。
- Violation consequence: 分类语义变化会让「重新分类前后的差异」不可归因，
  子计划 01 的验收（分类结果变化必须全部由 `lastPublicationYear` 补齐解释）失效。
- 来源: original

### Invariant M-4: 不动 `minusDays(30)`
- Rule: 禁止修改 `ExpertDiscoveryService.kt:800/871` 的 30 天常量，
  或把它参数化。补采一律走独立过滤器。
- Applies to: 子计划 01。
- 来源: K-（05A-2 的 I5a2-1，已在该计划中验证有效）

---

## 现状审计（共享部分）

### 发信硬门禁：四处，两种实现（2026-08-28 重新 grep 复核）

`grep -rn "classification.version\|expertSendableFilter" src/main/kotlin`

| # | 位置 | 形态 | 备注 |
|---|---|---|---|
| 1 | `ExpertSearchService.expertSendableFilter():55-63` | ES 谓词 | `bool.filter` = `term sendable==true` + `terms version ∈ ACCEPTED` |
| 1a | 调用点 `ManualInitialOutreachService.kt:1326` | ES | `buildEsFiltersForLevel` 末尾追加 |
| 1b | 调用点 `ExpertSearchService.kt:420` | ES | `searchSendableExpertsWithEmail` |
| 2 | `BatchExecutionModels.kt:71` | 内存 | `RecipientScope.matchesExpert`，MySQL 重试联系人 |
| 3 | `ManualInitialOutreachService.kt:609-610` | 内存 | 发送前最后门禁，记 `EXPERT_NOT_SENDABLE` |
| 4 | `InitialOutreachService.kt:44-45` | 内存 | 发送前最后门禁，计 `skipped` |

> 更正既有知识条目 [[K-sendable-gate-two-implementations]]：该条写「两处独立实现」，
> 实际是**四处**（内存侧有三份逐字相同的复刻，不是一份）。该条的行号
> （`:1324` / `:376` / `:1951`）也已随 05A-2 偏移。**本次必须回写更正该条目。**

### 类型筛选（已存在，来自 2026-08-25 子计划 02）

- 白名单唯一声明：`ExpertSearchService.ALLOWED_EXPERT_TYPES:115-116`
  = `ExpertType.values()` 六值 + 字面量 `"UNCLASSIFIED"`。
- ES 侧多值谓词：`expertTypesFilter():124-134`（空集合返回 `null`，调用方不得追加）；
  单值纯谓词 `expertTypePredicate():142-152`（`UNCLASSIFIED` = `must_not exists expertClassification.type`）。
- 追加点：`buildEsFiltersForLevel:1327`（在门禁之前一行）。
- 内存侧：`BatchExecutionModels.kt:83-91`，`expertTypes.isNotEmpty()` 才判定。
- 存储：`batch_send_task_config.expert_types_json TEXT NOT NULL`（V108），**当前全部为 `'[]'`**。
- 校验：`BatchSendTaskConfigService.kt:299-310`，只校验白名单成员与逗号，**不校验非空**。

### 分类对象与 ES 声明

- `ExpertClassification.kt:37` `sendable` 是**只读派生 getter**（`type in SENDABLE_TYPES`，`:41`），
  构造函数不接受它。
- `ExpertIndexWriterService.kt:352-363` `classificationNode` 是唯一序列化点，`:360` 写 `version`。
- 三份 mapping（`orcid_info_raw/candidate/application.json`）均把 `expertClassification`
  声明为顶层 object，含 `sendable: boolean`。三份 `dynamic:false`（[[K-es-dynamic-false]]）。

### 实测数据事实（2026-08-29 盘点 + 追加查询）

- **F-1** CANDIDATE 共 117,544 条；`institutionType` 覆盖 75,438（64.2%）。
- **F-2** `UNKNOWN` 全库 **47,835** 条（40.7%）；其中 **41,409（86.6%）缺 `lastPublicationYear`**。
- **F-3** `institutionType` 对生产/学术轴无区分度：company 的分类构成
  （UNKNOWN 36.6% / ACADEMIC 37.2% / OUT_OF_SCOPE 24.8%）与 education
  （39.5 / 34.2 / 25.6）统计上不可分。**据此放弃「institutionType 参与打分」方案。**
- **F-4** `PRODUCTION_RND` 全库仅 55 条（0.07%）；生产分 P90=20、P99=45，阈值 50。
- **F-5** `UNKNOWN` 的 `researchScore` 分布 P50=35 / P75=40 / P90=40 / P95=45 / P99=45
  ——**全体挤在阈值 50 之下 5~15 分**，不是证据稀薄，是缺输入。
- **F-6** `recentWorkTitles` 在全库 UNKNOWN 中只有 **27** 条（0.06%）
  ⇒ `researchScore` 的 +25 项实质从未发放（`fetch-works-enabled: false`，`application.yml:176`）。
- **F-7** `hIndex` 在 UNKNOWN 中 P50=4 / P90=15 ⇒ 九成以上拿不到 `≥20` 的 +20。

代入 `researchScore():136-170` 的账本，F-2/F-5/F-6/F-7 完全自洽：
`researchFields(15) + hIndex≥5(10) + worksCount≥5(10) = 35`（正好 P50），
补上 `lastPublicationYear ≥ 2021` 的 +35 即 70 → 越过阈值。

---

## 研究检查点

### CP-1: OpenAlex author 对象是否含 `counts_by_year` —— **已完成（2026-08-28）**

`GET https://api.openalex.org/authors/A5023888391?mailto=wuwei@qftechtalent.com` 实测返回的
顶层键包含 `counts_by_year`，元素形如：

```json
{"year":2008,"works_count":1,"oa_works_count":0,"cited_by_count":119}
{"year":2010,"works_count":4,"oa_works_count":3,"cited_by_count":909}
```

两条结论：

1. 该字段**已在现有响应中**，两条解析路径都不带 `select=`（[[K-openalex-author-full-object]]），
   因此**单专家路径（`:114-120`）与批量路径（`:206-250`）都是零额外 API 请求**
   ——这一措辞按 [[K-openalex-fetch-works-gated]] 的要求，已逐路径说明。
2. **样本中的数组是按年份升序**（2008 在前）。实现**必须取 `works_count > 0` 的最大 year**，
   不得取首元素——与 05A 的 I5a-2「取数组第一项」相反，不可照抄。

### CP-2: 生产环境 `MAIL_SCHEDULING_ENABLED` 与队列 publisher —— **已完成（2026-08-28），结论：三条链路均未运行**

`InitialOutreachService.sendInitialBatch` 有 3 个调用方：
`MailAutomationController.kt:65`（无前端调用方，`grep -n "initial-outreach" app.js` 零命中）、
`MailQueueConsumer.kt:32`（需存在 publisher bean）、
`MailAutomationScheduler.kt:68`（`@ConditionalOnProperty scheduling.enabled=true`，
默认 `false`；`initial-outreach-cron` 默认 `-`）。

**必须在服务器上执行**（本地判断不作数）：

```bash
ssh root@150.158.92.103 \
  'grep -E "MAIL_SCHEDULING_ENABLED|MAIL_SCHEDULING_INITIAL_OUTREACH_CRON|MAIL_QUEUE" \
   /opt/apache-tomcat-9.0.71/bin/setenv.sh || echo "(未设置 → 全部走默认值：关闭)"'
```

**2026-08-28 实测结果**（读 Tomcat 进程真实环境）：

```
MAIL_QUEUE_ENABLED=false
MAIL_SCHEDULING_ENABLED=true
MAIL_SCHEDULING_AUTO_REPLY_MAX_MESSAGES_PER_ACCOUNT=5
MAIL_SCHEDULING_AUTO_REPLY_ALL_CRON=0 */10 * * * *
```

逐条判读：

| 调用方 | 结论 | 依据 |
|---|---|---|
| 队列 `MailQueueConsumer` | **未创建 bean** | `MAIL_QUEUE_ENABLED=false`，`@ConditionalOnProperty(havingValue="true")` 不满足 |
| 定时 `MailAutomationScheduler` | bean **已创建**（自动回复每 10 分钟在跑） | `MAIL_SCHEDULING_ENABLED=true` |
| └ `scheduleInitialOutreach()` | **该方法未启用** | 输出中**无** `MAIL_SCHEDULING_INITIAL_OUTREACH_CRON` ⇒ 取 `application.yml:75` 默认值 `-`；Spring 的 `ScheduledTaskRegistrar.CRON_DISABLED = "-"` 表示禁用该任务 |
| HTTP `POST /api/mail-automation/initial-outreach` | 存在但无调用方 | 前端零命中；需带登录态手工 curl |

**经验证据收口（2026-08-28 实测）** —— 定时与队列两条路都经 `taskExecutionService.runAndRecord`
落 `task_execution` 表（`task_type='INITIAL_OUTREACH'`，`trigger_type` 分别为 `SCHEDULED` / `QUEUE`）：

```bash
ssh root@150.158.92.103 'mysql -h 127.0.0.1 -P 3306 -u root -proot talent_introduction   -e "SELECT trigger_type, COUNT(*) AS n, MAX(started_at) AS last_run       FROM task_execution WHERE task_type="INITIAL_OUTREACH" GROUP BY trigger_type;"'
```

**实测输出：只有 mysql 的密码告警，无任何结果行 ⇒ `INITIAL_OUTREACH` 从未产生过执行记录。**
「`-` = `ScheduledTaskRegistrar.CRON_DISABLED`」的推断由此得到经验支持，
两条自动链路确实没在跑。**02 直接发布，无需加环境变量。**

> 残留不确定性（不影响决策）：`task-retention` 若被开启（`application.yml:84` 默认 `false`，
> 本次未查该变量），保留窗口为 90 天，则"零行"最坏只能证明"近 90 天没跑过"。
> 即便如此，02 之后该链路的失败模式是**快速失败**而非误发，风险可接受。

> 该 SQL **覆盖不到** HTTP 接口那条路径：`MailAutomationController:65` 直接调
> `sendInitialBatch`，不经 `runAndRecord`，不落表。但它无前端调用方且需登录态，
> 02 之后即使有人手工调用也只会快速失败（不会误发），可接受。

子计划 02 已按**参数化**方案写定（不删链路，改为读显式配置的类型集合，未配置即快速失败），
因此 CP-2 的结论**不改变计划内容**，只决定部署步骤：

- ✅ **实测：三条链路都关闭** → 直接发布 02，无需加环境变量。
- （备用分支，本次未命中）任一开启 → 发布 02 **之前**先在 `setenv.sh` 加
  `export MAIL_SCHEDULING_INITIAL_OUTREACH_EXPERT_TYPES=PRODUCTION_RND,ACADEMIC_RND,HYBRID_RND`
  （与今天 `SENDABLE_TYPES` 等价，行为不变），否则该链路下次触发时会抛错。

> 为什么不删整条链路：`grep -rln "InitialOutreachService\|initialOutreach"` 命中 27 个文件，
> 真正的删除面（服务 + 3 个调用方 + 队列消息 + publisher + 属性 + yml×2 + 测试）约 16 个文件，
> 远超单计划上限。参数化只需 7 个文件，且同样消灭了隐式门禁。删除留作日后单独议题。

---

## 子计划与顺序

| 序 | 子计划 | 覆盖需求 | 文件数 | 依赖 | 状态 |
|---|---|---|---|---|---|
| 01 | [`01-lastpublicationyear-recovery.md`](./01-lastpublicationyear-recovery.md) | 补齐 `lastPublicationYear` + 补采口径 + 全量重新分类 | 8 | 无 | ✅ 已写 |
| 02 | [`02-legacy-outreach-explicit-types.md`](./02-legacy-outreach-explicit-types.md) | 旧首发链路改为读显式配置的类型集合，不再依赖 `sendable` | 7 | 无 | ✅ 已写 |
| 03 | [`03-expert-types-required.md`](./03-expert-types-required.md) | 研发类型必填非空（写侧）+ V109 存量迁移 + 前端默认三类 | 7 | 无 | ✅ 已写 |
| 04 | [`04-single-gate-remove-sendable.md`](./04-single-gate-remove-sendable.md) | 删除 `expertSendableFilter` / 内存门禁 / `ACCEPTED_VERSIONS`，类型成唯一收口点 | 8 | 02 + 03 | ✅ 已写 |
| 05 | [`05-sendable-vocabulary-cleanup.md`](./05-sendable-vocabulary-cleanup.md) | 删 `sendable` 派生属性 / `SENDABLE_TYPES` / 序列化 / API DTO；回填统计改按类型 | 10 | 04 | ✅ 已写 |

**发布顺序：01 ∥ 02 ∥ 03 → 04 → 05**（前三份互不依赖，可并行；04 是唯一的语义翻转点）

**顺序理由（每一条都对应一个会出事的场景）**

- **01 最先**：与门禁改造完全解耦，且带来的是净收益（约 1.5~2 万人从 UNKNOWN 转出）。
  越早跑完，04 上线后运营能勾到的人越多。
- **03 必须早于 04**：04 把「类型集合」变成唯一门禁，届时空集 = 发给零个人。
  若 04 先上线而存量配置仍是 `'[]'`，**所有定时任务当场静默停发**。
  03 先把配置填成显式三类（与今天的 `SENDABLE_TYPES` 等价），线上行为零变化。
- **02 必须早于 04**：04 要删 `expertSendableFilter()`，而旧首发链路是它的第二个调用点。
  02 先把该链路切到显式配置的类型集合上，04 删函数时 `searchSendableExpertsWithEmail`
  已零生产调用点，可以整个删掉。
- **05 必须晚于 04**：`sendable` 还有过滤用途时不能删。04 之后它只剩三处非过滤用途
  （序列化、回填统计、API DTO），05 一并清理。

**回滚点**

- 01、02、03、05 单独回滚均安全（无语义翻转）。
- **04 是唯一的语义翻转点**（空集从"不限"变成"谁都不发"），回滚必须回滚代码；
  V109 写入的数据无需回滚 —— 三类 = 老门禁口径，对老代码无害。
- 若 04 上线后发现停发，第一件事是查 `expert_types_json` 是否真的非空
  （即 03 的 V109 是否已应用），而不是回滚 04。

**M-1 的终局判据**：05 上线后，`ExpertClassificationVersionGateGuardTest` 的两个守卫用例
白名单**全部为空集** —— 全仓库既无版本比较，也无 `sendable` 读取。
在此之前，白名单收窄的过程本身就是进度条：04 收到四个文件，05 收到空集。

---

## 遗留与不做

- `RECENT_PAPER_CUTOFF_YEAR = 2021`（`ExpertClassificationService.kt:229`）是**写死的字面量**，
  不是「当前年份 − N」。随时间推移满足该条件的人只会越来越少，`UNKNOWN` 会单调膨胀。
  本次不改（会与 M-3 冲突，且需重新定标），但必须记录：**这是下一个最该处理的问题**。
- `researchScore` 的 +25（`recentWorkTitles`）在默认配置下恒为 0，实际满分是 75 不是 100。
  是否打开 `OPENALEX_FETCH_WORKS_ENABLED` 需单独评估——实测打开后几乎全员越过阈值，
  会让 `ACADEMIC_RND` 失去区分力。
- `batch_send_task_config.reachability_filter`（V103）仍是孤儿列，沿用 05A-2 的结论不删。
