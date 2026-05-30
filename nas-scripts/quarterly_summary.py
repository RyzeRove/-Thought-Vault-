#!/usr/bin/env python3
"""
quarterly_summary.py — 季报生成脚本
执行频率: 每季首月 1 号 04:41 (DSM Cron: 41 4 1 1,4,7,10 *)
功能: 汇总上季度月报 → 生成季度总结 MD
"""

import sys
from datetime import date, timedelta
from collections import Counter
from common import *


def get_quarter_months(q: int) -> list:
    """季度 → 月份列表"""
    return {
        1: [1, 2, 3], 2: [4, 5, 6], 3: [7, 8, 9], 4: [10, 11, 12]
    }[q]


def generate_quarterly_summary(year: int, quarter: int):
    """为指定季度生成季报"""
    months = get_quarter_months(quarter)

    print(f"\n{'='*60}")
    print(f"[季报生成] {year}-Q{quarter} (月份: {months})")
    print(f"{'='*60}")

    total_entries = 0
    record_days = 0
    all_categories = Counter()
    monthly_stats = {}

    for m in months:
        monthly_path = MONTHLY_DIR / str(year) / f"{year}-{m:02d}.md"
        if monthly_path.exists():
            # 简单统计（解析月报获取数据）
            with open(monthly_path, "r", encoding="utf-8") as f:
                content = f.read()

            # 从月报提取统计信息
            import re
            days_match = re.search(r'记录天数\s*\|\s*(\d+)\s*/\s*\d+', content)
            entries_match = re.search(r'总条目数\s*\|\s*(\d+)', content)

            days = int(days_match.group(1)) if days_match else 0
            entries = int(entries_match.group(1)) if entries_match else 0

            monthly_stats[m] = {"days": days, "entries": entries}
            record_days += days
            total_entries += entries

            # 收集分类统计
            daily_dir = DAILY_DIR / str(year) / f"{m:02d}"
            if daily_dir.exists():
                for f_path in daily_dir.glob("*.md"):
                    refined = parse_daily_file(f_path)
                    for e in refined:
                        all_categories[e["category"]] += 1

    if not monthly_stats:
        print(f"[跳过] {year}-Q{quarter} 无记录")
        return False

    # 月度对比
    month_compare = ""
    for m in months:
        if m in monthly_stats:
            s = monthly_stats[m]
            month_compare += f"| {m}月 | {s['days']} 天 | {s['entries']} 条 |\n"

    # LLM 季综述
    api_key = load_api_key()
    ai_summary = generate_quarterly_observation(year, quarter, monthly_stats,
                                                  total_entries, all_categories, api_key)

    md = f"""# 季总结 | {year}年第{quarter}季度

> 自动生成于 {date.today().isoformat()} 04:41

## 季度统计

| 指标 | 数值 |
|------|------|
| 总条目数 | {total_entries} 条 |
| 记录天数 | {record_days} 天 |
| 月均条目 | {total_entries / max(len(monthly_stats), 1):.0f} 条 |
| 最活跃分类 | {all_categories.most_common(1)[0][0] if all_categories else 'N/A'} |

## 月度对比

| 月份 | 记录天数 | 条目数 |
|------|----------|--------|
{month_compare}

## 分类分布（季度汇总）

{chr(10).join(f'| {cat} | {cnt} |' for cat, cnt in all_categories.most_common(10))}

## AI 季度综述

{ai_summary}

---

*由思维札记自动生成*
"""

    output_path = QUARTERLY_DIR / str(year) / f"{year}-Q{quarter}.md"
    write_md(output_path, md)
    return True


def generate_quarterly_observation(year, quarter, monthly_stats, total_entries,
                                     categories, api_key):
    try:
        from openai import OpenAI
        client = OpenAI(api_key=api_key, base_url="https://api.deepseek.com")

        top_cats = categories.most_common(5)
        prompt = f"""基于以下季度数据，给出 4-5 句话的季度综述（中文，有洞察力）：

- {year}年第{quarter}季度
- {total_entries} 条总记录
- 月度分布: {', '.join(f'{m}月({s['entries']}条)' for m,s in monthly_stats.items())}
- 主要关注: {', '.join(f'{c}({n})' for c,n in top_cats)}

请评价：本季度的核心主题、与前季度的可能变化趋势、下季度建议关注方向。"""

        response = client.chat.completions.create(
            model="deepseek-chat",
            messages=[{"role": "user", "content": prompt}],
            max_tokens=500,
            temperature=0.7,
        )
        return response.choices[0].message.content.strip()
    except Exception as e:
        print(f"[WARN] AI 季综述失败: {e}")
        return f"{year}年第{quarter}季度共记录 {total_entries} 条思考。"


def main():
    today = date.today()
    # 上季度
    current_q = (today.month - 1) // 3 + 1
    last_q = current_q - 1 if current_q > 1 else 4
    last_q_year = today.year if current_q > 1 else today.year - 1

    if len(sys.argv) > 2:
        try:
            last_q_year = int(sys.argv[1])
            last_q = int(sys.argv[2])
        except ValueError:
            print(f"用法: python3 quarterly_summary.py [YEAR QUARTER]")
            sys.exit(1)

    generate_quarterly_summary(last_q_year, last_q)


if __name__ == "__main__":
    main()
