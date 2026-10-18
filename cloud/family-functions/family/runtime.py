"""ADC in production; anonymous credentials only for explicitly local demo emulators."""
import os
import firebase_admin
from firebase_admin import credentials
from google.auth.credentials import AnonymousCredentials


def initialize():
    if os.environ.get('FUNCTIONS_EMULATOR') == 'true':
        project = os.environ.get('GCLOUD_PROJECT')
        if project != 'demo-wardrobe' or not all(os.environ.get(key, '').startswith(('127.0.0.1:', 'localhost:'))
                for key in ('FIRESTORE_EMULATOR_HOST','FIREBASE_AUTH_EMULATOR_HOST','FIREBASE_STORAGE_EMULATOR_HOST')):
            raise RuntimeError('Family emulator requires local demo-wardrobe services')
        class EmulatorCredentials(credentials.Base):
            def get_credential(self):
                return AnonymousCredentials()
        return firebase_admin.initialize_app(EmulatorCredentials(), {'projectId':project})
    return firebase_admin.initialize_app()
