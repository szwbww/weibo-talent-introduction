const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/meeting-records.js'),'utf8');
const flush=async()=>{for(let i=0;i<15;i++)await Promise.resolve()};
function harness(options={}) {
 const elements=new Map(),listeners={},requests=[],uploads=[];let saved=null;
 const node=id=>{if(!elements.has(id))elements.set(id,{id,value:'',textContent:'',innerHTML:'',hidden:false,isConnected:true,disabled:false,open:false,dataset:{},listeners:{},addEventListener(t,f){this.listeners[t]=f},querySelectorAll(){return []},showModal(){this.open=true},close(){this.open=false;this.listeners.close?.()},pause(){},load(){},removeAttribute(){},reset(){for(const e of Object.values(this.elements||{}))e.value=''}});return elements.get(id)};
 const form=node('meetingForm');form.elements=Object.fromEntries(['title','time','participants','minutes'].map(k=>[k,{value:''}]));
 const api=async(url,opts={})=>{requests.push({url,...opts});if(options.api){const value=options.api(url,opts);if(value!==undefined)return value}if(opts.method==='PUT'){saved={...JSON.parse(opts.body),id:'meeting-id',version:1};return saved}if(url.includes('meeting-records'))return {items:saved?[{...saved,participants:saved.participants||[]}]:[],total:saved?1:0};return {items:[],total:0}};
 class XHR {constructor(){this.upload={};this.status=200;this.responseText='{}'}open(method,url){this.url=url}setRequestHeader(){}send(file){uploads.push({url:this.url,file});if(options.failUpload){this.status=500;this.responseText=JSON.stringify({message:'磁盘暂不可用'})}else this.responseText=JSON.stringify({id:'video-id',displayFilename:'video.mp4',version:1});this.onload()}abort(){this.onabort?.()}}
 const sandbox={document:{getElementById:node,addEventListener(t,f){listeners[t]=f}},window:{addEventListener(){}},location:{href:'http://localhost/',origin:'http://localhost'},crypto:{randomUUID:()=> 'meeting-id'},contextPath:'',api,URL,URLSearchParams,XMLHttpRequest:XHR};
 vm.runInNewContext(source,sandbox);
 const host=node('host');const session={host:{querySelector:()=>host},expertId:'expert-a',level:'APPLICATION',facts:{expertName:'测试专家'},closed:false};sandbox.window.ExpertMeetingRecords.mount(session);
 function click(attrs){listeners.click({target:{closest:()=>({id:attrs.id,dataset:attrs.dataset||{},hasAttribute:k=>!!attrs[k]})}})}
 function open(){click({'data-meeting-add':true});form.elements.title.value='技术会议';form.elements.participants.value='张老师、李经理、张老师';form.elements.minutes.value='  会议结论  '}
 const submit=()=>form.listeners.submit({preventDefault(){}});
 return {node,form,host,session,requests,uploads,click,open,submit,api:sandbox.window.ExpertMeetingRecords};
}
test('meeting save persists metadata and renders returned record, without localStorage or sample data',async()=>{
 const h=harness();await flush();h.open();await h.submit();
 const writes=h.requests.filter(r=>r.method==='PUT');assert.equal(writes.length,1);
 const input=JSON.parse(writes[0].body);assert.equal(input.expertId,'expert-a');assert.equal(input.level,'APPLICATION');assert.deepEqual(input.participants,['张老师','李经理']);assert.equal(input.minutes,'会议结论');
 assert.match(h.host.innerHTML,/技术会议/);assert.match(h.host.innerHTML,/张老师/);assert.equal(h.node('meetingEditor').open,false);
});
test('video upload failure retains saved metadata and pending file for retry',async()=>{
 const h=harness({failUpload:true});await flush();h.open();h.node('videoInput').listeners.change({target:{files:[{name:'video.mp4',size:16}]}});await h.submit();
 assert.equal(h.uploads.length,1);assert.match(h.uploads[0].url,/meetingRecordId=meeting-id/);assert.match(h.node('meetingError').textContent,/会议记录已保存/);assert.equal(h.node('meetingEditor').open,true);assert.match(h.node('fileQueue').innerHTML,/video/);
});
test('over-1-GiB file is rejected before upload; minute-only save remains allowed',async()=>{
 const h=harness();await flush();h.open();h.node('videoInput').listeners.change({target:{files:[{name:'large.mp4',size:1073741825}]}});
 assert.match(h.node('meetingError').textContent,/1 GiB/);assert.equal(h.node('fileCount').textContent,'0 个');await h.submit();assert.equal(h.uploads.length,0);
});
test('switching expert while metadata saves suppresses the later video upload',async()=>{
 let resolve;const pending=new Promise(r=>resolve=r);const h=harness({api:(url,opts)=>opts.method==='PUT'?pending:undefined});await flush();h.open();h.node('videoInput').listeners.change({target:{files:[{name:'video.mp4',size:16}]}});
 const save=h.submit();h.session.closed=true;h.api.unmount();resolve({id:'meeting-id',version:1});await save;assert.equal(h.uploads.length,0);
});
test('out-of-order record refresh cannot overwrite newer response',async()=>{
 const pending=[];const h=harness({api:(url,opts)=>url.includes('meeting-records')?new Promise(resolve=>pending.push(resolve)):undefined});h.click({'data-meeting-refresh':true});
 pending[1]({items:[{id:'new',title:'最新会议',participants:[],minutes:''}],total:1});await flush();pending[0]({items:[],total:0});await flush();assert.match(h.host.innerHTML,/最新会议/);
});
test('metadata failure does not claim that this edit was saved or upload any file',async()=>{
 const h=harness({api:(url,opts)=>opts.method==='PUT'?Promise.reject(Error('版本冲突')):undefined});await flush();h.open();await h.submit();assert.match(h.node('meetingError').textContent,/版本冲突/);assert.doesNotMatch(h.node('meetingError').textContent,/会议记录已保存/);assert.equal(h.uploads.length,0);assert.equal(h.node('meetingEditor').open,true);
});
