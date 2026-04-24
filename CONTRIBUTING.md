# Contributing

Thanks for helping improve Performance Doctor.

## Development Setup

1. Install JDK 21.
2. Clone the repository.
3. Run `gradle build`.

## Code Style

- Keep the mod server-side unless a feature explicitly belongs in a future client companion.
- Keep version-specific Minecraft API calls isolated.
- Prefer readable diagnostics over noisy raw data.
- Avoid collecting private server/player data in reports unless it is clearly documented and configurable.

## Pull Requests

Include:

- What changed
- Which Minecraft version was tested
- Which commands were tested
- Any report format changes
