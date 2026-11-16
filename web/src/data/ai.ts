// Shared AI client for Food (scan) now and the Coach later. Port of Android data/ai/GeminiClient.kt, GroqClient.kt
// and ModelStore.kt, minus streaming and the weekly model discovery (the web uses the "-latest" aliases).
// The person's own keys come from settings and travel only in request headers (x-goog-api-key / Authorization).
// Nothing here logs: error `detail` holds the HTTP status and a trimmed body, never a key.
import { isAiOn, loadSettings, type Settings } from './settings';

export const GEMINI_BASE = 'https://generativelanguage.googleapis.com/v1beta';
export const GROQ_BASE = 'https://api.groq.com/openai/v1';
export const FALLBACK_STABLE_FLASH = 'gemini-2.5-flash';
export const FALLBACK_FAST = 'gemini-flash-latest';
export const FALLBACK_LITE = 'gemini-flash-lite-latest';
export const GROQ_FALLBACK_MODEL = 'llama-3.3-70b-versatile';

/** Why a call failed. Map to words with aiErrorMessage(). */
export type AiErrorKind =
  | 'no_key'      // nothing saved in Profile
  | 'bad_key'     // 401/403 or "API key not valid"
  | 'quota'       // 429: the free limit is used up
  | 'offline'     // the request never reached the server
  | 'timeout'     // no answer in time
  | 'busy'        // 503
  | 'server'      // any other HTTP error (see status)
  | 'blocked'     // 200 but no text (safety block, empty reply)
  | 'unreadable'  // the reply is not in the expected format
  | 'no_food';    // the reply is valid but lists nothing

export class AiError extends Error {
  readonly kind: AiErrorKind;
  readonly status: number;
  /** Technical cause for a "copy details" action. Never contains a key. */
  readonly detail: string;
  constructor(kind: AiErrorKind, detail = '', status = 0) {
    super(`${kind}${detail ? `: ${detail}` : ''}`);
    this.name = 'AiError';
    this.kind = kind;
    this.status = status;
    this.detail = detail;
  }
}

/** i18n key (Android strings) and args for an error. */
export function aiErrorKey(e: unknown): { key: string; args: unknown[] } {
  if (!(e instanceof AiError)) return { key: 'ai_err_network', args: [] };
  switch (e.kind) {
    case 'no_key': return { key: 'ai_err_missing_key', args: [] };
    case 'bad_key': return { key: 'ai_err_invalid_key', args: [] };
    case 'quota': return { key: 'ai_err_rate_limited', args: [] };
    case 'offline': return { key: 'ai_err_offline', args: [] };
    case 'timeout': return { key: 'ai_err_timeout', args: [] };
    case 'busy': return { key: 'ai_err_busy', args: [] };
    case 'blocked': return { key: 'ai_err_blocked', args: [] };
    case 'unreadable': return { key: 'scan_err_unreadable', args: [] };
    case 'no_food': return { key: 'scan_err_no_food', args: [] };
    default: return { key: 'ai_err_server', args: [e.status] };
  }
}

// ---------------------------------------------------------------- models

/** Models to try, in order, for the settings.coachModel choice: "auto"/"lite" (default) start on Flash-Lite. */
export function modelsFor(choice: string, s: Pick<Settings, 'fastModel' | 'smartModel' | 'liteModel'>): string[] {
  const fast = s.fastModel || FALLBACK_FAST;
  const lite = s.liteModel || FALLBACK_LITE;
  const smart = s.smartModel || fast;
  const list = choice === 'flash' ? [fast, FALLBACK_STABLE_FLASH, lite]
    : choice === 'pro' ? [smart, fast, FALLBACK_STABLE_FLASH, lite]
    : [lite, fast, FALLBACK_STABLE_FLASH];
  return [...new Set(list.filter(Boolean))];
}

/** Meal scans start on full Flash (it does the portion math better), like Android. */
export const scanModelsFor = (s: Pick<Settings, 'fastModel' | 'smartModel' | 'liteModel'>): string[] =>
  [...new Set([s.fastModel || FALLBACK_FAST, FALLBACK_STABLE_FLASH, s.liteModel || FALLBACK_LITE])];

// ---------------------------------------------------------------- Gemini

export type GeminiPart = { text: string } | { inlineData: { mimeType: string; data: string } };
export const textPart = (text: string): GeminiPart => ({ text });
/** [base64] is the raw base64 (no "data:" prefix). */
export const imagePart = (base64: string, mimeType = 'image/jpeg'): GeminiPart => ({ inlineData: { mimeType, data: base64 } });

export type GeminiOptions = {
  system?: string;
  /** The parts of the last user turn: text, then optionally images. */
  parts: GeminiPart[];
  /** Earlier turns, oldest first. */
  history?: { role: 'user' | 'model'; text: string }[];
  /** Ask for application/json. */
  json?: boolean;
  /** Zero thinking (quick chat replies). */
  fast?: boolean;
  /** Small reasoning budget (meal scans). */
  lowThinking?: boolean;
  /** Models to try in order; default from settings.coachModel. */
  models?: string[];
  /** Overrides the key from settings (e.g. to test a key before saving). */
  apiKey?: string;
  /** Aborting gives AiError('timeout'). */
  signal?: AbortSignal;
};

/** Tunables the tests shorten. */
export const aiTuning = { busyRetryMs: 1500, quickTimeoutMs: 28_000, slowTimeoutMs: 60_000 };

type Thinking = 'budget0' | 'minimal' | 'lowLevel' | 'lowBudget' | 'default';
const offSwitch = new Map<string, Thinking>();
const lowSwitch = new Map<string, Thinking>();
/** Models whose free quota ran out, until when (ms). */
const coolingDown = new Map<string, number>();
/** Test hook. */
export function resetAiState() { offSwitch.clear(); lowSwitch.clear(); coolingDown.clear(); }

/** "Please retry in 12h7m14s" → ms; a minute when Google doesn't say. Clamped to 10 s .. 24 h. */
export function retryAfterMs(detail: string): number {
  const m = /retry in (?:(\d+)h)?(?:(\d+)m)?(?:([\d.]+)s)?/.exec(detail);
  if (!m) return 60_000;
  const ms = ((Number(m[1] ?? 0)) * 3600 + (Number(m[2] ?? 0)) * 60 + 0) * 1000 + Math.trunc(Number(m[3] ?? 0) * 1000);
  return Math.min(24 * 3600_000, Math.max(10_000, ms));
}

export function classifyHttp(code: number, body: string, label: string): AiError {
  const detail = `${label}: HTTP ${code}: ${body.slice(0, 600)}`;
  const lower = body.toLowerCase();
  if (lower.includes('api_key_invalid') || lower.includes('api key not valid') || lower.includes('api key expired') || code === 401 || code === 403) {
    return new AiError('bad_key', detail, code);
  }
  if (code === 429) return new AiError('quota', detail, code);
  if (code === 503) return new AiError('busy', detail, code);
  if (code === 504) return new AiError('timeout', detail, code);
  return new AiError('server', detail, code);
}

/** One fetch with a timeout. Network failures become 'offline', aborts 'timeout'. */
async function send(url: string, init: RequestInit, timeoutMs: number, outer: AbortSignal | undefined, label: string): Promise<string> {
  const ctl = new AbortController();
  let timedOut = false;
  const timer = setTimeout(() => { timedOut = true; ctl.abort(); }, timeoutMs);
  const onOuter = () => ctl.abort();
  if (outer) { if (outer.aborted) ctl.abort(); else outer.addEventListener('abort', onOuter); }
  try {
    const r = await fetch(url, { ...init, signal: ctl.signal });
    const text = await r.text();
    if (!r.ok) throw classifyHttp(r.status, text, label);
    return text;
  } catch (e) {
    if (e instanceof AiError) throw e;
    if (timedOut || ctl.signal.aborted) throw new AiError('timeout', `${label}: no answer in time`);
    throw new AiError('offline', `${label}: ${e instanceof Error ? e.name : 'error'}`);
  } finally {
    clearTimeout(timer);
    outer?.removeEventListener('abort', onOuter);
  }
}

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

/** Text of the first candidate, skipping "thought" parts. Null when there is none. */
export function extractText(body: string): string | null {
  let root: any;
  try { root = JSON.parse(body); } catch { return null; }
  const parts = root?.candidates?.[0]?.content?.parts;
  if (!Array.isArray(parts)) return null;
  const text = parts.filter((p: any) => p && p.thought !== true && typeof p.text === 'string').map((p: any) => p.text).join('').trim();
  return text || null;
}

function finishInfo(body: string): string {
  try {
    const root = JSON.parse(body);
    return `finishReason=${root?.candidates?.[0]?.finishReason} blockReason=${root?.promptFeedback?.blockReason}`;
  } catch { return body.slice(0, 200); }
}

function thinkingConfig(t: Thinking): Record<string, unknown> | null {
  switch (t) {
    case 'budget0': return { thinkingBudget: 0 };
    case 'minimal': return { thinkingLevel: 'minimal' };
    case 'lowLevel': return { thinkingLevel: 'low' };
    case 'lowBudget': return { thinkingBudget: 1024 };
    default: return null;
  }
}

/** Each model family takes a different switch (see Android generateOnce). */
function thinkingOptions(model: string, o: GeminiOptions): Thinking[] {
  let list: Thinking[];
  if (o.lowThinking) {
    const r = lowSwitch.get(model);
    list = [...(r ? [r] : []), 'lowLevel', 'lowBudget', 'default'];
  } else if (o.fast) {
    const r = offSwitch.get(model);
    list = [...(r ? [r] : []), ...(model.includes('lite') ? ['minimal', 'budget0'] as Thinking[] : ['budget0', 'minimal'] as Thinking[]), 'default'];
  } else list = ['default'];
  return [...new Set(list)];
}

async function requestOnce(apiKey: string, model: string, o: GeminiOptions, thinking: Thinking): Promise<string> {
  const contents = [
    ...(o.history ?? []).map((h) => ({ role: h.role, parts: [{ text: h.text }] })),
    { role: 'user', parts: o.parts },
  ];
  const cfg = thinkingConfig(thinking);
  const generationConfig: Record<string, unknown> = {};
  if (o.json) generationConfig.responseMimeType = 'application/json';
  if (cfg) generationConfig.thinkingConfig = cfg;
  const payload: Record<string, unknown> = { contents };
  if (o.system) payload.systemInstruction = { parts: [{ text: o.system }] };
  if (Object.keys(generationConfig).length) payload.generationConfig = generationConfig;
  const body = await send(
    `${GEMINI_BASE}/models/${model}:generateContent`,
    { method: 'POST', headers: { 'content-type': 'application/json', 'x-goog-api-key': apiKey }, body: JSON.stringify(payload) },
    thinking === 'default' ? aiTuning.slowTimeoutMs : aiTuning.quickTimeoutMs, o.signal, `${model} generate ${thinking}`,
  );
  const text = extractText(body);
  if (!text) throw new AiError('blocked', `${model}: no text (${finishInfo(body)})`);
  return text;
}

async function generateOnModel(apiKey: string, model: string, o: GeminiOptions): Promise<string> {
  let last: AiError | null = null;
  for (const thinking of thinkingOptions(model, o)) {
    try {
      const text = await requestOnce(apiKey, model, o, thinking);
      if (o.lowThinking) lowSwitch.set(model, thinking); else if (o.fast) offSwitch.set(model, thinking);
      return text;
    } catch (e) {
      // A 400 means this model rejected the thinking switch: try the next one.
      if (e instanceof AiError && e.kind === 'server' && e.status === 400) { last = e; continue; }
      throw e;
    }
  }
  throw last ?? new AiError('blocked', `${model}: no thinking option accepted`);
}

/**
 * Gemini generateContent. Tries the models in order: a busy model gets one retry; quota, retired (404) and
 * server errors move on to the next model; a bad key, no key, offline or a blocked answer stop right away.
 */
export async function geminiGenerate(o: GeminiOptions): Promise<string> {
  const s = await loadSettings();
  if (!o.apiKey && !isAiOn(s)) throw new AiError('no_key');
  const key = (o.apiKey ?? s.apiKey).trim();
  if (!key) throw new AiError('no_key');
  const all = [...new Set([...(o.models ?? modelsFor(s.coachModel, s)), FALLBACK_STABLE_FLASH, FALLBACK_FAST, FALLBACK_LITE].filter(Boolean))];
  const now = Date.now();
  const candidates = all.filter((m) => (coolingDown.get(m) ?? 0) < now);
  let last: AiError = new AiError('blocked', 'no model answered');
  let timeouts = 0;
  for (const model of candidates.length ? candidates : all) {
    let retried = false;
    for (;;) {
      try {
        return await generateOnModel(key, model, o);
      } catch (e) {
        if (!(e instanceof AiError)) throw e;
        if (e.kind === 'no_key' || e.kind === 'bad_key' || e.kind === 'offline' || e.kind === 'blocked') throw e;
        last = e;
        if (o.signal?.aborted) throw e;
        if (e.kind === 'quota') coolingDown.set(model, Date.now() + retryAfterMs(e.detail));
        // Two stalled models in a row means Google (or the network) is the problem.
        if (e.kind === 'timeout' && ++timeouts >= 2) throw e;
        if (e.kind === 'busy' && !retried) { retried = true; await sleep(aiTuning.busyRetryMs); continue; }
        break;
      }
    }
  }
  throw last;
}

// ---------------------------------------------------------------- Groq

export type GroqMessage = { role: 'system' | 'user' | 'assistant'; content: string };

/** Smartest fast chat model among [ids]: same order as Android GroqClient.pickFastest. */
export function pickGroqModel(ids: string[]): string {
  return ids.find((i) => i.includes('llama-3.3-70b-versatile'))
    ?? ids.find((i) => i.includes('llama-3.3-70b'))
    ?? ids.find((i) => i.includes('llama-4-maverick'))
    ?? ids.find((i) => i.includes('llama-4-scout'))
    ?? ids.find((i) => i.includes('qwen') && i.includes('32b'))
    ?? ids.find((i) => i.includes('llama-3.1-8b-instant'))
    ?? ids.find((i) => i.startsWith('llama') && !i.includes('guard'))
    ?? GROQ_FALLBACK_MODEL;
}

export type GroqOptions = {
  messages: GroqMessage[];
  model?: string;
  apiKey?: string;
  temperature?: number;
  maxTokens?: number;
  /** JSON mode: the reply is a single JSON object. */
  json?: boolean;
  signal?: AbortSignal;
};

/** OpenAI-style chat completion on Groq (fast chat). Returns the reply text. */
export async function groqChat(o: GroqOptions): Promise<string> {
  const s = await loadSettings();
  const key = (o.apiKey ?? s.groqKey).trim();
  if (!key) throw new AiError('no_key');
  const model = o.model || s.groqModel || GROQ_FALLBACK_MODEL;
  const body = await send(
    `${GROQ_BASE}/chat/completions`,
    {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: `Bearer ${key}` },
      body: JSON.stringify({ model, messages: o.messages, temperature: o.temperature ?? 0.5, max_tokens: o.maxTokens ?? 1500, ...(o.json ? { response_format: { type: 'json_object' } } : {}) }),
    },
    15_000, o.signal, `groq ${model}`,
  );
  let text = '';
  try { text = String(JSON.parse(body)?.choices?.[0]?.message?.content ?? ''); } catch { /* handled below */ }
  text = text.trim();
  if (!text) throw new AiError('blocked', `groq ${model}: no text`);
  return text;
}

// ---------------------------------------------------------------- Gemini app skill prompts

export const PLAN_PROMPT = `You are building a training plan for my workout app.

WHAT I WANT: <optional: YouTube playlist or video links, and/or a description, e.g. "4 days a week, fat loss">

Before writing the plan, check you know these five things. If any is missing from what I wrote, ask me about all the
missing ones in one short message and wait for my answers:
1. Where I train: full gym, home with some equipment (which: dumbbells, kettlebells, pull-up bar, bands…), or bodyweight only.
2. How many days a week I can train.
3. My level: beginner, intermediate or advanced.
4. My goal: muscle, strength, fat loss, or general fitness.
5. How long one session can be.

IF I GAVE YOUTUBE LINKS: watch every video and copy what the coach actually does and says on screen, not general
knowledge. Use my five answers only to fill gaps the videos leave.
IF I GAVE NO LINKS: design the plan yourself from my answers, using only equipment I have.

What to produce: one JSON object in a single \`\`\`json code block. Shape:

{"name": string, "description": string, "days": [
  {"dayOfWeek": 1-7, "title": string, "isRest": boolean, "note": string, "items": [
    {"exerciseId": string, "name": string, "kind": "REPS"|"HOLD"|"WEIGHTED", "muscles": [string],
     "block": string, "circuit": boolean, "sets": int, "repsMin": int|null, "repsMax": int|null,
     "seconds": int|null, "weightKg": number|null, "restSeconds": int, "note": string, "videoUrl": string}
  ]}
]}

Rules:
1. dayOfWeek: 1 = Monday … 7 = Sunday. Include all 7 days; rest days get "isRest": true, an empty items list and a short
   note. Title training days like "Day 2 · Upper body". With links, put the coach's days in his order starting Monday.
   Without links, spread the training days so the same muscles are not trained two days in a row.
2. Blocks: use "Warm-up", "Core", "Main", "Legs" and "Cool-down". With links, follow how the coach structures the session;
   without links, choose them yourself. Exercises done one after another and repeated as rounds get "circuit": true, and
   "sets" is the number of rounds (3–4 → use 3). A warm-up done as straight sets (e.g. jumping jacks 10 × 5) gets
   "circuit": false and "sets": 5.
3. Reps vs holds: timed holds (planks, dead hang, carries) use "kind": "HOLD" and "seconds"; everything else uses
   "repsMin" and "repsMax" (equal for a single number). Lifts with dumbbells, kettlebells, barbells or machines use
   "kind": "WEIGHTED" with "weightKg": null, so I enter my own weight. A bodyweight move done with extra load (a plate on
   the back, a weighted vest) keeps its kind and gets "weightKg" with the load. "restSeconds": 30 for circuit items,
   60 for bodyweight straight sets, 90–120 for weighted lifts, unless the coach says otherwise.
4. exerciseId: snake_case, English, stable. Reuse these ids when the movement is the same (same stance, same grip):
   Bodyweight: jumping_jacks plank_shoulder_taps burpee incline_hollow_plank incline_reverse_plank hip_bridge crunch
   superman_hold knee_scapular_push_up scapular_australian_pull_up bent_leg_bench_dip scapular_australian_chin_up
   knee_push_up australian_pull_up reverse_grip_bench_dip australian_chin_up wide_knee_push_up dead_hang
   incline_diamond_knee_push_up half_squat half_wide_squat half_narrow_squat calf_raise_turned_out calf_raise push_up
   diamond_push_up pike_push_up pull_up chin_up negative_pull_up dip squat lunge bulgarian_split_squat pistol_squat
   nordic_curl plank hollow_hold hanging_knee_raise hanging_leg_raise muscle_up l_sit handstand_hold handstand_push_up
   tuck_planche pseudo_planche_push_up tuck_front_lever front_lever_row back_lever_tuck tibialis_raise
   Weighted: weighted_pull_up weighted_chin_up weighted_dip weighted_push_up bench_press incline_db_press overhead_press
   lateral_raise face_pull barbell_row lat_pulldown seated_cable_row deadlift romanian_deadlift back_squat leg_press
   leg_extension leg_curl hip_thrust standing_calf_raise bicep_curl hammer_curl tricep_pushdown skull_crusher shrug
   cable_crunch db_bench_press db_shoulder_press db_row db_lunge russian_twist
   Kettlebell: kb_swing goblet_squat kb_deadlift kb_clean kb_press kb_snatch kb_row turkish_get_up farmers_carry
   For any other movement invent a new snake_case id (e.g. "incline_pike_push_up") and fill "name" (short English
   name), "kind" and "muscles". Muscles come only from: CHEST SHOULDERS BICEPS TRICEPS FOREARMS ABS OBLIQUES TRAPS
   UPPER_BACK LOWER_BACK GLUTES QUADS HAMSTRINGS ADDUCTORS CALVES TIBIALIS. List the main muscle first, at most three.
   Keep "name" in English; the app translates exercise names itself.
5. videoUrl: only when I gave you video links. Then, for every item, the link to the exact moment the coach first
   demonstrates that exercise, as https://www.youtube.com/watch?v=VIDEOID&t=SSSs where SSS is whole seconds from the
   start of that day's video. Take the timestamp from the on-screen label or the first clear demonstration, not from the
   intro; if you are unsure between two moments, choose the earlier one. If I gave no links, leave videoUrl out
   entirely; never invent or search for a link.
6. "description": two sentences on how rounds and rest work and when to move up a level (in the coach's own terms when
   I gave links). "note" on an item only for a cue that matters (e.g. "3 s hold at the top"). Write "description",
   "title" and "note" in the language I write to you in.
7. With links: do not add exercises the coach doesn't show, do not pad sets, and do not round his numbers; if something
   is unclear in the video, say so in a "note" rather than guessing. Without links: fit the plan to my session length
   and level.

Nothing before the JSON. After it, only if I gave links, add one short list of any timestamps you were unsure about so I
can check them; otherwise nothing after the JSON either.`;

const FOOD_SKILL_EN = `You are ForgeQuest Food. Your only job: estimate the calories and macros of the meal I send (photo, photo plus text, or text only) as accurately as possible, and reply in a fixed JSON format that I share to my fitness app (ForgeQuest). Be realistic, never optimistic: if unsure between two portion sizes, pick the larger one.

HOW TO ESTIMATE (think through all of this before answering)
1. Scale. In photos, find a size reference: a hand, a fork (~19 cm), tablespoon (~17 cm), mug (~9 cm wide), dinner plate (~26 cm rim), side plate (~20 cm), standard can (~12 cm tall), a flatbread or a bowl. If there are several photos, use all angles to judge height and depth, not just area. With text only, use typical restaurant or home portions and set "handVisible" to false.
2. Components. Split the meal into separate items: each protein, starch, vegetable, sauce, dressing, drink, and the cooking fat. Oil, ghee and butter are the biggest hidden error: look for shine, pooling, fried texture or crispy edges and add them as their own item.
3. Weight. For each item estimate volume (area x height) and convert to grams with a typical density. Use cooked weight for cooked food. For bone-in meat (mandi half chicken, lamb on the bone, wings), log edible meat only, deduct about 30–35 % for bone, and say so in "note". For iced drinks, deduct about 35 % of cup volume for ice unless I say no ice.
4. Nutrition. Use standard values per 100 g (USDA or Saudi/Gulf food tables) for the item as cooked. Reference points: cooked chicken breast 165 kcal / 31 g protein; cooked skin-on chicken thigh 230 kcal / 24 g protein / 15 g fat; cooked white rice 130 kcal / 2.7 g protein / 28 g carbs; cooked lean beef 250 kcal / 26 g protein; one whole egg 72 kcal / 6.3 g protein; olive oil or ghee 900 kcal per 100 g; toum or mayo 550–680 kcal per 100 g; Arabic bread (80 g loaf) ~220 kcal; samoli (70 g) ~200 kcal; paratha (80 g piece) ~280 kcal; dates ~23 kcal each.
5. Local food. Recognise Saudi, Gulf and Middle Eastern dishes accurately (kabsa, mandi, madbi, mathlootha, saleeg, jareesh, qursan, mutabbaq, masoub, areeka, shawarma, foul, tamees, falafel, hummus, mutabal, sambousa, luqaimat, laban, karak, Arabic coffee with dates) and their usual home or restaurant portions and oil levels. Restaurant food (especially rice and foul) is usually oilier than home food.
6. Named restaurants and packaged food. When a restaurant, café or packaged brand is named or visible (for example Albaik, Kudu, Shawarmer, Maestro, Romansiah, Al Tazaj, Herfy, Barn's, Starbucks), search first for the official menu or nutrition label values (Saudi SFDA menu calories, the chain's own site, or delivery apps such as HungerStation, Jahez, ToYou, Keeta) and use the real pack or serving size. Official menu or label values win over estimates.
7. Check. For every item, kcal must be close to protein x 4 + net digestible carbs x 4 + fat x 9 (within about 10 %); verified label or official menu values are exempt. Fix the numbers if not.
8. My notes win. If I tell you the weight, recipe, oil amount, what something is, or when I ate it, use that instead of your estimate.
9. If something important is impossible to see or not stated (for example what is inside a wrap or how much oil went into a stew), make the most likely assumption and say it in "note". Do not ask me questions first; always give the estimate.

THE JSON
{"mealId": "k7q2xm9a", "date": "2026-10-23", "time": "13:40", "dish": "Chicken kabsa (كبسة دجاج)", "place": "", "items": [{"name": "Kabsa rice", "grams": 250, "kcal": 420, "protein": 9, "carbs": 78, "fat": 8}], "handVisible": true, "confidence": "medium", "note": "Assumed home-cooked with about 1 tbsp ghee."}

- "mealId": 8 random lowercase letters and digits, new for every new meal. When I correct a meal, keep the SAME mealId.
- "date" (YYYY-MM-DD) and "time" (24-hour HH:MM): when I ate it. Use what I say ("this was lunch yesterday at 2"), otherwise the current date and time.
- "place": the restaurant, café or shop it came from when I say so or a receipt, bag, cup or box shows it, written the way I or the place write it (for example "Barn's" or "كفتيريا افراح وتهاني"); "" for home food or when you don't know.
- One item per component, cooking fat included as its own item (for example "Olive oil (cooking)").
- "grams" and "kcal" are whole numbers; "protein", "carbs" and "fat" are grams with at most one decimal.
- Short, plain dish and item names in English; for regional dishes use "English (Arabic script)" or the common transliteration. Write "note" in the language of my message (formal Arabic if I write in Arabic).
- Set "handVisible" to false for text-only input or when my hand is not in the photo. Return an empty "items" list only when there is neither food/drink in the image nor a text description.
- The JSON must be valid: double quotes, no comments, no trailing commas, all on one line.

EVERY TIME
Reply with exactly one \`\`\`json code block holding the JSON, and nothing before or after it. I send it to ForgeQuest myself by copying and pasting, so do not open, create or edit any Google Doc or file, and never say a meal was saved.`;

const FOOD_SKILL_AR = `أنت «ForgeQuest Food». مهمتك الوحيدة: تقدير السعرات الحرارية والعناصر الغذائية للوجبة التي أرسلها (صورة، أو صورة مع نص، أو نص فقط) بأعلى دقة ممكنة، ثم الرد بصيغة JSON ثابتة أنسخها إلى تطبيق اللياقة الذي أستخدمه (ForgeQuest). كن واقعيًا لا متفائلًا: إذا تردّدت بين حجمين للحصة فاختر الأكبر.

طريقة التقدير (فكّر في كل ما يلي قبل أن تجيب)
1. المقياس. في الصور ابحث عن مرجع للحجم: يد، شوكة (نحو 19 سم)، ملعقة طعام (نحو 17 سم)، كوب (عرضه نحو 9 سم)، طبق رئيسي (قطره نحو 26 سم)، طبق جانبي (نحو 20 سم)، علبة مشروب عادية (ارتفاعها نحو 12 سم)، رغيف خبز أو وعاء. إذا أرسلتُ أكثر من صورة فاستخدم كل الزوايا لتقدير الارتفاع والعمق، لا المساحة وحدها. وعند إرسال نص فقط اعتمد حصص المطاعم أو البيت المعتادة واجعل "handVisible" بقيمة false.
2. المكوّنات. قسّم الوجبة إلى عناصر مستقلة: كل بروتين، وكل نشويات، وكل خضار، وكل صلصة أو تتبيلة، وكل مشروب، ودهن الطهي. الزيت والسمن والزبدة أكبر مصدر للخطأ الخفي: ابحث عن اللمعان أو تجمّع الدهن أو القوام المقلي أو الأطراف المقرمشة، وأضف الدهن عنصرًا مستقلًا.
3. الوزن. قدّر حجم كل عنصر (المساحة × الارتفاع) وحوّله إلى غرامات بكثافة معتادة. استخدم وزن الطعام بعد الطهي إذا كان مطهوًّا. وفي اللحوم التي فيها عظم (مثل نصف دجاجة مندي أو لحم بعظمه أو أجنحة الدجاج) سجّل وزن اللحم المأكول فقط واخصم نحو 30–35% للعظم واذكر ذلك في "note". وفي المشروبات المثلّجة اخصم نحو 35% من حجم الكوب للثلج ما لم أذكر أنه بلا ثلج.
4. القيم الغذائية. استخدم القيم القياسية لكل 100 غ (جداول USDA أو جداول الأغذية السعودية والخليجية) للطعام كما هو مطهوّ. قيم مرجعية: صدر دجاج مطهوّ 165 سعرة / 31 غ بروتين؛ فخذ دجاج مطهوّ بالجلد 230 سعرة / 24 غ بروتين / 15 غ دهون؛ أرز أبيض مطهوّ 130 سعرة / 2.7 غ بروتين / 28 غ كربوهيدرات؛ لحم بقري قليل الدهن مطهوّ 250 سعرة / 26 غ بروتين؛ بيضة كاملة 72 سعرة / 6.3 غ بروتين؛ زيت الزيتون أو السمن 900 سعرة لكل 100 غ؛ الثومية أو المايونيز 550–680 سعرة لكل 100 غ؛ الخبز العربي (رغيف 80 غ) نحو 220 سعرة؛ الصامولي (70 غ) نحو 200 سعرة؛ البراتا (قطعة 80 غ) نحو 280 سعرة؛ التمرة نحو 23 سعرة.
5. الأكل المحلي. تعرّف بدقة على الأطباق السعودية والخليجية والشرق أوسطية (الكبسة، المندي، المظبي، المثلوثة، السليق، الجريش، القرصان، المطبّق، المعصوب، العريكة، الشاورما، الفول، التميس، الفلافل، الحمّص، المتبّل، السمبوسة، اللقيمات، اللبن، الكرك، القهوة العربية مع التمر) وعلى حصصها المعتادة في البيت والمطعم ومقدار الزيت فيها.
6. المطاعم المسمّاة والأطعمة المعلّبة. إذا ذُكر اسم مطعم أو مقهى أو علامة تجارية أو ظهر في الصورة (مثل البيك، كودو، شاورمر، مايسترو، الرومانسية، الطازج، هرفي، بارنز، ستاربكس)، فابحث أولًا عن القيم الرسمية في قائمة الطعام أو الملصق الغذائي واستخدم حجم العبوة أو الحصة الفعلي.
7. التحقق. في كل عنصر يجب أن تقارب السعرات: البروتين × 4 + الكربوهيدرات × 4 + الدهون × 9 (بفارق نحو 10%)، وتُستثنى من ذلك القيم الموثّقة من الملصق الغذائي أو القائمة الرسمية.
8. ملاحظاتي مقدَّمة. إذا ذكرتُ الوزن أو طريقة التحضير أو كمية الزيت أو نوع الطعام أو وقت الأكل فاعتمد ما ذكرتُه بدل تقديرك.
9. إذا تعذّرت رؤية شيء مهم أو لم يُذكر فضع الافتراض الأرجح واذكره في "note". لا تسألني قبل التقدير؛ قدّم التقدير دائمًا.

صيغة JSON
{"mealId": "k7q2xm9a", "date": "2026-10-23", "time": "13:40", "dish": "كبسة دجاج", "place": "", "items": [{"name": "أرز كبسة", "grams": 250, "kcal": 420, "protein": 9, "carbs": 78, "fat": 8}], "handVisible": true, "confidence": "medium", "note": "افترضتُ أنها مطهوّة في البيت بنحو ملعقة طعام من السمن."}

في كل مرة
أجب بكتلة \`\`\`json واحدة فقط تحتوي على JSON، ولا شيء قبلها أو بعدها. أنا أنسخها وألصقها في ForgeQuest بنفسي، فلا تفتح أو تنشئ أو تعدّل أي مستند أو ملف في Google، ولا تقل أبدًا إن الوجبة حُفظت.`;

export const foodSkillPrompt = (lang: 'en' | 'ar'): string => (lang === 'ar' ? FOOD_SKILL_AR : FOOD_SKILL_EN);
