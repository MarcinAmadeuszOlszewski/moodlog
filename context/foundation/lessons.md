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

## Prefer DOMContentLoaded over IIFE for externalized browser scripts

**Context**: `src/main/resources/static/js/register.js` — plan said IIFE for timezone detection; implementation used DOMContentLoaded

**Problem**: A plan specified an IIFE to run timezone detection immediately. When the script was later moved outside the form and loaded with `defer`, the IIFE approach would have required careful DOM-ordering awareness. DOMContentLoaded is safer because it guarantees the DOM is ready regardless of where the script tag is placed.

**Rule**: When extracting inline browser scripts to external files, wrap the logic in a `DOMContentLoaded` listener rather than an IIFE. This decouples the script from its position in the HTML and makes it safe to load with `defer`.

**Applies to**: All externalized browser scripts under `src/main/resources/static/js/`

## Use const/let over var in browser JS files

**Context**: `src/main/resources/static/js/register.js:2` — uses `var` while `journal-trends.js` and `site.js` use `const`/function-scoped declarations

**Problem**: Mixing `var` and `const` across JS files creates inconsistency. `var` has function scope and hoisting which can cause subtle bugs; `const`/`let` have block scope and clearer intent.

**Rule**: Use `const` by default, `let` only if the variable is reassigned later in the same scope. Never use `var` in new JS files under this project.

**Applies to**: All files under `src/main/resources/static/js/`
