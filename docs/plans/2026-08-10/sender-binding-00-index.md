# 专家—发送账号绑定：计划拆解索引

## 背景（缺陷起因）

2026-08-10 14:39:59 一封 INTRODUCTION 邮件由已禁用账号 `LiLei`（`enabled=0`）发出。
根因不是缓存：前端 `app.js:8358` 人工发送固定传 `senderAccountCode: null`，
后端 `ManualExpertMailService.kt:58` 回退到 `MailSenderAccountService.selectAccountForManualSending()`
（`MailSenderAccountService.kt:197-201`），其谓词 `isManualSendable()`（`:227-228`）
只排除 `SIMULATOR_NOOP`，**不看 `enabled`**，同分时 `thenBy { it.id }` 取 ID 最大者 → 命中 `LiLei`。

更深层的问题是：`expert_contact` 表（`V1__create_business_tables.sql:79-95`、
`ExpertContact.kt:8-31`）**没有任何 sender 归属字段**，账号归属只散落在
`mail_record.sender_account_code`。因此"同一专家先后由不同账号发信"是系统默认行为。

## 需求（已确认的四项决策）

| # | 议题 | 决策 |
|---|---|---|
| ① | 换绑后已有邮件线程 | 换绑只影响**新发起主题**；回复仍走 `mail_record.sender_account_code`（收信账号） |
| ② | 绑定账号不可用时 | **报错拦截**，不降级重选（细化口径见下方"与既有知识的冲突"） |
| ③ | 负载均衡 | 绑定是**强一致**的，存量绑定必须计入分发打分 |
| ④ | 变更标记 | 只有**运营主动换绑**打标；账号被烧后的**批量迁移不打标** |

## 与既有知识的冲突（必须先读，否则 P2 会误伤既有决策）

**冲突 1 — `enabled=false` 的语义**
`K-sender-account-enabled-scope`（hit_count 7，last_used 2026-07-20）记载：
`enabled=false` 的目标语义是"禁止**自动**外发"，人工发送与 IMAP 收信**允许** disabled 账号。
`MailSenderAccountServiceTest.kt:62-74`（`selectAccountForManualSending includes disabled accounts`）
是该决策的锁定测试。

决策 ② 要求人工发送也拦截 disabled。二者的**可调和口径**（本系列采用）：

> 拦截的对象是「**由绑定解析出的账号**」，不是「人工发送」这个动作本身。
> 收发件箱回复（`PendingMailOperationService.kt:642-647`）仍显式使用收信账号且不受 enabled 门禁，
> `K-sender-account-enabled-scope` 的原始场景（禁用账号仍能收信并回复）完整保留。

P3 完成后必须回写修正该知识条目（见 P3 Phase 6 交付项）。

**冲突 2 — 人工发送脱离每日配额**
`K-operator-send-quota-paths`（hit_count 10）记载："人工发送脱离配额"是刻意设计：
`selectAccountForManualSending` 走 `isManualSendable`，**不含** `todaySentCount < effectiveDailyLimit`；
`MailSenderAccountServiceTest.kt:35-46`（`selects account at daily limit`）与
`:48-57`（`includes auto-paused accounts`）锁定该行为。

因此决策 ② 的"限额"部分**不适用于人工路径**。本系列采用的门禁矩阵：

| 绑定账号状态 | 自动路径（首封/批量） | 人工路径（单封/会议/材料提醒） |
|---|---|---|
| `enabled=false` | 拦截 | **拦截**（本次新增） |
| `autoSendPaused=true` | 拦截 | 放行（保留既有决策） |
| `todaySentCount >= effectiveDailyLimit` | 拦截 | 放行（保留既有决策） |
| `accountCode == SIMULATOR_NOOP` | 拦截 | 拦截（既有） |

## 拆解（5 个顺序计划，后者依赖前者）

| 计划 | 交付 | 文件数 |
|---|---|---|
| [P1](sender-binding-01-schema-and-establish.md) | `expert_contact` 绑定列 + 回填 + 建立点 + 解析服务（未接入消费） | 9 |
| [P2](sender-binding-02-send-path-consistency.md) | 四条发送路径改为按绑定解析 + enabled 门禁 | 8 |
| [P3](sender-binding-03-assignment-stock-balance.md) | 分发打分计入存量绑定（决策 ③） | 6 |
| [P4](sender-binding-04-rebind-api-and-audit.md) | 换绑/迁移接口 + `operator_action_log` 审计 + 变更标记列（决策 ④） | 8 |
| [P5](sender-binding-05-frontend-visibility.md) | 专家列表徽标 + 账号列表绑定数 + 换绑 UI | 9 |

每个计划独立可部署、可验证。P1 落地后系统行为不变（只写字段不消费），
P2 起才产生用户可见的行为变化。

## 全局不变量（跨计划，各计划内以 `(全局 G-n)` 引用）

### G-1: 绑定 = 主题发起权归属，不是外发通道归属
- Rule: `expert_contact.bound_sender_account_code` 决定**新发起主题邮件**从哪个账号发出。
  已存在的邮件线程（回复）由 `mail_record.sender_account_code` 决定，绑定不参与。
- Applies to: `PendingMailOperationService.kt:642-647`（回复，**不改**）、
  `AutoMailReplyService.processSingle(account, ...)`（自动回复用收信账号，**不改**）。
- Violation consequence: 换绑后从新账号回复旧线程，`In-Reply-To` 与 `From` 域不一致，
  投递到垃圾箱；且旧账号信箱里的线程失去回复方。
- 来源: 决策 ①

### G-2: 绑定为 NULL 表示"未绑定"
- Rule: 未绑定用 SQL `NULL` 表示，禁止空串、`"UNKNOWN"`、`"NONE"` 等哨兵值。
- Applies to: 所有写 `bound_sender_account_code` 的路径。
- Violation consequence: `IS NULL` 判定失效，回填与"无绑定兜底"分支静默失效。

### G-3: `SIMULATOR_NOOP` 永不进入绑定
- Rule: `MailSenderAccountService.SIMULATOR_ACCOUNT_CODE`（`:257`）不得写入
  `bound_sender_account_code`，任何路径皆然。
- 来源: K-sender-account-enabled-scope（"`SIMULATOR_NOOP` 始终必须被真实收发路径排除"）

## 验证命令（全系列共用，各计划引用本节）

> 本项目必须用 JDK 11（zulu-11）。裸 `mvn` 会因 JDK 版本构建失败。
> 来源：项目根 `CLAUDE.md`「Commands」章节 + 「项目元信息」`test_command` / `build_command`。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

前端 JS 用例的执行入口见 `K-js-test-invocation-surface`：`mvn test` 通过
`exec-maven-plugin`（`pom.xml:185-234`）绑定 `node --test src/test/js/*.test.js`，
但 `verify.sh` **只跑一个文件**，不可作为前端门禁。P5 单独给出 `node --test` 命令。

通过判据：`Tests run: N, Failures: 0, Errors: 0`，且 `BUILD SUCCESS`。
