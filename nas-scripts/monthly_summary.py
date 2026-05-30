#!/usr/bin/env python3
"""
monthly_summary.py — 月报生成脚本
执行频率: 每月 1 号 03:37 (DSM Cron: 37 3 1 * *)
功能: 汇总上月日报 → 生成月总结 MD
"""

import sys
from datetime import date, timedelta
from collections import defaultdict, Counter
from common import *


def generate_monthly_summary(year: int, month: int):
    """为指定月份生成月报"""
    print(f"\n{'='*60}")
    print(f"[月报生成] {year}-{month:02d}")
    print(f"{'='*60}")

    # 收集本月每天的数据
    days_in_month = {
        1:31,2:29 if year%4==0 else 28,3:31,4:30,5:31,6:30,
        7:31,8:31,9:30,10:31,11:30,12:31
    }[month]

    daily_summary = {}
    total_entries = 0
    all_categories = Counter()
    gratitude_items = []
    todos_unfinished = []
    moods_by_week = defaultdict(list)

    for day in range(1, days_in_month + 1):
        d = date(year, month, day)
        daily_path = get_date_path(DAILY_DIR, d.year, d.month, d.day)

        if daily_path.exists():
            entries = parse_daily_file(daily_path)
            if entries:
                daily_summary[d] = entries
                total_entries += len(entries)

                for e in entries:
                    all_categories[e["category"]] += 1
                    if e["category"] == "感恩与美好":
                        gratitude_items.append((d, e["title"], e["content"]))
                    if "未完成" in e.get("content", "") or "迁移" in e.get("content", ""):
                        todos_unfinished.append(e["title"])

                # 按周分组情绪
                week_num = d.isocalendar()[1]
                raw_path = get_date_path(RAW_DIR, d.year, d.month, d.day)
                if raw_path.exists():
                    moods_by_week[week_num].append(extract_mood_keywords(parse_raw_file(raw_path)))

    record_days = len(daily_summary)

    if record_days == 0:
        print(f"[跳过] {year}-{month:02d} 无记录")
        return False

    # 活跃度排行
    top_day = max(daily_summary.items(), key=lambda x: len(x[1])) if daily_summary else None

    # 分类分布 ASCII 图
    max_count = all_categories.most_common(1)[0][1] if all_categories else 1
    dist_chart = ""
    for cat, cnt in all_categories.most_common():
        bar_len = int(cnt / max_count * 30)
        bar = "█" * bar_len
        dist_chart += f"| {cat} | {bar} | {cnt} |\n"

    # 情绪周曲线
    mood_lines = ""
    for wn in sorted(moods_by_week.keys()):
        moods = moods_by_week[wn]
        mood_str = "".join(moods)
        avg = "积极" if moods.count("😊") > len(moods)/2 else ("消极" if moods.count("😰")+moods.count("😢") > len(moods)/2 else "中性")
        mood_lines += f"| 第{wn}周 | {mood_str} | {avg} |\n"

    # 感恩清单
    gratitude_section = ""
    if gratitude_items:
        for d, title, content in gratitude_items[:10]:  # 最多 10 条
            gratitude_section += f"- **{d.isoformat()}** — {title}: {content[:50]}...\n"
    else:
        gratitude_section = "本月暂无感恩记录。\n"

    # LLM 月综述
    api_key = load_api_key()
    ai_summary = generate_monthly_observation(year, month, record_days, days_in_month,
                                               total_entries, all_categories, api_key)

    md = f"""# 月总结 | {year}年{month}月

> 自动生成于 {date.today().isoformat()} 03:37

## 月度统计

| 指标 | 数值 |
|------|------|
| 记录天数 | {record_days} / {days_in_month} |
| 总条目数 | {total_entries} 条 |
| 日均条目 | {total_entries / max(record_days, 1):.1f} 条 |
| 最活跃日 | {top_day[0].isoformat() if top_day else 'N/A'}（{len(top_day[1]) if top_day else 0} 条）|
| 最常用分类 | {all_categories.most_common(1)[0][0] if all_categories else 'N/A'} |

## 分类分布

| 分类 | 分布 | 数量 |
|------|------|------|
{dist_chart}

## 情绪月度曲线

| 周 | 情绪 | 趋势 |
|------|------|------|
{mood_lines}

## 本月感恩清单

{gratitude_section}

## 待办追踪

{chr(10).join('- [ ] ' + t for t in todos_unfinished) if todos_unfinished else '本月无未完成待办。'}

## AI 月度综述

{ai_summary}

---

*由思维札记自动生成*
"""

    output_path = MONTHLY_DIR / str(year) / f"{year}-{month:02d}.md"
    write_md(output_path, md)
    return True


def generate_monthly_observation(year, month, record_days, total_days,
                                  total_entries, categories, api_key):
    try:
        from openai import OpenAI
        client = OpenAI(api_key=api_key, base_url="https://api.deepseek.com")

        top_cats = categories.most_common(5)
        prompt = f"""基于以下月度数据，给出 3-4 句话的月度综述（中文，温暖有洞察力）：

- {year}年{month}月
- 记录 {record_days}/{total_days} 天
- {total_entries} 条记录
- 最活跃分类: {', '.join(f'{c}({n})' for c,n in top_cats)}

请评价记录习惯、关注点变化趋势、给出下月建议。"""

        response = client.chat.completions.create(
            model="deepseek-chat",
            messages=[{"role": "user", "content": prompt}],
            max_tokens=400,
            temperature=0.7,
        )
        return response.choices[0].message.content.strip()
    except Exception as e:
        print(f"[WARN] AI 月综述失败: {e}")
        return f"{year}年{month}月共记录 {total_entries} 条思考，覆盖 {record_days} 天。"


def main():
    today = date.today()
    # 上月
    first_of_this_month = today.replace(day=1)
    last_month = first_of_this_month - timedelta(days=1)

    year = last_month.year
    month = last_month.month

    if len(sys.argv) > 2:
        try:
            year = int(sys.argv[1])
            month = int(sys.argv[2])
        except ValueError:
            print(f"用法: python3 monthly_summary.py [YEAR MONTH]")
            sys.exit(1)

    generate_monthly_summary(year, month)


if __name__ == "__main__":
    main()
