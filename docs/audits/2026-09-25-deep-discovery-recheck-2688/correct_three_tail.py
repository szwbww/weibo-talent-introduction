import pathlib,copy,hashlib
from datetime import datetime
root=pathlib.Path('/opt/talent/backups/expert-identity-20260925-restore-03-correction')
assert not root.exists(),'Inspect existing correction backup before retry'
req=[{'_index':idx,'_id':r['id']} for r in targets for idx in indices.split(',')];hits=[h for h in es('_mget',{'docs':req})['docs'] if h.get('found')];assert len(hits)==6
byid={r['id']:r for r in targets}
for h in hits:
 r=byid[h['_id']];assert h['_source']['email']==r['email'];assert ' '.join(h['_source'].get(k) or '' for k in ['givenNames','familyNames']).strip()==r['wrongRestoredName']
emailset=','.join("'"+r['email']+"'" for r in targets);cr=sql('SELECT id,expert_name FROM expert_contact WHERE expert_email IN ('+emailset+')');assert cr['exit']==0 and not cr['stdout'],cr
root.mkdir(mode=0o700);backup={'time':datetime.now().isoformat(),'targets':targets,'es':hits,'contacts':cr};data=json.dumps(backup,indent=2).encode();(root/'before.json').write_bytes(data);os.chmod(str(root/'before.json'),0o600)
java=None
for pid in os.listdir('/proc'):
 if not pid.isdigit():continue
 try:
  if b'org.apache.catalina.startup.Bootstrap' in open('/proc/'+pid+'/cmdline','rb').read():java=os.readlink('/proc/'+pid+'/exe');break
 except OSError:pass
assert java
patched=[]
for h in hits:
 z=copy.deepcopy(h['_source']);z.update(byid[h['_id']]['patch']);patched.append(z)
helper='/opt/talent/backups/expert-identity-20260925-restore-02'
proc=subprocess.run([java,'-cp',helper+':/opt/apache-tomcat-9.0.71/webapps/talent/WEB-INF/classes:/opt/apache-tomcat-9.0.71/webapps/talent/WEB-INF/lib/*','ClassifyRestore'],input=('\n'.join(json.dumps(z) for z in patched)+'\n').encode(),stdout=subprocess.PIPE,stderr=subprocess.PIPE);assert proc.returncode==0,proc.stderr.decode();classes=[json.loads(l) for l in proc.stdout.splitlines()];assert len(classes)==6
lines=[];expected={}
for h,cls in zip(hits,classes):
 cls['classifiedAt']=cls['classifiedAt'][:19].replace('T',' ');patch=dict(byid[h['_id']]['patch']);patch['expertClassification']=cls;z=copy.deepcopy(h['_source']);z.update(patch);expected[(h['_index'],h['_id'])]=z
 lines += [{'update':{'_index':h['_index'],'_id':h['_id'],'if_seq_no':h['_seq_no'],'if_primary_term':h['_primary_term']}},{'script':{'lang':'painless','source':'for (entry in params.patch.entrySet()) {ctx._source[entry.getKey()] = entry.getValue();}','params':{'patch':patch}}}]
(root/'request.ndjson').write_text('\n'.join(json.dumps(l) for l in lines)+'\n');os.chmod(str(root/'request.ndjson'),0o600)
request=urllib.request.Request(setting('ES_BASE_URL')+'/_bulk?refresh=wait_for',(root/'request.ndjson').read_bytes(),{'Content-Type':'application/x-ndjson','Authorization':'Basic '+base64.b64encode((setting('ES_USERNAME')+':'+setting('ES_PASSWORD')).encode()).decode()});response=json.load(urllib.request.urlopen(request,timeout=60));(root/'response.json').write_text(json.dumps(response,indent=2));os.chmod(str(root/'response.json'),0o600);assert not response.get('errors'),response
fresh=[h for h in es('_mget',{'docs':req})['docs'] if h.get('found')];assert len(fresh)==6
for h in fresh:assert h['_source']==expected[(h['_index'],h['_id'])]
result={'status':'PASS','time':datetime.now().isoformat(),'experts':3,'esDocs':6,'contacts':0,'backupPath':str(root/'before.json'),'backupSha256':hashlib.sha256(data).hexdigest(),'corrections':[{'email':r['email'],'previousWrongRestoredName':r['wrongRestoredName'],'correctName':r['expectedName']} for r in targets],'mailUntouched':True}
(root/'verification.json').write_text(json.dumps(result,indent=2));os.chmod(str(root/'verification.json'),0o600);print(json.dumps(result))
