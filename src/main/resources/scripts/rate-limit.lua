-- Sliding Window Rate Limiting script for Redis
-- KEYS[1]: The unique key for the rate limit (e.g., ip:endpoint)
-- ARGV[1]: The maximum number of requests allowed
-- ARGV[2]: The time window in seconds
-- ARGV[3]: The current timestamp in milliseconds

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_seconds = tonumber(ARGV[2])
local current_time_ms = tonumber(ARGV[3])

-- Calculate the start of the time window
local window_start_ms = current_time_ms - (window_seconds * 1000)

-- Use a sorted set to store request timestamps
-- Remove timestamps that are outside the current window
redis.call('ZREMRANGEBYSCORE', key, 0, window_start_ms)

-- Get the number of requests in the current window
local request_count = redis.call('ZCARD', key)

-- Check if the rate limit has been exceeded
if request_count < limit then
    -- Add the current request timestamp to the sorted set
    -- The score and value are both the timestamp for simplicity
    redis.call('ZADD', key, current_time_ms, current_time_ms)
    
    -- Set the expiration for the key to the window size to clean up old keys
    redis.call('EXPIRE', key, window_seconds)
    
    return true -- Request is allowed
else
    return false -- Request is denied
end