# Lessons Learned

> Append-only register of recurring rules and patterns. Re-read at start by /10x-frame-spring, /10x-research-spring, /10x-plan-spring, /10x-plan-review-spring, /10x-implement-spring, /10x-impl-review-spring.

## Entity boilerplate convention (Lombok vs. hand-written)

**Context**: `src/main/java/com/amadeuszx/moodlog/user/UserAccount.java`,
`src/main/java/com/amadeuszx/moodlog/journal/JournalEntry.java`

**Problem**: AGENTS.md prefers Lombok for constructors and getters, but both
entity classes use hand-written boilerplate. When a new entity is added (or an
existing one extended), it's unclear whether to follow the AGENTS.md rule or
the actual entity-tier precedent — this ambiguity led to a finding in review.

**Rule**: Use Lombok on `@Entity` classes with this minimal pattern:
- `@Getter` — replaces hand-written getters
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` — satisfies JPA/Hibernate
  requirement for a no-args constructor without exposing it publicly
- Explicit domain constructor only for fields required by business logic
- Avoid `@AllArgsConstructor` on entities — it exposes id and persistence-managed
  fields, making the model easy to misuse

```java
@Entity
@Table(name = "user_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount {
}
```

**Applies to**: All `@Entity` classes under `com.amadeuszx.moodlog`
