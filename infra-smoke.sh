#!/bin/bash
# End-to-end smoke test of the running stack (./infra-up.sh first): registration mails a
# verification link through microservice-email into Mailpit, the token from that mail unlocks
# sign-in, the session works against /me, and the meme service serves an optimised upload.
set -euo pipefail

SEC=http://localhost:8080
MAIL_UI=http://localhost:8025
MEMES=http://localhost:8083
EMAIL_SVC=http://localhost:8082
USER_MAIL="smoke-$(date +%s)@example.com"
PASSWORD="StrongPassword1!"

step() { echo "== $*"; }

step "waiting for the services"
for url in "$SEC/health" "$MAIL_UI/api/v1/messages" "$MEMES/memes/hot"; do
    for i in $(seq 1 60); do
        curl -sf "$url" >/dev/null && break
        [ "$i" = 60 ] && { echo "FAIL: $url did not come up"; exit 1; }
        sleep 2
    done
done

step "register $USER_MAIL -> 201, verification mail lands in Mailpit"
curl -sf -X POST "$SEC/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$USER_MAIL\",\"password\":\"$PASSWORD\"}" >/dev/null

TOKEN=""
for i in $(seq 1 30); do
    MSG_ID=$(curl -sf "$MAIL_UI/api/v1/search?query=to:$USER_MAIL" | python3 -c \
        'import json,sys; m=json.load(sys.stdin)["messages"]; print(m[0]["ID"] if m else "")')
    if [ -n "$MSG_ID" ]; then
        TOKEN=$(curl -sf "$MAIL_UI/api/v1/message/$MSG_ID" | python3 -c \
            'import json,sys,re; t=json.load(sys.stdin)["Text"]; print(re.search(r"token=([A-Za-z0-9_\-]+)", t).group(1))')
        break
    fi
    sleep 1
done
[ -n "$TOKEN" ] || { echo "FAIL: no verification mail in Mailpit"; exit 1; }

step "sign-in before verification is refused with 403"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$SEC/authenticate" \
    -H 'Content-Type: application/json' -d "{\"email\":\"$USER_MAIL\",\"password\":\"$PASSWORD\"}")
[ "$STATUS" = 403 ] || { echo "FAIL: expected 403 before verification, got $STATUS"; exit 1; }

step "verify the address with the mailed token"
curl -sf -X POST "$SEC/verify-email" -H 'Content-Type: application/json' \
    -d "{\"token\":\"$TOKEN\"}" >/dev/null

step "sign-in now succeeds and the session works against /me"
ACCESS=$(curl -sf -X POST "$SEC/authenticate" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$USER_MAIL\",\"password\":\"$PASSWORD\"}" | python3 -c \
    'import json,sys; print(json.load(sys.stdin)["accessToken"])')
ME=$(curl -sf "$SEC/me" -H "Authorization: Bearer $ACCESS")
echo "$ME" | grep -q "$USER_MAIL" || { echo "FAIL: /me did not return the user, got: $ME"; exit 1; }

step "mail service refuses a caller without the API key"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$EMAIL_SVC/mails" \
    -H 'Content-Type: application/json' -d '{"to":"x@example.com","subject":"Hi","text":"Hello"}')
[ "$STATUS" = 401 ] || { echo "FAIL: expected 401 without API key, got $STATUS"; exit 1; }

step "meme service: upload is optimised to PNG and served back"
TMP_IMG=$(mktemp --suffix=.bmp)
python3 - "$TMP_IMG" <<'EOF'
import struct, sys
# minimal valid 2x2 24-bit BMP, no external deps
w, h = 2, 2
row = b'\x00\x00\xff' * w + b'\x00' * ((4 - (w * 3) % 4) % 4)
pixels = row * h
header = b'BM' + struct.pack('<IHHI', 54 + len(pixels), 0, 0, 54) \
       + struct.pack('<IiiHHIIiiII', 40, w, h, 1, 24, 0, len(pixels), 2835, 2835, 0, 0)
open(sys.argv[1], 'wb').write(header + pixels)
EOF
MEME_ID=$(curl -sf -F "file=@$TMP_IMG;type=image/bmp" "$MEMES/memes" | python3 -c \
    'import json,sys; print(json.load(sys.stdin)["id"])')
rm -f "$TMP_IMG"
curl -sf "$MEMES/memes/$MEME_ID" | head -c 4 | grep -q PNG \
    || { echo "FAIL: meme was not served back as PNG"; exit 1; }
curl -sf -X POST "$MEMES/memes/$MEME_ID/votes" -H 'Content-Type: application/json' \
    -d '{"direction":"UP"}' >/dev/null
curl -sf "$MEMES/memes/hot" | grep -q "$MEME_ID" || { echo "FAIL: meme missing from /memes/hot"; exit 1; }

echo
echo "SMOKE PASS: register -> mail(Mailpit) -> verify -> sign-in -> /me, mail auth, meme upload/vote/hot"
