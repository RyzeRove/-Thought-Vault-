#!/usr/bin/env python3
"""
monthly_summary.py — 月报生成脚本
执行频率: 每月 1 号 03:37 (DSM Cron: 37 3 1 * *)
功能: 汇总上月日报 → 生成月总结 MD（结构化深度复盘）
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

    days_in_month = {
        1:31,2:29 if year%4==0 else 28,3:31,4:30,5:31,6:30,
        7:31,8:31,9:30,10:31,11:30,12:31
    }[month]

    daily_summary = {}       # date -> [entries]
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
        for d, title, content in gratitude_items[:10]:
            gratitude_section += f"- **{d.isoformat()}** — {title}: {content[:100]}\n"
    else:
        gratitude_section = "本月暂无感恩记录。\n"

    # LLM 月度复盘
    api_key = load_api_key()
    ai_summary = generate_monthly_review(year, month, record_days, days_in_month,
                                          total_entries, all_categories,
                                          daily_summary, gratitude_items,
                                          todos_unfinished, moods_by_week, api_key)

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

## 月度复盘

{ai_summary}

---

*由思维札记自动生成*
"""

    output_path = MONTHLY_DIR / str(year) / f"{year}-{month:02d}.md"
    write_md(output_path, md)
    return True


def generate_monthly_review(year, month, record_days, total_days,
                              total_entries, categories, daily_summary,
                              gratitude_items, todos_unfinished,
                              moods_by_week, api_key):
    """按结构化框架生成月度深度复盘（800-1200字）"""
    top_cats = categories.most_common(5)
    categories_list = "、".join(f"{c}({n})" for c, n in top_cats)

    # 组装每日内容摘要（含标题+内容全文，供 LLM 引用）
    daily_content_text = ""
    for d in sorted(daily_summary.keys()):
        entries = daily_summary[d]
        daily_content_text += f"**{weekday_cn(d)} {d.isoformat()}**（{len(entries)} 条）:\n"
        for e in entries:
            daily_content_text += f"- [{e['category']}] {e['title']}：{e['content'][:150]}\n"
        daily_content_text += "\n"

    # 感恩清单
    gratitude_text = ""
    if gratitude_items:
        for d, title, content in gratitude_items[:15]:
            gratitude_text += f"- {d.isoformat()} — {title}: {content[:100]}\n"
    else:
        gratitude_text = "本月暂无感恩记录"

    # 待办
    todos_text = "、".join(todos_unfinished[:10]) if todos_unfinished else "本月无未完成待办"

    # 情绪
    mood_text = ""
    for wn in sorted(moods_by_week.keys()):
        moods = moods_by_week[wn]
        avg = "积极" if moods.count("😊") > len(moods)/2 else ("消极" if moods.count("😰")+moods.count("😢") > len(moods)/2 else "中性")
        mood_text += f"第{wn}周: {''.join(moods)} ({avg})；"

    prompt = f"""# 数据概要

## 周期
{year}年{month}月，记录 {record_days}/{total_days} 天，共 {total_entries} 条

## 分类分布
{categories_list}

## 情绪周趋势
{mood_text}

## 感恩记录
{gratitude_text}

## 未完成待办
{todos_text}

## 每日内容摘要
{daily_content_text}

---

基于以上数据，以"月度复盘"的形式，用第一人称"我"的口吻，撰写一份 {year}年{month}月的月度总结。月报侧重点：事务落地、细节执行、短期问题。

## 必写部分（工作向）

### 1. 核心业绩 & 任务完成
复盘本月完成的重要事项和既定目标的推进情况。从"工作与项目"、"今天做了什么"分类提取具体事例。

### 2. 能力与履职表现
评价本月技能成长、沟通协作、责任承担。从"学习与成长"、"人际与社交"分类提取。

### 3. 亮点与收获
本月最值得记录的突破、创新、可复用经验。

### 4. 问题、不足与原因
诚实审视本月未完成事项、效率短板、困难及其根因。结合情绪低点和未完成待办分析。

### 5. 改进措施 & 下一阶段计划
针对问题提出具体可执行的改善措施。明确下月 1-3 个核心目标。

## 可选部分（生活向 — 有数据则写，无则跳过）

### 6. 作息与健康（如有"健康与运动"分类数据则写）
### 7. 个人成长与学习（如有"学习与成长"分类数据则写）
### 8. 家庭与人际（如有"人际与社交"分类数据则写）
### 9. 消费与收支（如有相关记录则写）
### 10. 兴趣休闲 & 情绪状态（如有"阅读与观影"、"情绪与感受"分类数据则写）

---

输出要求：
1. 总计约 800-1200 字
2. 必须引用具体日期和事件，不能泛泛而谈
3. 问题和改进要有具体指向，不空洞
4. 标题用 ### 级别 Markdown
5. 直接输出总结内容，不要加"好的，以下是..."等引导语"""

    result = call_deepseek(prompt, api_key, max_tokens=2500, temperature=0.7)
    if result:
        return result
    return f"{year}年{month}月共记录 {total_entries} 条思考，覆盖 {record_days} 天。最活跃的分类是{top_cats[0][0] if top_cats else '综合'}。期待下月继续保持记录习惯，让记录成为日常生活的一部分。"


def main():
    today = date.today()
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
