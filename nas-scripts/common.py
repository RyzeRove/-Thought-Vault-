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
BASE_DIR = Path(os.environ.get("THOUGHTS_BASE_DIR", "<your-nas-thoughts-dir>"))
RAW_DIR = BASE_DIR / "raw"
DAILY_DIR = BASE_DIR / "daily"
WEEKLY_DIR = BASE_DIR / "weekly"
MONTHLY_DIR = BASE_DIR / "monthly"
QUARTERLY_DIR = BASE_DIR / "quarterly"
YEARLY_DIR = BASE_DIR / "yearly"
CONFIG_DIR = BASE_DIR / "config"
SCRIPTS_DIR = BASE_DIR / "scripts"
TODOS_DIR = BASE_DIR / "todos"
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

    with open(filepath, "r", encoding="utf-8") as f:
        text = f.read()

    # 加载分类标签集合，用于区分"分类区块"和"meta 区块"（如 ## 统计、## 今日关键词 等）
    cats = load_categories()
    known_labels = set(cats.values())

    entries = []
    # 按 ## 标题分割区块，不使用 DOTALL，逐区块解析
    blocks = re.split(r'\n(?=## )', text)
    for block in blocks:
        # 提取区块标题
        header_match = re.match(r'^## (.+?)\s*$', block, re.MULTILINE)
        if not header_match:
            continue
        category = header_match.group(1).strip()
        # 跳过非分类的 meta 区块
        if category not in known_labels:
            continue

        # 提取该区块下的 **标题** + 内容对
        body = block[header_match.end():]
        # 按 **标题** 分割子条目
        parts = re.split(r'\n\*\*(.+?)\*\*\s*\n', body)
        # parts[0] 是第一个 **标题** 之前的空白/杂项，跳过
        for i in range(1, len(parts), 2):
            title = parts[i].strip()
            content = parts[i + 1].strip() if i + 1 < len(parts) else ""
            if title or content:
                entries.append({
                    "category": category,
                    "title": title,
                    "content": content,
                })

    return entries


# ---------- LLM 调用 ----------
def call_deepseek(prompt: str, api_key: str, *,
                  max_tokens: int = 1000,
                  temperature: float = 0.7,
                  system_prompt: str | None = None) -> str:
    """统一的 DeepSeek API 调用封装。返回响应文本，失败返回空字符串。"""
    try:
        from openai import OpenAI
    except ImportError:
        print("[ERROR] 需要安装 openai 包")
        return ""

    client = OpenAI(api_key=api_key, base_url="https://api.deepseek.com")
    messages = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})
    messages.append({"role": "user", "content": prompt})

    try:
        response = client.chat.completions.create(
            model="deepseek-chat",
            messages=messages,
            max_tokens=max_tokens,
            temperature=temperature,
        )
        return response.choices[0].message.content.strip()
    except Exception as e:
        print(f"[ERROR] DeepSeek API 调用失败: {e}")
        return ""


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


def daily_insight(refined_entries: list[dict], api_key: str) -> str:
    """
    基于分类润色后的条目生成一篇 200 字左右的每日点评。
    风格：朋友般温和的观察，有洞察力但不说教。
    """
    if not refined_entries:
        return ""

    try:
        from openai import OpenAI
    except ImportError:
        return ""

    client = OpenAI(api_key=api_key, base_url="https://api.deepseek.com")

    # 组装当日概况
    entries_text = "\n".join(
        f"- [{e['category_label']}] {e['title']}: {e['content']}"
        for e in refined_entries
    )
    categories = list(dict.fromkeys(e["category_label"] for e in refined_entries))

    prompt = f"""以下是一人今天的思考记录整理稿，共 {len(refined_entries)} 条，涉及 {len(categories)} 个领域（{", ".join(categories)}）。

{entries_text}

请以一位了解他的朋友口吻，写一段约 200 字的今日点评。要求：
1. 先概括今天的整体状态（活跃度、关注焦点、情绪基调）
2. 挑 1-2 个最值得留意的点做温和的延伸（可以追问、可以关联、可以建议）
3. 语气理性温暖，不说教，不空洞
4. 结尾给一句简短鼓励或提醒

只输出点评文字，不要标题、不要标签。"""

    try:
        response = client.chat.completions.create(
            model="deepseek-chat",
            messages=[{"role": "user", "content": prompt}],
            max_tokens=500,
            temperature=0.7,
        )
        return response.choices[0].message.content.strip()
    except Exception as e:
        print(f"[WARN] 点评生成失败: {e}")
        return ""


def extract_todos(raw_entries: list[dict], api_key: str) -> list[dict]:
    """
    从原始记录中提取待办事项。
    返回: [{content, type: short|long, date: YYYY-MM-DD}, ...]
    """
    if not raw_entries:
        return []

    try:
        from openai import OpenAI
    except ImportError:
        return []

    client = OpenAI(api_key=api_key, base_url="https://api.deepseek.com")

    entries_text = "\n".join(
        f"时间:{e['time']} 内容:{e['content']}" for e in raw_entries
    )

    prompt = f"""从以下记录中提取出用户提到想要做、计划做、准备做的事情。
1. 区分长期计划（需较长时间，type=long）和近期待办（近期可完成，type=short）
2. 只提取明确表达意图的内容，不编造
3. 如用户说今天/明天/这周要做X，属于近期待办(short)
4. 如用户说想学/想做/长期目标是X，属于长期计划(long)
5. 无则返回空数组
6. 已完成的事情不要提取

记录：
{entries_text}

输出严格JSON，不要其他内容：
[{{"type":"short或long","content":"简述","date":"{raw_entries[0].get('date', date.today().isoformat()) if raw_entries else date.today().isoformat()}"}}]"""

    try:
        response = client.chat.completions.create(
            model="deepseek-chat",
            messages=[{"role": "user", "content": prompt}],
            max_tokens=500,
            temperature=0.2,
        )
        raw = response.choices[0].message.content.strip()
        m = re.search(r'```(?:json)?\s*(.*?)\s*```', raw, re.DOTALL)
        if m:
            raw = m.group(1)
        todos = json.loads(raw)
        print(f"[LLM] 提取到 {len(todos)} 条待办事项")
        return todos
    except Exception as e:
        print(f"[WARN] 待办提取失败: {e}")
        return []


def load_todos_file() -> dict:
    """加载待办文件，返回结构化数据"""
    todo_path = TODOS_DIR / "tasks.md"
    if not todo_path.exists():
        return {"pending_day": [], "pending_long": [], "completed": []}

    with open(todo_path, "r", encoding="utf-8") as f:
        content = f.read()

    result = {"pending_day": [], "pending_long": [], "completed": []}
    current = None
    for line in content.split("\n"):
        if line.startswith("## 近期待办"):
            current = "pending_day"
            continue
        elif line.startswith("## 长期计划"):
            current = "pending_long"
            continue
        elif line.startswith("## 已完成"):
            current = "completed"
            continue
        m = re.match(r"^- \[([ x])\] (.+?) \| (.+?)$", line)
        if m and current:
            result[current].append({
                "done": m.group(1) == "x",
                "content": m.group(2),
                "date": m.group(3),
            })
    return result


def save_todos_file(data: dict):
    """保存待办文件"""
    ensure_dir(TODOS_DIR)
    todo_path = TODOS_DIR / "tasks.md"
    lines = [
        "# 待办事项",
        f"\n> 最后更新：{date.today().isoformat()}",
        "",
        "## 近期待办",
    ]
    for t in data["pending_day"]:
        lines.append(f"- [{'x' if t.get('done') else ' '}] {t['content']} | {t['date']}")
    lines += ["", "## 长期计划"]
    for t in data["pending_long"]:
        lines.append(f"- [{'x' if t.get('done') else ' '}] {t['content']} | {t['date']}")
    lines += ["", "## 已完成"]
    for t in data["completed"]:
        lines.append(f"- [x] {t['content']} | {t['date']}")
    with open(todo_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"[OK] 待办文件已更新: {todo_path}")


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
