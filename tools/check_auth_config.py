#!/usr/bin/env python3
"""Offline client-config shape validation. Never prints API keys or other config values."""
from pathlib import Path
import json
import sys
path=Path(__file__).resolve().parents[1]/'app/google-services.json'
if not path.is_file():
    print('NOT CONFIGURED: add the Firebase Android client file at app/google-services.json. See AUTHENTICATION.md.')
    sys.exit(2)
try:
    data=json.loads(path.read_text())
    if data.get('type') == 'service_account':
        raise ValueError('Use the Android client config, never a service-account key.')
    assert data['project_info']['project_id'], 'Missing project ID'
    clients=[c for c in data['client'] if c['client_info']['android_client_info']['package_name']=='com.familywardrobe.app']
    assert len(clients)==1, 'Expected exactly one client for com.familywardrobe.app'
    assert clients[0]['client_info']['mobilesdk_app_id'], 'Missing Android app ID'
    assert any(key.get('current_key') for key in clients[0]['api_key']), 'Missing client API key'
except Exception:
    print('INVALID: verify that this is the Android client config for com.familywardrobe.app, not a service-account file.')
    sys.exit(1)
print('PASS: client config shape and package name. Firebase provider, password policy and email delivery still need live verification.')
