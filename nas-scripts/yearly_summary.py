#!/usr/bin/env python3
"""
yearly_summary.py — 年报生成脚本
执行频率: 每年 1 月 1 号 05:53 (DSM Cron: 53 5 1 1 *)
功能: 汇总上一年度 → 生成年度总结 MD
"""

import sys
from datetime import date, timedelta
from collections import Counter, defaultdict
from common import *


def generate_yearly_summary(year: int):
    """为指定年份生成年报"""
    print(f"\n{'='*60}")
    print(f"[年报生成] {year}年")
    print(f"{'='*60}")

    total_entries = 0
    record_days = 0
    all_categories = Counter()
    monthly_data = {}
    gratitude_all = []

    for month in range(1, 13):
        monthly_path = MONTHLY_DIR / str(year) / f"{year}-{month:02d}.md"
        month_entries = 0
        month_days = 0

        # 遍历该月每天
        import re
        daily_dir = DAILY_DIR / str(year) / f"{month:02d}"
        if daily_dir.exists():
            for f_path in sorted(daily_dir.glob("*.md")):
                entries = parse_daily_file(f_path)
                if entries:
                    month_days += 1
                    month_entries += len(entries)
                    for e in entries:
                        all_categories[e["category"]] += 1
                        if e["category"] == "感恩与美好":
                            gratitude_all.append((f_path.stem, e["title"], e["content"]))

        monthly_data[month] = {"days": month_days, "entries": month_entries}
        record_days += month_days
        total_entries += month_entries

    if total_entries == 0:
        print(f"[跳过] {year}年无记录")
        return False

    # 月度热力图（简单 ASCII）
    max_entries = max(m["entries"] for m in monthly_data.values()) if monthly_data else 1
    heatmap = ""
    for month in range(1, 13):
        m = monthly_data[month]
        bar_len = max(1, int(m["entries"] / max(max_entries, 1) * 20))
        bar = "█" * bar_len
        heatmap += f"| {month:02d}月 | {bar} | {m['entries']} 条 | {m['days']} 天 |\n"

    # 年度关键词
    top_keywords = [cat for cat, _ in all_categories.most_common(5)]

    # 感恩清单（取前 12 条，每月一条）
    gratitude_section = ""
    for d, title, content in gratitude_all[:12]:
        gratitude_section += f"- **{d}** — {title}\n"

    # LLM 年度叙事
    api_key = load_api_key()
    ai_narrative = generate_yearly_narrative(year, total_entries, record_days,
                                              monthly_data, all_categories, api_key)

    md = f"""# 年总结 | {year}年

> 自动生成于 {date.today().isoformat()} 05:53

## 年度统计

| 指标 | 数值 |
|------|------|
| 总条目数 | {total_entries} 条 |
| 记录天数 | {record_days} / 365 天 |
| 日均记录 | {total_entries / max(record_days, 1):.1f} 条 |
| 月均记录 | {total_entries / 12:.0f} 条 |
| 覆盖率 | {record_days / 365 * 100:.1f}% |

## 月度热力图

| 月份 | 活跃度 | 条目数 | 天数 |
|------|--------|--------|------|
{heatmap}

## 年度关键词

{chr(10).join(f'{i}. **#{kw}** ({all_categories[kw]} 条)' for i, kw in enumerate(top_keywords, 1))}

## 年度感恩清单

{gratitude_section if gratitude_section else '今年未记录感恩。'}

## 分类演化

{chr(10).join(f'| {cat} | {cnt} |' for cat, cnt in all_categories.most_common(13))}

## AI 年度叙事

{ai_narrative}

---

*由思维札记自动生成 · {year}年*
"""

    output_path = YEARLY_DIR / f"{year}.md"
    write_md(output_path, md)
    return True


def generate_yearly_narrative(year, total_entries, record_days, monthly_data,
                                categories, api_key):
    try:
        from openai import OpenAI
        client = OpenAI(api_key=api_key, base_url="https://api.deepseek.com")

        top_cats = categories.most_common(5)
        hot_months = sorted(monthly_data.items(), key=lambda x: x[1]["entries"], reverse=True)[:3]

        prompt = f"""基于以下年度数据，为 {year}年写一篇温暖的年度叙事（5-6句话，中文，有故事感）：

- {total_entries} 条记录，{record_days} 天
- 最活跃月份: {', '.join(f'{m}月({d["entries"]}条)' for m,d in hot_months)}
- 主要关注: {', '.join(f'{c}({n})' for c,n in top_cats)}
- 记录覆盖率: {record_days/365*100:.1f}%

请像一个了解你的朋友，总结这一年的心路历程和成长。语气温暖、有力量。"""

        response = client.chat.completions.create(
            model="deepseek-chat",
            messages=[{"role": "user", "content": prompt}],
            max_tokens=600,
            temperature=0.8,
        )
        return response.choices[0].message.content.strip()
    except Exception as e:
        print(f"[WARN] AI 年叙事失败: {e}")
        return f"{year}年，共 {record_days} 天保持了记录习惯，写下了 {total_entries} 条思考。\n\n这是认真生活的一年。"


def main():
    last_year = date.today().year - 1

    if len(sys.argv) > 1:
        try:
            last_year = int(sys.argv[1])
        except ValueError:
            print(f"用法: python3 yearly_summary.py [YEAR]")
            sys.exit(1)

    generate_yearly_summary(last_year)


if __name__ == "__main__":
    main()
