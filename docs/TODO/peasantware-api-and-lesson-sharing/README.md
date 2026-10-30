# PeasantWare API gateway and opt-in lesson sharing

**Status: BACKLOG (deferred 2026-10-30).** Recorded so the current work does not close doors on it. Nothing here is scheduled.

## Intent

Let people who use the PeasantWare API (`https://api.peasantware.com`) opt in to sharing their learned lessons, so that:

1. common failures one install has already learned (a tool's required config, an API that errors on a given input) can be surfaced to others; and
2. pooled (evidence, lesson) pairs become training and distillation data for a future small local model. This is the strongest reason to do it; lessons about one person's own projects transfer poorly, the training data transfers well.

## Start conditions

Do not start until both hold:

- The single-user loop is proven: on the owner's device, retrieved lessons measurably improve results (right lesson retrieved, past mistake not repeated). Depends on review evidence capture (#24), retrieval threshold and past-mistake rendering (#25), and request logging (step 1 below).
- Someone other than the owner uses the app.

## Current state (2026-10-30)

- `api.peasantware.com` is served by Nginx on the VPS. Only `/v1/embeddings` and `/v1/models` are exposed, proxied to llama.cpp (Qwen3-Embedding-0.6B) on `127.0.0.1:11435`. Every other path returns 404.
- One shared API key, checked by llama.cpp. No per-user identity.
- Embedding request text is not stored: llama.cpp does not log request bodies and Nginx logs only the URL. Keep it that way for embeddings regardless of lesson sharing.
- The data shape to share already exists: `TaskEpisode` lessons and, after #24, the exact Reflector input stored on each lesson (`TaskEpisodeEntity.reflectorInputJson`).

## Steps

### 1. API gateway with per-user keys (worth doing for the owner alone)

A small service (FastAPI or similar; LiteLLM is too heavy for the 1.8 GB VPS) becomes the only thing behind `api.peasantware.com`:

- Per-install API keys, issued and revocable individually. The key is the anonymous identity for deletion, abuse control and rate limits.
- Routes: `/v1/embeddings` (to the local embedding server), `/v1/chat/completions` (DeepSeek now, a local model later), later `/v1/lessons`.
- Owner-only request logging: exact assembled prompt and raw model reply per request, including preprocessing calls. This is also the prompt-assembly debugger asked for on 2026-10-30, without building one into the app. Off for every key except the owner's.
- Provider keys stay on the server, not on phones.

### 2. Client opt-in (HandleIT-Android)

- A "Share lessons to improve PeasantWare" setting, **shown only when the embedding endpoint is `api.peasantware.com`, and off by default**. Using the endpoint is not consent.
- Two consent levels: *lessons only* (the structured lesson fields) and *lessons + evidence* (adds the Reflector input, which contains the user's own messages). Evidence needs its own explicit yes.
- On-device scrubbing before upload: file paths, emails, keys and tokens, phone numbers. A visible queue of what will be sent.
- Turning the setting off stops uploads. "Delete my shared data" calls a deletion endpoint keyed by the install's API key.

### 3. Server intake (`/v1/lessons`)

- New lessons land in quarantine; nothing reaches other users directly.
- Promotion only when several independent keys report the same lesson. The existing `evidence_keys` normalization and deduplication is the starting point for "same lesson".
- Per-key rate limits; revoking one key removes its uploads.
- Deletion by key, honored completely, including from training exports.

### 4. Community lessons on the client

- Promoted lessons are pulled as a separate pool, rendered with lower trust than personal lessons, and never override a personal lesson.
- Same retrieval thresholds as personal lessons (#25).

### 5. Training export

- Only from installs that chose *lessons + evidence*.
- Re-scrubbed server-side; deleted keys excluded.

## Risks to design around

- **Poisoned lessons.** Shared lessons are injected into other people's prompts, so anyone could upload text built to manipulate the model. Mitigations: quarantine, independent confirmation before promotion, lower-trust pool, per-key revocation.
- **Moderation load** for a solo operator.
- **Capacity.** One 1.8 GB VPS serving embeddings for many users will need limits or more hardware.
- **Privacy and legal.** A privacy policy covering embeddings (text processed, not stored) and lesson sharing (what is stored, how to delete). The existing SMS opt-in pages on handleit.online are the same kind of obligation.

## Open questions

- Key issuance: self-serve sign-up, or keys handed out manually?
- Does the gateway replace the Nginx-only route immediately, or run beside it until keys exist?
- What counts as "the same lesson" across installs beyond normalized evidence keys: embedding similarity of task and mistake?
