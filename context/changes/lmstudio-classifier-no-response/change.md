---
change_id: lmstudio-classifier-no-response
title: Fix OpenAiMoodClassifier not getting a response from LMStudio
status: impl_reviewed
created: 2026-06-12
updated: 2026-06-15
archived_at: null
---

## Notes

After login, filling the journal form and clicking Save: the prompt is logged (OpenAiMoodClassifier.java:47), LMStudio receives the request, but no response is ever generated in LMStudio and the app silently falls back to MoodTag.UNKNOWN. The leading hypothesis is that response_format: {"type": "json_object"} sent unconditionally to LMStudio is incompatible with the local model server, causing LMStudio to reject the request before inference starts.
