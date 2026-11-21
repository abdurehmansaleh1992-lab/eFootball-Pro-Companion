import json
import os
import unittest
from unittest.mock import patch

from fastapi import HTTPException
from main import AnalyzeRequest, ChatRequest, analyze, chat, health


class FakeResponse:
    def __init__(self, payload):
        self.payload = json.dumps(payload).encode("utf-8")

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def read(self):
        return self.payload


class BackendTests(unittest.TestCase):
    def test_health_endpoint_payload(self):
        self.assertEqual(health(), {"status": "ok", "service": "efootball-ai"})

    def test_chat_rejects_missing_or_wrong_token(self):
        with patch.dict(os.environ, {"APP_ACCESS_TOKEN": "correct-token", "GEMINI_API_KEY": "unused"}):
            with self.assertRaises(HTTPException) as ctx:
                chat(ChatRequest(message="Hello"), x_app_token="wrong-token")
        self.assertEqual(ctx.exception.status_code, 401)

    def test_analyze_rejects_wrong_token(self):
        payload = AnalyzeRequest(image_base64="A" * 120)
        with patch.dict(os.environ, {"APP_ACCESS_TOKEN": "correct-token", "GEMINI_API_KEY": "unused"}):
            with self.assertRaises(HTTPException) as ctx:
                analyze(payload, x_app_token="wrong-token")
        self.assertEqual(ctx.exception.status_code, 401)

    def test_chat_joins_multiple_text_parts_with_real_newlines(self):
        response = {
            "candidates": [{
                "content": {
                    "parts": [{"text": "First line"}, {"text": "Second line"}]
                }
            }]
        }
        with patch.dict(os.environ, {"APP_ACCESS_TOKEN": "secret", "GEMINI_API_KEY": "test-key"}):
            with patch("main.urllib.request.urlopen", return_value=FakeResponse(response)):
                result = chat(ChatRequest(message="Hi"), x_app_token="secret")
        self.assertEqual(result["answer"], "First line\nSecond line")


if __name__ == "__main__":
    unittest.main()
