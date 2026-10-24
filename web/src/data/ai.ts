// Shared AI client for Food (scan) now and the Coach later. Port of Android data/ai/GeminiClient.kt, GroqClient.kt
// and ModelStore.kt, minus streaming and the weekly model discovery (the web uses the "-latest" aliases).
// The person's own keys come from settings and travel only in request headers (x-goog-api-key / Authorization).
// Nothing here logs: error `detail` holds the HTTP status and a trimmed body, never a key.
import { loadSettings, type Settings } from './settings';

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

/** Cheap key check for Profile: lists one model. Resolves to nothing, throws AiError. */
export async function geminiCheckKey(apiKey: string): Promise<void> {
  const key = apiKey.trim();
  if (!key) throw new AiError('no_key');
  await send(`${GEMINI_BASE}/models?pageSize=1`, { headers: { 'x-goog-api-key': key } }, aiTuning.quickTimeoutMs, undefined, 'models');
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
      body: JSON.stringify({ model, messages: o.messages, temperature: o.temperature ?? 0.5, max_tokens: o.maxTokens ?? 1500 }),
    },
    15_000, o.signal, `groq ${model}`,
  );
  let text = '';
  try { text = String(JSON.parse(body)?.choices?.[0]?.message?.content ?? ''); } catch { /* handled below */ }
  text = text.trim();
  if (!text) throw new AiError('blocked', `groq ${model}: no text`);
  return text;
}
