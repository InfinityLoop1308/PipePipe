// PipePipe Desktop — web UI (no build step, plain ES modules)
import { Player, fmtTime, escapeHtml as esc } from './player.js';

const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const icon = (id, cls = '') => `<svg class="${cls}"><use href="#i-${id}"/></svg>`;
const main = $('#main');

// ------------------------------------------------------------------ local store (server persisted)
const navLang = (navigator.language || 'ko-KR').split('-');
const DEFAULT_SETTINGS = {
  service: 0, language: navLang[0] || 'ko', country: (navLang[1] || (navLang[0] === 'ko' ? 'KR' : 'US')).toUpperCase(), quality: '1080', codec: 'auto', autoplay: true,
  subtitles: 'off', theme: 'dark', volume: 1, muted: false, theater: false, sidebar: true, audioOnly: false,
};
const docs = { subscriptions: [], history: [], playlists: { watchLater: [], lists: [] }, settings: { ...DEFAULT_SETTINGS } };
const saveTimers = {};
function save(name, delay = 400) {
  clearTimeout(saveTimers[name]);
  saveTimers[name] = setTimeout(() => {
    fetch('/api/store/' + name, { method: 'PUT', body: JSON.stringify(docs[name]), headers: { 'Content-Type': 'application/json' } })
      .catch(() => toast('저장 실패'));
  }, delay);
}
async function loadDocs() {
  await Promise.all(Object.keys(docs).map(async (k) => {
    try {
      const v = await (await fetch('/api/store/' + k)).json();
      if (v !== null) docs[k] = k === 'settings' ? { ...DEFAULT_SETTINGS, ...v } : k === 'playlists' ? { watchLater: [], lists: [], ...v } : v;
      else if (k === 'settings') save('settings', 0); // first run: let the server pick up language / region
    } catch (e) { /* first run */ }
  }));
}
const S = () => docs.settings;

// ------------------------------------------------------------------ api
const cache = new Map();
async function api(path, params = {}, { signal, ttl = 0 } = {}) {
  const qs = new URLSearchParams(params).toString();
  const key = path + '?' + qs;
  if (ttl) {
    const c = cache.get(key);
    if (c && Date.now() - c.at < ttl) return c.data;
  }
  const r = await fetch('/api/' + path + (qs ? '?' + qs : ''), { signal });
  const data = await r.json().catch(() => ({ error: 'HTTP ' + r.status }));
  if (!r.ok) throw new Error(data.error || 'HTTP ' + r.status);
  if (ttl) cache.set(key, { data, at: Date.now() });
  return data;
}

// ------------------------------------------------------------------ formatting
const loc = () => (S().language || 'ko');
function fmtCount(n, unit = '') {
  if (n == null || n < 0) return '';
  return new Intl.NumberFormat(loc(), { notation: 'compact', maximumFractionDigits: 1 }).format(n) + unit;
}
function fmtViews(n) {
  if (n == null || n < 0) return '';
  return loc() === 'ko' ? '조회수 ' + fmtCount(n) + '회' : fmtCount(n) + ' views';
}
function fmtAgo(ms, fallback) {
  if (!ms) return fallback || '';
  const rtf = new Intl.RelativeTimeFormat(loc(), { numeric: 'auto' });
  const s = (ms - Date.now()) / 1000;
  const units = [['year', 31536000], ['month', 2592000], ['week', 604800], ['day', 86400], ['hour', 3600], ['minute', 60]];
  for (const [u, sec] of units) if (Math.abs(s) >= sec) return rtf.format(Math.round(s / sec), u);
  return rtf.format(0, 'minute');
}
const route = (name, params) => '#/' + name + (params ? '?' + new URLSearchParams(params) : '');

// ------------------------------------------------------------------ toasts
function toast(msg) {
  const t = document.createElement('div');
  t.className = 'toast';
  t.textContent = msg;
  $('#toasts').appendChild(t);
  setTimeout(() => t.remove(), 2400);
}

// ------------------------------------------------------------------ items
const thumbImg = (src, alt = '') => src ? `<img src="${esc(src)}" alt="${esc(alt)}" loading="lazy" decoding="async" referrerpolicy="no-referrer">` : '';
function historyFor(url) { return docs.history.find((h) => h.url === url); }

function progressLine(it) {
  const h = historyFor(it.url);
  if (!h || !h.pos || !it.duration) return '';
  return `<div class="progress-line"><i style="width:${Math.min(100, (h.pos / it.duration) * 100)}%"></i></div>`;
}
function badge(it) {
  if (it.live) return '<span class="badge live">LIVE</span>';
  if (it.duration > 0) return `<span class="badge">${fmtTime(it.duration)}</span>`;
  return '';
}
function byline(it) {
  const parts = [];
  if (it.views >= 0 && it.views != null) parts.push(fmtViews(it.views));
  const ago = fmtAgo(it.date, it.dateText);
  if (ago) parts.push(ago);
  return parts.map((p, i) => `<span class="${i ? 'dot' : ''}">${esc(p)}</span>`).join('');
}
function encodeItem(it) { return esc(JSON.stringify(it)); }

function card(it, ctx = {}) {
  if (it.type === 'channel') {
    return `<a class="card channel" href="${route('channel', { url: it.url })}">
      ${it.thumb ? `<img class="avatar-lg" src="${esc(it.thumb)}" alt="" loading="lazy" referrerpolicy="no-referrer">` : '<div class="avatar-lg"></div>'}
      <div><h3 class="title">${esc(it.name)}</h3><div class="byline">${it.subs > 0 ? '구독자 ' + fmtCount(it.subs) + '명' : ''}</div></div></a>`;
  }
  if (it.type === 'playlist') {
    return `<a class="card" href="${route('playlist', { url: it.url })}">
      <div class="thumb">${thumbImg(it.thumb)}<span class="badge">${it.streams > 0 ? it.streams + '개 동영상' : '재생목록'}</span></div>
      <div class="meta"><div class="meta-text"><h3 class="title">${esc(it.name)}</h3><div class="byline">${esc(it.uploader || '')}</div></div></div></a>`;
  }
  const href = route('watch', ctx.queue ? { url: it.url, q: ctx.queue, i: ctx.index } : { url: it.url });
  return `<a class="card" href="${href}" data-prefetch="${esc(it.url)}" data-item="${encodeItem(it)}">
    <div class="thumb">${thumbImg(it.thumb, it.name)}${badge(it)}${progressLine(it)}
      <div class="hover-actions">
        <button data-act="later" title="나중에 볼 동영상">${icon('clock')}</button>
        <button data-act="addpl" title="재생목록에 추가">${icon('plus')}</button>
      </div></div>
    <div class="meta">
      ${ctx.noAvatar || !it.uploaderAvatar ? '' : `<img class="avatar" src="${esc(it.uploaderAvatar)}" alt="" loading="lazy" referrerpolicy="no-referrer">`}
      <div class="meta-text">
        <h3 class="title" title="${esc(it.name)}">${esc(it.name)}</h3>
        <div class="byline">${it.uploader ? `<div>${it.uploaderUrl ? `<span data-href="${route('channel', { url: it.uploaderUrl })}">${esc(it.uploader)}</span>` : esc(it.uploader)}${it.verified ? icon('verified', 'verified') : ''}</div>` : ''}<div>${byline(it)}</div></div>
      </div>
    </div></a>`;
}

function row(it, { compact = false, index, queue, remove } = {}) {
  if (it.type === 'channel') {
    return `<a class="row channel-row" href="${route('channel', { url: it.url })}">
      <div class="thumb">${it.thumb ? `<img class="loaded" src="${esc(it.thumb)}" alt="" loading="lazy" referrerpolicy="no-referrer">` : ''}</div>
      <div class="meta-text" style="align-self:center"><h3 class="title">${esc(it.name)}</h3>
      <div class="byline">${it.subs > 0 ? '구독자 ' + fmtCount(it.subs) + '명' : ''}${it.streams > 0 ? `<span class="dot">동영상 ${fmtCount(it.streams)}개</span>` : ''}</div>
      ${it.description ? `<div class="desc">${esc(it.description)}</div>` : ''}</div></a>`;
  }
  if (it.type === 'playlist') {
    return `<a class="row ${compact ? 'compact' : ''}" href="${route('playlist', { url: it.url })}">
      <div class="thumb">${thumbImg(it.thumb)}<span class="badge">${it.streams > 0 ? it.streams + '개' : '재생목록'}</span></div>
      <div class="meta-text"><h3 class="title">${esc(it.name)}</h3><div class="byline">${esc(it.uploader || '')}</div></div></a>`;
  }
  const href = route('watch', queue ? { url: it.url, q: queue, i: index } : { url: it.url });
  return `<a class="row ${compact ? 'compact' : ''}" href="${href}" data-prefetch="${esc(it.url)}" data-item="${encodeItem(it)}">
    ${index != null && !compact ? `<div class="pl-index">${index + 1}</div>` : ''}
    <div class="thumb">${thumbImg(it.thumb, it.name)}${badge(it)}${progressLine(it)}
      ${compact ? '' : `<div class="hover-actions"><button data-act="later" title="나중에 볼 동영상">${icon('clock')}</button><button data-act="addpl" title="재생목록에 추가">${icon('plus')}</button></div>`}</div>
    <div class="meta-text">
      <h3 class="title" title="${esc(it.name)}">${esc(it.name)}</h3>
      <div class="byline">${it.uploader ? `<div>${it.uploaderUrl ? `<span data-href="${route('channel', { url: it.uploaderUrl })}">${esc(it.uploader)}</span>` : esc(it.uploader)}${it.verified ? icon('verified', 'verified') : ''}</div>` : ''}<div>${byline(it)}</div></div>
    </div>
    ${remove ? `<button class="icon-btn sm remove" data-act="${remove}" title="삭제">${icon('x')}</button>` : ''}
  </a>`;
}

function skeletonGrid(n = 12) {
  return '<div class="grid">' + Array.from({ length: n }, () => `<div class="card"><div class="thumb sk"></div><div class="meta"><div class="avatar sk"></div><div class="meta-text"><div class="sk sk-line" style="width:90%"></div><div class="sk sk-line" style="width:60%"></div></div></div></div>`).join('') + '</div>';
}
function errorBox(e) {
  return `<div class="error-box">${icon('x')}<div><b>불러오지 못했습니다</b><div style="color:var(--text-2);font-size:13px">${esc(e.message || e)}</div></div><div style="flex:1"></div><button class="btn" onclick="location.reload()">다시 시도</button></div>`;
}
function emptyState(ic, title, text = '') {
  return `<div class="empty">${icon(ic)}<h3>${esc(title)}</h3><div>${text}</div></div>`;
}

// infinite scroll for any list returned by the server
function paginate(host, next, render, signal) {
  if (!next) return;
  const sentinel = document.createElement('div');
  sentinel.className = 'sentinel';
  host.after(sentinel);
  let loading = false;
  const io = new IntersectionObserver(async (entries) => {
    if (!entries[0].isIntersecting || loading || !next) return;
    loading = true;
    const spin = document.createElement('div');
    spin.className = 'spinner';
    sentinel.after(spin);
    try {
      const r = await api('more', { next }, { signal });
      host.insertAdjacentHTML('beforeend', r.items.map(render).join(''));
      next = r.next;
      if (!next) io.disconnect();
    } catch (e) {
      if (e.name !== 'AbortError') { io.disconnect(); sentinel.insertAdjacentHTML('afterend', errorBox(e)); }
    } finally { spin.remove(); loading = false; }
  }, { root: main, rootMargin: '1200px' });
  io.observe(sentinel);
  signal.addEventListener('abort', () => io.disconnect());
}

// ------------------------------------------------------------------ player + queue
let player;
const nowPlaying = { url: null, data: null, queue: null, index: -1 };
const sessionWatched = new Set();

function queueItems(q) {
  if (!q) return null;
  if (q === 'later') return docs.playlists.watchLater;
  if (q.startsWith('local:')) return (docs.playlists.lists.find((l) => l.id === q.slice(6)) || {}).items || null;
  if (q.startsWith('remote:')) return remoteQueues.get(q) || null;
  return null;
}
const remoteQueues = new Map();

function peekNext() {
  const items = queueItems(nowPlaying.queue);
  if (items && nowPlaying.index + 1 < items.length) return { ...items[nowPlaying.index + 1], _qi: nowPlaying.index + 1 };
  const rel = (nowPlaying.data && nowPlaying.data.related || []).filter((r) => r.type === 'stream' && !r.live);
  return rel.find((r) => !sessionWatched.has(r.url)) || rel[0] || null;
}
function playNext() {
  const n = peekNext();
  if (!n) return;
  const params = { url: n.url };
  if (n._qi != null) { params.q = nowPlaying.queue; params.i = n._qi; }
  location.hash = route('watch', params);
}

function recordHistory(d) {
  const prev = historyFor(d.url);
  const entry = { url: d.url, name: d.name, thumb: d.thumb, uploader: d.uploader && d.uploader.name, uploaderUrl: d.uploader && d.uploader.url,
    duration: d.duration, service: d.service, pos: prev ? prev.pos : 0, at: Date.now(), type: 'stream' };
  docs.history = [entry, ...docs.history.filter((h) => h.url !== d.url)].slice(0, 1000);
  save('history');
}
let lastPosSave = 0;
function onPlayerTime(t, dur) {
  const h = nowPlaying.url && historyFor(nowPlaying.url);
  if (!h || !dur || nowPlaying.data?.live) return;
  h.pos = dur - t < 15 ? 0 : Math.floor(t); // finished -> start over next time
  if (Date.now() - lastPosSave > 5000) { lastPosSave = Date.now(); save('history', 1500); }
}

function initPlayer() {
  player = new Player(S());
  player.on('next', () => playNext());
  player.on('peekNext', peekNext);
  player.handlers.peekNext = peekNext;
  player.on('time', onPlayerTime);
  player.on('settings', () => save('settings', 800));
  player.on('theater', () => { S().theater = !S().theater; save('settings'); $('.watch')?.classList.toggle('theater', S().theater); });
  player.on('chapter', (c) => {
    $$('.chapter').forEach((el) => el.classList.toggle('now', c && +el.dataset.t === c.t));
  });
}

function dockToMini() {
  if (!nowPlaying.url || !player || player.video.paused && player.video.currentTime < 1) { closeMini(); return; }
  const dock = $('#mini-dock');
  let slot = $('.mini-slot', dock);
  if (!slot) { slot = document.createElement('div'); slot.className = 'mini-slot'; dock.prepend(slot); }
  player.closeMenu();
  slot.appendChild(player.el);
  $('#mini-title').textContent = nowPlaying.data ? nowPlaying.data.name : '';
  dock.hidden = false;
}
function closeMini() {
  $('#mini-dock').hidden = true;
  if (player) player.stop();
  nowPlaying.url = null; nowPlaying.data = null;
}

// ------------------------------------------------------------------ description sanitising / linkify
function linkifyText(text) {
  return esc(text)
    .replace(/(https?:\/\/[^\s<]+)/g, (m) => `<a href="${m}" data-ext>${m}</a>`)
    .replace(/(^|\s)((?:\d{1,2}:)?\d{1,2}:\d{2})(?=\s|$)/gm, (m, pre, ts) => `${pre}<a href="#" data-seek="${toSec(ts)}">${ts}</a>`);
}
function toSec(ts) { return ts.split(':').reduce((a, b) => a * 60 + +b, 0); }
function sanitizeHtml(html) {
  const doc = new DOMParser().parseFromString(`<div>${html}</div>`, 'text/html');
  const out = document.createElement('div');
  const walk = (node, into) => {
    for (const n of node.childNodes) {
      if (n.nodeType === 3) { into.appendChild(document.createTextNode(n.textContent)); continue; }
      if (n.nodeType !== 1) continue;
      const tag = n.tagName.toLowerCase();
      if (tag === 'br') { into.appendChild(document.createElement('br')); continue; }
      if (tag === 'a') {
        const a = document.createElement('a');
        const href = n.getAttribute('href') || '';
        const t = href.match(/[?&#]t=(\d+)/);
        if (t && /watch|youtu\.be/.test(href) && nowPlaying.url && href.includes(nowPlaying.data?.id || '__')) { a.href = '#'; a.dataset.seek = t[1]; }
        else { a.href = href; a.dataset.ext = ''; }
        walk(n, a); into.appendChild(a); continue;
      }
      if (['b', 'strong', 'i', 'em', 'p', 'span', 'div', 'ul', 'ol', 'li'].includes(tag)) {
        const e = document.createElement(['p', 'div'].includes(tag) ? 'span' : tag);
        walk(n, e); into.appendChild(e); if (tag === 'p' || tag === 'div') into.appendChild(document.createElement('br'));
        continue;
      }
      walk(n, into);
    }
  };
  walk(doc.body.firstChild, out);
  return out.innerHTML;
}
function richText(text, isHtml) {
  if (!text) return '';
  return isHtml ? sanitizeHtml(text) : linkifyText(text);
}

/** turn a pasted URL into an internal route */
function routeForUrl(u) {
  if (!/^https?:\/\//i.test(u)) return null;
  if (/youtube\.com\/(watch|shorts|live)|youtu\.be\/|bilibili\.com\/video|b23\.tv|nicovideo\.jp\/watch|nico\.ms/.test(u)) return route('watch', { url: u });
  if (/[?&]list=/.test(u)) return route('playlist', { url: u });
  if (/youtube\.com\/(channel|c|user|@)|youtube\.com\/@|space\.bilibili\.com|nicovideo\.jp\/user/.test(u)) return route('channel', { url: u });
  return route('watch', { url: u });
}

// ------------------------------------------------------------------ subscriptions helpers
function isSubscribed(url) { return docs.subscriptions.some((s) => s.url === url); }
function toggleSub(ch) {
  if (isSubscribed(ch.url)) {
    docs.subscriptions = docs.subscriptions.filter((s) => s.url !== ch.url);
    toast('구독 취소됨');
  } else {
    docs.subscriptions.push({ url: ch.url, name: ch.name, avatar: ch.avatar, service: ch.service ?? 0 });
    toast(ch.name + ' 구독');
  }
  save('subscriptions');
  renderNavSubs();
  return isSubscribed(ch.url);
}
function subButton(ch) {
  const on = isSubscribed(ch.url);
  return `<button class="btn ${on ? 'subscribed' : 'primary'}" data-sub>${on ? icon('check') + '구독중' : '구독'}</button>`;
}
function renderNavSubs() {
  const host = $('#nav-subs');
  if (!docs.subscriptions.length) { host.innerHTML = '<div class="nav-empty">구독한 채널이 없습니다</div>'; return; }
  host.innerHTML = [...docs.subscriptions].sort((a, b) => a.name.localeCompare(b.name)).map((s) =>
    `<a class="nav-sub" href="${route('channel', { url: s.url })}" title="${esc(s.name)}">${s.avatar ? `<img src="${esc(s.avatar)}" alt="" loading="lazy" referrerpolicy="no-referrer">` : '<div class="ph"></div>'}<span>${esc(s.name)}</span></a>`).join('');
}

// ------------------------------------------------------------------ playlists helpers
function addToWatchLater(it) {
  if (docs.playlists.watchLater.some((x) => x.url === it.url)) { toast('이미 추가됨'); return; }
  docs.playlists.watchLater.unshift(slim(it));
  save('playlists');
  toast('나중에 볼 동영상에 추가됨');
}
function slim(it) {
  return { type: 'stream', url: it.url, name: it.name, thumb: it.thumb, uploader: it.uploader, uploaderUrl: it.uploaderUrl, duration: it.duration, service: it.service, views: it.views, date: it.date, dateText: it.dateText };
}
function addToPlaylistMenu(it, anchor) {
  $$('.menu.floating').forEach((m) => m.remove());
  const m = document.createElement('div');
  m.className = 'menu floating';
  m.style.position = 'fixed';
  const r = anchor.getBoundingClientRect();
  m.style.left = Math.min(window.innerWidth - 230, r.left) + 'px';
  m.style.top = Math.min(window.innerHeight - 300, r.bottom + 6) + 'px';
  m.style.width = '220px';
  m.innerHTML = docs.playlists.lists.map((l) => `<button data-pl="${esc(l.id)}">${icon('library')}${esc(l.name)}</button>`).join('') +
    `<button data-new>${icon('plus')}새 재생목록</button>`;
  document.body.appendChild(m);
  const close = (e) => { if (!m.contains(e.target)) { m.remove(); document.removeEventListener('mousedown', close, true); } };
  setTimeout(() => document.addEventListener('mousedown', close, true));
  m.addEventListener('click', (e) => {
    const b = e.target.closest('button');
    if (!b) return;
    let list;
    if (b.dataset.new != null) {
      const name = prompt('새 재생목록 이름');
      if (!name) return;
      list = { id: Math.random().toString(36).slice(2, 10), name, items: [] };
      docs.playlists.lists.push(list);
    } else list = docs.playlists.lists.find((l) => l.id === b.dataset.pl);
    if (!list.items.some((x) => x.url === it.url)) list.items.push(slim(it));
    save('playlists');
    toast(list.name + '에 추가됨');
    m.remove();
  });
}

// ------------------------------------------------------------------ views
let services = [];
const views = {};

views.home = async (p, signal) => {
  const svc = services.find((s) => s.id === +S().service) || services[0];
  const kiosk = p.get('kiosk') || (svc && svc.defaultKiosk) || '';
  main.innerHTML = `<div class="page"><div class="page-head"><h1>${esc(svc ? svc.name : '')}</h1><div class="chips" id="kiosks"></div></div><div id="list">${skeletonGrid()}</div></div>`;
  if (svc) $('#kiosks').innerHTML = svc.kiosks.map((k) => `<a class="chip ${k === kiosk ? 'on' : ''}" href="${route('', { kiosk: k })}">${esc(kioskName(k))}</a>`).join('');
  try {
    const r = await api('kiosk', { service: S().service, id: kiosk }, { signal, ttl: 10 * 60e3 });
    $('#list').innerHTML = r.items.length ? `<div class="grid">${r.items.map((i) => card(i)).join('')}</div>` : emptyState('flame', '표시할 동영상이 없습니다');
    if (r.items.length) paginate($('#list .grid'), r.next, (i) => card(i), signal);
  } catch (e) { if (e.name !== 'AbortError') $('#list').innerHTML = errorBox(e); }
};
const KIOSK_NAMES = { Trending: '인기 급상승', 'Recommended Lives': '추천 라이브', Live: '라이브', Music: '음악', Gaming: '게임', Movies: '영화', Recommended: '추천', 'Top 50': 'Top 50', 'New & hot': '최신 인기' };
const kioskName = (k) => KIOSK_NAMES[k] || k;

const FILTER_NAMES = { all: '전체', videos: '동영상', channels: '채널', playlists: '재생목록', movies: '영화', music_songs: '노래', music_videos: '뮤직비디오',
  music_albums: '앨범', music_playlists: '음악 재생목록', music_artists: '아티스트', lives: '라이브', animes: '애니메이션', movies_and_tv: '영화·TV' };
views.search = async (p, signal) => {
  const q = p.get('q') || '';
  const filter = p.get('filter');
  $('#search-input').value = q;
  main.innerHTML = `<div class="page" style="max-width:1280px"><div class="page-head"><h1>“${esc(q)}”</h1><span class="sub" id="sugg"></span></div>
    <div class="chips" id="filters" style="margin:-6px 0 22px"></div><div id="list"><div class="spinner"></div></div></div>`;
  try {
    const params = { q, service: S().service };
    if (filter) params.filter = filter;
    const r = await api('search', params, { signal, ttl: 5 * 60e3 });
    $('#filters').innerHTML = (r.filters || []).map((f) => `<a class="chip ${f.id === r.filter ? 'on' : ''}" href="${route('search', { q, filter: f.id })}">${esc(FILTER_NAMES[f.name] || f.name)}</a>`).join('');
    if (r.suggestion) $('#sugg').innerHTML = `${r.corrected ? '다음 검색결과 표시 중:' : '이것을 찾으셨나요?'} <a href="${route('search', { q: r.suggestion })}" style="color:var(--accent-2)">${esc(r.suggestion)}</a>`;
    $('#list').innerHTML = r.items.length ? `<div class="rows">${r.items.map((i) => row(i)).join('')}</div>` : emptyState('search', '검색 결과가 없습니다');
    if (r.items.length) paginate($('#list .rows'), r.next, (i) => row(i), signal);
  } catch (e) { if (e.name !== 'AbortError') $('#list').innerHTML = errorBox(e); }
};

views.feed = async (p, signal) => {
  main.innerHTML = `<div class="page"><div class="page-head"><h1>구독</h1><span class="sub">${docs.subscriptions.length}개 채널</span><div class="spacer"></div>
    <button class="btn" id="refresh">${icon('refresh')}새로고침</button></div><div id="list">${docs.subscriptions.length ? skeletonGrid() : ''}</div></div>`;
  if (!docs.subscriptions.length) {
    $('#list').innerHTML = emptyState('rss', '구독한 채널이 없습니다', '채널 페이지에서 구독하거나, <a href="#/subs" style="color:var(--accent-2)">NewPipe/PipePipe 구독 목록을 가져오세요</a>.');
    return;
  }
  const load = async (refresh) => {
    try {
      const r = await api('feed', refresh ? { refresh: 1 } : {}, { signal });
      $('#list').innerHTML = (r.failed ? `<div class="sub" style="margin-bottom:12px;color:var(--text-3)">${r.failed}개 채널을 불러오지 못했습니다</div>` : '') +
        (r.items.length ? `<div class="grid">${r.items.map((i) => card(i, { noAvatar: true })).join('')}</div>` : emptyState('rss', '새 동영상이 없습니다'));
    } catch (e) { if (e.name !== 'AbortError') $('#list').innerHTML = errorBox(e); }
  };
  $('#refresh').onclick = () => { $('#list').innerHTML = skeletonGrid(); load(true); };
  load(false);
};

views.history = async () => {
  const items = docs.history;
  main.innerHTML = `<div class="page" style="max-width:1200px"><div class="page-head"><h1>시청 기록</h1><span class="sub">${items.length}개</span><div class="spacer"></div>
    ${items.length ? `<button class="btn ghost" id="clear">${icon('trash')}기록 삭제</button>` : ''}</div>
    <div class="rows" id="list">${items.length ? groupByDay(items).map(([day, list]) => `<div class="section-title" style="margin:8px 0 0">${esc(day)}</div>` + list.map((i) => row(i, { remove: 'rmhist' })).join('')).join('') : emptyState('history', '시청 기록이 없습니다')}</div></div>`;
  const c = $('#clear');
  if (c) c.onclick = () => { if (confirm('시청 기록을 모두 삭제할까요?')) { docs.history = []; save('history', 0); views.history(); } };
};
function groupByDay(items) {
  const groups = new Map();
  const fmt = new Intl.DateTimeFormat(loc(), { month: 'long', day: 'numeric', weekday: 'short' });
  const today = new Date().toDateString(), yest = new Date(Date.now() - 864e5).toDateString();
  for (const it of items) {
    const d = new Date(it.at || 0);
    const key = d.toDateString() === today ? '오늘' : d.toDateString() === yest ? '어제' : fmt.format(d);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(it);
  }
  return [...groups];
}

views.library = async () => {
  const wl = docs.playlists.watchLater;
  const lists = docs.playlists.lists;
  main.innerHTML = `<div class="page"><div class="page-head"><h1>보관함</h1><div class="spacer"></div><button class="btn" id="newpl">${icon('plus')}새 재생목록</button></div>
    <div class="grid">
      <a class="card" href="${route('local', { id: 'later' })}"><div class="thumb">${thumbImg(wl[0] && wl[0].thumb)}<span class="badge">${wl.length}개</span></div>
        <div class="meta"><div class="meta-text"><h3 class="title">${icon('clock', 'verified')} 나중에 볼 동영상</h3><div class="byline">비공개 · 로컬</div></div></div></a>
      ${lists.map((l) => `<a class="card" href="${route('local', { id: l.id })}"><div class="thumb">${thumbImg(l.items[0] && l.items[0].thumb)}<span class="badge">${l.items.length}개</span></div>
        <div class="meta"><div class="meta-text"><h3 class="title">${esc(l.name)}</h3><div class="byline">로컬 재생목록</div></div></div></a>`).join('')}
    </div></div>`;
  $('#newpl').onclick = () => {
    const name = prompt('새 재생목록 이름');
    if (!name) return;
    docs.playlists.lists.push({ id: Math.random().toString(36).slice(2, 10), name, items: [] });
    save('playlists');
    views.library();
  };
};

views.local = async (p) => {
  const id = p.get('id');
  const isLater = id === 'later';
  const list = isLater ? { name: '나중에 볼 동영상', items: docs.playlists.watchLater } : docs.playlists.lists.find((l) => l.id === id);
  if (!list) { main.innerHTML = `<div class="page">${emptyState('library', '재생목록을 찾을 수 없습니다')}</div>`; return; }
  const q = isLater ? 'later' : 'local:' + id;
  const total = list.items.reduce((a, b) => a + (b.duration > 0 ? b.duration : 0), 0);
  main.innerHTML = `<div class="page"><div class="pl-layout">
    <div class="pl-hero"><div class="thumb">${thumbImg(list.items[0] && list.items[0].thumb)}</div><h1>${esc(list.name)}</h1>
      <div class="sub">동영상 ${list.items.length}개 · ${fmtTime(total)}</div>
      <div class="actions">${list.items.length ? `<a class="btn primary" href="${route('watch', { url: list.items[0].url, q, i: 0 })}">${icon('play')}모두 재생</a>` : ''}
      ${isLater ? '' : `<button class="btn ghost" id="delpl">${icon('trash')}삭제</button>`}</div></div>
    <div class="rows" id="list">${list.items.length ? list.items.map((it, i) => row(it, { index: i, queue: q, remove: 'rmlocal' })).join('') : emptyState('library', '비어 있습니다', '동영상 카드에 마우스를 올리고 + 버튼으로 추가하세요.')}</div></div></div>`;
  const del = $('#delpl');
  if (del) del.onclick = () => { if (confirm(`'${list.name}' 재생목록을 삭제할까요?`)) { docs.playlists.lists = docs.playlists.lists.filter((l) => l !== list); save('playlists'); location.hash = '#/library'; } };
  $('#list').dataset.local = id;
};

views.subs = async () => {
  main.innerHTML = `<div class="page" style="max-width:960px"><div class="page-head"><h1>구독 관리</h1><span class="sub">${docs.subscriptions.length}개 채널</span><div class="spacer"></div>
    <label class="btn">${icon('upload')}가져오기<input type="file" accept=".json,application/json" id="import" hidden></label>
    <button class="btn" id="export">${icon('download')}내보내기</button></div>
    <div class="rows">${docs.subscriptions.length ? [...docs.subscriptions].sort((a, b) => a.name.localeCompare(b.name)).map((s) => `
      <div class="row" style="align-items:center;cursor:default"><a href="${route('channel', { url: s.url })}" style="display:flex;align-items:center;gap:14px;flex:1;min-width:0">
        ${s.avatar ? `<img class="avatar" src="${esc(s.avatar)}" alt="" referrerpolicy="no-referrer">` : '<div class="avatar"></div>'}
        <div class="meta-text"><div class="title" style="margin:0">${esc(s.name)}</div><div class="byline">${esc((services.find((x) => x.id === s.service) || {}).name || '')}</div></div></a>
        <button class="btn subscribed" data-unsub="${esc(s.url)}">구독중</button></div>`).join('') : emptyState('rss', '구독한 채널이 없습니다', 'NewPipe 또는 PipePipe에서 내보낸 구독 JSON 파일을 가져올 수 있습니다.')}</div></div>`;
  $('#import').onchange = async (e) => {
    const f = e.target.files[0];
    if (!f) return;
    try {
      const j = JSON.parse(await f.text());
      const list = j.subscriptions || j;
      let added = 0;
      for (const s of list) {
        const url = s.url;
        if (!url || isSubscribed(url)) continue;
        docs.subscriptions.push({ url, name: s.name || url, avatar: s.avatar || null, service: s.service_id ?? s.service ?? 0 });
        added++;
      }
      save('subscriptions', 0);
      renderNavSubs();
      toast(added + '개 채널을 가져왔습니다');
      views.subs();
    } catch (err) { toast('가져오기 실패: ' + err.message); }
  };
  $('#export').onclick = () => {
    const out = { app_version: 'pipepipe-desktop', app_version_int: 1, subscriptions: docs.subscriptions.map((s) => ({ service_id: s.service, url: s.url, name: s.name })) };
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([JSON.stringify(out, null, 2)], { type: 'application/json' }));
    a.download = 'pipepipe_subscriptions.json';
    a.click();
  };
  $('.rows', main).addEventListener('click', (e) => {
    const b = e.target.closest('[data-unsub]');
    if (!b) return;
    const s = docs.subscriptions.find((x) => x.url === b.dataset.unsub);
    if (s) { toggleSub(s); views.subs(); }
  });
};

views.channel = async (p, signal) => {
  const url = p.get('url');
  main.innerHTML = `<div class="page"><div class="ch-banner sk"></div><div class="ch-head"><div class="avatar sk"></div><div style="flex:1"><div class="sk sk-line" style="width:240px;height:24px"></div><div class="sk sk-line" style="width:140px"></div></div></div>${skeletonGrid(8)}</div>`;
  try {
    const c = await api('channel', { url }, { signal, ttl: 10 * 60e3 });
    const ch = { url: c.url, name: c.name, avatar: c.avatar, service: c.service };
    main.innerHTML = `<div class="page">
      ${c.banner ? `<div class="ch-banner" style="background-image:url('${esc(c.banner)}')"></div>` : ''}
      <div class="ch-head">${c.avatar ? `<img class="avatar" src="${esc(c.avatar)}" alt="" referrerpolicy="no-referrer">` : '<div class="avatar"></div>'}
        <div style="flex:1;min-width:0"><h1>${esc(c.name)}</h1><div class="sub">${c.subs > 0 ? '구독자 ' + fmtCount(c.subs) + '명' : ''}</div>
        ${c.description ? `<div class="ch-desc">${esc(c.description)}</div>` : ''}</div>
        <div id="subbtn">${subButton(ch)}</div></div>
      <div class="section-title">동영상</div>
      <div id="list">${c.items.length ? `<div class="grid">${c.items.map((i) => card(i, { noAvatar: true })).join('')}</div>` : emptyState('rss', '동영상이 없습니다')}</div></div>`;
    $('#subbtn').onclick = () => { toggleSub(ch); $('#subbtn').innerHTML = subButton(ch); };
    // keep the stored name/avatar fresh
    const s = docs.subscriptions.find((x) => x.url === c.url);
    if (s && (s.avatar !== c.avatar || s.name !== c.name)) { s.avatar = c.avatar; s.name = c.name; save('subscriptions'); renderNavSubs(); }
    if (c.items.length) paginate($('#list .grid'), c.next, (i) => card(i, { noAvatar: true }), signal);
  } catch (e) { if (e.name !== 'AbortError') main.innerHTML = `<div class="page">${errorBox(e)}</div>`; }
};

views.playlist = async (p, signal) => {
  const url = p.get('url');
  main.innerHTML = `<div class="page"><div class="spinner"></div></div>`;
  try {
    const pl = await api('playlist', { url }, { signal, ttl: 10 * 60e3 });
    const q = 'remote:' + pl.url;
    remoteQueues.set(q, pl.items.filter((i) => i.type === 'stream'));
    main.innerHTML = `<div class="page"><div class="pl-layout">
      <div class="pl-hero"><div class="thumb">${thumbImg(pl.thumb || (pl.items[0] && pl.items[0].thumb))}</div><h1>${esc(pl.name)}</h1>
        <div class="sub">${pl.uploaderUrl ? `<a href="${route('channel', { url: pl.uploaderUrl })}">${esc(pl.uploader || '')}</a>` : esc(pl.uploader || '')}${pl.count > 0 ? ` · 동영상 ${pl.count}개` : ''}</div>
        <div class="actions">${pl.items.length ? `<a class="btn primary" href="${route('watch', { url: pl.items[0].url, q, i: 0 })}">${icon('play')}모두 재생</a>` : ''}</div></div>
      <div class="rows" id="list">${pl.items.map((it, i) => row(it, { index: i, queue: q })).join('')}</div></div></div>`;
    let n = pl.items.length;
    paginate($('#list'), pl.next, (it) => { remoteQueues.get(q).push(it); return row(it, { index: n++, queue: q }); }, signal);
  } catch (e) { if (e.name !== 'AbortError') main.innerHTML = `<div class="page">${errorBox(e)}</div>`; }
};

views.settings = async () => {
  const s = S();
  const opt = (v, cur, label) => `<option value="${v}" ${String(cur) === String(v) ? 'selected' : ''}>${label}</option>`;
  main.innerHTML = `<div class="page settings"><div class="page-head"><h1>설정</h1></div>
    <div class="set-group"><h2>콘텐츠</h2>
      <div class="set-row"><div class="lbl">기본 서비스<small>홈 화면과 검색에 사용됩니다</small></div><select data-k="service">${services.map((x) => opt(x.id, s.service, x.name)).join('')}</select></div>
      <div class="set-row"><div class="lbl">콘텐츠 언어</div><select data-k="language">${[['ko', '한국어'], ['en', 'English'], ['ja', '日本語'], ['zh', '中文'], ['de', 'Deutsch'], ['fr', 'Français'], ['es', 'Español']].map(([v, l]) => opt(v, s.language, l)).join('')}</select></div>
      <div class="set-row"><div class="lbl">콘텐츠 국가<small>인기 동영상 지역</small></div><select data-k="country">${[['KR', '대한민국'], ['US', '미국'], ['JP', '일본'], ['GB', '영국'], ['DE', '독일'], ['FR', '프랑스'], ['TW', '대만'], ['IN', '인도']].map(([v, l]) => opt(v, s.country, l)).join('')}</select></div>
    </div>
    <div class="set-group"><h2>재생</h2>
      <div class="set-row"><div class="lbl">최대 화질<small>자동 화질이 이 해상도를 넘지 않습니다</small></div><select data-k="quality">${[['0', '제한 없음'], ['2160', '2160p (4K)'], ['1440', '1440p'], ['1080', '1080p'], ['720', '720p'], ['480', '480p'], ['360', '360p']].map(([v, l]) => opt(v, s.quality, l)).join('')}</select></div>
      <div class="set-row"><div class="lbl">선호 코덱<small>AVC는 CPU 사용량이 낮고, VP9/AV1은 화질이 좋습니다</small></div><select data-k="codec">${[['auto', '자동'], ['avc', 'H.264 (AVC)'], ['av1', 'AV1']].map(([v, l]) => opt(v, s.codec, l)).join('')}</select></div>
      <div class="set-row"><div class="lbl">자동 재생<small>끝나면 다음 동영상 재생</small></div><label class="switch"><input type="checkbox" data-k="autoplay" ${s.autoplay ? 'checked' : ''}><span></span></label></div>
      <div class="set-row"><div class="lbl">오디오 전용 모드<small>영상 없이 소리만 (데이터·CPU 절약)</small></div><label class="switch"><input type="checkbox" data-k="audioOnly" ${s.audioOnly ? 'checked' : ''}><span></span></label></div>
      <div class="set-row"><div class="lbl">기본 자막</div><select data-k="subtitles">${[['off', '사용 안함'], ['ko', '한국어'], ['en', 'English'], ['ja', '日本語']].map(([v, l]) => opt(v, s.subtitles, l)).join('')}</select></div>
    </div>
    <div class="set-group"><h2>모양</h2>
      <div class="set-row"><div class="lbl">테마</div><select data-k="theme">${[['dark', '다크'], ['light', '라이트']].map(([v, l]) => opt(v, s.theme, l)).join('')}</select></div>
    </div>
    <div class="set-group"><h2>데이터</h2>
      <div class="set-row"><div class="lbl">구독 가져오기/내보내기<small>NewPipe · PipePipe JSON 형식 호환</small></div><a class="btn" href="#/subs">구독 관리</a></div>
      <div class="set-row"><div class="lbl">시청 기록 삭제</div><button class="btn ghost" id="clearhist">${icon('trash')}삭제</button></div>
    </div>
    <div class="set-group"><h2>단축키</h2><div class="kbd-list">
      <kbd>/</kbd><span>검색</span><kbd>K</kbd><span>재생 / 일시정지 (Space)</span><kbd>J</kbd><span>10초 뒤로 · L 10초 앞으로 · ←/→ 5초</span>
      <kbd>↑ ↓</kbd><span>볼륨</span><kbd>M</kbd><span>음소거</span><kbd>F</kbd><span>전체화면</span><kbd>T</kbd><span>영화관 모드</span><kbd>I</kbd><span>PIP</span>
      <kbd>C</kbd><span>자막</span><kbd>Shift + N</kbd><span>다음 동영상</span><kbd>Shift + &lt; &gt;</kbd><span>재생 속도</span><kbd>0-9</kbd><span>구간 이동 (10% 단위)</span><kbd>Ctrl + B</kbd><span>사이드바</span>
    </div></div>
    <div style="color:var(--text-3);font-size:12.5px">PipePipe Desktop · PipePipeExtractor 기반 · 모든 데이터는 이 컴퓨터에만 저장됩니다.</div></div>`;
  $$('[data-k]', main).forEach((el) => el.addEventListener('change', () => {
    const k = el.dataset.k;
    s[k] = el.type === 'checkbox' ? el.checked : (k === 'service' ? +el.value : el.value);
    if (k === 'theme') applyTheme();
    if (k === 'service') paintService();
    if (k === 'language' || k === 'country') cache.clear();
    save('settings', 0);
    toast('저장됨');
  }));
  $('#clearhist').onclick = () => { if (confirm('시청 기록을 모두 삭제할까요?')) { docs.history = []; save('history', 0); toast('삭제됨'); } };
};

// ------------------------------------------------------------------ watch
views.watch = async (p, signal) => {
  const url = p.get('url');
  const q = p.get('q');
  const qi = p.get('i') != null ? +p.get('i') : -1;
  const t = p.get('t');
  main.innerHTML = `<div class="watch ${S().theater ? 'theater' : ''}">
    <div class="player-slot" id="slot"></div>
    <div class="watch-main"><div id="info">
      <div class="sk sk-line" style="height:24px;width:70%;margin-top:18px"></div><div class="sk sk-line" style="width:40%"></div></div></div>
    <aside class="watch-side" id="side"><div class="rows">${Array.from({ length: 8 }, () => '<div class="row compact"><div class="thumb sk"></div><div class="meta-text"><div class="sk sk-line"></div><div class="sk sk-line" style="width:50%"></div></div></div>').join('')}</div></aside></div>`;
  $('#slot').appendChild(player.el);
  $('#mini-dock').hidden = true;
  const same = nowPlaying.url === url && nowPlaying.data;
  nowPlaying.queue = q; nowPlaying.index = qi;
  let d;
  try {
    d = same ? nowPlaying.data : await api('stream', { url }, { signal });
  } catch (e) {
    if (e.name === 'AbortError') return;
    $('#info').innerHTML = `<div style="margin-top:16px">${errorBox(e)}</div>`;
    $('#side').innerHTML = '';
    player.stop(); player.fail(e.message);
    return;
  }
  if (signal.aborted) return;
  if (!same) {
    nowPlaying.url = url; nowPlaying.data = d;
    sessionWatched.add(url); sessionWatched.add(d.url);
    const h = historyFor(d.url);
    const startAt = t ? +t : (h && h.pos > 10 && (!d.duration || h.pos < d.duration - 15) ? h.pos : 0);
    recordHistory(d);
    player.load(d, { startAt, autoplay: true });
  }
  document.title = d.name + ' · PipePipe';
  renderWatchInfo(d, signal);
  renderSide(d, q, qi);
  player.el.focus({ preventScroll: true });
};

function renderWatchInfo(d, signal) {
  const ch = { url: d.uploader.url, name: d.uploader.name, avatar: d.uploader.avatar, service: d.service };
  const date = d.date ? new Intl.DateTimeFormat(loc(), { dateStyle: 'medium' }).format(new Date(d.date)) : (d.dateText || '');
  $('#info').innerHTML = `
    <h1 class="vtitle">${esc(d.name)}</h1>
    <div class="owner">
      <a href="${route('channel', { url: ch.url })}">${ch.avatar ? `<img class="avatar" src="${esc(ch.avatar)}" alt="" referrerpolicy="no-referrer">` : '<div class="avatar"></div>'}</a>
      <div><a class="owner-name" href="${route('channel', { url: ch.url })}">${esc(ch.name)}${d.uploader.verified ? icon('verified', 'verified') : ''}</a>
        <div class="owner-subs">${d.uploader.subs > 0 ? '구독자 ' + fmtCount(d.uploader.subs) + '명' : ''}</div></div>
      <span id="subbtn" style="margin-left:8px">${ch.url ? subButton(ch) : ''}</span>
      <div class="spacer"></div>
      <div class="actions">
        ${d.likes > 0 ? `<span class="pill-stat">${icon('thumb')}${fmtCount(d.likes)}</span>` : ''}
        <button class="btn" id="later">${icon('clock')}나중에 보기</button>
        <button class="btn" id="addpl">${icon('plus')}저장</button>
        <button class="btn" id="share">${icon('share')}링크 복사</button>
      </div>
    </div>
    ${d.chapters.length ? `<div class="chapters">${d.chapters.map((c) => `<button class="chapter" data-t="${c.t}">${c.thumb ? `<img src="${esc(c.thumb)}" alt="" loading="lazy" referrerpolicy="no-referrer">` : ''}<div class="t">${fmtTime(c.t)}</div><div class="n">${esc(c.title)}</div></button>`).join('')}</div>` : ''}
    <div class="desc-box" id="desc">
      <div class="desc-stats">${d.live ? '실시간 스트리밍' : fmtViews(d.views)}<span class="dot"></span>${esc(date)}${d.category ? `<span class="dot"></span>${esc(d.category)}` : ''}</div>
      <div class="desc-body">${richText(d.description.text, d.description.html)}</div>
      ${d.tags.length ? `<div class="tags">${d.tags.slice(0, 12).map((tg) => `<a href="${route('search', { q: tg })}">#${esc(tg)}</a>`).join('')}</div>` : ''}
      <button class="desc-toggle" id="desc-toggle">더보기</button>
    </div>
    <div id="comments"><div class="section-title">댓글</div><div class="spinner"></div></div>`;
  const desc = $('#desc');
  const body = $('.desc-body', desc);
  if (body.scrollHeight <= body.clientHeight + 4 && !d.tags.length) $('#desc-toggle').remove();
  else $('#desc-toggle').onclick = () => { desc.classList.toggle('open'); $('#desc-toggle').textContent = desc.classList.contains('open') ? '간략히' : '더보기'; };
  $('#subbtn').onclick = () => { if (ch.url) { toggleSub(ch); $('#subbtn').innerHTML = subButton(ch); } };
  $('#later').onclick = () => addToWatchLater({ ...d, uploader: d.uploader.name, uploaderUrl: d.uploader.url });
  $('#addpl').onclick = (e) => addToPlaylistMenu({ ...d, uploader: d.uploader.name, uploaderUrl: d.uploader.url }, e.currentTarget);
  $('#share').onclick = () => { navigator.clipboard.writeText(d.url).then(() => toast('링크를 복사했습니다')); };
  $$('.chapter').forEach((b) => b.onclick = () => { player.seek(+b.dataset.t); player.play(); });

  // comments load lazily when scrolled near
  const host = $('#comments');
  const io = new IntersectionObserver(async (en) => {
    if (!en[0].isIntersecting) return;
    io.disconnect();
    try {
      const c = await api('comments', { url: d.url }, { signal, ttl: 5 * 60e3 });
      if (c.disabled) { host.innerHTML = '<div class="section-title">댓글</div><div class="sub" style="color:var(--text-3)">댓글이 사용 중지되었습니다.</div>'; return; }
      host.innerHTML = `<div class="section-title">댓글</div><div id="clist">${c.items.map(comment).join('') || '<div style="color:var(--text-3)">댓글이 없습니다</div>'}</div>`;
      paginate($('#clist'), c.next, comment, signal);
    } catch (e) { if (e.name !== 'AbortError') host.innerHTML = '<div class="section-title">댓글</div><div style="color:var(--text-3)">댓글을 불러오지 못했습니다</div>'; }
  }, { root: main, rootMargin: '600px' });
  io.observe(host);
  signal.addEventListener('abort', () => io.disconnect());
}

function comment(c) {
  return `<div class="comment">${c.avatar ? `<img class="avatar" src="${esc(c.avatar)}" alt="" loading="lazy" referrerpolicy="no-referrer">` : '<div class="avatar"></div>'}
    <div style="min-width:0"><div class="comment-head"><b>${esc(c.author)}</b><span>${esc(fmtAgo(c.at, c.date))}</span></div>
    <div class="comment-text">${richText(c.text, c.html)}</div>
    <div class="comment-foot">${c.likes > 0 || c.likesText ? `<span style="display:flex;gap:5px;align-items:center">${icon('thumb')}${esc(c.likesText || fmtCount(c.likes))}</span>` : ''}${c.replies > 0 ? `<span>답글 ${c.replies}개</span>` : ''}</div></div></div>`;
}

function renderSide(d, q, qi) {
  const items = queueItems(q);
  let html = '';
  if (items && items.length) {
    const title = q === 'later' ? '나중에 볼 동영상' : q.startsWith('local:') ? (docs.playlists.lists.find((l) => l.id === q.slice(6)) || {}).name : '재생목록';
    html += `<div class="desc-box" style="margin:0 0 18px;padding:12px;max-height:420px;overflow:auto"><div class="desc-stats" style="margin-bottom:10px">${esc(title || '')} <span style="color:var(--text-3);font-weight:500">${qi + 1} / ${items.length}</span></div>
      <div class="rows">${items.map((it, i) => row(it, { compact: true, queue: q, index: i })).join('')}</div></div>`;
  }
  html += `<div class="autoplay-row"><span>다음 동영상</span><label class="switch" title="자동 재생"><input type="checkbox" id="autoplay" ${S().autoplay ? 'checked' : ''}><span></span></label></div>
    <div class="rows">${d.related.map((it) => row(it, { compact: true })).join('') || '<div style="color:var(--text-3)">관련 동영상이 없습니다</div>'}</div>`;
  $('#side').innerHTML = html;
  $('#autoplay').onchange = (e) => { S().autoplay = e.target.checked; save('settings'); };
  const cur = $$('#side .desc-box .row')[qi];
  if (cur) { cur.style.background = 'var(--bg-active)'; cur.style.borderRadius = '10px'; cur.scrollIntoView({ block: 'nearest' }); }
}

// ------------------------------------------------------------------ router
let currentNav = new AbortController();
const scrollPos = new Map();
async function router() {
  const hash = location.hash || '#/';
  const [path, qs] = hash.slice(2).split('?');
  const name = path || 'home';
  const p = new URLSearchParams(qs || '');
  scrollPos.set(router.last, main.scrollTop);
  currentNav.abort();
  currentNav = new AbortController();
  $$('.menu.floating').forEach((m) => m.remove());
  $$('.nav-item').forEach((a) => a.classList.toggle('active', a.dataset.route === name));
  if (name !== 'watch') {
    document.title = 'PipePipe';
    if (player && player.el.isConnected && !$('#mini-dock').contains(player.el)) dockToMini();
  }
  const view = views[name] || views.home;
  main.scrollTop = 0;
  await view(p, currentNav.signal);
  const back = router.popped && scrollPos.get(hash);
  if (back) requestAnimationFrame(() => (main.scrollTop = back));
  router.last = hash;
  router.popped = false;
}
window.addEventListener('popstate', () => (router.popped = true));
window.addEventListener('hashchange', router);

// ------------------------------------------------------------------ search box + suggestions
function setupSearch() {
  const form = $('#search-form'), input = $('#search-input'), box = $('#suggest');
  let items = [], active = -1, timer, seq = 0;
  const hide = () => { box.hidden = true; active = -1; };
  const paint = () => {
    box.innerHTML = items.map((s, i) => `<li class="${i === active ? 'active' : ''}" data-i="${i}">${icon('search')}<span>${highlight(s, input.value)}</span></li>`).join('');
    box.hidden = !items.length;
  };
  const go = (q) => {
    q = q.trim();
    if (!q) return;
    hide();
    input.blur();
    location.hash = routeForUrl(q) || route('search', { q });
  };
  input.addEventListener('input', () => {
    clearTimeout(timer);
    const q = input.value.trim();
    if (!q || /^https?:/.test(q)) { items = []; paint(); return; }
    timer = setTimeout(async () => {
      const my = ++seq;
      try {
        const r = await api('suggest', { q, service: S().service }, { ttl: 60e3 });
        if (my !== seq) return;
        items = r.slice(0, 10); active = -1; paint();
      } catch (e) { /* ignore */ }
    }, 120);
  });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      if (!items.length) return;
      e.preventDefault();
      // cycle through -1 (typed text) .. items.length-1
      const n = items.length + 1;
      active = ((active + 1 + (e.key === 'ArrowDown' ? 1 : -1)) % n + n) % n - 1;
      paint();
      if (active >= 0) input.value = items[active];
    } else if (e.key === 'Enter') {
      e.preventDefault();
      go(active >= 0 ? items[active] : input.value);
    } else if (e.key === 'Escape') { hide(); input.blur(); }
  });
  input.addEventListener('blur', () => setTimeout(hide, 150));
  input.addEventListener('focus', () => { if (items.length) paint(); });
  box.addEventListener('mousedown', (e) => { const li = e.target.closest('li'); if (li) { e.preventDefault(); go(items[+li.dataset.i]); } });
  form.addEventListener('submit', (e) => { e.preventDefault(); go(input.value); });
}
function highlight(s, q) {
  const i = s.toLowerCase().indexOf(q.toLowerCase().trim());
  if (i < 0 || !q.trim()) return esc(s);
  const n = q.trim().length;
  return esc(s.slice(0, i)) + esc(s.slice(i, i + n)) + '<b>' + esc(s.slice(i + n)) + '</b>';
}

// ------------------------------------------------------------------ service switcher
function paintService() {
  const svc = services.find((s) => s.id === +S().service) || services[0];
  $('#service-name').textContent = svc ? svc.name : '';
  $('#service-menu').innerHTML = services.map((s) => `<button data-svc="${s.id}" class="${s.id === +S().service ? 'on' : ''}">${esc(s.name)}</button>`).join('');
}
function setupServiceMenu() {
  const btn = $('#service-btn'), menu = $('#service-menu');
  btn.onclick = (e) => { e.stopPropagation(); menu.hidden = !menu.hidden; };
  document.addEventListener('click', () => (menu.hidden = true));
  menu.addEventListener('click', (e) => {
    const b = e.target.closest('[data-svc]');
    if (!b) return;
    S().service = +b.dataset.svc;
    save('settings', 0);
    paintService();
    menu.hidden = true;
    if (!location.hash || location.hash.startsWith('#/?') || location.hash === '#/') router(); else location.hash = '#/';
  });
}

// ------------------------------------------------------------------ theme / sidebar
function applyTheme() {
  document.documentElement.dataset.theme = S().theme;
  try { localStorage.setItem('pp.theme', S().theme); } catch (e) { /* private */ }
  $('#theme-toggle').innerHTML = icon(S().theme === 'dark' ? 'moon' : 'sun');
}
function applySidebar() { $('#app').classList.toggle('collapsed', !S().sidebar); }

// ------------------------------------------------------------------ global interactions
function setupGlobal() {
  // thumbnails fade in once decoded
  document.addEventListener('load', (e) => { if (e.target.tagName === 'IMG') e.target.classList.add('loaded'); }, true);
  document.addEventListener('error', (e) => { if (e.target.tagName === 'IMG') e.target.style.visibility = 'hidden'; }, true);

  document.addEventListener('click', (e) => {
    const act = e.target.closest('[data-act]');
    if (act) {
      e.preventDefault(); e.stopPropagation();
      const host = act.closest('[data-item]');
      const it = host ? JSON.parse(host.dataset.item) : null;
      const a = act.dataset.act;
      if (a === 'later' && it) addToWatchLater(it);
      if (a === 'addpl' && it) addToPlaylistMenu(it, act);
      if (a === 'rmhist' && it) { docs.history = docs.history.filter((h) => h.url !== it.url); save('history'); host.remove(); }
      if (a === 'rmlocal' && it) {
        const id = $('#list').dataset.local;
        const list = id === 'later' ? docs.playlists.watchLater : docs.playlists.lists.find((l) => l.id === id).items;
        const i = list.findIndex((x) => x.url === it.url);
        if (i >= 0) list.splice(i, 1);
        save('playlists'); host.remove();
      }
      return;
    }
    const inner = e.target.closest('[data-href]');
    if (inner) { e.preventDefault(); e.stopPropagation(); location.hash = inner.dataset.href; return; }
    const seek = e.target.closest('[data-seek]');
    if (seek) { e.preventDefault(); player.seek(+seek.dataset.seek); player.play(); $('#main').scrollTo({ top: 0, behavior: 'smooth' }); return; }
    const ext = e.target.closest('a[data-ext]');
    if (ext) {
      e.preventDefault();
      const r = routeForUrl(ext.href);
      if (r && /youtube\.com|youtu\.be|bilibili|nicovideo/.test(ext.href)) location.hash = r;
      else window.open(ext.href, '_blank', 'noopener');
    }
  });

  // hover prefetch: warms the server-side stream cache so opening a video is near instant
  let pfTimer, inflight = 0;
  const prefetched = new Set();
  document.addEventListener('mouseover', (e) => {
    const el = e.target.closest('[data-prefetch]');
    clearTimeout(pfTimer);
    if (!el) return;
    const url = el.dataset.prefetch;
    if (prefetched.has(url)) return;
    pfTimer = setTimeout(() => {
      if (inflight >= 2) return;
      prefetched.add(url); inflight++;
      fetch('/api/stream?url=' + encodeURIComponent(url)).catch(() => {}).finally(() => inflight--);
    }, 180);
  });

  $('#sidebar-toggle').onclick = () => { S().sidebar = !S().sidebar; applySidebar(); save('settings'); };
  $('#theme-toggle').onclick = () => { S().theme = S().theme === 'dark' ? 'light' : 'dark'; applyTheme(); save('settings'); };
  $('#mini-expand').onclick = () => { if (nowPlaying.url) location.hash = route('watch', nowPlaying.queue ? { url: nowPlaying.url, q: nowPlaying.queue, i: nowPlaying.index } : { url: nowPlaying.url }); };
  $('#mini-title').onclick = () => $('#mini-expand').click();
  $('#mini-close').onclick = closeMini;

  document.addEventListener('keydown', (e) => {
    const tag = (e.target.tagName || '').toLowerCase();
    if (tag === 'input' || tag === 'textarea' || tag === 'select' || e.target.isContentEditable) return;
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'b') { e.preventDefault(); $('#sidebar-toggle').click(); return; }
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    if (e.key === '/') { e.preventDefault(); $('#search-input').focus(); $('#search-input').select(); return; }
    const active = player && nowPlaying.url && player.el.isConnected;
    if (!active) return;
    const k = e.key;
    const handled = () => { e.preventDefault(); player.wake && player.wake(); };
    if (k === ' ' || k === 'k' || k === 'K') { handled(); player.toggle(); }
    else if (k === 'j' || k === 'J') { handled(); player.seekBy(-10); }
    else if (k === 'l' || k === 'L') { handled(); player.seekBy(10); }
    else if (k === 'ArrowLeft') { handled(); player.seekBy(-5); }
    else if (k === 'ArrowRight') { handled(); player.seekBy(5); }
    else if (k === 'ArrowUp' && document.activeElement === player.el || k === 'ArrowUp' && document.fullscreenElement) { handled(); player.volumeBy(0.05); }
    else if (k === 'ArrowDown' && document.activeElement === player.el || k === 'ArrowDown' && document.fullscreenElement) { handled(); player.volumeBy(-0.05); }
    else if (k === 'f' || k === 'F') { handled(); player.fullscreen(); }
    else if (k === 'm' || k === 'M') { handled(); player.setMuted(!player.isMuted()); player.osd(player.isMuted() ? '음소거' : '음소거 해제'); }
    else if (k === 't' || k === 'T') { handled(); player.emit('theater'); }
    else if (k === 'i' || k === 'I') { handled(); player.pip(); }
    else if (k === 'c' || k === 'C') { handled(); player.toggleCC(); }
    else if (k === 'N' && e.shiftKey) { handled(); playNext(); }
    else if (k === '>' ) { handled(); player.rateBy(1); }
    else if (k === '<') { handled(); player.rateBy(-1); }
    else if (/^[0-9]$/.test(k)) { handled(); player.seek(player.duration() * (+k / 10)); }
    else if (k === 'Escape') { player.closeMenu(); }
  });
}

// ------------------------------------------------------------------ boot
(async function boot() {
  await loadDocs();
  applyTheme();
  applySidebar();
  try { services = await api('services'); } catch (e) { services = [{ id: 0, name: 'YouTube', kiosks: [], defaultKiosk: null }]; }
  paintService();
  renderNavSubs();
  setupSearch();
  setupServiceMenu();
  setupGlobal();
  const waitShaka = () => new Promise((r) => { if (window.shaka) r(); else window.addEventListener('load', r, { once: true }); });
  await waitShaka();
  if (window.shaka) shaka.polyfill.installAll();
  initPlayer();
  router();
})();
