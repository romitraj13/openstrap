// OpenHealth app icon, option D (dot matrix): the full artwork, a small-size cut, the iOS 18 tinted
// artwork, every PNG an Xcode AppIcon set needs (iPhone, iPad, App Store), and a comparison sheet.
const { Resvg } = require('@resvg/resvg-js');
const fs = require('fs');
const { PNG } = require('pngjs');
// resvg writes RGBA; app icons must be opaque RGB (no alpha channel), so re-encode as colour type 2.
const opaque = (buf) => { const im = PNG.sync.read(buf); return PNG.sync.write(im, { colorType: 2, inputHasAlpha: true }); };
const OUT = process.argv[2] || 'out';
fs.mkdirSync(`${OUT}/AppIcon.appiconset`, { recursive: true });

const BG = '#0B0D10', DOT = '#FF4D5E';
const GLYPH = {
  O: ['.###.', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
  H: ['#...#', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
};
const blur = (id, sd) => `<filter id="${id}" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="${sd}"/></filter>`;

// variant: 'full' (lit dots on a faint unlit grid), 'small' (no grid, fatter dots, bigger period),
// 'tinted' (white on black, no colour: iOS tints it).
function artwork(variant) {
  const cols = 13, rows = 7;
  const pitch = variant === 'full' ? 58 : 60;
  const d = variant === 'full' ? 44 : 52;
  const w = (cols - 1) * pitch + d, h = (rows - 1) * pitch + d;
  const x0 = (1024 - w) / 2 + d / 2, y0 = (1024 - h) / 2 + d / 2;
  let s = `<rect width="1024" height="1024" fill="${BG === BG && variant === 'tinted' ? '#000000' : BG}"/>`;
  if (variant === 'full')
    for (let r = 0; r < rows; r++) for (let c = 0; c < cols; c++)
      s += `<circle cx="${x0 + c * pitch}" cy="${y0 + r * pitch}" r="${d / 2}" fill="#FFFFFF" opacity="0.07"/>`;
  ['O', 'H'].forEach((ch, i) => GLYPH[ch].forEach((line, r) => [...line].forEach((v, c) => {
    if (v === '#') s += `<circle cx="${x0 + (i * 6 + c) * pitch}" cy="${y0 + r * pitch}" r="${d / 2}" fill="#FFFFFF"/>`;
  })));
  const px = x0 + 12 * pitch, py = y0 + 6 * pitch, pr = variant === 'full' ? d / 2 : d / 2 + 6;
  const dot = variant === 'tinted' ? '#FFFFFF' : DOT;
  if (variant !== 'tinted') s += `<circle cx="${px}" cy="${py}" r="${pr + 14}" fill="${dot}" opacity="0.6" filter="url(#h)"/>`;
  s += `<circle cx="${px}" cy="${py}" r="${pr}" fill="${dot}"/>`;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024"><defs>${blur('h', 14)}</defs>${s}</svg>`;
}

const png = (svg, px) => opaque(new Resvg(svg, { fitTo: { mode: 'width', value: px } }).render().asPng());
const art = { full: artwork('full'), small: artwork('small'), tinted: artwork('tinted') };
for (const [k, v] of Object.entries(art)) {
  fs.writeFileSync(`${OUT}/openhealth-icon-${k}.svg`, v);
  fs.writeFileSync(`${OUT}/openhealth-icon-${k}-1024.png`, png(v, 1024));
}

// Legacy per-size AppIcon set (iPhone + iPad + App Store). Sizes up to 87 px use the small cut,
// larger ones the full artwork. Every image is opaque, as iOS requires.
const SMALL_MAX = 87;
const entries = [
  ['iphone', 20, 2], ['iphone', 20, 3], ['iphone', 29, 2], ['iphone', 29, 3], ['iphone', 40, 2], ['iphone', 40, 3],
  ['iphone', 60, 2], ['iphone', 60, 3],
  ['ipad', 20, 1], ['ipad', 20, 2], ['ipad', 29, 1], ['ipad', 29, 2], ['ipad', 40, 1], ['ipad', 40, 2],
  ['ipad', 76, 1], ['ipad', 76, 2], ['ipad', 83.5, 2],
  ['ios-marketing', 1024, 1],
];
const images = [];
for (const [idiom, pt, scale] of entries) {
  const px = Math.round(pt * scale);
  const file = `AppIcon-${px}.png`;
  if (!fs.existsSync(`${OUT}/AppIcon.appiconset/${file}`))
    fs.writeFileSync(`${OUT}/AppIcon.appiconset/${file}`, png(px <= SMALL_MAX ? art.small : art.full, px));
  images.push({ filename: file, idiom, scale: `${scale}x`, size: `${pt}x${pt}` });
}
fs.writeFileSync(`${OUT}/AppIcon.appiconset/Contents.json`,
  JSON.stringify({ images, info: { author: 'xcode', version: 1 } }, null, 2) + '\n');

// Comparison: full artwork scaled down vs the small cut, masked like iOS, on dark and light.
const b64 = (svg, px) => png(svg, px).toString('base64');
const sizes = [58, 87, 120, 180];
let defs = '', body = `<rect width="1120" height="560" fill="#000"/><rect y="280" width="1120" height="280" fill="#E9E6E1"/>`;
let n = 0;
const tile = (svg, x, y, s) => {
  defs += `<clipPath id="c${n}"><rect x="${x}" y="${y}" width="${s}" height="${s}" rx="${s * 0.224}"/></clipPath>`;
  body += `<image href="data:image/png;base64,${b64(svg, s)}" x="${x}" y="${y}" width="${s}" height="${s}" clip-path="url(#c${n++})"/>`;
};
[['full', 30], ['small', 590]].forEach(([k, x]) => {
  body += `<text x="${x}" y="30" font-size="20" fill="#fff" font-family="sans-serif">${k === 'full' ? 'Full artwork, scaled down' : 'Small cut (used up to 87 px)'}</text>`;
  let cx = x;
  sizes.forEach((s) => { tile(art[k], cx, 60, s); tile(art[k], cx, 330, s); cx += s + 20; });
});
const sheet = `<svg xmlns="http://www.w3.org/2000/svg" width="1120" height="560"><defs>${defs}</defs>${body}</svg>`;
fs.writeFileSync(`${OUT}/openhealth-icon-D-sizes.png`, new Resvg(sheet, { font: { loadSystemFonts: true } }).render().asPng());
console.log(`${images.length} entries, ${fs.readdirSync(`${OUT}/AppIcon.appiconset`).length - 1} PNG files`);
