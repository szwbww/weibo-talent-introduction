# Plan 02b — mailto 退订通道生效

> 顺序位置：从 [unsubscribe-02-suppression-gate.md](unsubscribe-02-suppression-gate.md) v2 修订中拆出。
> 建议顺序：Plan 02 之后（两者都改 `EmailSuppressionService.kt`，顺序合并可避免冲突），但无逻辑依赖，也可先做本计划。
> 见 [unsubscribe-closure-master.md](unsubscribe-closure-master.md)
> 优先级：P1 —— Gmail 实际收信测试的前置阻塞项

## 需求描述

### Observable outcome

1. 收件人通过 `List-Unsubscribe` 头里的 `mailto:` 通道退订（主题 `unsubscribe`、正文为空的邮件）能进入抑制名单，`source` 记为 `MAILTO`。
2. 主题里只是**提到**退订字样的正常来信不会被误加入抑制名单。

### What must NOT change

- 正文退订话术的判定行为与短语全集（`UNSUBSCRIBE_PHRASES`，`EmailSuppressionService.kt:82-92`，9 项）一字不改；正文命中的来源仍为 `INBOUND_REPLY`。
- `looksLikeUnsubscribe(body: String?)` 的公开签名与行为不变（`EmailSuppressionServiceTest.kt:76-81` 依赖）。
- `InboundIntentClassifier` 不改 —— 退订捕获是独立副作用，`EmailSuppressionService.kt:73` 注释明确「独立退订关键词判定，不复用 InboundIntentClassifier」。
- `EmailSuppressionService.suppress()` 的幂等语义（`:25-41`）不变。
- 抑制名单的 6 个既有 `isSuppressed` 检查点不变。

### Out of scope

- 投递层抑制拦截 → Plan 02。
- `List-Unsubscribe` 头里 mailto 地址本身的形态（`SmtpMailDeliveryService.kt:59` 的 `mailto:${account.senderEmail}?subject=unsubscribe`）不改。
- 收到 mailto 退订邮件后是否给对方回执 —— 不做（RFC 8058 不要求，且回执给已退订者本身矛盾）。

## 关键不变量

### Invariant I-1：主题退订判定用精确相等，不用包含

- Rule：主题触发的退订判定必须对**归一化后的主题**（`trim()` + `lowercase(Locale.ROOT)`）与一个小集合做**精确相等**比较；集合固定为 `setOf("unsubscribe", "退订", "取消订阅")`。**禁止**对主题使用 `contains`。正文判定继续用既有 `contains` + `UNSUBSCRIBE_PHRASES` 全集，行为不变。
- Applies to：`EmailSuppressionService` 新增的主题判定。
- Violation consequence：主题用 `contains` 会把任何标题带 "unsubscribe"/"退订" 的正常来信（含我们自己外发主题被引用进 `Re:` 的情形）误判为退订并**永久**加入抑制名单，属不可逆误伤。精确相等恰好匹配 `SmtpMailDeliveryService.kt:59` 生成的 `?subject=unsubscribe` 契约。
- 来源：original

### Invariant I-2：退订来源可区分，主题优先

- Rule：主题精确命中 → `SuppressionSource.MAILTO`；主题未命中而正文命中 → `SuppressionSource.INBOUND_REPLY`；都不命中 → 不写入。两者互斥，主题优先。
- Applies to：`EmailSuppressionService.detectUnsubscribeSource()`；`AutoMailReplyService.captureUnsubscribeIfPresent()`。
- Violation consequence：`SuppressionSource.MAILTO`（`EmailSuppressionService.kt:10`）当前是**全仓零写入方的死枚举值**（`grep "SuppressionSource\." src/main/kotlin` 只命中 `INBOUND_REPLY` / `ONE_CLICK` ×2 / `MANUAL`）。若统一记 `INBOUND_REPLY`，运营无法区分「回信里说了退订」与「点了客户端退订按钮走 mailto」，也无法验证 mailto 通道是否真的生效。
- 来源：original

### Invariant I-3：三个捕获调用点必须同时改

- Rule：`captureUnsubscribeIfPresent` 的 3 个调用点（`AutoMailReplyService.kt:138`、`:197`、`:310`）必须全部传入 `received.subject`。
- Applies to：`AutoMailReplyService`。
- Violation consequence：漏改任一处，走该分支的 mailto 退订仍然失效，且失效是静默的（邮件被当普通来信处理，不报错）。三处的 `received` 均在作用域内（`ReceivedMail.subject`，`MailReceiveService.kt:23`）。
- 来源：original

## 现状审计

> Step 1b-fe **未触发**：无前端文件。故无 `## 样式契约` 节。

### Store：MySQL `email_suppression`

- Schema：`V30__create_email_suppression.sql`。**本计划不改 schema，不新增字段** —— `source` 列与 `MAILTO` 枚举值均已存在。

**Write paths（全量 grep `SuppressionSource\.` src/main/kotlin）：**

| # | 位置 | 来源值 | 本计划是否改动 |
|---|---|---|---|
| 1 | `AutoMailReplyService.kt:839-843` | `INBOUND_REPLY` | ✅ 改为按判定结果写 `MAILTO` 或 `INBOUND_REPLY` |
| 2 | `UnsubscribeController.kt:24` | `ONE_CLICK` | 否 |
| 3 | `UnsubscribeController.kt:38` | `ONE_CLICK` | 否 |
| 4 | `EmailSuppressionController.kt:30` | `MANUAL` | 否 |
| 5 | `SuppressionSource.MAILTO` | — | **当前零写入方**，本计划赋予唯一写入点（#1） |

### 当前判定实现

`EmailSuppressionService.kt:73-77`：

```kotlin
/** 独立退订关键词判定，不复用 InboundIntentClassifier。 */
fun looksLikeUnsubscribe(body: String?): Boolean {
    val b = body?.lowercase(Locale.ROOT) ?: return false
    return UNSUBSCRIBE_PHRASES.any { b.contains(it) }
}
```

`UNSUBSCRIBE_PHRASES`（`:82-92`）9 项：`unsubscribe`、`please remove me`、`remove me from`、`stop emailing`、`opt out`、`opt-out`、`取消订阅`、`退订`、`不要再发`。

调用点（全量 grep `looksLikeUnsubscribe`）：`AutoMailReplyService.kt:838`（生产唯一）、`EmailSuppressionServiceTest.kt:77-80`（测试 4 处断言）。

`AutoMailReplyService.kt:837-845`：

```kotlin
private fun captureUnsubscribeIfPresent(senderEmail: String, cleanedBody: String?) {
    if (emailSuppressionService.looksLikeUnsubscribe(cleanedBody)) {
        emailSuppressionService.suppress(senderEmail, SuppressionSource.INBOUND_REPLY, "inbound reply unsubscribe")
    }
}
```

3 个调用点：`:138`、`:197`、`:310`，均为 `captureUnsubscribeIfPresent(received.from, cleanedBody)`。

### 失效机理（本计划存在的理由）

`SmtpMailDeliveryService.kt:59-60` 生成的 header 是：

```kotlin
val mailto = "mailto:${account.senderEmail}?subject=unsubscribe"
message.addHeader("List-Unsubscribe", "<$httpsUrl>, <$mailto>")
```

邮件客户端走这条通道时发出的是**主题为 `unsubscribe`、正文为空**的邮件。而当前判定只看 `cleanedBody` → `body?.lowercase() ?: return false` → 正文为 null/空时判 false → 不入抑制名单，该邮件掉进普通入站流程（大概率意图不明 → `MANUAL_HANDOFF`）。不算静默丢失，但等于对外承诺了一个不生效的自动退订通道。

### Interaction points

- **IP-1**：主题判定 × 3 个 `captureUnsubscribeIfPresent` 调用点（I-3）。
- **IP-2**：主题判定 × `InboundIntentClassifier`。本计划**不改**分类器；退订捕获保持为独立副作用（`EmailSuppressionService.kt:73` 注释确立的边界）。mailto 退订邮件仍会被分类并落入 `MANUAL_HANDOFF` 之类的常规流转 —— 这是可接受的（人工能看到该来信），本计划只保证抑制名单被正确写入。
- **IP-3**：主题判定 × 我们自己外发邮件的主题。当前 4 个模板主题为 `Research Collaboration Opportunity`（V2）、`Follow-up on the Talent Program`（V2）、`Gentle Follow-up on the Requested Materials`（V71）、会议确认类，**均不含**退订字样，故 `Re:` 引用不会误触发。但这是数据层的偶然安全，不能作为设计依据 —— 精确相等（I-1）才是。

## 实现方案

### 任务 T-1：主题判定与来源区分（遵循 I-1、I-2）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionService.kt`

1. 把 `looksLikeUnsubscribe(body)`（`:74-77`）的短语匹配逻辑抽为 `private fun containsUnsubscribePhrase(text: String?): Boolean`，实现**一字不改**（`UNSUBSCRIBE_PHRASES` 全集 9 项不增不减）。
2. 保留公开入口 `fun looksLikeUnsubscribe(body: String?): Boolean = containsUnsubscribePhrase(body)`（既有测试依赖，行为不变）。
3. 新增：

```kotlin
/** 主题触发的退订只接受精确相等，禁止 contains。见 plan I-1。 */
private fun subjectRequestsUnsubscribe(subject: String?): Boolean {
    val s = subject?.trim()?.lowercase(Locale.ROOT) ?: return false
    return s in SUBJECT_UNSUBSCRIBE_PHRASES
}

/** 主题优先，其次正文；都不命中返回 null。见 plan I-2。 */
fun detectUnsubscribeSource(subject: String?, body: String?): SuppressionSource? = when {
    subjectRequestsUnsubscribe(subject) -> SuppressionSource.MAILTO
    containsUnsubscribePhrase(body) -> SuppressionSource.INBOUND_REPLY
    else -> null
}
```

4. companion object（`:79-93`）新增：

```kotlin
        private val SUBJECT_UNSUBSCRIBE_PHRASES = setOf("unsubscribe", "退订", "取消订阅")
```

### 任务 T-2：捕获逻辑接入主题（遵循 I-2、I-3，覆盖 IP-1）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`

1. `captureUnsubscribeIfPresent`（`:837-845`）改为：

```kotlin
private fun captureUnsubscribeIfPresent(senderEmail: String, subject: String?, cleanedBody: String?) {
    val source = emailSuppressionService.detectUnsubscribeSource(subject, cleanedBody) ?: return
    emailSuppressionService.suppress(
        senderEmail,
        source,
        if (source == SuppressionSource.MAILTO) "mailto unsubscribe" else "inbound reply unsubscribe"
    )
}
```

2. **三个调用点全部**改为传 `received.subject`：
   - `:138` → `captureUnsubscribeIfPresent(received.from, received.subject, cleanedBody)`
   - `:197` → 同上
   - `:310` → 同上

## 变更文件清单

| # | 文件 | 类型 | 改动 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionService.kt` | 修改 | 主题精确判定 + `detectUnsubscribeSource`（T-1） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 修改 | 捕获签名与实现 + 3 个调用点传 subject（T-2） |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionServiceTest.kt` | 修改 | 5 条新用例（见验收标准） |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | 修改 | mailto 捕获用例 |

文件数：4（≤10 ✅）。子系统数：1（入站退订判定）（≤2 ✅）。新增数据字段：0（≤1 ✅）。

**与 Plan 02 的重叠**：两者都改 `EmailSuppressionService.kt` —— Plan 02 只在文件末尾追加异常类，本计划只改类体内的判定方法，无同行冲突。建议顺序合并。

## 验证命令

> 本项目**必须**用 JDK 11（zulu-11），裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=EmailSuppressionServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AutoMailReplyServiceTest

# 单方法（定位失败时）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='EmailSuppressionServiceTest#detectUnsubscribeSource rejects subject that merely contains the phrase'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`（`mvn clean package` 额外要求 `BUILD SUCCESS`）。
来源：CLAUDE.md 「Commands」章节与项目元信息 `test_command` / `build_command`。

## 验收标准

- **I-1**：新增用例 `detectUnsubscribeSource rejects subject that merely contains the phrase` 通过 —— 主题 `"Re: unsubscribe policy question"`、`"Question about unsubscribe"`、`"关于退订的问题"` 且正文不含话术时返回 `null`。`grep -n "SUBJECT_UNSUBSCRIBE_PHRASES" src/main/kotlin/.../EmailSuppressionService.kt` 显示主题分支用 `in`（集合相等）而非 `contains`。
- **I-2**：`grep -rn "SuppressionSource.MAILTO" src/main/kotlin` 命中 ≥1 处（不再是死枚举）；用例 `detectUnsubscribeSource prefers subject over body` 通过（主题精确命中且正文也含话术 → `MAILTO`）。
- **I-3**：`grep -n "captureUnsubscribeIfPresent" src/main/kotlin/.../AutoMailReplyService.kt` 共 4 处命中（3 调用 + 1 定义），3 个调用点全部含 `received.subject` 实参。
- **回归（What must NOT change）**：`EmailSuppressionServiceTest.kt:76-81` 的 `looksLikeUnsubscribe detects unsubscribe phrases` **原样保留且通过**；`git diff` 显示 `UNSUBSCRIBE_PHRASES` 列表零改动、`InboundIntentClassifier.kt` 零改动。
- 其余新增用例：`detectUnsubscribeSource returns MAILTO for exact unsubscribe subject`（`"unsubscribe"` / `" Unsubscribe "` / `"退订"` / `"取消订阅"`，正文 null 或空）、`detectUnsubscribeSource falls back to body with INBOUND_REPLY`、`detectUnsubscribeSource returns null when neither matches`。
- `AutoMailReplyServiceTest` 新用例：主题 `"unsubscribe"`、正文为空的入站邮件经 `processSingle` 后，`suppress` 被以 `SuppressionSource.MAILTO` 调用一次。
- 回归：执行「验证命令」节的全量测试命令通过；执行「验证命令」节的构建命令通过。

## 人工验收清单

### A-1：mailto 退订进入抑制名单且来源为 MAILTO

- 前置条件：可控测试邮箱 T；T 不在抑制名单；系统已给 T 发过一封介绍邮件（存在 `ExpertContact`）；收信自动处理已开启。
- 操作步骤：
  1. 从 T 向发件账号发一封邮件，**主题填写 `unsubscribe`，正文完全留空**。
  2. 等待一次收信轮询（或后台手工触发一次自动回复批处理）。
  3. 后台 → 抑制名单，搜索 T。
- 预期结果：抑制名单出现 T，`source` 列显示 `MAILTO`，`reason` 显示 `mailto unsubscribe`。T 未收到任何自动回复。
- 覆盖：observable outcome 1；I-1；I-2；IP-1

### A-2：真实客户端 mailto 退订按钮闭环（端到端）

- 前置条件：一封真实收到的介绍邮件在支持 mailto 退订的客户端里（Apple Mail / Thunderbird 等；Gmail 网页版通常优先走 HTTPS 一键退订，若不展示 mailto 入口则本条改为查看邮件原始头确认 mailto 地址存在，并手工按 A-1 方式发信替代）。
- 操作步骤：
  1. 在客户端点击退订入口，确认发出的邮件主题。
  2. 等一次收信处理。
  3. 后台 → 抑制名单搜索该邮箱。
- 预期结果：客户端发出的主题为 `unsubscribe`；抑制名单出现该邮箱，`source` = `MAILTO`。
- 覆盖：observable outcome 1

### A-3：主题里只是提到「退订」不会被误判（防误伤）

- 前置条件：全新未退订的测试邮箱 V，已有介绍邮件往来。
- 操作步骤：
  1. 从 V 回一封邮件，**主题填 `Question about your unsubscribe policy`**，正文写一句与退订无关的话（例如 `Could you tell me more about the programme timeline?`；注意不要出现 unsubscribe / 退订 / opt out / remove me 等词）。
  2. 等一次收信处理。
  3. 后台 → 抑制名单搜索 V。
- 预期结果：V **不在**抑制名单中；该来信按正常意图分类流转（QA 自动回复或转人工）；V 后续仍能正常收到邮件。
- 覆盖：observable outcome 2；I-1

### A-4：正文退订话术的既有行为未变（回归）

- 前置条件：未退订的测试邮箱 U，已有介绍邮件往来。
- 操作步骤：
  1. 从 U 回一封邮件，主题正常（例如 `Re: Research Collaboration Opportunity`），正文写 `Please unsubscribe me from this list.`。
  2. 等一次收信处理。
  3. 后台 → 抑制名单搜索 U。
- 预期结果：U 进入抑制名单，`source` 为 `INBOUND_REPLY`（**不是** `MAILTO`），`reason` 为 `inbound reply unsubscribe`。
- 覆盖：What must NOT change 第 1 项；I-2

### A-5：中文退订主题同样生效

- 前置条件：测试邮箱 W，未退订，已有往来。
- 操作步骤：从 W 发一封主题为 `退订`、正文为空的邮件，等一次收信处理，查抑制名单。
- 预期结果：W 进入抑制名单，`source` = `MAILTO`。
- 覆盖：I-1（集合含中文项）

> 人工验收开始时，从本节导出 `docs/plans/2026-08-11/unsubscribe-02b-mailto-channel-acceptance.md`。清单本身有误时先改本节再重新导出。
