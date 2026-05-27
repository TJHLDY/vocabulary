const fs = require('fs');
const path = require('path');
const assert = require('assert');

const htmlPath = path.resolve(__dirname, '../../main/resources/static/index.html');
const html = fs.readFileSync(htmlPath, 'utf8');

function extractFunction(name) {
  const start = html.indexOf(`function ${name}`);
  assert.notStrictEqual(start, -1, `${name} should exist`);
  const braceStart = html.indexOf('{', start);
  let depth = 0;
  for (let i = braceStart; i < html.length; i++) {
    const ch = html[i];
    if (ch === '{') depth++;
    if (ch === '}') depth--;
    if (depth === 0) return html.slice(start, i + 1);
  }
  throw new Error(`Could not extract ${name}`);
}

const incomingSource = extractFunction('pkOnInviteIncoming');
assert.ok(!incomingSource.includes('pkCloseArena()'), 'incoming invite must not close and disconnect the PK socket');
assert.ok(incomingSource.includes('pkHideArenaOverlay'), 'incoming invite should only hide the arena overlay');

assert.match(html, /function pkStartInviteButtonCooldown/);
assert.match(html, /cooldownSeconds/);
assert.match(html, /pkSendInvite\(/);

assert.match(html, /id="pkArenaRefresh"/, 'arena should expose a manual refresh button');
assert.match(html, /function pkRenderRankBadge/, 'arena should render rank badges for invite targets');
assert.match(html, /pk-crown/, 'top-ranked invite targets should show crown treatment');
assert.match(html, /pk-arena-me/, 'arena should show current user competitive info');

console.log('pk invite ui handling ok');


