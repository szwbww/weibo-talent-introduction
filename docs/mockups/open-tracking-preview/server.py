"""Loopback-only preview. Serves allowlisted static files; no application APIs."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit
import mimetypes

HERE = Path(__file__).resolve().parent
STATIC = HERE.parents[2] / 'src/main/resources/static'
FILES = {'/': HERE / 'index.html', '/index.html': HERE / 'index.html',
         '/preview.css': HERE / 'preview.css', '/preview.js': HERE / 'preview.js'}
for name in ['styles.css', 'expert-materials.css', 'mailbox-chat.css', 'meeting-confirmation.css', 'world-clock.css']:
    FILES['/assets/' + name] = STATIC / name

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = FILES.get(urlsplit(self.path).path)
        if path is None or not path.is_file():
            self.send_error(404)
            return
        data = path.read_bytes()
        self.send_response(200)
        self.send_header('Content-Type', (mimetypes.guess_type(str(path))[0] or 'application/octet-stream') + '; charset=utf-8')
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Security-Policy', "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'; img-src 'self' data:; connect-src 'none'; font-src 'self'; form-action 'none'")
        self.end_headers()
        self.wfile.write(data)

if __name__ == '__main__':
    print('Open tracking preview: http://127.0.0.1:18765', flush=True)
    ThreadingHTTPServer(('127.0.0.1', 18765), Handler).serve_forever()
