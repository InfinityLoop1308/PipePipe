// PipePipe Desktop player: shaka (DASH/HLS via local proxy) + progressive / split-stream fallback.
// One persistent instance; the root element moves between the watch page and the mini dock.

const $ = (s, r = document) => r.querySelector(s);
const icon = (id) => `<svg><use href="#i-${id}"/></svg>`;
const SPEEDS = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2, 2.5, 3];

export function fmtTime(sec) {
  if (!isFinite(sec) || sec < 0) sec = 0;
  sec = Math.floor(sec);
  const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
  return (h ? h + ':' + String(m).padStart(2, '0') : m) + ':' + String(s).padStart(2, '0');
}

export class Player {
  constructor(settings) {
    this.settings = settings;
    this.data = null;
    this.shaka = null;
    this.audio = null;     // companion <audio> for split streams
    this.mode = null;
    this.handlers = {};
    this.textTracks = [];
    this.ccLang = null;
    this.build();
  }

  on(evt, fn) { this.handlers[evt] = fn; }
  emit(evt, ...a) { this.handlers[evt] && this.handlers[evt](...a); }

  build() {
    const el = document.createElement('div');
    el.className = 'player idle';
    el.tabIndex = 0;
    el.innerHTML = `
      <video playsinline preload="auto"></video>
      <div class="p-poster"></div>
      <div class="top-shade"></div><div class="ptitle"></div>
      <div class="shade"></div>
      <div class="p-spin"></div>
      <div class="center-flash"></div>
      <div class="p-osd"></div>
      <div class="p-error"><div><b>재생할 수 없습니다</b><div class="p-error-msg" style="opacity:.75;margin-top:6px"></div></div></div>
      <div class="p-next"></div>
      <div class="controls">
        <div class="bar">
          <div class="bar-track">
            <div class="bar-buf"></div><div class="bar-hover"></div><div class="bar-fill"></div>
            <div class="bar-marks"></div>
          </div>
          <div class="bar-knob"></div>
          <div class="bar-tip"></div>
        </div>
        <div class="ctl-row">
          <button class="pbtn" data-a="play" title="재생 (k)">${icon('play')}</button>
          <button class="pbtn hide-mini" data-a="next" title="다음 동영상 (Shift+N)">${icon('next')}</button>
          <div class="vol">
            <button class="pbtn" data-a="mute" title="음소거 (m)">${icon('vol')}</button>
            <div class="vol-slider"><input type="range" min="0" max="1" step="0.01" aria-label="볼륨"></div>
          </div>
          <div class="time"><span class="t-cur">0:00</span> / <span class="t-dur">0:00</span></div>
          <div class="chapter-name"></div>
          <div class="grow"></div>
          <button class="pbtn hide-mini" data-a="cc" title="자막 (c)">${icon('cc')}</button>
          <button class="pbtn" data-a="settings" title="설정">${icon('gear')}</button>
          <button class="pbtn hide-mini" data-a="pip" title="PIP (i)">${icon('pip')}</button>
          <button class="pbtn hide-mini" data-a="theater" title="영화관 모드 (t)">${icon('theater')}</button>
          <button class="pbtn" data-a="fs" title="전체화면 (f)">${icon('fs')}</button>
        </div>
      </div>`;
    this.el = el;
    this.video = $('video', el);
    this.bar = $('.bar', el);
    this.wire();
  }

  wire() {
    const v = this.video, el = this.el;
    let idleTimer;
    const wake = () => {
      el.classList.remove('idle', 'hide-cursor');
      clearTimeout(idleTimer);
      idleTimer = setTimeout(() => {
        if (!v.paused && !this.menuOpen) el.classList.add('idle', 'hide-cursor');
      }, 2600);
    };
    this.wake = wake;
    el.addEventListener('mousemove', wake);
    el.addEventListener('mouseleave', () => { if (!v.paused) el.classList.add('idle'); });

    el.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-a]');
      if (btn) { e.stopPropagation(); this.action(btn.dataset.a, btn); return; }
      if (e.target.closest('.controls, .pmenu, .p-next')) return;
      if (this.menuOpen) { this.closeMenu(); return; }
      this.toggle();
    });
    el.addEventListener('dblclick', (e) => {
      if (e.target.closest('.controls, .pmenu, .p-next')) return;
      this.fullscreen();
    });

    // seek bar
    const pct = (e) => {
      const r = $('.bar-track', el).getBoundingClientRect();
      return Math.min(1, Math.max(0, (e.clientX - r.left) / r.width));
    };
    const tip = $('.bar-tip', el);
    this.bar.addEventListener('mousemove', (e) => {
      const p = pct(e), d = this.duration();
      $('.bar-hover', el).style.width = p * 100 + '%';
      tip.style.left = p * 100 + '%';
      const ch = this.chapterAt(p * d);
      tip.innerHTML = fmtTime(p * d) + (ch ? `<small>${escapeHtml(ch.title)}</small>` : '');
    });
    this.bar.addEventListener('pointerdown', (e) => {
      this.bar.setPointerCapture(e.pointerId);
      this.bar.classList.add('drag');
      this.dragging = true;
      const move = (ev) => this.paintProgress(pct(ev) * this.duration());
      move(e);
      const up = (ev) => {
        this.bar.classList.remove('drag');
        this.dragging = false;
        this.seek(pct(ev) * this.duration());
        this.bar.removeEventListener('pointermove', move);
        this.bar.removeEventListener('pointerup', up);
      };
      this.bar.addEventListener('pointermove', move);
      this.bar.addEventListener('pointerup', up);
    });

    const vol = $('.vol-slider input', el);
    vol.addEventListener('input', () => { this.setVolume(+vol.value); });

    v.addEventListener('play', () => { this.syncAudio('play'); this.paintPlay(); this.el.classList.add('started'); wake(); });
    v.addEventListener('pause', () => { this.syncAudio('pause'); this.paintPlay(); el.classList.remove('idle'); });
    v.addEventListener('waiting', () => { el.classList.add('buffering'); if (this.audio) this.audio.pause(); });
    v.addEventListener('playing', () => { el.classList.remove('buffering'); this.syncAudio('play'); });
    v.addEventListener('canplay', () => el.classList.remove('buffering'));
    v.addEventListener('seeking', () => this.syncAudio('seek'));
    v.addEventListener('ratechange', () => { if (this.audio) this.audio.playbackRate = v.playbackRate; });
    v.addEventListener('volumechange', () => this.paintVolume());
    v.addEventListener('timeupdate', () => this.onTime());
    v.addEventListener('progress', () => this.paintBuffer());
    v.addEventListener('loadedmetadata', () => this.onTime());
    v.addEventListener('ended', () => this.onEnded());
    v.addEventListener('error', () => {
      if (this.mode === 'file' || this.mode === 'dual') this.fail(v.error ? '미디어 오류 ' + v.error.code : '');
    });
    document.addEventListener('fullscreenchange', () => {
      const fs = document.fullscreenElement === el;
      el.classList.toggle('fs', fs);
      $('[data-a="fs"]', el).innerHTML = icon(fs ? 'fs-exit' : 'fs');
    });
    v.addEventListener('enterpictureinpicture', () => this.emit('pip', true));
    v.addEventListener('leavepictureinpicture', () => this.emit('pip', false));
  }

  // ------------------------------------------------------------ loading
  async load(data, { startAt = 0, autoplay = true } = {}) {
    await this.stop();
    this.data = data;
    this.el.classList.remove('errored', 'started');
    this.el.classList.add('buffering');
    $('.p-poster', this.el).style.backgroundImage = data.thumb ? `url("${data.thumb}")` : '';
    $('.ptitle', this.el).textContent = data.name || '';
    $('.p-next', this.el).classList.remove('show');
    this.ccLang = null;
    this.paintChapters();
    this.setVolume(this.settings.volume ?? 1, true);
    this.video.muted = !!this.settings.muted;
    this.video.playbackRate = 1;

    const p = data.player || {};
    const want = this.settings.audioOnly ? 'audio' : p.mode;
    try {
      if (want === 'audio' && p.audio && p.audio.length) {
        this.mode = 'file';
        const a = [...p.audio].sort((x, y) => (y.kbps || 0) - (x.kbps || 0))[0];
        this.sources = []; this.video.src = a.src;
      } else if ((p.mode === 'dash' || p.mode === 'hls') && window.shaka && shaka.Player.isBrowserSupported()) {
        await this.loadShaka(p.src, startAt);
      } else {
        this.loadFile(p, startAt);
      }
      if (startAt && this.mode !== 'shaka') this.video.currentTime = startAt;
      this.setupSubtitles(data.subtitles || []);
      this.mediaSession();
      if (autoplay) await this.video.play().catch(() => this.paintPlay());
    } catch (e) {
      console.error(e);
      // shaka failed (codec / manifest) -> fall back to plain files
      if (this.mode === 'shaka' && (p.videoOnly?.length || p.muxed?.length)) {
        try {
          await this.destroyShaka();
          this.loadFile(p, startAt);
          this.setupSubtitles(data.subtitles || []);
          if (autoplay) await this.video.play().catch(() => {});
          return;
        } catch (e2) { console.error(e2); }
      }
      this.fail(e && (e.message || e.code) ? (e.message || 'shaka ' + e.code) : String(e));
    }
  }

  async loadShaka(src, startAt) {
    this.mode = 'shaka';
    const pl = new shaka.Player();
    await pl.attach(this.video);
    this.shaka = pl;
    const maxH = +this.settings.quality || 0;
    pl.configure({
      abr: { enabled: true, restrictions: maxH ? { maxHeight: maxH } : {} },
      streaming: { bufferingGoal: 40, rebufferingGoal: 1.5, bufferBehind: 30, retryParameters: { maxAttempts: 4, baseDelay: 400 } },
      manifest: { retryParameters: { maxAttempts: 3 } },
      preferredAudioLanguage: this.settings.audioLanguage || '',
      preferredVideoCodecs: this.settings.codec === 'avc' ? ['avc1'] : this.settings.codec === 'av1' ? ['av01'] : [],
    });
    pl.addEventListener('error', (ev) => {
      const err = ev.detail;
      console.error('shaka', err);
      if (err && err.severity === 2) this.fail('shaka ' + err.code);
    });
    pl.addEventListener('buffering', (ev) => this.el.classList.toggle('buffering', ev.buffering));
    pl.addEventListener('variantchanged', () => this.paintQuality());
    pl.addEventListener('adaptation', () => this.paintQuality());
    await pl.load(location.origin + src, startAt || null);
  }

  loadFile(p, startAt) {
    const maxH = +this.settings.quality || 1080;
    const pick = (list) => {
      const sorted = [...list].sort((a, b) => (b.h || 0) - (a.h || 0));
      return sorted.find((s) => (s.h || 0) <= maxH) || sorted[sorted.length - 1];
    };
    const playable = (s) => !s.mime || this.video.canPlayType(s.mime + (s.codec ? `; codecs="${s.codec}"` : '')) !== '';
    if (p.muxed && p.muxed.length) {
      this.mode = 'file';
      this.sources = p.muxed.filter(playable);
      const s = pick(this.sources.length ? this.sources : p.muxed);
      this.current = s;
      this.video.src = s.src;
    } else if (p.videoOnly && p.videoOnly.length && p.audio && p.audio.length) {
      this.mode = 'dual';
      this.sources = p.videoOnly.filter(playable);
      if (!this.sources.length) this.sources = p.videoOnly;
      const s = pick(this.sources);
      this.current = s;
      const a = [...p.audio].filter(playable).sort((x, y) => (y.kbps || 0) - (x.kbps || 0))[0] || p.audio[0];
      this.audio = new Audio();
      this.audio.preload = 'auto';
      this.audio.src = a.src;
      this.audio.volume = this.video.volume;
      this.audio.muted = this.video.muted;
      this.video.muted = true; // the audio element carries the sound
      this.video.src = s.src;
      if (startAt) this.audio.currentTime = startAt;
      this.driftTimer = setInterval(() => {
        if (!this.audio || this.video.paused) return;
        const d = this.audio.currentTime - this.video.currentTime;
        if (Math.abs(d) > 0.25) this.audio.currentTime = this.video.currentTime;
      }, 1000);
    } else if (p.audio && p.audio.length) {
      this.mode = 'file';
      this.sources = [];
      this.video.src = p.audio[0].src;
    } else {
      throw new Error('재생 가능한 스트림이 없습니다');
    }
  }

  syncAudio(what) {
    const a = this.audio, v = this.video;
    if (!a) return;
    if (what === 'play') { a.currentTime = v.currentTime; a.play().catch(() => {}); }
    else if (what === 'pause') a.pause();
    else if (what === 'seek') a.currentTime = v.currentTime;
  }

  async destroyShaka() {
    if (this.shaka) {
      const s = this.shaka; this.shaka = null;
      try { await s.destroy(); } catch (e) { /* ignore */ }
    }
  }

  async stop() {
    clearInterval(this.driftTimer);
    if (this.audio) { this.audio.pause(); this.audio.removeAttribute('src'); this.audio.load(); this.audio = null; }
    await this.destroyShaka();
    this.video.pause();
    [...this.video.querySelectorAll('track')].forEach((t) => t.remove());
    this.video.removeAttribute('src');
    this.video.load();
    this.mode = null;
    this.textTracks = [];
    this.closeMenu();
  }

  fail(msg) {
    this.el.classList.remove('buffering');
    this.el.classList.add('errored');
    $('.p-error-msg', this.el).textContent = msg || '';
  }

  // ------------------------------------------------------------ actions
  action(a, btn) {
    switch (a) {
      case 'play': this.toggle(); break;
      case 'next': this.emit('next'); break;
      case 'mute': this.setMuted(!this.isMuted()); break;
      case 'fs': this.fullscreen(); break;
      case 'pip': this.pip(); break;
      case 'theater': this.emit('theater'); break;
      case 'cc': this.toggleCC(); break;
      case 'settings': this.menuOpen === 'settings' ? this.closeMenu() : this.openSettings(); break;
    }
  }

  toggle() {
    if (this.video.paused) this.video.play().catch(() => {}); else this.video.pause();
    this.flash(this.video.paused ? 'pause' : 'play');
  }
  play() { return this.video.play().catch(() => {}); }
  pause() { this.video.pause(); }
  duration() {
    const d = this.video.duration;
    if (isFinite(d) && d > 0) return d;
    if (this.shaka && this.shaka.isLive()) { const r = this.shaka.seekRange(); return r.end; }
    return this.data?.duration || 0;
  }
  seek(t) {
    const d = this.duration();
    this.video.currentTime = Math.max(0, d ? Math.min(t, d - 0.2) : t);
    this.onTime();
  }
  seekBy(dt) { this.seek(this.video.currentTime + dt); this.osd((dt > 0 ? '+' : '') + dt + '초'); }

  isMuted() { return this.audio ? this.audio.muted : this.video.muted; }
  setMuted(m) {
    if (this.audio) this.audio.muted = m; else this.video.muted = m;
    this.settings.muted = m;
    this.emit('settings');
    this.paintVolume();
  }
  setVolume(vol, silent) {
    vol = Math.min(1, Math.max(0, vol));
    if (this.audio) this.audio.volume = vol;
    this.video.volume = vol;
    if (vol > 0 && this.isMuted() && !silent) this.setMuted(false);
    this.settings.volume = vol;
    if (!silent) this.emit('settings');
    this.paintVolume();
  }
  volumeBy(d) { this.setVolume((this.audio ? this.audio.volume : this.video.volume) + d); this.osd('볼륨 ' + Math.round(this.video.volume * 100) + '%'); }

  setRate(r) { this.video.playbackRate = r; this.osd(r + 'x'); }
  rateBy(dir) {
    const i = SPEEDS.indexOf(this.video.playbackRate);
    const n = SPEEDS[Math.min(SPEEDS.length - 1, Math.max(0, (i < 0 ? 3 : i) + dir))];
    this.setRate(n);
  }

  fullscreen() {
    if (document.fullscreenElement) document.exitFullscreen();
    else this.el.requestFullscreen().catch(() => {});
  }
  async pip() {
    try {
      if (document.pictureInPictureElement) await document.exitPictureInPicture();
      else await this.video.requestPictureInPicture();
    } catch (e) { /* unsupported */ }
  }

  flash(kind) {
    const f = $('.center-flash', this.el);
    f.innerHTML = icon(kind);
    f.classList.remove('go'); void f.offsetWidth; f.classList.add('go');
  }
  osd(text) {
    const o = $('.p-osd', this.el);
    o.textContent = text;
    o.classList.add('show');
    clearTimeout(this.osdT);
    this.osdT = setTimeout(() => o.classList.remove('show'), 900);
  }

  // ------------------------------------------------------------ subtitles
  async setupSubtitles(subs) {
    this.subs = subs;
    $('[data-a="cc"]', this.el).style.display = subs.length ? '' : 'none';
    const preferred = this.settings.subtitles;
    if (preferred && preferred !== 'off') {
      const s = subs.find((x) => !x.auto && x.lang.startsWith(preferred)) || subs.find((x) => x.lang.startsWith(preferred));
      if (s) this.setCC(s.lang);
    }
  }

  async setCC(lang) {
    this.ccLang = lang;
    const v = this.video;
    if (this.shaka) {
      if (!lang) { this.shaka.setTextTrackVisibility(false); }
      else {
        const s = this.subs.find((x) => x.lang === lang);
        let t = this.shaka.getTextTracks().find((x) => x.language === lang && x.label === s.name);
        if (!t) t = await this.shaka.addTextTrackAsync(location.origin + s.src, s.lang, 'subtitles', 'text/vtt', '', s.name);
        this.shaka.selectTextTrack(t);
        this.shaka.setTextTrackVisibility(true);
      }
    } else {
      [...v.textTracks].forEach((t) => (t.mode = 'disabled'));
      if (lang) {
        const s = this.subs.find((x) => x.lang === lang);
        let tr = [...v.querySelectorAll('track')].find((t) => t.srclang === lang);
        if (!tr) {
          tr = document.createElement('track');
          tr.kind = 'subtitles'; tr.srclang = s.lang; tr.label = s.name; tr.src = s.src;
          v.appendChild(tr);
        }
        tr.track.mode = 'showing';
      }
    }
    $('[data-a="cc"]', this.el).classList.toggle('cc-on', !!lang);
  }

  toggleCC() {
    if (!this.subs || !this.subs.length) return;
    if (this.ccLang) { this.setCC(null); this.osd('자막 끔'); return; }
    const pref = this.settings.subtitles && this.settings.subtitles !== 'off' ? this.settings.subtitles : (navigator.language || 'ko').slice(0, 2);
    const s = this.subs.find((x) => !x.auto && x.lang.startsWith(pref)) || this.subs.find((x) => !x.auto) || this.subs[0];
    this.setCC(s.lang);
    this.osd('자막: ' + s.name);
  }

  // ------------------------------------------------------------ menus
  closeMenu() {
    const m = $('.pmenu', this.el);
    if (m) m.remove();
    this.menuOpen = null;
  }

  menu(kind, html) {
    this.closeMenu();
    const m = document.createElement('div');
    m.className = 'pmenu';
    m.innerHTML = html;
    m.addEventListener('click', (e) => e.stopPropagation());
    this.el.appendChild(m);
    this.menuOpen = kind;
    return m;
  }

  qualities() {
    if (this.shaka) {
      const tracks = this.shaka.getVariantTracks();
      const heights = [...new Set(tracks.filter((t) => t.height).map((t) => t.height))].sort((a, b) => b - a);
      const active = tracks.find((t) => t.active);
      return { list: heights.map((h) => ({ label: h + 'p' + (tracks.find((t) => t.height === h && t.frameRate > 30) ? (tracks.find((t) => t.height === h).frameRate > 49 ? '60' : '') : ''), h })),
               auto: this.shaka.getConfiguration().abr.enabled, current: active ? active.height : null };
    }
    if (this.sources && this.sources.length) {
      const seen = new Set();
      const list = this.sources.filter((s) => { if (seen.has(s.q)) return false; seen.add(s.q); return true; })
        .sort((a, b) => (b.h || 0) - (a.h || 0)).map((s) => ({ label: s.q, h: s.h, src: s }));
      return { list, auto: false, current: this.current ? this.current.h : null };
    }
    return { list: [], auto: true, current: null };
  }

  setQuality(h) {
    if (this.shaka) {
      if (h === 'auto') { this.shaka.configure({ abr: { enabled: true } }); }
      else {
        this.shaka.configure({ abr: { enabled: false } });
        const tracks = this.shaka.getVariantTracks().filter((t) => t.height === h).sort((a, b) => b.bandwidth - a.bandwidth);
        const cur = this.shaka.getVariantTracks().find((t) => t.active);
        const sameLang = tracks.find((t) => !cur || t.language === cur.language) || tracks[0];
        if (sameLang) this.shaka.selectVariantTrack(sameLang, true);
      }
    } else if (this.sources) {
      const s = this.sources.find((x) => x.h === h);
      if (!s) return;
      const t = this.video.currentTime, paused = this.video.paused;
      this.current = s;
      this.video.src = s.src;
      this.video.currentTime = t;
      if (!paused) this.video.play().catch(() => {});
    }
    this.closeMenu();
  }

  openSettings() {
    const q = this.qualities();
    const qLabel = q.auto ? '자동' + (q.current ? ` (${q.current}p)` : '') : (q.current ? q.current + 'p' : '-');
    const langs = this.shaka ? this.shaka.getAudioLanguagesAndRoles() : [];
    const m = this.menu('settings', `
      <button class="mi" data-m="quality">화질<span class="v">${qLabel}</span></button>
      <button class="mi" data-m="speed">재생 속도<span class="v">${this.video.playbackRate === 1 ? '보통' : this.video.playbackRate + 'x'}</span></button>
      ${this.subs && this.subs.length ? `<button class="mi" data-m="subs">자막<span class="v">${this.ccLang ? escapeHtml((this.subs.find((s) => s.lang === this.ccLang) || {}).name || this.ccLang) : '사용 안함'}</span></button>` : ''}
      ${langs.length > 1 ? `<button class="mi" data-m="audio">오디오 트랙<span class="v">${langs.length}개</span></button>` : ''}
      <button class="mi" data-m="loop">반복 재생<span class="v">${this.video.loop ? '켬' : '끔'}</span></button>`);
    m.addEventListener('click', (e) => {
      const b = e.target.closest('[data-m]');
      if (!b) return;
      const k = b.dataset.m;
      if (k === 'quality') this.openQuality();
      if (k === 'speed') this.openSpeed();
      if (k === 'subs') this.openSubs();
      if (k === 'audio') this.openAudio(langs);
      if (k === 'loop') { this.video.loop = !this.video.loop; this.closeMenu(); this.osd('반복 ' + (this.video.loop ? '켬' : '끔')); }
    });
  }

  back() { return `<div class="mh"><button class="pbtn" data-back style="width:28px;height:28px">${icon('chev-left')}</button>`; }

  openQuality() {
    const q = this.qualities();
    const m = this.menu('settings', this.back() + '화질</div>' +
      (this.shaka ? `<button class="mi ${q.auto ? 'on' : ''}" data-q="auto">자동</button>` : '') +
      q.list.map((x) => `<button class="mi ${!q.auto && q.current === x.h ? 'on' : ''}" data-q="${x.h}">${x.label}${x.h >= 2160 ? '<span class="v">4K</span>' : x.h >= 1440 ? '<span class="v">QHD</span>' : x.h >= 720 ? '<span class="v">HD</span>' : ''}</button>`).join(''));
    m.addEventListener('click', (e) => {
      if (e.target.closest('[data-back]')) return this.openSettings();
      const b = e.target.closest('[data-q]');
      if (b) this.setQuality(b.dataset.q === 'auto' ? 'auto' : +b.dataset.q);
    });
  }

  openSpeed() {
    const m = this.menu('settings', this.back() + '재생 속도</div>' +
      SPEEDS.map((s) => `<button class="mi ${this.video.playbackRate === s ? 'on' : ''}" data-s="${s}">${s === 1 ? '보통' : s + 'x'}</button>`).join(''));
    m.addEventListener('click', (e) => {
      if (e.target.closest('[data-back]')) return this.openSettings();
      const b = e.target.closest('[data-s]');
      if (b) { this.setRate(+b.dataset.s); this.closeMenu(); }
    });
  }

  openSubs() {
    const m = this.menu('settings', this.back() + '자막</div>' +
      `<button class="mi ${!this.ccLang ? 'on' : ''}" data-l="">사용 안함</button>` +
      this.subs.map((s) => `<button class="mi ${this.ccLang === s.lang ? 'on' : ''}" data-l="${escapeHtml(s.lang)}">${escapeHtml(s.name)}</button>`).join(''));
    m.addEventListener('click', (e) => {
      if (e.target.closest('[data-back]')) return this.openSettings();
      const b = e.target.closest('[data-l]');
      if (b) { this.setCC(b.dataset.l || null); this.closeMenu(); }
    });
  }

  openAudio(langs) {
    const cur = (this.shaka.getVariantTracks().find((t) => t.active) || {}).language;
    const names = new Map(this.shaka.getVariantTracks().map((t) => [t.language, t.label || t.language]));
    const m = this.menu('settings', this.back() + '오디오 트랙</div>' +
      langs.map((l) => `<button class="mi ${cur === l.language ? 'on' : ''}" data-al="${escapeHtml(l.language)}" data-role="${escapeHtml(l.role || '')}">${escapeHtml(names.get(l.language) || l.language)}</button>`).join(''));
    m.addEventListener('click', (e) => {
      if (e.target.closest('[data-back]')) return this.openSettings();
      const b = e.target.closest('[data-al]');
      if (b) { this.shaka.selectAudioLanguage(b.dataset.al, b.dataset.role || undefined); this.closeMenu(); }
    });
  }

  // ------------------------------------------------------------ painting
  paintPlay() {
    $('[data-a="play"]', this.el).innerHTML = icon(this.video.paused ? 'play' : 'pause');
    if ('mediaSession' in navigator) navigator.mediaSession.playbackState = this.video.paused ? 'paused' : 'playing';
  }
  paintVolume() {
    const muted = this.isMuted();
    const vol = this.audio ? this.audio.volume : this.video.volume;
    $('[data-a="mute"]', this.el).innerHTML = icon(muted || vol === 0 ? 'mute' : vol < 0.5 ? 'vol-low' : 'vol');
    $('.vol-slider input', this.el).value = muted ? 0 : vol;
  }
  paintProgress(t) {
    const d = this.duration();
    const p = d ? Math.min(100, (t / d) * 100) : 0;
    $('.bar-fill', this.el).style.width = p + '%';
    $('.bar-knob', this.el).style.left = p + '%';
    $('.t-cur', this.el).textContent = fmtTime(t);
  }
  paintBuffer() {
    const v = this.video, b = v.buffered, d = this.duration();
    let end = 0;
    for (let i = 0; i < b.length; i++) if (b.start(i) <= v.currentTime + 1) end = Math.max(end, b.end(i));
    $('.bar-buf', this.el).style.width = d ? Math.min(100, (end / d) * 100) + '%' : 0;
  }
  paintChapters() {
    const marks = $('.bar-marks', this.el);
    const ch = (this.data && this.data.chapters) || [];
    const d = (this.data && this.data.duration) || 0;
    marks.innerHTML = d ? ch.filter((c) => c.t > 0).map((c) => `<i style="left:${(c.t / d) * 100}%"></i>`).join('') : '';
  }
  paintQuality() { /* reflected lazily in the settings menu */ }

  chapterAt(t) {
    const ch = (this.data && this.data.chapters) || [];
    let cur = null;
    for (const c of ch) if (c.t <= t) cur = c;
    return cur;
  }

  onTime() {
    const t = this.video.currentTime;
    if (!this.dragging) this.paintProgress(t);
    const live = this.data && this.data.live;
    $('.t-dur', this.el).textContent = live ? '실시간' : fmtTime(this.duration());
    const ch = this.chapterAt(t);
    const name = ch ? ch.title : '';
    if (name !== this.lastChapter) {
      this.lastChapter = name;
      $('.chapter-name', this.el).textContent = name ? '· ' + name : '';
      this.emit('chapter', ch);
    }
    this.paintBuffer();
    this.emit('time', t, this.duration());
    this.maybeShowNext(t);
  }

  maybeShowNext(t) {
    const d = this.duration();
    const next = this.handlers.peekNext && this.handlers.peekNext();
    const box = $('.p-next', this.el);
    if (!next || !this.settings.autoplay || this.video.loop || !d || this.data.live) { box.classList.remove('show'); return; }
    const left = d - t;
    if (left < 12 && left > 0.5 && !this.nextDismissed) {
      if (!box.classList.contains('show')) {
        box.innerHTML = `<img src="${next.thumb || ''}" alt=""><div style="min-width:0"><div class="s">다음 동영상 · <b class="cd"></b></div><div class="n">${escapeHtml(next.name)}</div>
          <div class="acts"><button class="go">재생</button><button class="cancel">취소</button></div></div>`;
        $('.go', box).onclick = (e) => { e.stopPropagation(); this.emit('next'); };
        $('.cancel', box).onclick = (e) => { e.stopPropagation(); this.nextDismissed = true; box.classList.remove('show'); };
        box.classList.add('show');
      }
      $('.cd', box).textContent = Math.ceil(left) + '초';
    } else if (left >= 12) {
      box.classList.remove('show');
      this.nextDismissed = false;
    }
  }

  onEnded() {
    this.paintPlay();
    if (this.settings.autoplay && !this.nextDismissed && !this.video.loop) this.emit('next', true);
    this.emit('ended');
  }

  mediaSession() {
    if (!('mediaSession' in navigator) || !this.data) return;
    const d = this.data;
    navigator.mediaSession.metadata = new MediaMetadata({
      title: d.name, artist: d.uploader && d.uploader.name,
      artwork: d.thumb ? [{ src: d.thumb, sizes: '1280x720', type: 'image/jpeg' }] : [],
    });
    const ms = navigator.mediaSession;
    ms.setActionHandler('play', () => this.play());
    ms.setActionHandler('pause', () => this.pause());
    ms.setActionHandler('seekbackward', () => this.seekBy(-10));
    ms.setActionHandler('seekforward', () => this.seekBy(10));
    ms.setActionHandler('nexttrack', () => this.emit('next'));
    try { ms.setActionHandler('seekto', (e) => this.seek(e.seekTime)); } catch (e) { /* old */ }
  }
}

export function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
