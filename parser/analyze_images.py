#!/usr/bin/env python3
"""
Анализатор картинок в PDF ступеней.
Для каждой картинки выводит размеры, пропорции, долю белого, MD5.
Помогает отличить полезные (схемы, кроссворды, определители)
от мусора (грамоты, медали, логотипы, рамки, бублики).

Запуск:
    py analyze_images.py
"""

import hashlib
import sys
from collections import defaultdict
from pathlib import Path

try:
    import fitz  # PyMuPDF
except ImportError:
    print("Установите PyMuPDF: py -m pip install PyMuPDF")
    sys.exit(1)

try:
    from PIL import Image
    import io
except ImportError:
    print("Установите Pillow: py -m pip install Pillow")
    sys.exit(1)


CACHE_DIR = Path(__file__).resolve().parent / "_cache"

PDFS = [
    "drug19.pdf",
    "sputnik19.pdf",
    "issled19.pdf",
    "razv19.pdf",
    "putesh19.pdf",
    "prov19.pdf",
]


def analyze_image(image_bytes):
    """Возвращает dict с метриками картинки."""
    img = Image.open(io.BytesIO(image_bytes))
    if img.mode not in ("RGB", "L"):
        img = img.convert("RGB")
    w, h = img.size
    aspect = max(w / h, h / w) if h else 0

    # Доля белых пикселей (для отсева пустых бланков и фонов)
    small = img.resize((100, 100))
    pixels = list(small.getdata())
    if small.mode == "RGB":
        whites = sum(1 for p in pixels if p[0] > 240 and p[1] > 240 and p[2] > 240)
    else:
        whites = sum(1 for p in pixels if p > 240)
    white_ratio = whites / len(pixels)

    # Средний цвет (для понимания — серое/цветное)
    if small.mode == "RGB":
        r = sum(p[0] for p in pixels) / len(pixels)
        g = sum(p[1] for p in pixels) / len(pixels)
        b = sum(p[2] for p in pixels) / len(pixels)
        avg_color = (int(r), int(g), int(b))
    else:
        v = sum(pixels) / len(pixels)
        avg_color = (int(v), int(v), int(v))

    md5 = hashlib.md5(image_bytes).hexdigest()

    return {
        "w": w,
        "h": h,
        "aspect": round(aspect, 2),
        "white_ratio": round(white_ratio, 2),
        "avg_color": avg_color,
        "md5": md5,
        "bytes": len(image_bytes),
    }


def main():
    if not CACHE_DIR.is_dir():
        print(f"Папка не найдена: {CACHE_DIR}")
        print("Сначала запустите parse_all.py, чтобы PDF скачались.")
        sys.exit(1)

    # {level: {page: [info, ...]}}
    all_data = {}
    # {md5: [(level, page, filename), ...]}
    md5_index = defaultdict(list)

    for pdf_name in PDFS:
        pdf_path = CACHE_DIR / pdf_name
        if not pdf_path.exists():
            print(f"⚠️  Нет файла: {pdf_path}")
            continue

        level = pdf_name.replace("19.pdf", "")  # drug, sputnik, ...
        all_data[level] = {}

        print(f"\n{'='*70}")
        print(f"  {pdf_name}  ({level})")
        print(f"{'='*70}")

        with fitz.open(pdf_path) as doc:
            for page_num_0, page in enumerate(doc):
                page_num = page_num_0 + 1
                images = page.get_images(full=True)
                if not images:
                    continue

                page_info = []
                for img_index, img_info in enumerate(images):
                    xref = img_info[0]
                    base = doc.extract_image(xref)
                    image_bytes = base["image"]
                    ext = base["ext"]
                    filename = f"{level}_page{page_num}_img{img_index+1}.{ext}"

                    metrics = analyze_image(image_bytes)
                    metrics["filename"] = filename
                    metrics["page"] = page_num
                    page_info.append(metrics)

                    md5_index[metrics["md5"]].append(
                        (level, page_num, filename)
                    )

                all_data[level][page_num] = page_info

    # ============ ВЫВОД ============

    print(f"\n\n{'='*70}")
    print("  ПОСТРАНИЧНЫЙ ОТЧЁТ")
    print(f"{'='*70}")

    for level, pages in all_data.items():
        print(f"\n--- {level} ---")
        for page_num in sorted(pages.keys()):
            for m in pages[page_num]:
                print(
                    f"  p{page_num:>3}  {m['w']:>5}x{m['h']:<5} "
                    f"ar={m['aspect']:<5} "
                    f"white={m['white_ratio']:<5} "
                    f"color={m['avg_color']}  "
                    f"{m['bytes']//1024:>4}KB  "
                    f"{m['filename']}"
                )

    # ============ ДУБЛИКАТЫ ============

    print(f"\n\n{'='*70}")
    print("  ДУБЛИКАТЫ (одинаковый MD5)")
    print(f"{'='*70}")

    dup_count = 0
    for md5, items in md5_index.items():
        if len(items) > 1:
            dup_count += 1
            print(f"\n  MD5 {md5[:8]}… × {len(items)}:")
            for level, page, fname in items:
                print(f"    {level} p{page} {fname}")

    if dup_count == 0:
        print("  Нет дубликатов.")

    # ============ ПОДОЗРЕВАЕМЫЕ НА МУСОР ============

    print(f"\n\n{'='*70}")
    print("  ПОДОЗРЕВАЕМЫЕ НА МУСОР")
    print(f"{'='*70}")

    print("\n  [Мелкие < 150×150]")
    for level, pages in all_data.items():
        for page_num, items in pages.items():
            for m in items:
                if m["w"] < 150 or m["h"] < 150:
                    print(f"    {level} p{page_num} {m['w']}x{m['h']} {m['filename']}")

    print("\n  [Вытянутые aspect > 5]")
    for level, pages in all_data.items():
        for page_num, items in pages.items():
            for m in items:
                if m["aspect"] > 5:
                    print(f"    {level} p{page_num} ar={m['aspect']} {m['filename']}")

    print("\n  [Почти белые white_ratio > 0.9]")
    for level, pages in all_data.items():
        for page_num, items in pages.items():
            for m in items:
                if m["white_ratio"] > 0.9:
                    print(f"    {level} p{page_num} white={m['white_ratio']} {m['filename']}")

    print("\n  [Очень крупные > 1500×1500]")
    for level, pages in all_data.items():
        for page_num, items in pages.items():
            for m in items:
                if m["w"] > 1500 or m["h"] > 1500:
                    print(f"    {level} p{page_num} {m['w']}x{m['h']} {m['filename']}")

    # ============ СТАТИСТИКА ============

    print(f"\n\n{'='*70}")
    print("  СТАТИСТИКА")
    print(f"{'='*70}")

    total = sum(len(items) for pages in all_data.values() for items in pages.values())
    print(f"  Всего картинок: {total}")
    print(f"  Уникальных (по MD5): {len(md5_index)}")
    print(f"  Дубликатов: {total - len(md5_index)}")


if __name__ == "__main__":
    main()