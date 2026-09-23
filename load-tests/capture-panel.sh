#!/usr/bin/env bash
# Grafana 패널 하나를 PNG로 렌더링한다. 문서에 넣을 그래프를 같은 크기와 테마로 만들기 위한 것이다.
#   docker compose --profile capture up -d grafana-image-renderer
#   ./load-tests/capture-panel.sh <dashboard-uid> <panel-id> <from-epoch-sec> <to-epoch-sec> <output.png>
set -euo pipefail

uid="$1"
panel_id="$2"
from_ms="$(( $3 * 1000 ))"
to_ms="$(( $4 * 1000 ))"
output="$5"

curl -sf -u "${GRAFANA_USER:-admin}:${GRAFANA_PASSWORD:-admin}" \
  "http://localhost:3001/render/d-solo/${uid}/_?orgId=1&panelId=${panel_id}&from=${from_ms}&to=${to_ms}&width=1280&height=720&tz=Asia%2FSeoul&theme=light" \
  -o "${output}"
echo "saved ${output}"
