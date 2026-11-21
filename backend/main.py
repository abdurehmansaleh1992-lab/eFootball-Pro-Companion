import base64
import hmac
import json
import os
import re
import urllib.error
import urllib.request

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="eFootball Pro Companion AI", version="1.0.0")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
MAX_IMAGE_BYTES = 8 * 1024 * 1024


class AnalyzeRequest(BaseModel):
    image_base64: str = Field(min_length=100)
    mime_type: str = Field(default="image/jpeg")


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)


@app.get("/health")
def health():
    return {"status": "ok", "service": "efootball-ai"}


@app.post("/analyze")
def analyze(payload: AnalyzeRequest, x_app_token: str = Header(default="")):
    expected = os.getenv("APP_ACCESS_TOKEN", "")
    api_key = os.getenv("GEMINI_API_KEY", "")
    if not expected or not hmac.compare_digest(x_app_token, expected):
        raise HTTPException(status_code=401, detail="Invalid app access token.")
    if not api_key:
        raise HTTPException(status_code=503, detail="AI service is not configured by the server owner.")
    if payload.mime_type not in ("image/jpeg", "image/png", "image/webp"):
        raise HTTPException(status_code=415, detail="Unsupported image type.")

    try:
        image_bytes = base64.b64decode(payload.image_base64, validate=True)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid image encoding.")
    if len(image_bytes) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="Screenshot is larger than 8 MB.")

    prompt = """
You are an expert eFootball Mobile tactical analyst. Analyze ONLY what is actually visible in this single screenshot.
Return a clear coaching report in plain text with these headings:
1) WHAT IS VISIBLE: identify whether this is a formation/tactics screen, in-match pitch view, or another screen.
2) OPPONENT FORMATION: name it only if the visible player layout supports a reliable identification; otherwise say NOT CLEAR.
3) OPPONENT PLAYSTYLE: infer only if the screenshot visibly contains explicit playstyle information. A static in-match screenshot alone cannot prove Possession, Quick Counter, Long Ball Counter, Out Wide, or Long Ball. Mark as UNKNOWN if not shown.
4) THREAT AREAS: describe only visible spacing, overloads, or gaps.
5) COUNTER PLAN: give a cautious, practical formation/defensive/pressing recommendation based on visible evidence; label it PROVISIONAL when evidence is incomplete.
6) CONFIDENCE: High/Medium/Low and explain why.
Never invent player names, positions, formation, or playstyle. Do not claim to see hidden match events. Ask for a second screenshot of the opponent Game Plan screen or several match moments when needed to identify playstyle and habits.
Keep the report concise, useful, and specific to eFootball Mobile.
"""
    body = {
        "contents": [{
            "role": "user",
            "parts": [
                {"inline_data": {"mime_type": payload.mime_type, "data": payload.image_base64}},
                {"text": prompt}
            ]
        }],
        "generationConfig": {"temperature": 0.2, "maxOutputTokens": 1200}
    }
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent"
    request = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
        method="POST"
    )
    try:
        with urllib.request.urlopen(request, timeout=75) as response:
            result = json.loads(response.read().decode("utf-8"))
        parts = result.get("candidates", [{}])[0].get("content", {}).get("parts", [])
        analysis = "\n".join(p.get("text", "") for p in parts if p.get("text"))
        if not analysis.strip():
            raise ValueError("Model returned no text.")
        return {"analysis": analysis, "model": GEMINI_MODEL}
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")[:1000]
        raise HTTPException(status_code=502, detail=f"AI provider error: {detail}")
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"AI analysis failed: {str(exc)[:300]}")



@app.post("/chat")
def chat(payload: ChatRequest, x_app_token: str = Header(default="")):
    expected = os.getenv("APP_ACCESS_TOKEN", "")
    api_key = os.getenv("GEMINI_API_KEY", "")
    if not expected or not hmac.compare_digest(x_app_token, expected):
        raise HTTPException(status_code=401, detail="Invalid app access token.")
    if not api_key:
        raise HTTPException(status_code=503, detail="AI service is not configured by the server owner.")

    prompt = (
        "You are eFootball Pro Companion, a practical expert eFootball Mobile coach. "
        "Reply in Amharic when the user writes Amharic; otherwise reply in the user's language. "
        "Give concise, direct tactical advice. Never claim to see a live match or know hidden game state. "
        "Ask for a screenshot when visual evidence is required. Do not promise network fixes or zero lag. "
        "User message: " + payload.message
    )
    body = {
        "contents": [{"role": "user", "parts": [{"text": prompt}]}],
        "generationConfig": {"temperature": 0.35, "maxOutputTokens": 900}
    }
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent"
    request = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
        method="POST"
    )
    try:
        with urllib.request.urlopen(request, timeout=75) as response:
            result = json.loads(response.read().decode("utf-8"))
        parts = result.get("candidates", [{}])[0].get("content", {}).get("parts", [])
        answer = "\\n".join(p.get("text", "") for p in parts if p.get("text"))
        if not answer.strip():
            raise ValueError("Model returned no text.")
        return {"answer": answer, "model": GEMINI_MODEL}
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")[:1000]
        raise HTTPException(status_code=502, detail=f"AI provider error: {detail}")
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"AI chat failed: {str(exc)[:300]}")
