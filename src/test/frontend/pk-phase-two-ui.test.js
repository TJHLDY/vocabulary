const fs = require('fs');
const path = require('path');
const assert = require('assert');

const htmlPath = path.resolve(__dirname, '../../main/resources/static/index.html');
const html = fs.readFileSync(htmlPath, 'utf8');

assert.match(html, /pk-brush-slash\.png/, 'settlement should use the brush slash asset');
assert.match(html, /pkDeclarationOverlay/, 'arena should include victory declaration settings');
assert.match(html, /firstWinDeclaration/, 'declaration form should include first win declaration');
assert.match(html, /赛场回顾/, 'arena should expose recent match review');
assert.match(html, /pkLoadRecentMatches/, 'frontend should load recent PK matches');
assert.match(html, /pkOpenReplay/, 'frontend should open replay from recent matches');
assert.match(html, /winnerDeclaration/, 'settlement should render winner declaration payload');
assert.match(html, /第 1\/20 题/, 'PK static progress text should reflect 20 questions');
assert.match(html, /pkRequestRematch/, 'settlement should expose rematch request action');
assert.match(html, /pk\.rematch\.accept/, 'frontend should accept BO3 rematch requests');
assert.match(html, /BO3/, 'settlement should show BO3 series score');
assert.match(html, /function pkCurrentUserId\(\)/, 'PK should resolve the current user id through one helper');
assert.match(html, /payload\.userId \|\| payload\.sub \|\| payload\.id/, 'PK current user id should fall back to JWT subject');
assert.ok(!html.includes('decodeJwtPayload(token).userId'), 'PK should not assume JWT contains userId claim');
assert.match(html, /id="pkMobileResult"/, 'match overlay should include a dedicated mobile settlement container');
assert.match(html, /pk-mobile-result/, 'mobile settlement should have dedicated result styles');
assert.match(html, /pk-mobile-actions/, 'mobile settlement should keep actions in a dedicated bottom area');
assert.match(html, /pkMobileRematchNotice/, 'mobile settlement should expose a visible rematch notice target');
assert.match(html, /env\(safe-area-inset-bottom\)/, 'mobile settlement actions should respect phone safe areas');
assert.match(html, /@media \(max-width: 640px\)/, 'mobile settlement should use the project mobile breakpoint');
assert.match(html, /function pkIsMobileLayout\(\)/, 'settlement should branch on mobile layout');
assert.match(html, /function pkRenderMobileMatchFinished\(/, 'settlement should render a dedicated mobile finish view');
assert.match(html, /function pkRenderMobileMatchFinished\([\s\S]*id="pkRematchBtn" onclick="pkRequestRematch\(\)">再战/, 'mobile rematch button should remain bound to pkRequestRematch');

console.log('pk phase two ui ok');
