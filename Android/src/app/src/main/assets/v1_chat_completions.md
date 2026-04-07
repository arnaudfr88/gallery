### Create chat completion
Creates a model response for the given chat conversation.

**POST** `/v1/chat/completions`

Supported features
- ✅ Chat completions
- ✅ Streaming
- ❌ JSON mode
- ❌ Reproducible outputs
- ❌ Vision
- ❌ Tools
- ✅ Reasoning/thinking control (for thinking models)
- ❌ Logprobs

Supported request fields
- ✅ model
- ✅ messages
  - ✅ Text content
  - ❌ Image content
    - ❌ Base64 encoded image
    - ❌ Image URL
  - ❌ Array of content parts
- ❌ frequency_penalty
- ❌ presence_penalty
- ❌ response_format
- ❌ seed
- ❌ stop
- ✅ stream
- ❌ stream_options
- ❌ include_usage
- ❌ temperature
- ❌ top_p
- ❌ max_tokens
- ❌ tools
- ❌ reasoning_effort ("high", "medium", "low", "none")
- ❌ reasoning
- ❌ effort ("high", "medium", "low", "none")
- ❌ tool_choice
- ❌ logit_bias
- ❌ user
- ❌ n

**Request Body**
```json
{
  "model": "Gemma-4-E2B-it",
  "messages": [
    {
      "role": "user",
      "content": "Hello!"
    }
  ]
}
```

**Response**
Returns a chat completion object, or a streamed sequence of chat completion chunk objects if the request is streamed.
```json
{
  "id": "chatcmpl-e70cbeae-ff9c-4e66-8494-2618be6e4f06",
  "object": "chat.completion",
  "created": 1775557216,
  "model": "Gemma-4-E2B-it",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "\n\nHello there, how may I assist you today?"
    },
    "finish_reason": "stop"
  }]
}
```
