import { afterEach, describe, expect, it, vi } from 'vitest';
import { hotelNow } from '../services/housekeepingService.js';

afterEach(() => { vi.unstubAllEnvs(); vi.resetModules(); });

describe('Public frontend configuration', () => {
  it('defaults to the existing API location without making an API request', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '');
    const { API_BASE_URL } = await import('../services/config.js');
    expect(API_BASE_URL).toBe('http://localhost:8080/api');
  });

  it('uses the configured public API location', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://example.test/api');
    const { API_BASE_URL } = await import('../services/config.js');
    expect(API_BASE_URL).toBe('https://example.test/api');
  });

  it('fixes the approved display zone without converting backend timestamps', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-01T12:30:00Z'));
    expect(hotelNow()).toBe('2026-07-01T08:30:00');
    vi.useRealTimers();
  });
});
