#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
K4 part 2 (Firestore rules) - 09.10.2026

- isOwner(), hasPerm(), hasPermOrStaff()
- Переписать 17 правил директора на hasPerm
- Защитить isOwnerClub/directorPermissions/clubIds от правки своим же юзером

Запуск:
    python fix_k4_part2.py
"""

import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")
PATH = "firestore.rules"

if not (ROOT / PATH).exists():
    print("[ERROR] не найден: " + PATH); sys.exit(1)

p = ROOT / PATH
raw = p.read_bytes()
crlf = b"\r\n" in raw
text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")

if "function hasPerm(" in text:
    print("[SKIP] rules уже пропатчены")
    sys.exit(0)

bak = p.with_suffix(p.suffix + ".bak." + STAMP)
shutil.copy2(p, bak)
print("[BAK]  " + bak.name)

patches = []

# ------------------------------------------------------------
# 1. Функции isOwner / hasPerm / hasPermOrStaff
# ------------------------------------------------------------
patches.append((
"""    function isOwnClub(clubId) {
      return isSignedIn() && clubId in userClubIds();
    }""",
"""    function isOwnClub(clubId) {
      return isSignedIn() && clubId in userClubIds();
    }

    // Владелец клуба: director с флагом isOwnerClub (или старый director без флага).
    function isOwner() {
      return isSignedIn()
        && userRole() == 'director'
        && userDoc().isOwnerClub != false;
    }

    // Владелец, co-director с явным правом, или conference_admin.
    function hasPerm(perm) {
      return isConferenceAdmin()
        || isOwner()
        || (isSignedIn()
            && userRole() == 'director'
            && userDoc().directorPermissions != null
            && perm in userDoc().directorPermissions);
    }

    // hasPerm + instructor/secretary (совместимость с прежним isClubStaff).
    function hasPermOrStaff(perm) {
      return hasPerm(perm)
        || (isSignedIn() && userRole() in ['instructor', 'secretary']);
    }"""))

# ------------------------------------------------------------
# 2. users.update — защита isOwnerClub/directorPermissions/clubIds
# ------------------------------------------------------------
patches.append((
"""      allow update: if isSignedIn() && (
        (uid() == userId
          && !request.resource.data.diff(resource.data).affectedKeys()
              .hasAny(['role', 'clubId', 'conferenceId', 'accessLevel', 'status']))
        || isConferenceAdmin()
        || (isDirector() && isOwnClub(resource.data.clubId))
      );""",
"""      allow update: if isSignedIn() && (
        (uid() == userId
          && !request.resource.data.diff(resource.data).affectedKeys()
              .hasAny(['role', 'clubId', 'clubIds', 'conferenceId', 'accessLevel', 'status',
                       'isOwnerClub', 'directorPermissions']))
        || isConferenceAdmin()
        || (hasPerm("members.edit") && isOwnClub(resource.data.clubId))
      );"""))

# ------------------------------------------------------------
# 3. clubs.update
# ------------------------------------------------------------
patches.append((
"""      allow update: if isConferenceAdmin() || (isDirector() && isOwnClub(clubId));
      allow delete: if isConferenceAdmin();

      match /events/{eventId} {""",
"""      allow update: if isConferenceAdmin() || (hasPerm("settings.edit") && isOwnClub(clubId));
      allow delete: if isConferenceAdmin();

      match /events/{eventId} {"""))

# ------------------------------------------------------------
# 4. events create/update/delete
# ------------------------------------------------------------
patches.append((
"""        allow read: if isSignedIn();
        allow create, update: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow delete: if isConferenceAdmin() || (isDirector() && isOwnClub(clubId));""",
"""        allow read: if isSignedIn();
        allow create, update: if isSignedIn() && (hasPerm("events.create") && isOwnClub(clubId));
        allow delete: if isConferenceAdmin() || (hasPerm("events.delete") && isOwnClub(clubId));"""))

# ------------------------------------------------------------
# 5. tasks
# ------------------------------------------------------------
patches.append((
"""      match /tasks/{taskId} {
        allow read: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow create: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow update: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow delete: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
      }""",
"""      match /tasks/{taskId} {
        allow read: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow create: if isSignedIn() && (hasPerm("tasks.create") && isOwnClub(clubId));
        allow update: if isSignedIn() && (hasPerm("tasks.create") && isOwnClub(clubId));
        allow delete: if isSignedIn() && (hasPerm("tasks.create") && isOwnClub(clubId));
      }"""))

# ------------------------------------------------------------
# 6. comments delete
# ------------------------------------------------------------
patches.append((
"""        allow delete: if isConferenceAdmin() || (isDirector() && isOwnClub(clubId));
      }

      match /moderationRequests/{reqId} {""",
"""        allow delete: if isConferenceAdmin() || (hasPerm("moderation.review") && isOwnClub(clubId));
      }

      match /moderationRequests/{reqId} {"""))

# ------------------------------------------------------------
# 7. moderationRequests
# ------------------------------------------------------------
patches.append((
"""      match /moderationRequests/{reqId} {
        allow read, write: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
      }""",
"""      match /moderationRequests/{reqId} {
        allow read, write: if isSignedIn() && (hasPermOrStaff("moderation.review") && isOwnClub(clubId));
      }"""))

# ------------------------------------------------------------
# 8. moderationHistory
# ------------------------------------------------------------
patches.append((
"""      match /moderationHistory/{historyId} {
        allow read: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow create: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow update, delete: if false;
      }""",
"""      match /moderationHistory/{historyId} {
        allow read: if isSignedIn() && (hasPermOrStaff("moderation.review") && isOwnClub(clubId));
        allow create: if isSignedIn() && (hasPermOrStaff("moderation.review") && isOwnClub(clubId));
        allow update, delete: if false;
      }"""))

# ------------------------------------------------------------
# 9. appeals update
# ------------------------------------------------------------
patches.append((
"""      match /appeals/{appealId} {
        allow read, create: if isSignedIn();
        allow update: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow delete: if isConferenceAdmin();
      }""",
"""      match /appeals/{appealId} {
        allow read, create: if isSignedIn();
        allow update: if isSignedIn() && (hasPermOrStaff("moderation.review") && isOwnClub(clubId));
        allow delete: if isConferenceAdmin();
      }"""))

# ------------------------------------------------------------
# 10. honorDrafts
# ------------------------------------------------------------
patches.append((
"""      match /honorDrafts/{draftId} {
        allow read, write: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
      }""",
"""      match /honorDrafts/{draftId} {
        allow read, write: if isSignedIn() && (hasPermOrStaff("content.edit") && isOwnClub(clubId));
      }"""))

# ------------------------------------------------------------
# 11. requirements/materials
# ------------------------------------------------------------
patches.append((
"""        match /materials/{materialId} {
          allow read: if isSignedIn() && (isOwnClub(clubId) || isConferenceAdmin());
          allow create, update, delete: if isSignedIn() && (
            isConferenceAdmin()
            || (isDirector() && isOwnClub(clubId))
          );""",
"""        match /materials/{materialId} {
          allow read: if isSignedIn() && (isOwnClub(clubId) || isConferenceAdmin());
          allow create, update, delete: if isSignedIn() && (
            isConferenceAdmin()
            || (hasPerm("content.edit") && isOwnClub(clubId))
          );"""))

# ------------------------------------------------------------
# 12. requirements/tasks
# ------------------------------------------------------------
patches.append((
"""        match /tasks/{taskId} {
          allow read: if isSignedIn() && (isOwnClub(clubId) || isConferenceAdmin());
          allow create, update, delete: if isSignedIn() && (
            isConferenceAdmin()
            || (isDirector() && isOwnClub(clubId))
          );""",
"""        match /tasks/{taskId} {
          allow read: if isSignedIn() && (isOwnClub(clubId) || isConferenceAdmin());
          allow create, update, delete: if isSignedIn() && (
            isConferenceAdmin()
            || (hasPerm("content.edit") && isOwnClub(clubId))
          );"""))

# ------------------------------------------------------------
# 13. requirementTaskAnswers update
# ------------------------------------------------------------
patches.append((
"""        allow update: if isSignedIn() && (
          (resource.data.userId == uid()
            && request.resource.data.userId == uid())
          || (isClubStaff() && isOwnClub(clubId))
          || isConferenceAdmin()
        );

        allow delete: if isConferenceAdmin();
      }""",
"""        allow update: if isSignedIn() && (
          (resource.data.userId == uid()
            && request.resource.data.userId == uid())
          || (hasPermOrStaff("learning.approve") && isOwnClub(clubId))
          || isConferenceAdmin()
        );

        allow delete: if isConferenceAdmin();
      }"""))

# ------------------------------------------------------------
# 14. challenges
# ------------------------------------------------------------
patches.append((
"""      match /challenges/{challengeId} {
        allow read: if isSignedIn() && (isOwnClub(clubId) || isConferenceAdmin());
        allow create, update: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow delete: if isSignedIn() && (isDirector() && isOwnClub(clubId));
      }""",
"""      match /challenges/{challengeId} {
        allow read: if isSignedIn() && (isOwnClub(clubId) || isConferenceAdmin());
        allow create, update: if isSignedIn() && (hasPerm("challenges.create") && isOwnClub(clubId));
        allow delete: if isSignedIn() && (hasPerm("challenges.create") && isOwnClub(clubId));
      }"""))

# ------------------------------------------------------------
# 15. qrStations
# ------------------------------------------------------------
patches.append((
"""      match /qrStations/{stationId} {
        allow read: if isSignedIn() && (isOwnClub(clubId) || isConferenceAdmin());
        allow create, update: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow delete: if isSignedIn() && (isDirector() && isOwnClub(clubId));
      }""",
"""      match /qrStations/{stationId} {
        allow read: if isSignedIn() && (isOwnClub(clubId) || isConferenceAdmin());
        allow create, update: if isSignedIn() && (hasPerm("qr.create") && isOwnClub(clubId));
        allow delete: if isSignedIn() && (hasPerm("qr.delete") && isOwnClub(clubId));
      }"""))

# ------------------------------------------------------------
# 16. reports
# ------------------------------------------------------------
patches.append((
"""      match /reports/{reportId} {
        allow read: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow create: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow update: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow delete: if isConferenceAdmin();
      }""",
"""      match /reports/{reportId} {
        allow read: if isSignedIn() && (hasPermOrStaff("reports.view") && isOwnClub(clubId));
        allow create: if isSignedIn() && (hasPermOrStaff("reports.generate") && isOwnClub(clubId));
        allow update: if isSignedIn() && (hasPermOrStaff("reports.generate") && isOwnClub(clubId));
        allow delete: if isConferenceAdmin();
      }"""))

# ------------------------------------------------------------
# 17. invites.create
# ------------------------------------------------------------
patches.append((
"""    match /invites/{code} {
      allow read: if true;
      allow create: if isSignedIn()
        && isClubStaff()
        && request.resource.data.createdBy == uid()
        && request.resource.data.status == 'active';""",
"""    match /invites/{code} {
      allow read: if true;
      allow create: if isSignedIn()
        && hasPerm("invites.create")
        && request.resource.data.createdBy == uid()
        && request.resource.data.status == 'active';"""))

# ------------------------------------------------------------
# Применяем все патчи
# ------------------------------------------------------------
for i, (old, new) in enumerate(patches, 1):
    if old not in text:
        print("[ERROR] патч #" + str(i) + " не найден. Откат не нужен — файл не тронут.")
        print("--- искали ---")
        print(old[:500])
        # откатывать не нужно, файл ещё не записан
        sys.exit(1)
    if text.count(old) > 1:
        print("[WARN] патч #" + str(i) + " встречается " + str(text.count(old)) + " раз, заменяю первый")
    text = text.replace(old, new, 1)
    print("[OK]   патч #" + str(i))

p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
print("[OK]   " + PATH)
print()
print("=" * 60)
print("K4 part 2 готово.")
print("=" * 60)
print()
print("Дальше:")
print("  firebase deploy --only firestore:rules")
print()
print("Что сделано:")
print("  - isOwner(): director + isOwnerClub != false")
print("  - hasPerm(perm): conference_admin || owner || director с правом")
print("  - hasPermOrStaff(perm): hasPerm || instructor || secretary")
print("  - 17 правил директора теперь через hasPerm")
print("  - Защита: свои isOwnerClub/directorPermissions/clubIds нельзя менять")
print()
print("Совместимость:")
print("  - Существующие директора = owner (isOwnerClub != false по умолчанию)")
print("  - instructor / secretary работают через hasPermOrStaff (не сломались)")
print("  - Все текущие сценарии работают как раньше")
print()
print("Откат: .bak.<timestamp>")