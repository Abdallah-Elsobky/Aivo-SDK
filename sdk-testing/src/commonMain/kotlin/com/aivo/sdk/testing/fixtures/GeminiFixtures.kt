package com.aivo.sdk.testing.fixtures

public object GeminiFixtures {
    public const val PLAIN_RESPONSE: String = """{
      "id": "v1_gemini_interaction_123",
      "status": "completed",
      "model": "gemini-2.5-flash",
      "usage": {
        "total_input_tokens": 12,
        "total_output_tokens": 24,
        "total_tokens": 36
      },
      "steps": [
        {
          "type": "model_output",
          "content": [{ "type": "text", "text": "Hello! I am Gemini, how can I assist?" }]
        }
      ]
    }"""

    public const val TOOL_CALL_RESPONSE: String = """{
      "id": "v1_gemini_interaction_124",
      "status": "requires_action",
      "model": "gemini-2.5-flash",
      "steps": [
        {
          "type": "function_call",
          "id": "call_gemini_456",
          "name": "get_balance",
          "arguments": "{\"accountId\":\"ACC-101\"}"
        }
      ]
    }"""

    public const val SSE_STREAM: String = """event: interaction.created
data: {"id":"v1_stream_gemini_1","model":"gemini-2.5-flash"}

event: step.start
data: {"stepIndex":0,"type":"model_output"}

event: step.delta
data: {"stepIndex":0,"delta":{"text":"Hello"}}

event: step.delta
data: {"stepIndex":0,"delta":{"text":" from Gemini!"}}

event: step.stop
data: {"stepIndex":0}

event: interaction.completed
data: {"id":"v1_stream_gemini_1","status":"completed","usage":{"total_input_tokens":5,"total_output_tokens":6}}

"""

    public const val ERROR_RESPONSE: String = """{
      "error": {
        "code": 403,
        "message": "API key not valid. Please pass a valid API key.",
        "status": "PERMISSION_DENIED"
      }
    }"""
}
