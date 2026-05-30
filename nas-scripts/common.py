"""
思维札记 — 共享工具函数 + DeepSeek LLM 调用封装
使用方式: from common import *
"""

from __future__ import annotations
import json
import os
import re
import sys
from datetime import date, timedelta
from pathlib import Path
from collections import Counter, defaultdict

# ---------- 路径配置 ----------
BASE_DIR = Path(os.environ.get("THOUGHTS_BASE_DIR", "/var/services/homes/<USER>/thoughts"))
RAW_DIR = BASE_DIR / "raw"
DAILY_DIR = BASE_DIR / "daily"
WEEKLY_DIR = BASE_DIR / "weekly"
MONTHLY_DIR = BASE_DIR / "monthly"
QUARTERLY_DIR = BASE_DIR / "quarterly"
YEARLY_DIR = BASE_DIR / "yearly"
CONFIG_DIR = BASE_DIR / "config"
SCRIPTS_DIR = BASE_DIR / "scripts"
LOCK_FILE = BASE_DIR / ".sync" / "write.lock"

# ---------- 分类配置 ----------
def load_categories() -> dict:
    """从配置文件加载分类定义，返回 {id: label} 映射"""
    cat_path = CONFIG_DIR / "categories.json"
    if not cat_path.exists():
        print(f"[ERROR] 分类配置文件不存在: {cat_path}")
        sys.exit(1)
    with open(cat_path, "r", encoding="utf-8") as f:
        cats = json.load(f)
    return {c["id"]: c["label"] for c in cats}


def load_api_key() -> str:
    """读取 DeepSeek API Key"""
    key_path = CONFIG_DIR / "api_key.txt"
    if not key_path.exists():
        print(f"[ERROR] API Key 文件不存在: {key_path}")
        sys.exit(1)
    with open(key_path, "r", encoding="utf-8") as f:
        return f.read().strip()


# ---------- Markdown 解析 ----------
def parse_raw_file(filepath: Path) -> list[dict]:
    """
    解析原始记录 MD 文件。
    格式: ## HH:MM\n\ncontent
    返回: [{"time": "09:15", "content": "原始口语文字..."}, ...]
    """
    if not filepath.exists():
        return []

    entries = []
    with open(filepath, "r", encoding="utf-8") as f:
        text = f.read()

    # 按 ## HH:MM 分割
    pattern = re.compile(r'^## (\d{2}:\d{2})\s*$', re.MULTILINE)
    matches = list(pattern.finditer(text))

    for i, match in enumerate(matches):
        time = match.group(1)
        content_start = match.end()
        content_end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        content = text[content_start:content_end].strip()
        if content:
            entries.append({"time": time, "content": content})

    return entries


def parse_daily_file(filepath: Path) -> list[dict]:
    """
    解析 AI 日报 MD 文件，提取分类和内容。
    返回: [{"category": "想法/灵感", "title": "...", "content": "..."}, ...]
    """
    if not filepath.exists():
        return []

    entries = []
    with open(filepath, "r", encoding="utf-8") as f:
        text = f.read()

    # 匹配 ## 分类标签（如 "## 想法/灵感"），然后跟着 **标题** 和内容
    pattern = re.compile(
        r'^## (.+?)\s*\n+\*\*(.+?)\*\*\s*\n+(.*?)(?=\n## |\n---|\Z)',
        re.MULTILINE | re.DOTALL
    )
    for match in pattern.finditer(text):
        entries.append({
            "category": match.group(1).strip(),
            "title": match.group(2).strip(),
            "content": match.group(3).strip(),
        })

    return entries


# ---------- LLM 调用 ----------
def classify_and_refine(entries: list[dict], api_key: str) -> list[dict]:
    """
    调用 DeepSeek API 批量分类+润色。
    entries: [{"time": "09:15", "content": "口语化原文..."}, ...]
    returns: [{"time": "09:15", "category_id": "ideas", "category_label": "想法/灵感",
                "title": "...", "content": "精炼文字..."}, ...]
    """
    if not entries:
        return []

    try:
        from openai import OpenAI
    except ImportError:
        print("[ERROR] 需要安装 openai 包: pip install openai")
        sys.exit(1)

    client = OpenAI(
        api_key=api_key,
        base_url="https://api.deepseek.com",
    )

    categories = load_categories()
    categories_text = "\n".join(f"- {cid}: {label}" for cid, label in categories.items())

    entries_text = "\n\n---\n\n".join(
        f"[条目{i}] 时间:{e['time']}\n{e['content']}"
        for i, e in enumerate(entries)
    )

    system_prompt = """你是一个个人知识管理助手。对每条原始记录：
1. 归类到最匹配的分类（只能选一个）
2. 将口语化文字精炼为简洁书面语（保留核心信息，去除冗余和语气词）
3. 为每个条目提取一个简短的标题（10字以内）

注意：
- 保留原文中的所有事实信息
- 去除口语化冗余（如"然后"、"那个"、"就是"等填充词）
- 对于引用类文字，保留原话的完整性
- 适当补充背景知识（如提到的人名全称、专业术语解释）
- 如果内容无法判断具体时间地点，保持模糊处理"""

    user_prompt = f"""## 可用分类
{categories_text}

## 原始记录
{entries_text}

## 输出格式（严格 JSON，不要输出其他内容）
```json
[
  {{
    "time": "原始时间",
    "category_id": "分类ID",
    "category_label": "分类标签",
    "title": "条目标题",
    "content": "精炼后的文字"
  }}
]
```"""

    try:
        response = client.chat.completions.create(
            model="deepseek-chat",
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            max_tokens=4000,
            temperature=0.3,
        )
        raw = response.choices[0].message.content.strip()

        # 提取 JSON（可能包裹在 ```json ... ``` 中）
        json_match = re.search(r'```(?:json)?\s*(.*?)\s*```', raw, re.DOTALL)
        if json_match:
            raw = json_match.group(1)

        results = json.loads(raw)
        print(f"[LLM] 成功分类+润色 {len(results)} 条记录")
        return results

    except json.JSONDecodeError as e:
        print(f"[ERROR] LLM 返回的 JSON 解析失败: {e}")
        print(f"原始返回: {raw[:500]}...")
        # 降级：返回原始内容
        return fallback_classify(entries)
    except Exception as e:
        print(f"[ERROR] LLM 调用失败: {e}")
        return fallback_classify(entries)


def fallback_classify(entries: list[dict]) -> list[dict]:
    """LLM 不可用时的降级处理：所有条目归为「随想杂记」"""
    return [
        {
            "time": e["time"],
            "category_id": "random",
            "category_label": "随想杂记",
            "title": e["content"][:20],
            "content": e["content"],
        }
        for e in entries
    ]


def extract_mood_keywords(entries: list[dict]) -> str:
    """简单关键词情绪检测"""
    mood_map = {
        "开心": "😊", "快乐": "😊", "高兴": "😊", "太好了": "😊", "顺利": "😊",
        "焦虑": "😰", "担心": "😰", "压力": "😰", "紧张": "😰", "烦躁": "😰",
        "难过": "😢", "伤心": "😢", "失望": "😢",
        "生气": "😡", "愤怒": "😡", "不爽": "😡",
        "平静": "😐", "一般": "😐", "还行": "😐",
    }
    for e in entries:
        for kw, emoji in mood_map.items():
            if kw in e.get("content", ""):
                return emoji
    return "😐"


# ---------- 文件工具 ----------
def ensure_dir(path: Path):
    """确保目录存在"""
    path.mkdir(parents=True, exist_ok=True)


def is_lock_present() -> bool:
    """检查是否有 APP 正在写入"""
    return LOCK_FILE.exists()


def write_md(path: Path, content: str):
    """写入 Markdown 文件"""
    ensure_dir(path.parent)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"[OK] 已生成: {path}")


def get_date_path(base: Path, year: int, month: int, day: int = None) -> Path:
    """构建日期路径"""
    p = base / str(year) / f"{month:02d}"
    if day:
        p = p / f"{year}-{month:02d}-{day:02d}.md"
    return p


def weekday_cn(d: date) -> str:
    """返回中文星期"""
    return ["周一", "周二", "周三", "周四", "周五", "周六", "周日"][d.weekday()]
