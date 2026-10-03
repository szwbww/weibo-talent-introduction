from pathlib import Path
root = Path(__file__).resolve().parent
s = (root/'assets/mailbox-chat.js').read_text()
def patch(old, new):
    global s
    assert s.count(old) == 1, (old[:80], s.count(old))
    s = s.replace(old,new)
patch('const CHIP_UNMATCHED = "unmatched";', 'const CHIP_UNMATCHED = "unmatched";\n    const CHIP_SUSPENDED = "suspended";')
patch('        { key: CHIP_REPLIED, label: "已回复" },\n        { key: CHIP_PENDING, label: "待处理" },', '        { key: CHIP_PENDING, label: "待处理" },\n        { key: CHIP_SUSPENDED, label: "已挂起" },\n        { key: CHIP_REPLIED, label: "已回复" },')
patch('chip: (options && options.filters && typeof options.filters.pendingOnly === "boolean" && options.filters.pendingOnly) ? CHIP_PENDING : CHIP_ALL,', 'chip: (options && options.filters && options.filters.pendingOnly === true) ? CHIP_PENDING : CHIP_FOLLOWED,')
patch('if (chipValues.pendingOnly) params.set("pendingOnly", "true");', 'if (chipValues.pendingOnly) params.set("pendingOnly", "true");\n            if (instance.chip === CHIP_SUSPENDED) params.set("suspendedOnly", "true");')
patch('${lastReplyListMarkup(item)}', '${lastReplyListMarkup(item)}\n                        ${global.SuspendPreview.badge(item.contactId)}')
patch('<span data-role="person-actions">', '<span data-role="person-actions">\n                        ${global.SuspendPreview.button(item.contactId, "mc-text-button sp-card-action")}')
patch('actions.innerHTML = `\n                    <button', 'actions.innerHTML = `\n                    ${global.SuspendPreview.button(contactId, "button sp-action")}\n                    <button')
patch('            renderHeaderMeetingSummary();\n            ensureMeetingSummaryFor(contactId);', '''            let suspension = body.querySelector(".sp-status");
            if (suspension) suspension.remove();
            head.insertAdjacentHTML("afterend", global.SuspendPreview.banner(contactId));
            renderHeaderMeetingSummary();
            ensureMeetingSummaryFor(contactId);''')
patch('                footerButtons.push(`<button class="mc-text-button mc-process" type="button" data-action="mc-mark-resolved" data-message-key="${escapeText(key)}">✓ 标记已处理</button>`);', '                footerButtons.push(global.SuspendPreview.processingControls(instance.selectedContactId, message));')
patch('                footerButtons.push(`<span class="mc-done">✓ 已处理</span>`);', '                footerButtons.push(global.SuspendPreview.processedLabel(instance.selectedContactId, message));')
patch('                    ${footerHtml}\n                </article>', '                    ${footerHtml}\n                    ${global.SuspendPreview.completionLine(instance.selectedContactId, message, instance.conversation.items)}\n                </article>')
patch('            if (action === "mc-mark-resolved") {\n                markResolvedByKey(data.messageKey || "");\n                return;\n            }', '''            if (action === "mc-mark-resolved" || action === "mc-preview-process-cancel" || action === "mc-preview-process-confirm") {
                const message = messageByKey(data.messageKey || "");
                if (!message || message.processStatus !== "MANUAL_REVIEW") return;
                const contactId = Number(instance.selectedContactId);
                if (action !== "mc-preview-process-confirm") {
                    global.SuspendPreview.setConfirming(contactId, message.id, action === "mc-mark-resolved");
                    renderTimeline();
                    return;
                }
                global.SuspendPreview.resolve(contactId, message.id);
                message.processStatus = "PROCESSED";
                instance.selectedSummary = global.SuspendPreview.item(contactId);
                renderTimeline();
                renderHeader();
                syncChipButtons();
                refreshListWithFallback();
                return;
            }
            if (action === "mc-preview-keep" || action === "mc-preview-end") {
                const id = Number(data.contactId);
                if (action === "mc-preview-keep") {
                    global.SuspendPreview.keep(id);
                    renderTimeline();
                    return;
                }
                const destination = global.SuspendPreview.end(id);
                instance.chip = destination;
                instance.list.page = 0;
                if (Number(instance.selectedContactId) === id) {
                    instance.selectedSummary = global.SuspendPreview.item(id);
                    renderHeader();
                    renderTimeline();
                }
                syncChipButtons();
                refreshListWithFallback();
                return;
            }
            if (action === "mc-preview-suspend") {
                const id = Number(data.contactId);
                global.SuspendPreview.toggle(id, button).then((destination) => {
                    if (instance.disposed || !destination) return;
                    instance.chip = destination;
                    instance.list.page = 0;
                    if (Number(instance.selectedContactId) === id) {
                        instance.selectedSummary = global.SuspendPreview.item(id);
                        renderHeader();
                        renderTimeline();
                    }
                    syncChipButtons();
                    refreshListWithFallback();
                });
                return;
            }''')
# Clearly mark the locally simulated processing outcome.
patch('                return "";\n            }\n            if (message.sendStatus === "SENT")', '                return message.processStatus === "PROCESSED" ? \'<span class="mc-badge" data-tone="success">已处理</span>\' : "";\n            }\n            if (message.sendStatus === "SENT")')
(root/'preview-mailbox.js').write_text(s)
