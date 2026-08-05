import re, pathlib
base = pathlib.Path(r"D:\Agent\Agent  workplace\Claude Code\Echo\.claude\worktrees\v3-task1-onboarding\app\src\main\res")
zh = (base / "values" / "strings.xml").read_text(encoding="utf-8")
en = (base / "values-en" / "strings.xml").read_text(encoding="utf-8")

def keys(xml):
    return set(re.findall(r'<string name="([^"]+)"', xml))

zhk, enk = keys(zh), keys(en)
print("zh total:", len(zhk), "| en total:", len(enk))
print("MISSING in en:", sorted(zhk - enk) if zhk - enk else "NONE")
print("extra in en:", sorted(enk - zhk) if enk - zhk else "NONE")
za = set(re.findall(r'<string-array name="([^"]+)"', zh))
ea = set(re.findall(r'<string-array name="([^"]+)"', en))
print("arrays zh:", za, "| en:", ea, "| diff:", za ^ ea)
