---
starter_id: spring
package_manager: maven
project_name: mood-log
hints:
  language_family: java
  team_size: solo
  deployment_target: fly
  ci_provider: github-actions
  ci_default_flow: auto-deploy-on-merge
  bootstrapper_confidence: verified
  path_taken: standard
  quality_override: false
  self_check_answers: null
  has_auth: true
  has_payments: false
  has_realtime: false
  has_ai: false
  has_background_jobs: false
---

## Why this stack

MoodLog is a small, after-hours web app with private user accounts and a four-week MVP, so the safest path is the registry’s verified Java default. Spring Boot gives a typed, convention-based foundation with mature web, data, and security support in one starter, which keeps solo setup work low and avoids assembling core pieces by hand. Fly is the starter’s default deployment target, and GitHub Actions with auto-deploy on merge matches a lightweight solo delivery flow. The PRD forces auth but not payments, realtime, or background jobs, so the standard Spring path stays aligned with the product without adding stack risk.
