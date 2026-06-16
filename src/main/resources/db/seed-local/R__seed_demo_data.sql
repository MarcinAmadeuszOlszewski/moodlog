-- Repeatable migration, local profile only (see spring.flyway.locations in application-local.properties).
-- Idempotent via WHERE NOT EXISTS so it is safe to rerun against the same persisted H2 file.

insert into user_accounts (id, email, password_hash, active, created_at, updated_at, timezone)
select 'd16b783c-b21e-43d8-be87-adad8ada9ae4', 'DemoDay@hotmail.com',
       '$2a$10$bvrntj2VgDunEm8LacsRYeeKMPvNsMooOG/Y0wr4ICPhFxrgP9J9i',
       true, '2026-05-25 08:00:00+02', '2026-05-25 08:00:00+02', 'Europe/Warsaw'
where not exists (select 1 from user_accounts where email = 'DemoDay@hotmail.com');

insert into journal_entries (
    id, user_account_id, content, system_mood_tag, system_mood_score,
    override_mood_tag, override_mood_score, classifier_provider, classifier_model,
    classified_at, created_at, updated_at
)
select v.id, 'd16b783c-b21e-43d8-be87-adad8ada9ae4', v.content, v.system_mood_tag, v.system_mood_score,
       v.override_mood_tag, null, v.classifier_provider, v.classifier_model,
       v.classified_at, v.created_at, v.updated_at
from (values
    ('a0000000-0000-0000-0000-000000000001'::uuid,
     'Obudziłem się wcześniej niż zwykle i od razu poczułem przypływ energii. Słońce świeciło przez okno, zjadłem spokojne śniadanie i wyszedłem na krótki spacer przed pracą.',
     'JOY', 86, cast(null as varchar(32)), 'stub', 'stub-v1',
     timestamp '2026-05-25 09:10:05+02', timestamp '2026-05-25 09:10:00+02', timestamp '2026-05-25 09:10:00+02'),

    ('a0000000-0000-0000-0000-000000000002'::uuid,
     'Zwykły dzień, nic szczególnego się nie wydarzyło. Praca, obiad, trochę telewizji wieczorem.',
     'NEUTRAL', 52, null, 'stub', 'stub-v1',
     timestamp '2026-05-26 21:40:04+02', timestamp '2026-05-26 21:40:00+02', timestamp '2026-05-26 21:40:00+02'),

    ('a0000000-0000-0000-0000-000000000003'::uuid,
     'Jutro ważna rozmowa z szefem o podwyżce, nie mogę przestać o tym myśleć. Trudno mi zasnąć.',
     'ANXIETY', 64, null, 'stub', 'stub-v1',
     timestamp '2026-05-27 22:05:03+02', timestamp '2026-05-27 22:05:00+02', timestamp '2026-05-27 22:05:00+02'),

    ('a0000000-0000-0000-0000-000000000004'::uuid,
     'Poranna sesja jogi naprawdę pomogła mi się wyciszyć przed długim dniem.',
     'CALM', 70, 'JOY', 'stub', 'stub-v1',
     timestamp '2026-05-28 08:30:04+02', timestamp '2026-05-28 08:30:00+02', timestamp '2026-05-28 18:00:00+02'),

    ('a0000000-0000-0000-0000-000000000005'::uuid,
     'Rozmowa z mamą przypomniała mi, jak dawno nie widziałem rodziny. Czuję lekki smutek i tęsknotę.',
     'SADNESS', 38, null, 'stub', 'stub-v1',
     timestamp '2026-05-29 19:15:02+02', timestamp '2026-05-29 19:15:00+02', timestamp '2026-05-29 19:15:00+02'),

    ('a0000000-0000-0000-0000-000000000006'::uuid,
     'Mam trzy deadliny w tym tygodniu i nie wiem, od czego zacząć. Czuję się przytłoczony liczbą zadań.',
     'OVERWHELMED', 89, null, 'stub', 'stub-v1',
     timestamp '2026-05-30 07:50:03+02', timestamp '2026-05-30 07:50:00+02', timestamp '2026-05-30 07:50:00+02'),

    ('a0000000-0000-0000-0000-000000000007'::uuid,
     'Pracowałem, zjadłem lunch, nic więcej do zapisania.',
     'NEUTRAL', 50, null, 'stub', 'stub-v1',
     timestamp '2026-06-01 12:00:02+02', timestamp '2026-06-01 12:00:00+02', timestamp '2026-06-01 12:00:00+02'),

    ('a0000000-0000-0000-0000-000000000008'::uuid,
     'Klient znowu zmienił wymagania w ostatniej chwili, mimo że właśnie skończyliśmy pracę. Jestem wściekły na ten chaos w komunikacji.',
     'ANGER', 81, null, 'stub', 'stub-v1',
     timestamp '2026-06-02 20:20:04+02', timestamp '2026-06-02 20:20:00+02', timestamp '2026-06-02 20:20:00+02'),

    ('a0000000-0000-0000-0000-000000000009'::uuid,
     'Dzisiaj był naprawdę wspaniały dzień. Spotkałem się z przyjaciółmi, których nie widziałem od miesięcy, i śmialiśmy się jak za starych czasów. Zjedliśmy razem kolację w nowej restauracji, którą długo chcieliśmy wypróbować. Wieczorem wróciłem do domu pełen energii i wdzięczności za to, że mam takich ludzi w swoim życiu. Czuję, że to był jeden z tych dni, które zapamiętam na długo.',
     'JOY', 90, null, 'stub', 'stub-v1',
     timestamp '2026-06-03 21:00:06+02', timestamp '2026-06-03 21:00:00+02', timestamp '2026-06-03 21:00:00+02'),

    ('a0000000-0000-0000-0000-000000000010'::uuid,
     'Wstałem bardzo wcześnie, zrobiłem herbatę i czytałem książkę w ciszy, zanim dom się obudził.',
     'CALM', 68, null, 'stub', 'stub-v1',
     timestamp '2026-06-04 06:45:03+02', timestamp '2026-06-04 06:45:00+02', timestamp '2026-06-04 06:45:00+02'),

    ('a0000000-0000-0000-0000-000000000011'::uuid,
     'Dostałem awans, o który zabiegałem od miesięcy, ale teraz boję się, że nie dam sobie rady z nowymi obowiązkami. Radość miesza się z niepokojem.',
     'ANXIETY', 60, null, 'stub', 'stub-v1',
     timestamp '2026-06-05 23:10:05+02', timestamp '2026-06-05 23:10:00+02', timestamp '2026-06-05 23:10:00+02'),

    ('a0000000-0000-0000-0000-000000000012'::uuid,
     'Cały dzień padał deszcz, zostałem w domu i nadrobiłem zaległości w serialach.',
     'NEUTRAL', 48, null, 'stub', 'stub-v1',
     timestamp '2026-06-06 13:25:02+02', timestamp '2026-06-06 13:25:00+02', timestamp '2026-06-06 13:25:00+02'),

    ('a0000000-0000-0000-0000-000000000013'::uuid,
     'Nie wiem nawet, jak to opisać. Dzień był jakiś, po prostu był.',
     'UNKNOWN', cast(null as integer), null, 'unknown', 'unknown',
     timestamp '2026-06-07 18:00:00+02', timestamp '2026-06-07 18:00:00+02', timestamp '2026-06-07 18:00:00+02'),

    ('a0000000-0000-0000-0000-000000000014'::uuid,
     'Straciłem mecz, na który przygotowywałem się tygodniami. Jestem rozczarowany sobą.',
     'SADNESS', 35, null, 'stub', 'stub-v1',
     timestamp '2026-06-08 18:40:03+02', timestamp '2026-06-08 18:40:00+02', timestamp '2026-06-08 18:40:00+02'),

    ('a0000000-0000-0000-0000-000000000015'::uuid,
     'Mała wygrana: udało mi się dokończyć projekt przed terminem. Poczułem ogromną satysfakcję.',
     'JOY', 82, null, 'stub', 'stub-v1',
     timestamp '2026-06-09 09:05:04+02', timestamp '2026-06-09 09:05:00+02', timestamp '2026-06-09 09:05:00+02'),

    ('a0000000-0000-0000-0000-000000000016'::uuid,
     'Wszystko się na mnie zwaliło naraz: pranie, rachunki, praca. Nie wiem, w co najpierw się rzucić.',
     'OVERWHELMED', 85, 'ANXIETY', 'stub', 'stub-v1',
     timestamp '2026-06-10 22:50:05+02', timestamp '2026-06-10 22:50:00+02', timestamp '2026-06-11 08:00:00+02'),

    ('a0000000-0000-0000-0000-000000000017'::uuid,
     'Spokojny wieczór z herbatą i muzyką jazzową w tle. Nareszcie czuję, że mogę odetchnąć.',
     'CALM', 75, null, 'stub', 'stub-v1',
     timestamp '2026-06-11 20:15:03+02', timestamp '2026-06-11 20:15:00+02', timestamp '2026-06-11 20:15:00+02'),

    ('a0000000-0000-0000-0000-000000000018'::uuid,
     'Stałem w kolejce do urzędu ponad godzinę, żeby usłyszeć, że brakuje jednego dokumentu. Wściekam się na ten bezsens.',
     'ANGER', 77, null, 'stub', 'stub-v1',
     timestamp '2026-06-12 11:30:04+02', timestamp '2026-06-12 11:30:00+02', timestamp '2026-06-12 11:30:00+02'),

    ('a0000000-0000-0000-0000-000000000019'::uuid,
     'Standardowy poniedziałek. Nic wybitnego, nic złego.',
     'NEUTRAL', 53, null, 'stub', 'stub-v1',
     timestamp '2026-06-13 07:15:02+02', timestamp '2026-06-13 07:15:00+02', timestamp '2026-06-13 07:15:00+02'),

    ('a0000000-0000-0000-0000-000000000020'::uuid,
     'Wieczorem zrobiło mi się smutno, kiedy przypomniałem sobie stare zdjęcia z wakacji.',
     'SADNESS', 40, null, 'stub', 'stub-v1',
     timestamp '2026-06-13 21:45:03+02', timestamp '2026-06-13 21:45:00+02', timestamp '2026-06-13 21:45:00+02'),

    ('a0000000-0000-0000-0000-000000000021'::uuid,
     'Najlepsza kawa w historii. Świetny początek dnia!',
     'JOY', 88, null, 'stub', 'stub-v1',
     timestamp '2026-06-14 10:00:02+02', timestamp '2026-06-14 10:00:00+02', timestamp '2026-06-14 10:00:00+02'),

    ('a0000000-0000-0000-0000-000000000022'::uuid,
     'Czekam na wyniki badań i nie mogę myśleć o niczym innym. Stres narasta z każdą godziną.',
     'ANXIETY', 66, null, 'stub', 'stub-v1',
     timestamp '2026-06-15 19:30:04+02', timestamp '2026-06-15 19:30:00+02', timestamp '2026-06-15 19:30:00+02'),

    ('a0000000-0000-0000-0000-000000000023'::uuid,
     'Spokojny poranny spacer z psem zanim zacznie się zgiełk dnia.',
     'CALM', 72, null, 'stub', 'stub-v1',
     timestamp '2026-06-16 08:00:03+02', timestamp '2026-06-16 08:00:00+02', timestamp '2026-06-16 08:00:00+02')
) as v (id, content, system_mood_tag, system_mood_score, override_mood_tag, classifier_provider, classifier_model, classified_at, created_at, updated_at)
where not exists (select 1 from journal_entries where id = v.id);
