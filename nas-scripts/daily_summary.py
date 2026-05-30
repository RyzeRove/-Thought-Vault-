#!/usr/bin/env python3
"""
daily_summary.py — 日报生成脚本
执行频率: 每天 01:17 (DSM Cron: 17 1 * * *)
功能: 读取前一天的原始记录 → DeepSeek 分类+润色 → 生成整洁日报 MD
"""

import sys
from datetime import date, timedelta
from collections import defaultdict
from common import *


def generate_daily_summary(target_date: date):
    """为指定日期生成日报"""
    print(f"\n{'='*60}")
    print(f"[日报生成] 处理 {target_date.isoformat()} ({weekday_cn(target_date)})")
    print(f"{'='*60}")

    # 1. 读取原始记录
    raw_path = get_date_path(RAW_DIR, target_date.year, target_date.month, target_date.day)
    if not raw_path.exists():
        print(f"[跳过] 原始记录文件不存在: {raw_path}")
        return False

    raw_entries = parse_raw_file(raw_path)
    if not raw_entries:
        print(f"[跳过] {target_date} 没有有效记录")
        return False

    print(f"[读取] 共 {len(raw_entries)} 条原始记录")

    # 2. 检查锁
    if is_lock_present():
        print("[ABORT] 检测到写入锁，可能存在 APP 正在写入，退出")
        sys.exit(1)

    # 3. 调用 LLM 分类+润色
    api_key = load_api_key()
    refined = classify_and_refine(raw_entries, api_key)

    # 4. 构建日报
    categories_seen = list(dict.fromkeys(e["category_label"] for e in refined))
    times = sorted(e["time"] for e in refined)
    mood = extract_mood_keywords(raw_entries)

    # 按分类分组
    by_category = defaultdict(list)
    for e in refined:
        by_category[e["category_label"]].append(e)

    # 构建分类汇总
    sections = ""
    for cat in categories_seen:
        sections += f"## {cat}\n\n"
        for e in by_category[cat]:
            sections += f"**{e['title']}**\n\n{e['content']}\n\n"

    # 提取关键词（从分类名 + 标题生成）
    all_cat_ids = set(e["category_id"] for e in refined)
    keywords = " ".join(f"#{cid}" for cid in sorted(all_cat_ids))

    md = f"""# 每日整理 | {target_date.isoformat()}（{weekday_cn(target_date)}）

> 自动生成于 {date.today().isoformat()} 01:17 · 原始条目 {len(raw_entries)} 条

## 统计

| 指标 | 数值 |
|------|------|
| 总条目数 | {len(refined)} |
| 涉及分类 | {', '.join(categories_seen)} |
| 活跃时段 | {times[0]} - {times[-1]} |
| 情绪指标 | {mood} |

---

{sections}---

## 今日关键词
{keywords}
"""

    # 5. 写入
    output_path = get_date_path(DAILY_DIR, target_date.year, target_date.month, target_date.day)
    write_md(output_path, md)
    return True


def main():
    yesterday = date.today() - timedelta(days=1)

    # 支持命令行参数指定日期（格式: YYYY-MM-DD，用于回填）
    if len(sys.argv) > 1:
        try:
            target = date.fromisoformat(sys.argv[1])
        except ValueError:
            print(f"用法: python3 daily_summary.py [YYYY-MM-DD]")
            sys.exit(1)
    else:
        target = yesterday

    success = generate_daily_summary(target)
    if success:
        print(f"\n[完成] {target.isoformat()} 日报已生成")
    else:
        print(f"\n[跳过] {target.isoformat()} 无需处理")


if __name__ == "__main__":
    main()
