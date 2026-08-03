# OpenCode Provider Management Guide

> **Purpose**: How to manage LLM providers, credentials, and configuration in OpenCode.

---

## Overview

OpenCode supports 75+ LLM providers through AI SDK and Models.dev. This guide covers credential management, configuration, and customization.

---

## Credential Storage

### Location
```
~/.local/share/opencode/auth.json
```

### Management
- Use `/connect` command in TUI to add/update credentials
- Use `/disconnect` to remove credentials
- Credentials are stored per-provider

---

## Configuration Files

### Project-Level Config
**Location**: Project root `opencode.json`

```json
{
  "$schema": "https://opencode.ai/config.json",
  "provider": {
    "anthropic": {
      "options": { "baseURL": "https://api.anthropic.com/v1" },
      "blacklist": ["claude-opus-4-20250514"]
    }
  }
}
```

### Global Config
**Location**: `~/.config/opencode/opencode.jsonc`

```json
{
  "provider": {
    "ollama": {
      "npm": "@ai-sdk/openai-compatible",
      "name": "Ollama (local)",
      "options": { "baseURL": "http://localhost:11434/v1" },
      "models": { "llama2": { "name": "Llama 2" } }
    }
  }
}
```

---

## Common Commands

| Command | Purpose |
|---------|---------|
| `/connect` | Add/update provider credentials |
| `/disconnect` | Remove provider credentials |
| `/models` | Select available models |
| `/providers` | List configured providers |

---

## Provider Configuration Options

### Base URL Override
```json
{
  "provider": {
    "anthropic": {
      "options": {
        "baseURL": "https://custom-proxy.example.com/v1"
      }
    }
  }
}
```

### Model Blacklist/Whitelist
```json
{
  "provider": {
    "anthropic": {
      "blacklist": ["claude-opus-4-20250514"],
      "whitelist": ["claude-sonnet-4-20250514"]
    }
  }
}
```

### Custom Provider (OpenAI-compatible)
```json
{
  "provider": {
    "my-custom-provider": {
      "npm": "@ai-sdk/openai-compatible",
      "name": "My Custom Provider",
      "options": {
        "baseURL": "http://localhost:8080/v1"
      },
      "models": {
        "model-id": {
          "name": "Model Display Name",
          "limit": { "context": 128000, "output": 65536 }
        }
      }
    }
  }
}
```

---

## Environment Variables

Some providers require environment variables instead of or in addition to `/connect`:

| Provider | Required Variables |
|----------|-------------------|
| Azure OpenAI | `AZURE_RESOURCE_NAME` |
| Amazon Bedrock | `AWS_PROFILE` or `AWS_ACCESS_KEY_ID` + `AWS_SECRET_ACCESS_KEY` |
| Google Vertex AI | `GOOGLE_CLOUD_PROJECT`, `GOOGLE_APPLICATION_CREDENTIALS` |
| GitLab | `GITLAB_TOKEN` (optional, for self-hosted) |

---

## Common Patterns

### Adding a New Provider
1. Run `/connect` in TUI
2. Search for provider name
3. Enter API key or authenticate via OAuth
4. Run `/models` to verify availability

### Switching Between Providers
1. Run `/models` to see all available models
2. Select desired model
3. Provider is automatically used for that session

### Project-Specific Overrides
Use project `opencode.json` to:
- Override base URLs for proxies
- Blacklist/whitelist models
- Add custom providers not in directory
- Configure provider-specific options

---

## Troubleshooting

### "Provider not found"
- Check if provider is in the [directory](https://opencode.ai/docs/providers#directory)
- For custom providers, ensure `npm` field is correct

### "API key invalid"
- Run `/connect` again to re-enter credentials
- Check `~/.local/share/opencode/auth.json` for stale entries

### "Model not available"
- Verify model ID matches provider's model list
- Check whitelist/blacklist in config

---

## See Also

- [OpenCode Providers Docs](https://opencode.ai/docs/providers)
- [OpenCode Config Docs](https://opencode.ai/docs/config)

---

**Last Updated**: 2026-07-29