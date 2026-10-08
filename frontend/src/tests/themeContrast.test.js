// WCAG contrast checks read the actual CSS, not copied theme values.
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
const css = readFileSync('src/styles/index.css','utf8');
/** @param {string} hex */
function luminance(hex) {
  const channels = hex.replace('#','').match(/../g)?.map(pair=>parseInt(pair,16)/255) || [];
  const [r,g,b] = channels.map(c=>c<=0.04045?c/12.92:((c+0.055)/1.055)**2.4);
  return .2126*r+.7152*g+.0722*b;
}
/** @param {string} a @param {string} b */
function contrast(a,b) { const x=luminance(a), y=luminance(b); return (Math.max(x,y)+.05)/(Math.min(x,y)+.05); }
for (const [index,name] of ['light','dark'].entries()) {
  const block = [...css.matchAll(/:root(?:\[data-theme="dark"\])?\s*\{([^}]+)\}/g)][index]?.[1] || '';
  const palette = Object.fromEntries([...block.matchAll(/--([a-z-]+):\s*(#[0-9a-f]{6})/g)].map(m=>[m[1],m[2]]));
  describe(name+' original Rockey palette', () => {
    it.each([['text','bg'],['text','surface'],['muted','surface'],['danger','surface'],['on-accent','accent']])('%s on %s has 4.5:1 text contrast',(a,b)=>{
      expect(contrast(palette[a],palette[b])).toBeGreaterThanOrEqual(4.5);
    });
    it.each(['surface','bg'])('focus outline on %s has 3:1 contrast',background=>{
      expect(contrast(palette.focus,palette[background])).toBeGreaterThanOrEqual(3);
    });
  });
}
