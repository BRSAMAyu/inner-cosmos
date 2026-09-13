-- CP-33 closing-checklist §2-6: read receipts are the RECIPIENT's opt-in choice.
-- A letter's receiver decides, per letter, whether the sender may ever learn it was read:
--   NEVER (default) = the sender's view keeps showing DELIVERED and never learns the read
--                     moment (privacy default -- no receipt is emitted unless asked for);
--   ALWAYS          = the sender's view may show READ once the recipient marks it read.
-- Only the receiver can change this column; sender-facing letter views mask READ -> DELIVERED
-- while it is NEVER. The lifecycle itself (DRAFT -> ... -> READ -> ...) is unchanged and stays
-- server-authoritative -- this column only shapes what the SENDER is allowed to see.
ALTER TABLE tb_slow_letter ADD COLUMN receipt_policy VARCHAR(16) NOT NULL DEFAULT 'NEVER';
