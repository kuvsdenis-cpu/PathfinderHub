#!/usr/bin/env python3
"""
parse_sabbath_pdf.py — PDF → JSON для Субботней школы (v21).

Title и страницы — из оглавления.
Колонтитулы в начале/середине текста вырезаются (включая '1\\nНазвание').
"""

import json
import re
import argparse
from pathlib import Path

try:
    import fitz
    HAS_FITZ = True
except ImportError:
    fitz = None
    HAS_FITZ = False

HEADERS = ["ПАМЯТНЫЙ СТИХ", "ПОРАЗМЫСЛИ!", "ЦИТАТЫ", "ДЕЙСТВУЙ!",
           "МЫСЛИ НА БУМАГЕ", "КОПАЙ ГЛУБЖЕ!", "МОТИВАЦИЯ"]

YEAR_TITLES = {"A": "А", "B": "Б", "V": "В", "G": "Г"}
LETTERS = r'А-Яа-яЁёA-Za-z'


# ============================================================
# Нормализация и чистка
# ============================================================

def normalize(text: str) -> str:
    text = text.replace('\xa0', ' ')
    text = text.replace('\u00ad', '')
    text = text.replace('\ufffd', '')
    text = text.replace('\x08', ' ')
    return text


def strip_footers(text: str) -> str:
    """
    Убираем колонтитулы:
      начало:  '1\\nКак стать популярным\\n'
      середина: '\\n6\\nУрок 1. …\\n'
      одиночные номера: '\\n6\\n'
    """
    # начало текста: 'N\nНазвание\n' (без ведущего \n)
    text = re.sub(r'^\s*\d+\s*\n\s*[^\n]{3,80}\n', '', text, count=1)
    # 'N\nУрок M. <title>\n' в любом месте
    text = re.sub(r'\n\d+\nУрок\s*\d+\.?\s*[^\n]*\n', '\n', text)
    # 'N\n<title>\n' в любом месте
    text = re.sub(r'\n\d+\n[^\n]{3,80}\n', '\n', text)
    # одиночные номера страниц
    text = re.sub(r'\n\d+\n', '\n', text)
    return text


def strip_dot_lines(text: str) -> str:
    text = re.sub(r'[·•●○◦]', '', text)
    text = re.sub(r'\x08', ' ', text)
    text = re.sub(r'(?:\.\s*){5,}', ' ', text)
    return text


def clean_text(text: str) -> str:
    text = re.sub(r'-\n([а-яё])', r'\1', text)
    text = re.sub(r'[ \t]+', ' ', text)
    text = re.sub(r'\n+', '\n', text)
    return text.strip()


def is_dots_block(text: str) -> bool:
    stripped = text.replace(" ", "").replace("\n", "")
    return bool(stripped) and set(stripped) <= {".", "·", "…"}


def extract_blocks(page) -> str:
    w = page.rect.width
    blocks = page.get_text("blocks")
    kept = [b for b in blocks if b[6] == 0 and b[4] and not is_dots_block(b[4])]
    kept.sort(key=lambda b: (b[0] > w / 2, b[1]))
    return "\n".join(b[4] for b in kept)


# ============================================================
# Оглавление → {n: (page, title)}
# ============================================================

def clean_toc_title(raw: str) -> str:
    raw = re.sub(r'(?:\s*\.\s*){3,}\s*\d+\s*$', '', raw)
    raw = re.sub(r'\s*\.{3,}\s*\d+\s*$', '', raw)
    raw = re.sub(r'-\s*\n\s*', '', raw)
    raw = re.sub(r'\s*\n\s*', ' ', raw)
    raw = re.sub(r'\s+', ' ', raw)
    return raw.strip().rstrip('.,;:—-').strip()


def extract_toc(doc) -> dict:
    toc_text = ""
    for pno in range(min(5, doc.page_count)):
        text = doc[pno].get_text()
        if "Содержание" in text or "СОДЕРЖАНИЕ" in text:
            toc_text = text
            break
    if not toc_text:
        return {}

    result = {}
    matches = list(re.finditer(r'Урок\s*(\d+)\.?', toc_text, re.IGNORECASE))
    for i, m in enumerate(matches):
        n = int(m.group(1))
        if not (1 <= n <= 13) or n in result:
            continue
        start = m.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(toc_text)
        chunk = toc_text[start:end]
        pm = re.search(r'(\d+)\s*$', chunk.rstrip())
        if not pm:
            continue
        printed = int(pm.group(1))
        pdf_index = printed - 1
        if not (0 <= pdf_index < doc.page_count):
            continue
        title = clean_toc_title(chunk)
        result[n] = (pdf_index, title)
    return result


def verify_pages(doc, toc: dict) -> dict:
    out = {}
    for n, (idx, title) in toc.items():
        for delta in (0, 1, -1, 2, -2):
            check = idx + delta
            if 0 <= check < doc.page_count:
                text = doc[check].get_text()
                if text and re.search(rf'Урок\s*{n}\b', text, re.IGNORECASE):
                    out[n] = (check, title)
                    break
        else:
            out[n] = (idx, title)
    return out


# ============================================================
# Парсинг секций
# ============================================================

def find_headers(text: str):
    indices = []
    for h in HEADERS:
        pattern = rf'(?<![{LETTERS}]){re.escape(h)}(?![{LETTERS}])'
        for match in re.finditer(pattern, text):
            indices.append((match.start(), h))
    indices.sort()
    seen = set()
    dedup = []
    for pos, h in indices:
        if pos in seen:
            continue
        seen.add(pos)
        dedup.append((pos, h))
    return dedup


def parse_lesson_text(text: str, lesson_num: int, year: str, quarter: int,
                      title: str) -> dict:
    text = normalize(text)
    text = strip_footers(text)
    text = strip_dot_lines(text)
    text = clean_text(text)

    header_indices = find_headers(text)

    intro_text = text[:header_indices[0][0]] if header_indices else text
    intro_text = re.sub(r'^\s*Урок\s*\d+\.?[^\n]*\n', '', intro_text,
                        count=1, flags=re.IGNORECASE)
    # ещё раз снять ведущий 'N\nНазвание\n', если остался
    intro_text = re.sub(r'^\s*\d+\s*\n\s*[^\n]{3,80}\n', '', intro_text, count=1)
    intro = clean_text(intro_text)

    sections = []
    memory_verse = {}

    for i in range(len(header_indices)):
        start_idx = header_indices[i][0]
        end_idx = header_indices[i + 1][0] if i + 1 < len(header_indices) else len(text)
        header_name = header_indices[i][1]
        section_text = clean_text(text[start_idx:end_idx].replace(header_name, "", 1))

        blocks = []

        if header_name == "ПАМЯТНЫЙ СТИХ":
            ref_match = re.search(r'\(([А-Яа-яЁё0-9\s:,\-\.]+)\)', section_text)
            ref = ref_match.group(1).strip() if ref_match else ""
            verse_text = section_text.replace(ref_match.group(0), "").strip() if ref_match else section_text
            verse_text = re.sub(r'_{3,}', '{0}', verse_text, count=1)
            verse_text = re.sub(r'_{3,}', '{1}', verse_text, count=1)
            verse_text = re.sub(r'\s+([.,;:])', r'\1', verse_text).strip()
            memory_verse = {"text": verse_text, "reference": ref, "answers": ["", ""]}

        elif header_name in ["ПОРАЗМЫСЛИ!", "МЫСЛИ НА БУМАГЕ", "ДЕЙСТВУЙ!", "КОПАЙ ГЛУБЖЕ!"]:
            q_idx = 0
            for q in re.split(r'(?<=[?])\s*', section_text):
                q = q.strip()
                if not q:
                    continue
                blocks.append({"type": "text", "content": q})
                if "?" in q:
                    q_idx += 1
                    blocks.append({
                        "type": "input_text",
                        "id": f"q_{header_name[:4].lower()}_{q_idx}",
                        "placeholder": "Твой ответ...",
                    })

        elif header_name == "ЦИТАТЫ":
            blocks.append({"type": "text", "content": section_text})
            blocks.append({
                "type": "input_text",
                "id": f"quote_{lesson_num}",
                "placeholder": "Твоё предложение с выделенными словами...",
            })

        else:
            blocks.append({"type": "text", "content": section_text})

        sections.append({"title": header_name, "blocks": blocks})

    lesson_id = f"{year}{quarter}_{lesson_num:02d}"
    quarter_title = f"Год {YEAR_TITLES.get(year, year)}, квартал {quarter}. Версия"

    return {
        "lesson_id": lesson_id,
        "year": year,
        "quarter": quarter,
        "quarter_title": quarter_title,
        "lesson_number": lesson_num,
        "title": title,
        "introduction": intro,
        "memory_verse": memory_verse or {"text": "—", "reference": "—", "answers": []},
        "sections": [s for s in sections if s["title"] != "ПАМЯТНЫЙ СТИХ"],
    }


# ============================================================
# Заглушка
# ============================================================

def make_stub(year: str, quarter: int, lesson_num: int) -> dict:
    return {
        "lesson_id": f"{year}{quarter}_{lesson_num:02d}",
        "year": year,
        "quarter": quarter,
        "quarter_title": f"Год {YEAR_TITLES.get(year, year)}, квартал {quarter}. Версия",
        "lesson_number": lesson_num,
        "title": f"Урок {lesson_num}. (в разработке)",
        "introduction": "Материал этого урока пока недоступен. "
                        "Он появится после загрузки PDF с текстовым слоем.",
        "memory_verse": {"text": "—", "reference": "—", "answers": []},
        "sections": [],
    }


# ============================================================
# main
# ============================================================

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pdf", help="Путь к PDF")
    ap.add_argument("--year", required=True, help="A | B | V | G")
    ap.add_argument("--quarter", required=True, type=int, help="1..4")
    ap.add_argument("--out", required=True, help="assets/sabbath_school")
    ap.add_argument("--stub", action="store_true", help="Сгенерировать 13 заглушек")
    ap.add_argument("--start-pages", help="0-индексы через запятую (override)")
    args = ap.parse_args()

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    if args.stub:
        for n in range(1, 14):
            data = make_stub(args.year, args.quarter, n)
            path = out_dir / f"{data['lesson_id']}.json"
            path.write_text(json.dumps(data, ensure_ascii=False, indent=2),
                            encoding="utf-8")
            print(f"  ✓ заглушка {data['lesson_id']} -> {path.name}")
        return

    if not args.pdf:
        raise SystemExit("Нужен --pdf или --stub")
    if not HAS_FITZ:
        raise SystemExit("PyMuPDF не установлен. Выполни: py -m pip install pymupdf")

    doc = fitz.open(args.pdf)
    print(f"📖 {args.pdf}: {doc.page_count} страниц")

    if args.start_pages:
        pages = [int(x) for x in args.start_pages.split(",")]
        lesson_pages = {i + 1: (p, "") for i, p in enumerate(pages)}
        print(f"🔧 страницы вручную: {sorted(lesson_pages.items())}")
    else:
        toc = extract_toc(doc)
        if not toc:
            raise SystemExit("Не нашёл оглавление. Используй --start-pages.")
        lesson_pages = verify_pages(doc, toc)
        for n in sorted(lesson_pages):
            p, t = lesson_pages[n]
            print(f"  📑 {n}: p{p}  {t}")

    for lesson_num in range(1, 14):
        if lesson_num not in lesson_pages:
            print(f"  ⚠ урок {lesson_num}: страница не найдена, пропуск")
            continue
        start, title = lesson_pages[lesson_num]
        end = min([p for (p, _) in lesson_pages.values() if p > start] +
                  [doc.page_count])
        full_text = ""
        for p in range(start, end):
            full_text += extract_blocks(doc[p]) + "\n"

        if not title:
            title = f"Урок {lesson_num}"

        data = parse_lesson_text(full_text, lesson_num, args.year, args.quarter,
                                 title=title)
        path = out_dir / f"{data['lesson_id']}.json"
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2),
                        encoding="utf-8")
        secs = [s["title"] for s in data["sections"]]
        print(f"  ✓ {data['lesson_id']}: {data['title']} | секции: {secs}")


if __name__ == "__main__":
    main()