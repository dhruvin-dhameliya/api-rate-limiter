package com.project.api_rate_limiter.service;

import lombok.Setter;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DdosProtectionService {
    private final Map<String, Integer> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> bannedIps = new ConcurrentHashMap<>();
    @Setter
    private int ddosThreshold = 1000;
    @Setter
    private int ddosBanDurationSeconds = 3600; // 1 hour
    @Setter
    private int countResetIntervalSeconds = 60; // Reset counts every minute
    
    public DdosProtectionService() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::resetRequestCounts,
                countResetIntervalSeconds, countResetIntervalSeconds, TimeUnit.SECONDS);
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
        if (count > ddosThreshold) {
            log.warn("Possible DDoS attack detected from IP: {}. Request count: {}", ipAddress, count);
            banIp(ipAddress, ddosBanDurationSeconds);
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
        requestCounts.clear();
    }

    public int getBanDurationSeconds() {
        return ddosBanDurationSeconds;
    }
} 