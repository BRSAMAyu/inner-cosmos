-- One logical relationship/live invitation per unordered user pair.
--
-- Older schemas only constrained the directional (requester, addressee) tuple, so simultaneous
-- A->B and B->A requests could both commit. Reconcile any historical reverse duplicates before
-- installing expression indexes. Prefer an accepted relationship, then a pending one.
WITH ranked_friend_pairs AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY LEAST(requester_id, addressee_id),
                            GREATEST(requester_id, addressee_id)
               ORDER BY CASE status
                            WHEN 'ACCEPTED' THEN 0
                            WHEN 'PENDING' THEN 1
                            ELSE 2
                        END,
                        id
           ) AS pair_rank
    FROM tb_friend_relation
)
DELETE FROM tb_friend_relation
WHERE id IN (
    SELECT id FROM ranked_friend_pairs WHERE pair_rank > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_friend_unordered_pair
    ON tb_friend_relation (
        LEAST(requester_id, addressee_id),
        GREATEST(requester_id, addressee_id)
    );

-- Pending live-chat invitations have no dependent session yet, so duplicate historical pending
-- rows can be expired safely. Decided invitations remain as their audit history.
WITH ranked_pending_live_invites AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY LEAST(inviter_user_id, invitee_user_id),
                            GREATEST(inviter_user_id, invitee_user_id)
               ORDER BY id
           ) AS pair_rank
    FROM tb_live_chat_invite
    WHERE status = 'PENDING'
)
UPDATE tb_live_chat_invite
SET status = 'EXPIRED',
    updated_at = CURRENT_TIMESTAMP
WHERE id IN (
    SELECT id FROM ranked_pending_live_invites WHERE pair_rank > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_live_chat_pending_unordered_pair
    ON tb_live_chat_invite (
        LEAST(inviter_user_id, invitee_user_id),
        GREATEST(inviter_user_id, invitee_user_id)
    )
    WHERE status = 'PENDING';
