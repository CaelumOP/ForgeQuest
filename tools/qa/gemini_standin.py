"""Fake Gemini API for ForgeQuest QA builds (-PgeminiBase=http://10.0.2.2:8765/v1beta). Canned but well-formed replies."""
import json, os, sys, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PLAN = {
    "name": "3-day full body", "description": "Three full-body days with rest in between.",
    "days": [
        {"dayOfWeek": 1, "title": "Full body A", "isRest": False, "items": [
            {"exerciseId": "goblet_squat", "sets": 3, "repsMin": 8, "repsMax": 12, "restSeconds": 90},
            {"exerciseId": "db_bench_press", "sets": 3, "repsMin": 8, "repsMax": 12, "restSeconds": 90},
            {"exerciseId": "db_row", "sets": 3, "repsMin": 8, "repsMax": 12, "restSeconds": 90}]},
        {"dayOfWeek": 2, "title": "Rest", "isRest": True, "items": []},
        {"dayOfWeek": 3, "title": "Full body B", "isRest": False, "items": [
            {"exerciseId": "db_lunge", "sets": 3, "repsMin": 10, "repsMax": 12, "restSeconds": 90},
            {"exerciseId": "db_shoulder_press", "sets": 3, "repsMin": 8, "repsMax": 12, "restSeconds": 90},
            {"exerciseId": "hip_bridge", "sets": 3, "repsMin": 12, "repsMax": 15, "restSeconds": 60}]},
        {"dayOfWeek": 4, "title": "Rest", "isRest": True, "items": []},
        {"dayOfWeek": 5, "title": "Full body C", "isRest": False, "items": [
            {"exerciseId": "goblet_squat", "sets": 3, "repsMin": 10, "repsMax": 12, "restSeconds": 90},
            {"exerciseId": "knee_push_up", "sets": 3, "repsMin": 8, "repsMax": 15, "restSeconds": 60},
            {"exerciseId": "hammer_curl", "sets": 3, "repsMin": 10, "repsMax": 12, "restSeconds": 60}]},
        {"dayOfWeek": 6, "title": "Rest", "isRest": True, "items": []},
        {"dayOfWeek": 7, "title": "Rest", "isRest": True, "items": []},
    ],
}
MEAL = {"dish": "Chicken and rice", "items": [
    {"name": "Grilled chicken breast", "grams": 150, "kcal": 248, "protein": 46.5, "carbs": 0},
    {"name": "Basmati rice", "grams": 200, "kcal": 260, "protein": 5.4, "carbs": 56}],
    "handVisible": False, "note": "Assumed little oil; rice weighed cooked."}
# A text score and a single string where a list is asked for: fast models drift like this, and the app must cope.
DEBRIEF = {"score": "7/10", "summary": "Solid session.", "highlights": "You finished every set", "watch": [], "nextTime": ["Add one rep to each set"]}
# Touch tools/qa/fail_debrief to make debrief requests fail (tests Try again); delete it to let them through.
FAIL_DEBRIEF = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fail_debrief")


def reply_for(req):
    system = " ".join(p.get("text", "") for p in req.get("systemInstruction", {}).get("parts", []))
    contents = req.get("contents", [])
    last = contents[-1] if contents else {}
    text = " ".join(p.get("text", "") for p in last.get("parts", []) if "text" in p).lower()
    has_image = any("inlineData" in p for p in last.get("parts", []))
    if has_image or "meal photos" in system:
        return json.dumps(MEAL)
    if "10-second debrief" in system:
        return json.dumps(DEBRIEF)
    if "Convert the user's workout plan text" in system:
        return json.dumps(PLAN)
    if "plan" in text or "خطة" in text or "برنامج" in text:
        return "Here's a simple 3-day full-body week that fits your equipment.\n\n```json\n" + json.dumps(PLAN) + "\n```"
    if "knee" in text or "ركب" in text:
        return ("Sorry about the knee. For now, swap deep squats for box squats or glute bridges, keep pain under 3/10, "
                "and see a physio if it lasts more than two weeks.\nMEMORY: Has knee pain when squatting.")
    if "check-in" in text:
        return "Went well: you trained 3 times. This week: add a rep per set. Tip: aim for protein at every meal."
    return "Good question. Keep sessions short and regular, add a rep or a little weight each week, and sleep 7 to 9 hours."


class H(BaseHTTPRequestHandler):
    def _send(self, code, body, ctype="application/json"):
        data = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if "/models" in self.path:
            names = ["gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro"]
            self._send(200, json.dumps({"models": [{"name": "models/" + n, "supportedGenerationMethods": ["generateContent"]} for n in names]}))
        else:
            self._send(404, "{}")

    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0))
        req = json.loads(self.rfile.read(n) or b"{}")
        key = self.headers.get("x-goog-api-key", "")
        if key.startswith("bad"):
            self._send(400, json.dumps({"error": {"code": 400, "message": "API key not valid. Please pass a valid API key.", "status": "INVALID_ARGUMENT"}}))
            return
        if os.path.exists(FAIL_DEBRIEF) and "10-second debrief" in json.dumps(req.get("systemInstruction", {})):
            self._send(500, json.dumps({"error": {"code": 500, "message": "Internal error (QA fail_debrief)", "status": "INTERNAL"}}))
            return
        text = reply_for(req)
        sys.stderr.write(f"{time.strftime('%H:%M:%S')} {self.path[:60]} -> {text[:60]!r}\n")
        if "streamGenerateContent" in self.path:
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            words = text.split(" ")
            for i in range(0, len(words), 6):
                chunk = " ".join(words[i:i + 6]) + (" " if i + 6 < len(words) else "")
                ev = {"candidates": [{"content": {"parts": [{"text": chunk}], "role": "model"}}]}
                self.wfile.write(("data: " + json.dumps(ev) + "\r\n\r\n").encode())
                self.wfile.flush()
                time.sleep(0.05)
            end = {"candidates": [{"content": {"parts": [{"text": ""}], "role": "model"}, "finishReason": "STOP"}]}
            self.wfile.write(("data: " + json.dumps(end) + "\r\n\r\n").encode())
            return
        self._send(200, json.dumps({"candidates": [{"content": {"parts": [{"text": text}], "role": "model"}, "finishReason": "STOP"}],
                                    "usageMetadata": {"promptTokenCount": 100, "candidatesTokenCount": 50}}))

    def log_message(self, *a):
        pass


ThreadingHTTPServer(("0.0.0.0", 8765), H).serve_forever()
