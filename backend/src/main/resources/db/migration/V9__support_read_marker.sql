-- Phase 4, part 5: remember when the member last read each help request, so the app can show "new reply".

ALTER TABLE support_tickets ADD COLUMN member_read_at TIMESTAMPTZ;

UPDATE support_tickets SET member_read_at = updated_at;
