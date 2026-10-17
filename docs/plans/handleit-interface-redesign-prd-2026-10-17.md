---
title: HandleIT Interface Redesign PRD
version: 0.1
created: 2026-10-17
owner: Teddy
repository: https://github.com/Mavaebrook/HandleIT-Android
status: future-design-reference
implementation_authorized: false
---

# HandleIT Interface Redesign

## 1. Purpose and reason

Make HandleIT feel like one coherent, modern product. The current concern is that Operit-derived features and settings are scattered across multiple places, and ordinary chat exposes too much technical activity.

Provide a metallic visual style with rounded corners and blue accents, simplify everyday interaction, and make advanced controls available through an explicit Advanced mode.

This PRD captures the conversation requirements for future reference. It does NOT dispatch implementation, change bridge job #26, or authorize a UI rewrite. Proposed defaults and navigation must be validated against a screen inventory and reviewed mockups before implementation.

## 2. Confirmed user requirements

- Redesign the chat interface with a modern metallic appearance, rounded corners and blue accents.
- Consolidate scattered features and settings into a predictable organization.
- Hide most advanced features until Advanced mode is enabled.
- In Simple mode, hide or collapse the tool feed and available thinking details, showing a compact working status instead.
- Preserve a referenceable plan for later development.

## 3. Goals and boundaries

Goals:
- Keep everyday chat focused on the conversation and results.
- Make each setting discoverable in one canonical location.
- Preserve access to technical capabilities without overwhelming the default interface.
- Show truthful progress and make necessary user actions obvious.
- Preserve existing conversations, configuration, permissions and execution behavior.

Not included:
- Changes to memory-learning algorithms, provider routing logic or tool permissions.
- New autonomous capabilities, model subscriptions or backend replacement.
- Branching conversations, multi-pane workspaces or a new task-management engine.
- Rebranding internal packages or changing signing identity.
- Guaranteeing access to model reasoning that a provider does not expose.

## 4. Proposed information architecture

Use three primary destinations, subject to the inventory phase:

### Chat
Conversations, new chat, attachments, model selection, task activity and results. Conversation history is accessible here without becoming another settings location.

### Workspace
Projects, relevant files and available skills. Show approachable operations in Simple mode. Expose skill authoring, revision management and workflow editing only in Advanced mode.

### Settings
A single organized home for configuration:
- Appearance and interface mode
- Models and connections
- Memory and learning
- Tools and permissions
- Notifications and app behavior
- Advanced diagnostics and developer configuration
- About, attribution and version information

Contextual shortcuts may open the same canonical settings page. Do not create separately maintained duplicate controls.

Before design implementation, inventory every existing feature, screen, entry point and deep link. Map each to a destination, mode visibility and migration behavior. Do not remove functionality merely because it is difficult to place.

## 5. Simple and Advanced modes

Proposed default: Simple mode for new installations. For existing users, preserve all configuration and explain where controls moved; exact migration presentation remains a design decision.

Keep an easily discoverable Interface mode control in Settings. Persist the choice using existing preferences infrastructure and define whether it is profile-scoped during code inspection.

Simple mode:
- Chat, attachments, conversation history and straightforward model selection
- Basic memory controls and essential permission visibility
- Available skills and uncomplicated enable/disable controls
- Essential appearance, notification and connection setup
- Compact task activity with optional View details

Advanced mode additionally exposes:
- Provider parameters and detailed routing configuration
- Memory inspection, retrieval tuning and learning configuration
- Skill editing, revisions and teacher-review configuration
- MCP configuration, terminal, developer tools and workflow editing
- Detailed tool activity, diagnostics and available reasoning summaries

Visibility and behavior:
- Switching modes changes presentation, not capability authorization.
- Previously configured tools and connections continue operating under their existing permissions.
- Simple mode must still explain missing setup and allow the minimum configuration needed for ordinary chat.
- Mode changes must not cancel tasks, clear messages, lose drafts or reset settings.
- Approvals, permission requests, blocking failures and requests for clarification remain visible in both modes.
- Hiding advanced controls is not a security boundary.
- Direct links to advanced settings must offer an explicit route to enable Advanced mode rather than an unexplained dead end.

## 6. Chat and activity requirements

### Conversation layout
Use rounded message surfaces, readable spacing, clear speaker distinction and a pill-shaped composer. Preserve streaming text, Markdown, code blocks, attachments, copy actions and existing message interactions.

Keep message backgrounds comparatively flat. Reserve the strongest metallic treatment for the header, composer and controls so long conversations remain readable.

### Simple-mode activity
Replace the default expanded tool/thinking feed with a compact status near the active response. Labels reflect actual observable execution:
- Working when activity is known but a specific operation is unavailable
- Searching, Reading files or Editing files when supported by actual events
- Waiting for approval when user authorization is needed
- Build running with elapsed time when a build is actually running
- Completed, Failed, Interrupted or Cancelled as appropriate

Do not invent percentage completion, a current operation or successful results. Distinguish a submitted external job from an acknowledged running job. Elapsed time is not proof of progress; disconnected or stale external status must be identified honestly.

For parallel activity, aggregate into a concise truthful status and allow inspection of individual operations.

Provide View details to inspect tool activity on demand without forcing a permanent mode change. Display only provider-exposed reasoning summaries/details already available to the app; never fabricate hidden reasoning.

### Advanced-mode activity
Expand technical activity by default, with individual sections still collapsible. Preserve chronological tool order, results and failure details.

### User control and completion
- Stop/cancel remains accessible while work is active in both modes.
- If an external job cannot be cancelled, explain the limitation; do not imply stopping the chat stops that job.
- Required approval controls must never be buried in collapsed details.
- Completion shows the answer, deliverables and a concise summary of meaningful changes.
- Failed or partially completed tasks must not present a generic success state.
- Longer tasks should expose meaningful stage changes rather than only an indefinite spinner.

## 7. Visual direction

Direction: brushed gunmetal, charcoal surfaces, subtle metallic highlights, rounded panels and restrained electric-blue accents. Avoid heavy chrome effects, busy textures and neon glow across all surfaces.

Proposed starting design tokens, not approved final values:
- Background: #101318
- Raised surface: #1B2129
- Elevated metallic edge: #343E4B
- Primary text: #F2F5F8
- Secondary text: #B8C2CE
- Accent: #4CA8FF
- Message/card corner radius: approximately 16-20 dp
- Composer: pill shape with sufficient room for multiline input

Use blue for selected controls, links, primary actions and a restrained activity animation. Error and approval states require distinct readable labels/icons; blue must not carry every meaning.

Prefer lightweight gradients and borders over animated textures or costly real-time blur. Preserve semantic color tokens so contrast and themes can be adjusted consistently. Exact tokens, light-theme handling and component sizing require mockup validation.

## 8. Accessibility and performance

- Validate text and control contrast across gradients, selected states and disabled states.
- Support font scaling without clipped composer controls or inaccessible settings.
- Provide meaningful screen-reader labels and adequate touch targets.
- Do not rely on color or motion alone for status.
- Respect reduced-motion preferences where supported; provide a static working state.
- Avoid screen-reader announcements for every streamed token or tool event.
- Verify keyboard/inset behavior, long conversations, code blocks and attachment previews.
- Preserve sensible scrolling: do not pull users away from older messages they are reading.
- Compare responsiveness and memory use against the current app on Teddy's Android device or a representative device. Set numerical performance budgets after baseline measurement.
- Avoid continuous recomposition of the entire message history for a status animation.

## 9. Technical approach and invariants

Inspect existing Compose screens, theme tokens, navigation routes, preferences, chat renderers and runtime event streams before selecting exact files or APIs. This PRD does not assert that a full UI source audit has occurred.

Separate presentation from execution:
- A reusable theme/component layer supplies metallic styling.
- A persisted interface-mode preference controls progressive disclosure.
- A presentation mapper derives user-facing activity from existing runtime events.
- Navigation points every setting to its canonical screen.
- Existing execution, permissions and conversation state remain authoritative.

Preserve namespace/applicationId com.ai.assistance.operit, existing variants and signing identity, internal service names, installed data and required upstream attribution. Reuse existing skill and tool systems.

Maintain compatibility with installed builds. Do not change schemas, tool semantics or permission grants merely to simplify a screen.

All future builds and Gradle tests use GitHub-hosted Actions, never local/VPS/phone/PRoot APK builds. Do not reverse engineer an installed APK to perform the redesign.

## 10. Acceptance scenarios

- A user can start a chat, choose an available model and attach a file without entering Advanced mode.
- A tool-using response in Simple mode shows a compact truthful status, not an automatically expanded feed.
- View details reveals activity, and returning to chat preserves draft, scroll position and task state.
- Switching to Advanced mode during a response reveals technical detail without restarting execution.
- Approval requests and blocking errors remain actionable in Simple mode.
- A configured MCP connection remains configured and usable after switching back to Simple mode.
- Navigation inventory accounts for every existing setting and deep link with one canonical destination.
- A queued external job is not displayed as running until evidence supports that state.
- Completion, partial failure, interruption and cancellation are visually distinguishable.
- Streaming, Markdown, code blocks, attachments, keyboard behavior and long-chat scrolling remain functional.
- Restarting the app preserves mode choice and existing user data.
- A compatible update installs without requiring removal of the existing app.
- Visual review on a phone confirms the metallic/rounded/blue direction and readable text at enlarged font sizes.

## 11. Future implementation checklist

All items below are backlog, not dispatched work.

- [ ] U01 Inventory current screens, features, settings, deep links and chat components.
- [ ] U02 Map canonical destinations and Simple/Advanced visibility; identify minimum setup paths.
- [ ] U03 Produce and review mockups for idle chat, working, approval, failure, details and settings.
- [ ] U04 Validate visual tokens, typography, touch targets and accessibility.
- [ ] U05 Implement reusable theme/components and persisted mode selection.
- [ ] U06 Consolidate navigation/settings with compatibility for existing entry points.
- [ ] U07 Implement event-driven compact status and expandable details.
- [ ] U08 Verify preserved execution, permissions, rendering and state transitions.
- [ ] U09 Run GitHub-hosted CI and actual phone acceptance; record evidence and remaining limitations.
- [ ] U10 Update README, changelog and this checklist with the delivered scope.

## 12. Open design decisions

Resolve through inventory and mockups before implementation:
- Exact canonical placement of existing features and the primary navigation control
- Existing-user introduction and profile versus app-wide mode persistence
- Light-theme treatment and final visual tokens
- Whether detailed activity opens inline, in a sheet or on a dedicated screen
- Which runtime events can reliably support specific working labels
- Existing accessibility/performance baseline and measurable release thresholds

## Revision history

2026-10-17 — v0.1: captured Teddy's interface requirements, proposed architecture, acceptance scenarios and future implementation checklist. Documentation only; no bridge dispatch.
