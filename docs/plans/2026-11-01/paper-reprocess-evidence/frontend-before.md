# 前端改动前基线（2026-11-01）
## 当前 DOM
```html
<section id="discoveryPapersPane" class="modal-body dp-root" role="tabpanel" aria-labelledby="discoveryPapersTab" hidden>
  <div class="dp-heading"><div><h4>论文原文</h4><p class="dp-muted">本功能启用后开始留存；历史抽样未自动迁入。</p></div><button class="button secondary small" type="button" data-dp-action="settings">留存设置</button></div>
  <div class="dp-storage">
    <div class="dp-storage-main"><span>原文库占用（磁盘）</span><strong data-dp="archiveBytes">未知</strong><p class="dp-muted" data-dp="archiveCounts">正在读取…</p></div>
    <div class="dp-disk"><div class="dp-between"><span>所在磁盘总容量 <b data-dp="diskTotal">未知</b></span><span>可用 <b data-dp="diskAvailable">未知</b></span></div><progress class="dp-meter" data-dp="diskMeter" aria-label="所在磁盘已用空间" max="1" value="0"></progress><p class="dp-muted" data-dp="diskBreakdown">未知</p><p class="dp-muted" data-dp="measuredAt">尚未获取快照</p></div>
  </div>
  <div class="dp-policy" role="status" data-dp="policy">正在读取留存设置…</div>
  <div class="dp-error" role="alert" data-dp="error" hidden></div>
  <div class="dp-filters">
    <label class="dp-search">论文标题 / DOI<input type="search" data-dp="q" maxlength="200" placeholder="输入标题或 DOI"></label>
    <label>执行任务<select data-dp="execution"><option value="">全部任务</option></select></label>
    <label>原文状态<select data-dp="status"><option value="">全部状态</option><option value="STORED">已留存</option><option value="NOT_STORED">未留存</option><option value="SAVE_ERROR">保存异常</option><option value="DELETED">已删除</option></select></label>
    <label>保护状态<select data-dp="protected"><option value="">全部</option><option value="true">已保护</option><option value="false">未保护</option></select></label>
    <label>解析对照<select data-dp="gain"><option value="">全部</option><option value="true">有新增关联</option></select></label>
  </div>
  <div class="dp-selection"><span data-dp="selection">已选 0 篇</span><div class="dp-actions"><button class="button secondary small" type="button" data-dp-action="protect" disabled>批量保护</button><button class="button secondary small" type="button" data-dp-action="unprotect" disabled>批量取消保护</button><button class="button danger small" type="button" data-dp-action="delete" disabled>批量删除原文</button></div></div>
  <div class="dp-table-wrap"><table class="dp-table"><thead><tr><th><input type="checkbox" data-dp="selectPage" aria-label="选择本页论文"></th><th>论文 / 原文状态</th><th>来源 / 大小</th><th>执行记录</th><th>邮箱 / 姓名关联</th><th>职称 / 学位证据</th><th>操作</th></tr></thead><tbody data-dp="rows"></tbody></table></div>
  <div class="dp-footer"><span data-dp="listInfo">正在加载…</span><div class="dp-actions"><button class="button secondary small" type="button" data-dp-action="previous" disabled>上一页</button><span data-dp="pageInfo">第 1 页</span><button class="button secondary small" type="button" data-dp-action="next" disabled>下一页</button></div></div>
</section>
<dialog id="discoveryPaperReader" class="dp-dialog dp-reader" aria-labelledby="dpReaderTitle">
```
## 原文页完整现有规则
```css
/* discovery-paper-archive-contract:start */
#taskProgressModal[data-discovery-review="true"] .dp-tabbar{overflow-x:auto;white-space:nowrap}
.dp-root{color:#1e293b;font-size:12px;line-height:1.6;gap:14px}
.dp-root h4,.dp-dialog h4{margin:0;font-size:14px;font-weight:600;color:#1e293b}
.dp-root p,.dp-dialog p{color:#475569;line-height:1.7;margin:0}
.dp-root .dp-muted,.dp-dialog .dp-muted{font-size:11px;color:#64748b;line-height:1.7}
.dp-heading,.dp-between,.dp-selection,.dp-footer,.dp-reader-tools,.dp-dialog-foot{display:flex;align-items:center;justify-content:space-between;gap:16px}
.dp-heading>div{min-width:0}.dp-heading .button{flex-shrink:0}
.dp-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
.dp-storage{display:grid;grid-template-columns:1fr 1.65fr;border:1px solid rgba(15,23,42,.08);border-radius:8px;background:#fff}
.dp-storage-main{padding:12px 18px;border-right:1px solid rgba(15,23,42,.08);font-size:12px;color:#64748b}
.dp-storage-main>strong{display:block;font-size:26px;font-weight:600;line-height:1.45;letter-spacing:-.5px;color:#1e293b}
.dp-disk{padding:14px 20px;color:#64748b;font-size:12px}
.dp-disk b{font-weight:600;color:#334155}
.dp-meter{display:block;width:100%;height:8px;margin:15px 0 11px;border:0;border-radius:4px;overflow:hidden;appearance:none;background:#edf1f7;accent-color:#1e40af}
.dp-meter::-webkit-progress-bar{background:#edf1f7;border-radius:4px}.dp-meter::-webkit-progress-value{background:#1e40af;border-radius:4px}.dp-meter::-moz-progress-bar{background:#1e40af;border-radius:4px}
.dp-policy{padding:10px 12px;border:1px solid #e8edf3;border-radius:6px;background:#f8fafc;color:#475569;font-size:11px;line-height:1.7}
.dp-policy[data-state="BLOCKED"]{border-color:#fde6b0;background:#fffbeb;color:#92400e}
.dp-error{padding:11px 12px;border:1px solid rgba(225,29,72,.16);border-radius:6px;background:rgba(225,29,72,.07);color:#be123c;font-size:12px;line-height:1.7;overflow-wrap:anywhere}
.dp-filters{display:grid;grid-template-columns:1.6fr 1.3fr .9fr .9fr 1fr;gap:10px}
.dp-filters label{display:flex;flex-direction:column;gap:5px;margin:0;font-size:11px;color:#64748b}
.dp-filters :is(input,select),.dp-setting-field input,.dp-inline-label select{width:100%;min-width:0;height:35px;min-height:35px;padding:8px 10px;border:1px solid #dfe4eb;border-radius:6px;background:#f8fafc;color:#334155;font:inherit;font-size:12px;line-height:1.4;box-shadow:none;margin:0}
.dp-filters :is(input,select):hover:not(:disabled),.dp-setting-field input:hover:not(:disabled),.dp-inline-label select:hover:not(:disabled){border-color:#93b4ec}
.dp-filters :is(input,select):focus,.dp-setting-field input:focus,.dp-inline-label select:focus{outline:2px solid #c7d2fe;outline-offset:0;border-color:#818cf8;box-shadow:none}
.dp-selection{font-size:12px;color:#64748b}
.dp-check{display:flex;flex-direction:row;align-items:center;gap:7px;margin:0;font-size:12px;line-height:1.5;color:#334155}
.dp-check input[type="checkbox"],.dp-table input[type="checkbox"]{appearance:auto;width:14px;height:14px;min-width:14px;min-height:14px;max-width:14px;max-height:14px;margin:0;padding:0;flex:none;box-sizing:border-box;accent-color:#1e40af;cursor:pointer;box-shadow:none}
.dp-table-wrap{border:1px solid #e3e8ef;border-radius:7px;overflow-x:auto}
.dp-table{width:100%;min-width:1010px;table-layout:fixed;border-collapse:collapse}
.dp-table th,.dp-table td{padding:12px 10px;font-size:12px;line-height:1.55;text-align:left;border-bottom:1px solid #e8edf3;vertical-align:middle;overflow-wrap:anywhere}
.dp-table th{position:static;top:auto;z-index:auto;height:40px;background:#f8f9fb;color:#94a3b8;font-weight:600;white-space:nowrap}
.dp-table th:first-child{width:34px;padding-left:12px}.dp-table th:nth-child(2){width:auto}.dp-table th:nth-child(3){width:10%}.dp-table th:nth-child(4){width:17%}.dp-table th:nth-child(5){width:13%}.dp-table th:nth-child(6){width:13%}.dp-table th:last-child{width:11%}
.dp-table tbody tr:last-child td{border-bottom:0}.dp-table tbody tr:hover{background:#f8faff}.dp-table tbody tr.is-selected{background:#eef2ff}
.dp-title{display:block;color:#334155;font-weight:500;line-height:1.5}.dp-sub{display:block;margin-top:4px;font-size:10px;color:#94a3b8;line-height:1.5;white-space:normal}
.dp-link{border:0;background:none;padding:0;color:#1e40af;font:inherit;font-size:12px;cursor:pointer;text-decoration:none;box-shadow:none}
.dp-link:hover:not(:disabled){text-decoration:underline;color:#1e3a8a}.dp-link:active:not(:disabled){color:#172554}
.dp-badge{display:inline-flex;align-items:center;padding:2px 6px;margin-top:4px;border:1px solid #e2e8f0;border-radius:4px;background:#f1f5f9;color:#64748b;font-size:11px;line-height:1.5;white-space:nowrap}
.dp-ok{border-color:#cceedd;background:#ecfdf5;color:#047857}.dp-warn{border-color:#fde6b0;background:#fffbeb;color:#b45309}.dp-protected{border-color:#d5e3ff;background:#eff6ff;color:#1e40af}
.dp-footer{font-size:11px;color:#94a3b8;line-height:1.6}
.dp-empty,.dp-table td.dp-empty{padding:40px 20px;text-align:center;font-size:12px;color:#64748b;background:#f8fafc}
.dp-run-summary{display:flex;flex-wrap:wrap;align-items:center;gap:12px;padding:12px;border:1px solid #e2e8f0;border-radius:6px;background:#f8fafc;font-size:12px;color:#475569}
.dp-dialog{margin:auto;padding:0;border:1px solid #dfe5ee;border-radius:14px;background:#fff;color:#1e293b;max-height:90vh;overflow:auto;box-shadow:0 25px 80px rgba(15,23,42,.19);font-family:var(--font-body)}
.dp-dialog::backdrop{background:rgba(15,23,42,.45)}.dp-reader{width:min(1180px,calc(100vw - 48px));max-width:none}.dp-confirm{width:min(520px,calc(100vw - 48px));max-width:none}
.dp-dialog-head{display:flex;align-items:flex-start;justify-content:space-between;gap:15px;padding:16px 20px;border-bottom:1px solid #e2e8f0}
.dp-dialog h3{margin:0;font-size:15px;font-weight:600;color:#1e293b}.dp-dialog-head .dp-muted{margin-top:6px}
.dp-reader-tools{padding:10px 20px;border-bottom:1px solid #e2e8f0;font-size:11px;color:#64748b}
.dp-inline-label{display:flex;flex-direction:row;align-items:center;gap:8px;margin:0;min-width:0;font-size:12px;white-space:nowrap}.dp-inline-label select{max-width:330px}
.dp-reader-grid{display:grid;grid-template-columns:1.08fr 1fr;height:57vh;min-height:420px;max-height:620px}
.dp-original{min-width:0;overflow:auto;padding:16px;background:#edf0f5;border-right:1px solid #e2e8f0}
.dp-pdf{display:block;width:100%;height:100%;min-height:380px;border:1px solid #dbe1ea;background:#fff}
.dp-text{margin:0;padding:16px;background:#fff;color:#293340;border:1px solid #dbe1ea;white-space:pre-wrap;overflow-wrap:anywhere;font:11px/1.8 var(--font-mono)}
.dp-text mark{padding:0;background:#fef3c7;color:#293340;outline:2px solid #fcd34d;outline-offset:2px;border-radius:2px}
.dp-evidence{min-width:0;overflow:auto;padding:0 18px 18px}
.dp-evidence-tabs{display:flex;gap:18px;position:sticky;top:0;z-index:1;background:#fff;border-bottom:1px solid #e2e8f0;margin-bottom:15px}
.dp-evidence-tabs button{border:0;border-bottom:2px solid transparent;background:none;padding:14px 0 12px;color:#64748b;font:inherit;font-size:12px;cursor:pointer;white-space:nowrap}
.dp-evidence-tabs button:hover{color:#1e40af}.dp-evidence-tabs button:active{color:#172554}.dp-evidence-tabs button.is-active{border-bottom-color:#1e40af;color:#1e40af;font-weight:600}
.dp-evidence-card{margin:11px 0;padding:13px;border:1px solid #e2e8f0;border-radius:7px;background:#fff;font-size:12px}
.dp-evidence-card h4{font-size:12px;line-height:1.5}.dp-evidence-card .dp-muted{margin:3px 0 9px;overflow-wrap:anywhere}
.dp-evidence-card blockquote{margin:10px 0;padding:9px 10px;border-left:2px solid #cbd5e1;background:#f8fafc;color:#475569;font-size:11px;line-height:1.75;overflow-wrap:anywhere;white-space:pre-wrap}
.dp-evidence-card footer{display:flex;align-items:center;justify-content:space-between;gap:10px;color:#94a3b8;font-size:10px}.dp-evidence-card footer .dp-link{font-size:11px}
.dp-dialog .dp-warning{margin:12px 0;padding:11px 12px;border:1px solid #fde7b3;border-radius:6px;background:#fffbeb;color:#92400e;font-size:11px;line-height:1.8}
.dp-dialog-body{padding:18px 20px;font-size:12px;line-height:1.7}.dp-dialog-body>p{margin:12px 0}
.dp-dialog-foot{padding:13px 20px;border-top:1px solid #e2e8f0;background:#fbfcfe;font-size:11px;color:#94a3b8}
.dp-setting-field{display:flex;flex-direction:row;align-items:center;justify-content:space-between;gap:20px;margin:20px 0;color:#334155;font-size:12px}.dp-setting-field input{width:100px}
.dp-delete-metrics{display:grid;grid-template-columns:1fr 1fr;gap:12px;padding:15px;border:1px solid #e2e8f0;border-radius:7px;background:#f8fafc;color:#64748b;font-size:11px}
.dp-delete-metrics strong{display:block;font-size:23px;line-height:1.5;color:#1e293b}
.dp-root :is(button,input,select,a):focus-visible,.dp-dialog :is(button,input,select,a):focus-visible,.dp-run-summary button:focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
.dp-root :is(button,input,select):disabled,.dp-dialog :is(button,input,select):disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.dp-root .button:disabled:hover,.dp-dialog .button:disabled:hover{transform:none;box-shadow:none}
@media(max-width:900px){.dp-filters{grid-template-columns:repeat(3,minmax(0,1fr))}.dp-search{grid-column:span 2}.dp-storage{grid-template-columns:1fr 1.3fr}.dp-reader-grid{display:block;overflow:auto;height:65vh;min-height:0;max-height:none}.dp-original{height:40vh;border-right:0;border-bottom:1px solid #e2e8f0}.dp-evidence{overflow:visible}.dp-pdf{min-height:0}}
@media(max-width:540px){.dp-root{padding:14px;gap:12px}.dp-storage{grid-template-columns:1fr}.dp-storage-main{border-right:0;border-bottom:1px solid rgba(15,23,42,.08)}.dp-storage-main>strong{font-size:24px}.dp-disk{padding:13px 15px}.dp-filters{grid-template-columns:repeat(2,minmax(0,1fr))}.dp-selection,.dp-footer{align-items:flex-start;gap:8px;flex-direction:column}.dp-reader,.dp-confirm{width:calc(100vw - 24px)}.dp-reader-tools{align-items:flex-start;flex-direction:column}.dp-inline-label{width:100%}.dp-dialog-head{padding:14px}.dp-dialog-foot{padding:12px 14px}.dp-original{padding:12px}.dp-evidence-tabs{gap:12px}.dp-evidence-tabs button{font-size:11px}.dp-heading{align-items:flex-start}.dp-root .button,.dp-dialog .button{height:auto;min-height:36px}}
```

## styles.css:1
```css
:root {
    /* Brand — modern business blue */
    --primary: #1e40af;
    --primary-hover: #1e3a8a;
    --primary-active: #172554;
    --primary-rgb: 30, 64, 175;
    --primary-light: rgba(var(--primary-rgb), 0.07);
    --primary-tint: rgba(var(--primary-rgb), 0.1);

    --bg-main: #f5f7fb;
    --bg-sidebar: #ffffff;
    --bg-sidebar-hover: rgba(var(--primary-rgb), 0.06);
    --bg-sidebar-active: rgba(var(--primary-rgb), 0.09);

    --panel-bg: rgba(255, 255, 255, 0.55);
    --panel-border: rgba(15, 23, 42, 0.08);
    --line: rgba(15, 23, 42, 0.055);
    --border: rgba(15, 23, 42, 0.11);
    --surface: rgba(15, 23, 42, 0.022);

    --text-main: #1e293b;
    --text-muted: #94a3b8;
    --text-sidebar: #64748b;
    --text-sidebar-active: #1e293b;
    --text-secondary: #475569;
    --text-strong: #334155;
    --ink: #1e293b;
    --bg-subtle: #f8fafc;
    --border-strong: #cbd5e1;
    --primary-bright: #3b82f6;

    --success: #059669;
    --success-rgb: 5, 150, 105;
    --success-bg: rgba(var(--success-rgb), 0.08);
    --success-border: rgba(var(--success-rgb), 0.18);
    --green: var(--success);

    --error: #e11d48;
    --error-rgb: 225, 29, 72;
    --error-bg: rgba(var(--error-rgb), 0.07);
    --error-border: rgba(var(--error-rgb), 0.16);
    --error-strong: #be123c;
    --red: var(--error);

    --warning: #d97706;
    --warning-rgb: 217, 119, 6;
    --warning-bg: rgba(var(--warning-rgb), 0.08);
    --warning-border: rgba(var(--warning-rgb), 0.2);
    --warning-strong: #b45309;
    --warning-bright: #f59e0b;
    --amber: var(--warning);

    --info: #0ea5e9;
    --info-rgb: 14, 165, 233;
    --info-bg: rgba(var(--info-rgb), 0.08);
    --info-border: rgba(var(--info-rgb), 0.2);

    --z-sticky: 10;
    --z-dropdown: 20;
    --z-overlay: 50;
    --z-drawer: 60;
    --z-modal: 1000;
    --z-confirm: 1200;
    --z-toast: 9999;

    --glass-border: rgba(255, 255, 255, 0.5);
    --glass-shadow: 0 8px 32px rgba(var(--primary-rgb), 0.1);
    --glass-blur: blur(16px);

    --radius-sm: 7px;
    --radius-md: 10px;
    --radius-lg: 18px;

    --shadow-sm: 0 1px 2px rgba(15, 23, 42, 0.04);
    --shadow-md: 0 1px 3px rgba(15, 23, 42, 0.06), 0 1px 2px rgba(15, 23, 42, 0.03);
    --shadow-lg: 0 10px 28px -8px rgba(15, 23, 42, 0.14), 0 2px 6px rgba(15, 23, 42, 0.05);
    --shadow-xl: 0 20px 48px -12px rgba(15, 23, 42, 0.2), 0 4px 12px rgba(15, 23, 42, 0.06);
    --shadow: var(--shadow-md);

    --transition: all 0.15s ease;

    --font-mono: 'SF Mono', ui-monospace, Menlo, monospace;
    --font-body: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Helvetica Neue', sans-serif;

    --verbatim: #7c3aed;
    --verbatim-bg: rgba(124, 58, 237, 0.06);
    --verbatim-border: rgba(124, 58, 237, 0.24);
}
```

## styles.css:102
```css
.button.modal-close-x {
    border: none;
    background: transparent;
    padding: 4px 8px;
    font-size: 18px;
    line-height: 1;
}
```

## styles.css:304
```css
p {
    font-size: 12px;
    color: var(--text-muted);
    margin-top: 2px;
}
```

## styles.css:799
```css
/* Buttons */
.button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    min-height: 32px;
    height: 32px;
    padding: 0 12px;
    border-radius: var(--radius-sm);
    font-weight: 500;
    font-size: 12px;
    cursor: pointer;
    border: 1px solid var(--border);
    background-color: transparent;
    color: var(--text-main);
    transition: transform 0.12s ease, box-shadow 0.15s ease, background-color 0.15s ease, border-color 0.15s ease, opacity 0.1s ease;
    outline: none;
    user-select: none;
    font-family: var(--font-body);
    position: relative;
    overflow: hidden;
}
```

## styles.css:823
```css
.button:hover {
    border-color: rgba(15, 23, 42, 0.2);
    background-color: var(--surface);
    transform: translateY(-1px);
    box-shadow: 0 2px 6px rgba(15, 23, 42, 0.08);
}
```

## styles.css:830
```css
.button:active {
    transform: translateY(0) scale(0.97);
    box-shadow: none;
    opacity: 0.85;
}
```

## styles.css:836
```css
.button.primary {
    background-image: linear-gradient(180deg, var(--primary-bright), var(--primary));
    background-color: var(--primary);
    border-color: transparent;
    color: #ffffff;
    font-weight: 600;
    box-shadow: 0 1px 2px rgba(var(--primary-rgb), 0.4), inset 0 1px 0 rgba(255,255,255,0.18);
}
```

## styles.css:845
```css
.button.primary:hover {
    background-image: linear-gradient(180deg, #2f7bff, var(--primary-hover));
    box-shadow: 0 4px 14px rgba(var(--primary-rgb), 0.35), inset 0 1px 0 rgba(255,255,255,0.18);
}
```

## styles.css:850
```css
.button.secondary {
    background-color: var(--primary-light);
    border-color: rgba(var(--primary-rgb), 0.12);
    color: var(--primary);
}
```

## styles.css:856
```css
.button.secondary:hover {
    background-color: rgba(var(--primary-rgb), 0.1);
}
```

## styles.css:860
```css
.button.danger {
    background-color: var(--error-bg);
    border-color: var(--error-border);
    color: var(--error);
}
```

## styles.css:866
```css
.button.danger:hover {
    background-color: rgba(var(--error-rgb), 0.1);
}
```

## styles.css:1044
```css
td.actions .button {
    height: 26px;
    min-height: 26px;
    padding: 0 8px;
    font-size: 11px;
}
```

## styles.css:1115
```css
label {
    display: flex;
    flex-direction: column;
    gap: 4px;
    color: var(--text-muted);
    font-size: 11px;
    font-weight: 600;
    font-family: var(--font-body);
    text-transform: uppercase;
    letter-spacing: 0.3px;
}
```

## styles.css:1219
```css
.inline-form .button {
    height: 34px;
}
```

## styles.css:1582
```css
.contact-head-actions .button {
    height: 28px;
    min-height: 28px;
    padding: 0 10px;
    font-size: 11px;
    flex-shrink: 0;
}
```

## styles.css:1841
```css
.action-card-row .button {
    height: 30px;
    min-height: 30px;
    font-size: 11px;
    padding: 0 10px;
}
```

## styles.css:1848
```css
.action-card-row .button svg {
    width: 12px;
    height: 12px;
    stroke-width: 2.2;
}
```

## styles.css:1868
```css
.mail-send-group .button {
    flex-shrink: 0;
}
```

## styles.css:2480
```css
.button.small {
    height: 26px;
    min-height: 26px;
    padding: 0 8px;
    font-size: 11px;
}
```

## styles.css:2801
```css
#accountEditorPanel .account-form-footer .button { min-height: 40px; }
```

## styles.css:2815
```css
#accountEditorPanel .account-form-footer .button { min-height: 44px; }
```

## styles.css:2835
```css
.button.danger-outline {
    color: var(--error);
    border-color: var(--error-border);
    background: transparent;
}
```

## styles.css:2841
```css
.button.danger-outline:hover {
    background-color: var(--error-bg);
}
```

## styles.css:3613
```css
/* Button ripple effect */
.button::after {
    content: '';
    position: absolute;
    inset: 0;
    background: radial-gradient(circle, rgba(255,255,255,0.35) 10%, transparent 70%);
    opacity: 0;
    transform: scale(0);
    transition: transform 0.4s ease, opacity 0.4s ease;
    pointer-events: none;
    border-radius: inherit;
}
```

## styles.css:3626
```css
.button:active::after {
    opacity: 1;
    transform: scale(2.5);
    transition: transform 0s, opacity 0s;
}
```

## styles.css:4388
```css
.contact-head-actions .button,
    .contact-head-actions select {
        height: 26px;
        min-height: 26px;
        font-size: 11px;
    }
```

## styles.css:4431
```css
#operatorStatusSelect,
    #indexLevelSelect,
    #autoReplySelect,
    #manualMailOption,
    .contact-head-status-row .button,
    .contact-head-mail-row .button {
        width: 100%;
        max-width: none;
        flex: none;
    }
```

## styles.css:4462
```css
.meeting-schedule-panel .panel-actions .button {
        width: 100%;
        margin-left: 0;
    }
```

## styles.css:5069
```css
.contacts-toolbar .toolbar-actions .button,
    .contacts-toolbar .toolbar-actions .dropdown {
        flex: 1 1 auto;
    }
```

## styles.css:5074
```css
.contacts-toolbar .toolbar-actions .dropdown .button {
        width: 100%;
    }
```

## styles.css:5078
```css
/* Enlarge touch targets */
    .toolbar .button,
    .list-pager .button {
        min-height: 40px;
    }
```

## styles.css:5084
```css
.list-pager .button {
        flex: 1;
        max-width: 160px;
    }
```

## styles.css:5232
```css
.split-button .button:first-child {
    border-top-right-radius: 0;
    border-bottom-right-radius: 0;
    border-right: 1px solid rgba(255, 255, 255, 0.2);
}
```

## styles.css:5238
```css
.split-button .button.split-arrow {
    border-top-left-radius: 0;
    border-bottom-left-radius: 0;
    padding-left: 8px;
    padding-right: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    min-width: 24px;
}
```

## styles.css:5893
```css
.compose-selected-actions .button.small {
    padding: 2px 6px;
    line-height: 1.2;
}
```

## styles.css:6444
```css
.compose-draft-actions .button:disabled,
.compose-draft-actions .button:disabled:hover,
.compose-draft-actions .button:disabled:active {
    cursor: not-allowed;
    opacity: 0.45;
    transform: none;
    box-shadow: none;
}
```

## styles.css:6864
```css
.compose-template-block-row .block-actions .button {
    width: 36px;
    min-width: 36px;
    padding: 0;
}
```

## styles.css:7030
```css
.compose-template-footer .button {
    min-width: 96px;
    height: 40px;
}
```

## styles.css:7992
```css
/* 主按钮微质感 */
.trust-reply-workbench .button { transition: box-shadow .15s ease, transform .15s ease, background .15s ease; }
```

## styles.css:7995
```css
.trust-reply-workbench .button.primary:not(:disabled) { box-shadow: 0 1px 2px rgba(var(--primary-rgb), .35); }
```

## styles.css:7996
```css
.trust-reply-workbench .button.primary:not(:disabled):hover {
  box-shadow: 0 3px 8px rgba(var(--primary-rgb), .3);
  transform: translateY(-0.5px);
}
```

## styles.css:8501
```css
.trust-reply-item-actions .button,
  .trust-reply-final-actions .button,
  .trust-training-evaluation-actions .button { width: 100%; }
```

## styles.css:8535
```css
.trust-reply-workbench .trust-reply-page-actions .button {
    width: 100%;
  }
```

## styles.css:8765
```css
.snippet-variant-editor .button:disabled,
.snippet-variant-editor .button:disabled:hover,
.snippet-variant-editor .button:disabled:active {
    opacity: 0.45;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
}
```

## styles.css:8773
```css
.snippet-variant-editor .button:focus-visible {
    outline: 2px solid var(--primary);
    outline-offset: 2px;
}
```

## styles.css:8891
```css
:root {
    --preview-dock-width: 460px;
}
```

## styles.css:9337
```css
.batch-recipient-preview-controls .button {
  flex: 0 0 auto;
}
```

## styles.css:9341
```css
.batch-recipient-preview-controls .button:disabled {
  cursor: wait;
  opacity: .7;
}
```

## styles.css:9363
```css
.batch-cron-row .button {
  flex: 0 0 auto;
}
```

## styles.css:10014
```css
.batch-manual-source-row .button { align-self: flex-start; }
```

## styles.css:10031
```css
:root {
        color-scheme: dark;

        --bg-main: #0d1420;
        --bg-sidebar: #111a2b;
        --bg-sidebar-hover: rgba(148, 163, 184, 0.08);
        --bg-sidebar-active: rgba(59, 130, 246, 0.18);

        --panel-bg: rgba(21, 31, 48, 0.55);
        --panel-border: rgba(148, 163, 184, 0.16);
        --line: rgba(148, 163, 184, 0.1);
        --border: rgba(148, 163, 184, 0.26);
        --border-strong: #475569;
        --surface: rgba(148, 163, 184, 0.08);
        --bg-subtle: #1a2436;

        --text-main: #e2e8f0;
        --text-strong: var(--bg-subtle);
        --text-secondary: #a8b6c8;
        --text-muted: #7d8ca3;
        --text-sidebar: #8fa0b8;
        --text-sidebar-active: #e2e8f0;
        --ink: #e2e8f0;

        --primary: #3b82f6;
        --primary-hover: #60a5fa;
        --primary-active: #2563eb;
        --primary-bright: #60a5fa;
        --primary-light: rgba(59, 130, 246, 0.16);
        --primary-tint: rgba(59, 130, 246, 0.22);

        --success: #34d399;
        --success-bg: rgba(52, 211, 153, 0.12);
        --success-border: rgba(52, 211, 153, 0.26);

        --error: #fb7185;
        --error-bg: rgba(251, 113, 133, 0.12);
        --error-border: rgba(251, 113, 133, 0.26);
        --error-strong: #fda4af;

        --warning: #fbbf24;
        --warning-bg: rgba(251, 191, 36, 0.12);
        --warning-border: rgba(251, 191, 36, 0.26);
        --warning-strong: #fcd34d;

        --info: #38bdf8;
        --info-bg: rgba(56, 189, 248, 0.12);
        --info-border: rgba(56, 189, 248, 0.26);

        --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.35);
        --shadow-md: 0 1px 3px rgba(0, 0, 0, 0.4), 0 1px 2px rgba(0, 0, 0, 0.3);
        --shadow-lg: 0 10px 28px -8px rgba(0, 0, 0, 0.55), 0 2px 6px rgba(0, 0, 0, 0.35);
        --shadow-xl: 0 20px 48px -12px rgba(0, 0, 0, 0.65), 0 4px 12px rgba(0, 0, 0, 0.4);
    }
```

## styles.css:10114
```css
.button.primary {
        box-shadow: 0 1px 2px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.12);
    }
```

## styles.css:10271
```css
/* S-4: styles.css 无 .button[disabled] 规则，追加本段（见 PR 说明） */
.contact-head-actions .button[disabled] {
    opacity: 0.5;
    cursor: not-allowed;
}
```

## styles.css:11500
```css
.followup-dialog :is(button,.button):disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
```

## styles.css:11553
```css
.calendar-root .button{min-height:32px;height:auto;white-space:nowrap}
```

## styles.css:11554
```css
.calendar-root .calendar-month-controls .button{padding:4px 11px;background:#fff}
```

## styles.css:11557
```css
.calendar-root .calendar-segmented .button{border:0;background:transparent;box-shadow:none;padding:5px 12px;min-height:26px;font-size:12px;color:#7d8da5}
```

## styles.css:11588
```css
.calendar-root .calendar-agenda-card .button{display:block;margin-left:auto;min-height:26px;padding:3px 0;border:0;background:transparent;color:#355dbe;font-size:12px;box-shadow:none}
```

## styles.css:11596
```css
/* Manual material request: mailbox composer only. */
.mail-chat .button.material-request-trigger{color:#1e40af;border-color:#b8cef5;background:#eef4ff;gap:7px;margin-left:3px}
```

## styles.css:11598
```css
.mail-chat .button.material-request-trigger:hover{background:#e5eeff;border-color:#93b4ec}
```

## styles.css:11599
```css
.mail-chat .button.material-request-trigger:active{background:#dbeafe}
```

## styles.css:11600
```css
.mail-chat .button.material-request-trigger:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
```

## styles.css:11626
```css
.material-request-dialog .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
```

## styles.css:11896
```css
.batch-email-verification-pager .button:disabled { opacity: .5; cursor: not-allowed; pointer-events: none; }
```

## styles.css:12007
```css
.mail-chat .button.reply-template-trigger{color:#284cc3;border-color:#b6cafa;background:#eef4ff;gap:6px}
```

## styles.css:12009
```css
.mail-chat .button.reply-template-trigger:hover:not(:disabled){background:#e5eeff;border-color:#93b4ec}
```

## styles.css:12010
```css
.mail-chat .button.reply-template-trigger:active:not(:disabled){background:#dbeafe}
```

## styles.css:12011
```css
.mail-chat .button.reply-template-trigger:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
```

## styles.css:12012
```css
.mail-chat .button.reply-template-trigger:focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
```

## styles.css:12069
```css
.reply-template-actions .button{min-width:86px;font-size:12px}
```

## styles.css:12071
```css
.reply-template-dialog :is(button,.button):disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
```

## styles.css:12284
```css
.task-center-global:focus-visible,
.task-center-link:focus-visible,
.task-center-detail .button:focus-visible,
#taskActivePager .button:focus-visible {
    outline: 2px solid #3b82f6;
    outline-offset: 3px;
}
```

## styles.css:12291
```css
.task-center-link:disabled,
.task-center-detail .button:disabled,
#taskActivePager .button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
}
```

## styles.css:12490
```css
.contact-timing-dialog .button{min-height:33px;height:33px;padding:0 13px;font-size:12px}
```

## styles.css:12491
```css
.contact-timing-dialog .button:focus-visible{outline:2px solid #6389d3;outline-offset:2px}
```

## styles.css:12492
```css
.contact-timing-dialog .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
```

## styles.css:12537
```css
.mobile-core-nav select:focus-visible,
    .mobile-contact-bar .button:focus-visible { outline: 2px solid #3b82f6; outline-offset: 2px; }
```

## styles.css:12543
```css
.topnav .logout-btn,
    .topbar-actions .button,
    .topbar-actions .task-center-global { min-height: 44px; height: auto; }
```

## styles.css:12548
```css
.auth-card .button { min-height: 44px; height: auto; }
```

## styles.css:12560
```css
#view-contacts .button { min-height: 44px; height: auto; white-space: normal; }
```

## styles.css:12587
```css
#view-mailbox .panel-head .button { min-height: 44px; height: auto; white-space: normal; }
```

## styles.css:12606
```css
.mail-chat.mobile-core-mailbox :is(button, .button) { min-height: 44px; height: auto; white-space: normal; }
```

## styles.css:12622
```css
body .mail-chat.mc-overlay-root .mc-manage-dialog .button,
    body .reply-template-dialog .button,
    body .meeting-dialog .button,
    body .meeting-dialog .meeting-close { min-height: 44px; height: auto; }
```

## styles.css:12650
```css
#view-meeting-calendar .button { min-height: 44px; height: auto; white-space: normal; }
```

## styles.css:12657
```css
body .calendar-dialog .button { min-height: 44px; height: auto; white-space: normal; }
```

## styles.css:12667
```css
#view-tasks :is(.button, .task-center-link) { min-height: 44px; height: auto; white-space: normal; }
```

## styles.css:13324
```css
#taskProgressModal[data-discovery-review="true"] .apv-logs .button.secondary:disabled,
#taskProgressModal[data-discovery-review="true"] .apv-logs .button.secondary:disabled:hover,
#taskProgressModal[data-discovery-review="true"] .apv-logs .button.secondary:disabled:active {
    background-color: rgba(30, 64, 175, 0.07);
    border-color: rgba(30, 64, 175, 0.12);
    color: #1e40af;
    opacity: 0.45;
    transform: none;
    box-shadow: none;
}
```

## styles.css:13353
```css
.reply-identity-settings .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
```

## styles.css:13354
```css
.reply-identity-settings .button:focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
```

## styles.css:13377
```css
.dp-heading .button{flex-shrink:0}
```

## styles.css:13437
```css
.dp-root .button:disabled:hover,.dp-dialog .button:disabled:hover{transform:none;box-shadow:none}
```

## styles.css:13440
```css
.dp-root .button,.dp-dialog .button{height:auto;min-height:36px}
```

## styles.css:13460
```css
#taskProgressModal[data-discovery-review="true"] .apc-settings .button:disabled:hover{transform:none;box-shadow:none}
```

## styles.css:13542
```css
.dr-recheck .button{min-height:36px}
```

## styles.css:13556
```css
.mail-chat .mail-review-actions .button{min-height:36px;height:auto}
```

## styles.css:13563
```css
.mail-chat .mail-review-confirm .button{min-height:44px;height:auto;white-space:normal}
```
