#!/usr/bin/env python3
"""One explicitly budgeted request through the actual backend. Never reads an OpenAI API key."""
import json
import os
from pathlib import Path
import secrets
import time
import urllib.request
from decimal import Decimal

ROOT=Path(__file__).resolve().parents[1]
STATE=ROOT/'.local'/'openai-live-verification-state.json'
REPORT=ROOT/'.local'/'openai-live-verification.json'
IMAGE=ROOT/'.local'/'openai-live-verification.jpg'
BASE='http://127.0.0.1:8080/api/studio'

def main():
    state=json.loads(STATE.read_text()) if STATE.exists() else {'session':secrets.token_hex(32)}
    def save_state():
        STATE.parent.mkdir(exist_ok=True)
        fd=os.open(STATE,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
        with os.fdopen(fd,'w') as stream:json.dump(state,stream)
    def call(path,body=None,raw=False):
        data=None if body is None else json.dumps(body).encode()
        request=urllib.request.Request(BASE+path,data=data,headers={'X-Studio-Session':state['session'],**({'Content-Type':'application/json'} if data is not None else {})})
        try:
            with urllib.request.urlopen(request,timeout=15) as response:
                result=response.read(9*1024*1024)
                return result if raw else json.loads(result or '{}')
        except urllib.error.HTTPError as error:
            # Only safe backend messages, never print request headers or raw upstream bodies.
            try:message=json.loads(error.read(8192)).get('message','Request failed')
            except Exception:message='Request failed'
            raise RuntimeError(f'Backend status {error.code}: {message}') from None
    if not state.get('jobId'):
        status=call('/status');budget=call('/budget')
        if status.get('provider')!='OPENAI' or not status.get('imageConfigured'):
            raise RuntimeError('Start ./dev openai in the terminal containing OPENAI_API_KEY first.')
        if budget.get('maxPaidRequests')!=1 or budget.get('paidRequests')!=0:
            raise RuntimeError('The live check requires a fresh one-request verification budget. No image job was submitted.')
        docs=call('/find',{'message':'A baby blue fitted cardigan for class, clean seams and pearl-like buttons'})['concepts']
        doc=docs[0];revision=doc['revisions'][-1]
        quote=call('/items/'+doc['id']+'/quote',{'revisionId':revision['id']})
        if not quote['estimateAvailable'] or quote['inputImageCount']!=0 or Decimal(quote['estimatedUsd'])>Decimal('0.25'):
            raise RuntimeError('Reliable pre-call estimate is unavailable or exceeds $0.25. No paid request was submitted; ask the user before proceeding.')
        if quote['model']!='gpt-image-2.5-flare-2026-09-08' or quote['quality']!='low' or quote['size']!='1024x1024':
            raise RuntimeError('Verification model/settings differ from the reviewed cost plan.')
        state.update({'conceptId':doc['id'],'revisionId':revision['id'],'quote':quote})
        save_state()
        print('Preflight estimate USD:',quote['estimatedUsd'],'; one image, no reference image. Conservative budget reservation: $0.25.',flush=True)
        # This is the only enqueue in the script. Recovery reuses this exact revision/job.
        job=call('/items/'+doc['id']+'/generate',{'revisionId':revision['id']})
        state['jobId']=job['id'];save_state()
    print('Checking the existing verification job; no additional image requests will be made.',flush=True)
    previous=None
    deadline=time.monotonic()+180
    while time.monotonic()<deadline:
        job=call('/jobs/'+state['jobId'])
        if job['state']!=previous:print('Job:',job['state'],flush=True);previous=job['state']
        if job['state'] in ('SUCCEEDED','FAILED','CANCELLED'):
            report={'model':state['quote']['model'],'preflight':state['quote'],'jobId':job['id'],'conceptId':state['conceptId'],'state':job['state'],'result':job['result'],'error':job['error'],'budget':call('/budget'),'liveVerificationRequests':1,'billingCostVerified':False}
            if job['state']=='SUCCEEDED':
                image_path=job['result']['image'];IMAGE.write_bytes(call(image_path.removeprefix('/api/studio'),raw=True));report['localImage']=str(IMAGE)
            REPORT.write_text(json.dumps(report,indent=2))
            print(json.dumps(report,indent=2));return 0 if job['state']=='SUCCEEDED' else 1
        time.sleep(2)
    raise RuntimeError('Verification polling timed out. Do not submit another request. Rerun only to recover the existing job status.')

if __name__=='__main__':
    try:raise SystemExit(main())
    except Exception as error:
        print(type(error).__name__+': '+str(error))
        raise SystemExit(1)
