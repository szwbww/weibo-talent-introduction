# 04：页面配置深度发现执行间隔

状态：待审阅，未实施。依赖03接口；7个实施文件，1个前端子系统，无新增持久字段。使用create-p样式契约。

## 需求描述

深度发现弹窗增加“执行间隔（小时）”输入框和“保存定时”按钮。空闲、正在运行时均可查看/修改；保存成功显示周期与下一候选时间。第一版仅整数1～168小时。

保持：原手动立即执行、取消、关键词/来源/范围、任务进度、关闭弹窗后的后台运行；连续模式暂停/恢复；其他任务弹窗内容。范围外：cron输入、定时开关、日历、多份定时查询、自动保存、改动既有任务框架、任何专家数据写入。

## 关键不变量

### Invariant I-1：只有明确保存才改定时
- Rule：打开/关闭弹窗、输入变化、立即执行均不得PUT schedule。只有“保存定时”发PUT；不附带关键词/来源参数，不调用start/resume/discover。成功显示后端返回的有效值与时间，不乐观假定生效。
- Applies to：初始化、保存、手动启动、closeTaskModal。
- Violation consequence：用户只是查看或改关键词便改变后台计划。
- 来源：03 API契约。

### Invariant I-2：状态跟随当前弹窗
- Rule：只在EXPERT_DISCOVERY显示区域；以独立打开代次或既有modal generation校验异步GET/PUT结果。切任务/关闭/重新打开后旧响应不能写新弹窗。正在输入时不被任务进度轮询覆盖。
- Applies to：openTaskModal、openTaskLaunchModal、closeTaskModal、GET/PUT callbacks。
- Violation consequence：别的任务看到发现设置，或输入被旧请求覆盖。
- 来源：既有任务弹窗生命周期。

### Invariant I-3：不能设置时不展示可保存假象
- Rule：加载中禁用；加载失败显示错误并禁止保存；连续/管理员禁用显示说明并隐藏小时控件；400/409/503均显示服务端原因，503 saved=true不能显示“已生效”。保存中防重复提交，成功/失败后按最新权限恢复。
- Applies to：渲染、校验、保存响应与错误处理。
- Violation consequence：无效设置误导运营，或保存失败无提示。
- 来源：K-frontend-cache-key-triad；03应用结果语义。

## 样式契约

### S-1：定时设置区域
- 复用：`.task-modal-input-label`（styles.css:4668）、`.task-modal-input-field`及focus（4677/4690）、`.button`及hover/active（802/824/831）、`.button.primary`及hover（838/847）。保留原有规则，不改变其他使用位置。
- 新增CSS逐字复制（放于task-modal样式附近）：

```css
.discovery-schedule-panel {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 12px;
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-sm);
}
.discovery-schedule-row {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-end;
    gap: 8px;
}
.discovery-schedule-hours {
    width: 120px;
}
.discovery-schedule-hint {
    margin: 0;
    font-size: 12px;
    line-height: 1.5;
    color: var(--text-secondary);
}
.discovery-schedule-save:focus-visible {
    outline: 2px solid var(--primary);
    outline-offset: 2px;
}
.discovery-schedule-save:disabled,
.discovery-schedule-save:disabled:hover,
.discovery-schedule-save:disabled:active {
    opacity: 0.45;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
    background-image: linear-gradient(180deg, var(--primary-bright), var(--primary));
}
.discovery-schedule-hours:disabled {
    opacity: 0.65;
    cursor: not-allowed;
}
```

- DOM：放在现有`.modal-body`下、`#taskModalConfigSection`之前，作为兄弟；不能塞进运行时会隐藏的configSection。以下元素逐一属于S-1：

```html
<div id="discoverySchedulePanel" class="discovery-schedule-panel" hidden>
    <div id="discoveryScheduleControls" class="discovery-schedule-row">
        <label for="discoveryScheduleHours" class="task-modal-input-label">
            执行间隔（小时）
            <input id="discoveryScheduleHours" class="task-modal-input-field discovery-schedule-hours"
                   type="number" min="1" max="168" step="1"
                   aria-describedby="discoveryScheduleHint" disabled>
        </label>
        <button id="discoveryScheduleSave" class="button primary discovery-schedule-save"
                type="button" disabled>保存定时</button>
    </div>
    <p id="discoveryScheduleHint" class="discovery-schedule-hint" role="status" aria-live="polite"></p>
</div>
```

- 不可编辑模式隐藏controls、保留hint；整个其他任务panel hidden。使用全局`[hidden]{display:none!important}`（styles.css:96）。按钮用JS绑定事件，不增加onclick或inline style。错误信息使用textContent与现有showStatus，不创建未声明class。
- 禁止：改既有class、inline style、新图标/图标库、未列出class、额外弹窗或设置菜单。既有HTML内inline style不在此轮清理范围。

### S-2：静态资源版本
- index.html已有link/script节点只改?v，不改层级/class。全部现有版本化资源一起更新为实施时唯一新key；建议`20260929-discovery-schedule`，若已有更晚key则使用更晚唯一值，禁止退回旧key。不新增JS资源文件。
- 既有固定key测试本轮检索0命中；执行前重查。若其他任务新增固定key断言，先同步文件清单，不能悄悄越界。（来源：K-frontend-cache-key-triad）

## 现状审计

### 后端设置与前端内存
- 持久schema与全部DB写读已由03界定，本计划不直接访问DB/ES/localStorage；唯一写路径为按钮PUT `/api/expert-discovery/schedule`；唯一读路径为打开弹窗GET与保存响应。
- 当前app.js:2095 openTaskModal会隐藏configSection，:7587附近openTaskLaunchModal展示该区；深度发现先读取pipeline模式，有配置的连续模式转到进度弹窗；closeTaskModal:2222停止UI轮询但不停止后台。
- 新状态放在app.js现有弹窗模块附近；GET初始化、PUT返回更新；关闭/切换重置或提升代次。进度轮询没有写小时输入的权利。
- 交互 P-1：运行/启动两入口→同一设置面板；P-2：PUT→03已应用快照→页面文案；P-3：关闭/切换→延迟响应；P-4：index缓存key→用户实际加载新JS/CSS。
- 已查三个VM测试会抽取改动函数，需要补sandbox加载/隔离桩：discoveryContinuousRun.test.js、taskModalStateMachine.test.js、taskModalLifecycleIntegration.test.js。不能为躲避这些测试给生产函数加无意义typeof检查。（来源：现有测试调用面事实）

### 前端样式盘点

完整基线与grep回执见 [frontend-paths.txt](discovery-repair-evidence/frontend-paths.txt)。改动前HTML逐字片段：

```html
<div id="taskProgressModal" class="modal-overlay" hidden>
    <div class="modal-content task-modal">
        <div class="modal-header">
            <h3 id="taskModalTitle">任务管理</h3>
            <button class="button small" onclick="closeTaskModal()">×</button>
        </div>
        <div id="taskModalToast" class="task-modal-toast" hidden></div>
        <div class="modal-body" style="gap: 20px;">
            <!-- Configuration Section (visible when starting task) -->
            <div id="taskModalConfigSection" class="task-modal-config-section">
```

对应相关CSS逐字基线：

```css
.task-modal {
    max-width: 700px;
}

.task-modal-config-section {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.task-modal-input-label {
    font-size: 12px;
    font-weight: 600;
    color: var(--text-main);
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.task-modal-input-field {
    width: 100%;
    padding: 8px 12px;
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-sm);
    background-color: var(--surface);
    color: var(--text-main);
    font-family: inherit;
    font-size: 13px;
    transition: border-color 0.2s, box-shadow 0.2s;
}

.task-modal-input-field:focus {
    outline: none;
    border-color: var(--primary);
    box-shadow: 0 0 0 3px rgba(var(--primary-rgb), 0.1);
}
```

基准token：primary #1e40af、bright #3b82f6、hover #1e3a8a、primary-rgb=30,64,175；正文 #1e293b、辅助 #475569；panel-border rgba(15,23,42,.08)，surface rgba(15,23,42,.022)；圆角7px；已有按钮32px高/左右12px/字体12px，hover translateY(-1px)、active scale(.97)/opacity .85；primary阴影0 1px 2px rgba(30,64,175,.4)+inset 0 1px 0 rgba(255,255,255,.18)。新增面板padding12/gap8，输入宽120、文字13、label12/600、hint12/1.5。深色模式沿用既有变量，不硬编码白背景。

## 实现方案

### T-1：加入区域（I-2、I-3；S-1、S-2）

修改index.html/styles.css，严格使用S-1代码块；不修改既有配置区。缓存key按S-2统一更新。区域初始hidden，控件初始disabled，防止JS未加载完成时误操作。

### T-2：读写与提示（I-1～I-3；S-1）

修改app.js：集中三个小函数（初始化/渲染/保存，必要清理函数）即可，无独立状态框架。两个打开入口在当前modal context建立后调用初始化；打开其他任务立即隐藏并使上一请求失效；关闭同样清理。用现有api封装、contextPath和认证处理。

显示规则：
- 加载：“正在读取定时配置…”；GET失败：“定时配置加载失败，请重新打开弹窗重试”，禁用保存，原手动执行不因设置GET失败被禁用。
- 默认CONFIG已知2小时：“当前沿用系统定时：每2小时整点尝试执行。保存后按保存时间重新计时。”其他cron不猜小时，input留空，提示“当前沿用系统定时；可设置整数小时周期。”
- OVERRIDE且applied=true：“已设置每 N 小时执行一次。下次计划触发：YYYY-MM-DD HH:mm（北京时间）；运行中将跳过。”
- 保存成功按实际响应值/时间渲染；相同值保存不宣称“从现在重新计时”。nextTriggerAt为空显示“下次时间暂不可用”，不推算伪时间。
- CONTINUOUS：“当前为连续发现模式，按已有策略持续推进，暂不支持小时周期设置。”隐藏控件；ADMIN_DISABLED：“当前系统定时已停用”，同样隐藏。
- 503且saved=true：“已保存但定时应用失败，请重试保存”；允许当前可编辑模式重试，不覆盖成成功toast。

只接受十进制数字组成的整数输入，Number.isInteger且1～168；空白、1.5、0、169、字母不发请求，提示“请输入1～168的整数小时”。PUT期间禁用输入和保存；失败保留输入供重试。打开时只读取一次，任务进度poll不刷新该输入。服务端reason/文本用textContent处理。

### T-3：生命周期回归（I-1～I-3；S-1、S-2）

新增discoveryScheduleSetting.test.js，加载实际新函数和真实打开/关闭函数，mock API/DOM：空闲/运行、默认/覆盖、非法值、延迟GET、连续/禁用、PUT成功/400/409/503、关闭不取消后台、手动执行不保存。补三个已有VM测试的依赖注入，使新辅助函数可运行；不删原断言、不把生产函数替换成整个假实现来取得通过。

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | src/main/resources/static/index.html | S-1 DOM、S-2缓存版本 |
| 2 | src/main/resources/static/app.js | 读取/保存/生命周期 |
| 3 | src/main/resources/static/styles.css | 仅新增S-1样式 |
| 4 | src/test/js/discoveryScheduleSetting.test.js | 新行为测试 |
| 5 | src/test/js/discoveryContinuousRun.test.js | 打开入口sandbox与连续回归 |
| 6 | src/test/js/taskModalStateMachine.test.js | 关闭入口sandbox与状态回归 |
| 7 | src/test/js/taskModalLifecycleIntegration.test.js | 生命周期依赖与回归 |

## 验收标准

- I-1：打开/输入/关闭/立即执行的PUT数均0；明确点击保存PUT数1，body只含intervalHours；响应应用成功才显示成功。
- I-2：运行态仍见设置；非发现面板隐藏；旧GET/PUT响应不改新弹窗；任务poll不覆盖未保存输入。
- I-3：坏值请求0；模式禁用控件隐藏；加载失败/保存失败有可见提示；saved=true的503不假成功；保存期间只发一次PUT。
- S-1：新增CSS与契约逐字一致，DOM层级/id/class一致，无新inline style；既有class规则diff为0；键盘焦点、禁用、窄屏换行可见。
- S-2：index所有带?v的现有资源key一致且更新；对应JS/CSS实际返回新内容。
- 运行`node --test src/test/js/discoveryScheduleSetting.test.js src/test/js/discoveryContinuousRun.test.js src/test/js/taskModalStateMachine.test.js src/test/js/taskModalLifecycleIntegration.test.js`；然后`node --test src/test/js/*.test.js`。最终整组Java11 clean package仅一次。

## 人工验收清单

### A-1：用户保存周期
- 前置条件：03后端运行在隔离环境，空表默认cron；浏览器打开深度发现弹窗。
- 操作步骤：1. 查看输入默认2与系统定时说明；2. 改3但不保存；3. 关闭重开；4. 改3点保存；5. 再次关闭重开。
- 预期结果：第3步仍2；保存后显示每3小时和北京时间下次候选时间；第5步仍3；保存不启动任务，后台DB仅一行。
- 覆盖：I-1、I-2；P-1、P-2、P-4；需求结果。

### A-2：运行中及失败
- 前置条件：已有隔离发现任务运行；可用浏览器网络拦截分别令GET失败、PUT返回503 saved=true。
- 操作步骤：1. 从运行任务打开弹窗；2. 修改5保存；3. 查看当前任务；4. 依次模拟两种失败；5. 输入0/1.5/169保存。
- 预期结果：运行中面板可用，保存不终止当前任务；GET失败显示重开提示；503显示“已保存但定时应用失败，请重试保存”；非法输入显示1～168整数提示且网络PUT为0。
- 覆盖：I-1、I-3；P-1、P-2。

### A-3：切换、连续模式与原操作
- 前置条件：模拟延迟GET；另有连续模式PAUSED测试配置；原任务启动/取消按钮可用。
- 操作步骤：1. 打开发现后马上关闭并开“补充数据”；2. 放回旧GET；3. 连续模式打开/关闭发现；4. 同步模式填关键词手动执行再取消。
- 预期结果：补充数据弹窗无发现设置，旧响应不污染；连续模式只见说明、PAUSED仍保持；手动条件/来源/范围原样发送，启动与取消各执行原接口、schedule PUT均0；关闭仍不中断后台任务。
- 覆盖：I-1～I-3；P-3；全部保留行为。

### A-4：样式与缓存
- 前置条件：重新加载页面，分别桌面和窄屏查看，浏览器可查看网络响应和计算样式。
- 操作步骤：1. 核对所有版本化资源新key；2. 查看面板/输入/按钮计算样式；3. Tab至保存按钮；4. 保存中查看禁用态；5. 切换深色模式。
- 预期结果：资源key一致；输入120px、面板padding12/gap8/圆角7；焦点2px蓝色轮廓；保存禁用opacity .45且无抬升阴影；窄屏按钮换行无横向溢出；深色模式使用既有色值变量。
- 覆盖：S-1、S-2；P-4。
