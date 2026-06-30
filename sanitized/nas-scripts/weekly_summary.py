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
    jan4 = date(year, 1, 4)
    first_monday = jan4 - timedelta(days=jan4.weekday())
    monday = first_monday + timedelta(weeks=week - 1)
    sunday = monday + timedelta(days=6)

    print(f"\n{'='*60}")
    print(f"[周报生成] {year}-W{week:02d} ({monday.isoformat()} ~ {sunday.isoformat()})")
    print(f"{'='*60}")

    # 收集本周每天的数据
    daily_data = {}
    daily_refined_entries = {}  # date -> [entry dicts from parse_daily_file()]
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
            daily_refined_entries[d] = refined
            for e in refined:
                all_categories[e["category"]] += 1
        else:
            daily_refined_entries[d] = []

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

    # 调用 LLM 生成本周点评
    api_key = load_api_key()
    ai_observation = weekly_insight(daily_refined_entries, daily_data, all_categories, mood_line, monday, sunday, api_key)

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

## 本周点评

{ai_observation}

---

*由思维札记自动生成*
"""

    output_path = WEEKLY_DIR / str(year) / f"{year}-W{week:02d}.md"
    write_md(output_path, md)
    return True


def weekly_insight(daily_refined_entries: dict, daily_data: dict,
                   all_categories: Counter, mood_line: str,
                   monday: date, sunday: date, api_key: str) -> str:
    """
    调用 LLM 生成本周点评（参考 daily_insight 的朋友语气风格）。
    300-400 字，有洞察力但不教条。
    """
    total = sum(d["raw_count"] for d in daily_data.values())
    days_with_data = len(daily_data)
    top_cats = all_categories.most_common(5)
    categories_list = "、".join(f"{c}({n})" for c, n in top_cats)

    # 组装每日内容摘要
    daily_summary_text = ""
    for d in sorted(daily_refined_entries.keys()):
        entries = daily_refined_entries.get(d, [])
        if not entries:
            if d in daily_data:
                daily_summary_text += f"**{weekday_cn(d)} {d.isoformat()}**：有 {daily_data[d]['raw_count']} 条原始记录，无日报\n\n"
            continue
        daily_summary_text += f"**{weekday_cn(d)} {d.isoformat()}**（{len(entries)} 条）:\n"
        for e in entries:
            daily_summary_text += f"- [{e['category']}] {e['title']}：{e['content'][:100]}\n"
        daily_summary_text += "\n"

    prompt = f"""以下是一人过去一周（{monday.isoformat()} 至 {sunday.isoformat()}）的思考记录整理稿。

## 数据概要
- 记录天数：{days_with_data}/7 天
- 总条目数：{total} 条
- 分类分布：{categories_list}
- 情绪曲线：{mood_line}

## 每日内容

{daily_summary_text}

请以一位了解他的朋友口吻，写一段约 300-400 字的本周点评。要求：
1. 先概括本周整体状态（活跃度变化、关注焦点转移、情绪走势）
2. 挑 2-3 个值得留意的主题或细节做温和延伸（可跨天对比、关联不同领域）
3. 对本周记录习惯做简短评价（是否稳定、遗漏的日子是否值得注意）
4. 语气理性温暖，有洞察力但不说教，不空洞
5. 结尾给一句简短鼓励或下周小提醒

只输出点评文字，不要标题、不要标签。"""

    result = call_deepseek(prompt, api_key, max_tokens=800, temperature=0.7)
    if result:
        return result
    # 降级
    return f"本周共记录 {total} 条思考，活跃在 {days_with_data} 天。关注最多的领域是{top_cats[0][0] if top_cats else '综合'}。期待下周继续保持记录习惯。"


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
