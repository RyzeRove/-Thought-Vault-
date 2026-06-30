#!/bin/bash
#
# run_all.sh — 历史数据回填 / 修复工具
#
# 用法:
#   ./run_all.sh                    # 处理昨天
#   ./run_all.sh 2026-05-30         # 处理指定日期
#   ./run_all.sh 2026-05-01 2026-05-30  # 处理日期范围
#   ./run_all.sh --full             # 回填所有历史数据（谨慎使用）
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

PYTHON="${PYTHON:-python3}"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# --- 检查依赖 ---
check_deps() {
    log_info "检查 Python 依赖..."

    if ! $PYTHON -c "import openai" 2>/dev/null; then
        log_warn "openai 包未安装，正在安装..."
        pip install openai
    fi

    # 检查 api_key.txt
    if [ ! -f "../config/api_key.txt" ]; then
        log_error "请先创建 ../config/api_key.txt 并填入 DeepSeek API Key"
        exit 1
    fi

    log_info "依赖检查通过"
}

# --- 处理单天 ---
process_day() {
    local date_str=$1
    log_info "处理: $date_str"

    # 日报
    $PYTHON daily_summary.py "$date_str" || log_warn "日报生成失败: $date_str"

    echo ""
}

# --- 处理周报 ---
process_week() {
    local year=$1
    local week=$2
    log_info "生成周报: $year-W$week"
    $PYTHON weekly_summary.py "$year" "$week" || log_warn "周报生成失败"
}

# --- 处理月报 ---
process_month() {
    local year=$1
    local month=$2
    log_info "生成月报: $year-$month"
    $PYTHON monthly_summary.py "$year" "$month" || log_warn "月报生成失败"
}

# --- 处理季报 ---
process_quarter() {
    local year=$1
    local quarter=$2
    log_info "生成季报: $year-Q$quarter"
    $PYTHON quarterly_summary.py "$year" "$quarter" || log_warn "季报生成失败"
}

# --- 处理年报 ---
process_year() {
    local year=$1
    log_info "生成年报: $year"
    $PYTHON yearly_summary.py "$year" || log_warn "年报生成失败"
}

# --- 主逻辑 ---
main() {
    check_deps

    if [ "$1" = "--full" ]; then
        # 全量回填：从 raw/ 中最早的日期开始
        log_warn "全量回填模式（将处理所有历史数据）"
        read -p "确认继续? (y/N): " confirm
        if [ "$confirm" != "y" ]; then
            log_info "已取消"
            exit 0
        fi

        # 找到最早日期
        earliest=$(find ../raw -name "*.md" -type f | sort | head -1)
        if [ -z "$earliest" ]; then
            log_error "raw/ 目录下无数据"
            exit 1
        fi

        start_date=$(basename "$earliest" .md)
        end_date=$(date -d "yesterday" +%Y-%m-%d)

        log_info "回填范围: $start_date ~ $end_date"

        current=$start_date
        while [ "$current" != "$end_date" ]; do
            process_day "$current"
            current=$(date -I -d "$current + 1 day")
        done

        # 处理周/月/季/年报
        log_info "生成周/月/季/年报..."
        start_year=$(echo "$start_date" | cut -d- -f1)
        end_year=$(echo "$end_date" | cut -d- -f1)

        for y in $(seq "$start_year" "$end_year"); do
            process_year "$y"
            for q in 1 2 3 4; do
                process_quarter "$y" "$q"
            done
            for m in $(seq 1 12); do
                process_month "$y" "$m"
            done
        done

        log_info "全量回填完成！"
    elif [ $# -eq 0 ]; then
        # 默认：处理昨天
        yesterday=$(date -d "yesterday" +%Y-%m-%d)
        process_day "$yesterday"
    elif [ $# -eq 1 ]; then
        # 单天
        process_day "$1"
    elif [ $# -eq 2 ]; then
        # 日期范围
        start=$1
        end=$2
        log_info "批量处理: $start ~ $end"

        current=$start
        while [ "$current" != "$end" ]; do
            process_day "$current"
            current=$(date -I -d "$current + 1 day")
        done
        process_day "$end"
    fi

    echo ""
    log_info "全部完成！查看输出目录:"
    echo "  日报: $(realpath ../daily/)"
    echo "  周报: $(realpath ../weekly/)"
    echo "  月报: $(realpath ../monthly/)"
    echo "  季报: $(realpath ../quarterly/)"
    echo "  年报: $(realpath ../yearly/)"
}

main "$@"
