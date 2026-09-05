-- Car sharing — the one table behind the public link and the printable QR code.
--
-- An owner shares a car as https://tweakdapp.com/c/{code}. The same URL is what the QR encodes,
-- so the code is not a display detail: it can end up printed on a sticker glued to a car, and it
-- has to keep resolving for as long as that sticker exists.
--
-- Why a separate table rather than two columns on `cars`:
--   * a share can be revoked on transfer while the car row survives — an old sticker then answers
--     410 Gone instead of silently pointing a stranger at the new owner's build,
--   * the counters have a home without widening the hot `cars` row,
--   * a car that was never shared costs nothing.

begin;

create table public.car_share_links (
    id             uuid        primary key default gen_random_uuid(),
    car_id         uuid        not null references public.cars(id)     on delete cascade,
    owner_id       uuid        not null references public.profiles(id) on delete cascade,
    code           text        not null,
    is_enabled     boolean     not null default true,
    created_at     timestamptz not null default now(),
    revoked_at     timestamptz,
    view_count     bigint      not null default 0,
    qr_scan_count  bigint      not null default 0,
    last_viewed_at timestamptz,

    -- Crockford base32 minus I, L, O and U: the characters a human retyping a code off a scratched
    -- sticker confuses. The backend folds O -> 0 and I/L -> 1 on lookup and stores this canonical
    -- uppercase form, so the check is what keeps the two halves of that contract honest.
    constraint car_share_links_code_format_check
        check (code ~ '^[0-9A-HJKMNP-TV-Z]{10}$'),
    constraint car_share_links_view_count_check    check (view_count >= 0),
    constraint car_share_links_qr_scan_count_check check (qr_scan_count >= 0)
);

-- A code is a public identifier that may be printed: it is never reused, not even after
-- revocation, which is why uniqueness deliberately spans revoked rows too. Reissuing a retired
-- code would point an existing sticker at a different car.
create unique index car_share_links_code_uq on public.car_share_links (code);

-- At most one live link per car, enforced by the database rather than by a read-then-insert in the
-- service: two concurrent "Share" taps would both pass that pre-check and mint two codes, and the
-- loser's code would already be printable by then.
create unique index car_share_links_active_car_uq
    on public.car_share_links (car_id) where revoked_at is null;

-- Postgres does not index the referencing side of a foreign key. Without this, deleting a profile
-- (cascade) and the owner's future "my shared cars" read both scan the whole table.
create index car_share_links_owner_idx on public.car_share_links (owner_id);

comment on table  public.car_share_links is
    'Public share links (web URL + QR payload) for cars. One live row per car; revoked rows are kept so an old printed code answers 410 rather than 404 or, worse, another car.';
comment on column public.car_share_links.code is
    'Immutable random public identifier used in https://tweakdapp.com/c/{code}. 10 Crockford-base32 characters, canonical uppercase. Unique across every row ever issued. Printed on physical QR stickers: it must stay valid for as long as the owner owns the car, which is why there is no regenerate action.';
comment on column public.car_share_links.owner_id is
    'The owner the code was issued to (the car''s garages.owner_id at creation). A code belongs to the (car, owner) pair: when the marketplace transfers a car, the live row is revoked, never re-pointed at the new owner.';
comment on column public.car_share_links.is_enabled is
    'Owner-controlled pause. false = the public endpoint answers 410, but the code stays reserved and can be re-enabled, so a printed sticker starts working again.';
comment on column public.car_share_links.revoked_at is
    'Set by system events only: car transferred. A revoked code answers 410 forever and is never reissued. Deleting the car or the account removes the row through the FK cascade instead.';
comment on column public.car_share_links.view_count is
    'Public page views plus in-app resolves that did not carry ?s=qr. Known crawler user-agents are not counted. Analytics only — never authoritative, and never read on a hot path.';
comment on column public.car_share_links.qr_scan_count is
    'The same, for requests carrying ?s=qr — the tag baked into the URL the QR code encodes. Separating the two is the only way to tell whether the printed sticker is doing any work.';
comment on column public.car_share_links.last_viewed_at is
    'Last counted view or scan, for the owner-facing "viewed X times, last on Y" stat.';

-- Backend-owned data, like user_badges and user_devices_firebase_token: it reaches clients only
-- through /api/v1/garage/... and /public/v1/cars/..., which is what applies the owner-only rule on
-- the share surface and the projection on the public read. RLS with no policies closes reads;
-- the revoke also strips the TRUNCATE / REFERENCES / TRIGGER that Supabase grants by default and
-- that TRUNCATE ignores RLS for.
alter table public.car_share_links enable row level security;
revoke all on table public.car_share_links from anon, authenticated;

commit;
