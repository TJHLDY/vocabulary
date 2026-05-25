const API = 'http://localhost:8086';
const DICT = 'https://api.dictionaryapi.dev/api/v2/entries/en';
const token = localStorage.getItem('token');
let currentTab = 'search';

if (!token) { location.href = 'login.html'; }

fetchUser();
loadTopWords();

// ---- Tab Switching ----
function switchTab(tab) {
  currentTab = tab;
  document.querySelectorAll('.tab-nav button').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
  document.querySelectorAll('.tab-content').forEach(c => c.classList.toggle('active', c.id === `tab-${tab}`));
  if (tab === 'book') loadWordbook();
}

// ---- Auth helpers ----
function authHeaders() {
  return { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' };
}

async function fetchUser() {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    document.getElementById('userBadge').textContent = payload.username || '用户';
  } catch(e) {
    document.getElementById('userBadge').textContent = '用户';
  }
}

function logout() {
  localStorage.removeItem('token');
  location.href = 'login.html';
}

// ---- Chinese detection ----
function isChinese(text) {
  return /[一-鿿]/.test(text);
}

// =============================================
//  Tab 1: Word Lookup
// =============================================
async function lookup() {
  const input = document.getElementById('wordInput');
  const word = input.value.trim();
  if (!word) return;

  const btn = document.getElementById('searchBtn');
  const area = document.getElementById('resultArea');
  btn.disabled = true;
  btn.textContent = '查询中...';
  area.innerHTML = '<div class="loading-spin"><div class="spinner"></div><div>正在查询...</div></div>';

  try {
    let searchWord = word;
    // If Chinese, translate to English first
    if (isChinese(word)) {
      const tRes = await fetch(`${API}/api/translate`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ text: word, from: 'zh', to: 'en' })
      });
      const tData = await tRes.json();
      if (tData.code === 200 && tData.data && tData.data.translation) {
        searchWord = tData.data.translation;
      } else {
        area.innerHTML = '<div class="error-msg">翻译失败，请重试</div>';
        return;
      }
    }

    // Save to DB and get Chinese translation + relations
    const [saveRes, dictRes] = await Promise.allSettled([
      fetch(`${API}/api/words?word=${encodeURIComponent(searchWord)}`, {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + token }
      }),
      fetch(`${DICT}/${encodeURIComponent(searchWord)}`)
    ]);

    let dbData = null;
    if (saveRes.status === 'fulfilled' && saveRes.value.ok) {
      const json = await saveRes.value.json();
      if (json.code === 200 && json.data) dbData = json.data;
    }

    // Render
    if (dictRes.status === 'fulfilled' && dictRes.value.ok) {
      const dictData = await dictRes.value.json();
      renderResult(dictData, dbData);
    } else if (dictRes.status === 'fulfilled' && dictRes.value.status === 404) {
      // No dictionary entry, but we might have translation
      if (dbData && dbData.chinese) {
        area.innerHTML = `<div class="result-card">
          <div class="word-head"><div class="word-title">${escapeHtml(searchWord)}</div></div>
          <div class="quick-meaning">${escapeHtml(dbData.chinese)}</div>
        </div>`;
      } else {
        area.innerHTML = '<div class="error-msg">未找到该单词的定义</div>';
      }
    } else {
      area.innerHTML = '<div class="error-msg">词典服务暂不可用</div>';
    }

    loadTopWords();
  } finally {
    btn.disabled = false;
    btn.textContent = '查 询';
  }
}

// Enter key triggers search
document.getElementById('wordInput').addEventListener('keydown', e => {
  if (e.key === 'Enter') lookup();
});

async function renderResult(entries, dbData) {
  if (!entries || entries.length === 0) return;
  const word = entries[0].word;
  const firstEntry = entries[0];
  const phonetic = firstEntry.phonetic || (firstEntry.phonetics && firstEntry.phonetics[0] && firstEntry.phonetics[0].text) || '';

  // Collect POS tags and examples from all entries
  const posSet = new Set();
  const allExamples = [];
  for (const entry of entries) {
    for (const meaning of entry.meanings || []) {
      if (meaning.partOfSpeech) posSet.add(meaning.partOfSpeech);
      for (const def of meaning.definitions || []) {
        if (def.example) allExamples.push(def.example);
      }
    }
  }
  const posTags = [...posSet];

  // Build the single merged card (without relations or examples, loaded async)
  let html = '<div class="result-card">';
  html += `<div class="word-head">
    <div class="word-title">${escapeHtml(word)}</div>
    ${phonetic ? `<div class="phonetic">/${phonetic}/</div>` : ''}
  </div>`;

  // Line 1: POS + Chinese
  if (dbData && dbData.chinese) {
    html += '<div class="quick-meaning">';
    for (const pos of posTags) {
      html += `<span class="pos-tag">${escapeHtml(pos)}</span>`;
    }
    html += escapeHtml(dbData.chinese);
    html += '</div>';
  }

  html += '</div>'; // close card
  document.getElementById('resultArea').innerHTML = html;

  const uniqueExamples = [...new Set(allExamples)].slice(0, 5);

  // Load relations (Datamuse) and examples in parallel
  const card = document.querySelector('#resultArea .result-card');
  if (!card) return;

  let relWords = [];
  try {
    const dmRes = await fetch(`https://api.datamuse.com/words?rel_trg=${encodeURIComponent(word)}&max=8`);
    if (dmRes.ok) {
      const dmData = await dmRes.json();
      relWords = dmData.map(d => d.word).filter(w => w !== word);
    }
  } catch(e) {}

  // Fetch Chinese for relation words + translate examples in parallel
  const relChinesePromises = relWords.map(w =>
    fetch(`${API}/api/words?word=${encodeURIComponent(w)}`, {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + token }
    }).then(r => r.ok ? r.json() : null)
  );

  const exampleTranslatePromises = uniqueExamples.map(ex =>
    fetch(`${API}/api/translate`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ text: ex, from: 'en', to: 'zh' })
    }).then(r => r.ok ? r.json() : null)
  );

  const [relResults, exTransResults] = await Promise.all([
    Promise.all(relChinesePromises),
    Promise.all(exampleTranslatePromises)
  ]);

  // Build relation -> Chinese map
  const relMap = {};
  for (let i = 0; i < relWords.length; i++) {
    const r = relResults[i];
    relMap[relWords[i]] = (r && r.code === 200 && r.data && r.data.chinese) ? r.data.chinese : '';
  }

  // Build example -> Chinese map
  const exChineseMap = {};
  for (let i = 0; i < uniqueExamples.length; i++) {
    const t = exTransResults[i];
    exChineseMap[uniqueExamples[i]] = (t && t.code === 200 && t.data && t.data.translation) ? t.data.translation : '';
  }

  // Collect content words from examples for meaning map
  const stopwordsRe = /^(the|a|an|is|are|was|were|be|been|in|on|at|to|for|of|and|or|it|he|she|they|we|you|I|me|him|her|us|them|this|that|these|those|my|your|his|its|our|their|do|does|did|has|have|had|can|could|will|would|shall|should|may|might|must|not|no|yes|but|if|so|as|by|from|with|about|up|out|all|just|only|also|very|too|well|here|there|when|where|why|how|which|who|whom|what)$/;
  const wordSet = new Set();
  for (const ex of uniqueExamples) {
    for (let t of ex.split(/[\s,.!?;:"]+/)) {
      t = t.replace(/[^a-zA-Z0-9-]/g, '').toLowerCase();
      if (t.length > 2 && !stopwordsRe.test(t)) wordSet.add(t);
    }
  }

  const wordArr = [...wordSet];
  const meaningPromises = wordArr.map(w =>
    fetch(`${API}/api/words?word=${encodeURIComponent(w)}`, {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + token }
    }).then(r => r.ok ? r.json() : null)
  );
  const meaningResults = await Promise.all(meaningPromises);
  const meaningMap = {};
  for (let i = 0; i < wordArr.length; i++) {
    const m = meaningResults[i];
    meaningMap[wordArr[i]] = (m && m.code === 200 && m.data && m.data.chinese) ? m.data.chinese : '';
  }

  // Build the full content to insert into card
  let contentHtml = '';

  // Line 2: Related words (from Datamuse)
  if (relWords.length > 0) {
    contentHtml += '<div class="related-words">';
    contentHtml += '<span class="label">关联词:</span>';
    for (const rw of relWords) {
      const rch = relMap[rw] || '';
      contentHtml += `<span class="rel-tag" onclick="event.stopPropagation();lookupWord('${escapeHtml(rw)}')">${escapeHtml(rw)}${rch ? ' ' + escapeHtml(rch) : ''}</span>`;
    }
    contentHtml += '</div>';
  }

  // Line 3: Examples with interactive words
  if (uniqueExamples.length > 0) {
    contentHtml += '<div class="examples-section">';
    contentHtml += '<span class="section-label">例句</span>';
    for (const ex of uniqueExamples) {
      contentHtml += '<div class="example-item">';
      const tokens = ex.split(/([\s,.!?;:"]+)/);
      let textHtml = '';
      for (const token of tokens) {
        if (!token || /^\s+$/.test(token)) {
          textHtml += token;
        } else if (/^[,.!?;:"]+$/.test(token)) {
          textHtml += `<span class="sword punct">${escapeHtml(token)}</span>`;
        } else {
          const clean = token.replace(/[^a-zA-Z0-9-]/g, '').toLowerCase();
          const meaning = meaningMap[clean] || '';
          textHtml += `<span class="sword" data-word="${escapeHtml(clean)}" data-meaning="${escapeHtml(meaning)}" onmouseenter="showTooltip(event, this)" onmouseleave="hideTooltip()" onclick="openSidebar('${escapeHtml(clean)}')">${escapeHtml(token)}</span>`;
        }
      }
      contentHtml += `<div class="example-text">${textHtml}</div>`;
      if (exChineseMap[ex]) {
        contentHtml += `<div class="example-translation">${escapeHtml(exChineseMap[ex])}</div>`;
      }
      contentHtml += '</div>';
    }
    contentHtml += '</div>';
  }

  card.insertAdjacentHTML('beforeend', contentHtml);
  }

function lookupWord(word) {
  document.getElementById('wordInput').value = word;
  switchTab('search');
  lookup();
}

// ---- Top Words ----
async function loadTopWords() {
  try {
    const res = await fetch(`${API}/api/words/all`, {
      headers: { 'Authorization': 'Bearer ' + token }
    });
    const data = await res.json();
    if (data.code === 200 && data.data && data.data.length > 0) {
      const top10 = data.data.slice(0, 10);
      const list = document.getElementById('topList');
      list.innerHTML = top10.map((w, i) =>
        `<li onclick="lookupWord('${escapeHtml(w.word)}')"><span class="rank-num">${i + 1}</span> ${escapeHtml(w.word)} <span class="rank-count">${w.count}次</span></li>`
      ).join('');
    }
  } catch(e) {}
}

// =============================================
//  Tab 2: Sentence Translation
// =============================================
async function translateSentence() {
  const input = document.getElementById('sentenceInput');
  const text = input.value.trim();
  if (!text) return;

  const btn = document.getElementById('translateBtn');
  const area = document.getElementById('sentenceArea');
  btn.disabled = true;
  btn.textContent = '翻译中...';
  area.innerHTML = '<div class="loading-spin"><div class="spinner"></div><div>正在翻译...</div></div>';

  try {
    const res = await fetch(`${API}/api/translate/sentence`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ text, from: 'en', to: 'zh' })
    });
    const data = await res.json();

    if (data.code !== 200 || !data.data) {
      area.innerHTML = '<div class="error-msg">翻译失败，请重试</div>';
      return;
    }

    const { original, translated, words } = data.data;

    // Build word meaning map
    const meaningMap = {};
    for (const w of (words || [])) {
      meaningMap[w.word.toLowerCase()] = w.chinese || '';
    }

    // Build original with interactive words
    const tokens = original.split(/([\s,.!?;:]+)/);
    let originalHtml = '';
    for (const token of tokens) {
      if (!token || /^\s+$/.test(token)) {
        originalHtml += token;
      } else if (/^[,.!?;:]+$/.test(token)) {
        originalHtml += `<span class="sword punct">${escapeHtml(token)}</span>`;
      } else {
        const clean = token.replace(/[^a-zA-Z0-9-]/g, '').toLowerCase();
        const meaning = meaningMap[clean] || '';
        originalHtml += `<span class="sword" data-word="${escapeHtml(clean)}" data-meaning="${escapeHtml(meaning)}" onmouseenter="showTooltip(event, this)" onmouseleave="hideTooltip()" onclick="openSidebar('${escapeHtml(clean)}')">${escapeHtml(token)}</span>`;
      }
    }

    area.innerHTML = `
      <div class="sentence-result">
        <div class="sentence-original">${originalHtml}</div>
        <div class="sentence-translation">
          <span class="label">翻译</span>
          ${escapeHtml(translated)}
        </div>
      </div>`;
  } catch(e) {
    area.innerHTML = '<div class="error-msg">翻译失败，请检查网络</div>';
  } finally {
    btn.disabled = false;
    btn.textContent = '翻 译';
  }
}

// Enter key triggers sentence translation
document.getElementById('sentenceInput').addEventListener('keydown', e => {
  if (e.key === 'Enter' && e.ctrlKey) translateSentence();
});

// ---- Tooltip ----
function showTooltip(e, el) {
  const meaning = el.dataset.meaning;
  if (!meaning) return;
  const tip = document.getElementById('wordTooltip');
  tip.textContent = meaning;
  tip.style.opacity = '1';
  tip.style.left = (e.clientX + 12) + 'px';
  tip.style.top = (e.clientY - 36) + 'px';
}

function hideTooltip() {
  document.getElementById('wordTooltip').style.opacity = '0';
}

// Track tooltip position
document.addEventListener('mousemove', e => {
  const tip = document.getElementById('wordTooltip');
  if (tip.style.opacity === '1') {
    tip.style.left = (e.clientX + 12) + 'px';
    tip.style.top = (e.clientY - 36) + 'px';
  }
});

// =============================================
//  Sidebar (word detail on click)
// =============================================
async function openSidebar(word) {
  const overlay = document.getElementById('sidebarOverlay');
  const sidebar = document.getElementById('sidebar');
  const content = document.getElementById('sidebarContent');

  overlay.classList.add('show');
  sidebar.classList.add('show');

  content.innerHTML = '<div class="loading-spin"><div class="spinner"></div></div>';

  try {
    // Save to DB (counts as query), fetch dictionary, fetch relations
    const [saveRes, dictRes, relRes] = await Promise.allSettled([
      fetch(`${API}/api/words?word=${encodeURIComponent(word)}`, {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + token }
      }),
      fetch(`${DICT}/${encodeURIComponent(word)}`),
      fetch(`${API}/api/words/relations?word=${encodeURIComponent(word)}`, {
        headers: { 'Authorization': 'Bearer ' + token }
      })
    ]);

    let dbData = null, dictData = null, relations = [];

    if (saveRes.status === 'fulfilled' && saveRes.value.ok) {
      const json = await saveRes.value.json();
      if (json.code === 200 && json.data) dbData = json.data;
    }
    if (dictRes.status === 'fulfilled' && dictRes.value.ok) {
      dictData = await dictRes.value.json();
    }
    if (relRes.status === 'fulfilled' && relRes.value.ok) {
      const json = await relRes.value.json();
      if (json.code === 200) relations = json.data || [];
    }

    renderSidebar(word, dictData, dbData, relations);
  } catch(e) {
    content.innerHTML = '<div class="error-msg">加载失败</div>';
  }
}

function renderSidebar(word, dictData, dbData, relations) {
  const content = document.getElementById('sidebarContent');
  const entry = dictData && dictData.length > 0 ? dictData[0] : null;
  const phonetic = entry ? (entry.phonetic || (entry.phonetics && entry.phonetics[0] && entry.phonetics[0].text) || '') : '';

  let html = `<div class="word-title">${escapeHtml(word)}</div>`;
  if (phonetic) html += `<div class="phonetic">/${phonetic}/</div>`;

  // Quick meaning
  if (dbData && dbData.chinese) {
    const firstPos = entry && entry.meanings && entry.meanings[0] ? entry.meanings[0].partOfSpeech : '';
    html += '<div class="quick-meaning">';
    if (firstPos) html += `<span class="pos-tag">${escapeHtml(firstPos)}</span>`;
    html += escapeHtml(dbData.chinese);
    html += '</div>';
  }

  // Related words
  if (relations.length > 0) {
    html += '<div class="sidebar-related"><span style="font-size:0.8rem;color:#999;">关联词:</span>';
    for (const rel of relations) {
      html += `<span class="rel-tag" onclick="openSidebar('${escapeHtml(rel.relatedWord)}')">${escapeHtml(rel.relatedWord)}</span>`;
    }
    html += '</div>';
  }

  // Detailed definitions
  if (entry) {
    for (const meaning of entry.meanings || []) {
      html += '<div class="meaning-group" style="padding:16px 0;">';
      html += `<div class="part-of-speech">${escapeHtml(meaning.partOfSpeech)}</div>`;
      for (const def of meaning.definitions || []) {
        html += '<div class="def-item">';
        html += `<div class="def-text">${escapeHtml(def.definition)}</div>`;
        if (def.example) {
          html += `<div class="def-example">"${escapeHtml(def.example)}"</div>`;
        }
        html += '</div>';
      }
      html += '</div>';
    }
  } else if (!dbData || !dbData.chinese) {
    html += '<div class="error-msg">暂无详细释义</div>';
  }

  // Query count hint
  if (dbData) {
    html += `<div class="query-count">已存入 · 每次点击都计入查询</div>`;
  }

  content.innerHTML = html;
}

function closeSidebar() {
  document.getElementById('sidebarOverlay').classList.remove('show');
  document.getElementById('sidebar').classList.remove('show');
}

// Esc to close sidebar
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') closeSidebar();
});

// =============================================
//  Tab 3: My Words
// =============================================
async function loadWordbook() {
  const area = document.getElementById('wordbookArea');
  try {
    const res = await fetch(`${API}/api/words/all`, {
      headers: { 'Authorization': 'Bearer ' + token }
    });
    const data = await res.json();
    if (data.code === 200 && data.data && data.data.length > 0) {
      area.innerHTML = `
        <table class="wordbook-table">
          <thead><tr><th>单词</th><th>中文</th><th>查询次数</th></tr></thead>
          <tbody>
            ${data.data.map(w => `
              <tr onclick="openSidebar('${escapeHtml(w.word)}')">
                <td>${escapeHtml(w.word)}</td>
                <td style="color:#888;font-size:0.85rem;">${w.chinese || '-'}</td>
                <td><span class="count-badge">${w.count} 次</span></td>
              </tr>
            `).join('')}
          </tbody>
        </table>`;
    } else {
      area.innerHTML = '<div class="wordbook-empty"><div class="icon">📖</div><p>还没有存入任何单词</p><p style="font-size:0.82rem;margin-top:4px;">去「查词」或「翻译句子」Tab 试试吧</p></div>';
    }
  } catch(e) {
    area.innerHTML = '<div class="error-msg">加载失败，请重试</div>';
  }
}

function escapeHtml(s) {
  if (!s) return '';
  const div = document.createElement('div');
  div.textContent = s;
  return div.innerHTML;
}
