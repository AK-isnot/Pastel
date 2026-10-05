CREATE TABLE messages(
    user_id VARCHAR(255) NOT NULL, 
    message_id CHAR(26) NOT NULL PRIMARY KEY,  -- ULID
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    input_tokens INTEGER,
    output_tokens INTEGER,
    use_model VARCHAR(50) 
);