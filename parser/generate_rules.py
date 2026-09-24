#!/usr/bin/env python3
"""Генератор Firebase Rules для Pathfinder Hub."""

from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent.parent
FILES = []

def add(path, content):
    FILES.append((path, content.rstrip() + "\n"))

def write_all():
    for path, content in FILES:
        full = ROOT / path
        full.parent.mkdir(parents=True, exist_ok=True)
        with open(full, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"OK {path}")
    print(f"\nГотово: {len(FILES)} файлов")

FIRESTORE_FULL = """rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {

    function isSignedIn() {
      return request.auth != null;
    }

    function uid() {
      return request.auth.uid;
    }

    function userRole() {
      return request.auth.token.role;
    }

    function userClubId() {
      return request.auth.token.clubId;
    }

    function isDirector() {
      return userRole() == 'director';
    }

    function isInstructor() {
      return userRole() == 'instructor';
    }

    function isConference() {
      return userRole() == 'conference';
    }

    function isLeader() {
      return isDirector() || isInstructor();
    }

    function belongsToClub(clubId) {
      return userClubId() == clubId;
    }

    match /users/{userId} {
      allow read: if isSignedIn() && (
        uid() == userId ||
        isConference() ||
        (isLeader() && belongsToClub(resource.data.clubId))
      );
      allow create: if isSignedIn() && uid() == userId;
      allow update: if isSignedIn() && (uid() == userId || isConference());
      allow delete: if isSignedIn() && isConference();

      match /levelProgress/{progressId} {
        allow read, write: if isSignedIn() && (
          uid() == userId || isConference()
        );
      }

      match /honorProgress/{progressId} {
        allow read, write: if isSignedIn() && (
          uid() == userId || isConference()
        );
      }
    }

    match /clubs/{clubId} {
      allow read: if isSignedIn();
      allow create: if isSignedIn() && isConference();
      allow update: if isSignedIn() && (
        isConference() || (isDirector() && belongsToClub(clubId))
      );
      allow delete: if isSignedIn() && isConference();

      match /events/{eventId} {
        allow read: if isSignedIn() && (isConference() || belongsToClub(clubId));
        allow create: if isSignedIn() && isLeader() && belongsToClub(clubId);
        allow update: if isSignedIn() && (isConference() || belongsToClub(clubId));
        allow delete: if isSignedIn() && (isConference() || (isDirector() && belongsToClub(clubId)));
      }

      match /tasks/{taskId} {
        allow read: if isSignedIn() && (isConference() || belongsToClub(clubId));
        allow create, update, delete: if isSignedIn() && (
          isConference() || (isDirector() && belongsToClub(clubId))
        );
      }
    }

    match /conferences/{conferenceId} {
      allow read: if isSignedIn();
      allow write: if isSignedIn() && isConference();
    }

    match /{document=**} {
      allow read, write: if false;
    }
  }
}
"""

FIRESTORE_PERMISSIVE = """rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
"""

STORAGE_FULL = """rules_version = '2';

service firebase.storage {
  match /b/{bucket}/o {

    function isSignedIn() {
      return request.auth != null;
    }

    function uid() {
      return request.auth.uid;
    }

    function userRole() {
      return request.auth.token.role;
    }

    function isLeader() {
      return userRole() == 'director' || userRole() == 'instructor';
    }

    match /reports/{userId}/{fileName} {
      allow read: if isSignedIn() && (uid() == userId || isLeader() || userRole() == 'conference');
      allow write: if isSignedIn() && uid() == userId &&
        request.resource.size < 50 * 1024 * 1024 &&
        (request.resource.contentType.matches('image/.*') ||
         request.resource.contentType.matches('video/.*'));
      allow delete: if isSignedIn() && uid() == userId;
    }

    match /newspaper/{fileName} {
      allow read: if isSignedIn();
      allow write: if isSignedIn() && userRole() == 'conference' &&
        request.resource.contentType == 'application/pdf';
    }

    match /{allPaths=**} {
      allow read, write: if false;
    }
  }
}
"""

STORAGE_PERMISSIVE = """rules_version = '2';

service firebase.storage {
  match /b/{bucket}/o {
    match /{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
"""

FIREBASE_JSON = """{
  "firestore": {
    "rules": "firestore.rules",
    "indexes": "firestore.indexes.json"
  },
  "storage": {
    "rules": "storage.rules"
  }
}
"""

INDEXES_JSON = """{
  "indexes": [],
  "fieldOverrides": []
}
"""

FIREBASERC = """{
  "projects": {
    "default": "REPLACE_WITH_YOUR_FIREBASE_PROJECT_ID"
  }
}
"""

if __name__ == "__main__":
    add("firestore.rules", FIRESTORE_FULL)
    add("firestore.rules.permissive", FIRESTORE_PERMISSIVE)
    add("storage.rules", STORAGE_FULL)
    add("storage.rules.permissive", STORAGE_PERMISSIVE)
    add("firebase.json", FIREBASE_JSON)
    add("firestore.indexes.json", INDEXES_JSON)
    add(".firebaserc", FIREBASERC)
    write_all()
    print("\nСледующие шаги:")
    print("1. npm install -g firebase-tools")
    print("2. firebase login")
    print("3. Заменить project ID в .firebaserc")
    print("4. Развернуть: firebase deploy --only firestore:rules,storage")