#!/usr/bin/env python3
"""
yearly_summary.py — 年报生成脚本
执行频率: 每年 1 月 1 号 05:53 (DSM Cron: 53 5 1 1 *)
功能: 汇总上一年度 → 生成年度总结 MD（结构化深度复盘）
"""
import sys
import re as re_mod
from datetime import date, timedelta
from collections import Counter, defaultdict
from common import *


def extract_review_section(md_content: str) -> str:
    """从季报/月报 MD 中提取复盘部分文字"""
    for header in ['## 季度复盘', '## 月度复盘', '## AI 季度综述', '## AI 月度综述']:
        m = re_mod.search(rf'{header}\s*\n+(.*?)(?=\n---|\Z)', md_content, re_mod.DOTALL)
        if m:
            text = m.group(1).strip()
            return text[:400] + ("..." if len(text) > 400 else "")
    return "(暂无复盘内容)"


def generate_yearly_summary(year: int):
    """为指定年份生成年报"""
    print(f"\n{'='*60}")
    print(f"[年报生成] {year}年")
    print(f"{'='*60}")

    total_entries = 0
    record_days = 0
    all_categories = Counter()
    monthly_data = {}         # month -> {"days": N, "entries": N}
    quarterly_reviews = {}    # quarter -> extracted review text
    category_by_quarter = defaultdict(Counter)  # quarter -> Counter
    gratitude_all = []

    for month in range(1, 13):
        month_entries = 0
        month_days = 0
        quarter = (month - 1) // 3 + 1

        daily_dir = DAILY_DIR / str(year) / f"{month:02d}"
        if daily_dir.exists():
            for f_path in sorted(daily_dir.glob("*.md")):
                entries = parse_daily_file(f_path)
                if entries:
                    month_days += 1
                    month_entries += len(entries)
                    for e in entries:
                        all_categories[e["category"]] += 1
                        category_by_quarter[quarter][e["category"]] += 1
                        if e["category"] == "感恩与美好":
                            gratitude_all.append((f_path.stem, e["title"], e["content"]))

        monthly_data[month] = {"days": month_days, "entries": month_entries}
        record_days += month_days
        total_entries += month_entries

    if total_entries == 0:
        print(f"[跳过] {year}年无记录")
        return False

    # 读取各季度复盘摘要
    for q in range(1, 5):
        q_path = QUARTERLY_DIR / str(year) / f"{year}-Q{q}.md"
        if q_path.exists():
            with open(q_path, "r", encoding="utf-8") as f:
                quarterly_reviews[q] = extract_review_section(f.read())
        else:
            quarterly_reviews[q] = "(季报尚未生成)"

    # 月度热力图
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

    # 季度分类趋势
    quarter_trend = ""
    for q in sorted(category_by_quarter.keys()):
        cats = category_by_quarter[q]
        top = cats.most_common(3)
        quarter_trend += f"| Q{q} | " + "、".join(f"{c}({n})" for c, n in top) + f" | {sum(cats.values())} |\n"

    # LLM 年度复盘
    api_key = load_api_key()
    ai_narrative = generate_yearly_review(year, total_entries, record_days,
                                           monthly_data, all_categories,
                                           gratitude_all, category_by_quarter,
                                           quarterly_reviews, api_key)

    md = f"""# 年总结 | {year}年

> 自动生成于 {date.today().isoformat()} 05:53

## 年度统计

| 指标 | 数值 |
|------|------|
| 总条目数 | {total_entries} 条 |
| 记录天数 | {record_days} / {365 if year % 4 != 0 else 366} 天 |
| 日均记录 | {total_entries / max(record_days, 1):.1f} 条 |
| 月均记录 | {total_entries / 12:.0f} 条 |
| 覆盖率 | {record_days / 365 * 100:.1f}% |

## 月度热力图

| 月份 | 活跃度 | 条目数 | 天数 |
|------|--------|--------|------|
{heatmap}

## 年度关键词

{chr(10).join(f'{i}. **#{kw}** ({all_categories[kw]} 条)' for i, kw in enumerate(top_keywords, 1))}

## 季度分类趋势

| 季度 | 主要关注 | 季度总条数 |
|------|----------|------------|
{quarter_trend}

## 年度感恩清单

{gratitude_section if gratitude_section else '今年未记录感恩。'}

## 年度复盘

{ai_narrative}

---

*由思维札记自动生成 · {year}年*
"""

    output_path = YEARLY_DIR / str(year) / f"{year}.md"
    write_md(output_path, md)
    return True


def generate_yearly_review(year, total_entries, record_days, monthly_data,
                             categories, gratitude_all, category_by_quarter,
                             quarterly_reviews, api_key):
    """按结构化框架生成年度深度复盘（2000-3000字）"""
    top_cats = categories.most_common(5)
    coverage = record_days / 365 * 100
    hot_months = sorted(monthly_data.items(), key=lambda x: x[1]["entries"], reverse=True)[:3]

    # 月度热力数据
    heatmap_text = ""
    for month in range(1, 13):
        m = monthly_data[month]
        heatmap_text += f"{month:02d}月: {m['entries']}条/{m['days']}天；"

    # 季度复盘摘要
    reviews_text = ""
    for q in sorted(quarterly_reviews.keys()):
        reviews_text += f"## Q{q} 季度复盘摘要\n\n{quarterly_reviews[q]}\n\n"

    # 季度分类趋势
    trend_text = ""
    for q in sorted(category_by_quarter.keys()):
        cats = category_by_quarter[q]
        top = cats.most_common(5)
        trend_text += f"Q{q}（{sum(cats.values())}条）：" + "、".join(f"{c}({n})" for c, n in top) + "\n"

    # 感恩
    gratitude_text = ""
    for d, title, content in gratitude_all[:20]:
        gratitude_text += f"- {d} — {title}\n"
    if not gratitude_text:
        gratitude_text = "今年暂无感恩记录"

    prompt = f"""# 数据概要

## 年度统计
{year}年，总条目数 {total_entries}，记录天数 {record_days}/365，覆盖率 {coverage:.1f}%

## 月度活跃度
{heatmap_text}

## 最活跃月份：{', '.join(f'{m}月({d["entries"]}条)' for m, d in hot_months)}

## 年度关键词
{', '.join(f'{c}({n})' for c, n in top_cats)}

## 季度分类趋势
{trend_text}

## 各季度 AI 复盘摘要
{reviews_text}

## 年度感恩清单
{gratitude_text}

---

基于以上数据，以"年度复盘"的形式，用第一人称"我"的口吻，撰写一份 {year}年的全年总结。

年报侧重点：全年规划达成、成长蜕变轨迹、长期沉淀积累、来年战略布局。年报不纠结细节，而是串联起 12 个月的线索，讲清楚这一年的故事。

## 必写部分（工作向）

### 1. 核心业绩 & 任务完成
复盘全年的重要成就和关键目标达成情况。按季度回顾主线任务推进的里程碑。从数据中提取代表性的成功交付和产出。

### 2. 能力与履职表现
全年能力成长的叙事弧线。年初和年尾的你有什么不同？核心能力发生了什么变化？回顾各季度分类趋势中反映出的成长轨迹。

### 3. 亮点与收获
全年最值得记录的 3-5 个高光时刻。这些亮点如何串联成一条成长线索？从代表性事件中提炼可复用的成功经验。

### 4. 问题、不足与原因
全年反复出现的模式性问题。是什么在持续阻碍你？根因分析要有深度，不要停留在表面。诚实面对自己。

### 5. 改进措施 & 下一阶段计划
面向新的一年。基于全年复盘，明确来年的 2-3 个核心战略方向。包括具体的行动安排。

## 可选部分（生活向 — 有数据则写，无则跳过）

### 6. 作息与健康（全年回顾）
### 7. 个人成长与学习（年度积累）
### 8. 家庭与人际（年度变化）
### 9. 消费与收支（年度汇总）
### 10. 兴趣休闲 & 情绪状态（年度情绪旅程）

---

输出要求：
1. 总计约 2000-3000 字
2. 必须做跨季度、跨半年的对比和趋势分析
3. 要有一个整体叙事线（这一年的故事是什么）
4. 引用具体数据、月份和事件要准确
5. 标题用 ### 级别 Markdown
6. 直接输出总结内容，不要引导语"""

    result = call_deepseek(prompt, api_key, max_tokens=5000, temperature=0.7)
    if result:
        return result
    return f"{year}年，共 {record_days} 天保持了记录习惯，写下了 {total_entries} 条思考。最关注的领域是{top_cats[0][0] if top_cats else '综合'}。这是认真生活的一年。期待来年继续相伴。"


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
