# 邮件打开跟踪：上线后无信号排查

时间：2026-09-26，Asia/Shanghai。范围：原测试邮件、历史成功实验、单封三路对照。未修改业务代码、未部署、未重启。

## 结论与边界

已定位直接故障：Gmail 发起图片请求，但 Google 图片代理返回 HTTP 404（HTML），没有向应用产生可见的对应 GET，因此没有打开信号。不是“邮件没有植入图片”或“用户没有点击图片”。

历史实验确实成功；目前用同类静态图片重新测试也失败，所以不能把静态改动态认定为根因。证据指向 Google 图片代理取图链路异常，尚不能进一步区分代理端拒绝/失败、DNS/TLS、上游网络或云侧拦截。不能据此宣布发件域名被 Gmail 风控，亦不能宣布全部跟踪代码已端到端验证通过。

## 历史成功证据

- 原记录：`docs/plans/2026-09-25/mail-open-tracking-live-test.md`。
- 账号 LuKai，发件域名 updates.szwebotech.cn；2026-09-25 17:09:50 SMTP 接受。
- 图片为 Tomcat ROOT 下独立随机路径的 42 字节静态 GIF。
- 服务器访问日志在 2026-09-25 17:11:26 记录来源 `66.249.90.104` 的唯一图片 GET，HTTP 200，42 字节。实验者未访问该邮件专属图片。
- 该临时目录后来已清理，不能通过再次打开旧信复现实验。

## 原上线邮件证据

- mail_record=7194，mail_open_tracking=1；账号 LuKai_QF，发件域名 qingfeitalent.com；00:46:15 发送。
- 主题：Electrocatalysts for Energy Conversion: a research collaboration enquiry。
- Gmail 原始邮件中存在正确生产 HTTPS 像素 URL；SPF、DKIM、DMARC 均 PASS。
- Gmail 设置为始终显示外来图片；渲染后的 IMG 地址被改写到 ci3.googleusercontent.com/meips/。
- Chrome Network：该代理请求约 50.19 秒后 HTTP 404，Content-Type 为 text/html; charset=UTF-8，Server 为 fife；随后显示 net::ERR_BLOCKED_BY_ORB。再次加载约 50.48 秒仍失败。ORB 是错误响应不能作为图片使用的表现，不应据此倒推像素被邮件正文清理。
- Tomcat 日志没有该 token 的 GET；此前 00:52:39 的 HEAD 属于诊断，不计信号。
- 本轮结束查询：tracking=1 的 first_open_at、last_open_at 仍 NULL；mailOpenTracking.enabled=true。

## 单封三路对照

用户“继续”后，仅发一封：`Mail tracking diagnostic comparison - 4b03c810`。SMTP 接受：01:15:49。账号仍为 LuKai_QF；同一收件人、同一邮件、同一域名、三个独立地址。

| 路径类别 | 内容 | Gmail 图片代理 | 普通 HTTPS 探针 |
| --- | --- | --- | --- |
| ROOT/static42.gif | 与历史实验相同的 42 字节 GIF | 404 | 200 / image/gif / 42 B / 0.156 s |
| ROOT/static51.gif | 与上线接口相同的 51 字节 GIF | 404 | 200 / image/gif / 51 B / 0.155 s |
| /talent/t/mail-open/独立诊断标识.gif | 正式 Controller，使用无效 token | 404 | 200 / image/gif / 51 B / 0.163 s |

执行细节与限制：

- 邮件最初进入垃圾箱，打开后没有像素请求；将这封授权测试邮件标记为非垃圾邮件后，重新打开并刷新，才捕获三个代理请求。Gmail 同时提示未来该发件人邮件将进入收件箱；未修改全局图片设置。
- 三个 Google 代理请求分别点击检查，均为 HTTP 404；第一条响应为 HTML、Server=fife，Date=2026-09-25 17:21:20 GMT。
- 01:21:23–24 的三条源站 GET 全部来自服务器自身 `150.158.92.103`，是明确标记的普通探针；不是 Google 请求，不是用户打开事件。此前没有对照地址访问记录。
- 诊断接口 token 不符合 43 字符校验，因此不会写入打开表。这验证接口取图路径，不能替代合法 token 的数据库写入端到端验收。
- 三路均由代理失败，足以反驳“仅生产 GIF 格式或动态 Controller 才失败”；不证明不存在其他独立缺陷。

## 其他排除与局限

- 普通 HTTPS 请求、TLS 1.2 请求均成功。直接 SNI 证书链验证通过。所查 Google DNS 返回 A=150.158.92.103，无 AAAA。
- HTTPS 由 Tomcat 443 Connector 提供。已检查 server.xml 修改时间早于历史成功实验，没有本次新增 nginx 路由。
- 已查防火墙未见针对历史来源地址的显式封禁；不能代表所有 Google 出口 IP 均放行。
- 短时 SYN 抓包没有识别出 Google 新连接，但抓包未覆盖全部请求生命周期，也不能排除连接复用、未知出口或云侧丢弃，因此不作为网络根因定论。

## 代码依据

- `SmtpMailDeliveryService.kt:27–35`：发送前判断是否跟踪并添加 IMG。
- `MailOpenTrackingController.kt:44–52`：GET 记录信号并返回 GIF；HEAD 仅返回 GIF。
- `MailOpenTrackingService.kt:66–69`：无效 token 直接返回，不写数据。
- `MailOpenTrackingRepository.kt:78`：合法 token 且总开关开启时更新 first/last_open_at。

## 后续定位最小范围

优先取得 Google 回源或云侧网络/防护日志，与同一请求窗口关联；当前代理 404 没有提供上游失败原因。若采用另一公网入口作对照，应先用 Gmail 实测其可达性，再决定是否迁移像素入口。当前证据不支持直接重写跟踪业务逻辑、修改 GIF、关闭安全防护或承诺更换域名即可解决。

临时诊断回执：`/tmp/mail-tracking-compare-receipt.json`。仅包含测试地址与投递信息，不包含 SMTP 密码。

收尾：完成比较后，逐一核验内容并清理本轮两个静态 GIF 和空目录；邮件与业务数据保留。此后重开对照邮件不能作为新的有效静态取图实验，需与上述清理前证据区分。
