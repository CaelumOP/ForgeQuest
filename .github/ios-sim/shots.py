"""
iPhone Simulator screenshots of ForgeQuest Web (run by .github/workflows/ios-sim.yml on a GitHub macOS runner).

1. Safari: on iPhone the app only shows its "Add ForgeQuest to your Home Screen" page there, in English and Arabic.
2. Home Screen app: Share → Add to Home Screen, open it from the icon, onboard, then every tab, the chest exercise
   sheet and #/viewport, in English and then Arabic. This is how people really use it on an iPhone.

Each step is logged and a failure in one part never stops the rest: the screenshots that did work still get uploaded.
Screenshots come from `simctl io screenshot`, so they show the whole screen (status bar and home indicator too).
"""
import os
import subprocess
import time
import traceback

from appium import webdriver
from appium.options.ios import XCUITestOptions
from appium.webdriver.common.appiumby import AppiumBy

URL = os.environ.get("FQ_URL", "http://localhost:4173/ForgeQuest/")
UDID = os.environ["SIM_UDID"]
OUT = os.environ.get("OUT", "shots")
os.makedirs(OUT, exist_ok=True)
TABS = [("home", "#/"), ("workouts", "#/workouts"), ("food", "#/food"), ("coach", "#/coach"), ("profile", "#/profile")]


def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)


def shot(name):
    subprocess.run(["xcrun", "simctl", "io", UDID, "screenshot", f"{OUT}/{name}.png"], check=False, capture_output=True)
    log("shot", name)


def step(name, fn, *args):
    """Runs one part; on failure logs it, saves a screenshot and the page tree, and carries on."""
    try:
        return fn(*args)
    except Exception:
        log(f"FAILED {name}\n{traceback.format_exc()}")
        shot(f"zz-failed-{name}")
        try:
            with open(f"{OUT}/zz-failed-{name}.xml", "w") as f:
                f.write(driver.page_source)
        except Exception:
            pass
        return None


def new_driver():
    o = XCUITestOptions()
    o.udid = UDID
    o.set_capability("browserName", "Safari")
    o.set_capability("appium:safariInitialUrl", URL)
    # The workflow already booted the phone without a window; opening Simulator.app on top of it hung on CI.
    o.set_capability("appium:isHeadless", True)
    o.set_capability("appium:simulatorStartupTimeout", 600000)
    o.set_capability("appium:newCommandTimeout", 900)
    o.set_capability("appium:wdaLaunchTimeout", 900000)
    o.set_capability("appium:wdaConnectionTimeout", 900000)
    o.set_capability("appium:webviewConnectTimeout", 30000)
    # Home Screen web apps run outside Safari; ask for every web view so that one shows up as a context too.
    o.set_capability("appium:additionalWebviewBundleIds", ["*"])
    o.set_capability("appium:includeSafariInWebviews", True)
    return webdriver.Remote("http://127.0.0.1:4723", options=o)


# ---------------------------------------------------------------- web context helpers (language-free selectors)

JS_SET = """
const [sel, i, v] = arguments; const el = document.querySelectorAll(sel)[i];
if (!el) return false; el.focus(); el.value = v; el.dispatchEvent(new Event('input', { bubbles: true })); el.blur(); return true;
"""
JS_CLICK = "const [sel, i] = arguments; const el = document.querySelectorAll(sel)[i]; if (!el) return false; el.click(); return true;"
JS_HAS = "return document.querySelectorAll(arguments[0]).length;"


def js(code, *args):
    return driver.execute_script(code, *args)


def wait_for(sel, timeout=30):
    end = time.time() + timeout
    while time.time() < end:
        if js(JS_HAS, sel):
            return True
        time.sleep(0.5)
    raise TimeoutError(f"no {sel}")


def click(sel, i=0, pause=1.0):
    if not js(JS_CLICK, sel, i):
        raise RuntimeError(f"nothing to click: {sel}[{i}]")
    time.sleep(pause)


def next_step(pause=1.2):
    # The footer's last button is always the forward one (Continue, then Skip/Let's go on the last step).
    js("const b = document.querySelectorAll('.onb-foot button'); b[b.length - 1].click();")
    time.sleep(pause)


def skip_tour():
    if js(JS_HAS, ".tour"):
        js("const b = document.querySelector('.tour-top button'); if (b) b.click();")
        time.sleep(1)


def onboard_web(lang, prefix):
    wait_for(".onb-lang-btns button")
    shot(f"{prefix}00-language")
    click(".onb-lang-btns button", 0 if lang == "en" else 1, 1.5)
    skip_tour()
    wait_for(".onb input")
    js(JS_SET, ".onb input", 0, "Test")
    shot(f"{prefix}01-name")
    next_step()
    wait_for(".onb-num-input")
    js(JS_SET, ".onb-num-input", 0, "172")
    js(JS_SET, ".onb-num-input", 1, "30")
    shot(f"{prefix}02-body")
    next_step()
    shot(f"{prefix}03-plan")
    next_step()
    click(".onb-plans .onb-plan", 0)  # Lose fat, so the pace control shows
    shot(f"{prefix}04-goal")
    js("document.querySelector('.scroll, .onb-body')?.scrollTo(0, 99999); window.scrollTo(0, 99999);")
    time.sleep(0.5)
    shot(f"{prefix}04-goal-bottom")
    next_step()
    shot(f"{prefix}05-key")
    next_step(3)
    skip_tour()


def scroll_to_end():
    js("const s = document.querySelector('.scroll'); if (s) s.scrollTo(0, s.scrollHeight);")
    time.sleep(0.8)


def tabs_web(prefix):
    for name, hash_ in TABS:
        js(f"location.hash = '{hash_}'")
        time.sleep(2)
        skip_tour()
        shot(f"{prefix}10-{name}")
        scroll_to_end()
        shot(f"{prefix}11-{name}-bottom")
    js("location.hash = '#/workouts'")
    time.sleep(2)
    if js(JS_HAS, ".bm-chips button"):
        click(".bm-chips button", 0)  # Chest
        click(".bm .kit-btn", 0, 1.5)  # See Chest exercises
        shot(f"{prefix}12-chest-sheet")
        js("document.querySelector('.kit-backdrop')?.click()")
        time.sleep(1)
    js("location.hash = '#/viewport'")
    time.sleep(2)
    shot(f"{prefix}13-viewport")


def reset_app():
    """Wipes the app's data and reloads it, back to the language picker."""
    js("""
      localStorage.clear(); sessionStorage.clear();
      indexedDB.deleteDatabase('forgequest');
      setTimeout(() => location.replace(location.pathname), 800);
    """)
    time.sleep(5)


def dismiss_popups():
    """Safari's first-run tips cover the page; close any that show."""
    native()
    for label in ("Close", "Dismiss", "Not Now", "OK", "Continue"):
        for el in driver.find_elements(AppiumBy.IOS_PREDICATE, f'type == "XCUIElementTypeButton" AND label == "{label}" AND visible == 1'):
            try:
                el.click()
                time.sleep(1)
            except Exception:
                pass


def safari_gate():
    """The install page Safari shows on iPhone, in both languages."""
    dismiss_popups()
    shot("safari-en-00-install-page")
    # The language button by its class from the page (its label changes with the language); back to English after.
    driver.switch_to.context(driver._fq_safari_context)
    click(".lang-toggle", 0, 1.5)
    shot("safari-ar-00-install-page")
    click(".lang-toggle", 0, 1.5)
    native()
    dump("safari-page")


# ---------------------------------------------------------------- Home Screen app

def native():
    driver.switch_to.context("NATIVE_APP")


def find_label(label, kind=None, timeout=10):
    pred = f'label == "{label}"' + (f' AND type == "{kind}"' if kind else "")
    end = time.time() + timeout
    while time.time() < end:
        found = driver.find_elements(AppiumBy.IOS_PREDICATE, pred)
        if found:
            return found[0]
        time.sleep(0.5)
    raise TimeoutError(f"no element labelled {label!r}")


def dump(name):
    try:
        with open(f"{OUT}/zz-tree-{name}.xml", "w") as f:
            f.write(driver.page_source)
    except Exception as e:
        log("no page source", e)


def share_sheet_open():
    return bool(driver.find_elements(AppiumBy.IOS_PREDICATE, 'label == "Copy" AND visible == 1'))


def open_share_sheet():
    """The install page's Share button first (a real tap: iOS only shares from a touch), else Safari's ⋯ → Share."""
    dismiss_popups()
    find_label("Share", "XCUIElementTypeButton").click()
    time.sleep(3)
    if share_sheet_open():
        return
    log("No share sheet from the page's button: trying Safari's menu")
    find_label("More", "XCUIElementTypeButton").click()
    time.sleep(1.5)
    shot("app-00-safari-menu")
    dump("safari-menu")
    find_label("Share").click()
    time.sleep(3)
    if not share_sheet_open():
        raise RuntimeError("share sheet didn't open")


def add_to_home_screen():
    native()
    open_share_sheet()
    shot("app-00-share-sheet")
    dump("share-sheet")
    for _ in range(10):
        found = driver.find_elements(AppiumBy.IOS_PREDICATE, 'label == "Add to Home Screen"')
        if found:
            try:
                driver.execute_script("mobile: scroll", {"elementId": found[0].id, "toVisible": True})
            except Exception:
                pass
            if found[0].is_displayed():
                found[0].click()
                break
        # Swipe on the action list itself (the cell that says Copy), so the sheet scrolls instead of closing.
        cells = driver.find_elements(AppiumBy.IOS_PREDICATE, 'label == "Copy" AND visible == 1')
        if not cells:
            dump("sheet-closed")
            raise RuntimeError("the share sheet closed")
        driver.execute_script("mobile: swipe", {"elementId": cells[0].id, "direction": "up"})
        time.sleep(1)
    else:
        dump("no-add-to-home")
        raise RuntimeError("no Add to Home Screen")
    time.sleep(2.5)
    shot("app-01-add-sheet")
    dump("add-sheet")
    find_label("Add", "XCUIElementTypeButton").click()
    time.sleep(3)


def open_from_home_screen():
    driver.execute_script("mobile: pressButton", {"name": "home"})
    time.sleep(2)
    driver.update_settings({"defaultActiveApplication": "com.apple.springboard"})
    shot("app-02-home-screen")
    for _ in range(4):
        icons = driver.find_elements(AppiumBy.IOS_PREDICATE, 'label == "ForgeQuest" AND visible == 1')
        if icons:
            icons[0].click()
            break
        driver.execute_script("mobile: swipe", {"direction": "left"})  # the icon can land on the next page
        time.sleep(1.5)
    else:
        dump("home-screen")
        raise RuntimeError("no ForgeQuest icon")
    time.sleep(5)
    driver.update_settings({"defaultActiveApplication": "auto"})
    shot("app-03-opened")


def app_web_context():
    """The Home Screen app's page as a web context, when Appium can see it."""
    for _ in range(10):
        contexts = driver.contexts
        log("contexts", contexts)
        for c in contexts:
            if c.startswith("WEBVIEW"):
                driver.switch_to.context(c)
                # Safari's tab has the same address; only the Home Screen app runs standalone.
                if js("return navigator.standalone === true || matchMedia('(display-mode: standalone)').matches"):
                    return True
        time.sleep(2)
    return False


def onboard_native():
    """Fallback when the app's page isn't reachable as a web context: tap by the English labels."""
    native()
    find_label("English").click()
    time.sleep(2)
    field = driver.find_elements(AppiumBy.CLASS_NAME, "XCUIElementTypeTextField")[0]
    field.click()
    field.send_keys("Test")
    shot("app-04-name")
    find_label("Continue").click()
    time.sleep(1.5)
    fields = driver.find_elements(AppiumBy.CLASS_NAME, "XCUIElementTypeTextField")
    for f, v in zip(fields[:2], ("172", "30")):
        f.click()
        f.send_keys(v)
    try:
        driver.execute_script("mobile: hideKeyboard", {"keys": ["Done", "done"]})
    except Exception:
        pass
    shot("app-05-body")
    for label in ("Continue", "Continue", "Continue"):
        find_label(label).click()
        time.sleep(1.5)
    find_label("Skip for now").click()
    time.sleep(3)
    skip = driver.find_elements(AppiumBy.IOS_PREDICATE, 'label == "Skip"')
    if skip:
        skip[0].click()
        time.sleep(1)


def app_tabs_native():
    for label in ("Home", "Workouts", "Food", "Coach", "Profile"):
        find_label(label).click()
        time.sleep(2)
        shot(f"app-10-{label.lower()}")


def home_screen_run():
    if step("app-add", add_to_home_screen) is None and not os.path.exists(f"{OUT}/app-01-add-sheet.png"):
        return
    if step("app-open", open_from_home_screen) is None and not os.path.exists(f"{OUT}/app-03-opened.png"):
        return
    if step("app-webview", app_web_context):
        for lang in ("en", "ar"):
            step(f"app-{lang}-onboarding", onboard_web, lang, f"app-{lang}-")
            step(f"app-{lang}-tabs", tabs_web, f"app-{lang}-")
            if lang == "en":
                step("app-reset", reset_app)
    else:
        log("No web context for the Home Screen app: driving it by its labels instead")
        step("app-onboarding-native", onboard_native)
        step("app-tabs-native", app_tabs_native)


if __name__ == "__main__":
    log("Starting session (the first one builds WebDriverAgent, which takes a few minutes)", URL)
    driver = new_driver()
    try:
        time.sleep(3)
        driver._fq_safari_context = driver.current_context
        log("contexts", driver.contexts, "current", driver.current_context)
        step("safari-install-page", safari_gate)
        home_screen_run()
    finally:
        driver.quit()
    log("done", sorted(os.listdir(OUT)))
