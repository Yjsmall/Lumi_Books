# Context analysis implementation plan

**Goal:** Keep the 上下文 entry, generate contextual meaning and sentence translation with the configured AI, automatically save to one note, and support automatic or manual grammar/vocabulary supplements.

**Architecture:** Store human-readable content in `Note.note` and structured context in an optional `Note.contextJson`. A lifecycle-owned context session loads the original sentence or saved document, requests only missing sections, and serializes successful saves. Reuse translation credentials and transport. Reopen context notes from their cards without duplicating them.

**Tech stack:** Kotlin, Compose, Room, DataStore, OkHttp, coroutine state flows, JUnit/Robolectric.

**Approved design:** Conversation on 2026-10-15; user selected A (automatic initial save and updates).

## Constraints

- Keep the name 上下文 / Context.
- Grammar and vocabulary automatic switches are independent and default false.
- Disabled automatic switches still permit manual requests via 继续解析.
- A manual supplement affects only this note and never changes settings.
- Preserve source text, annotation anchors, tags, identity, and successful existing sections.
- Network failures preserve the original sentence and completed results. Save failures remain visible and retryable.
- Successful initial generation and supplements automatically persist to the same note.
- No real user AI credentials or paid network calls are needed for verification.

## Task 1: Persistent context metadata

- [x] Add nullable `contextJson: String? = null` to Note and NoteEntity, update repository mappings and sync serialization.
- [x] Add an additive Room migration and bump database version; cover migration/backward compatibility with focused tests.
- [x] Provide an identity-safe context save operation that inserts once, updates body and context metadata only, preserves tags/anchors, and fails if an existing note was deleted.

## Task 2: Context automatic options

- [x] Add contextGrammarEnabled/contextVocabularyEnabled Boolean settings, default false, preserving old saved configuration behavior.
- [x] Add an 上下文 configuration section to the AI settings page with two switches and explain automatic versus manual generation.
- [x] Add Chinese and English resources, verify options persist and do not affect credential scope.

## Task 3: AI context workflow and UI

- [x] Write tests for contextual request payloads, structured response validation, auto/manual sections, failed and retried saves, reopening, duplicates, cancellation, and stale responses.
- [x] Implement ContextDocument codec and context-specific AI requests using the existing guarded transport and token store.
- [x] Implement a lifecycle-owned ContextSession that saves successful results automatically and retains completed sections.
- [x] Replace the old preview/save dialog with source/meaning/translation sections, continued analysis choices, statuses, retry, settings, and close actions.
- [x] Wire reader selection and saved note cards to the same document/session, including bookshelf notes.
- [x] Run focused unit/UI tests and assembleDebug; review changes for regressions and resolve material findings.

## Verification commands

`gradlew.bat :app:testDebugUnitTest --tests "com.huangder.lumibooks.translation.*" --tests "com.huangder.lumibooks.ui.reader.*Context*" --tests "com.huangder.lumibooks.ui.settings.TranslationSettingsScreenTest" --tests "com.huangder.lumibooks.data.local.database.*Context*" --console=plain`

`gradlew.bat :app:assembleDebug --console=plain`

## Final validation

- 172 tests across 31 suites passed, with no failures, errors, or skips. Coverage includes translation, context sessions/cards, settings, persistence, migrations, backup/sync, annotation range edits, TXT migration, and note export.
- `:app:assembleDebug` completed successfully; universal and ABI-specific Debug APKs are in `app/build/outputs/apk/debug/`.
- `git diff --check` passed. Phone-size context and settings screenshots were rendered and visually inspected in `app/build/reports/context/` and `app/build/reports/translation/`.
- An independent review and scoped re-review completed. Regression tests cover exact source whitespace, resized annotation reopening, and clearing context metadata from fragments without a note.
- The original sentence is saved after successful extraction even without AI credentials; successful analyses update the same note. Analyzed source text remains independent from later annotation anchor changes.
- Verification uses local service fixtures, without real provider requests. No device is connected, so physical-device validation was not performed.
