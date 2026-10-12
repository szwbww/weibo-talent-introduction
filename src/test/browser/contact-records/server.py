"""Local-only UI fixture. Real static files; no production API or credentials."""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[4]
STATIC=ROOT/'src/main/resources/static'
class Handler(SimpleHTTPRequestHandler):
 def do_GET(self):
  name=self.path.split('?')[0].lstrip('/')
  if name in ('','index.html'):
   body=(STATIC/'index.html').read_text().replace('</body>','<script src="fixture.js"></script></body>');typ='text/html'
  elif name=='app.js':
   body=(STATIC/name).read_text().replace('\nbootstrap();','\n/* bootstrap disabled only by local test server */');typ='text/javascript'
  elif name=='fixture.js': body=Path(__file__).with_name('fixture.js').read_text();typ='text/javascript'
  elif name.startswith('api/'):
   body=json.dumps({'items':[],'total':0});typ='application/json'
  else:
   p=STATIC/name
   if not p.is_file() or not p.resolve().is_relative_to(STATIC): self.send_error(404);return
   body=p.read_text();typ='text/css' if name.endswith('.css') else 'text/javascript'
  self.send_response(200);self.send_header('Content-Type',typ+';charset=utf-8');self.end_headers();self.wfile.write(body.encode())
ThreadingHTTPServer(('127.0.0.1',8877),Handler).serve_forever()
