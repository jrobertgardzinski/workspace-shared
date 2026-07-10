#!/bin/bash
# End-to-end smoke test of the running stack (./infra-up.sh first): registration mails a
# verification link through microservice-email into Mailpit, the token from that mail unlocks
# sign-in, the session works against /me, and the meme service serves an optimised upload.
set -euo pipefail

SEC=http://localhost:8080
MAIL_UI=http://localhost:8025
MEMES=http://localhost:8083
COMMENTS=http://localhost:8085
COLLECTIONS=http://localhost:8092
EMAIL_SVC=http://localhost:8082
USER_MAIL="smoke-$(date +%s)@example.com"
PASSWORD="StrongPassword1!"

step() { echo "== $*"; }

step "resetting brute-force state (manual clicking may have tripped the source block)"
docker compose exec -T postgres psql -q -U postgres -d security \
    -c "DELETE FROM authentication_blocks; DELETE FROM rejected_authentications;" >/dev/null 2>&1 || true

step "waiting for the services"
for url in "$SEC/health" "$MAIL_UI/api/v1/messages" "$MEMES/memes/hot" "$COMMENTS/memes/warmup/comments" "$COLLECTIONS/health"; do
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

step "social login: the OAuth dance against the stub IdP opens a session"
OAUTH_MAIL="smoke-oauth-$(date +%s)@example.com"
AUTH_URL=$(curl -s -o /dev/null -w '%{redirect_url}' "$SEC/oauth/google/start?return=http://localhost:8083/")
case "$AUTH_URL" in http://localhost:8091/authorize*) ;; *) echo "FAIL: /oauth/google/start did not redirect to the IdP ($AUTH_URL)"; exit 1;; esac
# the stub's automation convenience: an &email= on /authorize signs in headlessly
CALLBACK_URL=$(curl -s -o /dev/null -w '%{redirect_url}' "$AUTH_URL&email=$OAUTH_MAIL")
case "$CALLBACK_URL" in "$SEC/oauth/callback"*) ;; *) echo "FAIL: the stub did not send the browser back ($CALLBACK_URL)"; exit 1;; esac
FINAL_URL=$(curl -s -o /dev/null -w '%{redirect_url}' "$CALLBACK_URL")
case "$FINAL_URL" in *"#accessToken="*) ;; *) echo "FAIL: the callback handed back no token ($FINAL_URL)"; exit 1;; esac
OTOKEN=${FINAL_URL#*#accessToken=}
OME=$(curl -sf "$SEC/me" -H "Authorization: Bearer $OTOKEN")
echo "$OME" | grep -q "$OAUTH_MAIL" \
    || { echo "FAIL: /me does not know the federated user, got: $OME"; exit 1; }

step "social login (USERINFO source): the github-flavoured provider signs in through /userinfo"
PROVIDERS=$(curl -sf "$SEC/oauth/providers")
echo "$PROVIDERS" | grep -q '"github"' \
    || { echo "FAIL: /oauth/providers does not list github, got: $PROVIDERS"; exit 1; }
UINFO_MAIL="smoke-userinfo-$(date +%s)@example.com"
AUTH_URL=$(curl -s -o /dev/null -w '%{redirect_url}' "$SEC/oauth/github/start?return=http://localhost:8083/")
case "$AUTH_URL" in http://localhost:8091/authorize*) ;; *) echo "FAIL: /oauth/github/start did not redirect to the IdP ($AUTH_URL)"; exit 1;; esac
CALLBACK_URL=$(curl -s -o /dev/null -w '%{redirect_url}' "$AUTH_URL&email=$UINFO_MAIL")
FINAL_URL=$(curl -s -o /dev/null -w '%{redirect_url}' "$CALLBACK_URL")
case "$FINAL_URL" in *"#accessToken="*) ;; *) echo "FAIL: the USERINFO callback handed back no token ($FINAL_URL)"; exit 1;; esac
UTOKEN=${FINAL_URL#*#accessToken=}
UME=$(curl -sf "$SEC/me" -H "Authorization: Bearer $UTOKEN")
echo "$UME" | grep -q "$UINFO_MAIL" \
    || { echo "FAIL: /me does not know the userinfo-federated user, got: $UME"; exit 1; }

step "MFA: enrol the e-mail factor, then sign-in needs the mailed code"
MFA_MAIL="smoke-mfa-$(date +%s)@example.com"
curl -sf -X POST "$SEC/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$MFA_MAIL\",\"password\":\"$PASSWORD\"}" >/dev/null
# verify the address (the verification link's token)
MFA_TOKEN=""
for i in $(seq 1 30); do
    MSG=$(curl -sf "$MAIL_UI/api/v1/search?query=to:$MFA_MAIL" | python3 -c \
        'import json,sys; m=json.load(sys.stdin)["messages"]; print(m[0]["ID"] if m else "")')
    [ -n "$MSG" ] && { MFA_TOKEN=$(curl -sf "$MAIL_UI/api/v1/message/$MSG" | python3 -c \
        'import json,sys,re; print(re.search(r"(?:token|verify)=([A-Za-z0-9_\-]+)", json.load(sys.stdin)["Text"]).group(1))'); break; }
    sleep 1
done
curl -sf -X POST "$SEC/verify-email" -H 'Content-Type: application/json' -d "{\"token\":\"$MFA_TOKEN\"}" >/dev/null
MFA_ACCESS=$(curl -sf -X POST "$SEC/authenticate" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$MFA_MAIL\",\"password\":\"$PASSWORD\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
# reads the newest 6-digit sign-in code mailed to a target (empty on any hiccup, never throws)
read_signin_code() {
    python3 - "$MAIL_UI" "$1" <<'PY'
import json, re, sys, urllib.request
mail_ui, target = sys.argv[1], sys.argv[2]
try:
    search = json.load(urllib.request.urlopen(f"{mail_ui}/api/v1/search?query=to:{target}"))
    for m in search.get("messages", []):
        if m.get("Subject") == "Your sign-in code":
            text = json.load(urllib.request.urlopen(f"{mail_ui}/api/v1/message/{m['ID']}"))["Text"]
            hit = re.search(r"\b(\d{6})\b", text)
            if hit:
                print(hit.group(1))
                break
except Exception:
    pass
PY
}
# wait for a sign-in code for $1 that differs from $2 (the enrolment code and the sign-in code
# share a subject, so we must not grab the stale one that is already in the mailbox)
latest_code() {
    local exclude="${2:-}"
    for i in $(seq 1 30); do
        CODE=$(read_signin_code "$1")
        [ -n "${CODE:-}" ] && [ "$CODE" != "$exclude" ] && { echo "$CODE"; return; }
        sleep 1
    done
}
# enrol: start sends a code, confirm seals it
curl -sf -X POST "$SEC/account/factors/EMAIL_CODE/enroll/start" -H "Authorization: Bearer $MFA_ACCESS" \
    -H 'Content-Type: application/json' -d '{}' >/dev/null
ENROL_CODE=$(latest_code "$MFA_MAIL")
[ -n "${ENROL_CODE:-}" ] || { echo "FAIL: no enrolment code mailed"; exit 1; }
ENROLLED=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$SEC/account/factors/EMAIL_CODE/enroll/confirm" \
    -H "Authorization: Bearer $MFA_ACCESS" -H 'Content-Type: application/json' -d "{\"code\":\"$ENROL_CODE\"}")
[ "$ENROLLED" = 200 ] || { echo "FAIL: e-mail factor enrolment ($ENROLLED)"; exit 1; }
# now the password alone yields a ticket, not a session
MFA_RESP=$(curl -sf -X POST "$SEC/authenticate" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$MFA_MAIL\",\"password\":\"$PASSWORD\"}")
TICKET=$(echo "$MFA_RESP" | python3 -c 'import json,sys; b=json.load(sys.stdin); print(b["mfaTicket"] if b.get("status")=="MFA_REQUIRED" else "")')
[ -n "$TICKET" ] || { echo "FAIL: password alone did not require MFA ($MFA_RESP)"; exit 1; }
# the sign-in code is the fresh one, not the enrolment code still sitting in the mailbox
SIGNIN_CODE=$(latest_code "$MFA_MAIL" "$ENROL_CODE")
[ -n "${SIGNIN_CODE:-}" ] || { echo "FAIL: no sign-in code mailed"; exit 1; }
MFA_SESSION=$(curl -s -X POST "$SEC/authenticate/factor" -H 'Content-Type: application/json' \
    -d "{\"mfaTicket\":\"$TICKET\",\"proof\":\"$SIGNIN_CODE\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessToken",""))')
[ -n "$MFA_SESSION" ] || { echo "FAIL: the mailed code did not complete the sign-in"; exit 1; }
curl -sf "$SEC/me" -H "Authorization: Bearer $MFA_SESSION" | grep -q "$MFA_MAIL" \
    || { echo "FAIL: /me does not know the MFA user after the two-step sign-in"; exit 1; }

step "MFA: TOTP (authenticator app) — enrol with an otpauth secret, sign in with a computed code"
TOTP_MAIL="smoke-totp-$(date +%s)@example.com"
curl -sf -X POST "$SEC/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$TOTP_MAIL\",\"password\":\"$PASSWORD\"}" >/dev/null
for i in $(seq 1 30); do
    MSG=$(curl -sf "$MAIL_UI/api/v1/search?query=to:$TOTP_MAIL" | python3 -c \
        'import json,sys; m=json.load(sys.stdin)["messages"]; print(m[0]["ID"] if m else "")')
    [ -n "$MSG" ] && { TT=$(curl -sf "$MAIL_UI/api/v1/message/$MSG" | python3 -c \
        'import json,sys,re; print(re.search(r"(?:token|verify)=([A-Za-z0-9_\-]+)", json.load(sys.stdin)["Text"]).group(1))'); break; }
    sleep 1
done
curl -sf -X POST "$SEC/verify-email" -H 'Content-Type: application/json' -d "{\"token\":\"$TT\"}" >/dev/null
TOTP_ACCESS=$(curl -sf -X POST "$SEC/authenticate" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$TOTP_MAIL\",\"password\":\"$PASSWORD\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
# computes the current TOTP code from a base32 secret (stdlib only)
totp_code() { python3 - "$1" <<'PY'
import base64, hashlib, hmac, struct, sys, time
key = base64.b32decode(sys.argv[1] + "=" * (-len(sys.argv[1]) % 8))
counter = struct.pack(">Q", int(time.time()) // 30)
h = hmac.new(key, counter, hashlib.sha1).digest()
o = h[-1] & 0x0f
print("%06d" % ((struct.unpack(">I", h[o:o+4])[0] & 0x7fffffff) % 1000000))
PY
}
OTPAUTH=$(curl -sf -X POST "$SEC/account/factors/TOTP/enroll/start" -H "Authorization: Bearer $TOTP_ACCESS" \
    -H 'Content-Type: application/json' -d '{}' | python3 -c 'import json,sys; print(json.load(sys.stdin).get("display",""))')
SECRET=$(echo "$OTPAUTH" | python3 -c 'import sys,re; print(re.search(r"secret=([A-Z2-7]+)", sys.stdin.read()).group(1))')
[ -n "$SECRET" ] || { echo "FAIL: TOTP enrol returned no secret"; exit 1; }
TOTP_ENROLLED=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$SEC/account/factors/TOTP/enroll/confirm" \
    -H "Authorization: Bearer $TOTP_ACCESS" -H 'Content-Type: application/json' -d "{\"code\":\"$(totp_code "$SECRET")\"}")
[ "$TOTP_ENROLLED" = 200 ] || { echo "FAIL: TOTP enrolment ($TOTP_ENROLLED)"; exit 1; }
TOTP_TICKET=$(curl -sf -X POST "$SEC/authenticate" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$TOTP_MAIL\",\"password\":\"$PASSWORD\"}" | python3 -c 'import json,sys; b=json.load(sys.stdin); print(b.get("mfaTicket",""))')
[ -n "$TOTP_TICKET" ] || { echo "FAIL: TOTP sign-in did not require the factor"; exit 1; }
TOTP_SESSION=$(curl -s -X POST "$SEC/authenticate/factor" -H 'Content-Type: application/json' \
    -d "{\"mfaTicket\":\"$TOTP_TICKET\",\"proof\":\"$(totp_code "$SECRET")\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessToken",""))')
[ -n "$TOTP_SESSION" ] || { echo "FAIL: the TOTP code did not complete the sign-in"; exit 1; }
curl -sf "$SEC/me" -H "Authorization: Bearer $TOTP_SESSION" | grep -q "$TOTP_MAIL" \
    || { echo "FAIL: /me does not know the TOTP user after sign-in"; exit 1; }

step "MFA: a recovery code stands in for the factor once (generated, spent, dead on replay)"
RC_CODES=$(curl -sf -X POST "$SEC/account/recovery-codes" -H "Authorization: Bearer $TOTP_SESSION")
RC_FIRST=$(echo "$RC_CODES" | python3 -c 'import json,sys; print(json.load(sys.stdin)["codes"][0])')
[ -n "$RC_FIRST" ] || { echo "FAIL: no recovery codes generated, got: $RC_CODES"; exit 1; }
RC_TICKET=$(curl -sf -X POST "$SEC/authenticate" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$TOTP_MAIL\",\"password\":\"$PASSWORD\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("mfaTicket",""))')
RC_SESSION=$(curl -s -X POST "$SEC/authenticate/factor" -H 'Content-Type: application/json' \
    -d "{\"mfaTicket\":\"$RC_TICKET\",\"proof\":\"$RC_FIRST\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessToken",""))')
[ -n "$RC_SESSION" ] || { echo "FAIL: the recovery code did not complete the sign-in"; exit 1; }
RC_LEFT=$(curl -sf "$SEC/account/recovery-codes" -H "Authorization: Bearer $RC_SESSION" | python3 -c 'import json,sys; print(json.load(sys.stdin)["unused"])')
[ "$RC_LEFT" = 9 ] || { echo "FAIL: expected 9 unused recovery codes after spending one, got $RC_LEFT"; exit 1; }
RC_TICKET2=$(curl -sf -X POST "$SEC/authenticate" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$TOTP_MAIL\",\"password\":\"$PASSWORD\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("mfaTicket",""))')
RC_REPLAY=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$SEC/authenticate/factor" -H 'Content-Type: application/json' \
    -d "{\"mfaTicket\":\"$RC_TICKET2\",\"proof\":\"$RC_FIRST\"}")
[ "$RC_REPLAY" = 401 ] || { echo "FAIL: a spent recovery code should be refused, got $RC_REPLAY"; exit 1; }

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

step "the image bytes live in object storage (MinIO), not in the meme row"
docker compose exec -T minio ls "/data/memes/$MEME_ID" >/dev/null 2>&1 \
    || { echo "FAIL: meme $MEME_ID has no object in the MinIO bucket"; exit 1; }
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

step "account-deletion SAGA: memes purged, comments anonymised, collections cleared, account gone"
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
# the leaver saves a favourite in user-collections — the saga's third axis must clear it too
curl -sf -X PUT "$COLLECTIONS/collections/favourites/items/meme/$MEME_ID" -H "Authorization: Bearer $LACCESS" >/dev/null
SAVED=$(curl -sf "$COLLECTIONS/collections/favourites/items" -H "Authorization: Bearer $LACCESS" \
    | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))')
[ "$SAVED" = 1 ] || { echo "FAIL: expected 1 saved favourite before deletion, got $SAVED"; exit 1; }
# deleting is step-up (FULL_CHAIN): no factors here, so re-entering the password elevates at once
ELEV=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$SEC/account/step-up" -H "Authorization: Bearer $LACCESS" \
    -H 'Content-Type: application/json' -d "{\"action\":\"delete-account\",\"password\":\"$PASSWORD\"}")
[ "$ELEV" = 200 ] || { echo "FAIL: step-up before deletion expected 200, got $ELEV"; exit 1; }
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
    # the token still verifies offline (until exp), so we can read the now-empty collection
    COLL_GONE=$(curl -sf "$COLLECTIONS/collections/favourites/items" -H "Authorization: Bearer $LACCESS" \
        | python3 -c 'import json,sys; print(len(json.load(sys.stdin)) == 0)')
    if [ "$MEME_GONE" = 404 ] && [ "$ANON" = True ] && [ "$COLL_GONE" = True ]; then SAGA_OK=yes; break; fi
    sleep 2
done
[ -n "$SAGA_OK" ] || { echo "FAIL: purge incomplete (meme:$MEME_GONE anon:$ANON coll:$COLL_GONE)"; exit 1; }
# register answers 201 whether the email is taken or not (anti-enumeration), so a status probe
# can no longer detect the freed email. The goodbye mail is sent in the same transaction that
# deletes the user row, so once it shows up the email IS free — prove it by re-registering and
# expecting a SECOND verification mail (a still-taken address would mail an
# "already have an account" notice instead).
GOODBYE=""
for i in $(seq 1 30); do   # the goodbye mail is async (Kafka -> email -> SMTP); give it a moment
    curl -sf "$MAIL_UI/api/v1/search?query=to:$LEAVER" | grep -q "account is deleted" && { GOODBYE=1; break; }
    sleep 2
done
[ -n "$GOODBYE" ] || { echo "FAIL: no goodbye mail"; exit 1; }
curl -sf -X POST "$SEC/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$LEAVER\",\"password\":\"$PASSWORD\"}" >/dev/null
VERIFY_MAILS=""
for i in $(seq 1 15); do
    VERIFY_MAILS=$(curl -sf "$MAIL_UI/api/v1/search?query=to:$LEAVER" | python3 -c \
        'import json,sys; ms=json.load(sys.stdin)["messages"]; print(sum(1 for m in ms if m["Subject"]=="Verify your email"))')
    [ "$VERIFY_MAILS" -ge 2 ] && break
    sleep 2
done
[ "$VERIFY_MAILS" -ge 2 ] || { echo "FAIL: email not freed after full saga (verification mails: $VERIFY_MAILS)"; exit 1; }

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
curl -sf -X POST "$SEC/account/step-up" -H "Authorization: Bearer $KACCESS" -H 'Content-Type: application/json' \
    -d "{\"action\":\"delete-account\",\"password\":\"$PASSWORD\"}" >/dev/null
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
# same freed-email proof as above: goodbye mail first, then a re-register must start a
# brand-new verification (register alone is 201 either way — anti-enumeration)
GOODBYE2=""
for i in $(seq 1 30); do
    curl -sf "$MAIL_UI/api/v1/search?query=to:$KEEPER" | grep -q "account is deleted" && { GOODBYE2=1; break; }
    sleep 2
done
[ -n "$GOODBYE2" ] || { echo "FAIL: no goodbye mail after wizard saga"; exit 1; }
curl -sf -X POST "$SEC/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$KEEPER\",\"password\":\"$PASSWORD\"}" >/dev/null
VERIFY_MAILS=""
for i in $(seq 1 15); do
    VERIFY_MAILS=$(curl -sf "$MAIL_UI/api/v1/search?query=to:$KEEPER" | python3 -c \
        'import json,sys; ms=json.load(sys.stdin)["messages"]; print(sum(1 for m in ms if m["Subject"]=="Verify your email"))')
    [ "$VERIFY_MAILS" -ge 2 ] && break
    sleep 2
done
[ "$VERIFY_MAILS" -ge 2 ] || { echo "FAIL: email not freed after wizard saga (verification mails: $VERIFY_MAILS)"; exit 1; }

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

step "collections UI on its own origin: page served, both CORS edges answer the preflight"
curl -sf http://localhost:8093/ | grep -q "My collections" \
    || { echo "FAIL: collections-ui is not serving its page"; exit 1; }
# what the browser does before the UI's first authorized call — one preflight per service
curl -sf -X OPTIONS "http://localhost:8092/collections/favourites/items" \
    -H "Origin: http://localhost:8093" -H "Access-Control-Request-Method: GET" -o /dev/null -D - \
    | grep -qi "^Access-Control-Allow-Origin: http://localhost:8093" \
    || { echo "FAIL: user-collections does not allow the UI origin"; exit 1; }
curl -sf -X OPTIONS "http://localhost:8080/authenticate" \
    -H "Origin: http://localhost:8093" -H "Access-Control-Request-Method: POST" -o /dev/null -D - \
    | grep -qi "^Access-Control-Allow-Origin: http://localhost:8093" \
    || { echo "FAIL: security does not allow the collections-ui origin"; exit 1; }

step "observability: Prometheus scrapes containers, Grafana serves the dashboard"
for i in $(seq 1 30); do
    UP=$(curl -sf "http://localhost:9090/api/v1/query?query=up%7Bjob%3D%22cadvisor%22%7D" \
        | python3 -c 'import json,sys; r=json.load(sys.stdin)["data"]["result"]; print(r[0]["value"][1] if r else "0")' 2>/dev/null || echo 0)
    [ "$UP" = 1 ] && break
    [ "$i" = 30 ] && { echo "FAIL: Prometheus never saw cAdvisor up"; exit 1; }
    sleep 2
done
curl -sf http://localhost:3000/api/health | grep -q '"database": *"ok"' \
    || { echo "FAIL: Grafana health not ok"; exit 1; }
curl -sf "http://localhost:3000/api/dashboards/uid/stack-containers" >/dev/null \
    || { echo "FAIL: the provisioned dashboard is missing"; exit 1; }

echo
echo "SMOKE PASS: register -> mail(Mailpit via Kafka) -> verify -> sign-in -> /me, mail auth,"
echo "            social login via the stub IdP (OAuth code+PKCE -> session -> /me),"
echo "            social login, USERINFO flavour (github-shaped: access token -> /userinfo -> session),"
echo "            MFA: enrol e-mail factor, password -> ticket -> mailed code -> session,"
echo "            MFA: enrol TOTP (authenticator app), password -> ticket -> computed code -> session,"
echo "            MFA: recovery code stands in for the factor once (batch shown once; replay refused),"
echo "            memes gated by security (401 anon; public reads; toggle votes on memes and comments),"
echo "            outbox survives a mail-service outage,"
echo "            delete-account SAGA: memes purged, comments anonymised, goodbye mail, email freed,"
echo "            wizard override honoured: popular meme kept anonymised, chosen comments deleted,"
echo "            formula race simulated in Python and streamed as SSE state,"
echo "            collections UI served on its own origin; CORS preflights pass on both edges,"
echo "            observability: Prometheus scrapes cAdvisor, Grafana provisioned with the stack dashboard"
