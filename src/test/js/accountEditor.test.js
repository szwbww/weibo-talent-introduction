const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const staticDir = path.join(__dirname, '../../main/resources/static');
const source = fs.readFileSync(path.join(staticDir, 'app.js'), 'utf8');
const html = fs.readFileSync(path.join(staticDir, 'index.html'), 'utf8');
const modal = html.slice(html.indexOf('<div class="modal-shell" id="accountModal"'), html.indexOf('<!-- Modal 2:'));

function harness() {
    const controls = {}, ids = {};
    const element = () => ({ value: '', hidden: false, disabled: false, checked: false, options: [],
        appendChild(e) { this.options.push(e); }, removeChild() { this.options.shift(); },
        get firstChild() { return this.options[0]; } });
    // Derive controls from the real markup, so a missing field cannot be hidden by a permissive stub.
    for (const match of modal.matchAll(/<(input|select|textarea|button)\b[^>]*>/g)) {
        const tag = match[0], name = tag.match(/\bname="([^"]+)"/)?.[1];
        const e = element();
        if (name) { e.name = name; controls[name] = e; }
    }
    for (const match of modal.matchAll(/\bid="([^"]+)"/g)) ids[match[1]] = element();
    const form = Object.assign(controls, { elements: Object.values(controls) });
    ids.accountForm = form;
    const toggle = element(), requests = [], errors = [];
    const state = { accounts: [{accountCode:'LuKai',senderEmail:'lukai@updates.szwebotech.cn'},
        {accountCode:'LuKai_QF',senderEmail:'lukai@qingfeitalent.com',inboundMailboxCode:'LuKai'}] };
    const ctx = vm.createContext({ state, newAccountDefaults: {smtpPort:465,imapPort:993},
        $: selector => ids[selector.slice(1)],
        document: {createElement:element, body:{classList:{add(){}}},
            querySelector:() => toggle, querySelectorAll:() => []},
        FormData: class { constructor(f) { this.f=f; } entries() {
            return this.f.elements.filter(e => e.name && !e.disabled).map(e => [e.name,e.value]);
        } },
        toDatetimeLocalValue:() => '', updateWarmupFieldsVisibility(){}, updateWarmupStepsMode(){},
        updateAccountStatusBadge(){}, updateEffectiveLimitHint(){}, updateWarmupStatusBadge(){},
        hideAccountEditor(){}, loadAccounts:async()=>{}, showStatus:msg=>errors.push(msg),
        numberValue:(v,d)=>v === undefined || v === '' ? d : Number(v),
        api:async(url,request)=>requests.push({url,payload:JSON.parse(request.body)})
    });
    for (const name of ['showAccountEditor','fillInboundMailboxOptions','updateInboundMailboxFields',
        'fillAccountForm','formValues','saveAccount']) {
        const match = source.match(new RegExp('(?:async )?function '+name+'\\([^]*?\\n}'));
        assert.ok(match, name); vm.runInContext(match[0],ctx);
    }
    return {ctx,form,ids,state,requests,errors,toggle};
}
const alias = {accountCode:'LuKai_QF',senderEmail:'lukai@qingfeitalent.com',senderName:'LuKai',
    inboundMailboxCode:'LuKai',smtpHost:'smtp.example.com',smtpUsername:'alias-login',smtpPort:465,
    imapHost:'old-imap.example.com',imapUsername:'old-login',imapPort:143,enabled:false,
    strategyWeight:100,dailySendLimit:100,todaySentCount:3};

test('actual account markup fills all settings and preserves shared source when saving', async()=>{
    const h=harness(); h.ctx.fillAccountForm(alias);
    assert.equal(h.form.smtpHost.value,alias.smtpHost);
    assert.equal(h.form.inboundMailboxCode.value,'LuKai');
    assert.equal(h.form.imapHost.value,alias.imapHost);
    assert.equal(h.form.imapPassword.disabled,true);
    assert.equal(h.form.enabled.checked,false);
    await h.ctx.saveAccount({preventDefault(){},currentTarget:h.form});
    const p=h.requests[0].payload;
    assert.equal(p.inboundMailboxCode,'LuKai'); assert.equal(p.smtpUsername,'alias-login');
    for(const key of ['imapHost','imapPort','imapUsername']) assert.equal(key in p,false,key);
    assert.equal(p.imapPassword,null); assert.equal(p.enabled,false);
});

test('switching source restores independent inputs and required fields without erasing values',async()=>{
    const h=harness(); h.ctx.fillAccountForm(alias);
    h.form.inboundMailboxCode.value=''; h.ctx.updateInboundMailboxFields();
    assert.equal(h.form.imapHost.disabled,false); assert.equal(h.form.imapHost.required,true);
    assert.equal(h.form.imapHost.value,alias.imapHost); assert.equal(h.ids.inboundMailboxAddress.hidden,true);
    await h.ctx.saveAccount({preventDefault(){},currentTarget:h.form});
    assert.equal(h.requests[0].payload.inboundMailboxCode,null);
    assert.equal(h.requests[0].payload.imapPort,143);
    h.ctx.fillAccountForm(null,'new'); assert.equal(h.form.imapPassword.required,true);
    h.form.inboundMailboxCode.value='LuKai'; h.ctx.updateInboundMailboxFields();
    assert.equal(h.form.imapPassword.required,false);
    await h.ctx.saveAccount({preventDefault(){},currentTarget:h.form});
    assert.equal('imapPassword' in h.requests[1].payload,false);
});

test('view mode remains read-only and missing source field cannot clear stored ownership',async()=>{
    const h=harness(); h.ctx.fillAccountForm(alias,'view');
    assert.equal(h.form.inboundMailboxCode.disabled,true); assert.equal(h.toggle.disabled,true);
    assert.equal(h.ids.saveAccountBtn.hidden,true);
    h.state.accountEditorMode='edit'; delete h.form.inboundMailboxCode;
    await h.ctx.saveAccount({preventDefault(){},currentTarget:h.form});
    assert.equal(h.requests.length,0); assert.match(h.errors[0],/刷新/);
});

test('footer is outside the scrolling form and save still submits that form',()=>{
    const footer=modal.indexOf('<div class="account-form-footer">');
    assert.ok(footer>modal.indexOf('</form>'));
    assert.match(modal, /<button[^>]*type="submit"[^>]*form="accountForm"[^>]*id="saveAccountBtn"/);
});
