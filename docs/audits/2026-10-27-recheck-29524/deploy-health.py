import os,pathlib,json,hashlib,urllib.request,urllib.error,datetime,re,subprocess
root=pathlib.Path('/opt/apache-tomcat-9.0.71');incoming=root/'deploy/incoming/recheck-29524'
r=json.loads((incoming/'receipt.json').read_text());m=json.loads((incoming/'manifest.json').read_text())
base=root/'webapps/talent'
matched=sum(1 for n,h in m['files'].items() if (base/n).exists() and hashlib.sha256((base/n).read_bytes()).hexdigest()==h)
print('CLASS_MATCH',matched,len(m['files']))
assert matched==len(m['files'])
assert hashlib.sha256((base/'WEB-INF/classes/application.yml').read_bytes()).hexdigest()==r['configSha']
pids=[]
for p in pathlib.Path('/proc').glob('[0-9]*'):
 try:
  if b'org.apache.catalina.startup.Bootstrap' in (p/'cmdline').read_bytes().split(b'\0'):pids.append(p)
 except FileNotFoundError:pass
assert len(pids)==1 and int(pids[0].name)!=r['oldPid']
print('NEW_PID',pids[0].name)
values=dict(x.split('=',1) for x in (pids[0]/'environ').read_bytes().decode().split('\0') if '=' in x)
u=re.match(r'jdbc:mysql://([^/:?]+)(?::([0-9]+))?/([^?]+)',values['DB_URL'])
cmd=['mysql','--connect-timeout=5','--quick','--host='+u.group(1),'--port='+(u.group(2) or '3306'),'--user='+values['DB_USERNAME'],'--database='+u.group(3),'--batch','--raw','--skip-column-names']
sql="SET SESSION MAX_EXECUTION_TIME=5000; SET SESSION TRANSACTION READ ONLY; START TRANSACTION; SELECT execution_id,revision,input_closed,JSON_UNQUOTE(JSON_EXTRACT(recheck_json,'$.control.phase')),JSON_UNQUOTE(JSON_EXTRACT(recheck_json,'$.control.scopeHash')) FROM discovery_verification_run WHERE execution_id=29524; SELECT id,status,success_count,failure_count FROM task_execution WHERE id=29524; SELECT state,COUNT(*),SUM(request_count),SUM(admission_revision IS NOT NULL) FROM discovery_verification_item WHERE owner_execution_id=29524 GROUP BY state; COMMIT;"
p=subprocess.run(cmd,input=sql,stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True,env=dict(os.environ,MYSQL_PWD=values['DB_PASSWORD']),timeout=10)
assert p.returncode==0
print('TASK_STATE',p.stdout.strip())
http=[]
for url in ['http://127.0.0.1/talent/','https://qingfei.szwbww.com/talent/','https://qingfei.szwbww.com/talent/api/discovery/rechecks/active']:
 try:
  response=urllib.request.urlopen(urllib.request.Request(url,method='GET'),timeout=8);response.read(1024);code=response.status
 except urllib.error.HTTPError as e:code=e.code
 except Exception as e:code=type(e).__name__
 http.append(code);print('HTTP',url,code)
with (root/'logs/catalina.out').open('rb') as f:
 f.seek(r['catalinaLogOffset']);log=f.read(4*1024*1024).decode('utf-8','replace')
flags={key:key in log for key in ['Application run failed','OutOfMemoryError','NoSuchMethodError','BeanCreationException']}
print('NEW_STARTUP_ERROR_FLAGS',json.dumps(flags))
print('STARTED_MARKER', 'Started TalentIntroductionApplication' in log)
print('TIME',datetime.datetime.now().isoformat())
if http[0:2]==[200,200] and not any(flags.values()):
 r.update(stage='VERIFIED',newPid=int(pids[0].name),verifiedAt=datetime.datetime.now().isoformat(),classMatches=matched,http=http,taskState=p.stdout.strip())
 (incoming/'receipt.json').write_text(json.dumps(r,indent=2))
 print('VERIFIED')
