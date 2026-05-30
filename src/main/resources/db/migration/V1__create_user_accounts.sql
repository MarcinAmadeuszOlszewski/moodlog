create table user_accounts (
	id uuid primary key,
	email varchar(320) not null,
	password_hash varchar(255) not null,
	active boolean not null,
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	constraint uk_user_accounts_email unique (email)
);
