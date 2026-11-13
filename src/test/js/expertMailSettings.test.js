const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/expert-mail-settings.js','utf8');
function setup(){
    const window={confirm:()=>true};
    vm.runInNewContext(source,{window,document:{addEventListener(){}},URLSearchParams,Set,Map});
    const panel={isConnected:true,dataset:{},innerHTML:'',querySelectorAll:()=>[],closest:()=>null};
    return {mod:window.ExpertMailSettings,panel};
}
function button(attribute,dataset={}){return {dataset,hasAttribute:n=>n===attribute,closest(){return this;}};}
test('mailbox email count deduplicates current primary and escapes untrusted values',()=>{
    const {mod}=setup();
    const html=mod.emailMarkup({email:'A@example.org',emails:['a@example.org',' b@example.org ','B@example.org']});
    assert.match(html,/有 2 个邮箱/);assert.match(html,/（主邮箱）/);
    assert.doesNotMatch(mod.emailMarkup({email:'one@example.org',emails:['ONE@example.org']}),/expert-email-alert/);
    assert.doesNotMatch(mod.emailMarkup({email:'<img src=x>',emails:['other@example.org']}),/<img/);
});
test('variable edit saves only changed expert keys, restores via omission and keeps scope captured',async()=>{
    const {mod,panel}=setup(),calls=[];
    const api=async(url,opts)=>{
        if(opts){calls.push(JSON.parse(opts.body));return {version:4,values:calls.at(-1).values};}
        if(url.includes('template-variables?'))return [{key:'expertFamilyName',label:'姓氏',value:'Old'},{key:'senderEmail',label:'发件人',value:'sender@test'}];
        return {version:3,values:{},original:{expertFamilyName:'Old'}};
    };
    await mod.mountVariables(panel,'expert-one','CANDIDATE',api,()=>{},['expertFamilyName']);
    await panel.onclick({target:button('data-variable-edit')});
    assert.match(panel.innerHTML,/data-variable-key="expertFamilyName"/);
    assert.doesNotMatch(panel.innerHTML,/data-variable-key="senderEmail"/);
    panel.oninput({target:{dataset:{variableKey:'expertFamilyName'},value:'Corrected'}});
    await panel.onclick({target:button('data-variable-save')});
    assert.deepEqual(calls[0],{expertId:'expert-one',level:'CANDIDATE',expectedVersion:3,values:{expertFamilyName:'Corrected'}});
    await panel.onclick({target:button('data-variable-edit')});
    await panel.onclick({target:button('',{variableRestore:'expertFamilyName'})});
    await panel.onclick({target:button('data-variable-save')});
    assert.deepEqual(calls[1].values,{});
});
test('late variable response cannot overwrite a remounted expert panel',async()=>{
    const {mod,panel}=setup();let release;
    const pending=new Promise(r=>release=r);
    const first=mod.mountVariables(panel,'old','CANDIDATE',async()=>pending,()=>{},['expertFamilyName']);
    await mod.mountVariables(panel,'new','CANDIDATE',async url=>url.includes('template-variables?')?[{key:'expertFamilyName',value:'New',label:'姓氏'}]:{version:0,values:{},original:{expertFamilyName:'New'}},()=>{},['expertFamilyName']);
    const rendered=panel.innerHTML;
    release([]);await first;
    assert.equal(panel.innerHTML,rendered);
});
test('alias rows exclude primary duplicates and submit compare-and-set target',async()=>{
    const {mod,panel}=setup(),calls=[];let current='a@example.org';
    const api=async(url,options)=>{
        if(options){calls.push(JSON.parse(options.body));current='b@example.org';return {email:current};}
        return [{id:1,email:'A@example.org',source:'MANUAL_BIND',verified:true},{id:2,email:'b@example.org',source:'MANUAL_ADD',verified:true}];
    };
    await mod.mountAliases(panel,{id:1,expertEmail:current},api,()=>{},()=>{});
    assert.match(panel.innerHTML,/共 2 个邮箱/);
    assert.doesNotMatch(panel.innerHTML,/data-primary-email="A@example.org"/);
    await panel.onclick({target:button('',{primaryEmail:'b@example.org'})});
    assert.deepEqual(calls,[{email:'b@example.org',expectedEmail:'a@example.org'}]);
    assert.match(panel.innerHTML,/data-primary-display>b@example.org/);
});
