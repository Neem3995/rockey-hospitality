import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const css = readFileSync(resolve(process.cwd(), 'src/styles/index.css'), 'utf8');
/** @param {string} selector */
function tokens(selector) {
  const block = css.slice(css.indexOf(selector)).split('}')[0];
  return Object.fromEntries([...block.matchAll(/--([\w-]+):\s*(#[\da-f]{6});/gi)].map((match) => [match[1], match[2]]));
}
/** @param {string} hex */
function luminance(hex) {
  const [r, g, b] = [1, 3, 5].map((offset) => {
    const value = parseInt(hex.slice(offset, offset + 2), 16) / 255;
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
/** @param {string} foreground @param {string} background */
function contrast(foreground, background) {
  const a = luminance(foreground);
  const b = luminance(background);
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

describe.each(['light', 'dark'])('%s semantic palette contrast', (theme) => {
  const palette = tokens(theme === 'light' ? ':root {' : ':root[data-theme="dark"] {');
  it('keeps body, secondary, accent and status text at least 4.5:1 on all content surfaces', () => {
    for (const text of ['text', 'muted-text', 'primary', 'accent', 'success', 'warning', 'danger']) {
      for (const surface of ['background', 'surface', 'surface-elevated', 'accent-surface']) {
        expect(contrast(palette[text], palette[surface]), `${text} on ${surface}`).toBeGreaterThanOrEqual(4.5);
      }
    }
  });
  it('keeps primary and danger button text at least 4.5:1 in normal/hover/active states', () => {
    for (const state of ['primary', 'primary-hover', 'primary-active']) {
      expect(contrast(palette['on-primary'], palette[state]), state).toBeGreaterThanOrEqual(4.5);
    }
    for (const state of ['danger', 'danger-hover']) {
      expect(contrast(palette['on-danger'], palette[state]), state).toBeGreaterThanOrEqual(4.5);
    }
    expect(contrast(palette['disabled-text'], palette['disabled-surface'])).toBeGreaterThanOrEqual(4.5);
  });
  it('keeps borders/focus at least 3:1 including disabled inputs and modal surfaces', () => {
    for (const token of ['border', 'input-border', 'focus']) {
      for (const surface of ['background', 'surface', 'surface-elevated', 'disabled-surface', 'accent-surface']) {
        expect(contrast(palette[token], palette[surface]), `${token} on ${surface}`).toBeGreaterThanOrEqual(3);
      }
    }
  });
});

it('keeps literal colors in tokens and provides mutually exclusive logo selectors', () => {
  const componentCss = css.slice(css.indexOf('* {'));
  expect(componentCss).not.toMatch(/#[\da-f]{3,8}\b/i);
  expect(componentCss).toContain('.logo-dark { display: none; }');
  expect(componentCss).toContain(':root[data-theme="dark"] .logo-light { display: none; }');
  expect(componentCss).toContain(':root[data-theme="dark"] .logo-dark { display: block; }');
  expect(componentCss).toContain('@media (prefers-reduced-motion: reduce)');
});
