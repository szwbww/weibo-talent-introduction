# 前端改动前基线
本附件由当前源码逐行摘录；新设计样式契约见子计划06。

[styles.css:1](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/resources/static/styles.css:1)
```
1: :root {
2:     /* Brand — modern business blue */
3:     --primary: #1e40af;
4:     --primary-hover: #1e3a8a;
5:     --primary-active: #172554;
6:     --primary-rgb: 30, 64, 175;
7:     --primary-light: rgba(var(--primary-rgb), 0.07);
8:     --primary-tint: rgba(var(--primary-rgb), 0.1);
9: 
10:     --bg-main: #f5f7fb;
11:     --bg-sidebar: #ffffff;
12:     --bg-sidebar-hover: rgba(var(--primary-rgb), 0.06);
13:     --bg-sidebar-active: rgba(var(--primary-rgb), 0.09);
14: 
15:     --panel-bg: rgba(255, 255, 255, 0.55);
16:     --panel-border: rgba(15, 23, 42, 0.08);
17:     --line: rgba(15, 23, 42, 0.055);
18:     --border: rgba(15, 23, 42, 0.11);
19:     --surface: rgba(15, 23, 42, 0.022);
20: 
21:     --text-main: #1e293b;
22:     --text-muted: #94a3b8;
23:     --text-sidebar: #64748b;
24:     --text-sidebar-active: #1e293b;
25:     --text-secondary: #475569;
26:     --text-strong: #334155;
27:     --ink: #1e293b;
28:     --bg-subtle: #f8fafc;
29:     --border-strong: #cbd5e1;
30:     --primary-bright: #3b82f6;
31: 
32:     --success: #059669;
33:     --success-rgb: 5, 150, 105;
34:     --success-bg: rgba(var(--success-rgb), 0.08);
35:     --success-border: rgba(var(--success-rgb), 0.18);
36:     --green: var(--success);
37: 
38:     --error: #e11d48;
39:     --error-rgb: 225, 29, 72;
40:     --error-bg: rgba(var(--error-rgb), 0.07);
41:     --error-border: rgba(var(--error-rgb), 0.16);
42:     --error-strong: #be123c;
43:     --red: var(--error);
44: 
45:     --warning: #d97706;
46:     --warning-rgb: 217, 119, 6;
47:     --warning-bg: rgba(var(--warning-rgb), 0.08);
48:     --warning-border: rgba(var(--warning-rgb), 0.2);
49:     --warning-strong: #b45309;
50:     --warning-bright: #f59e0b;
51:     --amber: var(--warning);
52: 
53:     --info: #0ea5e9;
54:     --info-rgb: 14, 165, 233;
55:     --info-bg: rgba(var(--info-rgb), 0.08);
56:     --info-border: rgba(var(--info-rgb), 0.2);
57: 
58:     --z-sticky: 10;
59:     --z-dropdown: 20;
60:     --z-overlay: 50;
61:     --z-drawer: 60;
62:     --z-modal: 1000;
63:     --z-confirm: 1200;
64:     --z-toast: 9999;
65: 
66:     --glass-border: rgba(255, 255, 255, 0.5);
67:     --glass-shadow: 0 8px 32px rgba(var(--primary-rgb), 0.1);
68:     --glass-blur: blur(16px);
69: 
70:     --radius-sm: 7px;
71:     --radius-md: 10px;
72:     --radius-lg: 18px;
73: 
74:     --shadow-sm: 0 1px 2px rgba(15, 23, 42, 0.04);
75:     --shadow-md: 0 1px 3px rgba(15, 23, 42, 0.06), 0 1px 2px rgba(15, 23, 42, 0.03);
76:     --shadow-lg: 0 10px 28px -8px rgba(15, 23, 42, 0.14), 0 2px 6px rgba(15, 23, 42, 0.05);
77:     --shadow-xl: 0 20px 48px -12px rgba(15, 23, 42, 0.2), 0 4px 12px rgba(15, 23, 42, 0.06);
78:     --shadow: var(--shadow-md);
79: 
80:     --transition: all 0.15s ease;
81: 
82:     --font-mono: 'SF Mono', ui-monospace, Menlo, monospace;
83:     --font-body: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Helvetica Neue', sans-serif;
84: 
85:     --verbatim: #7c3aed;
86:     --verbatim-bg: rgba(124, 58, 237, 0.06);
87:     --verbatim-border: rgba(124, 58, 237, 0.24);
```

[styles.css:802](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/resources/static/styles.css:802)
```
802: .button {
803:     display: inline-flex;
804:     align-items: center;
805:     justify-content: center;
806:     gap: 6px;
807:     min-height: 32px;
808:     height: 32px;
809:     padding: 0 12px;
810:     border-radius: var(--radius-sm);
811:     font-weight: 500;
812:     font-size: 12px;
813:     cursor: pointer;
814:     border: 1px solid var(--border);
815:     background-color: transparent;
816:     color: var(--text-main);
817:     transition: transform 0.12s ease, box-shadow 0.15s ease, background-color 0.15s ease, border-color 0.15s ease, opacity 0.1s ease;
818:     outline: none;
819:     user-select: none;
820:     font-family: var(--font-body);
821:     position: relative;
822:     overflow: hidden;
823: }
824: 
825: .button:hover {
826:     border-color: rgba(15, 23, 42, 0.2);
827:     background-color: var(--surface);
828:     transform: translateY(-1px);
829:     box-shadow: 0 2px 6px rgba(15, 23, 42, 0.08);
830: }
831: 
832: .button:active {
833:     transform: translateY(0) scale(0.97);
834:     box-shadow: none;
835:     opacity: 0.85;
836: }
837: 
838: .button.primary {
839:     background-image: linear-gradient(180deg, var(--primary-bright), var(--primary));
840:     background-color: var(--primary);
841:     border-color: transparent;
842:     color: #ffffff;
843:     font-weight: 600;
844:     box-shadow: 0 1px 2px rgba(var(--primary-rgb), 0.4), inset 0 1px 0 rgba(255,255,255,0.18);
845: }
846: 
847: .button.primary:hover {
848:     background-image: linear-gradient(180deg, #2f7bff, var(--primary-hover));
849:     box-shadow: 0 4px 14px rgba(var(--primary-rgb), 0.35), inset 0 1px 0 rgba(255,255,255,0.18);
850: }
851: 
852: .button.secondary {
853:     background-color: var(--primary-light);
854:     border-color: rgba(var(--primary-rgb), 0.12);
855:     color: var(--primary);
856: }
857: 
858: .button.secondary:hover {
859:     background-color: rgba(var(--primary-rgb), 0.1);
860: }
861: 
862: .button.danger {
863:     background-color: var(--error-bg);
864:     border-color: var(--error-border);
865:     color: var(--error);
866: }
867: 
868: .button.danger:hover {
869:     background-color: rgba(var(--error-rgb), 0.1);
870: }
871: 
872: .icon-button {
873:     width: 32px;
```

[styles.css:3421](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/resources/static/styles.css:3421)
```
3421: .data-table {
3422:     width: 100%;
3423:     border-collapse: collapse;
3424:     font-size: 11px;
3425: }
3426: 
3427: .data-table th,
3428: .data-table td {
3429:     padding: 6px 8px;
3430:     text-align: left;
3431:     border-bottom: 1px solid var(--line);
3432: }
3433: 
3434: .data-table th {
3435:     font-weight: 600;
3436:     color: var(--text-muted);
3437:     background: rgba(15, 23, 42, 0.02);
3438:     position: sticky;
3439:     top: 0;
3440:     font-family: var(--font-body);
3441:     font-size: 11px;
3442:     text-transform: uppercase;
3443:     letter-spacing: 0.3px;
3444: }
3445: 
```

[styles.css:4494](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/resources/static/styles.css:4494)
```
4494: .modal-content {
4495:     background-color: var(--bg-sidebar);
4496:     border-radius: var(--radius-lg);
4497:     border: 1px solid var(--panel-border);
4498:     box-shadow: var(--shadow-xl);
4499:     width: 100%;
4500:     max-height: 80vh;
4501:     overflow-y: auto;
4502:     display: flex;
4503:     flex-direction: column;
4504: }
4505: 
4506: .modal-content.task-modal {
4507:     max-width: 700px;
4508: }
4509: 
4510: .modal-header {
4511:     display: flex;
4512:     justify-content: space-between;
4513:     align-items: center;
4514:     padding: 16px 20px;
4515:     border-bottom: 1px solid var(--panel-border);
4516: }
4517: 
4518: .modal-header h3 {
4519:     font-family: var(--font-body);
4520:     font-size: 15px;
4521:     font-weight: 600;
4522:     margin: 0;
4523: }
4524: 
4525: .modal-body {
4526:     padding: 20px;
4527:     display: flex;
4528:     flex-direction: column;
4529:     gap: 16px;
4530: }
4531: 
```

[styles.css:5498](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/resources/static/styles.css:5498)
```
5498: .bsc-input {
5499:     width: 100%;
5500:     padding: 7px 10px;
5501:     border: 1px solid var(--panel-border);
5502:     border-radius: var(--radius-sm);
5503:     background-color: var(--panel-bg);
5504:     font-size: 13px;
5505:     color: var(--text-main);
5506:     transition: border-color 0.15s, box-shadow 0.15s;
5507:     box-sizing: border-box;
5508: }
5509: .bsc-select {
5510:     appearance: none;
5511:     background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%239b9a97'/%3E%3C/svg%3E");
5512:     background-repeat: no-repeat;
5513:     background-position: right 10px center;
5514:     padding-right: 28px;
5515:     cursor: pointer;
5516: }
5517: .bsc-input:focus {
5518:     outline: none;
5519:     border-color: var(--primary);
5520:     box-shadow: 0 0 0 3px rgba(var(--primary-rgb), 0.1);
5521: }
5522: .bsc-input-wrap {
5523:     position: relative;
5524:     display: flex;
5525:     align-items: center;
5526: }
5527: .bsc-input-wrap .bsc-input {
5528:     padding-right: 36px;
5529: }
5530: .bsc-input-suffix {
```

[styles.css:9150](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/resources/static/styles.css:9150)
```
9150: .batch-send-tabs {
9151:   display: flex;
9152:   gap: 28px;
9153:   min-height: 48px;
9154:   padding: 0 28px;
9155:   border-bottom: 1px solid rgba(15, 23, 42, .08);
9156:   flex-shrink: 0;
9157: }
9158: 
9159: .batch-send-tab {
9160:   position: relative;
9161:   border: 0;
9162:   background: transparent;
9163:   color: var(--text-sidebar);
9164:   padding: 0 2px;
9165:   font: inherit;
9166:   font-weight: 600;
9167:   cursor: pointer;
9168: }
9169: 
9170: .batch-send-tab::after {
9171:   content: "";
9172:   position: absolute;
9173:   right: 0;
9174:   bottom: -1px;
9175:   left: 0;
9176:   height: 2px;
9177:   background: transparent;
9178: }
9179: 
9180: .batch-send-tab:hover { color: var(--primary); }
9181: .batch-send-tab.is-active { color: var(--primary); }
9182: .batch-send-tab.is-active::after { background: var(--primary); }
9183: 
9184: .batch-send-tab-panel {
9185:   flex: 1;
9186:   min-height: 0;
9187:   padding: 20px 28px 28px;
9188:   overflow: auto;
9189: }
9190: 
```

[index.html:628](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/resources/static/index.html:628)
```
628:                     <div class="split-button" id="discoverBtnGroup">
629:                         <button class="button primary" id="discoverBtn" onclick="handleDiscoverClick()">发现专家</button>
630:                         <button class="button primary split-arrow" id="discoverModeToggle" aria-expanded="false">
631:                             <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>
632:                         </button>
633:                         <div class="dropdown-menu" id="discoverModeMenu" hidden style="right: auto; left: 0;">
634:                             <button class="dropdown-item" id="promoteRawBtn" onclick="handleDiscoverOption('quick')">快速晋升（扫描 RAW）</button>
635:                             <button class="dropdown-item" id="discoverDeepBtn" onclick="handleDiscoverOption('deep')">深度发现（外部数据源）</button>
636:                             <button class="dropdown-item" onclick="handleDiscoverOption('enrich')">补充学术数据（OpenAlex）</button>
637:                             <button class="dropdown-item" id="enrichBackfillBtn" hidden onclick="handleDiscoverOption('enrichBackfill')">补采机构类型（一次性）</button>
638:                             <button class="dropdown-item" id="enrichYearBackfillBtn" hidden onclick="handleDiscoverOption('enrichYearBackfill')">补采发表年份（一次性）</button>
639:                             <hr class="dropdown-divider">
640:                             <button class="dropdown-item" onclick="handleDiscoverOption('revalidate')">重新验证候选人</button>
641:                         </div>
```

[index.html:1120](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/resources/static/index.html:1120)
```
1120: 
1121: <!-- Task Progress & Launch Modal (统一任务管理弹窗) -->
1122: <div id="taskProgressModal" class="modal-overlay" hidden>
1123:     <div class="modal-content task-modal">
1124:         <div class="modal-header">
1125:             <h3 id="taskModalTitle">任务管理</h3>
1126:             <button class="button small" onclick="closeTaskModal()">×</button>
1127:         </div>
1128:         <div id="taskModalToast" class="task-modal-toast" hidden></div>
1129:         <div class="modal-body" style="gap: 20px;">
1130:             <div id="discoverySchedulePanel" class="discovery-schedule-panel" hidden>
1131:                 <div id="discoveryScheduleControls" class="discovery-schedule-row">
1132:                     <label for="discoveryScheduleHours" class="task-modal-input-label">
1133:                         执行间隔（小时）
1134:                         <input id="discoveryScheduleHours" class="task-modal-input-field discovery-schedule-hours"
1135:                                type="number" min="1" max="168" step="1"
1136:                                aria-describedby="discoveryScheduleHint" disabled>
1137:                     </label>
1138:                     <button id="discoveryScheduleSave" class="button primary discovery-schedule-save"
1139:                             type="button" disabled>保存定时</button>
1140:                 </div>
1141:                 <p id="discoveryScheduleHint" class="discovery-schedule-hint" role="status" aria-live="polite"></p>
1142:             </div>
1143:             <!-- Configuration Section (visible when starting task) -->
1144:             <div id="taskModalConfigSection" class="task-modal-config-section">
1145:                 <p id="taskLaunchDesc" class="text-muted" style="margin: 0; font-size: 13px; line-height: 1.5; color: var(--text-muted);"></p>
1146: 
1147:                 <div id="taskLaunchKeywordRow" hidden>
1148:                     <label class="task-modal-input-label">
1149:                         发现关键词 (多个用逗号分隔，留空使用默认条件):
1150:                         <input type="text" id="taskLaunchKeywordInput" class="task-modal-input-field" placeholder="输入关键词，如: AI, Machine Learning...">
1151:                     </label>
1152:                 </div>
```

[index.html:1328](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/resources/static/index.html:1328)
```
1328:                 <section class="batch-config-editor-section">
1329:                     <div class="batch-config-editor-section-heading">
1330:                         <h4>收件范围</h4>
1331:                         <span>所有条件同时生效；已绑定发件账号的专家会跳过；发件邮箱留空使用全部可发送账号</span>
1332:                     </div>
1333:                     <div class="batch-config-editor-grid">
1334:                         <label class="batch-config-field">
1335:                             <span class="batch-config-field-label">漏斗层级</span>
1336:                             <select id="batchConfigEditorFunnelLevel" class="bsc-input bsc-select">
1337:                                 <option value="">全部层级</option>
1338:                                 <option value="CANDIDATE">CANDIDATE</option>
1339:                                 <option value="APPLICATION">APPLICATION</option>
1340:                             </select>
1341:                         </label>
1342:                         <div class="batch-config-field">
1343:                             <span class="batch-config-field-label">标签</span>
```

[index.html:1431](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/resources/static/index.html:1431)
```
1431:                         <div class="batch-config-field batch-gate-field" id="editorFieldGateFilter">
1432:                             <span class="batch-config-field-label">邮件模版门禁过滤</span>
1433:                             <div class="batch-gate-row">
1434:                                 <label class="batch-task-status-toggle batch-gate-toggle">
1435:                                     <input type="checkbox" id="batchConfigEditorGateFilter">
1436:                                     <span class="batch-task-status-switch"></span>
1437:                                     <span class="batch-task-status-label" id="batchConfigEditorGateFilterLabel">已关闭</span>
1438:                                 </label>
1439:                                 <span class="batch-gate-hint" id="batchConfigEditorGateFilterHint">仅向满足该模板必填字段的专家发送，缺字段的会在发送时被门禁拦下并计入失败。</span>
1440:                             </div>
1441:                             <div class="batch-gate-keys" id="batchConfigEditorGateFilterKeys" hidden></div>
1442:                         </div>
```

[index.html:1575](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/resources/static/index.html:1575)
```
1575:                     <label class="batch-config-field" id="manualFieldTemplate">
1576:                         <span class="batch-config-field-label">模板</span>
1577:                         <select id="batchManualTemplateId" class="bsc-input bsc-select">
1578:                             <option value="">系统默认介绍邮件模板</option>
1579:                         </select>
1580:                         <span class="batch-config-diff-badge" hidden>已修改</span>
1581:                         <div class="batch-config-diff-original" hidden></div>
1582:                     </label>
1583:                     <label class="batch-config-field" id="manualFieldFunnelLevel">
1584:                         <span class="batch-config-field-label">漏斗层级</span>
1585:                         <select id="batchManualFunnelLevel" class="bsc-input bsc-select">
1586:                             <option value="">全部层级</option>
1587:                             <option value="CANDIDATE">CANDIDATE</option>
1588:                             <option value="APPLICATION">APPLICATION</option>
1589:                         </select>
1590:                         <span class="batch-config-diff-badge" hidden>已修改</span>
1591:                         <div class="batch-config-diff-original" hidden></div>
1592:                     </label>
```

[index.html:1695](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/resources/static/index.html:1695)
```
1695:                         <span class="batch-config-diff-badge" hidden>已修改</span>
1696:                         <div class="batch-config-diff-original" hidden></div>
1697:                     </div>
1698:                     <div class="batch-config-field batch-gate-field" id="manualFieldGateFilter">
1699:                         <span class="batch-config-field-label">邮件模版门禁过滤</span>
1700:                         <div class="batch-gate-row">
1701:                             <label class="batch-task-status-toggle batch-gate-toggle">
1702:                                 <input type="checkbox" id="batchManualGateFilter">
1703:                                 <span class="batch-task-status-switch"></span>
1704:                                 <span class="batch-task-status-label" id="batchManualGateFilterLabel">已关闭</span>
1705:                             </label>
1706:                             <span class="batch-gate-hint" id="batchManualGateFilterHint">仅影响本次执行，不修改原定时任务。</span>
1707:                         </div>
1708:                         <div class="batch-gate-keys" id="batchManualGateFilterKeys" hidden></div>
1709:                         <span class="batch-config-diff-badge" hidden>已修改</span>
1710:                         <div class="batch-config-diff-original" hidden></div>
1711:                     </div>
1712:                     <div class="batch-config-field batch-gate-field" id="manualFieldExcludeVerifiedUnavailableEmails">
1713:                         <span class="batch-config-field-label">排除已验证不可用邮箱</span>
1714:                         <div class="batch-gate-row">
```
