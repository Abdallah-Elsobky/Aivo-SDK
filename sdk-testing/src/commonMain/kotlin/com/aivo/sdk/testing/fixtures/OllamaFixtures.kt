package com.aivo.sdk.testing.fixtures

public object OllamaFixtures {
    public const val PLAIN_RESPONSE: String = """{
      "model": "gemma4:31b",
      "created_at": "2026-09-24T12:23:57.505627128Z",
      "message": { "role": "assistant", "content": "Hello! How can I help you today?" },
      "done": true,
      "done_reason": "stop",
      "total_duration": 215228907,
      "prompt_eval_count": 19,
      "eval_count": 3
    }"""

    public const val TOOL_CALL_RESPONSE: String = """{
      "model": "gemma4:31b",
      "created_at": "2026-09-24T12:23:57.505627128Z",
      "message": {
        "role": "assistant",
        "content": "",
        "tool_calls": [
          {
            "function": {
              "name": "get_balance",
              "arguments": { "accountId": "ACC-101" }
            }
          }
        ]
      },
      "done": true,
      "done_reason": "stop",
      "eval_count": 12
    }"""

    public const val NDJSON_STREAM: String = """{"message":{"role":"assistant","content":"Hello"},"done":false}
{"message":{"role":"assistant","content":" there"},"done":false}
{"message":{"role":"assistant","content":"!"},"done":false}
{"done":true,"done_reason":"stop","prompt_eval_count":10,"eval_count":3}
"""

    public const val ERROR_RESPONSE: String = """{
      "error": "model 'non-existent' not found, try pulling it first"
    }"""
}
