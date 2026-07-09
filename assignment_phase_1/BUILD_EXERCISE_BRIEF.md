# Build Exercise

A two-phase test that mirrors how you'd actually work if you joined. A backend take-home you can put real thought into, then a fast live frontend build on top of what you shipped.

Use Claude Code throughout. We're as interested in how you build as what you build, and we want to see Claude Code in your hands specifically.

---

## Product context (read once, 2 minutes)

We're building a pharmacovigilance (PV) case processing platform. Software that helps pharma companies process adverse event reports called ICSRs (Individual Case Safety Reports). When a patient has a bad reaction to a drug, that case is documented in a regulated format and a human case processor reviews it before submission to regulators.

We use AI to extract structured fields from source documents. A human reviewer then validates those fields, fixes mistakes, and signs off. Cases often get follow-up updates as new information arrives. When that happens, the AI re-extracts and the reviewer needs to see what changed.

You'll build the backend that handles follow-up case merging and queries, the infrastructure to operate it, and then the screen the reviewer uses to validate the merged case.

You do not need any pharmacovigilance knowledge. Treat it as: structured data extracted by AI from a document, which a human reviewer needs to validate, with v1 vs v2 conflict resolution.

---

## Phase 1A: Java backend take-home (~90 min, before the session)

Build a Spring Boot service that powers case review.

### Endpoints

1. `GET /cases/{caseId}` returns the most recent version of a case.
2. `POST /cases/{caseId}/follow-ups` accepts a follow-up payload (same shape as the initial case, with updated or new fields), merges it onto the stored version, and returns the merged case with diff annotations.
3. `POST /queries` creates a reviewer query against a specific field. Payload: `{caseId, fieldPath, question}`.
4. `GET /queries?caseId={id}` lists queries for a case.
5. `GET /health` returns service health for liveness checks.

### Bootstrap

On startup, load `case_v1.json` (attached) into in-memory storage as the initial version of case `PV-2026-0451`. No database needed.

### Merge behavior (the meaningful part)

When a follow-up is POSTed, for each field:

- Same value as stored version: `status: "unchanged"`
- Different value: `status: "overridden"`, include `previous_value`
- New field not in stored version: `status: "new"`
- Field in stored version but not in follow-up: your call. Document your reasoning.

The follow-up also has a top-level `missing_fields` array (fields the AI couldn't extract). Surface this in the merged response.

### Requirements

- Spring Boot, Java 17+
- Sensible package structure (controllers, services, models, your call on the split)
- Input validation with meaningful error responses (400, 404)
- At least 3 unit tests covering merge logic edge cases
- Runnable with `./gradlew bootRun` or `mvn spring-boot:run`
- README with 4 `curl` examples, one per endpoint

### Non-goals

No authentication. No database. No production logging setup. Don't over-engineer.

---

## Phase 1B: Make it operable (~60 min, before the session)

A backend that only runs on your laptop is half a backend. We need the infrastructure to run, exercise, monitor, and back up your service. Treat this as the artifacts you'd hand to an on-call engineer at 2am.

### Required deliverables

1. **`Dockerfile`** for the Spring Boot service. Multi-stage build, reasonable image size, non-root runtime user, pinned base image versions.

2. **`docker-compose.yml`** that brings up the service with any dependencies you've introduced. None are required, but if you've added Redis or a real DB, include it. Include a healthcheck definition.

3. **`ops/run.sh`** with subcommands: `build`, `start`, `stop`, `test`, `logs`, `clean`. Argument parsing with `--help` output. Proper exit codes. Should fail gracefully if Docker isn't running.

4. **`ops/backup.sh`** that fetches all known cases from the running service via `curl` and `jq`, writes them to a timestamped JSON file under `backups/`, logs to stderr with timestamps, and exits non-zero on failure. Should be safe to schedule as a cron job (no interactive prompts, no surprises).

5. **`ops/restore.sh`** that takes a backup file as argument, POSTs each case back to the service, and supports a `--dry-run` flag that shows what would happen without making any changes. Idempotent.

6. **`Makefile`** with `.PHONY` targets covering common operations. Fine if it just delegates to `ops/run.sh` for most things.

7. **README section titled "Operations"** as a runbook. Cover: how to build and deploy, how to verify the service is healthy, how to back up and restore data, how to debug a failed startup, what to check first if requests are failing. Write it as the doc you'd actually want at 2am.

### What we're looking for

- Real shell scripting craft, not Claude Code output you didn't read
- Sensible Docker patterns (small images, layer caching, non-root user, pinned versions)
- Defensive scripts (proper variable quoting, error handling on every external call, idempotency where it matters)
- Operational thinking (what could go wrong, how would I debug it, what would surprise me)

### Non-goals

No Kubernetes manifests. No Terraform. No real cloud deployment. Just clean, defensive local operability.

---

## Phase 2: Live React session (45 min build, 10 min walkthrough)

You'll build the reviewer's screen, calling your own Java backend from Phase 1.

### Setup before the call

- Your Java backend running locally on a known port (use your `ops/run.sh start`)
- A fresh React project (Next.js, Vite, or CRA, your choice). Empty is fine, just have the toolchain ready.
- Same GitHub repo as Phase 1, but a new folder (`/frontend`)
- `case_v2_followup_payload.json` (attached) ready to POST to your backend at the start of the session, so the merged case is queryable

### What to build

**Must-have**

1. Fetch the merged case from `GET /cases/{caseId}` on load.
2. Display extracted fields grouped by section: Patient, Suspect Drug, Adverse Event, Reporter.
3. For each field show: label, value, AI confidence score, source reference (e.g., "p.4 §1").
4. Color-code confidence: low (<0.80), medium (0.80 to 0.90), high (>0.90).
5. Conflict view: when a field has `status: "overridden"`, show both the new value and the previous value side-by-side, with the new value visually primary.
6. "Raise Query" button on conflicting fields. Opens a modal with a textarea and submit. Submit POSTs to your `/queries` endpoint.
7. Case Classification selector at the top: significant, non-significant, null. Editable in UI (no backend persistence needed for this).
8. Sort and filter: sort fields by AI confidence (low first), filter to show only conflicting fields.

**Bonus (only if time allows)**

9. Status pills on each field: New, Overridden, Unchanged.
10. Surface the `missing_fields` array prominently.
11. Empty, loading, and error states for the API calls.
12. Keyboard navigation between fields.

### Constraints

- Brand colors: navy `#0C1A36`, brand blue `#0077B6`, accent teal `#00C2E0`. Use sensibly, no need to overdesign.
- Commit every 10 to 15 minutes so we can see the progression.
- Backend down? Fallback to importing the JSON directly so the frontend work isn't blocked. Flag it as a known issue.

---

## Repo and logistics

- Create one public GitHub repo for everything. Folders: `/backend`, `/ops`, `/frontend`.
- Invite [@gargi-github-handle] as a collaborator.
- Push Phase 1A and 1B before the live session.
- Commit frequently in all phases.
- After the live session: save your Claude Code session log (located at `~/.claude/projects/<encoded-project-path>/<session-id>.jsonl` on macOS/Linux, `%USERPROFILE%\.claude\projects\...` on Windows) and commit the latest one to `/claude-code-session.jsonl` or share via email. We use it as part of the evaluation.

### Time budget

- Phase 1A (Java backend): ~90 min
- Phase 1B (Operability): ~60 min
- Phase 2 (Live React): 45 min + 10 min walkthrough

Total focused work: about 2.5 hours of take-home, plus 55 minutes live. You have 48 hours of clock time before the live session for Phase 1.

---

## What we're looking for

- **Process.** How you scope, scaffold, and iterate with Claude Code.
- **Product judgment.** What you ask before building, what assumptions you make explicit, what you add unprompted.
- **Backend craft.** Package structure, validation, error handling, test coverage, API design.
- **Operability craft.** Docker patterns, shell scripting discipline, defensive automation, runbook quality.
- **Frontend craft.** Component decomposition, state management, accessibility instincts, UX polish under time pressure.
- **Full-stack coherence.** Does your API design make the frontend pleasant or painful to build? Does your runbook match how the service actually behaves?
- **Communication.** Narrate while you build (live session). Write the runbook for a stranger (take-home).

---

## Files attached

- `case_v1.json`: bootstrap data for your Java backend
- `case_v2_followup_payload.json`: the follow-up you'll POST at the start of the live session

## Questions?

Email anything ahead of time. During the live session, ask freely.

Good luck. Have fun with it.
