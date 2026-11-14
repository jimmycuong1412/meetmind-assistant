# CLAUDE.md — MeetMind Assistant

On-device (offline) Android meeting assistant: Jetpack Compose UI, Sherpa-ONNX STT,
llama.cpp LLM. **Before any non-trivial task, read [`docs/AI_AGENT_HANDOFF.md`](docs/AI_AGENT_HANDOFF.md)**:
it has the module map, conventions, gotchas and feature notes. Don't duplicate it here.

## Workflow: Superpowers

This repo uses the [Superpowers](https://github.com/obra/superpowers) plugin, enabled for
everyone through `.claude/settings.json` (`superpowers@claude-plugins-official`). Use its
skills instead of improvising:

1. **Brainstorm** (`superpowers:brainstorming`): talk through the idea before any feature or
   behaviour change. Output: a design spec in `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md`.
2. **Plan** (`superpowers:writing-plans`): turn the spec into small, verifiable steps.
   Output: `docs/superpowers/plans/YYYY-MM-DD-<topic>.md`.
3. **Execute** (`superpowers:subagent-driven-development` or `superpowers:executing-plans`):
   one task at a time, test-first where tests exist (`superpowers:test-driven-development`).
4. **Debug** (`superpowers:systematic-debugging`): find the root cause before fixing anything.
5. **Verify** (`superpowers:verification-before-completion`): run the gates below and show
   their output before saying something is done.
6. **Finish** (`superpowers:requesting-code-review`, then `superpowers:finishing-a-development-branch`).

Skip steps 1–2 for small fixes such as typos, one-line bugs or copy changes.

## Verification gates

- Kotlin or UI change: `./gradlew :app:compileDebugKotlin` (use `gradlew.bat` on Windows)
- Domain or parsing logic: `./gradlew :domain:test` (plain Kotlin module, so the task is `test`)
- Before saying the build is green: `./gradlew :app:assembleDebug`
- Agents can't run an emulator, so a person has to check visual or behaviour changes on a
  device. Never say UX was "verified" when only the build ran.

## Ground rules

- Clean Architecture: `app → presentation → domain ← data`. Only interfaces cross layers.
- User-facing text goes in `strings.xml`, plus the 24 locale variants.
- Use the single sources of truth: `SupportedLanguages.ALL`, `DefaultModelConfig`,
  `AppIcons`, and the theme tokens in `ui/ui/theme/`.
- Conventional Commits (`feat(ui): …`, `fix(domain): …`). The default branch is `develop`.
- Never commit `google-services.json`, keystores or `.env` files.
