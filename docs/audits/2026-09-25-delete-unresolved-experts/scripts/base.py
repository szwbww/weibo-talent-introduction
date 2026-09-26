import os,re,subprocess,json,urllib.request,base64,sys
s=open('/opt/apache-tomcat-9.0.71/webapps/talent/WEB-INF/classes/application.yml',encoding='utf-8').read();env={}
for pid in os.listdir('/proc'):
 if not pid.isdigit(): continue
 try:
  if b'org.apache.catalina.startup.Bootstrap' in open('/proc/'+pid+'/cmdline','rb').read():
   env.update(dict(x.decode().split('=',1) for x in open('/proc/'+pid+'/environ','rb').read().split(b'\0') if b'=' in x))
 except (OSError,UnicodeDecodeError): pass
def setting(key):
 m=re.search(r'\$\{'+key+r':([^}]*)\}',s);return env.get(key,m.group(1) if m else '')
def sql(q):
 e=os.environ.copy();e['MYSQL_PWD']=setting('DB_PASSWORD')
 p=subprocess.run(['mysql','-u',setting('DB_USERNAME') or 'root','--default-character-set=utf8mb4','-N','-B','--raw','talent_introduction','-e',q],env=e,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 return {'stdout':p.stdout.decode('utf-8'),'stderr':p.stderr.decode('utf-8'),'exit':p.returncode}
def es(path,body=None):
 r=urllib.request.Request(setting('ES_BASE_URL')+'/'+path,json.dumps(body).encode() if body is not None else None,{'Content-Type':'application/json','Authorization':'Basic '+base64.b64encode((setting('ES_USERNAME')+':'+setting('ES_PASSWORD')).encode()).decode()})
 return json.load(urllib.request.urlopen(r,timeout=45))
indices=','.join(setting(k) for k in ['ES_RAW_INDEX_NAME','ES_CANDIDATE_INDEX_NAME','ES_APPLICATION_INDEX_NAME'])
