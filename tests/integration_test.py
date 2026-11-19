#!/usr/bin/env python3
"""HTTP integration checks against a running LOCAL DEMO; creates uniquely named demo records."""
import argparse
import base64
import concurrent.futures
import html
import http.cookiejar
import re
import time
import urllib.error
import urllib.parse
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base-url', default='http://127.0.0.1:8080')
args = parser.parse_args()
BASE = args.base_url.rstrip('/')
checks = 0
TAG = 't' + str(time.time_ns())[-13:]
PASSWORD = 'Test@1234567'


def check(condition, message):
    global checks
    if not condition:
        raise AssertionError(message)
    checks += 1
    print('PASS:', message)


class Client:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.csrf = None

    def request(self, path, data=None, method=None, raw=None, content_type=None):
        body = raw
        headers = {}
        if data is not None:
            body = urllib.parse.urlencode(data).encode()
        if content_type:
            headers['Content-Type'] = content_type
        request = urllib.request.Request(BASE + path, data=body, headers=headers, method=method)
        try:
            response = self.opener.open(request, timeout=25)
        except urllib.error.HTTPError as error:
            response = error
        content = response.read()
        text = content.decode('utf-8', errors='replace')
        match = re.search(r'name="csrf" value="([a-f0-9]+)"', text)
        if match:
            self.csrf = match.group(1)
        return response.status, text, response.headers, response.geturl(), content

    def get(self, path):
        return self.request('/app' + path)

    def post(self, path, **data):
        data.setdefault('csrf', self.csrf or '')
        return self.request('/app' + path, data=data)

    def login(self, username, password):
        self.get('/login')
        return self.post('/login', username=username, password=password)

    def multipart(self, path, fields, filename=None, image=None):
        boundary = '----Boundary' + TAG
        parts = []
        fields = dict(fields, csrf=self.csrf or '')
        for name, value in fields.items():
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
        if image is not None:
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="image"; filename="{filename}"\r\nContent-Type: image/png\r\n\r\n'.encode() + image + b'\r\n')
        parts.append(f'--{boundary}--\r\n'.encode())
        return self.request('/app' + path, raw=b''.join(parts), content_type='multipart/form-data; boundary=' + boundary)


public = Client()
status, home, headers, _, _ = public.get('/forums')
check(status == 200 and 'The conversation' in home, 'Home page and JSP render')
check('default-src' in headers.get('Content-Security-Policy', ''), 'Content Security Policy present')
check('HttpOnly' in headers.get('Set-Cookie', '') and 'SameSite=Lax' in headers.get('Set-Cookie', ''), 'Session cookie HttpOnly and SameSite')
check('Welcome to a better conversation' in home, 'Seed forums exist')
for path in ['/about', '/contact', '/login', '/register', '/forum?id=1']:
    check(public.get(path)[0] == 200, 'Public page ' + path)
check(public.get('/forum?id=99999999')[0] == 404, 'Missing forum returns 404')
check(public.get('/forum?id=abc')[0] == 400, 'Invalid forum ID returns 400')
check(public.request('/WEB-INF/view.jsp')[0] == 404, 'JSP cannot be accessed directly')
check(public.get('/forums?q=not-a-real-forum-' + TAG)[0] == 200, 'Empty search works')
check(public.post('/register', csrf='bad', username=TAG)[0] == 403, 'Invalid CSRF rejected')
public.get('/register')
check(public.post('/register', username=TAG, full_name='Test', email=TAG+'@example.test', password='short')[0] == 400, 'Short password rejected')
check(public.post('/register', username=TAG, full_name='Test', email='invalid', password=PASSWORD)[0] == 400, 'Malformed email rejected')
check(public.post('/register', username=TAG, full_name='Test', email=TAG+'@example.test', password=PASSWORD, phone='123')[0] == 400, 'Invalid phone rejected')
check(public.post('/register', username=TAG, full_name='Integration Reader', email=TAG+'@example.test', password=PASSWORD, phone='9876543210')[0] == 200, 'Reader registration succeeds')
check(public.post('/register', username=TAG, full_name='Duplicate', email=TAG+'@example.test', password=PASSWORD)[0] == 409, 'Duplicate account rejected')
reader = Client()
check(reader.login(TAG, 'incorrectpass')[0] == 401, 'Wrong password rejected')
check(reader.login(TAG, PASSWORD)[0] == 200, 'Reader sign-in succeeds')
for path in ['/users', '/moderation', '/dictionary', '/audit', '/edit-forum', '/verifier']:
    check(reader.get(path)[0] == 403, 'Reader forbidden from ' + path)
check(reader.post('/account', id=3, action='unblock')[0] == 403, 'Reader cannot call staff mutation')
check(reader.post('/comment', id=1, body='   ')[0] == 400, 'Empty comment rejected server-side')
check(reader.post('/comment', id=1, body='x'*3001)[0] == 400, 'Overlong comment rejected')
clean = 'A helpful contribution ' + TAG
check(reader.post('/comment', id=1, body=clean)[0] == 200, 'Clean comment accepted')
check(clean in public.get('/forum?id=1')[1], 'Clean comment published publicly')
xss = '<script>alert("hello")</script> ' + TAG
check(reader.post('/comment', id=1, body=xss)[0] == 200, 'Plain text with markup accepted')
page = public.get('/forum?id=1')[1]
check(html.escape(xss, quote=True).replace('&#x27;', '&#39;') in page and xss not in page, 'Stored comment markup is escaped')
check(reader.post('/profile', full_name='Updated Reader', email=TAG+'@example.test', phone='9876543210', city='Hyderabad', state='Telangana', address='Test address', birth_date='2000-01-01', gender='Prefer not to say')[0] == 200, 'Profile update succeeds')
check('Updated Reader' in reader.get('/profile')[1], 'Profile changes persisted')
flagged = 'SPAM! needs review ' + TAG
reply = reader.post('/comment', id=1, body=flagged)
check(reply[0] == 200 and 'Warning 1 of 4' in reply[1], 'Case and punctuation flag first warning')
check(flagged not in public.get('/forum?id=1')[1], 'Pending comment is hidden from public')
other_reader = Client();other_reader.login('user', 'User@12345')
check(flagged not in other_reader.get('/forum?id=1')[1], 'Pending comment hidden from another reader')
admin = Client(); verifier = Client()
check(admin.login('admin', 'Admin@12345')[0] == 200, 'Admin sign-in succeeds')
check(verifier.login('verifier', 'Verifier@12345')[0] == 200, 'Verifier sign-in succeeds')
for client, paths in [(admin, ['/dashboard','/moderation','/users','/edit-forum','/verifier','/dictionary','/audit','/profile']), (verifier, ['/dashboard','/moderation','/users','/dictionary','/audit'])]:
    for path in paths:
        check(client.get(path)[0] == 200, 'Staff page ' + path)
check(verifier.get('/edit-forum')[0] == 403, 'Verifier cannot create forums')
check(verifier.post('/verifier', username='illegal')[0] == 403, 'Verifier cannot create staff')
queue = verifier.get('/moderation')[1]
block = next(x for x in queue.split('<article class="panel review-card">') if flagged in x)
comment_id = re.search(r'name="id" value="(\d+)"', block).group(1)
check(verifier.post('/review', id=comment_id, decision='PUBLISHED', note='Valid in this context')[0] == 200, 'Verifier approves held comment')
check(flagged in public.get('/forum?id=1')[1], 'Approved comment becomes public')
check(verifier.post('/review', id=comment_id, decision='REJECTED')[0] == 400, 'Already-reviewed comment cannot be changed by duplicate review')
for n in (2, 3):
    reply = reader.post('/comment', id=1, body='scam '+TAG+' '+str(n))
    check(reply[0] == 200 and f'Warning {n} of 4' in reply[1], f'Flagged submission {n} increments once')
reply = reader.post('/comment', id=1, body='spam scam idiot '+TAG+' fourth')
check(reply[0] == 200 and 'blocked after 4' in reply[1], 'Fourth flagged submission blocks account')
check(reader.login(TAG, PASSWORD)[0] == 401, 'Blocked account cannot sign in')
users = verifier.get('/users')[1]
user_row = next(row for row in users.split('<tr>') if '@'+TAG in row)
user_id = re.search(r'name="id" value="(\d+)"', user_row).group(1)
check(verifier.post('/account', id=user_id, action='unblock')[0] == 200, 'Verifier restores blocked reader')
check(reader.login(TAG, PASSWORD)[0] == 200, 'Restored reader can sign in')
check(re.search(r'Your warnings:\s*<strong>\s*0\s*/\s*4', reader.get('/dashboard')[1]) is not None, 'Restore resets strikes to zero')
queue = verifier.get('/moderation')[1]
block = next(x for x in queue.split('<article class="panel review-card">') if TAG+' fourth' in x)
reject_id = re.search(r'name="id" value="(\d+)"', block).group(1)
check(verifier.post('/review', id=reject_id, decision='REJECTED', note='Not suitable')[0] == 200, 'Verifier rejects held comment')
check('spam scam idiot '+TAG+' fourth' not in public.get('/forum?id=1')[1], 'Rejected comment stays private')
check(admin.post('/category', name='Topic '+TAG)[0] == 200, 'Admin creates forum type')
edit = admin.get('/edit-forum')[1]
category_id = re.search(r'value="(\d+)"[^>]*>\s*Topic '+TAG, edit).group(1)
# One-pixel PNG tests image decode, normalized storage, and image delivery.
png = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a3ioAAAAASUVORK5CYII=')
fields = dict(title='Uploaded forum '+TAG, body='A body for the uploaded image test.', category_id=category_id)
reply = admin.multipart('/edit-forum', fields, 'cover.png', png)
check(reply[0] == 200 and 'Forum saved successfully' in reply[1], 'Admin creates forum with PNG upload')
forum_id = re.search(r'/forum\?id=(\d+)', reply[3]).group(1)
image_reply = public.get('/image?id='+forum_id)
check(image_reply[0] == 200 and image_reply[2].get('Content-Type') == 'image/png' and image_reply[4][:8] == b'\x89PNG\r\n\x1a\n', 'Uploaded image is served as PNG')
fields.update(id=forum_id, title='Edited forum '+TAG)
check(admin.multipart('/edit-forum', fields)[0] == 200, 'Admin edits forum')
check('Edited forum '+TAG in public.get('/forum?id='+forum_id)[1], 'Forum edit persisted')
check(admin.multipart('/edit-forum', fields, 'fake.png', b'<script>bad</script>')[0] == 400, 'Fake image rejected')
check(admin.multipart('/edit-forum', fields, 'huge.png', b'x'*(2*1024*1024+1))[0] == 400, 'Oversize image rejected')
check(verifier.post('/delete-forum', id=forum_id)[0] == 200, 'Verifier removes forum')
check(public.get('/forum?id='+forum_id)[0] == 404, 'Removed forum hidden')
check(public.get('/image?id='+forum_id)[0] == 404, 'Removed forum image hidden')
check(admin.post('/verifier', username='v'+TAG, full_name='New Verifier', email='v'+TAG+'@example.test', password=PASSWORD)[0] == 200, 'Admin creates verifier')
new_verifier = Client()
check(new_verifier.login('v'+TAG, PASSWORD)[0] == 200 and new_verifier.get('/moderation')[0] == 200, 'New verifier can access moderation')
check(verifier.post('/account', id=1, action='block')[0] == 400, 'Verifier cannot block admin')
check(verifier.post('/account', id=user_id, action='block')[0] == 200, 'Manual block succeeds')
reply = reader.post('/comment', id=1, body='Cannot post while blocked '+TAG)
check('blocked or inactive' in reply[1] and 'Cannot post while blocked '+TAG not in public.get('/forum?id=1')[1], 'Existing blocked session cannot post')
check(verifier.post('/account', id=user_id, action='unblock')[0] == 200, 'Manual restore succeeds')
reader.login(TAG, PASSWORD)
check(verifier.post('/account', id=user_id, action='deactivate')[0] == 200, 'Verifier deactivates reader without deleting history')
check(reader.login(TAG, PASSWORD)[0] == 401, 'Deactivated account cannot sign in')
check(clean in public.get('/forum?id=1')[1], 'Deactivation retains published history')
check('ACCOUNT_AUTO_BLOCKED' in admin.get('/audit')[1], 'Automatic block recorded in audit log')
check('COMMENT_REJECTED' in admin.get('/audit')[1], 'Review decision recorded in audit log')
dictionary_page = admin.get('/dictionary')[1]
original_csv = html.unescape(re.search(r'<textarea[^>]*name="csv"[^>]*>(.*?)</textarea>', dictionary_page, re.S).group(1))
new_term = 'qaterm' + TAG
check(admin.post('/dictionary', csv=original_csv+'\n'+new_term+'\n')[0] == 200, 'Admin updates CSV from web editor')
check(new_term in verifier.get('/dictionary')[1], 'Updated dictionary visible to verifier')
check(verifier.post('/dictionary', csv='term\nbypass')[0] == 403, 'Verifier cannot update dictionary')
check(admin.post('/dictionary', csv='term\n"unclosed')[0] == 400, 'Malformed dictionary rejected without replacement')
check(admin.post('/dictionary', csv='term\n')[0] == 400, 'Empty dictionary rejected without replacement')
check(new_term in admin.get('/dictionary')[1], 'Invalid save preserves existing dictionary')
verifier.post('/account', id=user_id, action='unblock')
reader.login(TAG, PASSWORD)
reply = reader.post('/comment', id=1, body=new_term)
check(reply[0] == 200 and 'Warning 1 of 4' in reply[1], 'Dictionary update applies to next comment without restart')
check(admin.post('/dictionary', csv=original_csv)[0] == 200, 'Original dictionary restored')
check(verifier.post('/logout')[0] == 200 and '/login' in verifier.get('/moderation')[3], 'Sign-out invalidates staff session')
print(f'\nPASS: {checks} HTTP integration checks. Demo fixture prefix: {TAG}')
