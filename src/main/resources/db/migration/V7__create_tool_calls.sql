CREATE TABLE tool_calls(
    call_id VARCHAR(26) NOT NULL PRIMARY KEY, --主キー
    message_id CHAR(26),  -- ULID
    tool_name VARCHAR(50) NOT NULL, --呼び出したツールの名前
    target TEXT NOT NULL, --ツールで何をしたか
    succeeded INTEGER NOT NULL, --呼び出しが成功したか
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);