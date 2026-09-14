# Modu Square

Modu Square는 오늘의 생각을 나누고, 다른 사람의 취향과 질문을 발견하는 커뮤니티입니다.

![Modu Square 메인 화면](docs/images/modu-square-home.png)

## 주요 기능

### 주제별 커뮤니티

![주제별 커뮤니티 화면](docs/images/modu-square-community.png)

전체, 일상, 취향, 질문, 동네, 여행 중 관심 있는 주제를 골라 둘러보세요. 

### 글과 대화

![게시글 상세 화면](docs/images/modu-square-article.png)

게시글을 열면 내용과 조회수, 좋아요, 댓글을 한곳에서 확인할 수 있습니다.

### 오늘의 인기글

![오늘의 인기글 Top 10 화면](docs/images/modu-square-popular.png)

좋아요, 댓글, 조회수를 모아 오늘의 인기글 Top 10을 보여줍니다. 

## 아키텍처

<img width="858" height="324" alt="Modu Square 서비스 아키텍처" src="https://github.com/user-attachments/assets/1b52ed8c-9a59-455d-82e2-e61e8e3c39f8" />

게시글, 댓글, 좋아요, 조회 도메인을 서비스별로 분리하고 Kafka 이벤트로 연결했습니다. 각 도메인 서비스가 상태 변경 이벤트를 발행하면 게시글 조회 서비스와 인기글 서비스가 이를 구독해 각자의 조회 모델을 갱신합니다. 서비스 간 직접 호출을 줄여 기능별로 독립적으로 확장하고 장애 영향을 분리할 수 있도록 구성했습니다.

## Transactional Outbox Pattern

MySQL의 비즈니스 데이터 저장과 Kafka 이벤트 발행은 서로 다른 시스템에서 실행되므로 하나의 트랜잭션으로 묶을 수 없습니다. DB 저장만 성공하거나 Kafka 이벤트만 먼저 전달되면 서비스 간 데이터가 어긋날 수 있습니다. 이를 막기 위해 비즈니스 데이터와 이벤트를 같은 MySQL 트랜잭션의 Outbox에 저장합니다. 커밋이 끝난 뒤 Kafka로 발행하며 전송에 성공한 이벤트만 삭제합니다. 실패한 이벤트는 DB에 남겨 스케줄러가 다시 발행합니다.

- [MessageRelay.java에서 구현 보기](https://github.com/ppupy1209/modu-square/blob/main/common/outbox-message-relay/src/main/java/board/common/outboxmessagerelay/MessageRelay.java)

## 삭제된 게시글 반복 조회

### 문제

인기글이 삭제된 뒤에도 검색 결과와 공유 링크 등에 기존 URL이 남아 있으면 같은 게시글 ID에 조회가 반복될 수 있다고 생각했습니다. Redis는 캐시 미스와 실제 게시글 부재를 구분하지 못하므로 요청마다 게시글 서비스와 MySQL을 다시 조회합니다. 이 상황이 계속되면 존재하지 않는 게시글을 확인하는 요청이 원본 서비스와 DB의 처리량을 차지하고 정상 게시글 조회까지 지연시킬 수 있습니다.

### 해결

게시글 서비스에서 404 응답을 받은 게시글 ID는 Redis에 `MISSING` 값으로 60초 동안 저장했습니다. 타임아웃과 5xx는 캐시하지 않아 게시글 서비스의 장애를 게시글 부재로 오인하지 않도록 했습니다.

## 로컬 실행

Git과 Docker Desktop이 필요합니다. Docker Desktop을 먼저 실행한 뒤 아래 순서대로 진행하세요.

### 1. 저장소 받기

```bash
git clone https://github.com/ppupy1209/modu-square.git
cd modu-square
```

### 2. 서비스 시작하기

```bash
docker compose up --build -d
```

처음에는 이미지를 내려받고 서비스를 빌드하느라 몇 분 정도 걸릴 수 있습니다. 준비 상태는 다음 명령으로 확인합니다.

```bash
docker compose ps
```

준비가 끝나면 브라우저에서 `http://localhost:3000`을 여세요. 첫 실행에는 주제별 게시글과 댓글, 좋아요, 조회수, 인기글 데이터가 함께 들어갑니다. Auth Service도 함께 실행되지만 기존 조회·체험 API에는 로그인을 강제하지 않으므로, 가입하거나 데이터를 직접 만들지 않아도 Guest로 주요 기능을 바로 둘러볼 수 있습니다.

상단의 `Guest`를 누르면 로그인·회원가입 화면으로 이동할 수 있습니다. 회원가입을 마치면 같은 이메일로 로그인하고, 로그인 뒤 상단 닉네임을 눌러 이메일과 로그아웃 메뉴를 확인할 수 있습니다. 로그인 상태에서 새로 작성한 글은 회원 닉네임으로 표시됩니다. 초기 데이터와 Guest가 작성한 글은 기존처럼 `modu_{writerId}`로 보여 기존 데이터를 그대로 유지합니다.

### 3. 서비스 종료하기

```bash
docker compose down
```

게시글과 첨부 이미지를 비롯한 로컬 데이터는 Docker 볼륨에 남습니다. 데이터를 모두 지우고 처음 상태로 되돌릴 때만 아래 명령을 사용하세요.

```bash
docker compose down -v
docker compose up --build -d
```
