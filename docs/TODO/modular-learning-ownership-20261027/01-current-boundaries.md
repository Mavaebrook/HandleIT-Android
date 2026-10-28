# Current boundaries and removal targets

Source snapshot: main/build 85, 17089b41b4f192968deeb0d36163333c53e222aa. Paths below refer to this snapshot. This is a targeted call-path inventory, not a claim that every historical writer has been enumerated.

## Completion and memory writers

`app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt`, finalizeAssistantResponse, lines 2070–2125:

- The enableMemoryAutoUpdate / non-subtask / nonblank-response condition surrounds both queues.
- MemoryAutoSaveCandidateRepository.enqueue is followed by ReviewSignalDeriver and ReviewTriggerEnqueuer.maybeEnqueue.
- The review receives content.take(2000), empty contextSummary and an inferred outcome.
- This is shared orchestration, not duplicate writes to the same table. Content/cost overlap is possible and requires task-level evidence to quantify.

`api/chat/library/MemoryAutoSaveScheduler.kt` under the same package processes candidates through MemoryLibrary.saveMemoryNow using the MEMORY function model. It requires at least five total candidates before extraction. General Memory storage must be separated from the policy that decides to extract it.

`api/chat/library/ReviewScheduler.kt` drains PendingReviewEntity, calls ReflectorCurator.reflect and persists/indexes TaskEpisode. It does not call the Curator or publish SKILL.md. Its reflector input concatenates taskSummary and contextSummary, not the full execution trajectory.

`EnhancedAIService.saveConversationToMemoryAsync` is an additional explicit/manual entry point into MemoryLibrary. Inventory tool-directed writes, editing, reinforcement, retirement, restore and rebuild operations before switching ownership; changing only the background scheduler leaves bypasses.

Removal target after cutover: independent Android scheduling, automatic extraction, salience/retirement policy and direct orchestration from response finalization. Retain explicit user intent, storage read access and migration compatibility through the owner interface.

## Retrieval and assembly

`api/chat/enhance/ConversationService.kt`, lines 559–680:

- System prompt composition occurs when the supplied history lacks a SYSTEM turn.
- TaskPreflightProvider is invoked only for a nonblank workspacePath.
- The resulting text is passed in hook metadata as architectPreflightBlock.
- Role-card tool access supplies allowed package, skill and MCP names.
- user.md is separately appended to the system prompt when its preference allows.

`core/chat/hooks/ArchitectRulesHook.kt` appends scoped requirements and optional episode context. The retrieved episode block is still placed in SYSTEM; fencing does not create a separate trust level.

`core/config/SystemPromptConfig.kt` lists packages/MCP/skills according to tool exposure, tool settings, role-card allowlists and registered MCP servers. Memory cannot compensate for a tool absent from this catalogue.

Removal target in bridge-managed conversations: Android's independent prompt assembly, learned-memory injection and skill-selection logic. Preserve user persona/settings as structured inputs with declared precedence. Keep conversation-history compression as an explicit controller-owned context operation; it is not a second long-term learning writer.

## Skills and storage

`core/tools/skill/SkillManager.kt` discovers installed SKILL.md packages. `data/repository/SkillRevisionRepository.kt` persists structured revisions separately. `ReflectorCurator.curate` returns draft revisions, while the inspected review scheduler only invokes reflect.

Do not claim automatic skill improvement until proposal, acceptance, publication and retrieval are connected and observed. Preserve existing general memories and task episodes as distinct record types behind the knowledge-service interface. A common API does not require destructive flattening of their schemas.

## Evidence needed before deletion

- Enumerate direct writer call sites under the narrow memory/review/skill packages and their actual production callers.
- Classify each as user-directed edit, automatic extraction, reinforcement, policy mutation, indexing, or pure storage.
- Record how every entry point routes to the selected owner after cutover.
- Verify old queues with pending work are accounted for. Retained rows must not be silently drained by both systems.
- Do not copy the entire personal database or credentials into fixtures; use sanitized synthetic equivalents with matching structure.
