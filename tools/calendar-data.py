"""Regenerate the bundled 2023–2028 calendar. Requires lunar_python==1.4.9."""

from datetime import date, timedelta
from pathlib import Path

from lunar_python import Solar


OUTPUT = Path(__file__).resolve().parents[1] / "src/main/resources/com/eye/calendar"
MONTH_LABELS = {"冬": "十一", "腊": "十二"}


def generate_year(year):
    day = date(year, 1, 1)
    lines = ["# Generated with lunar_python 1.4.9; calendar dates UTC+08:00"]
    while day.year == year:
        lunar = Solar.fromYmd(day.year, day.month, day.day).getLunar()
        month = lunar.getMonthInChinese()
        month = MONTH_LABELS.get(month, month) + "月"
        lines.append("|".join((day.isoformat(), month, lunar.getDayInChinese(), lunar.getJieQi())))
        day += timedelta(days=1)
    (OUTPUT / f"lunar-{year}.txt").write_bytes(("\n".join(lines) + "\n").encode("utf-8"))


if __name__ == "__main__":
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for year in range(2023, 2029):
        generate_year(year)
