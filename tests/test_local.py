"""Local storage and backup regression tests. All records are synthetic; external requests are blocked."""
from pathlib import Path
from urllib.parse import urlparse
import base64
import json
import os
import shutil
from importlib.metadata import version
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
ORIGIN = 'https://appassets.androidplatform.net/assets/www/'


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=os.environ.get('CHROME_PATH') or shutil.which('chromium') or shutil.which('chromium-browser') or shutil.which('google-chrome'), args=['--no-sandbox'])
        context = browser.new_context(user_agent='NadoFoss/1', viewport={'width': 412, 'height': 915}, device_scale_factor=2, service_workers='block')
        external, errors = [], []
        def route(r):
            parsed = urlparse(r.request.url)
            if parsed.netloc != 'appassets.androidplatform.net':
                external.append(r.request.url); r.abort(); return
            path = ROOT / parsed.path.removeprefix('/assets/www/')
            if path.is_file():
                mime = 'text/html' if path.suffix == '.html' else 'application/javascript' if path.suffix == '.js' else 'text/css' if path.suffix == '.css' else None
                r.fulfill(path=path, content_type=mime)
            else: r.fulfill(status=404, body='Not found')
        context.route('**/*', route)
        page = context.new_page()
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.on('dialog', lambda dialog: dialog.accept())
        page.goto(ORIGIN + 'index.html')
        page.wait_for_function("()=>document.getElementById('countMetric').textContent==='0'")
        assert page.locator('#authScreen').count() == 0
        assert page.locator('[id*=music]').count() == 0
        assert page.locator('body').evaluate('(body)=>body.scrollWidth<=innerWidth')
        page.locator('#startBtn').click()
        page.locator('#notes').fill('Sessão de exemplo')
        page.locator('#sessionForm button[type=submit]').click()
        page.wait_for_function("()=>document.getElementById('countMetric').textContent==='1'")
        page.locator('#weightKg').fill('70')
        page.locator('#weightNotes').fill('<img src=x onerror="window.injected=true">')
        page.locator('#weightForm button[type=submit]').click()
        page.wait_for_function("()=>document.getElementById('weightList').textContent.includes('70,0')")
        assert page.locator('#weightList img').count() == 0
        assert not page.evaluate('Boolean(window.injected)')
        page.locator('#activityForm button[type=submit]').click()
        page.wait_for_function("()=>state.activities.length===1")
        page.reload()
        page.wait_for_function("()=>state.sessions.length===1&&state.weights.length===1&&state.activities.length===1")
        page.locator('#settingsBtn').click()
        page.locator('#backupEmail').fill('test@example.invalid')
        password = 'synthetic-password-123'
        page.locator('#backupPassword').fill(password)
        text = page.evaluate('makeBackup()')
        envelope = json.loads(text)
        assert envelope['encrypted'] and envelope['iterations'] == 310000
        assert len(base64.b64decode(envelope['salt'])) == 16 and len(base64.b64decode(envelope['iv'])) == 12
        assert 'Sessão de exemplo' not in text and password not in text and 'test@example.invalid' not in text
        result = page.evaluate('async ([raw,pw])=>decodeDiary(raw,pw)', [envelope, password])
        assert len(result['sessions']) == len(result['weights']) == len(result['activities']) == 1
        assert page.evaluate('async raw=>{try{await decodeDiary(raw,"wrong");return false}catch(e){return true}}', envelope)
        changed = dict(envelope)
        ciphertext = bytearray(base64.b64decode(changed['data'])); ciphertext[0] ^= 1
        changed['data'] = base64.b64encode(ciphertext).decode()
        assert page.evaluate('async ([raw,pw])=>{try{await decodeDiary(raw,pw);return false}catch(e){return true}}', [changed, password])
        # Refuse adversarial derivation parameters instead of performing unbounded CPU work.
        changed = dict(envelope, iterations=2147483647)
        assert page.evaluate('async ([raw,pw])=>{try{await decodeDiary(raw,pw);return false}catch(e){return true}}', [changed, password])
        page.locator('#eraseDiary').click()
        page.wait_for_function("()=>state.sessions.length===0&&state.weights.length===0&&state.activities.length===0")
        page.locator('#importFile').set_input_files({'name': 'synthetic-backup.json', 'mimeType': 'application/json', 'buffer': text.encode()})
        page.locator('dialog[open] input').fill(password)
        page.locator('dialog[open] button[value=open]').click()
        page.wait_for_function("()=>state.sessions.length===1&&state.weights.length===1&&state.activities.length===1")
        page.locator('#protectBackup').uncheck()
        plain = json.loads(page.evaluate('makeBackup()'))
        assert plain['encrypted'] is False and len(plain['diary']['sessions']) == 1
        # Reject malformed input without changing stored records.
        before = page.evaluate('localStorage.getItem(RECORDS_KEY)')
        page.locator('#importFile').set_input_files({'name':'invalid.json','mimeType':'application/json','buffer':b'{"plan":[],"sessions":[]} '})
        page.wait_for_function("()=>document.getElementById('settingsMessage').textContent.includes('Não foi possível importar')")
        assert page.evaluate('localStorage.getItem(RECORDS_KEY)') == before
        # Screenshots contain only synthetic fixtures, never data copied from a phone.
        page.locator('#settingsMessage').evaluate("el=>el.textContent=''")
        page.locator('#toast').evaluate("el=>{el.classList.remove('show');el.style.display='none'}")
        images = ROOT / 'fastlane/metadata/android/pt-BR/images/phoneScreenshots'
        images.mkdir(parents=True, exist_ok=True)
        page.locator('#backupEmail').fill('')
        page.locator('#protectBackup').check()
        page.locator('#settingsPage').evaluate('(el)=>el.scrollTo(0,0)')
        page.screenshot(path=str(images/'2.png'))
        page.locator('#closeSettings').click()
        page.evaluate('window.scrollTo(0,0)')
        page.screenshot(path=str(images/'1.png'))
        assert not external, external
        assert not errors, errors
        print('PASS: local CRUD, restart persistence, escaped input, encrypted/plain backups, correct/wrong password, tamper detection, import, deletion, no external requests.')
        # Verify legacy on-device migration without contacting or recreating an account.
        legacy = plain['diary']
        page.evaluate('localStorage.clear()')
        page.evaluate('(value)=>localStorage.setItem("nadoMeuRitmo.v1",JSON.stringify(value))', legacy)
        page.reload()
        page.wait_for_function("()=>state.sessions.length===1")
        assert page.evaluate('localStorage.getItem("nadoMeuRitmo.v1")') is None
        print('PASS: legacy local data migration; no remote authentication.')
        browser.close()

if __name__ == '__main__': main()
