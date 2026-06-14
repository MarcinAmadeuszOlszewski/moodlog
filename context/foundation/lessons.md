# Lessons Learned

> Append-only register of recurring rules and patterns. Re-read at start by /10x-frame-spring, /10x-research-spring, /10x-plan-spring, /10x-plan-review-spring, /10x-implement-spring, /10x-impl-review-spring.

## Entity boilerplate convention (Lombok vs. hand-written)

**Context**: `src/main/java/com/amadeuszx/moodlog/user/UserAccount.java`,
`src/main/java/com/amadeuszx/moodlog/journal/JournalEntry.java`

**Problem**: AGENTS.md prefers Lombok for constructors and getters, but both
entity classes use hand-written boilerplate. When a new entity is added (or an
existing one extended), it's unclear whether to follow the AGENTS.md rule or
the actual entity-tier precedent — this ambiguity led to a finding in review.

**Rule**: [fill in — e.g. "Entity classes in this project follow a no-Lombok
convention; use hand-written constructors and getters. Update AGENTS.md if the
team decides to standardize."]

**Applies to**: [fill in — e.g. "All @Entity classes under
com.amadeuszx.moodlog"]
