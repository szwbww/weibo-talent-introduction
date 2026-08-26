---
id: K-sbir-gov-geo-blocked
domain: es-index
created: 2026-08-25
last_used: 2026-08-25
hit_count: 0
source: create-p:00-rnd-gate-master
severity: P1
---

**sbir.gov 对本项目的出口做地域/IP 级拒绝**，不是维护、不是缺 API key、不是路径错误。
排查它耗掉了整整五轮，结论与前四轮的每一个猜测都不同，记在这里免得重来。

## 定性证据（2026-08-25，生产服务器 150.158.92.103 实测）

| 观察 | 排除了什么 |
|---|---|
| 故意写错的路径 `/this/path/does/not/exist/xyz123` **也**返回 403 | 不是路径问题 |
| 响应头 `x-amzn-ErrorType: ForbiddenException` + `x-amz-apigw-id` | 是 API Gateway 在拒，不是应用返回 |
| 主站 `www.sbir.gov` 同样 403，但后端是 `Server: awselb/2.0` + nginx 式 HTML 错误页 | **两套不同基础设施给出同样拒绝** → 只能是共同的上游访问策略 |
| `X-Amz-Cf-Pop: HKG61-P1` | 请求经 CloudFront 香港边缘出去 |
| 浏览器 UA、`Referer`、换本地代理端口均无效 | 不是 UA / 指纹 / 单一出口 IP 问题 |

## 症状会随出口不同而变，别被误导

- 本地 Mac（mihomo fake-ip，域名落到 DIRECT 兜底）→ `curl: (35) SSL_ERROR_SYSCALL`，`HTTP_STATUS=000`
- 生产服务器（全局 `http_proxy=127.0.0.1:7890`，出口香港）→ **HTTP 403 + JSON 错误体**

**`000` 与 `403` 是完全不同的性质**：前者连接没建立，后者服务端已响应并拒绝。
排查脚本里把 403 写成「未触达服务端」是措辞缺陷，会把人引向错误方向（已修）。

## 判读口诀

排查外部 API 不通时，先分三层，不要跳步：
1. `%{http_code}` 是不是 `000` —— 是则网络层，否则应用层，两者结论不可互推
2. 故意写一个不存在的路径做**对照** —— 若同样被拒，与路径无关
3. 看响应头的 `Server` / `x-amzn-*` / `Via` —— 能直接区分是 CDN、WAF、API Gateway 还是应用在拒

## 架构结论

即使换美国出口节点能通，**生产定时任务永久依赖「代理节点在线且落在允许地区」**，
对每日定时拉取来说过于脆弱，且大概率静默失败
（参见 [[K-openalex-has-no-patent-data]]：`OPENALEX_FETCH_PATENTS_ENABLED` 查了两年空数据没人发现）。

因此 **SBIR 与 PatentsView 都应走离线数据摄入，不做实时 API 源**：
它们是存量档案（历年资助记录、专利），不是持续更新的流；
一次下载 → 本地索引 → 运行时零网络依赖；更新是可控运维动作。
OpenAlex / Crossref / EuropePMC 保持实时源是对的——它们本来就通，且是持续更新的文献流。

关联：[[K-sbir-awards-api-no-pi-title]]、[[K-openalex-has-no-patent-data]]
