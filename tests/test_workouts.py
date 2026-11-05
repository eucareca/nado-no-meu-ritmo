"""Exercise workout editing, timing and backup compatibility with synthetic local data."""
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
        context = browser.new_context(viewport={'width': 390, 'height': 844}, service_workers='block', user_agent='NadoFoss/1')
        external, errors = [], []
        def route(r):
            parsed = urlparse(r.request.url)
            if parsed.netloc != 'appassets.androidplatform.net':
                external.append(r.request.url); r.abort(); return
            path = ROOT / parsed.path.removeprefix('/assets/www/')
            if path.is_file():
                mime = {'.html':'text/html', '.js':'application/javascript', '.css':'text/css'}.get(path.suffix)
                r.fulfill(path=path, content_type=mime)
            else: r.fulfill(status=404, body='Not found')
        context.route('**/*', route)
        page = context.new_page()
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.on('dialog', lambda d: d.accept())
        page.goto(ORIGIN + 'index.html')
        page.wait_for_function('()=>currentUser && !hydrating')
        before = page.evaluate('JSON.stringify(state.plan)')
        page.locator('#editPlanBtn2').click()
        page.locator('[data-name]').first.fill('Rascunho sintético')
        page.locator('[data-detail]').first.fill('crawl · 25 m')
        page.locator('#planHint').fill('Nota preservada')
        page.locator('#addBlock').click()
        assert page.locator('[data-name]').first.input_value() == 'Rascunho sintético'
        assert page.locator('[data-detail]').first.input_value() == 'crawl · 25 m'
        page.locator('[data-remove]').last.click()
        assert page.locator('#planHint').input_value() == 'Nota preservada'
        page.locator('#closeModal').click()
        assert page.evaluate('JSON.stringify(state.plan)') == before
        # Selecting a template remains a draft until explicitly saved.
        page.locator('#workoutLibrary').click()
        page.locator('[data-template="1"]').click()
        assert page.evaluate('JSON.stringify(state.plan)') == before
        page.locator('#sessionForm button[type=submit]').click()
        page.wait_for_function('()=>total(state.plan)===20')
        assert page.locator('#heroMinutes').inner_text() == '20'
        page.reload()
        page.wait_for_function('()=>total(state.plan)===20')
        assert page.evaluate('state.plan[1].rounds') == 6
        assert page.evaluate('state.plan[2].minutes') == 4.5
        # Make a short real series through the editor, without waiting real minutes.
        page.locator('#editPlanBtn2').click()
        page.locator('[data-remove]').last.click()
        page.locator('[data-remove]').first.click()
        page.locator('[data-name]').fill('Série de teste')
        page.locator('[data-rounds]').fill('2')
        page.locator('[data-work]').fill('2')
        page.locator('[data-rest]').fill('1')
        page.locator('[data-detail]').fill('<img src=x onerror=alert(1)>')
        page.locator('#sessionForm button[type=submit]').click()
        page.wait_for_function('()=>state.plan.length===1 && state.plan[0].rounds===2')
        assert page.locator('#schedule img').count() == 0
        assert page.evaluate('workoutPhases(state.plan).map(p=>p.seconds)') == [2, 1, 2]
        # Backup preserves the new fields, including historic session snapshots.
        page.clock.install()
        page.locator('#workoutStart').click()
        assert page.locator('#workoutClock').inner_text() == '00:02'
        page.clock.fast_forward(2000)
        assert page.locator('#workoutPhase').inner_text().startswith('Pausa')
        page.clock.fast_forward(1000)
        assert '2/2' in page.locator('#workoutPhase').inner_text()
        page.locator('#workoutPause').click()
        paused = page.locator('#workoutClock').inner_text()
        page.clock.fast_forward(10000)
        assert page.locator('#workoutClock').inner_text() == paused
        # Editing the current plan cannot change an ongoing workout's snapshot.
        page.locator('#editPlanBtn2').click()
        page.locator('[data-name]').fill('Próximo treino')
        page.locator('#sessionForm button[type=submit]').click()
        page.wait_for_function('()=>state.plan[0].name==="Próximo treino"')
        page.locator('#workoutPause').click()
        page.clock.fast_forward(2000)
        assert page.locator('#workoutPhase').inner_text() == 'Treino concluído'
        assert page.evaluate('state.sessions.length') == 0
        page.locator('#workoutRegister').click()
        assert 'Série de teste' in page.locator('#actualBlocks').input_value()
        page.locator('#effort').select_option('Moderado')
        page.locator('#sessionForm button[type=submit]').click()
        page.wait_for_function('()=>state.sessions.length===1')
        assert page.locator('#workoutCard').is_hidden()
        assert page.evaluate('state.sessions[0].plan[0].name') == 'Série de teste'
        diary = page.evaluate('validatedDiary(state)')
        assert diary['sessions'][0]['plan'][0]['rounds'] == 2
        assert diary['sessions'][0]['effort'] == 'Moderado'
        encrypted = page.evaluate('encryptDiary(state, "synthetic-password-123")')
        restored = page.evaluate('raw=>decodeDiary(raw, "synthetic-password-123")', encrypted)
        assert restored['plan'][0]['restSeconds'] == 1
        assert restored['sessions'][0]['plan'][0]['workSeconds'] == 2
        assert page.evaluate('''()=>{const old=backupPlan([{name:'Antigo',minutes:19,type:'serie',detail:'25 m'}]);return !('rounds' in old[0]) && workoutPhases(old)[0].seconds===1140}''')
        assert page.evaluate('''()=>{try{backupPlan([{name:'Inválido',minutes:1,type:'serie',rounds:100000,workSeconds:2,restSeconds:1}]);return false}catch(e){return true}}''')
        # Delayed callbacks jump across phases and finish without adding extra rests.
        page.locator('#workoutStart').click()
        page.clock.fast_forward(60000)
        assert page.locator('#workoutPhase').inner_text() == 'Treino concluído'
        page.locator('#workoutCancel').click()
        assert page.evaluate('state.sessions.length') == 1
        for width in [320, 390, 800, 1280]:
            page.set_viewport_size({'width':width,'height':844})
            page.clock.run_for(300)
            assert page.locator('body').evaluate('(el)=>el.scrollWidth<=innerWidth'), (width, page.locator('body').evaluate('(el)=>[el.scrollWidth,innerWidth]'), page.evaluate("[...document.querySelectorAll('body *')].filter(el=>el.getBoundingClientRect().right>innerWidth+1).map(el=>[el.tagName,el.id,el.className]).slice(0,15)"))
        page.set_viewport_size({'width':390,'height':844})
        page.locator('#editPlanBtn2').click()
        assert page.locator('.modal').evaluate('(el)=>el.scrollWidth<=el.clientWidth')
        page.locator('#closeModal').click()
        assert not external, external
        assert not errors, errors
        print('PASS: draft isolation, add/remove preservation, templates, restart, interval order, pause/resume, completion, snapshot registration, backup round-trip, legacy plans, validation, responsive widths, no external requests.')
        browser.close()

if __name__ == '__main__':
    main()
