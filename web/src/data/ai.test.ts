import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DEFAULT_SETTINGS } from './settings';

const settings = vi.hoisted(() => ({ value: {} as Record<string, unknown> }));
vi.mock('./settings', async (orig) => ({ ...(await orig<typeof import('./settings')>()), loadSettings: async () => settings.value }));

import {
  AiError, FALLBACK_FAST, FALLBACK_LITE, FALLBACK_STABLE_FLASH, aiErrorKey, aiTuning, extractText, geminiGenerate, groqChat,
  imagePart, modelsFor, pickGroqModel, resetAiState, retryAfterMs, scanModelsFor, textPart,
} from './ai';

const ok = (text: string) => new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: 'hmm', thought: true }, { text }] } }] }), { status: 200 });
const fail = (status: number, body = '{}') => new Response(body, { status });
let calls: { url: string; init: RequestInit }[];
function mockFetch(...responses: (Response | Error)[]) {
  calls = [];
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    const r = responses.shift() ?? fail(500);
    if (r instanceof Error) throw r;
    return r;
  }));
}
const body = (i: number) => JSON.parse(calls[i].init.body as string);
const modelOf = (i: number) => /models\/([^:]+):/.exec(calls[i].url)![1];

beforeEach(() => {
  settings.value = { ...DEFAULT_SETTINGS, apiKey: 'KEY', groqKey: 'GKEY' };
  aiTuning.busyRetryMs = 0;
  resetAiState();
});
afterEach(() => vi.unstubAllGlobals());

describe('model choice', () => {
  const s = { fastModel: '', smartModel: '', liteModel: '' };
  it('defaults to Flash-Lite with Flash fallbacks', () => {
    expect(modelsFor('auto', s)).toEqual([FALLBACK_LITE, FALLBACK_FAST, FALLBACK_STABLE_FLASH]);
    expect(modelsFor('flash', s)[0]).toBe(FALLBACK_FAST);
    expect(modelsFor('pro', { ...s, smartModel: 'gemini-3-pro' })[0]).toBe('gemini-3-pro');
  });
  it('scans start on Flash', () => expect(scanModelsFor(s)[0]).toBe(FALLBACK_FAST));
});

describe('helpers', () => {
  it('reads retry delays', () => {
    expect(retryAfterMs('Please retry in 12h7m14s')).toBe(((12 * 60 + 7) * 60 + 14) * 1000);
    expect(retryAfterMs('retry in 2.5s')).toBe(10_000);
    expect(retryAfterMs('nothing')).toBe(60_000);
  });
  it('extracts text and skips thoughts', () => {
    expect(extractText(JSON.stringify({ candidates: [{ content: { parts: [{ text: 'x', thought: true }, { text: ' hi ' }] } }] }))).toBe('hi');
    expect(extractText('{}')).toBeNull();
    expect(extractText('nope')).toBeNull();
  });
  it('picks Groq models', () => {
    expect(pickGroqModel(['llama-3.1-8b-instant', 'llama-3.3-70b-versatile'])).toBe('llama-3.3-70b-versatile');
    expect(pickGroqModel(['whisper'])).toBe('llama-3.3-70b-versatile');
  });
  it('maps errors to strings', () => {
    expect(aiErrorKey(new AiError('quota')).key).toBe('ai_err_rate_limited');
    expect(aiErrorKey(new AiError('server', '', 500)).args).toEqual([500]);
  });
});

describe('geminiGenerate', () => {
  it('sends the key in a header, text and image parts, and JSON mode', async () => {
    mockFetch(ok('{"a":1}'));
    const out = await geminiGenerate({ system: 'sys', parts: [textPart('hi'), imagePart('QUJD')], json: true });
    expect(out).toBe('{"a":1}');
    expect(calls[0].url).not.toContain('KEY');
    expect((calls[0].init.headers as Record<string, string>)['x-goog-api-key']).toBe('KEY');
    expect(modelOf(0)).toBe(FALLBACK_LITE);
    const b = body(0);
    expect(b.systemInstruction.parts[0].text).toBe('sys');
    expect(b.contents[0].parts[1].inlineData).toEqual({ mimeType: 'image/jpeg', data: 'QUJD' });
    expect(b.generationConfig.responseMimeType).toBe('application/json');
  });
  it('throws no_key without calling', async () => {
    settings.value = { ...DEFAULT_SETTINGS };
    mockFetch();
    await expect(geminiGenerate({ parts: [textPart('x')] })).rejects.toMatchObject({ kind: 'no_key' });
    expect(calls).toHaveLength(0);
  });
  it('stops on a bad key', async () => {
    mockFetch(fail(400, '{"error":{"status":"INVALID_ARGUMENT","message":"API key not valid"}}'));
    await expect(geminiGenerate({ parts: [textPart('x')] })).rejects.toMatchObject({ kind: 'bad_key' });
    expect(calls).toHaveLength(1);
  });
  it('falls back to the next model on 429, 404 and 500', async () => {
    mockFetch(fail(429, 'retry in 30s'), fail(404), ok('done'));
    expect(await geminiGenerate({ parts: [textPart('x')] })).toBe('done');
    expect(calls.map((_, i) => modelOf(i))).toEqual([FALLBACK_LITE, FALLBACK_FAST, FALLBACK_STABLE_FLASH]);
  });
  it('retries a busy model once', async () => {
    mockFetch(fail(503), ok('ok'));
    expect(await geminiGenerate({ parts: [textPart('x')] })).toBe('ok');
    expect(modelOf(0)).toBe(modelOf(1));
  });
  it('tries the next thinking switch after a 400', async () => {
    mockFetch(fail(400), ok('ok'));
    await geminiGenerate({ parts: [textPart('x')], lowThinking: true });
    expect(body(0).generationConfig.thinkingConfig).toEqual({ thinkingLevel: 'low' });
    expect(body(1).generationConfig.thinkingConfig).toEqual({ thinkingBudget: 1024 });
  });
  it('reports quota when every model is out, and offline when fetch throws', async () => {
    mockFetch(fail(429), fail(429), fail(429));
    await expect(geminiGenerate({ parts: [textPart('x')] })).rejects.toMatchObject({ kind: 'quota' });
    resetAiState();
    mockFetch(new TypeError('Failed to fetch'));
    await expect(geminiGenerate({ parts: [textPart('x')] })).rejects.toMatchObject({ kind: 'offline' });
  });
  it('reports an empty answer as blocked', async () => {
    mockFetch(new Response('{"candidates":[{"finishReason":"SAFETY"}]}', { status: 200 }));
    await expect(geminiGenerate({ parts: [textPart('x')] })).rejects.toMatchObject({ kind: 'blocked' });
  });
});

describe('groqChat', () => {
  it('posts OpenAI-style messages with a bearer key', async () => {
    mockFetch(new Response(JSON.stringify({ choices: [{ message: { content: ' hello ' } }] }), { status: 200 }));
    expect(await groqChat({ messages: [{ role: 'user', content: 'hi' }] })).toBe('hello');
    expect(calls[0].url).toBe('https://api.groq.com/openai/v1/chat/completions');
    expect((calls[0].init.headers as Record<string, string>).authorization).toBe('Bearer GKEY');
    expect(body(0).messages[0]).toEqual({ role: 'user', content: 'hi' });
  });
  it('classifies errors', async () => {
    mockFetch(fail(401));
    await expect(groqChat({ messages: [] })).rejects.toMatchObject({ kind: 'bad_key' });
    mockFetch(fail(429));
    await expect(groqChat({ messages: [] })).rejects.toMatchObject({ kind: 'quota' });
  });
});
