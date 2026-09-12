-- Exactly one owner, enforced by the database.
--
-- The ownership-transfer endpoint demotes the current owner and promotes the target inside one
-- transaction, but "one owner" was until now only a convention: any direct UPDATE, or two
-- transfers racing, could leave two owner rows — and an owner is the only role that can transfer
-- ownership or be neither re-roled nor removed. This makes that state unrepresentable.
--
-- Partial unique index on a constant: at most one row may satisfy `role = 'owner'`.
-- Note for anyone writing a transfer by hand: demote first, promote second. The index is checked
-- per statement, so promoting before demoting fails even though the transaction would have ended
-- in a legal state.

create unique index if not exists admin_team_members_single_owner_idx
    on public.admin_team_members ((true))
    where role = 'owner';
