#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Создать conference_admin + conference demo_conf."""
import sys
from datetime import datetime, timezone
from pathlib import Path

try:
    import firebase_admin
    from firebase_admin import credentials, auth, firestore
except ImportError:
    print("[ERROR] pip install firebase-admin")
    sys.exit(1)

SA_KEY_PATH = Path("pathfinder-hub-c495d-firebase-adminsdk-fbsvc-6155bc9c6a.json")

ADMIN_EMAIL = "conference_admin@pathfinder.local"
ADMIN_PASSWORD = "ConfAdmin123!"
ADMIN_FIRST_NAME = "Админ"
ADMIN_LAST_NAME = "Конференции"

CONFERENCE_ID = "demo_conf"
CONFERENCE_NAME = "Демо-конференция"
CONFERENCE_TERRITORY = "Москва"
CONFERENCE_CONTACT_EMAIL = "conf@pathfinder.local"

if not SA_KEY_PATH.exists():
    print("[ERROR] не найден " + str(SA_KEY_PATH))
    sys.exit(1)

cred = credentials.Certificate(str(SA_KEY_PATH))
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)

db = firestore.client()

# 1. Firebase Auth
try:
    user = auth.get_user_by_email(ADMIN_EMAIL)
    uid = user.uid
    print("[INFO] Auth уже есть: " + uid)
    auth.update_user(uid, password=ADMIN_PASSWORD)
except auth.UserNotFoundError:
    user = auth.create_user(
        email=ADMIN_EMAIL,
        password=ADMIN_PASSWORD,
        display_name=f"{ADMIN_FIRST_NAME} {ADMIN_LAST_NAME}",
        email_verified=True,
    )
    uid = user.uid
    print("[NEW]  Auth создан: " + uid)

# 2. users/{uid}
now = datetime.now(timezone.utc)
db.collection("users").document(uid).set({
    "id": uid,
    "email": ADMIN_EMAIL,
    "emailVerified": True,
    "firstName": ADMIN_FIRST_NAME,
    "lastName": ADMIN_LAST_NAME,
    "role": "conference_admin",
    "clubId": None,
    "clubIds": [],
    "isOwnerClub": True,
    "directorPermissions": [],
    "conferenceId": CONFERENCE_ID,
    "birthDate": now,
    "avatar": None,
    "parentEmail": None,
    "parentalConsentStatus": "approved",
    "parentalConsentRequestedAt": None,
    "parentalConsentApprovedAt": None,
    "accessLevel": "full",
    "status": "active",
    "deletionRequestedBy": None,
    "deletionRequestedAt": None,
    "anonymizedAt": None,
    "permanentDeletionAt": None,
    "onboardingCompleted": True,
    "createdAt": now,
    "lastLoginAt": None,
})
print("[OK]   users/" + uid)

# 3. conferences/{id}
db.collection("conferences").document(CONFERENCE_ID).set({
    "id": CONFERENCE_ID,
    "name": CONFERENCE_NAME,
    "territory": CONFERENCE_TERRITORY,
    "logoUrl": None,
    "contactEmail": CONFERENCE_CONTACT_EMAIL,
    "contactPhone": "",
    "clubLeader": "",
    "dipkutor": "",
    "secretary": "",
    "updatedAt": now,
    "updatedBy": "seed",
})
print("[OK]   conferences/" + CONFERENCE_ID)

print()
print("Вход: " + ADMIN_EMAIL + " / " + ADMIN_PASSWORD)