# Chat replies empty on device: Android-unsupported regex flag (2026-10-30)

## Symptom

On the device every chat turn ended with an empty assistant reply. The app log showed `sendMessage调用开始`, then about 0.9 s later `Stream content is empty. Finalizing conversation state.`, with 0 input and 0 output tokens, no `sendMessage本地耗时: prepareConversationHistory` line, no `PromptPreprocessor` line and no error.

## Root cause

`TaskPreflightFormatter.WHITESPACE_RUN` was compiled with `Pattern.UNICODE_CHARACTER_CLASS` (added 2026-10-21, D06). Android does not support that flag; `Pattern.compile` throws `IllegalArgumentException`. The regex is a property of the `TaskPreflightFormatter` object, so the first use on a device threw `ExceptionInInitializerError` (later uses: `NoClassDefFoundError`). Both are `Error`s, not `Exception`s:

- `TaskPreflightProvider.buildPreflightBlockFrom` catches `Exception` only, so the preflight failure was not logged;
- `EnhancedAIService.sendMessage` catches `CancellationException` and `Exception` only, so `hadFatalError` stayed false and its `finally` ran stream completion with empty content.

The turn therefore died inside `prepareConversationHistory`, before the provider request and before prompt preprocessing. Ordinary chats reach the formatter since episode recall was enabled without a workspace (surgical repair, 2026-10-28), which matches when the failure started. JVM tests could not see it: the desktop JVM supports the flag.

The embedding server log is consistent: it received the query embedding for each turn (retrieval runs before formatting), and no chat request followed.

## Change

- `WHITESPACE_RUN` is now `Regex("[\\s\\p{Z}]+")`: ASCII whitespace plus every Unicode space, line and paragraph separator, identical on Android and the JVM. Existing D06 tests for ` `, ` `, ` ` keep passing.
- `AndroidRegexCompatibilityTest` fails the build if `UNICODE_CHARACTER_CLASS` or `CANON_EQ` appear in main sources, and checks Unicode whitespace collapsing.

## Not in this change

- `Error`s on the chat path are still not logged. A follow-up should log them at the `sendMessage` boundary (log and rethrow, no swallowing) so the next failure of this kind is visible.

## Validation

- [ ] Hosted CI: Android JVM tests.
- [ ] Device: a plain "hello" in a chat gets a reply; the log shows `sendMessage本地耗时: prepareConversationHistory`.
