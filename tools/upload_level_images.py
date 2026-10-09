#!/usr/bin/env python3
"""
Разово заливает картинки ступеней из app/src/main/assets/levels_images/
в Yandex Object Storage (S3-совместимый).

Запуск:
    pip install boto3
    set YC_ACCESS_KEY_ID=YCAJEag5uFhxH6i8w8RrdANmV
    set YC_SECRET_ACCESS_KEY=YCPfDthUMav7PfwjlnDF7CHYGzoRMdPVyM2omZdL
    set YC_BUCKET=pathfinder-hub-media
    py upload_level_images.py

По умолчанию — sync: пропускает уже залитые файлы (сравнивает по размеру).
Флаг --force перезаливает всё (полезно, чтобы обновить Cache-Control).
"""

import argparse
import os
import sys
from pathlib import Path

import boto3
from botocore.config import Config
from botocore.exceptions import ClientError

ENDPOINT = "https://storage.yandexcloud.net"
REGION = "ru-central1"
PREFIX = "levels_images/"

REPO_ROOT = Path(__file__).resolve().parent.parent
LOCAL_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "levels_images"


def make_client(access_key: str, secret_key: str):
    return boto3.client(
        "s3",
        endpoint_url=ENDPOINT,
        region_name=REGION,
        aws_access_key_id=access_key,
        aws_secret_access_key=secret_key,
        config=Config(signature_version="s3v4"),
    )


def content_type_for(path: Path) -> str:
    ext = path.suffix.lower().lstrip(".")
    return {
        "jpg": "image/jpeg", "jpeg": "image/jpeg",
        "png": "image/png",
        "gif": "image/gif",
        "webp": "image/webp",
        "bmp": "image/bmp",
        "svg": "image/svg+xml",
        "heic": "image/heic", "heif": "image/heic",
    }.get(ext, "application/octet-stream")


def remote_size(client, bucket: str, key: str):
    try:
        resp = client.head_object(Bucket=bucket, Key=key)
        return resp["ContentLength"]
    except ClientError as e:
        code = e.response.get("Error", {}).get("Code", "")
        if code in ("404", "NoSuchKey", "NotFound"):
            return None
        raise


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true",
                        help="перезалить даже если файл уже есть")
    parser.add_argument("--dry-run", action="store_true",
                        help="только показать, что будет залито")
    args = parser.parse_args()

    access_key = os.environ.get("YC_ACCESS_KEY_ID")
    secret_key = os.environ.get("YC_SECRET_ACCESS_KEY")
    bucket = os.environ.get("YC_BUCKET")

    if not (access_key and secret_key and bucket):
        print("Не заданы YC_ACCESS_KEY_ID / YC_SECRET_ACCESS_KEY / YC_BUCKET",
              file=sys.stderr)
        sys.exit(1)

    if not LOCAL_DIR.is_dir():
        print(f"Папка не найдена: {LOCAL_DIR}", file=sys.stderr)
        sys.exit(1)

    client = make_client(access_key, secret_key)

    files = sorted(p for p in LOCAL_DIR.rglob("*") if p.is_file())
    print(f"Найдено файлов: {len(files)}")

    uploaded = 0
    skipped = 0
    failed = 0

    for path in files:
        rel = path.relative_to(LOCAL_DIR).as_posix()
        key = PREFIX + rel
        local_size = path.stat().st_size

        if not args.force:
            existing = remote_size(client, bucket, key)
            if existing == local_size:
                skipped += 1
                continue

        if args.dry_run:
            print(f"[dry-run] {key} ({local_size} B)")
            continue

        try:
            with path.open("rb") as f:
                client.put_object(
                    Bucket=bucket,
                    Key=key,
                    Body=f,
                    ContentType=content_type_for(path),
                    CacheControl="public, max-age=604800",
                )
            uploaded += 1
            print(f"↑ {key}")
        except Exception as e:
            failed += 1
            print(f"✗ {key}: {e}", file=sys.stderr)

    print(f"\nГотово. Загружено: {uploaded}, пропущено: {skipped}, ошибок: {failed}")


if __name__ == "__main__":
    main()