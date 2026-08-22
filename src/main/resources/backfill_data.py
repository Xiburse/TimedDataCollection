#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
手动回填历史数据脚本
- 日数据：从 2026-06-01 到 2026-08-11，逐天调用 /update/time/{timestamp}
- 月数据：2026-06-01、2026-07-01、2026-08-01，调用 /update/timemonth/{timestamp}

用法：
    python backfill_data.py

前提：
    - Spring Boot 应用已在本地 8080 端口启动
    - Python 3.6+（仅使用标准库，无需额外安装）
"""

import urllib.request
import urllib.error
import json
from datetime import datetime, date, timedelta, timezone

# ============================================================
# 配置
# ============================================================
BASE_URL = "http://localhost:8080"
DAY_ENDPOINT = "/update/time/"      # GET，拼接毫秒时间戳
MONTH_ENDPOINT = "/update/timemonth/"
TZ = timezone(timedelta(hours=8))   # Asia/Shanghai UTC+8

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


# ============================================================
# 主流程
# ============================================================

def main():
    print("=" * 55)
    print("  历史数据回填脚本")
    print(f"  目标服务: {BASE_URL}")
    print("=" * 55)

    # ---- 日数据：06-01 ~ 08-10 ----
    day_start = date(2026, 6, 1)
    day_end = date(2026, 8, 10)
    days = list(date_range(day_start, day_end))
    print(f"\n📅 日数据回填: {day_start} → {day_end}  共 {len(days)} 天")
    print("-" * 40)

    for d in days:
        ts = to_timestamp_ms(d)
        url = f"{BASE_URL}{DAY_ENDPOINT}{ts}"
        call_api(url, f"日数据 {d.isoformat()} (ts={ts})")

    # ---- 月数据：06-01、07-01、08-01 ----
    months = [date(2026, 1, 1), date(2026, 2, 1), date(2026, 3, 1), date(2026, 4, 1), date(2026, 5, 1), date(2026, 6, 1), date(2026, 7, 1), date(2026, 8, 1)]
    print(f"\n📅 月数据回填: 共 {len(months)} 个月")
    print("-" * 40)

    for m in months:
        ts = to_timestamp_ms(m)
        url = f"{BASE_URL}{MONTH_ENDPOINT}{ts}"
        call_api(url, f"月数据 {m.strftime('%Y-%m')} (ts={ts})")

    print("\n" + "=" * 55)
    print("  回填完成")
    print("=" * 55)


if __name__ == "__main__":
    main()
