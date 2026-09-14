# modu-square — agent instructions

## Run

- Verify `./gradlew --version` reports JDK 21.
- Backend build: `./gradlew build`
- Backend tests: `./gradlew test`
- Focused backend test: `./gradlew :service:hot-article:test`
- Frontend: `cd web && npm run build`; `npm test`; `npm run lint`
- Full local stack: `docker compose up --build`

## Verify

- Run the relevant backend/frontend checks for changed files.
- Do not run `docker compose down -v`; it removes the large local data volume.
- Before committing, verify this repository’s local personal Git identity. Do not change global Git settings or credential helpers.
