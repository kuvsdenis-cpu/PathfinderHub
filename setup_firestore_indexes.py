#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Фикс индекса comments: привести в файле к тому виду, что в облаке.
"""

import json
import shutil
import sys
from pathlib import Path


def find_project_root() -> Path:
    here = Path.cwd()
    for p in [here] + list(here.parents):
        if (p / "settings.gradle.kts").exists() or (p / "settings.gradle").exists():
            return p
    print("❌ Не найден корень проекта.")
    sys.exit(1)


ROOT = find_project_root()
INDEXES_FILE = ROOT / "firestore.indexes.json"

# Точная копия того, что в облаке
COMMENTS_CLOUD = {
    "collectionGroup": "comments",
    "queryScope": "COLLECTION",
    "fields": [
        {"fieldPath": "status", "order": "ASCENDING"},
        {"fieldPath": "targetId", "order": "ASCENDING"},
        {"fieldPath": "targetType", "order": "ASCENDING"},
        {"fieldPath": "createdAt", "order": "ASCENDING"},
    ],
}


def key(idx: dict) -> str:
    fields = "|".join(
        f"{f.get('fieldPath')}:{f.get('order', f.get('arrayConfig', '?'))}"
        for f in idx.get("fields", [])
    )
    return f"{idx.get('collectionGroup')}::{fields}"


def main():
    print("=" * 60)
    print("  Фикс индекса comments")
    print("=" * 60)

    if not INDEXES_FILE.exists():
        print(f"❌ Не найден {INDEXES_FILE}")
        sys.exit(1)

    data = json.loads(INDEXES_FILE.read_text(encoding="utf-8"))
    indexes = data.get("indexes", [])

    # Удаляем все старые comments
    before = len(indexes)
    indexes = [i for i in indexes if i.get("collectionGroup") != "comments"]
    removed = before - len(indexes)
    print(f"🗑  Удалено старых comments: {removed}")

    # Добавляем точную копию
    indexes.append(COMMENTS_CLOUD)
    print(f"➕ Добавлен comments с полями: status, targetId, targetType, createdAt")

    data["indexes"] = indexes
    if "fieldOverrides" not in data:
        data["fieldOverrides"] = []

    shutil.copy2(INDEXES_FILE, INDEXES_FILE.with_suffix(".json.bak"))
    INDEXES_FILE.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")

    print(f"✅ Записан {INDEXES_FILE.name}. Всего индексов: {len(indexes)}")
    print("\nДальше:")
    print("  firebase deploy --only firestore:indexes")
    print("=" * 60)


if __name__ == "__main__":
    main()