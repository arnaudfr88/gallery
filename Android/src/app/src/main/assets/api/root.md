### Info server
Current server information

**GET** `/`

**Response**
Returns the app version, the currently initialized model, whether it is in inference, and the inference queue.
```json
{
  "name": "AI Edge Gallery OpenAI API Server",
  "version": "1.0.11",
  "model": "Gemma-4-E2B-it",
  "is_inferring": false,
  "request_count": 0
}
```
