# Google 投诉率规避方案

## 背景

- Postmaster Tools 报告 6/29 投诉率 6.9%（Google 阈值 0.3%）
- 根因：unsubscribe 未启用 + 发量指数级暴涨（5天从40→480封/日）
- 退订压制表为空（从未生效）
- 当前 `batchSend.runtimeStatus=PAUSED`，已暂停发送

## 目标

服务器：`root@150.158.92.103`
Tomcat：`/opt/apache-tomcat-9.0.71`
数据库：`mysql -u root -proot talent_introduction`
管理后台：`http://qingfei.szwbww.com/talent/`
域名解析：`qingfei.szwbww.com` → `198.18.17.9`（经代理/CDN，非直连服务器）
应用 context path：`/talent`（WAR 部署为 `talent.war`）
HTTPS：已启用，但 **SSL 证书域名不匹配** — 证书签发给 `szwbww.com` + `www.szwbww.com`，不覆盖 `qingfei.szwbww.com` 子域名

## 当前配置快照（2026-07-03 采集）

### 启用账号

| account_code | sender_email | daily_send_limit | warmup_enabled |
|---|---|---|---|
| LiLei | lilei@talents.szwebotech.cn | 30 | 0 |
| WuWei_WB | wuwei@mail.szwebotech.cn | 30 | 0 |

### batch_send_setting

| key | 当前值 | 目标值 |
|---|---|---|
| batchSend.dailyCap | 320 | 60 |
| batchSend.roundSize | 160 | 30 |
| batchSend.perMailIntervalMs | 1000 | 3000 |
| batchSend.perRoundIntervalMs | 60000 | 120000 |
| batchSend.runtimeStatus | PAUSED | 保持 PAUSED |

### DNS 现状

| 域名 | SPF | DMARC | DKIM |
|---|---|---|---|
| szwebotech.cn | `v=spf1 include:spf.mail.qq.com ~all` | `p=none` | 未查 |
| talents.szwebotech.cn | **缺失（继承主域名）** | `p=none` | 未查 |
| mail.szwebotech.cn | **缺失（继承主域名）** | `p=none` | 未查 |

### HTTPS 现状

Tomcat 80 端口直连，无 SSL 证书，无 nginx。`UNSUBSCRIBE_BASE_URL` 需要 HTTPS 才能让 Gmail 后端执行 RFC 8058 one-click unsubscribe POST。

---

## 执行步骤

### 步骤 0（手动）：修复 SSL 证书域名覆盖

**当前问题**：SSL 证书签发给 `szwbww.com` + `www.szwbww.com`，不覆盖 `qingfei.szwbww.com`。
Gmail 后端做 RFC 8058 one-click POST 时会验证证书，域名不匹配 → POST 失败。

**需要手动操作**：在证书提供商（TrustAsia）重新签发或添加 SAN，使证书覆盖 `qingfei.szwbww.com`。
或者申请通配符证书 `*.szwbww.com`。

> **不阻塞后续步骤**：即使证书不匹配，`mailto:` fallback 退订仍有效，HTTP 浏览器退订也能用。
> Agent 可先执行步骤 1-6，证书修复后 one-click 自动生效。

### 步骤 1：配置 UNSUBSCRIBE 环境变量

SSH 到服务器 `root@150.158.92.103`，编辑 setenv.sh：

```bash
# 生成 secret
SECRET=$(openssl rand -hex 32)
echo "生成的 secret: $SECRET"

# 追加到 setenv.sh
cat >> /opt/apache-tomcat-9.0.71/bin/setenv.sh << EOF

# Unsubscribe configuration (added 2026-07-03)
export UNSUBSCRIBE_BASE_URL='https://qingfei.szwbww.com/talent'
export UNSUBSCRIBE_SECRET='$SECRET'
EOF
```

> 注意 URL 末尾带 `/talent`（Tomcat context path），不带末尾斜杠。
> 生成的退订链接形如：`https://qingfei.szwbww.com/talent/u/unsubscribe?token=...`

### 步骤 2：降低发送参数

```bash
mysql -u root -proot talent_introduction << 'SQL'
UPDATE batch_send_setting SET setting_value = '60'     WHERE setting_key = 'batchSend.dailyCap';
UPDATE batch_send_setting SET setting_value = '30'     WHERE setting_key = 'batchSend.roundSize';
UPDATE batch_send_setting SET setting_value = '3000'   WHERE setting_key = 'batchSend.perMailIntervalMs';
UPDATE batch_send_setting SET setting_value = '120000' WHERE setting_key = 'batchSend.perRoundIntervalMs';

-- 确认
SELECT setting_key, setting_value FROM batch_send_setting
WHERE setting_key IN ('batchSend.dailyCap','batchSend.roundSize','batchSend.perMailIntervalMs','batchSend.perRoundIntervalMs');
SQL
```

### 步骤 3：重启 Tomcat 使 Unsubscribe 生效

```bash
cd /opt/apache-tomcat-9.0.71
./bin/shutdown.sh && sleep 5 && ./bin/startup.sh
```

### 步骤 4：验证

```bash
# 4a. 退订端点可达（服务器本地）
curl -s "http://127.0.0.1/talent/u/unsubscribe?token=test"
# 期望：返回 "invalid link"（400 状态码）

# 4b. 退订端点可达（外部域名）
curl -s "https://qingfei.szwbww.com/talent/u/unsubscribe?token=test"
# 期望：同上（如果证书已修复；否则用 http:// 验证）

# 4c. batch_send_setting 已更新
mysql -u root -proot talent_introduction -e "SELECT setting_key, setting_value FROM batch_send_setting WHERE setting_key LIKE 'batchSend.%' ORDER BY setting_key;"
# 期望：dailyCap=60, roundSize=30, perMailIntervalMs=3000, perRoundIntervalMs=120000

# 4d. 确认 setenv.sh 包含 UNSUBSCRIBE 配置
grep UNSUBSCRIBE /opt/apache-tomcat-9.0.71/bin/setenv.sh
# 期望：显示 UNSUBSCRIBE_BASE_URL 和 UNSUBSCRIBE_SECRET
```

### 步骤 5：DNS — 子域名 SPF 记录补全

当前只有主域名 `szwebotech.cn` 有 SPF 记录，子域名没有独立 SPF。需要在 DNS 管理面板给每个发信子域名添加 TXT 记录：

```
talents.szwebotech.cn     TXT  "v=spf1 include:spf.mail.qq.com ~all"
mail.szwebotech.cn        TXT  "v=spf1 include:spf.mail.qq.com ~all"
（新子域名）               TXT  "v=spf1 include:spf.mail.qq.com ~all"
```

验证：
```bash
dig +short TXT talents.szwebotech.cn
dig +short TXT mail.szwebotech.cn
# 每条应返回 SPF 记录
```

### 步骤 6：确认新子域名账号 warmup 配置

```bash
mysql -u root -proot talent_introduction -e "
SELECT account_code, sender_email, daily_send_limit, enabled, warmup_enabled, warmup_started_at
FROM mail_sender_account
WHERE enabled = 1
ORDER BY account_code;"
```

确认新账号：
- `enabled = 1`
- `warmup_enabled = 1`
- `daily_send_limit` ≤ 5（初始值）
- `warmup_started_at` 已设

---

## 不做的事

- 不改 `batchSend.emailDomain`（保持 gmail.com，先解决投诉率）
- 不改 `batchSend.runtimeStatus`（保持 PAUSED，等 Postmaster 数据回落后人工恢复）
- 不改现有账号的 `daily_send_limit`（LiLei/WuWei_WB 已经是 30）
- 不动代码，纯运维配置变更

## 恢复发送的前提

1. Postmaster 投诉率连续 3 天 < 0.3%
2. List-Unsubscribe 已验证生效
3. DNS SPF 记录已补全
4. 手动将 `batchSend.runtimeStatus` 改为 `IDLE`

## 风险

| 风险 | 应对 |
|------|------|
| SSL 证书不覆盖 qingfei 子域名 | mailto fallback 有效，需手动重签证书覆盖该子域名 |
| 重启 Tomcat 短暂中断 | 选择低峰时段操作，重启耗时约 10 秒 |
| 子域名 SPF 继承主域名不一定被所有 ESP 认可 | 显式添加子域名 SPF 记录 |
