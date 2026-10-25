"""Drive the ForgeQuest emulator like a person. Coordinates are in a 540x1200 frame (half the real screen).

  python fq.py shot [label]     screenshot -> prints the PNG path (540x1200) to Read
  python fq.py ui               what's on screen: each text / button with its centre (x,y)
  python fq.py tap X Y          tap
  python fq.py long X Y         long-press
  python fq.py type "text"      type into the focused field (ASCII only)
  python fq.py scroll down|up   scroll the screen
  python fq.py swipe left|right swipe sideways (pages, tours)
  python fq.py back | home | enter
  python fq.py wait SECONDS
"""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET
sys.stdout.reconfigure(encoding="utf-8")
from PIL import Image

ADB = r"G:\Android\platform-tools\adb.exe"
HERE = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.environ.get("FQ_SHOTS", os.path.join(HERE, "shots"))
S = 2  # real pixels per frame pixel


def adb(*args, out=False):
    r = subprocess.run([ADB, *args], capture_output=True)
    return r.stdout if out else None


def shot(label=""):
    os.makedirs(SHOTS, exist_ok=True)
    n = len([f for f in os.listdir(SHOTS) if f.endswith(".png")]) + 1
    raw = adb("exec-out", "screencap", "-p", out=True)
    tmp = os.path.join(SHOTS, "_raw.png")
    open(tmp, "wb").write(raw)
    path = os.path.join(SHOTS, f"{n:03d}{'-' + re.sub(r'[^a-z0-9]+', '-', label.lower()) if label else ''}.png")
    Image.open(tmp).resize((540, 1200)).save(path)
    os.remove(tmp)
    print(path)


def ui():
    adb("shell", "uiautomator", "dump", "/sdcard/fq_ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/fq_ui.xml", out=True).decode("utf-8", "replace")
    root = ET.fromstring(xml[xml.index("<"):])
    for n in root.iter("node"):
        label = (n.get("text") or "").strip() or (n.get("content-desc") or "").strip()
        click = n.get("clickable") == "true" or n.get("checkable") == "true"
        if not label and not click:
            continue
        m = re.findall(r"\d+", n.get("bounds", ""))
        if len(m) != 4:
            continue
        x1, y1, x2, y2 = map(int, m)
        if x2 <= x1 or y2 <= y1:
            continue
        tags = [t for t, on in (("button", click), ("field", n.get("class", "").endswith("EditText")),
                                ("checked", n.get("checked") == "true"), ("selected", n.get("selected") == "true")) if on]
        print(f"({(x1 + x2) // 2 // S},{(y1 + y2) // 2 // S}) {label or '<unlabelled>'}" + (f"  [{', '.join(tags)}]" if tags else ""))


def main(a):
    c = a[0] if a else "help"
    if c == "shot": shot(" ".join(a[1:]))
    elif c == "ui": ui()
    elif c == "tap": adb("shell", "input", "tap", str(int(a[1]) * S), str(int(a[2]) * S)); time.sleep(0.8)
    elif c == "long": adb("shell", "input", "swipe", *[str(int(v) * S) for v in (a[1], a[2], a[1], a[2])], "800"); time.sleep(0.8)
    elif c == "type": adb("shell", "input", "text", a[1].replace(" ", "%s").replace("'", "").replace("&", "and")); time.sleep(0.5)
    elif c == "scroll":
        y1, y2 = (1700, 700) if a[1] == "down" else (700, 1700)
        adb("shell", "input", "swipe", "540", str(y1), "540", str(y2), "400"); time.sleep(0.8)
    elif c == "swipe":
        x1, x2 = (900, 180) if a[1] == "left" else (180, 900)
        adb("shell", "input", "swipe", str(x1), "1200", str(x2), "1200", "300"); time.sleep(0.8)
    elif c == "back": adb("shell", "input", "keyevent", "4"); time.sleep(0.8)
    elif c == "home": adb("shell", "input", "keyevent", "3"); time.sleep(0.8)
    elif c == "enter": adb("shell", "input", "keyevent", "66"); time.sleep(0.5)
    elif c == "wait": time.sleep(float(a[1]))
    else: print(__doc__)


main(sys.argv[1:])
