// Converts Android string resources into flat JSON for the web app.
// Run: npm run export:android   (plain Node ESM, no dependencies)
import { readFileSync, writeFileSync, mkdirSync, readdirSync, copyFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const webDir = join(here, '..');
const resDir = join(webDir, '..', 'app', 'src', 'main', 'res');
const outDir = join(webDir, 'src', 'i18n');

const ENTITIES = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" };

function decodeEntity(name) {
  if (name[0] === '#') {
    const code = name[1] === 'x' || name[1] === 'X' ? parseInt(name.slice(2), 16) : parseInt(name.slice(1), 10);
    return Number.isNaN(code) ? `&${name};` : String.fromCodePoint(code);
  }
  return name in ENTITIES ? ENTITIES[name] : `&${name};`;
}

// Android text rules: collapse whitespace outside "quotes", process backslash escapes, decode entities.
function androidText(raw) {
  let s = raw.replace(/<\/?xliff:g\b[^>]*>/g, '');
  let out = '';
  let quoted = false;
  let pendingSpace = false;
  const push = (t) => {
    if (pendingSpace && out !== '') out += ' ';
    pendingSpace = false;
    out += t;
  };
  let i = 0;
  while (i < s.length) {
    if (s.startsWith('<![CDATA[', i)) {
      const end = s.indexOf(']]>', i);
      push(s.slice(i + 9, end));
      i = end + 3;
      continue;
    }
    const c = s[i];
    if (c === '\\') {
      const n = s[i + 1];
      if (n === 'n') push('\n');
      else if (n === 't') push('\t');
      else if (n === 'u') {
        push(String.fromCharCode(parseInt(s.slice(i + 2, i + 6), 16)));
        i += 4;
      } else push(n);
      i += 2;
      continue;
    }
    if (c === '"') {
      quoted = !quoted;
      i++;
      continue;
    }
    if (c === '&') {
      const m = /^&([^;\s]+);/.exec(s.slice(i, i + 12));
      if (m) {
        push(decodeEntity(m[1]));
        i += m[0].length;
        continue;
      }
    }
    if (!quoted && /\s/.test(c)) {
      pendingSpace = true;
      i++;
      continue;
    }
    push(c);
    i++;
  }
  return out;
}

// Only the conversions Android strings use (s, d, f). A bare "1% of" stays literal, as on Android.
const PH = /%(?:(\d+)\$)?[-#+0,(]*\d*(?:\.\d+)?[sdf%]/g;

function placeholders(text) {
  let n = 0;
  return text.replace(PH, (m, num) => {
    if (m === '%%') return '%';
    if (num) return `{${num}}`;
    n += 1;
    return `{${n}}`;
  });
}

const convert = (raw, formatted = true) => {
  const t = androidText(raw.trim());
  return formatted ? placeholders(t) : t; // formatted="false": % is literal
};

function parseFile(file) {
  const xml = readFileSync(file, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
  const strings = {};
  const nonTranslatable = [];
  const html = [];
  const re = /<(string|plurals)\s+([^>]*?)(\/>|>([\s\S]*?)<\/\1>)/g;
  let m;
  while ((m = re.exec(xml))) {
    const [, kind, attrs, , body = ''] = m;
    const name = /\bname="([^"]*)"/.exec(attrs)?.[1];
    if (!name) continue;
    if (/\btranslatable="false"/.test(attrs)) nonTranslatable.push(name);
    const formatted = !/\bformatted="false"/.test(attrs);
    let value;
    if (kind === 'string') {
      value = convert(body, formatted);
      if (/<\/?[a-zA-Z][^>]*>/.test(value)) html.push(name);
    } else {
      value = {};
      const ire = /<item\s+quantity="(\w+)"\s*>([\s\S]*?)<\/item>/g;
      let im;
      while ((im = ire.exec(body))) {
        value[im[1]] = convert(im[2], formatted);
        if (/<\/?[a-zA-Z][^>]*>/.test(value[im[1]])) html.push(name);
      }
    }
    strings[name] = value;
  }
  return { strings, nonTranslatable, html };
}

const sortKeys = (o) => Object.fromEntries(Object.keys(o).sort().map((k) => [k, o[k]]));
const counts = (o) => ({
  strings: Object.values(o).filter((v) => typeof v === 'string').length,
  plurals: Object.values(o).filter((v) => typeof v !== 'string').length,
});

const en = parseFile(join(resDir, 'values', 'strings.xml'));
const ar = parseFile(join(resDir, 'values-ar', 'strings.xml'));
const skipAr = new Set(ar.nonTranslatable);
const arStrings = Object.fromEntries(Object.entries(ar.strings).filter(([k]) => !skipAr.has(k)));

mkdirSync(join(outDir, 'prompts'), { recursive: true });
writeFileSync(join(outDir, 'en.json'), JSON.stringify(sortKeys(en.strings), null, 2) + '\n');
writeFileSync(join(outDir, 'ar.json'), JSON.stringify(sortKeys(arStrings), null, 2) + '\n');

const rawDir = join(resDir, 'raw');
const prompts = readdirSync(rawDir).filter((f) => f.endsWith('.txt'));
for (const f of prompts) copyFileSync(join(rawDir, f), join(outDir, 'prompts', f));

const enKeys = Object.keys(en.strings);
const arKeys = Object.keys(arStrings);
const missing = enKeys.filter((k) => !(k in arStrings) && !en.nonTranslatable.includes(k)).sort();
const extra = arKeys.filter((k) => !(k in en.strings)).sort();
const c1 = counts(en.strings);
const c2 = counts(arStrings);
console.log(`EN: ${c1.strings} strings, ${c1.plurals} plurals (${en.nonTranslatable.length} translatable=false)`);
console.log(`AR: ${c2.strings} strings, ${c2.plurals} plurals`);
console.log(`Prompts copied: ${prompts.join(', ')}`);
console.log(`Keys in EN missing from AR (${missing.length}): ${missing.join(', ') || '-'}`);
console.log(`Keys in AR not in EN (${extra.length}): ${extra.join(', ') || '-'}`);
console.log(`Keys with inline HTML: ${[...new Set([...en.html, ...ar.html])].join(', ') || '-'}`);
