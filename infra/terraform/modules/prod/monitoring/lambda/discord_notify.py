"""CloudWatch 알람(SNS) → Discord 웹훅 전달. 의존성 0(표준 urllib)이라 별도 패키징 불필요.

SNS는 Discord 웹훅을 직접 구독할 수 없다(구독 확인 핸드셰이크·메시지 포맷 불일치).
이 Lambda가 SNS 메시지를 Discord 임베드로 변환해 POST 한다. (ADR-0027)
"""

import json
import os
import urllib.request

WEBHOOK_URL = os.environ["DISCORD_WEBHOOK_URL"]

# 상태별 임베드 색상
_COLORS = {
    "ALARM": 0xE01E5A,  # red
    "OK": 0x2EB67D,  # green
    "INSUFFICIENT_DATA": 0xECB22E,  # yellow
}


def _post(payload):
    req = urllib.request.Request(
        WEBHOOK_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        resp.read()


def handler(event, context):
    for record in event.get("Records", []):
        sns = record.get("Sns", {})
        raw = sns.get("Message", "") or ""
        try:
            msg = json.loads(raw)
        except (ValueError, TypeError):
            msg = {}

        state = msg.get("NewStateValue", "NOTIFICATION")
        name = msg.get("AlarmName") or sns.get("Subject") or "CloudWatch"
        reason = msg.get("NewStateReason") or raw or "-"
        desc = msg.get("AlarmDescription") or ""
        region = msg.get("Region") or "-"
        when = msg.get("StateChangeTime") or ""

        embed = {
            "title": f"[{state}] {name}"[:256],
            "color": _COLORS.get(state, 0x808080),
            "fields": [
                {"name": "State", "value": state, "inline": True},
                {"name": "Region", "value": region, "inline": True},
                {"name": "Reason", "value": reason[:1000], "inline": False},
            ],
        }
        if desc:
            embed["description"] = desc[:2000]
        if when:
            embed["footer"] = {"text": when}

        _post({"username": "Kohere prod alerts", "embeds": [embed]})

    return {"ok": True}
