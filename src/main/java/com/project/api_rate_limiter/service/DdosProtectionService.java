package com.project.api_rate_limiter.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import com.project.api_rate_limiter.config.RateLimitConfig;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DdosProtectionService {
    private final Map<String, Integer> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> bannedIps = new ConcurrentHashMap<>();
    private final RateLimitConfig config;
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        // Skip the reset thread entirely when DDoS protection is off — no counters
        // are being written, so nothing needs clearing.
        if (!config.getEffectiveDdosProtectionEnabled()) {
            return;
        }
        int resetInterval = config.getEffectiveDdosCountResetIntervalSeconds();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ddos-counter-reset");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::resetRequestCounts,
                resetInterval, resetInterval, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public boolean isBanned(String ipAddress) {
        LocalDateTime banExpiration = bannedIps.get(ipAddress);
        if (banExpiration != null) {
            if (LocalDateTime.now().isBefore(banExpiration)) {
                log.debug("IP {} is banned until {}", ipAddress, banExpiration);
                return true;
            } else {
                bannedIps.remove(ipAddress);
                return false;
            }
        }
        return false;
    }

    public boolean trackRequest(String ipAddress) {
        if (isBanned(ipAddress)) return false;
        int count = requestCounts.compute(ipAddress, (k, v) -> v == null ? 1 : v + 1);
        int threshold = config.getEffectiveDdosThreshold();
        if (count > threshold) {
            int banDuration = config.getEffectiveDdosBanDurationSeconds();
            log.warn("Possible DDoS attack detected from IP: {}. Request count: {}", ipAddress, count);
            banIp(ipAddress, banDuration);
            return false;
        }
        return true;
    }

    public void banIp(String ipAddress, int durationSeconds) {
        LocalDateTime expirationTime = LocalDateTime.now().plusSeconds(durationSeconds);
        bannedIps.put(ipAddress, expirationTime);
        log.info("Banned IP {} until {}", ipAddress, expirationTime);
    }

    private void resetRequestCounts() {
        // Swallow: scheduleAtFixedRate cancels the task permanently if it throws.
        try {
            requestCounts.clear();
        } catch (Exception e) {
            log.error("Failed to reset DDoS request counters", e);
        }
    }

    public int getBanDurationSeconds() {
        return config.getEffectiveDdosBanDurationSeconds();
    }
}
