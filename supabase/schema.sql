create extension if not exists pgcrypto;
create extension if not exists pg_cron;

create schema if not exists private;
revoke all on schema private from public;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = timezone('utc', now());
    return new;
end;
$$;

create table if not exists public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    display_name text not null default '',
    swish_phone text,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.archives (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users (id) on delete cascade,
    name text not null,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint archives_name_not_blank check (char_length(btrim(name)) > 0)
);

create unique index if not exists archives_owner_id_name_key
    on public.archives (owner_id, lower(name));

create table if not exists public.receipts (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users (id) on delete cascade,
    archive_id uuid references public.archives (id) on delete set null,
    title text not null,
    note text not null default '',
    currency_code text not null default 'SEK',
    total_amount numeric(12, 2) not null default 0,
    purchase_date date not null default current_date,
    status text not null default 'draft',
    sent_at timestamptz,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint receipts_title_not_blank check (char_length(btrim(title)) > 0),
    constraint receipts_total_amount_nonnegative check (total_amount >= 0),
    constraint receipts_status_check
        check (status in ('draft', 'sent', 'archived', 'settled'))
);

create index if not exists receipts_owner_id_idx
    on public.receipts (owner_id, created_at desc);

create table if not exists public.receipt_participants (
    id uuid primary key default gen_random_uuid(),
    receipt_id uuid not null references public.receipts (id) on delete cascade,
    profile_id uuid references auth.users (id) on delete set null,
    display_order integer not null default 0,
    display_name text not null,
    initials text not null,
    accent_color integer not null default 0,
    phone_number text,
    share_amount numeric(12, 2) not null default 0,
    is_owner boolean not null default false,
    has_paid boolean not null default false,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint receipt_participants_name_not_blank
        check (char_length(btrim(display_name)) > 0),
    constraint receipt_participants_initials_not_blank
        check (char_length(btrim(initials)) > 0),
    constraint receipt_participants_share_nonnegative check (share_amount >= 0),
    constraint receipt_participants_receipt_order_key unique (receipt_id, display_order)
);

create index if not exists receipt_participants_receipt_id_idx
    on public.receipt_participants (receipt_id, display_order);

create table if not exists public.receipt_items (
    id uuid primary key default gen_random_uuid(),
    receipt_id uuid not null references public.receipts (id) on delete cascade,
    display_order integer not null default 0,
    name text not null,
    price_amount numeric(12, 2) not null default 0,
    has_paid boolean not null default false,
    payer_participant_id uuid references public.receipt_participants (id) on delete set null,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint receipt_items_name_not_blank check (char_length(btrim(name)) > 0),
    constraint receipt_items_price_nonnegative check (price_amount >= 0),
    constraint receipt_items_receipt_order_key unique (receipt_id, display_order)
);

create index if not exists receipt_items_receipt_id_idx
    on public.receipt_items (receipt_id, display_order);

create table if not exists public.receipt_item_assignments (
    item_id uuid not null references public.receipt_items (id) on delete cascade,
    participant_id uuid not null references public.receipt_participants (id) on delete cascade,
    created_at timestamptz not null default timezone('utc', now()),
    primary key (item_id, participant_id)
);

create table if not exists public.payment_requests (
    id uuid primary key default gen_random_uuid(),
    receipt_id uuid not null references public.receipts (id) on delete cascade,
    participant_id uuid references public.receipt_participants (id) on delete set null,
    requested_by_user_id uuid not null references auth.users (id) on delete cascade,
    public_token text not null unique default encode(gen_random_bytes(16), 'hex'),
    swish_payment_token text,
    payee_phone text not null,
    amount numeric(12, 2) not null,
    message text not null default '',
    status text not null default 'pending',
    opened_at timestamptz,
    callback_opened_at timestamptz,
    paid_at timestamptz,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    constraint payment_requests_amount_positive check (amount > 0),
    constraint payment_requests_status_check
        check (status in ('pending', 'opened', 'paid', 'expired', 'cancelled'))
);

create index if not exists payment_requests_receipt_id_idx
    on public.payment_requests (receipt_id, created_at desc);

create index if not exists payment_requests_public_token_idx
    on public.payment_requests (public_token);

create table if not exists public.history_entries (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    payload jsonb not null,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

create index if not exists history_entries_owner_id_idx
    on public.history_entries (owner_id, created_at desc);

alter table public.history_entries
    alter column owner_id set default auth.uid();

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (id, display_name)
    values (
        new.id,
        coalesce(new.raw_user_meta_data ->> 'display_name', '')
    )
    on conflict (id) do nothing;

    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;

create trigger on_auth_user_created
after insert on auth.users
for each row
execute function public.handle_new_user();

create or replace function public.ensure_receipt_archive_owner_match()
returns trigger
language plpgsql
as $$
declare
    archive_owner_id uuid;
begin
    if new.archive_id is null then
        return new;
    end if;

    select owner_id
    into archive_owner_id
    from public.archives
    where id = new.archive_id;

    if archive_owner_id is null then
        raise exception 'Archive % does not exist.', new.archive_id;
    end if;

    if archive_owner_id <> new.owner_id then
        raise exception 'Receipt owner must match archive owner.';
    end if;

    return new;
end;
$$;

drop trigger if exists receipts_archive_owner_match on public.receipts;

create trigger receipts_archive_owner_match
before insert or update on public.receipts
for each row
execute function public.ensure_receipt_archive_owner_match();

create or replace function public.ensure_item_payer_belongs_to_receipt()
returns trigger
language plpgsql
as $$
declare
    payer_receipt_id uuid;
begin
    if new.payer_participant_id is null then
        return new;
    end if;

    select receipt_id
    into payer_receipt_id
    from public.receipt_participants
    where id = new.payer_participant_id;

    if payer_receipt_id is null then
        raise exception 'Payer participant % does not exist.', new.payer_participant_id;
    end if;

    if payer_receipt_id <> new.receipt_id then
        raise exception 'Item payer must belong to the same receipt.';
    end if;

    return new;
end;
$$;

drop trigger if exists receipt_items_payer_receipt_match on public.receipt_items;

create trigger receipt_items_payer_receipt_match
before insert or update on public.receipt_items
for each row
execute function public.ensure_item_payer_belongs_to_receipt();

create or replace function public.ensure_item_assignment_receipt_match()
returns trigger
language plpgsql
as $$
declare
    assignment_item_receipt_id uuid;
    assignment_participant_receipt_id uuid;
begin
    select receipt_id
    into assignment_item_receipt_id
    from public.receipt_items
    where id = new.item_id;

    select receipt_id
    into assignment_participant_receipt_id
    from public.receipt_participants
    where id = new.participant_id;

    if assignment_item_receipt_id is null or assignment_participant_receipt_id is null then
        raise exception 'Item assignment references a missing row.';
    end if;

    if assignment_item_receipt_id <> assignment_participant_receipt_id then
        raise exception 'Assigned participant must belong to the same receipt as the item.';
    end if;

    return new;
end;
$$;

create or replace function public.assign_history_entry_owner()
returns trigger
language plpgsql
as $$
begin
    new.owner_id = auth.uid();
    return new;
end;
$$;

create or replace function public.delete_expired_history_entries()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    deleted_count integer;
begin
    with deleted_rows as (
        delete from public.history_entries
        where created_at < timezone('utc', now()) - interval '30 days'
        returning 1
    )
    select count(*)
    into deleted_count
    from deleted_rows;

    return deleted_count;
end;
$$;

drop trigger if exists receipt_item_assignments_receipt_match
    on public.receipt_item_assignments;

create trigger receipt_item_assignments_receipt_match
before insert or update on public.receipt_item_assignments
for each row
execute function public.ensure_item_assignment_receipt_match();

create or replace function private.is_archive_owner(target_archive_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
    select exists (
        select 1
        from public.archives
        where id = target_archive_id
          and owner_id = auth.uid()
    );
$$;

create or replace function private.is_receipt_owner(target_receipt_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
    select exists (
        select 1
        from public.receipts
        where id = target_receipt_id
          and owner_id = auth.uid()
    );
$$;

create or replace function private.is_participant_owner(target_participant_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
    select exists (
        select 1
        from public.receipt_participants participants
        join public.receipts receipts
          on receipts.id = participants.receipt_id
        where participants.id = target_participant_id
          and receipts.owner_id = auth.uid()
    );
$$;

create or replace function private.is_item_owner(target_item_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
    select exists (
        select 1
        from public.receipt_items items
        join public.receipts receipts
          on receipts.id = items.receipt_id
        where items.id = target_item_id
          and receipts.owner_id = auth.uid()
    );
$$;

revoke all on function private.is_archive_owner(uuid) from public;
revoke all on function private.is_receipt_owner(uuid) from public;
revoke all on function private.is_participant_owner(uuid) from public;
revoke all on function private.is_item_owner(uuid) from public;

create or replace function public.get_payment_request_by_token(request_token text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    request_record public.payment_requests%rowtype;
    receipt_title text;
begin
    select *
    into request_record
    from public.payment_requests
    where public_token = request_token;

    if not found then
        return jsonb_build_object('found', false);
    end if;

    select title
    into receipt_title
    from public.receipts
    where id = request_record.receipt_id;

    return jsonb_build_object(
        'found', true,
        'publicToken', request_record.public_token,
        'receiptId', request_record.receipt_id,
        'receiptTitle', receipt_title,
        'swishPaymentToken', request_record.swish_payment_token,
        'payeePhone', request_record.payee_phone,
        'amount', request_record.amount,
        'message', request_record.message,
        'status', request_record.status
    );
end;
$$;

create or replace function public.mark_payment_request_opened(request_token text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    updated_request public.payment_requests%rowtype;
begin
    update public.payment_requests
    set opened_at = coalesce(opened_at, timezone('utc', now())),
        status = case
            when status = 'pending' then 'opened'
            else status
        end,
        updated_at = timezone('utc', now())
    where public_token = request_token
    returning *
    into updated_request;

    if not found then
        return jsonb_build_object('found', false);
    end if;

    return jsonb_build_object(
        'found', true,
        'publicToken', updated_request.public_token,
        'status', updated_request.status,
        'openedAt', updated_request.opened_at
    );
end;
$$;

create or replace function public.mark_payment_request_callback(request_token text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    updated_request public.payment_requests%rowtype;
begin
    update public.payment_requests
    set callback_opened_at = timezone('utc', now()),
        status = case
            when status = 'pending' then 'opened'
            else status
        end,
        updated_at = timezone('utc', now())
    where public_token = request_token
    returning *
    into updated_request;

    if not found then
        return jsonb_build_object('found', false);
    end if;

    return jsonb_build_object(
        'found', true,
        'publicToken', updated_request.public_token,
        'status', updated_request.status,
        'callbackOpenedAt', updated_request.callback_opened_at
    );
end;
$$;

create or replace function public.get_history_payment_card_by_short_id(
    receipt_short_id text,
    payment_card_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    history_record public.history_entries%rowtype;
    payment_card jsonb;
    normalized_receipt_short_id text;
    normalized_payment_card_id text;
begin
    normalized_receipt_short_id := lower(btrim(coalesce(receipt_short_id, '')));
    normalized_payment_card_id := btrim(coalesce(payment_card_id, ''));

    if normalized_receipt_short_id = '' or normalized_payment_card_id = '' then
        return jsonb_build_object('found', false);
    end if;

    select *
    into history_record
    from public.history_entries
    where lower(left(id::text, 8)) = normalized_receipt_short_id
    order by created_at desc
    limit 1;

    if not found then
        return jsonb_build_object('found', false);
    end if;

    select card
    into payment_card
    from jsonb_array_elements(coalesce(history_record.payload -> 'payment_cards', '[]'::jsonb)) card
    where card ->> 'id' = normalized_payment_card_id
    limit 1;

    if payment_card is null then
        return jsonb_build_object('found', false);
    end if;

    return jsonb_build_object(
        'found', true,
        'receiptId', history_record.id,
        'receiptShortId', left(history_record.id::text, 8),
        'paymentCardId', normalized_payment_card_id,
        'recipientPhone', coalesce(payment_card ->> 'recipient_phone', ''),
        'amount', coalesce(payment_card ->> 'amount', ''),
        'hasPaid', case
            when jsonb_typeof(payment_card -> 'has_paid') = 'boolean'
                then (payment_card ->> 'has_paid')::boolean
            else false
        end,
        'message', coalesce(history_record.payload ->> 'receipt_name', '')
    );
end;
$$;

create or replace function public.mark_history_payment_card_paid(
    receipt_short_id text,
    payment_card_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    history_record public.history_entries%rowtype;
    payment_card jsonb;
    payment_card_index integer;
    normalized_receipt_short_id text;
    normalized_payment_card_id text;
begin
    normalized_receipt_short_id := lower(btrim(coalesce(receipt_short_id, '')));
    normalized_payment_card_id := btrim(coalesce(payment_card_id, ''));

    if normalized_receipt_short_id = '' or normalized_payment_card_id = '' then
        return jsonb_build_object('found', false);
    end if;

    select *
    into history_record
    from public.history_entries
    where lower(left(id::text, 8)) = normalized_receipt_short_id
    order by created_at desc
    limit 1;

    if not found then
        return jsonb_build_object('found', false);
    end if;

    select card, ordinality::integer - 1
    into payment_card, payment_card_index
    from jsonb_array_elements(coalesce(history_record.payload -> 'payment_cards', '[]'::jsonb))
        with ordinality as cards(card, ordinality)
    where card ->> 'id' = normalized_payment_card_id
    limit 1;

    if payment_card is null or payment_card_index is null then
        return jsonb_build_object('found', false);
    end if;

    update public.history_entries
    set payload = jsonb_set(
            payload,
            array['payment_cards', payment_card_index::text, 'has_paid'],
            'true'::jsonb,
            true
        ),
        updated_at = timezone('utc', now())
    where id = history_record.id
    returning *
    into history_record;

    payment_card := jsonb_set(payment_card, '{has_paid}', 'true'::jsonb, true);

    return jsonb_build_object(
        'found', true,
        'receiptId', history_record.id,
        'receiptShortId', left(history_record.id::text, 8),
        'paymentCardId', normalized_payment_card_id,
        'recipientPhone', coalesce(payment_card ->> 'recipient_phone', ''),
        'amount', coalesce(payment_card ->> 'amount', ''),
        'hasPaid', true,
        'message', coalesce(history_record.payload ->> 'receipt_name', '')
    );
end;
$$;

grant execute on function public.get_payment_request_by_token(text)
    to anon, authenticated;
grant execute on function public.mark_payment_request_opened(text)
    to anon, authenticated;
grant execute on function public.mark_payment_request_callback(text)
    to anon, authenticated;
grant execute on function public.get_history_payment_card_by_short_id(text, text)
    to anon, authenticated;
grant execute on function public.mark_history_payment_card_paid(text, text)
    to anon, authenticated;

select cron.schedule(
    'history-entries-retention',
    '0 3 * * *',
    $$ select public.delete_expired_history_entries(); $$
);

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at
before update on public.profiles
for each row
execute function public.set_updated_at();

drop trigger if exists archives_set_updated_at on public.archives;
create trigger archives_set_updated_at
before update on public.archives
for each row
execute function public.set_updated_at();

drop trigger if exists receipts_set_updated_at on public.receipts;
create trigger receipts_set_updated_at
before update on public.receipts
for each row
execute function public.set_updated_at();

drop trigger if exists receipt_participants_set_updated_at on public.receipt_participants;
create trigger receipt_participants_set_updated_at
before update on public.receipt_participants
for each row
execute function public.set_updated_at();

drop trigger if exists receipt_items_set_updated_at on public.receipt_items;
create trigger receipt_items_set_updated_at
before update on public.receipt_items
for each row
execute function public.set_updated_at();

drop trigger if exists payment_requests_set_updated_at on public.payment_requests;
create trigger payment_requests_set_updated_at
before update on public.payment_requests
for each row
execute function public.set_updated_at();

drop trigger if exists history_entries_set_updated_at on public.history_entries;
create trigger history_entries_set_updated_at
before update on public.history_entries
for each row
execute function public.set_updated_at();

drop trigger if exists history_entries_assign_owner on public.history_entries;
create trigger history_entries_assign_owner
before insert on public.history_entries
for each row
execute function public.assign_history_entry_owner();

alter table public.profiles enable row level security;
alter table public.archives enable row level security;
alter table public.receipts enable row level security;
alter table public.receipt_participants enable row level security;
alter table public.receipt_items enable row level security;
alter table public.receipt_item_assignments enable row level security;
alter table public.payment_requests enable row level security;
alter table public.history_entries enable row level security;

drop policy if exists "Users can read their own profile" on public.profiles;
create policy "Users can read their own profile"
on public.profiles
for select
to authenticated
using ((select auth.uid()) = id);

drop policy if exists "Users can insert their own profile" on public.profiles;
create policy "Users can insert their own profile"
on public.profiles
for insert
to authenticated
with check ((select auth.uid()) = id);

drop policy if exists "Users can update their own profile" on public.profiles;
create policy "Users can update their own profile"
on public.profiles
for update
to authenticated
using ((select auth.uid()) = id)
with check ((select auth.uid()) = id);

drop policy if exists "Users can delete their own profile" on public.profiles;
create policy "Users can delete their own profile"
on public.profiles
for delete
to authenticated
using ((select auth.uid()) = id);

drop policy if exists "Users can manage their own archives" on public.archives;
create policy "Users can manage their own archives"
on public.archives
for all
to authenticated
using ((select auth.uid()) = owner_id)
with check ((select auth.uid()) = owner_id);

drop policy if exists "Users can manage their own receipts" on public.receipts;
create policy "Users can manage their own receipts"
on public.receipts
for all
to authenticated
using ((select auth.uid()) = owner_id)
with check ((select auth.uid()) = owner_id);

drop policy if exists "Users can manage participants on their own receipts"
    on public.receipt_participants;
create policy "Users can manage participants on their own receipts"
on public.receipt_participants
for all
to authenticated
using (private.is_receipt_owner(receipt_id))
with check (private.is_receipt_owner(receipt_id));

drop policy if exists "Users can manage items on their own receipts"
    on public.receipt_items;
create policy "Users can manage items on their own receipts"
on public.receipt_items
for all
to authenticated
using (private.is_receipt_owner(receipt_id))
with check (private.is_receipt_owner(receipt_id));

drop policy if exists "Users can manage item assignments on their own receipts"
    on public.receipt_item_assignments;
create policy "Users can manage item assignments on their own receipts"
on public.receipt_item_assignments
for all
to authenticated
using (
    private.is_item_owner(item_id)
    and private.is_participant_owner(participant_id)
)
with check (
    private.is_item_owner(item_id)
    and private.is_participant_owner(participant_id)
);

drop policy if exists "Users can manage payment requests on their own receipts"
    on public.payment_requests;
create policy "Users can manage payment requests on their own receipts"
on public.payment_requests
for all
to authenticated
using (private.is_receipt_owner(receipt_id))
with check (
    private.is_receipt_owner(receipt_id)
    and (select auth.uid()) = requested_by_user_id
);

drop policy if exists "Users can manage their own history entries"
    on public.history_entries;
drop policy if exists "Users can read their own history entries"
    on public.history_entries;
drop policy if exists "Users can insert history entries"
    on public.history_entries;
drop policy if exists "Users can update their own history entries"
    on public.history_entries;
drop policy if exists "Users can delete their own history entries"
    on public.history_entries;

create policy "Users can read their own history entries"
on public.history_entries
for select
to authenticated
using ((select auth.uid()) = owner_id);

create policy "Users can insert history entries"
on public.history_entries
for insert
to authenticated
with check (true);

create policy "Users can update their own history entries"
on public.history_entries
for update
to authenticated
using ((select auth.uid()) = owner_id)
with check ((select auth.uid()) = owner_id);

create policy "Users can delete their own history entries"
on public.history_entries
for delete
to authenticated
using ((select auth.uid()) = owner_id);
