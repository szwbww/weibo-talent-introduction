# 08 按人选择首发目标与预估

状态：设计，未实施。依赖 04—06；在 09 完成前不得开启生产人物首发。上级：[总计划](discovery-identity-recovery-master.md)。

## 需求描述

同一人物在首发预估和实际目标中只计一次，选择一个满足当前条件的邮箱。保持原显式筛选、人工准入、抑制、模板门禁、配额和非 discovered 行为；不自动逐邮箱轮发，不改变材料提醒和会话回复。

## 关键不变量

### Invariant I-1：先邮箱合格，再归人选址
- Rule：逐成员先执行既有身份/准入/显式筛选/邮箱抑制与验证，再为一个 personId 选择一个邮箱；不能因为默认主邮箱不合格漏掉无其他限制的合格邮箱，也不能因一个邮箱合格放行其余地址。人工 HOLD/REJECTED、身份冲突、已联系/发送等人物保护按整个有效组检查，不能换成员绕过。
- Applies to：共享人物选择器、旧/新首发、失败重试。
- Violation consequence：误发或漏选；绕开人工决定。
- 来源：总计划 I-7、V148 当前结论。

### Invariant I-2：跨页和重试同一人物集合
- Rule：首次遇到人物时批量加载该人物当前成员，应用相同 scope 后确定唯一地址；仅完整判定后标记人物已处理。重试及新目标使用同一人集合，不在每页清空。安全重试沿用原尝试的邮箱，不自动换址；显示默认邮箱仅在合格时优先，否则按现有验证可信级别降序、docId 升序确定，确保同输入同结果。
- Applies to：OutreachTargetIterator、buildRetryableTargets/countEsTargets、旧 fetchSendableCandidates。
- Violation consequence：同人跨页多发、重试加新目标重复、先遇不合格地址导致漏人。
- 来源：K-batch-send-filter-retry-parity、本轮迭代器审计。

### Invariant I-3：同源只读预估
- Rule：预估和执行基于同一快照/scope/人物选择函数。只读预估不建 campaign/contact、人物成员或首发占位；未就绪/失效关系单列原因且不计为可发。预估是该时点目标数，发送前变化仍可减少实际量，不能承诺发信数恒等。
- Applies to：countPending/countBySnapshot、实际 run 路径、09 前置判断。
- Violation consequence：数量漂移、预估污染业务。
- 来源：K-recipient-count-preview-parity。

## 现状审计

- `BatchRecipientSelectionService.select` 以 docId 去重、按批查询 admission；selectAdmission 供旧路径。把 key 直接换成 personId 会破坏 includedDocIds 下游，必须保留实际选中邮箱 docId。
- `OutreachTargetIterator` 维护 seenOrcids，先取重试再取 ES；现实现中候选在 filterPage 前入 seen。人物 seen 不得沿用“先标记后审核”的顺序。
- `ManualInitialOutreachService` 的 countPending/countBySnapshot、runIntroductionFromSnapshot、buildRetryableTargets 两重载、countEsTargets、fetchEsPage、selectIncluded/filterTargets 为同链读者；旧 InitialOutreachService.fetchSendableCandidates 也须一致。
- 原始 ES 写路径及原邮件业务写路径不改；04 提供只读人物映射，V148/邮箱验证/抑制为现有真值；09 负责原子约束，本计划的集合去重不能替代它。
- 交互：重试邮箱+同人新候选、不同页默认邮箱不合格、成员人工拒绝、预估执行输入相同但历史发生变化。

## 实现方案

1. **共享纯计算（I-1— I-3）**：新增 DiscoveryPersonRecipientService，批量获取成员/当前事实、调用原逐邮箱准入能力，再输出 `{personId, selectedDocId, selectedEmail, memberDocIds, exclusionReasons}`。返回入选 docId 兼容下游，不重写原档案 orcidId。依赖方向为原邮箱 selector → 人物 selector 调用，避免两服务互相注入。
2. **统一调用（I-1— I-3）**：两条首发 service 的预估、目标、重试使用同一人物选择层；OutreachTargetIterator 支持调用者提供人物去重键，普通记录仍原 docId。每轮缓存只存本次 scope 与人物 revision 下的判断，发送前由 09 重验；不跨任务缓存“允许发送”。BatchRecipientSelectionService 继续输出每邮箱判定并保留精确拒绝原因。
3. **五份定向测试（I-1— I-3）**：同人三邮箱分散页、首地址不合格但第二合格、人工 HOLD 与已发保护、scope 不同、重试重复、新旧路径一致；断言预估 1 人、选中具体 1 地址、零副作用；材料提醒和范围外样本保持原结果。

## 变更文件清单

|文件|动作|
|---|---|
|src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientService.kt|新增|
|src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionService.kt|修改|
|src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt|修改|
|src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt|修改|
|src/main/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIterator.kt|修改|
|src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientServiceTest.kt|新增|
|src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionServiceTest.kt|修改|
|src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt|修改|
|src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt|修改|
|src/test/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIteratorTest.kt|修改|

共 10 文件；首发目标选择一个子系统，共享存储增字段 0。

## 验收标准

- I-1：只有实际合格地址入选；同组人工拒绝/历史保护不得通过另一地址突破。
- I-2：每人最多一个目标，跨页/重试依然如此；无重复和页首不合格导致的漏人。
- I-3：同一时点预估与目标人数相同；预估写入 0；未映射和失效映射可解释；非 discovered 与材料提醒回归不变。

## 人工验收清单

### A-1：选一个合格地址
- 前置条件：同人 3 邮箱放在不同页，默认邮箱被抑制、另一邮箱合格、第三待核，另有普通专家；禁真实 SMTP。
- 操作步骤：运行新/旧首发目标预览；再把同人的合格邮箱加入安全重试列表后重新预览。
- 预期结果：此人均只计 1 位、选中同一个合格地址；安全重试与新列表重叠后仍 1 目标；普通专家结果不变；预估业务写入 0。
- 覆盖：I-1— I-3、分页/重试/两发送链。

### A-2：保护不因换邮箱失效
- 前置条件：分别准备同人成员有人工 HOLD、历史已成功、身份冲突、映射未就绪的四组；另准备材料提醒基线。
- 操作步骤：为每组添加一个表面合格的新邮箱；运行预估和目标选择；运行材料提醒只读预估。
- 预期结果：四组可首发人数各 0，原因分别可见；新邮箱不能绕过保护；材料提醒与基线相同。
- 覆盖：I-1/I-3、人工历史保持项。
