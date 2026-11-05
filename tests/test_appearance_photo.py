"""Synthetic theme/photo tests. No personal photographs or social accounts are used."""
from pathlib import Path
from urllib.parse import urlparse
import base64
import io
import os
import shutil
import struct
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
ORIGIN='https://appassets.androidplatform.net/assets/www/'


def main():
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path=os.environ.get('CHROME_PATH') or shutil.which('chromium') or shutil.which('google-chrome'),args=['--no-sandbox'])
  context=browser.new_context(viewport={'width':390,'height':844},service_workers='block',color_scheme='light',accept_downloads=True)
  external,errors=[],[]
  def route(r):
   parsed=urlparse(r.request.url)
   if parsed.netloc!='appassets.androidplatform.net':external.append(r.request.url);r.abort();return
   path=ROOT/parsed.path.removeprefix('/assets/www/')
   if path.is_file():r.fulfill(path=path,content_type={'.js':'application/javascript','.html':'text/html','.css':'text/css','.png':'image/png'}.get(path.suffix))
   else:r.fulfill(status=404,body='Not found')
  context.route('**/*',route)
  page=context.new_page();page.on('pageerror',lambda e:errors.append(str(e)))
  page.goto(ORIGIN+'index.html');page.wait_for_function('()=>currentUser && !hydrating')
  assert page.locator('html').get_attribute('data-theme')=='light'
  page.locator('#appearanceSelect').select_option('dark')
  assert page.locator('html').get_attribute('data-theme')=='dark'
  page.reload();page.wait_for_function('()=>currentUser && !hydrating')
  assert page.locator('#appearanceSelect').input_value()=='dark'
  assert page.locator('html').get_attribute('data-theme')=='dark'
  page.emulate_media(color_scheme='light')
  assert page.locator('html').get_attribute('data-theme')=='dark'
  page.locator('#appearanceSelect').select_option('auto')
  assert page.locator('html').get_attribute('data-theme')=='light'
  page.emulate_media(color_scheme='dark');page.wait_for_function('()=>document.documentElement.dataset.theme==="dark"')
  # Native system preference wins over WebView's app-theme media preference in automatic mode.
  page.evaluate('setNativeColorScheme(false)')
  assert page.locator('html').get_attribute('data-theme')=='light'
  page.locator('#appearanceSelect').select_option('dark')
  page.evaluate('setNativeColorScheme(false)')
  assert page.locator('html').get_attribute('data-theme')=='dark'
  # Generate a two-color landscape fixture in memory, never a user's photo.
  data=page.evaluate('''()=>{const c=document.createElement('canvas');c.width=600;c.height=300;const x=c.getContext('2d');x.fillStyle='#e31a1c';x.fillRect(0,0,300,300);x.fillStyle='#1f78b4';x.fillRect(300,0,300,300);return c.toDataURL('image/png').split(',')[1];}''')
  fixture=base64.b64decode(data)
  page.locator('#photoStudioBtn').click()
  assert page.locator('#photoSave').is_disabled()
  page.locator('#photoFile').set_input_files({'name':'synthetic-swim.png','mimeType':'image/png','buffer':fixture})
  page.wait_for_function('()=>photoImage!==null && !document.getElementById("photoSave").disabled')
  assert page.evaluate('photoImage.width/photoImage.height')==2
  assert page.evaluate('document.getElementById("photoCanvas").width')==1080
  page.locator('#photoCaption').fill('Minha natação de teste')
  # Both edges remain visible; the photo is contained, never cropped or stretched.
  colors=page.evaluate('''async()=>{await drawPhoto();const c=document.getElementById('photoCanvas'),x=c.getContext('2d');const mid=Math.round((32+(c.height-172-72-48)/2));return [Array.from(x.getImageData(80,mid,1,1).data),Array.from(x.getImageData(1000,mid,1,1).data)];}''')
  assert colors[0][:3]==[227,26,28] and colors[1][:3]==[31,120,180]
  page.locator('#photoStyle').select_option('light')
  page.evaluate('drawPhoto()')
  assert page.evaluate('Array.from(document.getElementById("photoCanvas").getContext("2d").getImageData(5,5,1,1).data).slice(0,3)')==[239,247,250]
  # The actual PNG includes the existing logo, caption and exact export dimensions.
  for format_name,height in [('feed',1350),('square',1080),('story',1920)]:
   page.locator('#photoFormat').select_option(format_name)
   with page.expect_download() as downloaded:page.locator('#photoSave').click()
   raw=Path(downloaded.value.path()).read_bytes()
   assert raw[:8]==b'\x89PNG\r\n\x1a\n'
   assert struct.unpack('>II',raw[16:24])==(1080,height)
   assert len(raw)<12*1024*1024
   assert page.evaluate('Array.from(document.getElementById("photoCanvas").getContext("2d").getImageData(96,document.getElementById("photoCanvas").height-82,1,1).data).slice(0,3)')!=[239,247,250]
  # Unsupported sharing falls back to a downloadable image; no external post is attempted.
  page.evaluate('''()=>{Object.defineProperty(navigator,'canShare',{value:()=>false,configurable:true});}''')
  with page.expect_download():page.locator('#photoShare').click()
  assert 'Imagem baixada' in page.locator('#photoMessage').inner_text()
  assert page.evaluate('window.photoExportInfo("unknown")') is None
  assert page.evaluate('window.photoExportChunk("unknown",0,65536)') is None
  page.locator('#photoFile').set_input_files({'name':'bad-image.png','mimeType':'image/png','buffer':b'invalid-image'})
  page.wait_for_function('()=>document.getElementById("photoMessage").textContent.includes("Não foi possível")')
  for theme in ['light','dark']:
   page.evaluate('mode=>setAppearance(mode)',theme)
   for width in [320,390,800,1280]:
    page.set_viewport_size({'width':width,'height':844})
    assert page.locator('#photoDialog').evaluate('(el)=>el.scrollWidth<=el.clientWidth'),(theme,width)
    assert page.locator('body').evaluate('(el)=>el.scrollWidth<=innerWidth'),(theme,width)
  page.locator('#photoClose').click()
  page.wait_for_function('()=>photoImage===null')
  assert page.evaluate('preparedPhoto') is None
  assert page.evaluate('photoObjectUrl') is None
  assert page.evaluate('JSON.stringify(readRecords()).includes("base64")') is False
  page.locator('#photoStudioBtn').click()
  assert page.locator('#photoSave').is_disabled()
  assert page.locator('#photoCaption').input_value()==''
  assert not external,external
  assert not errors,errors
  print('PASS: light/dark/auto persistence, live system/native scheme, synthetic photo import, original aspect/edges, logo render, three PNG export sizes, sharing fallback, invalid photo, no photo persistence, close cleanup, responsive themes, no external requests.')
  browser.close()

if __name__=='__main__':main()
