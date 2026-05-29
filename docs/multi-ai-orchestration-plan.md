# Multi-AI 编排脚手架 · 执行计划

> 目标：在本仓库内一次性搭好"Opus 计划 / DeepSeek 编码 / GPT 测试审查 / Gemini 前端"的可复用工作流。
> 实施方：DeepSeek（按本计划落代码） → GPT（按 §6 用例验收） → 前端片段交 Gemini。

---

## 1. 目录结构（产物清单）

```
.claude/
├── settings.json                  # 权限 allowlist + 环境变量
├── agents/
│   ├── coder-deepseek.md          # 子 Agent：写代码
│   ├── reviewer-gpt.md            # 子 Agent：测试 + 审查
│   └── frontend-gemini.md         # 子 Agent：前端优化
├── commands/
│   └── ship.md                    # /ship 一键编排命令
└── scripts/
    ├── ai_common.py               # 公共：读 stdin / 写 patch / 错误处理
    ├── deepseek_cli.py            # CLI 包装：DeepSeek
    ├── gpt_cli.py                 # CLI 包装：GPT (OpenAI)
    └── gemini_cli.py              # CLI 包装：Gemini
.env.example                       # 三家 API key 占位
```

CLI 单独成文件，便于未来在 CI 里复用，不绑死在 Claude Code 里。

---

## 2. CLI 包装器规范（DeepSeek 实现重点）

### 2.1 统一命令行接口

所有三个 CLI 必须支持完全一致的参数，便于 Agent 调用：

```
<tool>_cli.py \
  --task <短任务名>                  # 用于日志/缓存键
  --prompt-file <path>              # 详细任务描述（markdown）
  --files <p1> <p2> ...             # 注入文件原文作为上下文
  --mode {patch|review|freeform}    # 输出形态
  --out <path>                      # 写入位置（patch / md / json）
  [--model <override>]              # 可选模型覆盖
```

### 2.2 输出形态

| mode | 含义 | 输出 |
|------|------|------|
| `patch` | 让模型改代码 | 统一 diff（`git apply` 可直接吃） |
| `review` | 审查 / 生成测试 | Markdown，包含 `## Findings` `## Suggested Tests` |
| `freeform` | 自由文本 | 原样写入 `--out` |

### 2.3 公共行为（`ai_common.py`）

- 读取 `.env`：`DEEPSEEK_API_KEY` / `OPENAI_API_KEY` / `GEMINI_API_KEY`
- 失败重试：指数退避，最多 3 次
- 超时：默认 120s，可 `--timeout` 覆盖
- 日志：写到 `.claude/logs/<tool>-<task>-<ts>.log`，包含 prompt + 响应摘要
- 不打印 API key；只在调试模式 `AI_DEBUG=1` 打印 prompt 全文

### 2.4 默认模型

| Tool | 默认 model |
|------|-----------|
| deepseek_cli | `deepseek-chat`（或 deepseek-coder） |
| gpt_cli | `gpt-4.1` 系列最新可用 |
| gemini_cli | `gemini-2.5-pro` 或最新可用 |

> 模型版本不写死在代码里，放 `.env` 的 `*_MODEL` 变量，便于后续升级。

---

## 3. Subagent 文件规范

每个 agent 用 Claude Code 标准 frontmatter，`description` 字段决定路由命中度，必须写清楚"何时用我"。

### 3.1 `.claude/agents/coder-deepseek.md`

```markdown
---
name: coder-deepseek
description: Use when implementing code changes from an approved plan. Delegates the actual code writing to DeepSeek via deepseek_cli. Input: plan section + file paths. Output: applied diff.
tools: Bash, Read, Edit, Write
---

调用 `python .claude/scripts/deepseek_cli.py --mode patch ...`，
拿到 diff 后用 `git apply --check` 验证，再 `git apply` 写入。
若 `git apply --check` 失败，把错误回灌给 DeepSeek 让其修订，最多 2 轮。
```

### 3.2 `.claude/agents/reviewer-gpt.md`

```markdown
---
name: reviewer-gpt
description: Use after code changes land to (a) review diff for bugs/security and (b) generate or extend tests. Calls gpt_cli in review mode.
tools: Bash, Read
---

输入：当前分支相对 main 的 diff + 关键文件。
调用 gpt_cli --mode review，把结果落到 docs/reviews/<feature>-<ts>.md。
如果发现 P0 问题，明确列出并停止后续步骤。
```

### 3.3 `.claude/agents/frontend-gemini.md`

```markdown
---
name: frontend-gemini
description: Use for optimizing frontend code (UI polish, a11y, perf, CSS, component refactor). Input: frontend file paths + goal. Output: diff.
tools: Bash, Read, Edit
---

调用 gemini_cli --mode patch，仅在前端目录（src/main/resources/static, *.vue, *.tsx 等）生效。
```

---

## 4. `/ship` 编排命令

文件：`.claude/commands/ship.md`

工作流（Claude/Opus 主导，按顺序触发 subagent）：

1. **理解需求** — 与用户澄清功能目标、范围、验收标准
2. **出计划** — 在 `docs/plans/<feature>.md` 写执行计划（任务拆解 + 涉及文件 + 验收）
3. **派 coder-deepseek** — 按计划逐段实现，每段落地一个 commit
4. **派 reviewer-gpt** — 审查 diff，生成/补测试，把结果落到 `docs/reviews/`
5. **若涉及前端** — 派 frontend-gemini 优化
6. **跑测试** — `mvn test`（本项目是 Maven），失败回到步骤 3
7. **汇总** — 输出"已做 / 待人工确认 / 风险点"三段式总结

命令参数：`/ship <feature 简述>`

---

## 5. `.claude/settings.json` 配置

需要添加的关键项：

```json
{
  "permissions": {
    "allow": [
      "Bash(python .claude/scripts/deepseek_cli.py:*)",
      "Bash(python .claude/scripts/gpt_cli.py:*)",
      "Bash(python .claude/scripts/gemini_cli.py:*)",
      "Bash(git apply:*)",
      "Bash(git apply --check:*)",
      "Bash(mvn test:*)"
    ]
  },
  "env": {
    "AI_DEBUG": "0"
  }
}
```

`.env` 不入库；`.env.example` 入库作为模板。

---

## 6. 验收用例（GPT 测试时跑这些）

| # | 场景 | 期望 |
|---|------|------|
| T1 | `python deepseek_cli.py --mode patch --task hello --prompt-file demo.md --files src/main/java/.../Foo.java --out out.patch` | 生成可 `git apply --check` 通过的 diff |
| T2 | API key 缺失 | 退出码非零，stderr 提示缺哪个 env，不泄露其他 key |
| T3 | 网络中断模拟 | 重试 3 次后失败，日志记录 3 次尝试 |
| T4 | `/ship 给 X 加字段 Y` | 在 `docs/plans/` 出现计划；产生至少 1 个 commit；`docs/reviews/` 出现审查文件 |
| T5 | reviewer-gpt 找到 P0 | 工作流停在第 4 步，不继续往下 |
| T6 | 前端纯 CSS 优化任务 | frontend-gemini 被路由命中，coder-deepseek 不被调用 |

---

## 7. 风险与权衡

- **diff 失败率**：DeepSeek 生成的 patch 行号偏移常见 → 必须有 `git apply --check` + 回灌修订循环（已在 §3.1 规定）
- **三家 API 费用**：每个 `/ship` 会跑多次模型；建议先在小功能验证 token 量，再决定是否加缓存
- **上下文一致性**：三家模型不共享上下文，依赖 plan.md 和 diff 作为"协议"。计划文档质量直接决定下游产出质量
- **MCP vs CLI**：选 CLI 是因为更易调试、可在 CI 复用、不绑定 Claude Code；MCP 留作未来升级路径

---

## 8. 给 DeepSeek 的实现顺序建议

1. `ai_common.py`（最先，所有 CLI 依赖它）
2. `deepseek_cli.py`（自己测自己，最快闭环）
3. `gpt_cli.py` / `gemini_cli.py`（基本是 deepseek_cli 的复制 + SDK 替换）
4. `.env.example` + `.gitignore` 补 `.env`
5. 三个 agent .md
6. `/ship` command
7. `settings.json` 权限
8. 跑 §6 的 T1 ~ T3 自验

完成后人工跑一次 T4 ~ T6 做端到端验收。
```