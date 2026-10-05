#!/usr/bin/env python3
"""
Pathfinder Hub — автономный парсер контента с версионированием.
Сам находит страницы с PDF, скачивает, парсит, сохраняет JSON.
Также извлекает изображения из страниц ступеней и сохраняет их в
app/src/main/assets/levels_images/, привязывая имена к требованиям.

Запуск:
    python parser/parse_all.py

Зависимости установятся автоматически.
"""

import hashlib
import io
import json
import re
import sys
import subprocess
from datetime import datetime
from pathlib import Path
from urllib.parse import urljoin

# ============================================================
# НАСТРОЙКИ
# ============================================================

BASE_URL = "https://yamolod.info"
PROJECT_ROOT = Path(__file__).resolve().parent.parent
OUTPUT_DIR = PROJECT_ROOT / "app" / "src" / "main" / "assets"
CACHE_DIR = Path(__file__).resolve().parent / "_cache"
IMAGES_DIR = OUTPUT_DIR / "levels_images"

# Версия контента — генерируется при каждом запуске
VERSION = datetime.now().strftime("%Y.%m.%d.%H%M")

# --- Фильтр картинок ---
MIN_SIZE = 150           # отсеять иконки/чекбоксы
MAX_SIZE = 1500          # отсеять целые страницы PDF
MAX_ASPECT = 5.0         # отсеять полоски/линии/разделители
MAX_WHITE_RATIO = 0.9    # отсеять пустые бланки/фоны
MIN_AREA = 200 * 200     # минимальная площадь (для отсева мелких артефактов)

PAGES_WITH_PDF = {
    "dnevniki": "https://yamolod.info/sluzhenie/klub-sledopyt/dnevniki/",
    "zachetnye_blanki": "https://yamolod.info/sluzhenie/klub-sledopyt/zachetnye-blanki-dlya-liderov/",
}

DIRECT_PDF_URLS = {
    "honor-path.pdf": "https://yamolod.info/wp-content/uploads/files/pathfinder/honor/honor-path.pdf",
}

LEVEL_FILE_MAP = {
    "drug": "friend",
    "sputnik": "companion",
    "issled": "explorer",
    "razv": "scout",
    "putesh": "traveler",
    "prov": "guide",
}

CATEGORIES = [
    {"id": "household", "name": "Домоводство", "order": 1, "startPage": 11},
    {"id": "agriculture", "name": "Сельскохозяйственные навыки", "order": 2, "startPage": 33},
    {"id": "spiritual", "name": "Духовный рост, миссионерская работа и наследие", "order": 3, "startPage": 53},
    {"id": "adra", "name": "ADRA", "order": 4, "startPage": 87},
    {"id": "health", "name": "Здоровье и наука", "order": 5, "startPage": 101},
    {"id": "arts", "name": "Искусство, ремесла и хобби", "order": 6, "startPage": 127},
    {"id": "nature", "name": "Природа", "order": 7, "startPage": 221},
    {"id": "outdoor", "name": "Активный отдых", "order": 8, "startPage": 309},
    {"id": "vocational", "name": "Профессиональные навыки", "order": 9, "startPage": 403},
    {"id": "master", "name": "Мастер", "order": 10, "startPage": 445},
]

LEVELS = [
    {"id": "friend", "name": "Друг", "order": 1, "ageMin": 10, "grade": 5, "key": "drug"},
    {"id": "companion", "name": "Спутник", "order": 2, "ageMin": 11, "grade": 6, "key": "sputnik"},
    {"id": "explorer", "name": "Исследователь", "order": 3, "ageMin": 12, "grade": 7, "key": "issled"},
    {"id": "scout", "name": "Разведчик", "order": 4, "ageMin": 13, "grade": 8, "key": "razv"},
    {"id": "traveler", "name": "Путешественник", "order": 5, "ageMin": 14, "grade": 9, "key": "putesh"},
    {"id": "guide", "name": "Проводник", "order": 6, "ageMin": 15, "grade": 10, "key": "prov"},
]

SECTIONS = [
    {"id": "general", "name": "Общие", "order": 1},
    {"id": "spiritual", "name": "Духовное возрастание", "order": 2},
    {"id": "service", "name": "Служение ближним", "order": 3},
    {"id": "friendship", "name": "Развитие дружеских отношений", "order": 4},
    {"id": "health", "name": "Здоровье и физическая культура", "order": 5},
    {"id": "leadership", "name": "Организация и развитие лидерских качеств", "order": 6},
    {"id": "nature", "name": "Исследование природы", "order": 7},
    {"id": "camping", "name": "Лагерные и походные навыки", "order": 8},
    {"id": "life_skills", "name": "Жизненные навыки", "order": 9},
]

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                  "AppleWebKit/537.36 (KHTML, like Gecko) "
                  "Chrome/120.0 Safari/537.36 PathfinderHubParser/1.0",
    "Accept": "text/html,application/xhtml+xml,application/pdf,*/*",
    "Accept-Language": "ru-RU,ru;q=0.9,en;q=0.8",
}

# ============================================================
# УСТАНОВКА ЗАВИСИМОСТЕЙ
# ============================================================

def ensure_deps():
    for pkg in ["pdfplumber", "requests", "beautifulsoup4", "PyMuPDF", "Pillow"]:
        module_name = pkg.replace("-", "_").lower()
        import_name = "fitz" if pkg == "PyMuPDF" else module_name
        try:
            __import__(import_name)
        except ImportError:
            print(f"📦 Устанавливаю {pkg}…")
            subprocess.check_call(
                [sys.executable, "-m", "pip", "install", pkg, "-q"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )

# ============================================================
# СКАЧИВАНИЕ PDF
# ============================================================

def find_pdf_links(page_url, session):
    from bs4 import BeautifulSoup
    links = set()
    try:
        r = session.get(page_url, timeout=30, headers=HEADERS)
        r.raise_for_status()
        soup = BeautifulSoup(r.text, "html.parser")
        for a in soup.find_all("a", href=True):
            href = a["href"].strip()
            low = href.lower()
            if ".pdf" in low:
                links.add(urljoin(page_url, href))
            elif "wp-content" in low and ("upload" in low or "file" in low):
                links.add(urljoin(page_url, href))
    except Exception as e:
        print(f"   ⚠️  Ошибка чтения {page_url}: {e}")
    return links


def download_file(url, target, session):
    try:
        r = session.get(url, timeout=60, stream=True, headers=HEADERS)
        r.raise_for_status()
        content_type = r.headers.get("Content-Type", "").lower()
        if "pdf" not in content_type and not url.lower().endswith(".pdf"):
            return False
        with open(target, "wb") as f:
            for chunk in r.iter_content(chunk_size=8192):
                f.write(chunk)
        with open(target, "rb") as f:
            if f.read(4) != b"%PDF":
                target.unlink()
                return False
        return True
    except Exception:
        if target.exists():
            target.unlink()
        return False


def download_all_pdfs():
    import requests
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    session = requests.Session()
    downloaded = {}

    print("\n   [Прямые ссылки]")
    for filename, url in DIRECT_PDF_URLS.items():
        target = CACHE_DIR / filename
        if target.exists() and target.stat().st_size > 10_000:
            print(f"   ✓ {filename} (уже в кэше)")
            downloaded[filename] = target
            continue
        print(f"   ⬇ {filename} …", end=" ", flush=True)
        if download_file(url, target, session):
            print(f"OK ({target.stat().st_size // 1024} КБ)")
            downloaded[filename] = target
        else:
            print("не удалось")

    print("\n   [Поиск на страницах]")
    all_links = set()
    for page_name, page_url in PAGES_WITH_PDF.items():
        print(f"   → {page_name}")
        found = find_pdf_links(page_url, session)
        print(f"     найдено ссылок: {len(found)}")
        all_links |= found

    print(f"\n   [Скачивание {len(all_links)} ссылок]")
    for link in sorted(all_links):
        name = link.split("/")[-1].split("?")[0]
        if not name.lower().endswith(".pdf"):
            name = name + ".pdf"
        target = CACHE_DIR / name
        if target.exists() and target.stat().st_size > 10_000:
            downloaded[name] = target
            continue
        got = False
        for variant in [link, link.rstrip("/") + ".pdf"]:
            if download_file(variant, target, session):
                print(f"   ✓ {name} ({target.stat().st_size // 1024} КБ)")
                downloaded[name] = target
                got = True
                break
        if not got:
            print(f"   ✗ {name}")

    return downloaded


def match_level_files(downloaded):
    result = {}
    for filename, path in downloaded.items():
        low = filename.lower()
        if "honor" in low or "blank" in low:
            continue
        for level in LEVELS:
            if level["key"] in low:
                result[level["id"]] = path
                break
    return result

# ============================================================
# ПАРСИНГ СПЕЦИАЛИЗАЦИЙ
# ============================================================

def detect_honor_title(line):
    s = line.strip()
    if not s or len(s) > 60:
        return False
    if s.endswith((".", ",")):
        return False
    if not s[0].isupper():
        return False
    skip = ["требования", "уровень", "год", "первоисточник", "содержание"]
    return not any(w in s.lower() for w in skip)


def detect_level(text):
    t = text.lower()
    if "уровень 3" in t or "продвинутый" in t:
        return 3
    if "уровень 2" in t or "средний" in t:
        return 2
    return 1


def parse_honors_pdf(pdf_path):
    import pdfplumber
    result = {"version": VERSION, "categories": [], "honors": []}
    seen_categories = set()

    with pdfplumber.open(pdf_path) as pdf:
        current_category = None
        current_honor = None
        counter = 0

        for page_idx, page in enumerate(pdf.pages):
            page_num = page_idx + 1
            for cat in CATEGORIES:
                if page_num >= cat["startPage"]:
                    if cat["id"] not in seen_categories:
                        seen_categories.add(cat["id"])
                        result["categories"].append({
                            "id": cat["id"], "name": cat["name"],
                            "order": cat["order"], "icon": None,
                        })
                    current_category = cat["id"]

            text = page.extract_text()
            if not text:
                continue

            for line in text.split("\n"):
                line = line.strip()
                if not line:
                    continue
                if any(cat["name"].lower() in line.lower() for cat in CATEGORIES):
                    continue

                if detect_honor_title(line):
                    if current_honor and current_honor["requirements"]:
                        result["honors"].append(current_honor)
                    counter += 1
                    current_honor = {
                        "id": f"honor_{counter:03d}", "name": line,
                        "categoryId": current_category or "nature",
                        "level": 1, "year": 0, "source": "ЕАД",
                        "description": "", "version": "1.0",
                        "isLocal": False, "localClubId": None, "scope": "global",
                        "promotedAt": None, "promotedBy": None,
                        "contentVersion": VERSION, "requirements": [],
                    }
                elif current_honor:
                    if "уровень" in line.lower():
                        current_honor["level"] = detect_level(line)
                    elif re.search(r"(19|20)\d{2}", line):
                        m = re.search(r"(19|20)\d{2}", line)
                        current_honor["year"] = int(m.group())
                    elif re.match(r"^\d+[\.\)]\s+", line) or line.startswith("•"):
                        req = re.sub(r"^\d+[\.\)]\s+", "", line).strip()
                        if req:
                            rid = f"{current_honor['id']}_req_{len(current_honor['requirements']) + 1:02d}"
                            current_honor["requirements"].append({
                                "id": rid, "honorId": current_honor["id"],
                                "text": req, "type": "theory" if len(req) < 100 else "practice",
                                "order": len(current_honor["requirements"]) + 1,
                            })
                    elif not current_honor["description"]:
                        current_honor["description"] = line

        if current_honor and current_honor["requirements"]:
            result["honors"].append(current_honor)

    return result

# ============================================================
# ПАРСИНГ СТУПЕНЕЙ (ИСПРАВЛЕННАЯ ВЕРСИЯ + КАРТИНКИ)
# ============================================================

def detect_section(line: str):
    """
    Ищет заголовки секций в строке, игнорируя регистр и опечатки.
    Возвращает id секции или None.
    """
    low = line.strip().upper()
    clean_low = re.sub(r'^[\d\.\)\s•▪✓-]+', '', low).strip()
    if not clean_low:
        return None

    section_keywords = {
        "general": ["ОБЩИЕ", "ОБШИЕ"],
        "spiritual": ["ДУХОВНОЕ ВОЗРАСТАНИЕ"],
        "service": ["СЛУЖЕНИЕ БЛИЖНИМ", "СПУЖЕНИЕ БЛИЖНИМ"],
        "friendship": ["РАЗВИТИЕ ДРУЖЕСКИХ ОТНОШЕНИЙ"],
        "health": ["ЗДОРОВЬЕ И ФИЗИЧЕСКАЯ КУЛЬТУРА", "ЭДОРОВЬЕ И ФИЗИЧЕСКАЯ КУЛЬТУРА"],
        "leadership": ["ОРГАНИЗАЦИЯ И РАЗВИТИЕ ЛИДЕРСКИХ КАЧЕСТВ", "ОРГАНИЗАЦИЯ И РАЗВИТИЕ ПИДЕРСКИХ КАЧЕСТВ"],
        "nature": ["ИССЛЕДОВАНИЕ ПРИРОДЫ", "ИССПЕДОВАНИЕ ПРИРОДЫ"],
        "camping": ["ЛАГЕРНЫЕ И ПОХОДНЫЕ НАВЫКИ", "ПАГЕРНЫЕ И ПОХОДНЫЕ НАВЫКИ"],
        "life_skills": ["ЖИЗНЕННЫЕ НАВЫКИ"],
    }

    for section_id, keywords in section_keywords.items():
        for keyword in keywords:
            if keyword in clean_low:
                return section_id
    return None


def _pil_metrics(image_bytes):
    """
    Возвращает (w, h, white_ratio, aspect) или None, если картинку
    не удалось прочитать. white_ratio — доля пикселей, близких к белому.
    """
    try:
        from PIL import Image
    except ImportError:
        return None
    try:
        img = Image.open(io.BytesIO(image_bytes))
        if img.mode not in ("RGB", "L"):
            img = img.convert("RGB")
        w, h = img.size
        aspect = max(w / h, h / w) if h else 0

        # Уменьшаем до 100×100 для скорости и считаем долю белых
        small = img.resize((100, 100))
        if hasattr(small, "get_flattened_data"):
            pixels = list(small.get_flattened_data())
        else:
            pixels = list(small.getdata())

        if small.mode == "RGB":
            whites = sum(1 for p in pixels if p[0] > 240 and p[1] > 240 and p[2] > 240)
        else:
            whites = sum(1 for p in pixels if p > 240)
        white_ratio = whites / len(pixels)
        return w, h, white_ratio, aspect
    except Exception:
        return None


def should_keep_image(w, h, white_ratio, aspect, md5, seen_md5):
    """
    True — картинка полезная, False — мусор.
    Фильтры: дедупликация, размер, пропорции, белизна, площадь.
    """
    # 1. Дедупликация
    if md5 in seen_md5:
        return False

    # 2. Мелкие иконки / чекбоксы
    if w < MIN_SIZE or h < MIN_SIZE:
        return False

    # 3. Целые страницы PDF
    if w > MAX_SIZE or h > MAX_SIZE:
        return False

    # 4. Вытянутые полоски / линии
    if aspect > MAX_ASPECT:
        return False

    # 5. Пустые бланки / фоны
    if white_ratio > MAX_WHITE_RATIO:
        return False

    # 6. Совсем мелкие по площади
    if w * h < MIN_AREA:
        return False

    return True


def extract_images_from_pdf(pdf_path, level_meta):
    """
    Извлекает изображения из PDF, фильтрует мусор, сохраняет оставшиеся
    в IMAGES_DIR. Возвращает dict: {page_num_1_based: [image_path, ...]}
    """
    import fitz
    IMAGES_DIR.mkdir(parents=True, exist_ok=True)
    page_images = {}

    seen_md5 = set()
    stats = {"saved": 0, "skipped": 0}

    with fitz.open(pdf_path) as doc:
        for page_num_0, page in enumerate(doc):
            page_num = page_num_0 + 1
            filenames = []

            for img_index, img_info in enumerate(page.get_images(full=True)):
                xref = img_info[0]
                try:
                    base_image = doc.extract_image(xref)
                except Exception:
                    continue
                image_bytes = base_image["image"]
                image_ext = base_image["ext"]

                # Метрики
                metrics = _pil_metrics(image_bytes)
                if metrics is None:
                    stats["skipped"] += 1
                    continue
                w, h, white_ratio, aspect = metrics

                # Дедупликация по MD5
                md5 = hashlib.md5(image_bytes).hexdigest()

                if not should_keep_image(w, h, white_ratio, aspect, md5, seen_md5):
                    stats["skipped"] += 1
                    continue

                seen_md5.add(md5)

                filename = f"{level_meta['id']}_page{page_num}_img{img_index+1}.{image_ext}"
                filepath = IMAGES_DIR / filename
                try:
                    with open(filepath, "wb") as f:
                        f.write(image_bytes)
                    filenames.append(f"levels_images/{filename}")
                    stats["saved"] += 1
                except Exception as e:
                    print(f"   ⚠️  Не сохранил {filename}: {e}")

            if filenames:
                page_images[page_num] = filenames

    print(f"   🖼  {level_meta['name']}: сохранено {stats['saved']}, "
          f"отфильтровано {stats['skipped']}")
    return page_images


def parse_level_pdf(pdf_path, level_meta):
    import pdfplumber

    # Картинки по страницам (уже отфильтрованные)
    page_images = extract_images_from_pdf(pdf_path, level_meta)

    all_sections = {
        s["id"]: {"id": s["id"], "levelId": level_meta["id"], "name": s["name"],
                  "order": s["order"], "requirements": []}
        for s in SECTIONS
    }

    current_section_id = "general"
    current_req_text = ""
    current_req_is_advanced = False
    current_req_page = 1  # страница, на которой началось требование

    def finalize_requirement():
        nonlocal current_req_text, current_req_is_advanced, current_req_page
        text = current_req_text.strip()
        if text:
            section = all_sections[current_section_id]
            req_index = len(section["requirements"]) + 1
            rid = f"{level_meta['id']}_{current_section_id}_req_{req_index:02d}"
            # Собираем картинки с текущей страницы (и соседних, если требование跨)
            imgs = []
            for pg in {current_req_page, current_req_page - 1, current_req_page + 1}:
                imgs.extend(page_images.get(pg, []))
            # Дедупликация с сохранением порядка
            seen = set()
            imgs_unique = []
            for im in imgs:
                if im not in seen:
                    seen.add(im)
                    imgs_unique.append(im)

            section["requirements"].append({
                "id": rid,
                "sectionId": current_section_id,
                "levelId": level_meta["id"],
                "text": text,
                "type": "boolean",
                "order": req_index,
                "isAdvanced": current_req_is_advanced,
                "imageUrls": imgs_unique,
            })
        current_req_text = ""
        current_req_is_advanced = False
        current_req_page = 1

    with pdfplumber.open(pdf_path) as pdf:
        for page_idx, page in enumerate(pdf.pages):
            page_num = page_idx + 1
            text = page.extract_text()
            if not text:
                continue

            for line in text.split("\n"):
                line = line.strip()
                if not line or re.match(r'^\d+\s*$', line):
                    continue

                # 1. Заголовок раздела?
                section_id = detect_section(line)
                if section_id:
                    finalize_requirement()
                    if section_id not in all_sections:
                        all_sections[section_id] = {
                            "id": section_id, "levelId": level_meta["id"],
                            "name": line.strip(), "order": 99, "requirements": []
                        }
                    current_section_id = section_id
                    continue

                # 2. Пометка "Повышенное"?
                if "повышенное" in line.lower() or "повышенные" in line.lower():
                    finalize_requirement()
                    current_req_is_advanced = True
                    continue

                # 3. Новое требование или продолжение?
                match = re.match(r"^\s*(\d+[\.\)]|[•▪✓-])\s*(.*)", line)
                if match:
                    finalize_requirement()
                    current_req_text = match.group(2).strip()
                    current_req_page = page_num
                elif current_req_text:
                    current_req_text += " " + line
                else:
                    # Строка до первого требования — игнорируем (мусор/заголовки PDF)
                    pass

        finalize_requirement()

    sorted_sections = sorted(
        [s for s in all_sections.values() if s["requirements"]],
        key=lambda s: s.get("order", 99)
    )

    return {
        "level": {
            "id": level_meta["id"], "name": level_meta["name"],
            "order": level_meta["order"], "ageMin": level_meta["ageMin"],
            "grade": level_meta["grade"], "icon": None,
            "contentVersion": VERSION,
        },
        "sections": sorted_sections,
    }

# ============================================================
# ГЛАВНАЯ ЛОГИКА
# ============================================================

def main():
    print("=" * 60)
    print("  Pathfinder Hub — автономный парсер")
    print("=" * 60)
    print(f"  Версия контента: {VERSION}")

    ensure_deps()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    IMAGES_DIR.mkdir(parents=True, exist_ok=True)
    print(f"\n📁 Вывод: {OUTPUT_DIR}")
    print(f"📁 Картинки: {IMAGES_DIR}")
    print(f"📁 Кэш:  {CACHE_DIR}")

    print("\n" + "─" * 60)
    print("1️⃣  СКАЧИВАНИЕ PDF")
    print("─" * 60)
    downloaded = download_all_pdfs()

    if not downloaded:
        print("\n❌ PDF не скачаны.")
        sys.exit(1)
    print(f"\n   Всего скачано: {len(downloaded)} файлов")

    print("\n" + "─" * 60)
    print("2️⃣  СПЕЦИАЛИЗАЦИИ")
    print("─" * 60)
    honor_pdf = next((p for n, p in downloaded.items() if "honor" in n.lower()), None)
    if honor_pdf:
        print(f"Парсим {honor_pdf.name}…")
        try:
            data = parse_honors_pdf(honor_pdf)
            out = OUTPUT_DIR / "honors.json"
            with open(out, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            total_req = sum(len(h["requirements"]) for h in data["honors"])
            print(f"✅ {out.name}: {len(data['honors'])} специализаций, "
                  f"{total_req} требований (версия {VERSION})")
        except Exception as e:
            print(f"❌ Ошибка парсинга: {e}")
    else:
        print("⚠️  honor-path.pdf не найден")

    print("\n" + "─" * 60)
    print("3️⃣  СТУПЕНИ")
    print("─" * 60)
    level_files = match_level_files(downloaded)
    if not level_files:
        print("⚠️  Ни один дневник не распознан")
    else:
        levels_data = {"version": VERSION, "levels": []}
        for meta in LEVELS:
            if meta["id"] not in level_files:
                print(f"⚠️  {meta['name']}: дневник не найден")
                continue
            pdf_path = level_files[meta["id"]]
            print(f"Парсим {meta['name']} ({pdf_path.name})…")
            try:
                parsed = parse_level_pdf(pdf_path, meta)
                levels_data["levels"].append(parsed)
                total = sum(len(s["requirements"]) for s in parsed["sections"])
                imgs = sum(len(r.get("imageUrls", [])) for s in parsed["sections"] for r in s["requirements"])
                print(f"   ✓ {meta['name']}: {total} требований, {imgs} привязок картинок")
            except Exception as e:
                print(f"   ❌ Ошибка: {e}")

        if levels_data["levels"]:
            out = OUTPUT_DIR / "levels.json"
            with open(out, "w", encoding="utf-8") as f:
                json.dump(levels_data, f, ensure_ascii=False, indent=2)
            print(f"\n✅ {out.name}: {len(levels_data['levels'])} ступеней "
                  f"(версия {VERSION})")

    print("\n" + "=" * 60)
    print("  ГОТОВО")
    print("=" * 60)
    print(f"\n📁 JSON: {OUTPUT_DIR}")
    print(f"📁 Картинки: {IMAGES_DIR}")
    print(f"🔢 Версия: {VERSION}\n")


if __name__ == "__main__":
    main()