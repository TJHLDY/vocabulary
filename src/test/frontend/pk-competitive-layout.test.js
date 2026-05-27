const fs = require('fs');
const path = require('path');
const assert = require('assert');

const htmlPath = path.resolve(__dirname, '../../main/resources/static/index.html');
const html = fs.readFileSync(htmlPath, 'utf8');

assert.match(html, /quiz-shell/, 'desktop quiz area should use a wider shell layout');
assert.match(html, /leaderboardModeTabs/, 'leaderboard panel should switch between quiz and PK modes');
assert.match(html, /switchLeaderboardMode\('quiz'/, 'unified leaderboard should expose quiz mode');
assert.match(html, /switchLeaderboardMode\('pk'/, 'unified leaderboard should expose PK mode');
assert.match(html, /renderPkLeaderboard\(/, 'PK leaderboard should render inside the unified leaderboard panel');
assert.ok(!html.includes('class="pk-lb-panel"'), 'separate PK leaderboard panel should be removed from quiz page');

console.log('pk competitive layout ok');
