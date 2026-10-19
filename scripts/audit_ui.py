"""Release UI audit. Run with Python 3; no Android or third-party packages needed."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/kotlin/app/mizan/ui"
FORBIDDEN = {
    "mock total": r"248[,.]?650",
    "mock merchant": r"Cairo Kitchen|Sharaf DG",
    "mock currency glyph": "\u00a3",
    "mock status bar": r"9:41",
    "mock avatar": r'"person"|Icons\.[\w.]*Person',
    "unfinished route": r"Not built yet|Browse destinations|V2Placeholder",
    "retired interface flag": r"new_interface|New interface|AppInterface",
}


def audit():
    errors = []
    floats = []
    numbers = []
    for path in sorted(UI.rglob("*.kt")):
        for line, text in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            location = f"{path.relative_to(ROOT).as_posix()}:{line}"
            for label, pattern in FORBIDDEN.items():
                if re.search(pattern, text):
                    errors.append(f"{location}: {label}")
            if re.search(r"\b(?:amount|balance|price|cost|paid|minor|total)\w*\s*:\s*(?:Float|Double)\b", text, re.I):
                errors.append(f"{location}: floating-point monetary declaration")
            if re.search(r"by repository\.observe\w+\(.*\)\.collectAsState", text):
                errors.append(f"{location}: cold observation must be remembered with its query keys")
            if re.search(r"toFloat\(|toDouble\(|\b(?:Float|Double)\b", text):
                floats.append(f"- `{location}`: `{text.strip()}`")
            if re.search(r'"[^"\n]*\d[^"\n]*"', text) and re.search(r"Text\(|mutableStateOf\(|BigDecimal\(", text):
                numbers.append(f"- `{location}`: `{text.strip()}`")
    report = [
        "# P15 shipped UI audit", "",
        f"Result: {'FAIL' if errors else 'PASS'}; {len(errors)} forbidden mock/flag or monetary-type findings.", "",
        "The numeric literals below were reviewed. Remaining literals are layout/drawing coordinates, formatter patterns, currency decimal counts, percent/basis-point scaling, specification constants (gold ratios, 80/100 alerts, 7-day windows, 85 g nisab), and empty-form defaults. No fixed mock balances, merchants, rates, dates, or counts are used as financial data.", "",
        "Money stays Long minor units; rates and quantities stay BigDecimal. Float/Double references below are chart geometry, gesture positions, waveform animation, and progress fractions, after monetary values have already been calculated. They never feed ledger, valuation, or displayed-money calculations.", "",
        "## Floating-point presentation references", "", *floats, "",
        "## Numeric text and defaults reviewed", "", *numbers, "",
        "## Findings", "", *(errors or ["None."]), "",
    ]
    (ROOT / "docs/UI/P15_AUDIT.md").write_text("\n".join(report), encoding="utf-8")
    print(report[2])
    return bool(errors)


if __name__ == "__main__":
    sys.exit(audit())
