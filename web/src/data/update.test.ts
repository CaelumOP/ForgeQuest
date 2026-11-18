import { describe, expect, it } from 'vitest';
import { compareVersions, parseLatestVersion, resolveWhatsNew } from './update';

describe('What\'s New version check', () => {
  it('compares dotted version strings', () => {
    expect(compareVersions('4.2.0', '4.1.9')).toBeGreaterThan(0);
    expect(compareVersions('4.2.0', '4.2.0')).toBe(0);
    expect(compareVersions('4.1.0', '4.2.0')).toBeLessThan(0);
    expect(compareVersions('4.10.0', '4.2.0')).toBeGreaterThan(0);
  });

  it('parses version.json payload', () => {
    const parsed = parseLatestVersion('{"versionCode":42,"versionName":"4.2.0","notes":"• Fix A"}');
    expect(parsed).toEqual({ versionName: '4.2.0', notes: '• Fix A' });
    expect(parseLatestVersion('bad')).toBeNull();
  });

  it('does not show on first install or same version', async () => {
    let fetched = 0;
    const fetcher = async () => { fetched++; return { versionName: '4.2.0', notes: 'Notes' }; };

    expect(await resolveWhatsNew(null, '4.2.0', fetcher)).toEqual({ nextSeen: '4.2.0', whatsNew: null });
    expect(await resolveWhatsNew('4.2.0', '4.2.0', fetcher)).toEqual({ nextSeen: '4.2.0', whatsNew: null });
    expect(fetched).toBe(0);
  });

  it('shows once after an update when version.json matches and has notes', async () => {
    const match = await resolveWhatsNew('4.1.0', '4.2.0', async () => ({ versionName: '4.2.0', notes: '• New stuff' }));
    expect(match).toEqual({ nextSeen: '4.2.0', whatsNew: { versionName: '4.2.0', notes: '• New stuff' } });

    const mismatch = await resolveWhatsNew('4.1.0', '4.2.0', async () => ({ versionName: '4.3.0', notes: 'Future' }));
    expect(mismatch).toEqual({ nextSeen: '4.2.0', whatsNew: null });

    const blank = await resolveWhatsNew('4.1.0', '4.2.0', async () => ({ versionName: '4.2.0', notes: '   ' }));
    expect(blank).toEqual({ nextSeen: '4.2.0', whatsNew: null });

    const offline = await resolveWhatsNew('4.1.0', '4.2.0', async () => null);
    expect(offline).toEqual({ nextSeen: '4.2.0', whatsNew: null });
  });
});
