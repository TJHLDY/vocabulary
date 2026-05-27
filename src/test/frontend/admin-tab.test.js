const fs = require('fs');
const path = require('path');
const vm = require('vm');
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

const sandbox = {};
vm.createContext(sandbox);
vm.runInContext([
  extractFunction('normalizeRole'),
  extractFunction('canSeeAdminTab')
].join('\n'), sandbox);

assert.strictEqual(sandbox.normalizeRole(' ADMIN '), 'admin');
assert.strictEqual(sandbox.normalizeRole(null), 'user');
assert.strictEqual(sandbox.canSeeAdminTab('admin'), true);
assert.strictEqual(sandbox.canSeeAdminTab('ADMIN'), true);
assert.strictEqual(sandbox.canSeeAdminTab(' tester '), false);
assert.strictEqual(sandbox.canSeeAdminTab('user'), false);

assert.match(html, /function refreshAdminTabVisibility/);
assert.match(html, /\/api\/user\/me/);
assert.match(html, /refreshAdminTabVisibility/);

console.log('admin tab role handling ok');
