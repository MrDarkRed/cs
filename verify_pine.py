import re
import sys

def verify_pine_script(file_path):
    print(f"Verifying Pine Script file: {file_path}")
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()
        lines = content.splitlines()

    errors = []
    warnings = []

    # 1. Version directive
    if not any(l.strip() == "//@version=6" for l in lines[:5]):
        errors.append("Missing //@version=6 directive in top 5 lines")

    # 2. Indicator declaration
    has_indicator = any(l.strip().startswith("indicator(") for l in lines)
    if not has_indicator:
        errors.append("Missing indicator() declaration")

    # 3. Plot counts
    plot_calls = re.findall(r'\b(plot|plotshape|plotchar|plotcandle|plotbar)\s*\(', content)
    print(f"Total plot-like calls: {len(plot_calls)}")
    if len(plot_calls) > 64:
        errors.append(f"Plot call count ({len(plot_calls)}) exceeds TradingView limit of 64")

    # 4. Security calls
    security_calls = re.findall(r'\brequest\.security\s*\(', content)
    print(f"Total request.security calls: {len(security_calls)}")
    if len(security_calls) > 40:
        errors.append(f"request.security calls ({len(security_calls)}) exceeds TradingView limit of 40")

    # 5. Check for lines ending with colon (causes CE10156)
    for idx, line in enumerate(lines):
        stripped = line.strip()
        if stripped.endswith(":") and not stripped.startswith("//"):
            errors.append(f"Line {idx+1}: Line ends with ':' (causes CE10156 end of line without continuation): {stripped}")

    # 6. Check for ta.* inside ternary or short-circuit expressions (CW10002, CW10004)
    for idx, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith("//") or not stripped:
            continue
        if re.search(r'\?\s*ta\.', line):
            errors.append(f"Line {idx+1}: ta.* called inside ternary operator (causes CW10004): {stripped}")
        if re.search(r'(\band\b|\bor\b)\s+.*ta\.(highest|lowest|ema|sma|rsi|macd|crossover|crossunder)\b', line):
            errors.append(f"Line {idx+1}: ta.* called after short-circuit 'and/or' (causes CW10002): {stripped}")

    # 7. Check balanced brackets/parentheses
    clean_lines = []
    for line in lines:
        cleaned = re.sub(r'//.*$', '', line)
        cleaned = re.sub(r'"([^"\\]|\\.)*"', '""', cleaned)
        cleaned = re.sub(r"'([^'\\]|\\.)*'", "''", cleaned)
        clean_lines.append(cleaned)

    full_cleaned = "\n".join(clean_lines)
    for open_ch, close_ch, name in [('(', ')', 'Parentheses'), ('[', ']', 'Square brackets'), ('{', '}', 'Curly braces')]:
        open_count = full_cleaned.count(open_ch)
        close_count = full_cleaned.count(close_ch)
        if open_count != close_count:
            errors.append(f"Unbalanced {name}: {open_count} '{open_ch}' vs {close_count} '{close_ch}'")

    print("\n--- Verification Summary ---")
    if warnings:
        print(f"Warnings ({len(warnings)}):")
        for w in warnings:
            print("  [WARN]", w)
    else:
        print("No warnings.")

    if errors:
        print(f"FAILED with {len(errors)} error(s):")
        for e in errors:
            print("  [ERROR]", e)
        return False
    else:
        print("PASSED: Pine Script v6 syntax and limits check successfully verified!")
        return True

if __name__ == "__main__":
    success = verify_pine_script("HTM_Multicator_v6.pine")
    sys.exit(0 if success else 1)
