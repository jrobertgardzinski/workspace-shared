#!/bin/bash
# End-to-end smoke test of the running stack (./infra-up.sh first): registration mails a
# verification link through microservice-email into Mailpit, the token from that mail unlocks
# sign-in, the session works against /me, and the meme service serves an optimised upload.
set -euo pipefail

SEC=http://localhost:8080
MAIL_UI=http://localhost:8025
MEMES=http://localhost:8083
COMMENTS=http://localhost:8085
EMAIL_SVC=http://localhost:8082
USER_MAIL="smoke-$(date +%s)@example.com"
PASSWORD="StrongPassword1!"

step() { echo "== $*"; }

step "resetting brute-force state (manual clicking may have tripped the source block)"
docker compose exec -T postgres psql -q -U postgres -d security \
    -c "DELETE FROM authentication_blocks; DELETE FROM rejected_authentications;" >/dev/null 2>&1 || true

step "waiting for the services"
for url in "$SEC/health" "$MAIL_UI/api/v1/messages" "$MEMES/memes/hot" "$COMMENTS/memes/warmup/comments"; do
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
            'import json,sys,re; t=json.load(sys.stdin)["Text"]; print(re.search(r"(?:token|verify)=([A-Za-z0-9_\-]+)", t).group(1))')
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

step "meme service: anonymous upload is refused, the security-issued token unlocks it"
TMP_IMG=$(mktemp --suffix=.bmp)
python3 - "$TMP_IMG" <<'EOF'
import os, struct, sys
# minimal valid 2x2 24-bit BMP, no external deps; random pixels so content-hash
# deduplication never links this run's meme to an earlier run's votes
w, h = 2, 2
row = os.urandom(3) * w + b'\x00' * ((4 - (w * 3) % 4) % 4)
pixels = row * h
header = b'BM' + struct.pack('<IHHI', 54 + len(pixels), 0, 0, 54) \
       + struct.pack('<IiiHHIIiiII', 40, w, h, 1, 24, 0, len(pixels), 2835, 2835, 0, 0)
open(sys.argv[1], 'wb').write(header + pixels)
EOF
STATUS=$(curl -s -o /dev/null -w '%{http_code}' -F "file=@$TMP_IMG;type=image/bmp" "$MEMES/memes")
[ "$STATUS" = 401 ] || { echo "FAIL: anonymous upload expected 401, got $STATUS"; exit 1; }
MEME_ID=$(curl -sf -H "Authorization: Bearer $ACCESS" -F "file=@$TMP_IMG;type=image/bmp" "$MEMES/memes" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
rm -f "$TMP_IMG"

step "browsing is public; comment is signed by the security identity"
curl -sf "$MEMES/memes/$MEME_ID" | head -c 4 | grep -q PNG \
    || { echo "FAIL: meme was not served back as PNG"; exit 1; }
curl -sf "$MEMES/memes" | grep -q "$MEME_ID" || { echo "FAIL: meme missing from the public gallery"; exit 1; }
COMMENT_ID=$(curl -sf -X POST "$COMMENTS/memes/$MEME_ID/comments" -H "Authorization: Bearer $ACCESS" \
    -H 'Content-Type: application/json' -d '{"text":"smoke says hi"}' | python3 -c \
    'import json,sys; print(json.load(sys.stdin)["id"])')
curl -sf "$COMMENTS/memes/$MEME_ID/comments" | grep -q "$USER_MAIL" \
    || { echo "FAIL: comment not signed by $USER_MAIL"; exit 1; }

step "votes are a toggle: up-vote counts, repeating it retracts (memes and comments)"
vote() { curl -sf -X POST "$1" -H "Authorization: Bearer $ACCESS" \
    -H 'Content-Type: application/json' -d '{"direction":"UP"}'; }
R=$(vote "$MEMES/memes/$MEME_ID/votes" | python3 -c 'import json,sys; b=json.load(sys.stdin); print(b["score"], b["myVote"])')
[ "$R" = "1 UP" ] || { echo "FAIL: first up-vote expected '1 UP', got '$R'"; exit 1; }
R=$(vote "$MEMES/memes/$MEME_ID/votes" | python3 -c 'import json,sys; b=json.load(sys.stdin); print(b["score"], b["myVote"])')
[ "$R" = "0 None" ] || { echo "FAIL: repeated up-vote should retract ('0 None'), got '$R'"; exit 1; }
vote "$MEMES/memes/$MEME_ID/votes" >/dev/null   # vote back for the hot check
R=$(vote "$COMMENTS/memes/$MEME_ID/comments/$COMMENT_ID/votes" | python3 -c 'import json,sys; print(json.load(sys.stdin)["score"])')
[ "$R" = 1 ] || { echo "FAIL: comment up-vote expected score 1, got '$R'"; exit 1; }
R=$(vote "$COMMENTS/memes/$MEME_ID/comments/$COMMENT_ID/votes" | python3 -c 'import json,sys; print(json.load(sys.stdin)["score"])')
[ "$R" = 0 ] || { echo "FAIL: repeated comment up-vote should retract (0), got '$R'"; exit 1; }
curl -sf "$MEMES/memes/hot" | grep -q "$MEME_ID" || { echo "FAIL: meme missing from /memes/hot"; exit 1; }

step "account-deletion SAGA: memes purged, comments elsewhere anonymised, account gone"
LEAVER="smoke-leaver-$(date +%s)@example.com"
curl -sf -X POST "$SEC/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$LEAVER\",\"password\":\"$PASSWORD\"}" >/dev/null
LTOKEN=""
for i in $(seq 1 30); do
    MSG=$(curl -sf "$MAIL_UI/api/v1/search?query=to:$LEAVER" | python3 -c \
        'import json,sys; m=json.load(sys.stdin)["messages"]; print(m[0]["ID"] if m else "")')
    [ -n "$MSG" ] && { LTOKEN=$(curl -sf "$MAIL_UI/api/v1/message/$MSG" | python3 -c \
        'import json,sys,re; print(re.search(r"(?:token|verify)=([A-Za-z0-9_\-]+)", json.load(sys.stdin)["Text"]).group(1))'); break; }
    sleep 1
done
curl -sf -X POST "$SEC/verify-email" -H 'Content-Type: application/json' -d "{\"token\":\"$LTOKEN\"}" >/dev/null
LACCESS=$(curl -sf -X POST "$SEC/authenticate" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$LEAVER\",\"password\":\"$PASSWORD\"}" | python3 -c \
    'import json,sys; print(json.load(sys.stdin)["accessToken"])')
# the leaver comments on someone else's meme (stays, anonymised) and uploads their own (goes away)
curl -sf -X POST "$COMMENTS/memes/$MEME_ID/comments" -H "Authorization: Bearer $LACCESS" \
    -H 'Content-Type: application/json' -d '{"text":"Lorem ipsum"}' >/dev/null
TMP2=$(mktemp --suffix=.bmp)
python3 - "$TMP2" <<'EOF'
import os, struct, sys
w, h = 2, 2
row = os.urandom(3) * w + b'\x00' * ((4 - (w * 3) % 4) % 4)
pixels = row * h
header = b'BM' + struct.pack('<IHHI', 54 + len(pixels), 0, 0, 54) \
       + struct.pack('<IiiHHIIiiII', 40, w, h, 1, 24, 0, len(pixels), 2835, 2835, 0, 0)
open(sys.argv[1], 'wb').write(header + pixels)
EOF
LEAVER_MEME=$(curl -sf -H "Authorization: Bearer $LACCESS" -F "file=@$TMP2;type=image/bmp" "$MEMES/memes" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
rm -f "$TMP2"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$SEC/account/delete" -H "Authorization: Bearer $LACCESS")
[ "$STATUS" = 202 ] || { echo "FAIL: deletion request expected 202, got $STATUS"; exit 1; }
STATUS=""
for i in 1 2 3; do   # the lock is synchronous, but under load give the request a moment to be seen
    STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$SEC/authenticate" \
        -H 'Content-Type: application/json' -d "{\"email\":\"$LEAVER\",\"password\":\"$PASSWORD\"}")
    [ "$STATUS" = 401 ] && break
    sleep 1
done
[ "$STATUS" = 401 ] || { echo "FAIL: locked account expected 401, got $STATUS"; exit 1; }
SAGA_OK=""
for i in $(seq 1 30); do
    MEME_GONE=$(curl -s -o /dev/null -w '%{http_code}' "$MEMES/memes/$LEAVER_MEME")
    ANON=$(curl -sf "$COMMENTS/memes/$MEME_ID/comments" | python3 -c \
        'import json,sys; cs=json.load(sys.stdin); print(any(c["author"]=="deleted account" and c["text"]=="Lorem ipsum" for c in cs))')
    if [ "$MEME_GONE" = 404 ] && [ "$ANON" = True ]; then SAGA_OK=yes; break; fi
    sleep 2
done
[ -n "$SAGA_OK" ] || { echo "FAIL: purge incomplete (meme:$MEME_GONE anon:$ANON)"; exit 1; }
REREG=""
for i in $(seq 1 30); do   # only after the purges: a 201 probe would consume the freed email
    REREG=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$SEC/register" -H 'Content-Type: application/json' \
        -d "{\"email\":\"$LEAVER\",\"password\":\"$PASSWORD\"}")
    [ "$REREG" = 201 ] && break
    sleep 2
done
[ "$REREG" = 201 ] || { echo "FAIL: email not freed after full saga (rereg:$REREG)"; exit 1; }
GOODBYE=""
for i in $(seq 1 15); do   # the goodbye mail is async (Kafka -> email -> SMTP); give it a moment
    curl -sf "$MAIL_UI/api/v1/search?query=to:$LEAVER" | grep -q "account is deleted" && { GOODBYE=1; break; }
    sleep 2
done
[ -n "$GOODBYE" ] || { echo "FAIL: no goodbye mail"; exit 1; }

step "deletion wizard: keep-popular memes survive anonymised, comments chosen to go, go"
KEEPER="smoke-keeper-$(date +%s)@example.com"
curl -sf -X POST "$SEC/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$KEEPER\",\"password\":\"$PASSWORD\"}" >/dev/null
KTOKEN=""
for i in $(seq 1 30); do
    MSG=$(curl -sf "$MAIL_UI/api/v1/search?query=to:$KEEPER" | python3 -c \
        'import json,sys; m=json.load(sys.stdin)["messages"]; print(m[0]["ID"] if m else "")')
    [ -n "$MSG" ] && { KTOKEN=$(curl -sf "$MAIL_UI/api/v1/message/$MSG" | python3 -c \
        'import json,sys,re; print(re.search(r"(?:token|verify)=([A-Za-z0-9_\-]+)", json.load(sys.stdin)["Text"]).group(1))'); break; }
    sleep 1
done
curl -sf -X POST "$SEC/verify-email" -H 'Content-Type: application/json' -d "{\"token\":\"$KTOKEN\"}" >/dev/null
KACCESS=$(curl -sf -X POST "$SEC/authenticate" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$KEEPER\",\"password\":\"$PASSWORD\"}" | python3 -c \
    'import json,sys; print(json.load(sys.stdin)["accessToken"])')
TMP3=$(mktemp --suffix=.bmp)
python3 - "$TMP3" <<'EOF'
import os, struct, sys
w, h = 2, 2
row = os.urandom(3) * w + b'\x00' * ((4 - (w * 3) % 4) % 4)
pixels = row * h
header = b'BM' + struct.pack('<IHHI', 54 + len(pixels), 0, 0, 54) \
       + struct.pack('<IiiHHIIiiII', 40, w, h, 1, 24, 0, len(pixels), 2835, 2835, 0, 0)
open(sys.argv[1], 'wb').write(header + pixels)
EOF
KEEPER_MEME=$(curl -sf -H "Authorization: Bearer $KACCESS" -F "file=@$TMP3;type=image/bmp" "$MEMES/memes" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
rm -f "$TMP3"
curl -sf -X POST "$MEMES/memes/$KEEPER_MEME/votes" -H "Authorization: Bearer $ACCESS" \
    -H 'Content-Type: application/json' -d '{"direction":"UP"}' >/dev/null   # the community likes it
curl -sf -X POST "$COMMENTS/memes/$MEME_ID/comments" -H "Authorization: Bearer $KACCESS" \
    -H 'Content-Type: application/json' -d '{"text":"chosen to vanish"}' >/dev/null
curl -sf -X POST "$SEC/account/delete" -H "Authorization: Bearer $KACCESS" -H 'Content-Type: application/json' \
    -d '{"purge":{"memes":"KEEP_POPULAR_ANONYMIZED:1","comments":"DELETE"}}' >/dev/null
WIZ_OK=""
for i in $(seq 1 30); do
    KEPT=$(curl -s -o /dev/null -w '%{http_code}' "$MEMES/memes/$KEEPER_MEME")
    GONE=$(curl -sf "$COMMENTS/memes/$MEME_ID/comments" | python3 -c \
        'import json,sys; print(any(c["text"]=="chosen to vanish" for c in json.load(sys.stdin)))')
    if [ "$KEPT" = 200 ] && [ "$GONE" = False ]; then WIZ_OK=yes; break; fi
    sleep 2
done
[ -n "$WIZ_OK" ] || { echo "FAIL: wizard policy not honoured (kept:$KEPT commentGone:$GONE)"; exit 1; }
REREG=""
for i in $(seq 1 30); do
    REREG=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$SEC/register" -H 'Content-Type: application/json' \
        -d "{\"email\":\"$KEEPER\",\"password\":\"$PASSWORD\"}")
    [ "$REREG" = 201 ] && break
    sleep 2
done
[ "$REREG" = 201 ] || { echo "FAIL: email not freed after wizard saga (rereg:$REREG)"; exit 1; }

step "resilience: a mail requested while the mail service is DOWN arrives once it is back"
RESIL_MAIL="smoke-resil-$(date +%s)@example.com"
docker compose stop email >/dev/null 2>&1
curl -sf -X POST "$SEC/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$RESIL_MAIL\",\"password\":\"$PASSWORD\"}" >/dev/null \
    || { echo "FAIL: registration must survive a mail-service outage"; docker compose start email >/dev/null; exit 1; }
docker compose start email >/dev/null 2>&1
FOUND=""
for i in $(seq 1 45); do
    FOUND=$(curl -sf "$MAIL_UI/api/v1/search?query=to:$RESIL_MAIL" | python3 -c \
        'import json,sys; m=json.load(sys.stdin)["messages"]; print(m[0]["ID"] if m else "")')
    [ -n "$FOUND" ] && break
    sleep 2
done
[ -n "$FOUND" ] || { echo "FAIL: outbox event did not reach the mail service after restart"; exit 1; }

step "formula: the Python race module simulates, the backend streams state over SSE"
FORMULA=http://localhost:8084
for i in $(seq 1 30); do curl -sf "$FORMULA/drivers" >/dev/null && break; sleep 2; done
DRIVERS=$(curl -sf "$FORMULA/drivers" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))')
if [ "$DRIVERS" -lt 2 ]; then
    curl -sf -X POST "$FORMULA/drivers" -H 'Content-Type: application/json' \
        -d '{"name":"SmokeAce","pace":95,"aggression":40,"consistency":92}' >/dev/null
    curl -sf -X POST "$FORMULA/drivers" -H 'Content-Type: application/json' \
        -d '{"name":"SmokeRival","pace":90,"aggression":85,"consistency":60}' >/dev/null
fi
step "formula gates game actions behind a security token (anon start refused)"
ANON=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$FORMULA/broadcast/races" \
    -H 'Content-Type: application/json' -d '{"laps":5}')
[ "$ANON" = 401 ] || { echo "FAIL: anonymous race start expected 401, got $ANON"; exit 1; }
RACE=$(curl -sf -X POST "$FORMULA/broadcast/races" -H "Authorization: Bearer $ACCESS" \
    -H 'Content-Type: application/json' -d '{"laps":5}' \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["raceId"])')
STREAM=$(curl -sN --max-time 20 "$FORMULA/broadcast/races/$RACE/stream")
echo "$STREAM" | grep -q "event: frame"  || { echo "FAIL: no frames on the race stream"; exit 1; }
echo "$STREAM" | grep -q "event: result" || { echo "FAIL: race stream did not finish with a result"; exit 1; }

echo
echo "SMOKE PASS: register -> mail(Mailpit via Kafka) -> verify -> sign-in -> /me, mail auth,"
echo "            memes gated by security (401 anon; public reads; toggle votes on memes and comments),"
echo "            outbox survives a mail-service outage,"
echo "            delete-account SAGA: memes purged, comments anonymised, goodbye mail, email freed,"
echo "            wizard override honoured: popular meme kept anonymised, chosen comments deleted,"
echo "            formula race simulated in Python and streamed as SSE state"
