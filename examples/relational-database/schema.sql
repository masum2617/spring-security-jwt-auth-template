-- Optional worked example, not a required application schema.
CREATE TABLE app_user (
    id BIGINT PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL
);

CREATE TABLE app_user_role (
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    role VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, role),
    CHECK (role ~ '^[A-Z][A-Z0-9_]{0,63}$' AND role NOT LIKE 'ROLE\_%' ESCAPE '\')
);
