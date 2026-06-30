#!/usr/bin/env python3
"""
quarterly_summary.py — 季报生成脚本
执行频率: 每季首月 1 号 04:41 (DSM Cron: 41 4 1 1,4,7,10 *)
功能: 汇总上季度月报 → 生成季度总结 MD（结构化深度复盘）
"""
import sys
import re as re_mod
from datetime import date, timedelta
from collections import Counter, defaultdict
from common import *


def get_quarter_months(q: int) -> list:
    """季度 → 月份列表"""
    return {
        1: [1, 2, 3], 2: [4, 5, 6], 3: [7, 8, 9], 4: [10, 11, 12]
    }[q]


def extract_review_section(md_content: str) -> str:
    """从月报 MD 中提取「月度复盘」部分的文字"""
    m = re_mod.search(r'## 月度复盘\s*\n+(.*?)(?=\n---|\Z)', md_content, re_mod.DOTALL)
    if m:
        text = m.group(1).strip()
        # 截取前 500 字符作为摘要
        return text[:500] + ("..." if len(text) > 500 else "")
    # 兼容旧格式
    m = re_mod.search(r'## AI 月度综述\s*\n+(.*?)(?=\n---|\Z)', md_content, re_mod.DOTALL)
    if m:
        return m.group(1).strip()[:500]
    return "(本月月报暂无 AI 复盘)"


def generate_quarterly_summary(year: int, quarter: int):
    """为指定季度生成季报"""
    months = get_quarter_months(quarter)

    print(f"\n{'='*60}")
    print(f"[季报生成] {year}-Q{quarter} (月份: {months})")
    print(f"{'='*60}")

    total_entries = 0
    record_days = 0
    all_categories = Counter()
    monthly_stats = {}             # month -> {"days": N, "entries": N}
    quarterly_entries = {}         # month -> list of daily entry dicts
    monthly_reviews = {}           # month -> extracted AI review text

    for m in months:
        monthly_entries = []
        month_days = 0
        month_entry_count = 0
        month_categories = Counter()

        daily_dir = DAILY_DIR / str(year) / f"{m:02d}"
        if daily_dir.exists():
            for f_path in sorted(daily_dir.glob("*.md")):
                entries = parse_daily_file(f_path)
                if entries:
                    month_days += 1
                    month_entry_count += len(entries)
                    monthly_entries.extend(entries)
                    for e in entries:
                        month_categories[e["category"]] += 1
                        all_categories[e["category"]] += 1

        monthly_stats[m] = {"days": month_days, "entries": month_entry_count}
        quarterly_entries[m] = monthly_entries
        record_days += month_days
        total_entries += month_entry_count

        # 读取月报复盘文字
        monthly_path = MONTHLY_DIR / str(year) / f"{year}-{m:02d}.md"
        if monthly_path.exists():
            with open(monthly_path, "r", encoding="utf-8") as f:
                monthly_reviews[m] = extract_review_section(f.read())
        else:
            monthly_reviews[m] = "(月报尚未生成)"

    if not monthly_stats or total_entries == 0:
        print(f"[跳过] {year}-Q{quarter} 无记录")
        return False

    # 月度对比
    month_compare = ""
    for m in months:
        s = monthly_stats[m]
        month_compare += f"| {m}月 | {s['days']} 天 | {s['entries']} 条 |\n"

    # 分类季度分布
    cat_dist = ""
    for cat, cnt in all_categories.most_common(10):
        cat_dist += f"| {cat} | {cnt} |\n"

    # LLM 季复盘
    api_key = load_api_key()
    ai_summary = generate_quarterly_review(year, quarter, monthly_stats,
                                            total_entries, all_categories,
                                            quarterly_entries, monthly_reviews, api_key)

    md = f"""# 季总结 | {year}年第{quarter}季度

> 自动生成于 {date.today().isoformat()} 04:41

## 季度统计

| 指标 | 数值 |
|------|------|
| 总条目数 | {total_entries} 条 |
| 记录天数 | {record_days} 天 |
| 月均条目 | {total_entries / max(len([m for m in months if monthly_stats[m]['entries'] > 0]), 1):.0f} 条 |
| 最活跃分类 | {all_categories.most_common(1)[0][0] if all_categories else 'N/A'} |

## 月度对比

| 月份 | 记录天数 | 条目数 |
|------|----------|--------|
{month_compare}

## 分类分布（季度汇总）

{cat_dist}

## 季度复盘

{ai_summary}

---

*由思维札记自动生成*
"""

    output_path = QUARTERLY_DIR / str(year) / f"{year}-Q{quarter}.md"
    write_md(output_path, md)
    return True


def generate_quarterly_review(year, quarter, monthly_stats, total_entries,
                                categories, quarterly_entries, monthly_reviews, api_key):
    """按结构化框架生成季度深度复盘（1200-1800字）"""
    top_cats = categories.most_common(5)
    categories_list = "、".join(f"{c}({n})" for c, n in top_cats)

    # 组装各月内容采样（每个月取前 15 条代表性条目）
    monthly_content_text = ""
    for m in sorted(quarterly_entries.keys()):
        entries = quarterly_entries[m]
        month_stats = monthly_stats.get(m, {})
        monthly_content_text += f"## {m}月（{month_stats.get('days', 0)} 天, {month_stats.get('entries', 0)} 条）\n\n"
        if entries:
            # 按分类排序，取每个分类的前几条作为代表
            by_cat = defaultdict(list)
            for e in entries:
                by_cat[e["category"]].append(e)
            for cat, cat_entries in sorted(by_cat.items()):
                for e in cat_entries[:3]:  # 每个分类最多取 3 条
                    monthly_content_text += f"- [{e['category']}] {e['title']}：{e['content'][:120]}\n"
            monthly_content_text += "\n"
        else:
            monthly_content_text += "本月无日报记录。\n\n"

    # 各月 AI 复盘摘要
    reviews_text = ""
    for m in sorted(monthly_reviews.keys()):
        reviews_text += f"## {m}月月报复盘摘要\n\n{monthly_reviews[m]}\n\n"

    # 跨月趋势数据
    trend_text = ""
    prev_entries = 0
    for m in sorted(monthly_stats.keys()):
        s = monthly_stats[m]
        trend_text += f"- {m}月：{s['days']} 天记录，{s['entries']} 条"
        if prev_entries > 0:
            change = s['entries'] - prev_entries
            trend_text += f"（{'增加' if change > 0 else '减少' if change < 0 else '持平'}{abs(change)} 条）"
        trend_text += "\n"
        prev_entries = s['entries']

    prompt = f"""# 数据概要

## 周期
{year}年第{quarter}季度

## 总览
- 总条目数：{total_entries}
- 分类分布：{categories_list}

## 月度趋势
{trend_text}

## 各月内容代表性条目

{monthly_content_text}

## 各月 AI 复盘摘要

{reviews_text}

---

基于以上数据，以"季度复盘"的形式，用第一人称"我"的口吻，撰写一份 {year}年第{quarter}季度的阶段总结。

季报侧重点：阶段目标达成、成果复盘、阶段性策略调整。相比月报侧重于"事务落地"，季报更关注"方向是否正确"和"哪些模式在重复出现"。

## 必写部分（工作向）

### 1. 核心业绩 & 任务完成
复盘本季度重要目标的达成情况。对比各月的完成度变化趋势。关注季度初设定的目标是否完成、重点项目是否推进。

### 2. 能力与履职表现
本季度的能力成长曲线。是否在某些领域有明显的提升？沟通、协作、独立解决问题的表现如何？

### 3. 亮点与收获
本季度最值得记录的 2-3 个突破或创新。这些亮点是否可复用？是否揭示了某种有效的工作模式？

### 4. 问题、不足与原因
审视本季度反复出现的问题，特别是跨月持续存在的瓶颈。从各月总结中提取共性问题的根因。

### 5. 改进措施 & 下一阶段计划
针对季度性问题的系统性改进方案。明确下季度的 1-3 个阶段性目标。

## 可选部分（生活向 — 有数据则写，无则跳过）

### 6. 作息与健康（季度趋势）
### 7. 个人成长与学习（季度积累）
### 8. 家庭与人际（季度变化）
### 9. 消费与收支（季度汇总）
### 10. 兴趣休闲 & 情绪状态（季度情绪曲线）

---

输出要求：
1. 总计约 1200-1800 字
2. 必须做跨月对比，指出趋势和变化
3. 引用具体月份、事件和数字
4. 标题用 ### 级别 Markdown
5. 直接输出总结内容，不要引导语"""

    result = call_deepseek(prompt, api_key, max_tokens=3500, temperature=0.7)
    if result:
        return result
    return f"{year}年第{quarter}季度共记录 {total_entries} 条思考。最活跃的分类是{top_cats[0][0] if top_cats else '综合'}。期待下季度继续保持记录习惯。"


def main():
    today = date.today()
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
