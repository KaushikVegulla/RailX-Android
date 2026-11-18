-- RailX — Supabase schema
-- Run this once in the Supabase dashboard: SQL Editor -> New query -> paste -> Run.
-- Safe to re-run: every statement is idempotent.
--
-- Security model: Row Level Security is ON for every table and each policy
-- restricts rows to the signed-in owner (auth.uid()). The publishable key in
-- the Android app is safe to ship ONLY because these policies exist — never
-- disable RLS, and never put the secret / service_role key in the app.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------- profiles
create table if not exists public.profiles (
    id         uuid primary key references auth.users (id) on delete cascade,
    full_name  text not null default '',
    email      text not null default '',
    mobile     text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Profile activation details live on the server so a signed-in user is
-- recognised as complete on any device; the app mirrors them locally.
alter table public.profiles
    add column if not exists gender           text not null default '',
    add column if not exists dob              text not null default '',
    add column if not exists occupation       text not null default '',
    add column if not exists marital_status   text not null default '',
    add column if not exists nationality      text not null default '',
    add column if not exists address_line     text not null default '',
    add column if not exists city             text not null default '',
    add column if not exists state            text not null default '',
    add column if not exists country          text not null default '',
    add column if not exists pin_code         text not null default '',
    add column if not exists profile_complete boolean not null default false;

-- ---------------------------------------------------------------- bookings
-- Mirrors the Room `bookings` table in the Android app. passengers are kept
-- as jsonb in the same shape the app already stores locally (passengersJson).
create table if not exists public.bookings (
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid not null references auth.users (id) on delete cascade,
    pnr              text not null,
    train_number     text not null default '',
    train_name       text not null default '',
    from_code        text not null default '',
    to_code          text not null default '',
    from_name        text not null default '',
    to_name          text not null default '',
    travel_class_code text not null default '',
    travel_class_name text not null default '',
    fare             integer not null default 0,
    journey_date     text not null default '',
    quota            text not null default '',
    passengers       jsonb not null default '[]'::jsonb,
    contact          text not null default '',
    status           text not null default 'CONFIRMED',
    amount           integer not null default 0,
    payment_id       text not null default '',
    order_id         text not null default '',
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    unique (user_id, pnr)
);

create index if not exists bookings_user_created_idx
    on public.bookings (user_id, created_at desc);

-- --------------------------------------------------------------- passengers
create table if not exists public.passengers (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null references auth.users (id) on delete cascade,
    full_name       text not null default '',
    age             integer,
    gender          text not null default '',
    berth_preference text not null default 'No Preference',
    concession      text not null default 'None',
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

create index if not exists passengers_user_idx
    on public.passengers (user_id, created_at);

-- ---------------------------------------------------------- favourite routes
create table if not exists public.favourite_routes (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references auth.users (id) on delete cascade,
    from_code  text not null,
    to_code    text not null,
    created_at timestamptz not null default now(),
    unique (user_id, from_code, to_code)
);

create index if not exists favourite_routes_user_idx
    on public.favourite_routes (user_id, created_at);

-- --------------------------------------------------------------- updated_at
create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists profiles_touch on public.profiles;
create trigger profiles_touch before update on public.profiles
    for each row execute function public.touch_updated_at();

drop trigger if exists bookings_touch on public.bookings;
create trigger bookings_touch before update on public.bookings
    for each row execute function public.touch_updated_at();

drop trigger if exists passengers_touch on public.passengers;
create trigger passengers_touch before update on public.passengers
    for each row execute function public.touch_updated_at();

-- ------------------------------------------- auto-create a profile on signup
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (id, full_name, email, mobile)
    values (
        new.id,
        coalesce(new.raw_user_meta_data ->> 'full_name', ''),
        coalesce(new.email, ''),
        coalesce(new.raw_user_meta_data ->> 'mobile', '')
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- --------------------------------------------------------------------- RLS
alter table public.profiles enable row level security;
alter table public.bookings enable row level security;
alter table public.passengers enable row level security;
alter table public.favourite_routes enable row level security;

-- One policy per operation per table, all scoped to the row owner.
do $$
declare
    t text;
begin
    foreach t in array array['profiles', 'bookings', 'passengers', 'favourite_routes']
    loop
        execute format('drop policy if exists %I on public.%I', t || '_select_own', t);
        execute format('drop policy if exists %I on public.%I', t || '_insert_own', t);
        execute format('drop policy if exists %I on public.%I', t || '_update_own', t);
        execute format('drop policy if exists %I on public.%I', t || '_delete_own', t);
    end loop;
end;
$$;

-- profiles: the row id IS the user id
create policy profiles_select_own on public.profiles
    for select using (auth.uid() = id);
create policy profiles_insert_own on public.profiles
    for insert with check (auth.uid() = id);
create policy profiles_update_own on public.profiles
    for update using (auth.uid() = id) with check (auth.uid() = id);
create policy profiles_delete_own on public.profiles
    for delete using (auth.uid() = id);

-- bookings
create policy bookings_select_own on public.bookings
    for select using (auth.uid() = user_id);
create policy bookings_insert_own on public.bookings
    for insert with check (auth.uid() = user_id);
create policy bookings_update_own on public.bookings
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy bookings_delete_own on public.bookings
    for delete using (auth.uid() = user_id);

-- passengers
create policy passengers_select_own on public.passengers
    for select using (auth.uid() = user_id);
create policy passengers_insert_own on public.passengers
    for insert with check (auth.uid() = user_id);
create policy passengers_update_own on public.passengers
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy passengers_delete_own on public.passengers
    for delete using (auth.uid() = user_id);

-- favourite_routes
create policy favourite_routes_select_own on public.favourite_routes
    for select using (auth.uid() = user_id);
create policy favourite_routes_insert_own on public.favourite_routes
    for insert with check (auth.uid() = user_id);
create policy favourite_routes_update_own on public.favourite_routes
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy favourite_routes_delete_own on public.favourite_routes
    for delete using (auth.uid() = user_id);
