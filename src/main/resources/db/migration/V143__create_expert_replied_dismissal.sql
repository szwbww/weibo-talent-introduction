CREATE TABLE expert_replied_dismissal (
    username           VARCHAR(64) NOT NULL,
    expert_contact_id  BIGINT NOT NULL,
    last_inbound_id    BIGINT NOT NULL,
    dismissed_at       DATETIME NOT NULL,
    PRIMARY KEY (username, expert_contact_id),
    CONSTRAINT fk_expert_replied_dismissal_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '按用户记录人工移出已回复时的最新来信，新来信可重新进入';
