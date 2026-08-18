# DMARC 收紧与 Google Postmaster Tools 接入手册

适用范围：发信域 `talents.szwebotech.cn`（如后续新增发信域，每个域都要重复本文全部步骤）。

本文分两部分，互不依赖，可分别执行：

- [第一部分：DMARC 收紧](#第一部分dmarc-收紧)——纯 DNS 操作，不涉及代码
- [第二部分：Postmaster Tools 接入](#第二部分postmaster-tools-接入)——GCP 配置 + 环境变量

> **先读这段再动手。** 当前处于测试阶段、几乎没有发信量。这决定了两件事：
>
> 1. **DMARC 现在收紧是最佳时机**——没有正常流量会被误伤，出问题影响面最小。
> 2. **Postmaster 现在接不到数据是正常的**。Google 要求域名每天向**个人 Gmail 账户**发送 **100 封以上通过认证的邮件**，指标才会出现；IP 信誉等面板需要 1000+。发往 Google Workspace 企业域的邮件不计入该阈值。因此现在接入的意义是"提前配好、放量后自动开始积累"，而不是"今天就能看到数字"。见 [Suped: GPT 最低发送量要求](https://www.suped.com/knowledge/email-deliverability/tools/what-email-volume-is-required-to-see-data-in-google-postmaster-tools)。

---

## 第一部分：DMARC 收紧

### 现状

抓包确认当前记录的效果是 `p=NONE`，且 SPF / DKIM / DMARC 三项校验均已通过。也就是说**认证链路本身是好的**，缺的只是策略强度和可观测性。

### 1.1 先确认 rua 接收方式

推荐用**第三方免费档**，理由是：报告是 gzip 压缩的 XML，人工读不现实；而当前量级完全落在各家免费额度内。

| 方案 | 适用 | 说明 |
|---|---|---|
| **第三方托管（推荐）** | 当前阶段 | 把 `rua` 指向服务商的收集地址，自动解析成面板。[DMARC Report](https://dmarcreport.com/) 免费档为 1 个域名 / 10,000 份报告每月 / 30 天历史，当前量级绰绰有余。同类可选 [EasyDMARC](https://easydmarc.com/tools/dmarc-report-analyzer)、[dmarcian](https://dmarcian.com/)、[Valimail](https://www.valimail.com/dmarc-report-analyzer/)。 |
| 自建 | 放量之后 | [parsedmarc](https://github.com/domainaware/parsedmarc) + Elasticsearch 是开源标准方案。本项目已有 ES，后续如果想把 DMARC 报告和专家数据放在一起分析，这条路是通的。 |
| 自有邮箱 | 不推荐 | 直接收 XML 压缩包，基本读不动。 |

**操作**：注册所选服务 → 添加域名 `talents.szwebotech.cn` → 拿到它给的 rua 地址（形如 `xxxx@in.somedmarcservice.com`）→ 填入下一步的 `<RUA_ADDRESS>`。

### 1.2 分三阶段收紧（不要一步到位）

每一阶段的 DNS 记录：

```
名称（Host）: _dmarc.talents.szwebotech.cn
类型:         TXT
TTL:          3600
```

**阶段一 · 只观察（立即执行，观察 1–2 周）**

```
v=DMARC1; p=none; rua=mailto:<RUA_ADDRESS>; fo=1; adkim=r; aspf=r
```

- `p=none` 不改变收件方行为，纯收集
- `fo=1` 任一认证失败就产生失败报告，便于发现问题
- `adkim=r` / `aspf=r` 宽松对齐，先摸清现状

**验收标准**：报告面板里所有发信来源都能识别（应该只有腾讯企业邮的出口），且 SPF/DKIM 通过率接近 100%。**没达到这个标准就不要进入阶段二。**

**阶段二 · 灰度隔离（阶段一达标后）**

```
v=DMARC1; p=quarantine; pct=25; rua=mailto:<RUA_ADDRESS>; fo=1; adkim=r; aspf=r
```

观察 1 周无异常后，把 `pct` 依次提到 `50` → `100`。

**阶段三 · 拒绝（放量且稳定后再做）**

```
v=DMARC1; p=reject; rua=mailto:<RUA_ADDRESS>; fo=1; adkim=s; aspf=s
```

`adkim=s` / `aspf=s` 是严格对齐。**只有在阶段二 `pct=100` 稳定运行至少两周、且报告中没有合法来源被拦截时才做这一步。**

### 1.3 验证命令

```bash
# 查看当前 DMARC 记录
dig +short TXT _dmarc.talents.szwebotech.cn

# 顺带核对 SPF 与 DKIM（DKIM selector 当前为 card2607）
dig +short TXT talents.szwebotech.cn
dig +short TXT card2607._domainkey.talents.szwebotech.cn
```

DNS 生效前记录仍会返回旧值，按 TTL 等待。

---

## 第二部分：Postmaster Tools 接入

### 2.1 代码侧已完成的修复

接入前请确认已包含以下修复（本次改动）：

| 问题 | 位置 | 说明 |
|---|---|---|
| **OAuth scope 无效（致命）** | `PostmasterDataCollector.kt` | 原代码请求 `.../auth/postmaster.readonly`，这是 **v1 时代的 scope，v2 已不存在**。v2 discovery 文档中 `domainStats.query` 只接受 `.../auth/postmaster` 与 `.../auth/postmaster.traffic.readonly`。用错 scope 会鉴权失败，异常被 `catch` 吞掉只留一行 WARN，表现为"永远没数据"——极易被误判成"量不够"。现改用 SDK 常量 `PostmasterToolsScopes.POSTMASTER_TRAFFIC_READONLY`。 |
| **服务账号不能代表 Postmaster 用户** | `PostmasterOAuthService.kt` | Postmaster API 要求已获域名访问权的 Google/Workspace 用户 OAuth2 授权；不再使用服务账号 JSON。授权完成后，应用保存 `authorized_user` refresh token 文件。 |
| **异常缺少堆栈** | 同上 | 采集失败原来只打印 `ex.message`，鉴权类错误无法定位。现已附带完整异常。 |
| **无法手动授权/触发** | `MailMonitoringController.kt` | 新增 OAuth 授权入口、回调、状态查询，以及原有手动采集接口。 |

### 2.2 GCP 侧配置

1. **创建 / 选择 GCP 项目**（https://console.cloud.google.com/）
2. **启用 API**：API 和服务 → 库 → 搜索 `Gmail Postmaster Tools API` → 启用
3. **配置 OAuth 权限请求页面**：Google Auth Platform → Branding / Audience / Data Access
4. Data Access 添加范围：

   ```text
   https://www.googleapis.com/auth/postmaster.traffic.readonly
   ```

5. Audience 选择 Internal（企业 Workspace）或 External（普通 Gmail）；External 测试状态下加入实际授权账号。
6. **创建 OAuth Client**：代码部署后创建 `Web application`，回调地址必须与 `POSTMASTER_OAUTH_REDIRECT_URI` 完全一致。

> 注意：不要创建 Service Account Key 或 API Key。授权账号必须是有 Postmaster 域名访问权的 Google/Workspace 用户。

### 2.3 Postmaster Tools 侧配置

1. 打开 https://postmaster.google.com/ ，用你的 Google 账号登录
2. 添加或确认发信认证域名（DKIM `d=` 或 SPF Return-Path 域名）
3. 按提示在 DNS 加 TXT 验证记录，等待验证通过
4. 每个已验证域名进入 `Manage → Manage users → Add`
5. 添加用于 OAuth 授权的 Google/Workspace 用户邮箱

> 用户没有域名访问权会得到 `PERMISSION_DENIED`，而不是空数据。

### 2.4 环境变量

```bash
# 必填
POSTMASTER_ENABLED=true
POSTMASTER_DOMAINS=mail.szwebotech.cn,szwebotech.cn,talents.szwebotech.cn,updates.szwebotech.cn

# OAuth 客户端配置
POSTMASTER_OAUTH_CLIENT_ID=...
POSTMASTER_OAUTH_CLIENT_SECRET=...
POSTMASTER_OAUTH_REDIRECT_URI=https://qingfei.szwbww.com/talent/api/mail-monitoring/postmaster/oauth/callback
POSTMASTER_OAUTH_TOKEN_FILE=/etc/talent/secrets/postmaster-oauth-token.json

# 可选，均有默认值
POSTMASTER_CRON="0 0 8 * * *"                     # 每天 08:00 采集
POSTMASTER_PAUSE_THRESHOLD_SPAM_RATE=0.003        # 垃圾率 ≥ 0.3% 自动暂停该域名下的发信账号
POSTMASTER_RESUME_THRESHOLD_SPAM_RATE=0.001       # 恢复阈值
POSTMASTER_RESUME_CONSECUTIVE_DAYS=3              # 需连续 N 天低于恢复阈值
```

配置项定义见 `config/PostmasterProperties.kt`，默认值见 `application.yml` 的 `talent-introduction.postmaster`。

> OAuth client secret 只放服务器环境变量或密钥管理系统；refresh token 由授权回调写入 `POSTMASTER_OAUTH_TOKEN_FILE`，文件权限为 `600`，不要提交 Git 或粘贴到聊天。

### 2.5 验证

重启应用后，在已登录后台的浏览器打开授权入口：

```text
https://qingfei.szwbww.com/talent/api/mail-monitoring/postmaster/oauth/start
```

完成 Google 授权并回到回调地址后，检查状态：

> 下列 API 受后台登录保护。`curl` 必须携带已登录会话 Cookie；最简单是直接在已登录浏览器打开授权入口，或从浏览器开发者工具复制请求。

```bash
curl 'https://qingfei.szwbww.com/talent/api/mail-monitoring/postmaster/oauth/status'

# 采集昨天的数据（Postmaster 数据有延迟，当天通常查不到）
curl -X POST 'https://qingfei.szwbww.com/talent/api/mail-monitoring/postmaster/collect?skipAutoPause=true'

# 查看落库结果
curl 'https://qingfei.szwbww.com/talent/api/mail-monitoring/reputation-history?days=30'
```

**如何判读结果**：

| 现象 | 含义 | 处理 |
|---|---|---|
| `triggered=false` 且提示 `POSTMASTER_ENABLED` | 开关没开或未重启 | 检查环境变量是否真正注入进程 |
| `triggered=false` 且提示 `POSTMASTER_DOMAINS` | 域名列表为空 | 检查逗号分隔格式 |
| `triggered=false` 且提示 `OAuth` | 尚未完成用户授权或 token 文件不可读 | 先打开 `/postmaster/oauth/start`，再查 token 文件权限 |
| `triggered=true`，但 history 为空数组 | 采集跑了，但 Google 没返回数据 | **看日志**：无异常 = 发信量未达阈值（当前阶段的预期结果）；有 `PERMISSION_DENIED` = 2.3 第 4 步没做；有 `invalid_scope` / `401` = 凭证或 scope 问题 |
| history 有行但各项指标为 null | 同上，Google 对该日无数据 | 同上 |
| `domainReputation` 恒为 null | **预期行为，不是故障** | v2 的 `StandardMetric` 枚举里没有域名信誉指标（只有 `SPAM_RATE` / `AUTH_SUCCESS_RATE` / `TLS_ENCRYPTION_*` / `DELIVERY_ERROR_*` / `FEEDBACK_LOOP_*`），域名信誉是 v1 的概念。该列会一直是空 |

### 2.6 自动暂停机制的注意事项

`ReputationAutoPauseService` 会在垃圾率 ≥ `POSTMASTER_PAUSE_THRESHOLD_SPAM_RATE` 时，暂停该域名下所有已启用发信账号的自动发送，暂停原因以 `REPUTATION:` 前缀写入。恢复需要连续 `POSTMASTER_RESUME_CONSECUTIVE_DAYS` 天低于恢复阈值，且**这些天必须是连续日期**（中间断档不算）。

当前阶段 `spamRate` 取不到值时会直接跳过判定，不会误暂停。但放量后请注意：**该判定没有最小样本量保护**，刚过 100 封阈值时，个位数的投诉就可能触碰 0.3%。建议放量初期先把 `POSTMASTER_PAUSE_THRESHOLD_SPAM_RATE` 调高或用 `skipAutoPause=true` 手动采集观察，稳定后再启用自动暂停。

---

## 附：一个尚未接入的能力（建议后续评估）

v2 API 提供了 `domains.getComplianceStatus` 接口，**本项目当前未使用**。它返回的是合规判定而非流量指标，因此**不受发信量阈值限制**——在当前几乎没有量的情况下，它可能是唯一能立刻拿到反馈的接口。

它覆盖的判定项包括：`SPF`、`DKIM`、`SPF_AND_DKIM`、`DMARC_POLICY`、`DMARC_ALIGNMENT`、`MESSAGE_FORMATTING`、`DNS_RECORDS`、`ENCRYPTION`、`USER_REPORTED_SPAM_RATE`、`ONE_CLICK_UNSUBSCRIBE`、`HONOR_UNSUBSCRIBE`，每项返回 `COMPLIANT` / `NEEDS_WORK`。

另外它的 `deliverabilityStatusVerdict.reason` 会明确区分 `MESSAGE_VOLUME_LOW`（量不够）与 `SENDER_NOT_COMPLIANT`、`SPAM_RATE_HIGH` 等，**正好能回答"到底是没量还是接入坏了"这个问题**——而这正是当前阶段最难判断的事。

其中 `ONE_CLICK_UNSUBSCRIBE` 一项与此前发现的 `${unsubscribeUrl}` 未替换问题直接相关，值得优先验证。

所需 scope 与现有采集一致（`postmaster.traffic.readonly`），无需额外授权。接入成本估计为一个新 service + 一张表 + 一个只读接口。

---

## 变更记录

| 日期 | 内容 |
|---|---|
| 2026-08-09 | 初版。修复 v2 scope 误用、凭证读取方式、异常日志，新增手动触发端点 |
