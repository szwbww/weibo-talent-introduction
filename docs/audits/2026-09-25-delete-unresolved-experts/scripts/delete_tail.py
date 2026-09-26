import pathlib,hashlib
from datetime import datetime
root=pathlib.Path('/opt/talent/backups/expert-identity-20260925-delete-unresolved-1924')
raw=(root/'before.json').read_bytes();assert hashlib.sha256(raw).hexdigest()=='99f99952fb2d33908ced0fdbf137ce1b35bf82baad2d596ece562d0ee9f61882'
b=json.loads(raw);assert len(b['targets'])==1924 and len(b['es'])==3848
assert not (root/'delete-started.json').exists(),'Already attempted; inspect journal before retry'
allTargets=list(b['targets'])
excluded='EMAIL-ecfe6a12ee6c6aec55a'
b['targets']=[r for r in b['targets'] if r['id']!=excluded]
b['es']=[h for h in b['es'] if h['_id']!=excluded]
assert len(b['targets'])==1923 and len(b['es'])==3846
def save(name,obj):
 path=root/name;path.write_text(json.dumps(obj,indent=2,ensure_ascii=True),encoding='utf-8');os.chmod(str(path),0o600)
def docs(requests):
 out=[]
 for i in range(0,len(requests),300):out+=es('_mget',{'docs':requests[i:i+300]})['docs']
 return out
def table_rows(table,where):
 q=sql('SHOW COLUMNS FROM '+table);assert q['exit']==0
 cols=[x.split('\t')[0] for x in q['stdout'].splitlines()];expr='JSON_OBJECT('+','.join("'"+c+"',`"+c+"`" for c in cols)+')'
 q=sql('SELECT '+expr+' FROM '+table+' WHERE '+where);assert q['exit']==0,q['stderr'];return [json.loads(l) for l in q['stdout'].splitlines()]
def contacts():
 out=[]
 for start in range(0,len(allTargets),200):
  emails=','.join("'"+r['email'].replace("'","''")+"'" for r in allTargets[start:start+200]);out+=table_rows('expert_contact','expert_email IN ('+emails+')')
 return {r['id']:r for r in out}
def jobs():
 out=[]
 for start in range(0,len(allTargets),200):
  ids=','.join("'"+r['id']+"'" for r in allTargets[start:start+200]);out+=table_rows('expert_academic_enrichment_job','expert_doc_id IN ('+ids+')')
 return {r['id']:r for r in out}
def mail():
 ids=','.join(str(c['id']) for c in b['contacts']);q=sql("SELECT expert_contact_id,COUNT(*),COALESCE(SUM(direction='OUTBOUND' AND sent_at IS NOT NULL),0),COALESCE(SUM(direction='INBOUND'),0),BIT_XOR(CRC32(CONCAT_WS('|',id,direction,COALESCE(subject,''),COALESCE(body,''),COALESCE(sent_at,''),COALESCE(received_at,''),COALESCE(send_status,'')))) FROM mail_record WHERE expert_contact_id IN ("+ids+") GROUP BY expert_contact_id");assert q['exit']==0,q['stderr'];return q['stdout']
def fingerprint():
 req=[{'_index':idx,'_id':i} for i in protected for idx in indices.split(',')];out={}
 for d in docs(req):
  key=d['_index']+'/'+d['_id'];out[key]=hashlib.sha256(json.dumps(d.get('_source'),sort_keys=True,ensure_ascii=True).encode()).hexdigest() if d.get('found') else None
 return out
def bulk(lines):
 data=('\n'.join(json.dumps(l) for l in lines)+'\n').encode();req=urllib.request.Request(setting('ES_BASE_URL')+'/_bulk?refresh=wait_for',data,{'Content-Type':'application/x-ndjson','Authorization':'Basic '+base64.b64encode((setting('ES_USERNAME')+':'+setting('ES_PASSWORD')).encode()).decode()});return json.load(urllib.request.urlopen(req,timeout=90))
assert contacts()=={c['id']:c for c in b['contacts']},'Contacts changed'
assert jobs()=={j['id']:j for j in b['jobs']},'Jobs changed'
assert mail()==b['mailBaseline']['stdout'],'Mail changed'
a=sql("SELECT id,task_type,status FROM task_execution WHERE status='RUNNING'");assert a['exit']==0
activeRows=[line.split('\t') for line in a['stdout'].splitlines()]
assert all(row[1]=='AUTO_REPLY_ALL' for row in activeRows),('Concurrent task',activeRows)
assert all(int(line.split('\t')[3])==0 for line in b['mailBaseline']['stdout'].splitlines()),'Target has inbound mail; do not overlap auto replies'
save('concurrent-task-observation.json',{'at':datetime.now().isoformat(),'tasks':activeRows,'targetInboundCount':0})
req=[{'_index':idx,'_id':r['id']} for r in b['targets'] for idx in indices.split(',')];before={(h['_index'],h['_id']):h for h in b['es']};current=docs(req)
assert { (h['_index'],h['_id']) for h in current if h.get('found')}==set(before)
for h in current:
 if not h.get('found'):continue
 old=before[(h['_index'],h['_id'])];assert h['_source']==old['_source'] and h['_seq_no']==old['_seq_no'] and h['_primary_term']==old['_primary_term'],'Expert changed'
assert not set(protected)&{r['id'] for r in b['targets']}
keep=fingerprint();save('protected-source-hashes.json',keep)
save('delete-started.json',{'time':datetime.now().isoformat(),'experts':1923,'documents':3846})
try:
 for i in range(0,len(b['es']),200):
  batch=b['es'][i:i+200];response=bulk([{'delete':{'_index':h['_index'],'_id':h['_id'],'if_seq_no':h['_seq_no'],'if_primary_term':h['_primary_term']}} for h in batch]);save('delete-batch-'+str(i)+'.json',response)
  assert not response.get('errors') and all(x['delete']['status']==200 for x in response['items']),response
  print(json.dumps({'stage':'deleted','documents':min(i+200,len(b['es'])),'total':3846}),flush=True)
 remaining=[h for h in docs(req) if h.get('found')];assert not remaining,'Targets remain'
 assert contacts()=={c['id']:c for c in b['contacts']},'Contacts changed after delete'
 assert jobs()=={j['id']:j for j in b['jobs']},'Jobs changed after delete'
 assert mail()==b['mailBaseline']['stdout'],'Mail changed after delete'
 assert fingerprint()==keep,'Protected expert data changed'
 result={'status':'PASS','at':datetime.now().isoformat(),'expertsDeleted':1923,'documentsDeleted':3846,'contactsPreserved':len(b['contacts']),'mailUnchanged':True,'academicJobsPreserved':len(b['jobs']),'protectedExpertsUnchanged':len(protected),'excludedPreviouslyConfirmedId':excluded,'affectedContactsPreserved':sum(c['expert_email'].lower() in {r['email'] for r in b['targets']} for c in b['contacts']),'backupPath':str(root/'before.json'),'backupSha256':hashlib.sha256(raw).hexdigest()};save('delete-complete.json',result);print(json.dumps(result),flush=True)
except Exception as exc:
 save('delete-failure.json',{'error':str(exc),'time':datetime.now().isoformat()})
 # Restore only missing exact documents with create semantics; never overwrite any concurrent writer.
 missing=[h for h in docs(req) if not h.get('found') and (h['_index'],h['_id']) in before];failures=[]
 for start in range(0,len(missing),200):
  lines=[]
  for h in missing[start:start+200]:lines += [{'create':{'_index':h['_index'],'_id':h['_id']}},before[(h['_index'],h['_id'])]['_source']]
  rr=bulk(lines);save('rollback-batch-'+str(start)+'.json',rr)
  if rr.get('errors'):failures.append(start)
 restored={ (h['_index'],h['_id']):h for h in docs(req) if h.get('found')};same=sum(k in restored and restored[k]['_source']==v['_source'] for k,v in before.items());save('rollback-result.json',{'restoredOrUnchanged':same,'total':3846,'failedBatches':failures});print(json.dumps({'stage':'ROLLBACK','restoredOrUnchanged':same,'failedBatches':failures}),flush=True);raise
