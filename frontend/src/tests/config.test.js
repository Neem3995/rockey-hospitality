import { afterEach, describe, expect, it, vi } from 'vitest';
import { HOTEL_TIME_ZONE } from '../utils/hotelTime.js';

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
    expect(HOTEL_TIME_ZONE).toBe('America/New_York');
  });
});
