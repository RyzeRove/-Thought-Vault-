#!/usr/bin/env python3
"""
weekly_summary.py — 周报生成脚本
执行频率: 每周一 02:23 (DSM Cron: 23 2 * * 1)
功能: 汇总上周 7 天的日报 → 生成周总结 MD
"""

import sys
from datetime import date, timedelta
from collections import defaultdict, Counter
from common import *


def generate_weekly_summary(year: int, week: int):
    """为指定 ISO 周生成周报"""
    # ISO 周的第一天是周一
    # 计算该周的周一日期
    jan4 = date(year, 1, 4)
    first_monday = jan4 - timedelta(days=jan4.weekday())
    monday = first_monday + timedelta(weeks=week - 1)
    sunday = monday + timedelta(days=6)

    print(f"\n{'='*60}")
    print(f"[周报生成] {year}-W{week:02d} ({monday.isoformat()} ~ {sunday.isoformat()})")
    print(f"{'='*60}")

    # 收集本周每天的数据
    daily_data = {}
    total_entries = 0
    all_categories = Counter()
    all_moods = []

    for i in range(7):
        d = monday + timedelta(days=i)
        raw_path = get_date_path(RAW_DIR, d.year, d.month, d.day)
        daily_path = get_date_path(DAILY_DIR, d.year, d.month, d.day)

        if raw_path.exists():
            entries = parse_raw_file(raw_path)
            daily_data[d] = {
                "raw_count": len(entries),
                "mood": extract_mood_keywords(entries),
            }
            total_entries += len(entries)
            all_moods.append((d, extract_mood_keywords(entries)))

        if daily_path.exists():
            refined = parse_daily_file(daily_path)
            for e in refined:
                all_categories[e["category"]] += 1

    if not daily_data:
        print(f"[跳过] 本周无记录")
        return False

    # 构建每日要点表
    daily_rows = ""
    for d in sorted(daily_data.keys()):
        dd = daily_data[d]
        daily_rows += f"| {weekday_cn(d)} {d.isoformat()} | {dd['mood']} | {dd['raw_count']} 条 |\n"

    # 情绪曲线
    mood_line = " → ".join(f"{wd}&nbsp;{m}" for wd, m in
                            [(weekday_cn(d), daily_data[d]["mood"]) for d in sorted(daily_data.keys())])

    # 分类分布
    category_dist = ""
    for cat, count in all_categories.most_common():
        bar = "█" * min(count, 30)
        category_dist += f"| {cat} | {bar} | {count} |\n"

    # 调用 LLM 生成本周观察（可选）
    api_key = load_api_key()
    ai_observation = generate_weekly_observation(daily_data, all_categories, mood_line, api_key)

    md = f"""# 周总结 | {year}年第{week}周（{monday.isoformat()} 至 {sunday.isoformat()}）

> 自动生成于 {date.today().isoformat()} 02:23

## 本周概览

| 指标 | 数值 |
|------|------|
| 记录天数 | {len(daily_data)} 天 / 7 天 |
| 总条目数 | {total_entries} 条 |
| 日均条目 | {total_entries / max(len(daily_data), 1):.1f} 条 |
| 最活跃分类 | {all_categories.most_common(1)[0][0] if all_categories else 'N/A'} |

## 每日概览

| 日期 | 情绪 | 条目数 |
|------|------|--------|
{daily_rows}

## 情绪波动

{mood_line}

## 分类分布

| 分类 | 分布 | 数量 |
|------|------|------|
{category_dist}

## AI 本周观察

{ai_observation}

---

*由思维札记自动生成*
"""

    output_path = WEEKLY_DIR / str(year) / f"{year}-W{week:02d}.md"
    write_md(output_path, md)
    return True


def generate_weekly_observation(daily_data: dict, all_categories: Counter, mood_line: str, api_key: str) -> str:
    """调用 LLM 生成本周观察（简单概括）"""
    if not daily_data:
        return "本周无足够数据生成观察。"

    days_with_data = len(daily_data)
    total_entries = sum(d["raw_count"] for d in daily_data.values())
    top_cats = all_categories.most_common(3)

    try:
        from openai import OpenAI
        client = OpenAI(api_key=api_key, base_url="https://api.deepseek.com")

        prompt = f"""基于以下本周数据，用 2-3 句话给出本周观察和建议（中文，简洁有力）：

- 记录天数: {days_with_data}/7
- 总条目数: {total_entries}
- 最活跃分类: {', '.join(f'{cat}({cnt})' for cat, cnt in top_cats)}
- 情绪曲线: {mood_line}

请用口语化的语气，像朋友聊天一样给出观察。"""

        response = client.chat.completions.create(
            model="deepseek-chat",
            messages=[{"role": "user", "content": prompt}],
            max_tokens=300,
            temperature=0.7,
        )
        return response.choices[0].message.content.strip()
    except Exception as e:
        print(f"[WARN] AI 周观察生成失败: {e}")
        return f"本周共记录 {total_entries} 条思考，活跃在 {len(daily_data)} 天。"


def main():
    today = date.today()
    # 获取上周的 ISO 周编号
    last_monday = today - timedelta(days=today.weekday() + 7)
    iso_year, iso_week, _ = last_monday.isocalendar()

    if len(sys.argv) > 2:
        try:
            iso_year = int(sys.argv[1])
            iso_week = int(sys.argv[2])
        except ValueError:
            print(f"用法: python3 weekly_summary.py [YEAR WEEK]")
            sys.exit(1)

    generate_weekly_summary(iso_year, iso_week)


if __name__ == "__main__":
    main()
