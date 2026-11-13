/* Expert-specific operational defaults. No writes to profile/identity evidence. */
(function (global) {
    'use strict';
    const escape = value => String(value == null ? '' : value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    const normalize = value => String(value || '').trim().toLowerCase();
    function emailMarkup(item) {
        const email = item.email || '';
        const emails = Array.from(new Set([email].concat(item.emails || []).map(normalize).filter(Boolean)));
        const hint = emails.length > 1 ? `该专家有 ${emails.length} 个邮箱\n${emails.map(e => e + (e === normalize(email) ? '（主邮箱）' : '')).join('\n')}` : '';
        return `<span class="expert-email-line"><span class="expert-email-address" title="${escape(email)}">${escape(email)}</span>${hint ? `<span class="expert-email-alert" tabindex="0" role="img" aria-label="${escape(hint)}" data-email-hint="${escape(hint)}">!</span>` : ''}</span>`;
    }
    async function mountVariables(panel, expertId, level, api, notify, keys) {
        const owner = {}; panel.mailSettingsOwner = owner;
        const current = () => panel.isConnected && panel.mailSettingsOwner === owner;
        panel.innerHTML = '<div class="tpl-var-loading">加载模板变量中…</div>';
        try {
            const query = new URLSearchParams({expertId, level});
            const [variables, snapshot] = await Promise.all([
                api(`/api/experts/template-variables?${new URLSearchParams({orcidId:expertId,level})}`),
                api(`/api/experts/template-overrides?${query}`)
            ]);
            if (!current()) return;
            let editing = false, busy = false, overrides = Object.assign({}, snapshot.values), draft = {};
            const originals = snapshot.original || {};
            const activeKeys = new Set(keys);
            function render() {
                const values = editing ? draft : overrides;
                const items = variables.map(v => Object.assign({},v,{value:Object.prototype.hasOwnProperty.call(values,v.key) ? values[v.key] : (activeKeys.has(v.key) ? originals[v.key] || '' : v.value)}));
                const expert = items.filter(v => activeKeys.has(v.key)), count = expert.filter(v => v.value.trim()).length;
                const pct = expert.length ? Math.round(count / expert.length * 100) : 0;
                panel.innerHTML = `<div class="expert-variable-toolbar"><div><strong>专家模板变量</strong><p>保存为该专家默认值，后续邮件统一使用。</p></div>${editing ? '' : '<button class="button" data-variable-edit>编辑变量</button>'}</div><div class="tpl-var-summary"><span class="tpl-var-coverage">专家变量覆盖: ${count}/${expert.length} (${pct}%)</span><progress class="expert-variable-progress" max="100" value="${pct}"></progress></div><div class="tpl-var-grid">${items.map(v => {
                    const writable = activeKeys.has(v.key), custom = Object.prototype.hasOwnProperty.call(values,v.key);
                    return `<div class="tpl-var-item ${v.value.trim() ? 'tpl-var-filled':'tpl-var-empty-val'}"><div class="tpl-var-key">\${${escape(v.key)}} <span class="expert-variable-badge">${!writable ? '系统变量 · 只读' : custom ? '已自定义' : ''}</span></div>${editing && writable ? `<textarea data-variable-key="${escape(v.key)}" aria-label="${escape(v.label)}" maxlength="8000" rows="${v.key === 'researchFields' ? 5 : 2}">${escape(v.value)}</textarea><button class="link-btn" data-variable-restore="${escape(v.key)}">恢复原始值</button>` : `<div class="tpl-var-value">${escape(v.value || '—')}</div>`}</div>`;
                }).join('')}</div>${editing ? '<div class="expert-variable-save"><span>仅影响当前专家</span><button class="button" data-variable-cancel>取消</button><button class="button primary" data-variable-save>保存变量</button></div>' : ''}`;
            }
            panel.oninput = e => { if (editing && e.target.dataset.variableKey) draft[e.target.dataset.variableKey] = e.target.value; };
            panel.onclick = async e => {
                const b = e.target.closest('button'); if (!b || busy || !current()) return;
                if (b.hasAttribute('data-variable-edit')) { editing = true; draft = Object.assign({},overrides); render(); }
                if (b.hasAttribute('data-variable-cancel')) { editing = false; render(); }
                if (b.dataset.variableRestore) { delete draft[b.dataset.variableRestore]; render(); }
                if (b.hasAttribute('data-variable-save')) {
                    busy = true; panel.querySelectorAll('button,textarea').forEach(el => el.disabled = true);
                    try {
                        const result = await api('/api/experts/template-overrides',{method:'PUT',body:JSON.stringify({expertId,level,expectedVersion:snapshot.version,values:draft})});
                        if (!current()) return;
                        snapshot.version = result.version; overrides = result.values; editing = false; render();
                        const detail = panel.closest('.detail');
                        if (detail) detail.querySelectorAll('[data-panel="mail-preview"]').forEach(el => { delete el.dataset.loaded; });
                        notify('专家模板变量已保存','ok');
                    } catch (err) { if (current()) notify(err.message,'error'); }
                    finally { busy = false; if(current()) panel.querySelectorAll('button,textarea').forEach(el => el.disabled = false); }
                }
            };
            render(); panel.dataset.loaded = 'true';
        } catch (err) { if (current()) { panel.innerHTML = `<div class="tpl-var-empty">${escape(err.message)}</div>`; delete panel.dataset.loaded; } }
    }
    async function mountAliases(panel, contact, api, notify, onChanged) {
        const owner = {}; panel.mailSettingsOwner = owner;
        const current = () => panel.isConnected && panel.mailSettingsOwner === owner;
        const id = Number(contact.id); let primary = contact.expertEmail || '';
        try {
            const aliases = await api(`/api/expert-contacts/${id}/email-aliases`);
            if (!current()) return;
            const rows = Array.from(new Map(aliases.filter(a => normalize(a.email) !== normalize(primary)).map(a=>[normalize(a.email),a])).values());
            panel.innerHTML = `<div class="metadata-card-header"><span>邮箱别名</span><span class="expert-variable-badge">共 ${rows.length + 1} 个邮箱</span></div><div class="expert-primary-summary"><strong>主邮箱：</strong><span data-primary-display>${escape(primary)}</span></div><p class="expert-variable-help">切换后用于后续邮件。旧地址保留；已有草稿需重新确认收件人，排队邮件不会自动改址。</p><table class="expert-email-table"><thead><tr><th>邮箱地址</th><th>来源</th><th>确认</th><th>创建时间</th><th>操作</th></tr></thead><tbody>${rows.map(a=>`<tr><td>${escape(a.email)}</td><td>${escape(a.source)}</td><td>${a.verified ? '已确认':'未确认'}</td><td>${escape(a.createdAt || '—')}</td><td><div class="expert-email-actions">${a.verified ? `<button class="button small" data-primary-email="${escape(a.email)}">设为主邮箱</button>`:''}<button class="button small danger" data-action="delete-alias" data-alias-id="${a.id}" data-contact-id="${id}">移除</button></div></td></tr>`).join('')}</tbody></table><div class="expert-alias-add"><input id="newAliasEmail" placeholder="输入邮箱地址"><button class="button primary small" data-action="add-alias" data-contact-id="${id}">添加别名</button></div>`;
            panel.onclick = async e => {
                const b = e.target.closest('[data-primary-email]'); if (!b || !current()) return;
                const email = b.dataset.primaryEmail;
                if (!global.confirm(`将 ${email} 设为主邮箱？旧地址保留，已有草稿及待发送邮件不会自动改址。`)) return;
                b.disabled = true;
                try {
                    const result = await api(`/api/expert-contacts/${id}/primary-email`,{method:'PUT',body:JSON.stringify({email,expectedEmail:primary})});
                    if (!current()) return;
                    contact.expertEmail = result.email;
                    await mountAliases(panel,contact,api,notify,onChanged);
                    if (onChanged) onChanged(id,result.email);
                    notify('主邮箱已切换。已有草稿请重新确认收件人。','ok');
                } catch (err) { if(current()) { b.disabled = false; notify(err.message,'error'); } }
            };
        } catch(err) { if(current()) panel.innerHTML = `<div class="metadata-card-header">邮箱别名</div><p>${escape(err.message)}</p>`; }
    }
    let tooltip;
    function hideTip(){ if(tooltip) tooltip.hidden = true; }
    function showTip(e){
        const el=e.target.closest && e.target.closest('[data-email-hint]'); if(!el)return;
        if(!tooltip){tooltip=document.createElement('div');tooltip.className='expert-email-tooltip';tooltip.setAttribute('role','tooltip');document.body.appendChild(tooltip);}
        tooltip.textContent=el.dataset.emailHint;tooltip.hidden=false;
        const rect=el.getBoundingClientRect();tooltip.style.left=Math.max(8,Math.min(rect.left,global.innerWidth-tooltip.offsetWidth-8))+'px';tooltip.style.top=Math.max(8,Math.min(rect.bottom+8,global.innerHeight-tooltip.offsetHeight-8))+'px';
    }
    document.addEventListener('pointerover',showTip);document.addEventListener('focusin',showTip);
    document.addEventListener('pointerout',hideTip);document.addEventListener('focusout',hideTip);document.addEventListener('scroll',hideTip,true);
    document.addEventListener('keydown',e=>{if(e.key==='Escape')hideTip();});
    global.ExpertMailSettings = {emailMarkup,mountVariables,mountAliases};
})(window);
