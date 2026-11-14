# PipePipe Desktop

PipePipe의 데스크톱(PC) 클라이언트입니다. 안드로이드 앱과 같은 **PipePipeExtractor**(이 저장소의 서브모듈)를 그대로 엔진으로 쓰고, 그 위에 로컬 웹 서버와 PC 전용 UI를 얹었습니다.

- YouTube · BiliBili · NicoNico 검색/재생/채널/재생목록/댓글
- 최대 4K60 적응형 재생 (DASH/HLS, shaka-player), 화질·속도·자막·오디오 트랙 선택
- 구독 피드, 시청 기록(이어보기), 나중에 볼 동영상, 로컬 재생목록
- NewPipe / PipePipe 구독 JSON 가져오기·내보내기
- 미니 플레이어, 영화관 모드, PIP, 키보드 단축키, 미디어 키
- 계정·광고·추적 없음. 모든 데이터는 이 컴퓨터에만 저장됩니다.

## 실행

### 설치 파일 (Java 불필요)
[Actions](../../actions/workflows/desktop.yml) 또는 Releases에서 OS별 `PipePipe-*.zip / .tar.gz`를 받아 압축을 풀고 `PipePipe`를 실행합니다.
설치된 Chrome / Edge / Chromium / Brave가 있으면 독립된 앱 창으로 열리고, 창을 닫으면 앱도 종료됩니다. (없으면 기본 브라우저 + 트레이 아이콘)

### 소스에서
JDK 25가 필요합니다.

```bash
git submodule update --init PipePipeExtractor
cd desktop
./gradlew fatJar
java -jar build/libs/pipepipe-desktop-0.1.0-all.jar           # 앱 창으로 실행
java -jar build/libs/pipepipe-desktop-0.1.0-all.jar --server  # 웹 서버만 (http://127.0.0.1:47615)
```

옵션: `--host 0.0.0.0`(LAN에 공개 — 같은 네트워크의 누구나 사용 가능), `--port N`, `--data DIR`, `--web DIR`(UI를 디스크에서 바로 제공, 개발용).
환경변수 `PIPEPIPE_BROWSER`로 앱 창에 쓸 브라우저 실행 파일을 지정할 수 있습니다.

데이터 위치: Windows `%APPDATA%\PipePipe Desktop`, macOS `~/Library/Application Support/PipePipe Desktop`, Linux `~/.local/share/pipepipe-desktop`.

## 구조

```
desktop/
  src/main/java/dev/pipepipe/desktop/
    Main.java               실행, 앱 창(Chromium --app) 띄우기, 트레이
    Server.java             JDK HttpServer + 가상 스레드, 라우팅, 정적 파일
    Api.java                PipePipeExtractor → JSON (검색, 스트림, 채널, 재생목록, 댓글, 피드)
    DesktopDownloader.java  안드로이드 DownloaderImpl의 OkHttp 포팅
    MediaProxy.java         서명된 미디어/HLS 프록시 (CORS·Referer·쿠키 처리, YouTube 청크)
    Mpd.java                바이트 범위 인덱스 스트림 → DASH 매니페스트 (YouTube, BiliBili)
    Store.java              구독/기록/재생목록/설정 JSON 저장
  src/main/resources/web/   UI (빌드 단계 없는 순수 ES 모듈)
    index.html  app.css  app.js  player.js  vendor/shaka-player.compiled.js
```

재생 방식: 서버가 추출기 결과로 DASH 매니페스트(YouTube·BiliBili) 또는 HLS 마스터 플레이리스트(NicoNico, 라이브)를 만들고, 모든 미디어 요청은 로컬 프록시(`/px/…`, HMAC 서명)를 거칩니다. 브라우저는 MSE로 재생하므로 CORS나 서비스별 헤더 문제가 없습니다.

## 단축키

| 키 | 동작 |
|---|---|
| `/` | 검색 |
| `K` / `Space` | 재생·일시정지 |
| `J` / `L`, `←` / `→` | 10초 / 5초 이동 |
| `↑` / `↓` | 볼륨 |
| `M` `F` `T` `I` `C` | 음소거 · 전체화면 · 영화관 · PIP · 자막 |
| `Shift+N` | 다음 동영상 |
| `<` / `>` | 재생 속도 |
| `0`–`9` | 10% 단위 이동 |
| `Ctrl+B` | 사이드바 |

## 라이선스
GPLv3 (PipePipe와 동일). shaka-player는 Apache-2.0 (`web/vendor/shaka-LICENSE.txt`).
