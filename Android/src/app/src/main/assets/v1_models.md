### List models
List currently available models, only those that have been downloaded.

**GET** `/v1/models`

**Response**
Returns a list of model objects.
```json
{
  "object": "list",
  "data": [
    {
      "id": "Gemma-4-E2B-it",
      "object": "model",
      "created": 1775556758,
      "owned_by": "local"
    }
  ]
}
```
