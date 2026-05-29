# Multi-AI Kit · 完整部署说明

> 面向用户的端到端操作手册。配合 `multi-ai-kit-deploy-plan.md`（架构与实施规范）一起使用。
> 本文所有路径基于 `HOME=/Users/lukai`，macOS。其他系统需对应调整。

---

## 0. 部署前清单

**Kit 默认走 CLI 模式**，优先复用你已登录的各家 CLI 订阅，仅在 CLI 不可用时回退到 API。所以你不一定需要全部 4 个 API key。

在开始之前，确认你有：

- [ ] 至少装好了想用的 CLI 并已登录：
  - `claude`（Claude Code）—— `claude login` 已完成
  - `codex`（OpenAI Codex CLI）—— `codex login` 已完成（复用 ChatGPT Plus/Pro 订阅额度）
  - `agy`（Google Antigravity CLI，承担 Gemini 前端优化角色）—— OAuth 登录已完成
  - DeepSeek **没有官方免登 CLI**，必须 API key
- [ ] 需要的 API key（**按"未装/未登录的 CLI"准备即可**）：
  - DeepSeek（必须）：https://platform.deepseek.com/
  - Anthropic（如果不用 Claude Code 订阅）：https://console.anthropic.com/
  - OpenAI（如果不用 Codex CLI 订阅）：https://platform.openai.com/
  - Google AI Studio（如果不用 Gemini CLI 订阅）：https://aistudio.google.com/
- [ ] 一台 macOS 或 Linux，能联网
- [ ] 已装 Git ≥ 2.30、Python ≥ 3.9
- [ ] 一个用于存放 kit 的 Git 仓库地址（建议 GitHub 私有仓库），下文记作 `<KIT_REPO_URL>`

**最省钱推荐组合**：Claude + GPT(Codex) + Gemini 三家都走 CLI 订阅（0 API 成本），**只有 DeepSeek 需要 API key**。

> 注：Codex CLI 在 headless 调用时一般用 `codex exec "<prompt>"` 或 `codex --prompt-file <path>`；具体子命令以你本机 `codex --help` 为准，install.sh 会探测可用形态并写入 `.env` 的 `GPT_CLI_EXEC_ARGS`。

---

## 1. 一键安装（推荐路径）

### 1.1 最简形式

```bash
curl -fsSL https://raw.githubusercontent.com/<you>/claude-multi-ai-kit/main/install.sh | bash
```

安装脚本会顺序执行：
1. 前置检查（git / python / jq）
2. clone kit 到 `/Users/lukai/.claude-multi-ai-kit`
3. 创建 venv：`/Users/lukai/.claude-multi-ai-kit/.venv`
4. 部署文件到 `/Users/lukai/.claude/{scripts,agents,commands}`
5. **CLI 探测**：自动查找 `claude` / `gemini` / `codex` 等已登录 CLI，找到的 provider 默认 `*_MODE=cli`
6. **按缺失补 API key**：只对 `*_MODE=sdk` 的 provider 交互式询问 key（找到 CLI 的就跳过）
7. 合并权限到 `/Users/lukai/.claude/settings.json`
8. 写 `/Users/lukai/bin/cship` 快捷命令
9. 生成 `/Users/lukai/.claude/kit.lock`
10. 跑 smoke test

预计耗时 1–2 分钟。

### 1.2 更安全的形式（先看后跑）

```bash
curl -fsSL https://raw.githubusercontent.com/<you>/claude-multi-ai-kit/main/install.sh \
  -o /tmp/install.sh
less /tmp/install.sh                  # 审阅脚本
bash /tmp/install.sh
```

### 1.3 离线 / 已 clone 仓库的形式

```bash
git clone <KIT_REPO_URL> /Users/lukai/.claude-multi-ai-kit
bash /Users/lukai/.claude-multi-ai-kit/install.sh
```

---

## 2. 交互式问答流程（install 中你会被问到的）

```
[CLI 探测]
  [✓] 发现 claude  CLI: /usr/local/bin/claude       → CLAUDE_MODE=cli
  [✓] 发现 codex   CLI: /usr/local/bin/codex        → GPT_MODE=cli
  [✓] 发现 agy     CLI: /Users/lukai/.local/bin/agy  → GEMINI_MODE=cli
  [!] DeepSeek 无官方 CLI                           → DEEPSEEK_MODE=sdk

[补 API key]（仅 *_MODE=sdk 的会问）
  DeepSeek API key (sk-...):       ▊
  跳过 Anthropic（已检测到 claude CLI）
  跳过 OpenAI   （已检测到 codex  CLI）
  跳过 Gemini   （已检测到 agy    CLI）

将向 /Users/lukai/.zshrc 追加：
  export PATH="/Users/lukai/bin:$PATH"
确认？ [Y/n]

将向 /Users/lukai/.claude/settings.json 合并以下权限：
  - Bash(/Users/lukai/bin/cship:*)
  - Bash(/Users/lukai/.claude-multi-ai-kit/.venv/bin/python:*)
  - Bash(git apply:*)
  - Bash(git apply --check:*)
确认？ [Y/n]
```

任一 key 跳过填写直接回车即可，对应 provider 会被标记 `disabled=true`，后续可在 `/Users/lukai/.claude/.env` 补。

---

## 3. 验证安装

### 3.1 立即验证

```bash
# 重载 PATH
source /Users/lukai/.zshrc

# 版本与体检
cship --version
/Users/lukai/.claude-multi-ai-kit/doctor.sh
```

预期 doctor 输出全绿：

```
[✓] Kit version           : 1.0.0
[✓] Python venv           : 3.11.x OK
[✓] Symlink scripts/      : OK
[✓] Agents installed      : 3/3
[✓] Command ship.md       : OK
[✓] Planner   (claude)    : MODE=cli  CLI=/usr/local/bin/claude  OK
[✓] Coder     (deepseek)  : MODE=sdk  KEY=*****  OK
[✓] Reviewer  (gpt)       : MODE=cli  CLI=/usr/local/bin/codex   OK
[✓] Frontend  (gemini/agy): ENABLED=true  MODE=cli  CLI=/Users/lukai/.local/bin/agy  OK
[✓] settings.json perms   : OK
[✓] cship in PATH         : /Users/lukai/bin/cship
[✓] Last smoke test       : passed
```

- multi-ai smoke test: run `cship --dry-run "smoke test"` to verify multi-agent planning/review integration, or use `doctor.sh` for a full health check.

### 3.2 端到端跑一次

```bash
cd /Users/lukai/IdeaProjects/weibo-talent-introduction
cship "在 README 末尾追加一行测试文字"
```

正常情况下你会看到 4 段输出：
1. Planner 写计划 → `docs/plans/<feature>.md`
2. Coder 出 diff → 自动 `git apply`
3. Reviewer 审查 → `docs/reviews/<feature>.md`
4. 测试结果汇总

跑完后 `git status` 应该有改动且未自动 commit（默认保守模式）。

---

## 4. 文件分布速查

安装完成后的所有产物：

| 路径 | 作用 | 是否可手动改 |
|------|------|---------------|
| `/Users/lukai/.claude-multi-ai-kit/` | Kit 本体（git 仓库） | 用 `upgrade.sh`，不要手改 |
| `/Users/lukai/.claude-multi-ai-kit/.venv/` | Python 虚拟环境 | 不要手改 |
| `/Users/lukai/.claude/scripts` | → symlink 到 kit/scripts | 不要手改 |
| `/Users/lukai/.claude/agents/*.md` | 3 个子 Agent 定义 | 可改，升级时会询问 |
| `/Users/lukai/.claude/commands/ship.md` | /ship 命令定义 | 可改 |
| `/Users/lukai/.claude/.env` | API keys + 角色路由 + 模型版本 | **常改**，见 §5 |
| `/Users/lukai/.claude/settings.json` | Claude Code 权限 | 可改 |
| `/Users/lukai/.claude/kit.lock` | 安装清单（sha256） | 不要手改 |
| `/Users/lukai/.claude/logs/` | 各 CLI 日志 | 可定期清理 |
| `/Users/lukai/bin/cship` | 命令行快捷入口 | 不要手改 |

---

## 5. 日常配置（`.env`）

打开 `/Users/lukai/.claude/.env`，结构如下：

```bash
# ===== 角色 → Provider 路由 =====
PLANNER_PROVIDER=claude
CODER_PROVIDER=deepseek
REVIEWER_PROVIDER=gpt
FRONTEND_PROVIDER=gemini

# ===== 前端优化开关 =====
# true  → coder 写完后再让 Gemini(agy) 做一轮前端优化
# false → 不调 Gemini，前端代码完全由 DeepSeek 按 plan §4 写
FRONTEND_ENABLED=true

# ===== 每个 Provider 的调用模式（cli = 走本机 CLI 订阅；sdk = 走 API key）=====
CLAUDE_MODE=cli
GPT_MODE=cli
GEMINI_MODE=cli
DEEPSEEK_MODE=sdk

# ===== CLI 路径（install.sh 自动探测填入）=====
CLAUDE_CLI_PATH=/usr/local/bin/claude
GPT_CLI_PATH=/usr/local/bin/codex
GEMINI_CLI_PATH=/Users/lukai/.local/bin/agy
DEEPSEEK_CLI_PATH=

# ===== API Keys（仅 *_MODE=sdk 时使用，可留空）=====
ANTHROPIC_API_KEY=
OPENAI_API_KEY=
DEEPSEEK_API_KEY=sk-...
GEMINI_API_KEY=

# ===== 模型版本 =====
CLAUDE_MODEL=claude-opus-4-7
GPT_MODEL=gpt-4.1
DEEPSEEK_MODEL=deepseek-coder
GEMINI_MODEL=gemini-2.5-pro

# ===== 可选：自定义 API endpoint =====
ANTHROPIC_BASE_URL=
OPENAI_BASE_URL=
DEEPSEEK_BASE_URL=
GEMINI_BASE_URL=

# ===== 行为开关 =====
AI_DEBUG=0                 # 1 = 打印完整 prompt 到日志
AI_TIMEOUT=120             # 单次调用超时（秒）
AI_MAX_RETRY=3
```

### 5.1 常见调整场景

**场景 A · 换某角色的模型版本**

```bash
DEEPSEEK_MODEL=deepseek-chat        # 改这一行即可
```

**场景 B · 换某角色背后的家**

```bash
REVIEWER_PROVIDER=claude            # 审查从 GPT 切到 Claude
```

**场景 C · CLI 订阅额度用完，临时切回 API**

```bash
CLAUDE_MODE=sdk
ANTHROPIC_API_KEY=sk-ant-...        # 补上 key
```

**场景 D · 临时一次性切换**

```bash
REVIEWER_PROVIDER=claude CLAUDE_MODE=cli cship "审查这次改动"
```

不修改 `.env`，仅本次生效。

**场景 E · 关闭前端优化角色（让 DeepSeek 直接写前端）**

```bash
FRONTEND_ENABLED=false              # 不调 Gemini/agy，前端由 coder 一并写
```

或单次：

```bash
cship --no-frontend "加 OAuth 登录"
cship --frontend    "加 OAuth 登录"   # 强制开
```

**场景 F · 新装了某家 CLI，想从 sdk 切到 cli**

```bash
# 重跑探测即可，会自动更新 .env 里 *_MODE 和 *_CLI_PATH
/Users/lukai/.claude-multi-ai-kit/install.sh --redetect-cli
```

---

## 6. 日常使用

### 6.1 命令行入口

```bash
cd /Users/lukai/IdeaProjects/<任意项目>

# 完整工作流（按 FRONTEND_ENABLED 决定是否调 Gemini/agy）
cship "加 OAuth 登录"

# 临时切换前端开关
cship --no-frontend "加 OAuth 登录"
cship --frontend    "加 OAuth 登录"

# 只跑某一步
cship --only planner  "设计导出功能"
cship --only coder    --plan docs/plans/export.md
cship --only reviewer --target HEAD~1..HEAD
cship --only frontend --files src/main/resources/static/index.html

# 干跑（不调用 API，只检查链路）
cship --dry-run "demo"
```

### 6.2 Claude Code 入口（slash command）

在 Claude Code 会话里：

```
/ship 加 OAuth 登录
```

行为与 `cship` 一致，但由 Claude Code 主导编排（可对话式调整计划）。

### 6.3 关于开发计划的详细度（重要）

Planner（Claude）输出的 `docs/plans/<feature>.md` 必须遵循固定模板，**§4 前端实现步骤要求最高**，强制展开为 7 个小节：

1. 4.1 页面 / 组件清单
2. 4.2 UI 结构 & 状态机
3. 4.3 交互细节（三态、表单、无障碍）
4. 4.4 样式规范（design token、响应式、暗色）
5. 4.5 接口约定（含 mock 示例）
6. 4.6 性能 / 体积
7. 4.7 前端验收清单

理由：当 `FRONTEND_ENABLED=false` 时，前端代码完全由 DeepSeek 按计划写——计划写多细，前端就有多稳。具体模板和 system prompt 注入规则见 `multi-ai-kit-deploy-plan.md` §3.5.2。

### 6.4 新项目增强配置（可选）

在项目根放 `CLAUDE.md`：

```markdown
# 项目元信息

test_command: mvn test -pl core
build_command: mvn package -DskipTests
frontend_paths:
  - src/main/resources/static
exclude_paths:
  - target
```

`cship` 会优先读这个文件来决定测试命令、前端目录范围等。

---

## 7. 升级

```bash
/Users/lukai/.claude-multi-ai-kit/upgrade.sh
```

脚本会：
1. `git fetch` 显示新版 CHANGELOG，等你确认
2. `git pull` 更新代码（symlink 的 scripts 立即生效）
3. `pip install --upgrade` 同步 Python 依赖
4. 按 `kit.lock` 比对 `agents/`、`commands/` 是否被你改过
   - 未改 → 直接覆盖
   - 已改 → 询问 `[k]eep / [o]verwrite / [b]ackup-and-overwrite`
5. 重新合并 `settings.json` 权限片段（幂等）
6. 跑 smoke test
7. 更新 `kit.lock` 的版本号

如果升级中断或失败，`kit.lock` 不会变更，可以重跑 `upgrade.sh`。

---

## 8. 健康检查与排错

### 8.1 体检

```bash
/Users/lukai/.claude-multi-ai-kit/doctor.sh
```

### 8.2 看日志

```bash
ls -lt /Users/lukai/.claude/logs/ | head
# 示例：deepseek-add-oauth-20260528-143012.log
```

每条日志包含：开始时间、参数、prompt 摘要（默认脱敏）、响应摘要、token 用量、退出码。

### 8.3 常见问题

| 现象 | 原因 | 解决 |
|------|------|------|
| `cship: command not found` | PATH 未刷新 | `source /Users/lukai/.zshrc` |
| `git apply --check failed` | DeepSeek 生成的 diff 行号偏移 | 自动回灌重试 2 次；仍失败看 `/Users/lukai/.claude/logs/` |
| `OPENAI_API_KEY missing` | `.env` 缺 key | 打开 `/Users/lukai/.claude/.env` 补 |
| `claude CLI not found, falling back to sdk but no API key` | CLI 失效且未填 key | `claude login` 重登；或填 `ANTHROPIC_API_KEY` 并改 `CLAUDE_MODE=sdk` |
| `claude CLI session expired` | OAuth 过期 | 跑 `claude login` 重新登录 |
| `permission denied: cship` | wrapper 没可执行位 | `chmod +x /Users/lukai/bin/cship` |
| Claude Code 里 `/ship` 找不到 | agents/commands 没装 | 重跑 `install.sh`，或检查 `/Users/lukai/.claude/agents/` |
| 模型返回 429 | 限流 | 调小并发，或在 `.env` 加 `AI_MAX_RETRY=5` |
| 想看完整 prompt | 默认脱敏 | `AI_DEBUG=1 cship "..."` |
| 新装了 gemini CLI 想用上 | `.env` 仍是 sdk | `install.sh --redetect-cli` 重探测 |

---

## 9. 卸载

```bash
/Users/lukai/.claude-multi-ai-kit/uninstall.sh
```

脚本会：
1. 按 `kit.lock` 删除安装过的文件，校验 sha256 未被改过
2. 询问是否删除 `/Users/lukai/.claude-multi-ai-kit/`（kit 仓库本体）
3. 询问是否删除 `/Users/lukai/.claude/.env`（**默认保留**，含 API key）
4. 从 `/Users/lukai/.claude/settings.json` 移除 kit 写入的权限
5. 删除 `/Users/lukai/bin/cship`
6. 从 `/Users/lukai/.zshrc` 移除 PATH 注入行

完全卸载后机器干净，不留残留。

---

## 10. 换机器迁移

新机器上：

```bash
# 1. 重新跑一键安装
curl -fsSL https://raw.githubusercontent.com/<you>/claude-multi-ai-kit/main/install.sh | bash

# 2. 若旧机器的 .env 你想直接搬运
scp old-host:/Users/lukai/.claude/.env /Users/lukai/.claude/.env
chmod 600 /Users/lukai/.claude/.env

# 3. 验证
/Users/lukai/.claude-multi-ai-kit/doctor.sh
```

新机器若 `HOME` 不是 `/Users/lukai`（比如 `/home/alice`），`install.sh` 会自动改写所有写入文件中的绝对路径，无需手动改。

---

## 11. 团队复用

如果队友也要装：

1. 把 `<KIT_REPO_URL>` 共享给队友（私有仓库要加协作者）
2. 队友执行 §1.1 的一条命令即可
3. 队友需要自己申请 4 家的 API key
4. 若公司有统一的 API 网关，可在 `.env` 里通过 `*_BASE_URL` 覆盖各家默认端点（CLI 实现已预留）

---

## 12. 一句话回顾

| 操作 | 命令 |
|------|------|
| 装 | `curl … install.sh \| bash` |
| 用 | `cship "..."` 或 Claude Code 里 `/ship ...` |
| 改模型 | 改 `/Users/lukai/.claude/.env` 一行 |
| 切 CLI ↔ API | 改 `/Users/lukai/.claude/.env` 里 `*_MODE` |
| 开/关前端优化 | `FRONTEND_ENABLED=true/false` 或 `cship --no-frontend` |
| 重探测 CLI | `install.sh --redetect-cli` |
| 升级 | `/Users/lukai/.claude-multi-ai-kit/upgrade.sh` |
| 体检 | `/Users/lukai/.claude-multi-ai-kit/doctor.sh` |
| 卸载 | `/Users/lukai/.claude-multi-ai-kit/uninstall.sh` |
