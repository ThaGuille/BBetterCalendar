---
name: stitch-upload
description: >-
  Upload local assets (images, mockups, extracted HTML, design markdown) to a Stitch project.
  ALWAYS use this skill when you need to upload visual assets, HTML pages, or design docs
  to Stitch, particularly when direct MCP tool calls fail or truncate due to
  base64 token limits.
allowed-tools:
  - "stitch*:*"
  - "Bash"
  - "Read"
  - "Write"
  - "web_fetch"
---

> ## ⚠️ PROJECT OVERRIDE — BBetterCalendar (read first, outranks everything below)
>
> **Role of Stitch in this repo:** Stitch is a *visual reference generator* — it is the image
> model this CLI session lacks. Its output (HTML/Tailwind + PNG screenshots) is **never shipped**.
> Android code comes from `image-to-code` + `impeccable`, rebuilt as XML on the `bb_*` tokens.
>
> - **Device:** always `deviceType: MOBILE`. This app is Android (Pixel-class, Material 3 patterns:
>   navigation bar, top app bar, single FAB). Never desktop, never iOS idioms.
> - **Screens worth generating:** Home (Pomodoro), Progress (charts + usage bands), Calendar, Projects.
> - **Outputs go in** `.stitch/designs/` (HTML + PNG) and `.stitch/metadata.json`. To use a result,
>   hand the PNG to `image-to-code`; `/impeccable shape` owns the direction.
> - **Needs the Stitch MCP** (`/mcp` must list `stitch`). If it is missing, stop and tell the user
>   to run the setup in `.claude/docs/stitch-setup.md` — never ask them to paste an API key in chat.
> - **Never write an API key into any file.** `upload_to_stitch.py` reads it from `--api-key` /
>   environment; pass it from the env var `STITCH_API_KEY`, do not inline it.
> - Reference images live in `.claude/references/inspiration/` (see its README). Don't upload
>   screenshots of apps to Stitch as *copy targets*: use them to extract a system, not to clone.
> - Stitch is web-oriented; treat its component choices (hover states, Tailwind classes, CSS
>   motion) as visual intent only.


# Upload-to-Stitch

Upload local assets (images, mockups, HTML, and markdown files) to a Stitch project using the
provided upload script, which bypasses the MCP tool's base64 output token limits.

> [!NOTE]
> The AI model cannot upload files via MCP tools directly because the base64
> encoding of even a small file exceeds the model's output token limit (~16K
> tokens). This script reads the file and sends it directly over HTTP.

## Steps

### 1. Identify Target Project

Use `list_projects` to find the correct `projectId`.

### 2. Get the API Key

Locate your active MCP server configuration file and extract the API key:
- **Antigravity**: `.gemini/antigravity/mcp_config.json` or `.gemini/jetski/mcp_config.json`
- **Gemini CLI**: `~/.gemini/settings.json` or `~/.gemini/extensions/Stitch/gemini-extension.json`
- **Claude Code**: `~/.claude.json`

Extract:
- **API Key**: From the `X-Goog-Api-Key` header or auth argument
- **MCP URL** (optional): From the `httpUrl` or endpoint argument (defaults to
  `https://stitch.googleapis.com`)

> [!IMPORTANT]
> If you cannot find the API key in any of these locations, or if you cannot access these files, you MUST ask the user to provide the Stitch API key. Do not proceed without a valid API key.

### 3. Run Upload Script

> [!WARNING]
> **Checkpoint — User Confirmation Required.**
> Before running the upload script, you **MUST** pause and present the file(s)
> to be uploaded (paths, sizes, and types) to the user and wait for explicit
> approval. Do **NOT** execute the upload script until the user confirms.

Use `run_command` to execute the Python script:

```bash
python3 <SKILL_DIR>/scripts/upload_to_stitch.py \
  --project-id <PROJECT_ID> \
  --file-path <PATH_TO_FILE> \
  --api-key <API_KEY> \
  [--api-url <STITCH_API_URL>] \
  [--title <SCREEN_TITLE>] \
  [--generated-by <GENERATED_BY>]
```

> [!TIP]
> **macOS / SSL Certificate Troubleshooting:**
> If the upload fails with `ssl.SSLCertVerificationError: [SSL: CERTIFICATE_VERIFY_FAILED] unable to get local issuer certificate`, this means your Python installation does not have root certificate authorities configured.
> 
> The script automatically attempts to use the `certifi` package to load the CA bundle if it is installed in your python environment. If `certifi` is not installed, you can either install it (`pip install certifi`) or manually supply the `SSL_CERT_FILE` environment variable when running the script:
> ```bash
> SSL_CERT_FILE=$(python3 -c "import certifi; print(certifi.where())") python3 <SKILL_DIR>/scripts/upload_to_stitch.py \
>   --project-id <PROJECT_ID> \
>   --file-path <PATH_TO_FILE> \
>   --api-key <API_KEY> \
>   [--api-url <STITCH_API_URL>] \
>   [--title <SCREEN_TITLE>] \
>   [--generated-by <GENERATED_BY>]
> ```

### Supported File Types

| Extension | MIME Type |
|:---|:---|
| `.png` | `image/png` |
| `.jpg`, `.jpeg` | `image/jpeg` |
| `.webp` | `image/webp` |
| `.html`, `.htm` | `text/html` |
| `.md` | `text/markdown` |

The script auto-detects MIME type from the file extension.

### Script Options

- `--project-id`: **Required**. The Stitch project ID.
- `--file-path`: **Required**. Path to the local file to upload.
- `--api-key`: **Required**. API key for Stitch authorization.
- `--api-url`: Optional. Base URL of the Stitch API. Defaults to `https://stitch.googleapis.com`.
- `--title`: Optional. Title for the uploaded screen. When uploading extracted HTML from a web app, set this to the **route path** of the page (e.g., `'/dashboard'`, `'/settings/profile'`, `'/inbox'`) so that the screen name/title in Stitch clearly identifies the route.
- `--generated-by`: Optional. Specify how the uploaded file was generated (e.g., 'stitch::extract-static-html' skill, 'Claude Code', 'Codex', 'Gemini' etc.).