package com.project.api_rate_limiter.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the in-memory sliding-window algorithm — no Spring context,
 * so the algorithm's contract is pinned down independently of the wiring above it.
 */
class SlidingWindowAlgorithmTest {

    @Test
    void admitsUpToConfiguredLimit() {
        SlidingWindowAlgorithm alg = new SlidingWindowAlgorithm();
        for (int i = 0; i < 5; i++) {
            assertTrue(alg.allowRequest("k", 5, 60), "request " + (i + 1) + " should be admitted");
        }
    }

    @Test
    void rejectsRequestPastLimit() {
        SlidingWindowAlgorithm alg = new SlidingWindowAlgorithm();
        for (int i = 0; i < 3; i++) alg.allowRequest("k", 3, 60);
        assertFalse(alg.allowRequest("k", 3, 60));
    }

    @Test
    void admitsAgainAfterWindowElapses() throws InterruptedException {
        SlidingWindowAlgorithm alg = new SlidingWindowAlgorithm();
        assertTrue(alg.allowRequest("k", 1, 1));
        assertFalse(alg.allowRequest("k", 1, 1));
        // Sleep slightly past the window so the oldest entry ages out.
        Thread.sleep(1100);
        assertTrue(alg.allowRequest("k", 1, 1));
    }

    @Test
    void remainingRequestsReflectsUsage() {
        SlidingWindowAlgorithm alg = new SlidingWindowAlgorithm();
        assertEquals(5, alg.getRemainingRequests("k", 5));
        alg.allowRequest("k", 5, 60);
        alg.allowRequest("k", 5, 60);
        assertEquals(3, alg.getRemainingRequests("k", 5));
    }

    @Test
    void waitTimeUsesCeilingSeconds() {
        SlidingWindowAlgorithm alg = new SlidingWindowAlgorithm();
        alg.allowRequest("k", 1, 5);
        // Oldest entry is ~now, so wait should round up to the full window.
        long wait = alg.getWaitTimeSeconds("k", 5);
        assertTrue(wait >= 4 && wait <= 5, "expected 4..5s ceiling, got " + wait);
    }

    @Test
    void separateKeysHaveIndependentBuckets() {
        SlidingWindowAlgorithm alg = new SlidingWindowAlgorithm();
        alg.allowRequest("a", 1, 60);
        assertFalse(alg.allowRequest("a", 1, 60));
        assertTrue(alg.allowRequest("b", 1, 60));
    }
}
