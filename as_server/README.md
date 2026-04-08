# As Server (OpenAI-Compatible API)

This directory documents the **OpenAI-compatible HTTP server** embedded in the **AI Edge Gallery** Android app, and hosts the **Bruno** API test collection. After the service is started on a device, clients on the same LAN can call Chat Completions– and Models-style endpoints at `http://<device-ip>:<port>`.

---

## Android code layout

These packages implement the full path from **on-device local LLM hosting** to **OpenAI-style REST** exposure.

### `customtasks/chatcompletion`

Built-in **Custom Task**: under Gallery **Server → Chat Completion**, it surfaces and manages the local OpenAI API server.

| File | Role |
|------|------|
| `ChatCompletionTask.kt` | Task definition (Server category); model init/teardown via `LlmChatModelHelper`; copy describes the local Chat Completions service |
| `ChatCompletionTaskModule.kt` | Hilt module: registers `ChatCompletionTask` in the `CustomTask` set |
| `ChatCompletionTaskScreen.kt` | Compose UI: start/stop, address/port, API doc links—where users control the server and see status |

### `server`

**Ktor (CIO)** HTTP server core.

| File | Role |
|------|------|
| `OpenAIApiServer.kt` | Embedded `ApplicationEngine`: `GET /` (info and runtime status), `GET /v1/models`, `POST /v1/chat/completions` (including SSE streaming); maps requests to LiteRT LM inference and multimodal inputs (image, audio, etc.) |
| `LlmModelHelperExt.kt` | Extensions supporting LLM inference |
| `dto/OpenAIRequest.kt`, `dto/OpenAIResponse.kt` | OpenAI-shaped request/response DTOs |

### `service`

**Android foreground `Service`**: keeps the API process eligible to run and supplies `OpenAIApiServer` with an initialized model manager.

| File | Role |
|------|------|
| `ApiServerService.kt` | Started with `startForegroundService`; reads `port` (default 8080) and `host` (default `0.0.0.0`) from `Intent`; builds `OpenAIApiServer` and `start()`; `stop()` tears down the engine |
| `ModelManagerHolder.kt` | Holds an injectable `ModelManagerAccessor` for the service when constructing `OpenAIApiServer` |
| `ModelManagerAccessor.kt` | Abstraction over downloads, lookup by name, model instances, etc., wired into Gallery’s model pipeline |

### `ui/apiserver`

**UI state and ViewModel**: bridges Compose and `ApiServerService`.

| File | Role |
|------|------|
| `ApiServerViewModel.kt` | Tracks running state, port, host; `startServer()` / `stopServer()` delegate to `ApiServerService`; exposes inference and request counters from `ApiServerStatus` |
| `ApiServerStatus.kt` | Global `StateFlow`s for “inferring” and in-flight request counts, shared by `GET /` and the UI |

**Flow**: user acts in `ChatCompletionTaskScreen` → `ApiServerViewModel` starts/stops `ApiServerService` → `OpenAIApiServer` serves HTTP → LAN clients (or this repo’s Bruno collection) use the device IP and port.

---

## `bruno/` API collection

Collection path: **`as_server/bruno/as-server/`** (collection name `as-server` in `bruno.json`; root metadata in `collection.bru`).

Requests use **`{{MY_PHONE}}`** as the hostname (typically the LAN IP of the phone or emulator running Gallery with the API server enabled). The port matches the Android default **8080**, for example:

- `http://{{MY_PHONE}}:8080/` — root info
- `http://{{MY_PHONE}}:8080/v1/models`
- `http://{{MY_PHONE}}:8080/v1/chat/completions`

The `.bru` files cover streaming/non-streaming chat, system prompts, temperature, multi-turn assistant, images (URL/base64/multi/detail), audio (WAV/MP3), thinking, empty input, model listing, no-model cases, and more. Some requests embed **very large base64 payloads**; Bruno CLI runs on Node.js, which may hit the default heap limit when loading or executing those requests.

### Running with Bruno CLI

**Node.js heap (recommended for large base64 requests):** raise the old-space limit before `bru run`, e.g. `8192` MB:

```bash
# Bash / zsh / Git Bash on Windows
export NODE_OPTIONS="--max-old-space-size=8192"
bru run --env-var MY_PHONE=192.168.1.100
```

```powershell
# PowerShell
$env:NODE_OPTIONS = "--max-old-space-size=8192"
bru run --env-var MY_PHONE=192.168.1.100
```

You can also prefix a single command on Unix: `NODE_OPTIONS="--max-old-space-size=8192" bru run ...`

1. **Install the CLI** (either):

   ```bash
   npm install -g @usebruno/cli
   ```

   Or use `npx` without a global install:

   ```bash
   npx @usebruno/cli --version
   ```

2. **Change to the collection directory** (adjust for your clone path):

   ```bash
   cd as_server/bruno/as-server
   ```

3. **Set `MY_PHONE` and run the whole collection**:

   ```bash
   bru run --env-var MY_PHONE=192.168.1.100
   ```

   Replace `192.168.1.100` with the device’s real IP. Repeat `--env-var` for other variables.

4. **Other common options**:

   - Single request: `bru run root-info.bru --env-var MY_PHONE=192.168.1.100`
   - Environment file: `bru run --env-file /path/to/environment.bru` (or JSON env exported from Bruno)
   - Stop on first failure: `bru run --bail --env-var MY_PHONE=...`
   - Bruno CLI v3+ may require developer sandbox for scripts that use extra npm packages or filesystem APIs: `bru run --sandbox=developer ...`

Before running: **Gallery’s API server must be started on the target device**, **the machine running Bruno must reach that IP** (same network, or port forwarding as needed), and **the port must not be blocked by a firewall**.

See also: [Running a Collection](https://docs.usebruno.com/bru-cli/runCollection).
