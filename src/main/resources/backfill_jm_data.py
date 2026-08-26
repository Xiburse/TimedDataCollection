#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
jm 日/月数据回填脚本
- 日数据：在配置的日期范围内，逐天调用 /jm/day/{timestamp}
- 月数据：在配置的月份范围内，逐月调用 /jm/month/{timestamp}
- 采集的数据分别存入 jm_day / jm_month 表

用法：
    python backfill_jm_data.py

前提：
    - Spring Boot 应用已在本地 8080 端口启动
    - Python 3.6+（仅使用标准库，无需额外安装）
"""

import urllib.request
import urllib.error
import json
from datetime import datetime, date, timedelta, timezone

# ============================================================
# 配置区（按需修改）
# ============================================================
BASE_URL = "http://localhost:8080"
DAY_ENDPOINT = "/jm/day/"        # jm 日数据接口
MONTH_ENDPOINT = "/jm/month/"    # jm 月数据接口
TZ = timezone(timedelta(hours=8))  # Asia/Shanghai UTC+8

# 日数据采集范围（含首尾两天）
DAY_START = date(2026, 6, 1)
DAY_END = date(2026, 8, 11)

# 月数据采集范围（含首尾两月，按每月1号采集）
MONTH_START = date(2026, 6, 1)
MONTH_END = date(2026, 8, 1)

# ============================================================
# 工具函数
# ============================================================

def to_timestamp_ms(d: date) -> int:
    """将日期转为当天0点（Asia/Shanghai）的毫秒时间戳，与 Java ZoneId.systemDefault() 一致"""
    dt = datetime(d.year, d.month, d.day, 0, 0, 0, tzinfo=TZ)
    return int(dt.timestamp() * 1000)


def call_api(url: str, label: str):
    """GET 请求并打印结果"""
    try:
        with urllib.request.urlopen(url, timeout=30) as resp:
            body = resp.read().decode("utf-8")
            data = json.loads(body)
            success = data.get("success", False)
            status = "✔ 成功" if success else f"✘ 失败: {data.get('message', '')}"
            print(f"  {status}  |  {label}")
    except urllib.error.HTTPError as e:
        print(f"  ✘ HTTP {e.code}  |  {label}")
    except Exception as e:
        print(f"  ✘ 异常: {e}  |  {label}")


def date_range(start: date, end: date):
    """生成 [start, end] 的日期序列"""
    current = start
    while current <= end:
        yield current
        current += timedelta(days=1)


def month_first_days(start: date, end: date):
    """生成 [start所在月, end所在月] 之间每个月的1号"""
    current = start.replace(day=1)
    end_first = end.replace(day=1)
    while current <= end_first:
        yield current
        # 跳到下个月1号
        if current.month == 12:
            current = current.replace(year=current.year + 1, month=1)
        else:
            current = current.replace(month=current.month + 1)


# ============================================================
# 主流程
# ============================================================

def main():
    print("=" * 60)
    print("  jm 日/月数据回填脚本")
    print(f"  目标服务: {BASE_URL}")
    print("=" * 60)

    # ---- 日数据 ----
    days = list(date_range(DAY_START, DAY_END))
    print(f"\n📅 日数据回填: {DAY_START} → {DAY_END}  共 {len(days)} 天")
    print("-" * 50)

    for d in days:
        ts = to_timestamp_ms(d)
        url = f"{BASE_URL}{DAY_ENDPOINT}{ts}"
        call_api(url, f"日数据 {d.isoformat()} (ts={ts})")

    # ---- 月数据 ----
    months = list(month_first_days(MONTH_START, MONTH_END))
    print(f"\n📅 月数据回填: 共 {len(months)} 个月")
    print("-" * 50)

    for m in months:
        ts = to_timestamp_ms(m)
        url = f"{BASE_URL}{MONTH_ENDPOINT}{ts}"
        call_api(url, f"月数据 {m.strftime('%Y-%m')} (ts={ts})")

    print("\n" + "=" * 60)
    print("  回填完成")
    print("=" * 60)


if __name__ == "__main__":
    main()
