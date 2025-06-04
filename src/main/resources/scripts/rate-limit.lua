-- Rate limiting script using the Token Bucket algorithm
-- KEYS[1]: The rate limit key
-- ARGV[1]: The rate limit (maximum number of tokens)
-- ARGV[2]: The window size in seconds
-- ARGV[3]: The current timestamp in seconds

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local current_time = tonumber(ARGV[3])

-- Check if the key exists
local current_tokens = tonumber(redis.call('get', key) or limit)

-- If this is the first request or the key has expired
if current_tokens == limit then
    -- Initialize with limit - 1 tokens (after consuming one)
    redis.call('set', key, limit - 1)
    -- Set expiration
    redis.call('expire', key, window)
    return true
end

-- If we have tokens available
if current_tokens > 0 then
    -- Consume a token
    redis.call('decrby', key, 1)
    return true
end

-- No tokens available
return false 