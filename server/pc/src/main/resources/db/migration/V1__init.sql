-- MuseClass: початкова схема.
-- Ідентифікатори UUID, щоб не можна було перебрати чужі партитури за номером.

create table users (
    id            uuid primary key default gen_random_uuid(),
    email         text not null,
    password_hash text not null,
    display_name  text not null check (length(display_name) between 1 and 60),
    created_at    timestamptz not null default now()
);
create unique index users_email_uq on users (lower(email));

-- Інструмент — м'який сигнал ранжування, не фільтр.
create table user_instruments (
    user_id    uuid not null references users (id) on delete cascade,
    instrument text not null check (instrument in (
        'piano', 'guitar', 'voice', 'violin', 'trumpet', 'flute',
        'bass_guitar', 'drums', 'saxophone', 'bandura')),
    primary key (user_id, instrument)
);

-- Клас. Викладач — окремо від учнів: один користувач може вести свій клас
-- і водночас бути учнем у чужому.
create table classes (
    id         uuid primary key default gen_random_uuid(),
    code       text not null unique check (code ~ '^[A-Z]{3}-[0-9][A-Z]$'),
    name       text not null check (length(name) between 1 and 80),
    teacher_id uuid not null references users (id) on delete cascade,
    created_at timestamptz not null default now()
);
create index classes_teacher_idx on classes (teacher_id);

create table class_members (
    class_id  uuid not null references classes (id) on delete cascade,
    user_id   uuid not null references users (id) on delete cascade,
    joined_at timestamptz not null default now(),
    primary key (class_id, user_id)
);
create index class_members_user_idx on class_members (user_id);

-- Партитура. Зберігаємо оригінал MusicXML як є (.musicxml або .mxl):
-- це кілобайти, у bytea їм нормально.
-- rights: публічний каталог — тільки public domain і народне;
-- кавери живуть як приватні або класні аранжування.
create table scores (
    id           uuid primary key default gen_random_uuid(),
    owner_id     uuid not null references users (id) on delete cascade,
    title        text not null check (length(title) between 1 and 200),
    composer     text,
    arranger     text,
    kind         text not null default 'other'
                 check (kind in ('classical', 'folk', 'cover', 'technique', 'other')),
    rights       text not null default 'unknown'
                 check (rights in ('public_domain', 'folk', 'arrangement', 'original', 'unknown')),
    visibility   text not null default 'private'
                 check (visibility in ('public', 'class', 'private')),
    file_format  text not null check (file_format in ('musicxml', 'mxl')),
    content      bytea not null,
    content_sha256 text not null,
    size_bytes   integer not null,
    measures     integer not null,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    constraint scores_public_rights_chk
        check (visibility <> 'public' or rights in ('public_domain', 'folk'))
);
create index scores_owner_idx on scores (owner_id);
create index scores_public_idx on scores (visibility) where visibility = 'public';

-- Партії витягуються з MusicXML при завантаженні: по них ранжуємо каталог.
create table score_parts (
    score_id     uuid not null references scores (id) on delete cascade,
    position     integer not null,
    part_id      text not null,
    name         text not null,
    instrument   text,           -- код з user_instruments або null, якщо не впізнали
    midi_program integer,
    primary key (score_id, position)
);
create index score_parts_instr_idx on score_parts (instrument, score_id);

-- Видача класу — гуртом на всіх учнів.
create table class_scores (
    class_id    uuid not null references classes (id) on delete cascade,
    score_id    uuid not null references scores (id) on delete cascade,
    assigned_by uuid not null references users (id) on delete cascade,
    assigned_at timestamptz not null default now(),
    primary key (class_id, score_id)
);
create index class_scores_score_idx on class_scores (score_id);

create table saved_scores (
    user_id  uuid not null references users (id) on delete cascade,
    score_id uuid not null references scores (id) on delete cascade,
    saved_at timestamptz not null default now(),
    primary key (user_id, score_id)
);

-- Єдине джерело правди про доступ на читання.
-- Власник бачить завжди; публічну — всі; класну — викладач і учні класів,
-- яким її видали; приватну — тільки власник.
create function score_readable(p_user uuid, p_score uuid) returns boolean
language sql stable as $$
    select exists (
        select 1 from scores s
        where s.id = p_score
          and (   s.owner_id = p_user
               or s.visibility = 'public'
               or (s.visibility = 'class' and exists (
                      select 1
                      from class_scores cs
                      join classes c on c.id = cs.class_id
                      where cs.score_id = s.id
                        and (   c.teacher_id = p_user
                             or exists (select 1 from class_members m
                                        where m.class_id = c.id and m.user_id = p_user)))))
    )
$$;
