"""Synthetic Health Connect bridge/UI tests; never query health data from a real device."""
from pathlib import Path
from urllib.parse import urlparse
import os
import shutil
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
ORIGIN = 'https://appassets.androidplatform.net/assets/www/'


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=os.environ.get('CHROME_PATH') or shutil.which('chromium') or shutil.which('google-chrome'), args=['--no-sandbox'])
        context = browser.new_context(viewport={'width':390,'height':844}, service_workers='block')
        external, errors = [], []
        def route(r):
            parsed = urlparse(r.request.url)
            if parsed.netloc != 'appassets.androidplatform.net':
                external.append(r.request.url); r.abort(); return
            path = ROOT / parsed.path.removeprefix('/assets/www/')
            if path.is_file(): r.fulfill(path=path, content_type={'.js':'application/javascript','.html':'text/html','.css':'text/css'}.get(path.suffix))
            else: r.fulfill(status=404, body='Not found')
        context.route('**/*', route)
        page = context.new_page()
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.on('dialog', lambda dialog: dialog.accept())
        page.goto(ORIGIN + 'index.html')
        page.wait_for_function('()=>currentUser && !hydrating')
        page.locator('#healthConnectBtn').click()
        page.locator('#healthRead').click()
        assert 'Android 14' in page.locator('#healthMessage').inner_text()
        def reply(payload):
            page.evaluate('''data=>{healthPending='synthetic-request';receiveHealthConnect('synthetic-request',data);}''', payload)
        samsung = dict(source='com.sec.android.app.shealth', sourceId='synthetic-samsung-1', start='2026-11-01T12:00:00Z', end='2026-11-01T12:30:00Z', date='2026-11-01', title='Natação sintética', type='pool', distanceMeters=750, heartRateAverage=112)
        xiaomi = dict(source='com.xiaomi.wearable', sourceId='synthetic-xiaomi-1', start='2026-11-02T12:00:00Z', end='2026-11-02T12:40:00Z', date='2026-11-02', title='<img src=x onerror="window.injected=true">', type='openWater')
        # Unsolicited/stale callbacks must never populate results or save data.
        page.evaluate('data=>receiveHealthConnect("stale",data)', {'sessions':[samsung]})
        assert page.locator('[data-health-index]').count() == 0
        reply({'error':'Acesso a exercícios não autorizado.'})
        assert 'não autorizado' in page.locator('#healthMessage').inner_text()
        reply({'sessions':[]})
        assert 'Nenhum treino' in page.locator('#healthMessage').inner_text()
        reply({'sessions':[samsung, xiaomi], 'partial':True, 'metricsMissing':True})
        assert page.locator('[data-health-index]').count() == 2
        assert 'limite' in page.locator('#healthMessage').inner_text()
        assert page.locator('#healthCandidates img').count() == 0
        assert not page.evaluate('Boolean(window.injected)')
        assert 'Samsung Health' in page.locator('#healthCandidates').inner_text()
        assert 'Mi Fitness' in page.locator('#healthCandidates').inner_text()
        assert '750 m' in page.locator('#healthCandidates').inner_text()
        assert page.locator('[data-health-index]:checked').count() == 0
        assert page.evaluate('state.sessions.length') == 0
        # No data is saved until rows are explicitly selected.
        page.locator('#healthSave').click()
        assert page.evaluate('state.sessions.length') == 0
        page.locator('[data-health-index="0"]').check()
        page.locator('[data-health-index="1"]').check()
        page.locator('#healthSave').click()
        page.wait_for_function('()=>state.sessions.length===2')
        assert '2 treino(s) salvo(s)' in page.locator('#healthMessage').inner_text()
        assert page.locator('[data-health-index]:disabled').count() == 2
        assert page.evaluate('state.sessions.every(s=>s.plan.length===0 && s.planMinutes===0 && s.effort==="Não informado")')
        assert page.evaluate('state.sessions[1].health.distanceMeters') is None
        page.locator('#healthClose').click()
        assert 'FC média 112 bpm' in page.locator('#history').inner_text()
        assert page.locator('#history img').count() == 0
        page.reload()
        page.wait_for_function('()=>state.sessions.length===2')
        assert page.evaluate('state.sessions[0].health.sourceId') == 'synthetic-xiaomi-1'
        # Backups retain stable source IDs even though restored local IDs are regenerated.
        encrypted = page.evaluate('encryptDiary(state,"synthetic-health-password")')
        diary = page.evaluate('value=>decodeDiary(value,"synthetic-health-password")', encrypted)
        assert {s['health']['sourceId'] for s in diary['sessions']} == {'synthetic-samsung-1','synthetic-xiaomi-1'}
        page.evaluate('async diary=>{localStorage.setItem(RECORDS_KEY,JSON.stringify(diaryRecords(diary)));await enterApp({id:"local"});}', diary)
        page.locator('#healthConnectBtn').click()
        # Same record and identical-time copies from another app are not imported twice.
        twin = dict(samsung, source='com.xiaomi.wearable', sourceId='synthetic-duplicate')
        reply({'sessions':[samsung, twin, xiaomi]})
        assert page.locator('[data-health-index]').count() == 2
        assert page.locator('[data-health-index]:disabled').count() == 2
        assert page.locator('#healthSave').is_hidden()
        # Storage failure must leave both authoritative storage and in-memory state unchanged.
        new = dict(samsung, sourceId='synthetic-new', start='2026-11-03T12:00:00Z', end='2026-11-03T12:30:00Z', date='2026-11-03')
        reply({'sessions':[new]})
        page.locator('[data-health-index]').check()
        before = page.evaluate('localStorage.getItem(RECORDS_KEY)')
        page.evaluate('''()=>{window.originalSetItem=Storage.prototype.setItem;Storage.prototype.setItem=function(key,value){if(key===RECORDS_KEY)throw new Error('Synthetic quota failure');return window.originalSetItem.call(this,key,value);};}''')
        page.locator('#healthSave').click()
        page.wait_for_function('()=>!healthSaving')
        assert page.evaluate('state.sessions.length') == 2
        assert page.evaluate('localStorage.getItem(RECORDS_KEY)') == before
        assert 'Não foi possível salvar' in page.locator('#healthMessage').inner_text()
        page.evaluate('()=>{Storage.prototype.setItem=window.originalSetItem;}')
        reply({'sessions':[dict(new, distanceMeters=-10)]})
        assert page.locator('[data-health-index]').count() == 0
        assert 'intervalo' in page.locator('#healthMessage').inner_text()
        # Closing clears staged data and rejects responses that arrive after dismissal.
        reply({'sessions':[new]})
        page.evaluate('healthPending="late"')
        page.locator('#healthClose').click()
        page.wait_for_function('()=>healthCandidates.length===0 && !healthPending')
        page.evaluate('data=>receiveHealthConnect("late",data)', {'sessions':[new]})
        assert page.evaluate('healthCandidates.length') == 0
        assert page.evaluate('state.sessions.length') == 2
        page.locator('#healthConnectBtn').click()
        reply({'sessions':[new]})
        for width in [320,390,800]:
            page.set_viewport_size({'width':width,'height':844})
            assert page.locator('#healthDialog').evaluate('(el)=>el.scrollWidth<=el.clientWidth'), width
            assert page.locator('body').evaluate('(el)=>el.scrollWidth<=innerWidth'), width
        assert not external, external
        assert not errors, errors
        print('PASS: optional browser fallback, permission/empty/error UI, review/selection, Xiaomi/Samsung origins, null metrics, escaping, atomic import, restart, encrypted backup IDs, duplicate recognition, storage failure, cancellation/stale callbacks, responsive layout, no external requests.')
        browser.close()

if __name__ == '__main__':
    main()
