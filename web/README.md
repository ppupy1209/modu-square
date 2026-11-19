# 모두의 광장 Web

자유게시판 중심 커뮤니티 서비스의 로컬 웹 UI입니다. 흰 바탕과 구분선을 중심으로 글에 집중하는 화면입니다. 홈에서는 최신 글을, 커뮤니티에서는 여섯 주제의 최근 글을 둘러볼 수 있으며 모바일 검색과 하단 탐색을 지원합니다.

## 로컬 실행

전체 시스템은 저장소 루트에서 실행합니다.

```bash
docker compose up --build
```

- Web: <http://localhost:3000>
- Grafana: <http://localhost:3001>
- Prometheus: <http://localhost:9090>

웹만 개발할 때는 Node.js 22.13 이상에서 다음을 실행합니다.

```bash
npm ci
npm run dev
```

## 검증

```bash
npm test
npm run lint
```
