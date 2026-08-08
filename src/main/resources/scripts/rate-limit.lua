-- Sliding-window rate limit.
-- KEYS[1]  bucket key (e.g. ip:endpoint)
-- ARGV[1]  max requests allowed in the window
-- ARGV[2]  window size in seconds
-- ARGV[3]  current timestamp in milliseconds (used as ZSET score)
-- ARGV[4]  unique member id for this request (prevents ZADD dedup when two
--          requests arrive in the same millisecond)

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_seconds = tonumber(ARGV[2])
local current_time_ms = tonumber(ARGV[3])
local member = ARGV[4]

local window_start_ms = current_time_ms - (window_seconds * 1000)

redis.call('ZREMRANGEBYSCORE', key, 0, window_start_ms)
local request_count = redis.call('ZCARD', key)

if request_count < limit then
    redis.call('ZADD', key, current_time_ms, member)
    redis.call('EXPIRE', key, window_seconds)
    return true
else
    return false
end