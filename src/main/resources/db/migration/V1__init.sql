CREATE TABLE members (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    character_id TINYINT UNSIGNED NOT NULL,
    name VARCHAR(255) NOT NULL,
    grade VARCHAR(16) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_members_email (email),
    CONSTRAINT chk_members_character CHECK (character_id BETWEEN 1 AND 6),
    CONSTRAINT chk_members_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_members_grade CHECK (
        grade IN ('1학년', '2학년', '3학년', '4학년', '졸업생', '게스트')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE guestbook_entries (
    member_id BIGINT UNSIGNED NOT NULL,
    message TEXT NOT NULL,
    like_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (member_id),
    KEY idx_guestbook_latest (created_at DESC, member_id),
    KEY idx_guestbook_popular (like_count DESC, created_at DESC, member_id),
    CONSTRAINT chk_guestbook_message CHECK (CHAR_LENGTH(TRIM(message)) > 0),
    CONSTRAINT fk_guestbook_member FOREIGN KEY (member_id)
        REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- Lock the target guestbook entry before adding or removing a like.
-- Change like_count only if the like record changed, in the same transaction.
-- Before deleting a liker, remove their outgoing likes and update the counts.
CREATE TABLE guestbook_likes (
    entry_member_id BIGINT UNSIGNED NOT NULL,
    liker_member_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (entry_member_id, liker_member_id),
    KEY idx_guestbook_likes_liker (liker_member_id),
    CONSTRAINT fk_guestbook_likes_entry FOREIGN KEY (entry_member_id)
        REFERENCES guestbook_entries (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_guestbook_likes_liker FOREIGN KEY (liker_member_id)
        REFERENCES members (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
