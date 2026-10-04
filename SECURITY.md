# Security Policy

## API Keys and Secrets

> **No API keys, secrets, or credentials are stored in this repository.**

All authentication is handled at runtime via:
- Environment variables on JVM/Server
- `local.properties` → `BuildConfig` on Android (excluded from VCS via `.gitignore`)
- Runtime user input or a secure credentials store

See [docs/DEVELOPER_GUIDE.md#2-security--api-key-best-practices](docs/DEVELOPER_GUIDE.md#2-security--api-key-best-practices) for the complete guide.

## Reporting a Vulnerability

If you discover a security vulnerability in the Aivo SDK, please report it responsibly:

1. **Do not** open a public GitHub issue.
2. Email the maintainers directly (see the GitHub repository contact info).
3. Include a clear description of the vulnerability and, if possible, steps to reproduce it.

We will acknowledge receipt within 48 hours and aim to release a fix within 14 days.

## Supported Versions

| Version | Supported |
|---|---|
| 1.x     | ✅ Yes    |
| < 1.0   | ❌ No     |
