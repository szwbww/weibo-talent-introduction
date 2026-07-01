# LibreTranslate 离线翻译服务部署方案

> 目标：在 `150.158.92.103` 上以 Docker 部署 LibreTranslate（基于 Argos Translate 的开源离线翻译引擎），为 weibo-talent-introduction 提供英→中邮件正文翻译能力。完全离线、零调用费、数据不出本地。
> 执行者：运维 / 自动化 agent。所有命令在目标服务器以 `root`（或具备 docker 权限的用户）执行。

---

## 0. 前置条件 / 环境要求

- OS：Linux x86_64（Ubuntu 20.04+ / CentOS 7+ / Debian 11+ 均可）。
- 内存：**≥ 2GB 可用**（仅加载 en/zh 模型）。低于 2GB 易 OOM。
- 磁盘：≥ 3GB 空闲（镜像 + 语言模型）。
- 网络：服务器**首次部署需能访问公网**下载镜像和语言模型；之后可断网运行。
- 端口：容器内 5000。**不要对公网放开**，仅供内网/本机 Spring 应用访问。

执行前先核对：

```bash
uname -m                 # 期望 x86_64
free -m                  # 查看可用内存
df -h /                  # 查看磁盘
cat /etc/os-release      # 确认发行版
```

---

## 1. 安装 Docker（已安装则跳过）

```bash
# 检查是否已装
docker --version || curl -fsSL https://get.docker.com | sh

# 启动并设置开机自启
systemctl enable --now docker

# 验证
docker run --rm hello-world
```

---

## 2. 部署 LibreTranslate

使用 docker compose 便于后续维护。创建目录与配置：

```bash
mkdir -p /opt/libretranslate && cd /opt/libretranslate
```

写入 `/opt/libretranslate/docker-compose.yml`：

```yaml
services:
  libretranslate:
    image: libretranslate/libretranslate:latest
    container_name: libretranslate
    restart: unless-stopped
    # 仅监听本机回环，杜绝公网暴露；若 Spring 在另一台内网机，改成 内网IP:5000:5000
    ports:
      - "127.0.0.1:5000:5000"
    environment:
      # 只下载英文与中文模型，显著降低内存与磁盘占用
      LT_LOAD_ONLY: "en,zh"
      # 关闭前端演示页对外暴露的额外功能（可选）
      LT_DISABLE_WEB_UI: "false"
    volumes:
      # 持久化语言模型，避免重启重新下载
      - lt-models:/home/libretranslate/.local/share/argos-translate
    healthcheck:
      test: ["CMD-SHELL", "python3 -c \"import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:5000/languages').status==200 else 1)\""]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 180s

volumes:
  lt-models:
```

> 说明：`LT_LOAD_ONLY: "en,zh"` 限定语言包。若专家来信可能为德/法/西等多语种，改为 `"en,zh,de,fr,es"` 等；留空则全量下载（数 GB，谨慎）。

启动：

```bash
cd /opt/libretranslate
docker compose up -d
```

---

## 3. 等待模型下载并确认就绪

首次启动会下载语言模型，**可能持续几分钟**，期间接口尚未可用。

```bash
# 跟踪日志，直到出现 "Running on http://0.0.0.0:5000"
docker compose logs -f libretranslate
# 看到 running 后按 Ctrl-C 退出日志

# 确认容器健康
docker ps --filter name=libretranslate
```

---

## 4. 功能验证

```bash
# 4.1 列出已加载语言（应包含 en 和 zh）
curl -s http://127.0.0.1:5000/languages

# 4.2 英译中冒烟测试
curl -s -X POST http://127.0.0.1:5000/translate \
  -H 'Content-Type: application/json' \
  -d '{"q":"I am very interested in this opportunity and would like to schedule a meeting.","source":"en","target":"zh","format":"text"}'
# 期望返回类似：{"translatedText":"我对这个机会很感兴趣，想安排一次会议。"}

# 4.3 自动识别源语言（source=auto）
curl -s -X POST http://127.0.0.1:5000/translate \
  -H 'Content-Type: application/json' \
  -d '{"q":"Thank you for reaching out.","source":"auto","target":"zh","format":"text"}'
```

验收标准：4.1 返回的 JSON 含 `"code":"zh"` 与 `"code":"en"`；4.2 / 4.3 返回非空 `translatedText` 中文。

---

## 5. 安全加固

```bash
# 5.1 确认未对公网暴露：端口应绑定在 127.0.0.1
ss -tlnp | grep 5000     # 期望 127.0.0.1:5000

# 5.2 若 Spring 应用在另一台内网机器，需放开内网访问：
#     - 修改 compose 端口为 "内网IP:5000:5000" 或 "0.0.0.0:5000:5000"
#     - 同时用防火墙/安全组仅放行内网网段，严禁公网 0.0.0.0/0 放行 5000
# 云厂商安全组：5000 入站仅允许应用服务器内网 IP

# 5.3 （可选）启用 API Key 鉴权
#     compose 中追加 environment: LT_API_KEYS: "true"，command: ["--api-keys"]
#     之后调用需带 ?api_key=xxx
```

---

## 6. Spring 应用接入（weibo-talent-introduction 侧，供参考）

> 服务部署完成后，应用侧需要的配置。此部分由代码改造任务负责，部署 agent 只需确保 base-url 可达。

`application.yml` 新增：

```yaml
talent-introduction:
  translation:
    enabled: true
    base-url: http://127.0.0.1:5000   # 或内网IP:5000
    source: en          # 或 auto
    target: zh
    timeout-ms: 5000
    api-key:            # 启用鉴权时填写
```

应用通过 `MailTranslationService` POST `${base-url}/translate`，对专家回信 `cleanedBody` 翻译后落库（`cleaned_body_zh`）。**翻译失败必须 try-catch 不阻断主流程**。

连通性自测（从应用服务器执行）：

```bash
curl -s -X POST http://<LibreTranslate地址>:5000/translate \
  -H 'Content-Type: application/json' \
  -d '{"q":"hello","source":"en","target":"zh","format":"text"}'
```

---

## 7. 运维 / 常用命令

```bash
cd /opt/libretranslate

docker compose ps                 # 状态
docker compose logs --tail=100    # 日志
docker compose restart            # 重启
docker compose down               # 停止并移除容器（模型 volume 保留）
docker compose pull && docker compose up -d   # 升级到最新镜像
```

---

## 8. 故障排查

| 现象 | 排查 |
|---|---|
| 启动后 curl 连不上 | 模型仍在下载，看 `docker compose logs -f`，等到 "Running on" |
| 容器反复重启 / 被 kill | 内存不足（OOM），`dmesg | grep -i oom` 确认；扩内存或减少 `LT_LOAD_ONLY` 语言数 |
| `/languages` 不含 zh | `LT_LOAD_ONLY` 写错或模型未下全；`docker compose down` 后删 volume 重来：`docker volume rm libretranslate_lt-models` |
| 翻译质量差 | 正常现象（开源模型弱于 DeepL）；专业措辞偏差可接受，运营看大意即可 |
| 首次下载失败 | 服务器无公网/被墙，检查出网；下载完成后才可断网运行 |

---

## 9. 回滚

```bash
cd /opt/libretranslate
docker compose down               # 停服
# 应用侧将 talent-introduction.translation.enabled 置 false 即可关闭翻译功能
# 彻底清理（含模型）：
docker compose down -v
docker volume rm libretranslate_lt-models 2>/dev/null || true
```

---

## 交付检查清单（部署 agent 自检）

- [ ] `docker ps` 中 libretranslate 状态 healthy
- [ ] `/languages` 返回含 en、zh
- [ ] 英译中 curl 返回非空中文
- [ ] 端口绑定 127.0.0.1（或仅内网安全组放行），公网不可达
- [ ] `restart: unless-stopped` 已生效（`docker inspect` 验证）
- [ ] 应用服务器可访问 base-url
