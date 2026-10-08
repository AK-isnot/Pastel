INSERT INTO
    token_usages(message_id, input_tokens, output_tokens)
SELECT
    message_id,
    input_tokens,
    output_tokens
FROM
    messages
WHERE
    role == 'assistant';