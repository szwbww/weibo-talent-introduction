# 发件账号硬退率告警（BOUNCE_RATE_HIGH）处置手册

> 首个真实触发实例：`LiLei` 于 `2026-09-03 09:40:28`，reason `BOUNCE_RATE_HIGH:6.25%`
> 域：`BounceRateMonitorService` / `MailSenderAccountService` / `BounceCollectionService` / `MailMonitoringService`
> 相关记忆：`bounce-hard-misclassified-as-soft`、`bounce-autopause-first-real-trigger`

**本手册 = 只读核对 + 名单侧人工止血；告警不暂停账号。不含任何代码改动、不改配置、不改阈值。**

> **执行边界（越界即停止）**
> - 不得修改源码，尤其**不得**为了让账号能发而调高 `BounceRateMonitorService.DEFAULT_THRESHOLD`
>   或调低 `MIN_SAMPLE_SIZE`。要改判据走计划流程。
> - 不得改 `application.yml` 或环境变量（含 `EMAIL_ENABLE_MX_CHECK`）。
> - **硬退率告警不暂停账号**；不要为告警调用 "恢复发送"（该按钮只服务其他真实暂停原因）。
> - 不得为了"让这批发完"把同一批地址切给别的发件账号（见 5.2，这是当前系统的默认行为）。
> - 任何一步出现「停止条件」，立即停止并把已有输出交回。

---

## 0. 这个告警意味着什么（先读，30 秒）

| 项 | 值 | 位置 |
|---|---|---|
| 判据 | 7 天窗口内 `硬退信数 / 已发信数 > 5%` | `BounceRateMonitorService.kt` |
| 最小样本 | 已发 < 20 封直接返回 `-1.0`，不判 | `MIN_SAMPLE_SIZE` |
| 检查时机 | 每次该账号 IMAP 轮询结束后 | `AutoMailReplyService:773`（**唯一调用点**） |
| 解除 | **无需解除**；V117 会清除历史 `BOUNCE_RATE_HIGH` 暂停，新告警不写暂停状态 | `V117__convert_bounce_rate_pause_to_warning.sql` |

**关键背景**：`b4b87cb fix(mail): DSN parse + EMAIL_INVALID writeback`（2026-08-18）之前，
硬退被系统性记成 SOFT，而判据只数 HARD —— **这条检查此前从未真正生效过**。
所以第一次看到它触发是正常的，*不*代表发信质量突然变差。
旧的历史退信率（如 1.94%）是坏分类算出来的，**不能拿来做对比基线**。

---

## 0.1 变量与登录

```bash
export BASE_URL='http://<APP_HOST>/talent/api'    # context path 是 /talent，漏掉会拿到 Tomcat 404
export COOKIE_JAR='/tmp/bounce_cookies.txt'       # 仅存 /tmp，结束后删除
export ACCT='<accountCode>'                       # 触发告警的账号，例：LiLei
```

> 不要把真实密码写进任何文件或提交记录；只在本会话 `export`。

```bash
rm -f "$COOKIE_JAR"; umask 077
curl -sS --max-time 15 -c "$COOKIE_JAR" -H 'Content-Type: application/json' \
  -d '{"username":"<ADMIN_USERNAME>","password":"<ADMIN_PASSWORD>"}' \
  "$BASE_URL/auth/login"; echo
chmod 600 "$COOKIE_JAR"
curl -sS --max-time 15 -b "$COOKIE_JAR" "$BASE_URL/auth/me"; echo
```

---

## 1. 取现场（全只读）

### 1.1 账号状态

```bash
curl -sS --max-time 20 -b "$COOKIE_JAR" "$BASE_URL/mail/sender-accounts" \
  | python3 -c "import sys,json;[print(r) for r in json.load(sys.stdin) if r['accountCode']=='$ACCT']"
```

记下 `enabled` / `autoSendPaused` / `hardBounceRateHigh`。

**停止条件**：账号 API 中 `hardBounceRateHigh` 为 `false`，或 `autoSendPaused=true`
且 `autoSendPausedReason` 非空（`SELF_CHECK_FAILED`、`SMTP_TRANSIENT`、`SMTP_INFRA`、
`DAILY_LIMIT`、声誉熔断 `ReputationAutoPauseService` 等）→ 不是硬退率告警，走对应手册。

### 1.2 退信明细

```bash
curl -sS --max-time 20 -b "$COOKIE_JAR" \
  "$BASE_URL/mail/bounces?accountCode=$ACCT&bounceType=HARD&pageSize=100" | python3 -m json.tool
# 同时看软退，用于 2.1 的解析健康度自检
curl -sS --max-time 20 -b "$COOKIE_JAR" \
  "$BASE_URL/mail/bounces?accountCode=$ACCT&bounceType=SOFT&pageSize=100" | python3 -m json.tool
```

> 返回 `{records:[...], totalCount}`，每条 record 已由 `BounceController` 关联出
> `expertName` / `expertEmail`（`originalExpertContactId` 为空时这两个字段为 `null`，见 3.2）。
> `pageSize` 上限 100，默认只有 20 —— 别漏掉后面的记录。

### 1.3 窗口统计

```bash
curl -sS --max-time 20 -b "$COOKIE_JAR" \
  "$BASE_URL/mail-monitoring/bounce-stats?accountCode=$ACCT&days=7" | python3 -m json.tool
```

> 这个接口（`MailMonitoringService.getBounceStats`）与告警共用同一对查询
> （`countHardBouncesSince` / `countSentByAccountSince`），口径与 API 的
> `hardBounceRateHigh` 一致；两边对不上就是数据在这期间变了，重取一次。

### 1.4 填这张表（后续每一步都引用它）

| 专家 / 收件方 | `failedRecipient` 原始串 | DSN 码 | `receivedAt` | `originalExpertContactId` |
|---|---|---|---|---|
| | | | | |

**`failedRecipient` 必须原样抄，不要手工"整理"** —— 第 5.1 步要靠它判断是不是抽取污染。

---

## 2. 判读：三个必答问题

### 2.1 退信是真的吗

看收件方和 DSN 码。来自规范邮件系统（大学、Gmail、Outlook、大厂自建）的 `5.x.x` → 真实失效。

**顺带自检**：DSN 解析修好之后，SOFT 退信应当带 `4.x.x` 的 `dsnStatus`。
若 1.2 拉出的 SOFT 记录 `dsnStatus` 仍为 `NULL` → 该 provider 的 DSN 格式还没被 `b4b87cb` 覆盖，
分类可能仍在漏。**记一条 issue，并且不要把这条 SOFT 计入任何结论。**

### 2.2 是「名单问题」还是「账号问题」——这一步决定后面走 5.1 还是 5.2

| DSN 码 | 含义 | 结论 |
|---|---|---|
| `5.1.1` 用户不存在 | 地址级 | **名单问题**，账号健康 |
| `5.1.3` 地址语法非法 | 地址级，**且我方校验放行了它** | **名单问题**，且指向抽取/校验缺陷 |
| `5.2.1` 邮箱停用/不可接收 | 地址级 | **名单问题** |
| `5.4.1` 收件人被拒 | 地址级 | **名单问题** |
| `5.7.x` 策略/反垃圾拒收 | 发件方级 | **账号/域名声誉问题** |
| 同一收件域集中拒收 | 发件方级 | **账号/域名声誉问题** |

账号问题走 `docs/operations/dmarc-and-postmaster-setup.md`，本手册后半段不适用。

### 2.3 这个百分比是被摊薄的吗

如果硬退的 `receivedAt` 集中在最近 1–2 天，7 天分母会把它摊薄，**真实的批次退信率远高于告警值**。

```bash
for d in <出现退信的日期，逐个列出，如 2026-09-02 2026-09-03>; do
  echo "== $d"
  curl -sS --max-time 20 -b "$COOKIE_JAR" "$BASE_URL/mail-monitoring/sender-accounts?date=$d" \
    | python3 -c "import sys,json;[print(r['accountCode'],r['introductionCount']) for r in json.load(sys.stdin) if r['accountCode']=='$ACCT']"
done
```

`批次级退信率 = 硬退条数 / 这些天 introductionCount 之和`

**决策以批次级为准。** 7 天率只是告警判据，不是事实本身。批次级明显更高时，
结论是「告警触发晚了」，而不是「阈值太敏感」。

---

## 3. 止损核对

### 3.1 三个地址已被拉黑

`BounceCollectionService:111` 在 `bounceType=="HARD" && originalContact!=null` 时调
`ExpertOperatorStatusService.markEmailInvalid()`（MySQL `expert_contact` + ES 三层双写）。
内联识别路径（`AutoMailReplyService:703-714`）走的是同一个 `ingest`，两条路都覆盖。

逐个核对 1.4 表里的 `originalExpertContactId`：

```bash
curl -sS --max-time 20 -b "$COOKIE_JAR" "$BASE_URL/expert-contacts/<contactId>" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('expertEmail'),d.get('operatorStatus'))"
```

- 全部是 `EMAIL_INVALID` → **绿**，但要清楚它到底买到了什么：

  | 它挡住的 | 它**没有**挡住的 |
  |---|---|
  | 运营列表上显示「邮箱失效」，人能看见 | **同一邮箱挂在另一个 ORCID 下的重复联系人** |
  | ES 查询的计数/预估口径排除（`ExpertSearchService:223/243`） | —— `expert_contact` 唯一键是 `uk_campaign_expert (campaign_id, orcid_id)`，**不是邮箱** |

  **注意 `ManualInitialOutreachService:1011` 那条 `it.operatorStatus != "EMAIL_INVALID"`
  对退信专家是冗余的** —— 同一行的 `!hasSentIntroduction(it.id!!)`（按 contact id 查
  `mail_record` 是否有 `SENT` 的 INTRODUCTION，`:968-971`）已经把他们排除了，
  而能退信就必然已经发过。所以「拉黑」不等于「这个地址不会再被发」。

  真正按地址维度拦截的是 `email_suppression`，它的门禁接得很全
  （目标构建 `:1251`、发送前 `:621`、旧链路 `InitialOutreachService:66`、
  最底层 `SmtpMailDeliveryService:20` 兜底），**但 `BounceCollectionService` 从不往它写**
  —— 硬退地址进不了抑制表。见文末待办。
- 有任何一个不是 → **停止条件**。写回没生效，先查 `markEmailInvalid` 的 warn 日志
  （`Failed to mark EMAIL_INVALID for orcid=`）。不修好就不算止损，这些地址下一轮还会被投一次。

### 3.2 `originalExpertContactId` 为空的退信

溯源失败（Message-ID 匹配不上 `mail_record`，且 `failedRecipient` 在 `expert_contact`/alias 里找不到）
的退信**不会触发任何专家侧变更**，只留 `bounce_record` 一行。
这类必须按 `failedRecipient` 人工找到专家并处理，不能当作已止损。

### 3.3 同批次的其他账号必须一并核对

见 5.2：`selectAccountForSending()` 会把同一批地址自动切给下一个账号，所以**不能只看触发告警的这一个**。

先看谁在发同一批（逐日对比各账号的 `introductionCount`）：

```bash
for d in <出现退信的日期>; do
  echo "== $d"
  curl -sS --max-time 20 -b "$COOKIE_JAR" "$BASE_URL/mail-monitoring/sender-accounts?date=$d" \
    | python3 -c "import sys,json;[print(r['accountCode'],r['introductionCount']) for r in json.load(sys.stdin)]"
done
```

再对**每一个有发送量的账号**拉退信统计，不只是触发告警的那个：

```bash
for a in <上一步列出的全部账号>; do
  echo "== $a"
  curl -sS --max-time 20 -b "$COOKIE_JAR" \
    "$BASE_URL/mail-monitoring/bounce-stats?accountCode=$a&days=7" | python3 -m json.tool
done
```

判读：

- 某账号发送量远高于触发告警的账号、`hardBounceCount` 却明显偏低 → 它的退信**还在路上**
  （DSN 延迟数小时到数天），是**未爆的雷**，不是健康。
- 某账号 `hardBounceCount` 不低但 `bounceRate` 仍低于 5% → 是**分母稀释**：
  判据是 7 天窗口的比率，发得越多越不容易触发，保护对暴露量反向敏感。

**停止条件**：只要有第二个账号在投放同一批次，先做 5.2 再回来，不要只处理告警账号。

> 2026-09-03 实测即为此形：LiLei 两天 18 封触发告警，同批次的 LuKai 79 封、WuWei_WB 23 封未触发告警。

---

## 4. 确认发送未被告警阻断

告警不写暂停状态，账号应仍被自动选号选中。刷新账号 API 核对：

```bash
curl -sS --max-time 20 -b "$COOKIE_JAR" "$BASE_URL/mail/sender-accounts" \
  | python3 -c "import sys,json;[print(r['accountCode'],'enabled=',r['enabled'],'autoSendPaused=',r['autoSendPaused'],'hardBounceRateHigh=',r['hardBounceRateHigh']) for r in json.load(sys.stdin) if r['accountCode']=='$ACCT']"
```

预期：`enabled=true`、`autoSendPaused=false`、`hardBounceRateHigh=true`；数据库
`auto_send_paused` / `auto_send_paused_reason` / `auto_send_paused_at` 三列为 `0/NULL/NULL`。
下一轮 IMAP 轮询（`AutoMailReplyService:773`）只写 WARN 日志，不改动这三列。

> 手工 `POST .../resume-auto-send` 端点保留，但**只用于其他真实暂停原因**
> （`SELF_CHECK_FAILED`、`SMTP_TRANSIENT`、`SMTP_INFRA`、`REPUTATION`、`DAILY_LIMIT` 等）。
> 硬退率告警不再产生暂停，账号列表**不显示恢复按钮**，也不应为此调用该端点。

---

## 5. 根因处置

### 5.1 名单问题（2.2 判为地址级时）

**顺序不能反，反了等于白跑一遍全库 scroll：**

1. **先看 `failedRecipient` 原始串**（1.4 表）。
   地址来自 `JatsXmlEmailParser` 从论文全文抽取的通讯地址。
   - `5.2.1` / `5.4.1`：作者离职/邮箱停用，属论文通讯地址的**正常衰减**，抽取链路无解，只能靠时效门槛压低。
   - `5.1.3`：地址在语法层面被对端拒绝，**而我方 `EmailValidationService` 的格式正则 + MX 检查放行了它**。
     这是抽取污染或校验缺口的信号 —— 同成因的地址在 L2 库里不会只有一个。

2. **补规则**。只有在 1 中确认了具体失败模式之后，才去扩 `EmailValidationService.isValidFormat`
   或加本地部分校验。这是代码改动，走计划流程，不在本手册内。

3. **最后才跑全量复验**：

```bash
curl -sS --max-time 30 -X POST -b "$COOKIE_JAR" "$BASE_URL/experts/revalidate-candidates"
# 轮询进度
curl -sS --max-time 15 -b "$COOKIE_JAR" "$BASE_URL/task-progress/EXPERT_REVALIDATION"
```

> **为什么顺序不能反**：`ExpertRevalidationService.revalidateCandidates()` 用的是
> **同一套** `emailValidationService.validate`（格式正则 + 一次性域名 + MX）。
> 而本次退信的地址正是通过了这套规则才被发出去的 —— 在补规则之前跑它，这类一个都抓不到。
>
> **不要试图靠 MX 解决**：`enable-mx-check` 默认已是 `true`（`application.yml:147`），
> 而且 MX 只管域名级，管不到邮箱级。`5.2.1`/`5.4.1` 它天然抓不到。

### 5.2 别让流量搬家（每次都要做）

`isSendable()` 会把 `autoSendPaused` 的账号过滤掉，于是 `selectAccountForSending()`
**自动把同一批脏地址切给下一个账号继续发** —— 止损只是搬家，下一个账号会以同样方式被打挂。

系统当前**没有名单级熔断**：`reachability` 分类与批量任务的 `EXCLUDE_BLOCKED` 过滤已被
`74ec24d refactor(expert): remove reachability classification` 删除，只剩 `EMAIL_INVALID` 的
单地址级排除。所以名单级止血**只能人工做**：

- 找到这批退信地址所属的 discovery 来源批次；
- 暂停该批次对应的批量发送任务；
- 确认没有第二个账号正在投放同一批（对照 `GET /mail-monitoring/sender-accounts` 各账号当日 `introductionCount`）。

---

## 6. 读数时必须知道的四条口径缺陷

在 `BounceRateMonitorService` 的判据修好之前，告警里的百分比只是**近似值**，方向如下：

| # | 缺陷 | 位置 | 对数字的影响 |
|---|---|---|---|
| 1 | 分子按 `bounce_record.received_at`，分母按 `mail_record.sent_at`，**不同期** | `countHardBouncesSince` vs `countSentByAccountSince` | 窗口外发出、窗口内退回的信进分子不进分母 → **偏高** |
| 2 | 分母含**全部** OUTBOUND，无 `mail_type` 过滤（自动回复、跟进信都算） | `MailRecordRepository:469-476` | 稀释冷发信退信率 → **偏低** |
| 3 | 小样本下判据脆 —— **但不要靠提高 `MIN_SAMPLE_SIZE` 解决**，见下方反例表 | `BounceRateMonitorService` | n=48/3 的 Wilson 95% CI 约 [2.2%, 16.8%]，与 5% 统计上不可分 |
| 4 | 只检查跑 auto-reply 轮询的账号 | 唯一调用点 `AutoMailReplyService:773` | 其他账号**从不被检查** |

> **缺陷 #3 的反例（2026-09-03 实测，优先于任何直觉）**：拿 LiLei 的真实数字跑一遍四种判据 ——
>
> | 判据 | 结果 | 会不会触发 |
> |---|---|---|
> | 现行（点估计 + 7 天分母 48） | 6.25% > 5% | 触发 —— **侥幸压线** |
> | 提 `MIN_SAMPLE_SIZE` 到 100 | n=48 < 100，不检查 | **漏掉** |
> | Wilson 下界 + 7 天分母 48 | 下界 2.15% < 5% | **漏掉** |
> | Wilson 下界 + 批次分母 18 | 下界 5.84% > 5% | **触发，且有统计依据** |
>
> 结论：**要修的是分母口径（缺陷 #1、#2），不是样本量下限。** 提高 `MIN_SAMPLE_SIZE`
> 会让这次 16.67% 的真实事故被完整放过。修法见 `docs/plans/2026-09-03/01-bounce-gate-run-cohort.md`。

> 改动这两个 repo 方法时注意：它们各有第二个生产调用方 `MailMonitoringService:270-272`
> （`getBounceStats`）。口径必须一起改，否则监控面板与告警判据会打架。
>
> 另：`BounceRateMonitorService` 的 WARN 日志已改用 `{}` + `String.format`，
> 打出的就是真实百分比，不再是字面量 `{:.2f}%`。

---

## 7. 事件台账（每次处置后追加一行）

| 日期 | 账号 | reason | 硬退/已发（7天） | 批次级率 | 2.2 判读 | 处置 | 解除时间 |
|---|---|---|---|---|---|---|---|
| 2026-09-03 | LiLei | `BOUNCE_RATE_HIGH:6.25%` | 3/48 | 待填（2.3） | 名单问题（NYU `5.1.3` / Harvard `550 5.4.1` / Berkeley `550 5.2.1`） | 待填 | 待填 |

---

## 8. 收尾

```bash
rm -f "$COOKIE_JAR"
unset BASE_URL COOKIE_JAR ACCT
```

---

## 待办：硬退地址未进抑制表

`BounceCollectionService.ingest()` 的 HARD 分支只调 `ExpertOperatorStatusService.markEmailInvalid()`
（人维度），**不写 `email_suppression`**（地址维度，全仓 grep `suppress` 于该文件零命中）。

后果：一个已硬退的地址，只要挂在另一个 ORCID 下（共用实验室邮箱、通讯作者地址被多位共同作者
共用、ORCID 重复记录），就会被再发一次 —— 四道 suppression 门禁一道都拦不住，因为它不在表里。

修法很小：HARD 分支旁边加一行写 `EmailSuppression(email, source = "HARD_BOUNCE")`。
管道全是现成的。风险方向也对：`bounce_type` 当前是**欠检测**（硬退被误记成 SOFT，
见 K-inline-bounce-path-preempts-mime-parse），不是过检测，所以按 HARD 写抑制表不会误杀。
