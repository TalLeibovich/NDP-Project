"""
firebase_init.py

Initialize Firebase Admin SDK and Firestore client.
This module should be imported once at application startup.
"""

import firebase_admin
from firebase_admin import credentials, firestore
import os

# Initialize Firebase Admin SDK (only once)
if not firebase_admin._apps:
    # In Cloud Run, use default credentials (automatically provided by GCP)
    # For local development, you can set GOOGLE_APPLICATION_CREDENTIALS env var
    # or use a service account key file
    try:
        # Try to use default credentials (works in Cloud Run)
        firebase_admin.initialize_app()
    except Exception as e:
        # Fallback: try to load from service account file if provided
        cred_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        if cred_path and os.path.exists(cred_path):
            cred = credentials.Certificate(cred_path)
            firebase_admin.initialize_app(cred)
        else:
            raise RuntimeError(
                f"Failed to initialize Firebase Admin SDK: {e}\n"
                "Make sure you're running in Cloud Run or set GOOGLE_APPLICATION_CREDENTIALS"
            )

# Get Firestore client
db = firestore.client()
