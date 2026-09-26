import pathlib,hashlib,copy
from datetime import datetime
root=pathlib.Path('/opt/talent/backups/expert-identity-20260925-restore-02')
assert hashlib.sha256((root/'before.json').read_bytes()).hexdigest()=='af717b4d6058f92ddf5d6ab9cec4e817c9f6556a5ef60bfb766786e1e6eb6489'
assert hashlib.sha256((root/'manifest.json').read_bytes()).hexdigest()=='93dcdcdd094e8e78a713c70e412275727436990705171b3b3b867bcca209ffc4'
b=json.loads((root/'before.json').read_bytes());m=json.loads((root/'manifest.json').read_bytes())
assert len(b['es'])==len(m['es'])==4204
before={(h['_index'],h['_id']):h for h in b['es']};patches={(h['index'],h['id']):h for h in m['es']};contactbefore={c['id']:c for c in b['contacts']}
def save(name,obj):
 path=root/name;path.write_text(json.dumps(obj,ensure_ascii=True,indent=2),encoding='utf-8');os.chmod(str(path),0o600)
def all_docs():
 out={};reqs=[{'_index':h['_index'],'_id':h['_id']} for h in b['es']]
 for i in range(0,len(reqs),300):
  for d in es('_mget',{'docs':reqs[i:i+300]})['docs']:assert d.get('found'),d;out[(d['_index'],d['_id'])]=d
 return out
def table_rows(table,where):
 cols=[l.split('\t')[0] for l in sql('SHOW COLUMNS FROM '+table)['stdout'].splitlines()]
 expr='JSON_OBJECT('+','.join("'"+c+"',`"+c+"`" for c in cols)+')'
 result=sql('SELECT '+expr+' FROM '+table+' WHERE '+where);assert result['exit']==0,result['stderr'];return [json.loads(l) for l in result['stdout'].splitlines()]
def contacts():
 rows=[]
 for i in range(0,len(m['plan']),300):
  emails=','.join("'"+r['email'].replace("'","''")+"'" for r in m['plan'][i:i+300]);rows+=table_rows('expert_contact','expert_email IN ('+emails+')')
 return {c['id']:c for c in rows}
def jobs():
 out=[]
 for i in range(0,len(m['plan']),300):
  ids=','.join("'"+r['id']+"'" for r in m['plan'][i:i+300]);out+=table_rows('expert_academic_enrichment_job','expert_doc_id IN ('+ids+')')
 return {j['id']:j for j in out}
def mail():
 ids=','.join(str(c['id']) for c in b['contacts'])
 r=sql("SELECT expert_contact_id,COUNT(*),COALESCE(SUM(direction='OUTBOUND' AND sent_at IS NOT NULL),0),COALESCE(SUM(direction='INBOUND'),0),BIT_XOR(CRC32(CONCAT_WS('|',id,direction,COALESCE(subject,''),COALESCE(body,''),COALESCE(sent_at,''),COALESCE(received_at,''),COALESCE(send_status,'')))) FROM mail_record WHERE expert_contact_id IN ("+ids+") GROUP BY expert_contact_id");assert r['exit']==0,r['stderr'];return r['stdout']
def expected(k):
 r=copy.deepcopy(before[k]['_source']);r.update(patches[k]['patch']);return r
def bulk(items):
 data=('\n'.join(json.dumps(line) for line in items)+'\n').encode()
 req=urllib.request.Request(setting('ES_BASE_URL')+'/_bulk?refresh=wait_for',data,{'Content-Type':'application/x-ndjson','Authorization':'Basic '+base64.b64encode((setting('ES_USERNAME')+':'+setting('ES_PASSWORD')).encode()).decode()})
 return json.load(urllib.request.urlopen(req,timeout=90))
def literal(v):
 if v is None:return 'NULL'
 return 'CONVERT(0x'+str(v).encode('utf-8').hex()+' USING utf8mb4) COLLATE utf8mb4_unicode_ci'
