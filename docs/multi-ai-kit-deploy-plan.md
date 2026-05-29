# Multi-AI Kit · 一键部署方案

> 目标：把"Opus 计划 / DeepSeek 编码 / GPT 审查 / Gemini 前端"的整套脚手架，做成一个独立仓库 `claude-multi-ai-kit`，新机器一条命令装好，任何新项目零配置即可使用。
> 设计原则：**工具中立**（角色与 provider 解耦）、**全局安装 + 项目零侵入**、**可升级可卸载**、**默认走 CLI 复用订阅，可配置切到 API**。

---

## 1. Kit 仓库结构

仓库名：`claude-multi-ai-kit`（建议放 GitHub 私有仓库）

```
claude-multi-ai-kit/
├── install.sh                     # 一键安装入口
├── uninstall.sh                   # 一键卸载
├── upgrade.sh                     # 一键升级（git pull + 重链）
├── doctor.sh                      # 健康检查（API key / 依赖 / 版本）
├── VERSION                        # 语义化版本号，install 时写入
├── README.md                      # 使用说明
│
├── scripts/                       # 4 个对等 CLI + 编排
│   ├── ai_common.py               # 公共：env / 日志 / 重试
│   ├── planner_cli.py             # 角色：写开发计划（默认 Claude，可切 GPT）
│   ├── deepseek_cli.py            # 角色：写代码
│   ├── gpt_cli.py                 # 角色：测试 + 审查
│   ├── gemini_cli.py              # 角色：前端优化
│   └── ship.py                    # 纯脚本编排器（不依赖 Claude Code）
│
├── agents/                        # Claude Code 适配层（可选）
│   ├── coder-deepseek.md
│   ├── reviewer-gpt.md
│   └── frontend-gemini.md
│
├── commands/                      # Claude Code slash command
│   └── ship.md                    # /ship 转发到 ship.py
│
├── templates/
│   ├── env.example                # API key 模板
│   ├── project-CLAUDE.md          # 新项目接入模板
│   └── settings.json.fragment     # 要并入用户 settings 的权限片段
│
├── requirements.txt               # python 依赖
└── tests/
    ├── smoke_test.sh              # install 后自动跑
    └── fixtures/                  # 最小测试样本
```

---

## 2. 安装目标布局

> 以下所有路径均使用绝对路径。本方案假设 `HOME=/Users/lukai`；若部署到其他用户/机器，install.sh 在运行时通过 `getent passwd` 或 `$HOME` 自动替换，但写入磁盘的所有 wrapper / lock / 日志路径都展开为绝对路径，不留 `~` 或 `$HOME`。

安装后用户机器上的样子：

```
/Users/lukai/.claude/
├── scripts/        → symlink → /Users/lukai/.claude-multi-ai-kit/scripts
├── agents/         ← 拷入 3 个 .md（不覆盖用户已有同名文件）
│   ├── coder-deepseek.md
│   ├── reviewer-gpt.md
│   └── frontend-gemini.md
├── commands/
│   └── ship.md
├── logs/                                       # 各 CLI 运行日志
├── .env                                        # API keys，install 时交互填入
├── settings.json                               # 合并写入权限段
└── kit.lock                                    # 已安装版本/文件清单/checksum

/Users/lukai/.claude-multi-ai-kit/              # kit 仓库 clone 位置
├── .venv/                                      # Python 虚拟环境（隔离依赖）
│   └── bin/python
├── scripts/                                    # 被 ~/.claude/scripts 链接到
├── agents/
├── commands/
├── templates/
├── tests/
├── install.sh
├── upgrade.sh
├── uninstall.sh
├── doctor.sh
└── VERSION

/Users/lukai/bin/cship                          # shell 快捷命令，调用 ship.py
```

**为什么用 symlink + 拷贝混合：**
- `/Users/lukai/.claude/scripts` 用 symlink → `git pull` 自动生效，无需重装
- `/Users/lukai/.claude/agents/*` `/Users/lukai/.claude/commands/*` 用拷贝 → 避免用户改写后被覆盖，升级时按 checksum 比对提示
- `/Users/lukai/.claude/.env` 与 `/Users/lukai/.claude/settings.json` 是用户私有 → 永不动，只做"片段并入"

---

## 3. `install.sh` 详细行为

### 3.1 流程图

```
1. 前置检查 ──→ 2. clone kit ──→ 3. python 环境 ──→ 4. 文件部署
                                                          ↓
5. 健康检查 ←─── 6. smoke test ←─── 5. 交互填 .env ←──── 写 settings.json
```

### 3.2 步骤详解

**Step 1 · 前置检查**

```bash
need git python3 curl
python3 --version >= 3.9
检测是否已安装 → 存在则提示 "已安装 vX.Y，执行 upgrade.sh？"
```

**Step 2 · clone 或更新 kit**

```bash
KIT_DIR=/Users/lukai/.claude-multi-ai-kit
if [ -d "$KIT_DIR/.git" ]; then
  git -C "$KIT_DIR" pull
else
  git clone <repo-url> "$KIT_DIR"
fi
```

**Step 3 · Python 虚拟环境（隔离依赖）**

```bash
python3 -m venv /Users/lukai/.claude-multi-ai-kit/.venv
/Users/lukai/.claude-multi-ai-kit/.venv/bin/pip install \
  -r /Users/lukai/.claude-multi-ai-kit/requirements.txt
```

CLI 脚本通过 wrapper 调用，wrapper 写入绝对路径，强制走 venv：

```bash
# /Users/lukai/bin/cship （install.sh 生成，文件内容如下）
#!/usr/bin/env bash
exec /Users/lukai/.claude-multi-ai-kit/.venv/bin/python \
     /Users/lukai/.claude-multi-ai-kit/scripts/ship.py "$@"
```

**Step 4 · 文件部署**

```bash
mkdir -p /Users/lukai/.claude/agents \
         /Users/lukai/.claude/commands \
         /Users/lukai/.claude/logs

ln -sfn /Users/lukai/.claude-multi-ai-kit/scripts \
        /Users/lukai/.claude/scripts

cp -n /Users/lukai/.claude-multi-ai-kit/agents/*.md \
      /Users/lukai/.claude/agents/

cp -n /Users/lukai/.claude-multi-ai-kit/commands/ship.md \
      /Users/lukai/.claude/commands/ship.md
```

冲突处理：发现同名文件时
- 打印 diff
- 询问 `[k]eep existing / [o]verwrite / [b]ackup-and-overwrite`
- backup 命名：`<file>.bak.<timestamp>`

**Step 5 · `.env` 交互填入**

```bash
ENV_FILE=/Users/lukai/.claude/.env
if [ ! -f "$ENV_FILE" ]; then
  cp /Users/lukai/.claude-multi-ai-kit/templates/env.example "$ENV_FILE"
fi
# 逐项检查，缺失则交互式询问
for KEY in ANTHROPIC_API_KEY OPENAI_API_KEY DEEPSEEK_API_KEY GEMINI_API_KEY; do
  grep -q "^$KEY=." "$ENV_FILE" || prompt_and_append "$KEY"
done
chmod 600 "$ENV_FILE"
```

**Step 6 · `settings.json` 合并**

读 `/Users/lukai/.claude-multi-ai-kit/templates/settings.json.fragment`，用 `jq` 深度合并到 `/Users/lukai/.claude/settings.json`：

```bash
SETTINGS=/Users/lukai/.claude/settings.json
FRAGMENT=/Users/lukai/.claude-multi-ai-kit/templates/settings.json.fragment

[ -f "$SETTINGS" ] || echo '{}' > "$SETTINGS"

jq -s '.[0] * .[1]' "$SETTINGS" "$FRAGMENT" > "$SETTINGS.new"
mv "$SETTINGS.new" "$SETTINGS"
```

权限片段内容（注意：写入的也是绝对路径，不留变量）：

```json
{
  "permissions": {
    "allow": [
      "Bash(/Users/lukai/.claude-multi-ai-kit/.venv/bin/python:*)",
      "Bash(/Users/lukai/bin/cship:*)",
      "Bash(git apply:*)",
      "Bash(git apply --check:*)"
    ]
  }
}
```

**Step 7 · 写 `/Users/lukai/bin/cship` 快捷命令**

```bash
mkdir -p /Users/lukai/bin
cat > /Users/lukai/bin/cship <<'EOF'
#!/usr/bin/env bash
exec /Users/lukai/.claude-multi-ai-kit/.venv/bin/python \
     /Users/lukai/.claude-multi-ai-kit/scripts/ship.py "$@"
EOF
chmod +x /Users/lukai/bin/cship
```

检测 `/Users/lukai/bin` 是否在 `PATH`，若不在则询问用户是否向 `/Users/lukai/.zshrc`（或 `/Users/lukai/.bashrc`）追加：

```bash
export PATH="/Users/lukai/bin:$PATH"
```

**Step 8 · 写 `/Users/lukai/.claude/kit.lock`**

```json
{
  "version": "1.0.0",
  "installed_at": "2026-05-28T12:34:56Z",
  "kit_dir": "/Users/lukai/.claude-multi-ai-kit",
  "venv_python": "/Users/lukai/.claude-multi-ai-kit/.venv/bin/python",
  "files": [
    {"path": "/Users/lukai/.claude/agents/coder-deepseek.md",  "sha256": "..."},
    {"path": "/Users/lukai/.claude/agents/reviewer-gpt.md",    "sha256": "..."},
    {"path": "/Users/lukai/.claude/agents/frontend-gemini.md", "sha256": "..."},
    {"path": "/Users/lukai/.claude/commands/ship.md",          "sha256": "..."},
    {"path": "/Users/lukai/.claude/scripts",                   "type": "symlink", "target": "/Users/lukai/.claude-multi-ai-kit/scripts"},
    {"path": "/Users/lukai/bin/cship",                         "sha256": "..."}
  ]
}
```

升级 / 卸载靠这份清单精确操作，绝不动用户自己的文件。

**Step 9 · smoke test**

跑 `tests/smoke_test.sh`：
1. 不带 API key 调一次 `planner_cli.py --dry-run` → 应正常退出
2. 各 CLI `--help` 都能输出
3. `cship --version` 打印 VERSION

任一失败则回滚并打印诊断。

---

## 3.5 前端角色可开关 + Planner 输出契约

### 3.5.1 前端优化角色（Gemini/agy）可开关

`/Users/lukai/.claude/.env` 提供：

```bash
FRONTEND_ENABLED=true            # true | false，默认 true
FRONTEND_PROVIDER=gemini         # enabled=true 时生效
```

`ship.py` 行为矩阵：

| FRONTEND_ENABLED | 当前任务涉及前端 | 行为 |
|---|---|---|
| true  | 是 | 调 `frontend-gemini`（走 `agy`），在 coder 完成后再做一轮前端优化 |
| true  | 否 | 跳过前端步骤 |
| false | 是 | **不调 Gemini**；前端代码完全由 `coder-deepseek` 写（依据 plan §4 段落）|
| false | 否 | 同上，无差别 |

临时覆盖：`cship --no-frontend "..."` / `cship --frontend "..."`。

### 3.5.2 Planner 输出契约（前端计划必须详细）

`planner_cli.py` 生成 `docs/plans/<feature>.md` 时**强制按下列模板**写。**§4 前端段落不可省略，且需达到下列详细度**——因为这是 DeepSeek（在 `FRONTEND_ENABLED=false` 时）或 Gemini（true 时）的唯一参考。

```markdown
# <feature> 开发计划

## 1. 需求摘要
- 用户目标 / 验收点

## 2. 影响范围
- 后端文件清单（绝对路径）
- 前端文件清单（绝对路径）
- 数据库 / 配置变更

## 3. 后端实现步骤

## 4. 前端实现步骤  ← 详细度要求最高
### 4.1 页面 / 组件清单
- 新增：<绝对路径>，职责一句话
- 修改：<绝对路径>，要改哪部分

### 4.2 UI 结构
- 布局（栅格 / Flex / 区块）
- 组件层级树（列表表示）
- 状态机：每个状态的触发条件、UI 表现、可达状态

### 4.3 交互细节
- 每个可交互元素：事件 → 请求 → loading/success/error 三态
- 表单：字段、校验、错误提示文案
- 无障碍：tab 顺序、aria-label

### 4.4 样式规范
- 颜色 / 间距 / 字号是否走 design token
- 响应式断点（mobile / tablet / desktop）
- 暗色模式适配（如支持）

### 4.5 接口约定
- 路径 / 方法 / 请求体 / 响应体（含错误码）
- mock 数据示例（粘贴即用）

### 4.6 性能 / 体积
- 新依赖体积评估
- 大列表是否虚拟滚动 / 图片懒加载

### 4.7 前端验收清单
- [ ] 视觉对照设计稿
- [ ] 三态截图
- [ ] 不同分辨率截图
- [ ] Lighthouse ≥ X

## 5. 测试计划
- 后端单测 + 前端 e2e

## 6. 风险与回滚
```

实现要点：

- `planner_cli.py` 的 system prompt 显式注入：**"§4 必须按 4.1–4.7 七小节展开，不可合并、不可省略；每小节至少 3 行实质内容"**
- 当 `FRONTEND_ENABLED=false` 时仍保留 §4，并在头部插入：
  `> 注：本次 FRONTEND_ENABLED=false，§4 将由 coder 直接实现，不走独立前端优化阶段`
- `reviewer-gpt` 在审查计划阶段检查 §4 是否齐七小节，缺则回退 Planner 补写（最多 1 轮）
- 若任务明确无前端（Planner 自判：纯后端 / CLI / 数据脚本），§4 写一句 `> 本次无前端改动`，跳过细则

---

## 4. `upgrade.sh`

```bash
1. git -C /Users/lukai/.claude-multi-ai-kit fetch
2. 显示 CHANGELOG（HEAD..origin/main）让用户确认
3. git -C /Users/lukai/.claude-multi-ai-kit pull
4. /Users/lukai/.claude-multi-ai-kit/.venv/bin/pip install \
       -r /Users/lukai/.claude-multi-ai-kit/requirements.txt --upgrade
5. 按 /Users/lukai/.claude/kit.lock 比对 agents/commands，
   若用户改过 → 询问 keep / overwrite / backup
6. 重新合并 settings.json fragment（幂等）
7. 跑 /Users/lukai/.claude-multi-ai-kit/tests/smoke_test.sh
8. 更新 /Users/lukai/.claude/kit.lock 的 version 字段
```

---

## 5. `uninstall.sh`

```bash
1. 读 /Users/lukai/.claude/kit.lock 的 files 清单，逐个删除
   （校验 sha256 未被用户改过，改过则跳过并告警）
2. 询问是否删除 /Users/lukai/.claude-multi-ai-kit （kit 仓库本体）
3. 询问是否删除 /Users/lukai/.claude/.env （默认保留，含 API key）
4. 从 /Users/lukai/.claude/settings.json 移除 fragment 引入的权限
   （按 _kit_managed_keys 元数据定位）
5. 移除 /Users/lukai/bin/cship
6. 移除 /Users/lukai/.zshrc（或 .bashrc）中 install.sh 写入的 PATH 注入行
```

为了支持第 4 步，fragment 在写入时带 marker：

```json
{
  "permissions": {
    "allow": [
      "// >>> multi-ai-kit",
      "Bash($HOME/bin/cship:*)",
      "// <<< multi-ai-kit"
    ]
  }
}
```

> 注：JSON 不支持注释，实际实现用单独的 `_kit_managed_keys` 元数据字段记录哪些 key 由 kit 添加。

---

## 6. `doctor.sh`（健康检查）

输出报告：

```
[✓] Kit version       : 1.0.0
[✓] Python venv       : 3.11.5 OK
[✓] Symlink scripts/  : OK
[✓] Agents installed  : 3/3
[!] OPENAI_API_KEY    : missing
[✓] settings.json     : permissions present
[✓] cship in PATH     : /Users/me/bin/cship
[✓] Last smoke test   : passed (2026-05-28)
```

失败项给出修复命令提示。

---

## 7. 新项目接入：零配置 vs 增强配置

### 7.1 零配置（默认）

新项目什么都不用做，直接：

```bash
cd /Users/lukai/IdeaProjects/<新项目目录>
cship "加一个用户导出功能"
```

`ship.py` 会：
1. 自动检测项目类型（看 `pom.xml` / `package.json` / `pyproject.toml`）→ 选默认测试命令
2. 在项目内创建 `docs/plans/` 和 `docs/reviews/`
3. 走完整工作流

### 7.2 增强配置（推荐）

项目根放 `CLAUDE.md`（拷自 `templates/project-CLAUDE.md`）：

```markdown
# 项目元信息（供 multi-ai-kit 使用）

test_command: mvn test -pl core
build_command: mvn package -DskipTests
frontend_paths:
  - src/main/resources/static
  - src/main/webapp
exclude_paths:
  - target
  - generated-sources
```

`ship.py` 会优先读这个文件。

---

## 8. 配置可切换性（落实"工具中立"）

`/Users/lukai/.claude/.env` 支持三层配置：**角色 → provider → 模式（cli / sdk）**。

### 8.1 完整环境变量

```bash
# ===== 角色 → Provider =====
PLANNER_PROVIDER=claude         # claude | gpt | gemini | deepseek
CODER_PROVIDER=deepseek
REVIEWER_PROVIDER=gpt
FRONTEND_PROVIDER=gemini

# ===== 每个 Provider 的调用模式（cli = 复用本机 CLI 订阅，sdk = 走 API key）=====
# 默认 cli；CLI 不可用时自动回退到 sdk 并打印提示
CLAUDE_MODE=cli                 # cli | sdk
GPT_MODE=cli
GEMINI_MODE=cli
DEEPSEEK_MODE=sdk                # DeepSeek 无 CLI，强制 sdk

# ===== CLI 路径（仅 *_MODE=cli 时使用，install.sh 自动探测填入）=====
CLAUDE_CLI_PATH=/usr/local/bin/claude
GPT_CLI_PATH=/usr/local/bin/codex          # OpenAI Codex CLI（复用 ChatGPT 订阅）
GEMINI_CLI_PATH=/Users/lukai/.local/bin/agy
DEEPSEEK_CLI_PATH=                          # 留空

# ===== API Key（仅 *_MODE=sdk 时使用，可留空）=====
ANTHROPIC_API_KEY=
OPENAI_API_KEY=
DEEPSEEK_API_KEY=
GEMINI_API_KEY=

# ===== 模型版本（cli 模式下也可作 -m 参数传入；sdk 模式下作请求字段）=====
CLAUDE_MODEL=claude-opus-4-7
GPT_MODEL=gpt-4.1
DEEPSEEK_MODEL=deepseek-coder
GEMINI_MODEL=gemini-2.5-pro

# ===== 可选：自定义 API endpoint（公司网关场景）=====
ANTHROPIC_BASE_URL=
OPENAI_BASE_URL=
DEEPSEEK_BASE_URL=
GEMINI_BASE_URL=
```

### 8.2 CLI 模式 vs SDK 模式

每个 `*_cli.py` 内部都有两条分支：

```python
def call_model(prompt: str, files: list[Path], mode_hint: str) -> str:
    mode = os.getenv(f"{PROVIDER.upper()}_MODE", "cli")
    if mode == "cli":
        cli_path = os.getenv(f"{PROVIDER.upper()}_CLI_PATH")
        if not cli_path or not shutil.which(cli_path):
            log.warning(f"{PROVIDER} CLI not found, falling back to sdk")
            mode = "sdk"
    if mode == "cli":
        return _call_via_cli(cli_path, prompt, files)
    return _call_via_sdk(prompt, files)
```

### 8.3 各家 CLI 适配差异（实现要点）

| Provider | CLI | headless 调用 | 输入约束 | 输出形态 | 注意 |
|---|---|---|---|---|---|
| Claude | `claude` (Claude Code) | `claude -p "<prompt>"` | 文本，可附文件路径 | 纯文本 | 复用订阅；session 失效需 `claude login` |
| Gemini | `agy`（Google Antigravity CLI，Gemini 前端 Agent） | `agy run "<prompt>"` 或 `--prompt-file`（以本机 `agy --help` 为准） | 文本 + 项目路径 | patch / 文本 | 复用 Google 账号订阅；项目级会话存放在 `<repo>/.antigravitycli/` |
| GPT | `codex`（OpenAI 官方 CLI） | `codex exec "<prompt>"` 或 `codex --prompt-file` | 文本，可附文件 | 纯文本 / JSON | 复用 ChatGPT 订阅（Plus / Pro 含 Codex 额度）；session 失效需 `codex login` |
| DeepSeek | 无官方 CLI | — | — | — | 强制 `DEEPSEEK_MODE=sdk` |

> install.sh 在 §3 的 Step 5 之前增加 CLI 探测步骤（详见 §8.4），把找到的 CLI 路径写入 `.env`，找不到的对应 provider 自动降级为 sdk 并提示用户填 API key。

### 8.4 install.sh 的 CLI 探测逻辑

新增 Step 4.5：

```bash
detect_cli() {
  local name=$1; local var=$2
  local path
  path=$(command -v "$name" 2>/dev/null || true)
  if [ -n "$path" ]; then
    echo "[✓] 发现 $name CLI: $path"
    write_env "$var" "$path"
    write_env "${var%_CLI_PATH}_MODE" "cli"
  else
    echo "[!] 未发现 $name CLI，将使用 SDK 模式（需 API key）"
    write_env "${var%_CLI_PATH}_MODE" "sdk"
  fi
}

detect_cli claude   CLAUDE_CLI_PATH
detect_cli codex    GPT_CLI_PATH        # 若失败则 GPT 走 sdk
detect_cli agy      GEMINI_CLI_PATH     # Google Antigravity
# deepseek 直接：write_env DEEPSEEK_MODE sdk
```

### 8.5 切换示例

**改某角色的模型版本（同 provider 同模式）**

```bash
DEEPSEEK_MODEL=deepseek-chat
```

**改某角色背后的家**

```bash
REVIEWER_PROVIDER=claude            # 审查从 GPT 切到 Claude
```

**强制某 provider 改走 API 模式（如订阅配额用完）**

```bash
CLAUDE_MODE=sdk
ANTHROPIC_API_KEY=sk-ant-...
```

**临时一次性覆盖**

```bash
REVIEWER_PROVIDER=claude CLAUDE_MODE=cli cship "审查这次改动"
```

未来若不再用 Claude，改 `PLANNER_PROVIDER=gpt` 即可，其余完全不动。

---

## 9. 安全清单

- `/Users/lukai/.claude/.env` 权限强制 `chmod 600`
- `install.sh` 检测当前所在仓库的 `.gitignore` 是否覆盖 `.env` → 警告（kit 自己不会把 .env 入库，但用户可能误放在项目目录）
- CLI 日志默认脱敏 API key，仅 `AI_DEBUG=1` 才打全文 prompt；日志写到 `/Users/lukai/.claude/logs/`
- `/Users/lukai/.claude/kit.lock` 记录 sha256，防止升级时悄悄覆盖用户改过的文件
- `uninstall.sh` 默认保留 `/Users/lukai/.claude/.env`，避免误删 key

---

## 10. 实施路线图（建议交付顺序）

| 阶段 | 产物 | 验收 |
|------|------|------|
| P1 | 4 个 CLI + ai_common.py | `--help` 全通；T1 patch 生成可 `git apply` |
| P2 | ship.py 纯脚本编排 | `python ship.py "demo"` 在本仓库跑通端到端 |
| P3 | install.sh + uninstall.sh + 模板 | 新机器 `bash install.sh` 一遍过；卸载干净 |
| P4 | upgrade.sh + kit.lock + doctor.sh | 改版本号 `upgrade.sh` 能识别并更新 |
| P5 | Claude Code 适配层（agents + ship.md） | `/ship` 在 Claude Code 内工作 |
| P6 | README + smoke_test 完善 | 文档单走可让新人 30 分钟装完 |

---

## 11. 用户最终体验

**首次安装**（新机器）：

```bash
curl -fsSL https://raw.githubusercontent.com/<you>/claude-multi-ai-kit/main/install.sh | bash
# 交互式填 4 个 API key
# 完成
```

**日常用**：

```bash
cd /Users/lukai/IdeaProjects/<任意项目>
cship "加 OAuth 登录"
# 或在 Claude Code 里 /ship 加 OAuth 登录
```

**升级**：

```bash
/Users/lukai/.claude-multi-ai-kit/upgrade.sh
```

**体检**：

```bash
/Users/lukai/.claude-multi-ai-kit/doctor.sh
```

**换机器**：重复"首次安装"，30 秒（新机器 HOME 不同时，install.sh 会自动改写所有写入文件中的绝对路径）。

**卸载**：

```bash
/Users/lukai/.claude-multi-ai-kit/uninstall.sh
```

---

## 12. 风险与注意

- **`curl | bash` 安全争议**：建议 README 同时给出"先下载再读后执行"版本
- **jq 依赖**：mac 默认没装，install.sh 检测并 `brew install jq`（询问后）
- **Windows 兼容**：本方案是 macOS/Linux 优先；Windows 提供 PowerShell 版 `install.ps1` 作为 P7
- **API 配额**：`/ship` 一次可能调多个模型，README 给出"小功能 ≈ X 千 token"的参考值
- **kit 升级破坏 agents**：用 sha256 比对 + 三选项交互（keep/overwrite/backup）保护用户改动
```