alter table journal_entries
    drop constraint ck_journal_entries_system_mood_score_range;

alter table journal_entries
    alter column system_mood_score drop not null;

alter table journal_entries
    add constraint ck_journal_entries_system_mood_score_range
    check (system_mood_score is null or system_mood_score between 0 and 100);
