function renderBySourceTable(bySource, container) {
    if (!bySource || Object.keys(bySource).length === 0) {
        container.innerHTML = "";
        return;
    }
    if (isEnrichmentBySource(bySource)) {
        renderEnrichmentSourceTable(bySource, container);
        return;
    }
    const rows = Object.entries(bySource).map(([name, stats]) => {
        const failures = stats.failureReasons ? Object.entries(stats.failureReasons)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 3)
            .map(([reason, count]) => `${reason}:${count}`)
            .join(", ") : "-";
        return `
            <tr>
                <td style="padding:3px 8px;">${escapeHtml(name)}</td>
                <td style="padding:3px 8px;">${escapeHtml(stats.extractionMethod || "-")}</td>
                <td style="padding:3px 8px;">${stats.papersSearched || 0}</td>
                <td style="padding:3px 8px;">${stats.authorsExtracted || 0}</td>
                <td style="padding:3px 8px;">${stats.emailsValid || 0}</td>
                <td style="padding:3px 8px;">${stats.indexed || 0}</td>
                <td style="padding:3px 8px;">${stats.promoted || 0}</td>
                <td style="padding:3px 8px;max-width:120px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(failures)}</td>
            </tr>
        `;
    }).join("");
    container.innerHTML = `
        <table style="width:100%;border-collapse:collapse;font-size:11px;">
            <thead><tr style="background:var(--panel-bg);border-bottom:1px solid var(--panel-border);">
                <th style="padding:4px 8px;text-align:left;">平台</th>
                <th style="padding:4px 8px;text-align:left;">方式</th>
                <th style="padding:4px 8px;text-align:left;">论文</th>
                <th style="padding:4px 8px;text-align:left;">邮箱</th>
                <th style="padding:4px 8px;text-align:left;">有效</th>
                <th style="padding:4px 8px;text-align:left;">收录</th>
                <th style="padding:4px 8px;text-align:left;">晋升</th>
                <th style="padding:4px 8px;text-align:left;">失败原因</th>
            </tr></thead>
            <tbody>${rows}</tbody>
        </table>
    `;
}

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => Array.from(document.querySelectorAll(selector));

// 收发件箱“待匹配”详情宿主 lease（I-5）：全页只允许存在一个 #unmatchedDetailPanel，
// 进入待匹配详情时把该既有节点临时挂入聊天右栏，归还时精确回原父节点/原兄弟位置。
// 只保存位置引用，不保存/复制面板 HTML（节点身份不变，按钮监听器随节点一起移动）。
const unmatchedDetailLease = { panel: null, parent: null, nextSibling: null };

