CREATE TABLE token_usages(
    message_id CHAR(26) NOT NULL PRIMARY KEY,
    -- ULID
    input_tokens INTEGER,
    --入力トークン
    output_tokens INTEGER,
    --出力トークン
    cache_read_tokens INTEGER,
    --キャッシュから読むのにかかったトークン
    cache_write_tokens INTEGER --キャッシュに書き込むのにかかったトークン
);