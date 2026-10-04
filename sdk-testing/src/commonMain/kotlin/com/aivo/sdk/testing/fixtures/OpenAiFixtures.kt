package com.aivo.sdk.testing.fixtures

public object OpenAiFixtures {
    public const val PLAIN_RESPONSE: String = """{
      "id": "gen-1790250440-kNXAwHes6n0MDbc8g5Fm",
      "object": "chat.completion",
      "model": "deepseek/deepseek-v4.1-flash",
      "choices": [{
        "index": 0,
        "finish_reason": "stop",
        "message": {
          "role": "assistant",
          "content": "Hello! How can I help you today?",
          "reasoning": "Simple greeting response"
        }
      }],
      "usage": {
        "prompt_tokens": 10,
        "completion_tokens": 15,
        "total_tokens": 25,
        "cost": 0.00001
      }
    }"""

    public const val TOOL_CALL_RESPONSE: String = """{
      "id": "gen-1790250441-toolcall",
      "object": "chat.completion",
      "model": "deepseek/deepseek-v4.1-flash",
      "choices": [{
        "index": 0,
        "finish_reason": "tool_calls",
        "message": {
          "role": "assistant",
          "content": null,
          "tool_calls": [
            {
              "id": "call_abc123",
              "type": "function",
              "function": {
                "name": "get_balance",
                "arguments": "{\"accountId\":\"ACC-101\"}"
              }
            }
          ]
        }
      }],
      "usage": {
        "prompt_tokens": 20,
        "completion_tokens": 25,
        "total_tokens": 45
      }
    }"""

    public const val SSE_STREAM: String = """: keep-alive
data: {"choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"}}]}

data: {"choices":[{"index":0,"delta":{"content":" world"}}]}

data: {"choices":[{"index":0,"delta":{"content":"!"},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":3}}

data: [DONE]
"""

    public const val ERROR_RESPONSE: String = """{
      "error": {
        "message": "Invalid API key provided",
        "type": "invalid_request_error",
        "code": "invalid_api_key"
      }
    }"""
}
