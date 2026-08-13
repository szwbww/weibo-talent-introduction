# P-D：operator_status 唯一写入口的守卫测试

优先级 **P0（根源 P3）** ｜ 前置：P-A ｜ **必须与 P-A 同一发布列车** ｜ 文件数：2

## 需求描述

**Observable outcome**

1. 任何人在 `src/main/kotlin` 新增一处对 `expert_contact.operator_status` 的写入而未登记白名单时，
   `mvn test` 失败并明确指出违规文件与行号。
2. 白名单本身即"唯一写入口"这条不变量的可执行文档。

**What must NOT change**：任何生产代码行为；本计划只加测试。

**Out of scope**：ES 侧写入口的守卫（P-B 处理）；其他字段的类似守卫。

## 为什么需要这个计划

P-A 把写入口收敛到一处，靠的是**约定**。而本次 bug 的成因正是约定失效——
`ManualOutreachTxHelper` 与 `ManualExpertMailService` 各写各的，编译通过、测试全绿、无人发现。
没有 P-D，P-A 只是"这一次都改对了"，不是"以后不可能改错"。

**发布耦合理由**：P-A 的 T-2/T-3 才把手动路径接通到 `updateAutomatically`，
从而创造出 I-1/I-2 的触发条件。先发 P-A 后补 P-D 的窗口期内新增旁路不会被发现。

## 关键不变量

### I-1：写入点白名单闭包
- Rule：`src/main/kotlin` 下对 `ExpertContact.operatorStatus` 赋值的位置集合，
  必须**恰好等于**测试中声明的白名单，多一个少一个都失败。
- 白名单初始内容（P-A 落地后的实测结果，见验收项 A-8）：
  - `campaign/service/ExpertOperatorStatusService.kt` —— 唯一自动出口 + 人工出口
  - `campaign/service/ManualInitialOutreachService.kt` —— 建行初始化 + EMAIL_INVALID 标记
- Violation consequence：白名单失效即退回 P-A 之前的状态。
- 来源：original

### I-2：白名单变更必须显式
- Rule：新增合法写入点时，必须同时修改白名单常量并在其注释中写明理由。
  测试失败信息须直接给出违规 `file:line` 与本条规则的说明。
- 来源：original

## 现状审计

### 技术选型依据

```
grep -n "archunit\|konsist\|ArchUnit" pom.xml   → (无匹配)
grep -n "<artifactId>" pom.xml | grep -i test   → spring-boot-starter-test  (仅此一项)
```

引入 ArchUnit 需新增依赖，且它工作在字节码层，对 Kotlin `data class` 的
`.copy(operatorStatus = ...)` 捕获不精确（copy 编译为合成方法，参数名信息不稳定）。
**故选择扫源码的白名单守卫测试。**

### 机制可行性（已验证，非推测）

```
grep -rn 'Paths.get("src/main' src/test/kotlin
  → QaRuleManagementServiceTest.kt:1080,1097,1129,1161 …（十余处）
     读取 src/main/resources/db/migration/*.sql
```

**已验证**：本仓库测试可按相对路径读取工程文件，即 `mvn test` 的工作目录为工程根。

**同时须说明（避免夸大先例）**：

```
grep -rn 'src/main/kotlin' src/test/kotlin           → (0 hits)
grep -rn 'src/main/resources/static' src/test/kotlin → (0 hits)
```

扫 `.kt` 源码在本仓库**尚无先例**。机制已验证、靶子是新的。

## 实现方案

### T-1 守卫测试
新增：`src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`

1. 递归遍历 `src/main/kotlin` 下全部 `.kt`。
2. 逐行匹配对 operator_status 的赋值，正则须同时覆盖两种写法：
   - `operatorStatus = ` （`copy(...)` / 构造器命名参数）
   - `operator_status` （SQL 字符串内的 `@Query` 更新）
3. 排除**非写入**的同名命中：`val operatorStatus` / `var operatorStatus` 声明、
   函数形参、DTO 字段赋值。排除规则本身写成显式列表并加注释，不用模糊启发式。
4. 断言命中文件集合 == 白名单。失败信息包含全部违规 `file:line` 与整改指引。

> **实现注意**：P-A 的现状审计已识别出 7 处「DTO 字段赋值」噪声
> （`UnmatchedInboundMailController:203/1097`、`MailboxService:165`、
> `ExpertContactManagementController:549`、`ExpertIndexController:85/410`、
> `ExpertSearchService:332`）。这些位于 controller / DTO 构造中，
> 排除规则应基于**文件路径 + 上下文**而非仅正则，否则会误报。

### T-2 白名单常量
在同文件内以 `private val ALLOWED_WRITE_SITES: Set<String>` 声明，
每一项带一行注释说明其合法理由。

## 变更文件清单（2 个）

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/test/kotlin/…/campaign/OperatorStatusWriteSeamGuardTest.kt` | 新增 |
| 2 | `docs/knowledge/campaign/K-operator-status-write-seam-guard.md` | 新增 |

## 验证命令

```bash
# 守卫测试本身
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OperatorStatusWriteSeamGuardTest

# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

git diff --check
```

通过判据：退出码 0，`Tests run: N, Failures: 0, Errors: 0`。
来源：CLAUDE.md「Commands」章节。

## 验收标准

- **I-1**：在 P-A 落地的代码上运行守卫测试通过（白名单与实际一致）。
- **I-2 反向验证**：临时在任一非白名单文件加一行
  `contact.copy(operatorStatus = "CONTACTED")`，运行测试**必须失败**，
  且失败信息包含该文件路径与行号。验证后回滚该行。
- **误报验证**：确认 7 处 DTO 赋值噪声均未被判为违规。

## 人工验收清单

### A-1：守卫在真实违规下报警【outcome 1 / I-2】
- 前置：P-A 已合入。
- 步骤：① 在 `mail/service/MailboxService.kt` 任意方法内加一行
  `val x = someContact.copy(operatorStatus = "CONTACTED")`；
  ② 执行『验证命令』节的守卫测试命令；③ 回滚。
- 预期：② 测试**失败**，输出含 `MailboxService.kt` 与具体行号，
  并提示"新增写入点须登记白名单"。

### A-2：守卫在合法代码下不误报【outcome 1】
- 步骤：在干净的 P-A 代码上执行守卫测试命令。
- 预期：通过。特别确认 `ExpertIndexController.kt`、`MailboxService.kt`、
  `UnmatchedInboundMailController.kt` 这三个含同名 DTO 字段的文件**未**被判违规。

### A-3：白名单可读【outcome 2】
- 步骤：打开守卫测试文件，阅读 `ALLOWED_WRITE_SITES`。
- 预期：每一项都有一行中文注释说明为何它是合法写入点，
  新人不看别处即可理解"唯一写入口"这条规则。
