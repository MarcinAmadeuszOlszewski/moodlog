create table journal_entries (
	id uuid primary key,
	user_account_id uuid not null,
	content varchar(2000) not null,
	system_mood_tag varchar(32) not null,
	system_mood_score integer not null,
	override_mood_tag varchar(32),
	override_mood_score integer,
	classifier_provider varchar(100) not null,
	classifier_model varchar(100) not null,
	classified_at timestamp with time zone not null,
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	constraint fk_journal_entries_user_account foreign key (user_account_id) references user_accounts(id),
	constraint ck_journal_entries_system_mood_score_range check (system_mood_score between 0 and 100),
	constraint ck_journal_entries_override_mood_score_range check (
		override_mood_score is null or override_mood_score between 0 and 100
	)
);

create index idx_journal_entries_user_account_created_at on journal_entries (user_account_id, created_at);
