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

console.log('pk phase two ui ok');
