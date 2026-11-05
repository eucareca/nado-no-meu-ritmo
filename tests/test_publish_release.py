"""Publication safeguards with a fake API. No real releases, tags or uploads are created."""
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import Request
import zipfile

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('publisher',ROOT/'scripts/publish-source-release.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
SHA='a'*40
class Response:
 def __init__(self,value):
  self.headers={'Content-Type':'application/json' if not isinstance(value,bytes) else 'application/zip'};self.value=json.dumps(value).encode() if not isinstance(value,bytes) else value
 def read(self):return self.value
 def __enter__(self):return self
 def __exit__(self,*args):pass
class Fake:
 def __init__(self,bad=False,wrong_tag=False,existing=False):
  self.calls=[];self.bad=bad;self.wrong_tag=wrong_tag;self.existing=existing
  b=io.BytesIO()
  with zipfile.ZipFile(b,'w') as z:
   z.writestr('nado-release-unsigned.apk',b'unsigned-production-fixture');z.writestr('nado-teste.apk',b'never-publish-this-preview')
   z.writestr('SHA256SUMS.txt',('0'*64 if bad else hashlib.sha256(b'unsigned-production-fixture').hexdigest())+'  nado-release-unsigned.apk\n')
  self.archive=b.getvalue()
 def open(self,request,timeout):
  self.calls.append(request);url=request.full_url;method=request.get_method()
  if url.endswith('/releases/tags/v1.10.0'):
   if self.existing:return Response({'html_url':'https://github.com/example/app/releases/tag/v1.10.0'})
   raise HTTPError(url,404,'missing',{},None)
  if '/git/ref/tags/' in url:
   if self.wrong_tag:return Response({'object':{'type':'commit','sha':'b'*40}})
   raise HTTPError(url,404,'missing',{},None)
  if url.endswith('/actions/runs/42/artifacts'):return Response({'artifacts':[{'id':7,'name':'nado-apks-10','expired':False,'workflow_run':{'head_sha':SHA},'digest':'sha256:'+hashlib.sha256(self.archive).hexdigest()}]})
  if url.endswith('/actions/artifacts/7/zip'):return Response(self.archive)
  if url.endswith('/git/refs') and method=='POST':return Response({})
  if url.endswith('/releases') and method=='POST':return Response({'id':9,'upload_url':'https://uploads.github.com/repos/example/app/releases/9/assets{?name,label}'})
  if '/assets?name=' in url:return Response({})
  if url.endswith('/releases/9') and method=='PATCH':return Response({'html_url':'https://github.com/example/app/releases/tag/v1.10.0'})
  raise AssertionError('Unexpected API call')

def execute(fake,event_change=None):
 with tempfile.TemporaryDirectory() as directory:
  root=Path(directory)
  for name,text in [('android/app/build.gradle','versionName "1.10.0"\nversionCode 11'),('fdroid/br.com.tabinformatica.nadonomeritmo.yml','recipe'),('docs/PEDIDO-F-DROID.md','request')]:
   p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
  run={'conclusion':'success','event':'push','head_branch':'main','head_repository':{'full_name':'example/app'},'head_sha':SHA,'id':42,'run_number':10,'html_url':'https://github.com/example/app/actions/runs/42'}
  if event_change:run.update(event_change)
  event=root/'event.json';event.write_text(json.dumps({'workflow_run':run}))
  def package(*args,**kwargs):
   out=root/'artifacts/production'
   for filename in ['source.zip','source.tar.gz','SOURCE-MANIFEST.sha256']:(out/filename).write_bytes(b'fixture')
  with patch.object(m,'ROOT',root),patch.object(m,'OPENER',fake),patch.object(m.subprocess,'check_output',return_value=SHA+'\n'),patch.object(m.subprocess,'run',side_effect=package),patch.dict(os.environ,{'GITHUB_EVENT_PATH':str(event),'GITHUB_REPOSITORY':'example/app','GH_TOKEN':'synthetic-token','GITHUB_STEP_SUMMARY':str(root/'summary')}):m.main()

f=Fake();execute(f)
mutations=[r for r in f.calls if r.get_method()!='GET']
assert json.loads(mutations[0].data)=={'ref':'refs/tags/v1.10.0','sha':SHA}
assert json.loads(mutations[1].data)['draft'] is True
assert json.loads(mutations[-1].data)['draft'] is False
assert any('nado-release-unsigned.apk' in r.full_url for r in mutations)
assert all('nado-teste.apk' not in r.full_url for r in mutations)
for f,change in [(Fake(bad=True),None),(Fake(wrong_tag=True),None),(Fake(),{'head_branch':'feature'}),(Fake(),{'conclusion':'failure'}),(Fake(),{'head_repository':{'full_name':'other/repo'}})]:
 try:execute(f,change);raise AssertionError('Expected rejection')
 except RuntimeError:pass
 assert all(r.get_method()=='GET' for r in f.calls)
f=Fake(existing=True);execute(f);assert len(f.calls)==1
handler=m.SafeRedirect();r=Request('https://api.github.com/file',headers={'Authorization':'Bearer synthetic-token'})
redirected=handler.redirect_request(r,None,302,'Found',{},'https://storage.example.invalid/file')
assert not redirected.has_header('Authorization')
try:handler.redirect_request(r,None,302,'Found',{},'http://storage.example.invalid/file');raise AssertionError('Expected rejection')
except RuntimeError:pass
print('PASS: trusted main/success gates, exact tested commit, immutable existing release/tag, artifact and APK checksums, draft until complete, preview excluded, redirect credentials stripped. No real publication.')
