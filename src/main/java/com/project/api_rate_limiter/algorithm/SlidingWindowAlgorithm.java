package com.project.api_rate_limiter.algorithm;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.stereotype.Component;

@Component
public class SlidingWindowAlgorithm implements RateLimitAlgorithm {

    public int getRemainingRequests(String key, int maxRequests) {
        Queue<Long> timestamps = requestTimestamps.get(key);
        if (timestamps == null) return maxRequests;
        return Math.max(0, maxRequests - timestamps.size());
    }

    private final Map<String, Queue<Long>> requestTimestamps = new ConcurrentHashMap<>();

    @Override
    public boolean allowRequest(String key, int maxRequests, int timeWindowSeconds) {
        long currentTime = System.currentTimeMillis();
        long timeWindowMs = timeWindowSeconds * 1000L;
        Queue<Long> timestamps = requestTimestamps.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());

        while (!timestamps.isEmpty() && timestamps.peek() <= currentTime - timeWindowMs) {
            timestamps.poll();
        }
        if (timestamps.size() < maxRequests) {
            timestamps.offer(currentTime);
            return true;
        }
        return false;
    }

    @Override
    public long getWaitTimeSeconds(String key) {
        Queue<Long> timestamps = requestTimestamps.get(key);
        if (timestamps == null || timestamps.isEmpty()) return 0;
        Long oldestTimestamp = timestamps.peek();
        if (oldestTimestamp == null) return 0;
        long currentTime = System.currentTimeMillis();
        long waitTimeMs = Math.max(0, oldestTimestamp - (currentTime - 60000));
        return waitTimeMs / 1000;
    }
}