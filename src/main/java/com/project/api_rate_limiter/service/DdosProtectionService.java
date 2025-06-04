package com.project.api_rate_limiter.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for DDoS protection.
 * Tracks request counts and bans IPs that exceed a threshold.
 */
@Service
@Slf4j
public class DdosProtectionService {
    
    private final Map<String, Integer> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> bannedIps = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    // Default settings - can be overridden via configuration
    private int ddosThreshold = 1000;
    private int ddosBanDurationSeconds = 3600; // 1 hour
    private int countResetIntervalSeconds = 60; // Reset counts every minute
    
    public DdosProtectionService() {
        // Schedule periodic reset of request counts
        scheduler.scheduleAtFixedRate(this::resetRequestCounts, 
                countResetIntervalSeconds, countResetIntervalSeconds, TimeUnit.SECONDS);
    }
    
    /**
     * Check if an IP address is banned.
     * 
     * @param ipAddress The IP address to check
     * @return true if the IP is banned, false otherwise
     */
    public boolean isBanned(String ipAddress) {
        LocalDateTime banExpiration = bannedIps.get(ipAddress);
        if (banExpiration != null) {
            if (LocalDateTime.now().isBefore(banExpiration)) {
                log.debug("IP {} is banned until {}", ipAddress, banExpiration);
                return true;
            } else {
                // Ban expired, remove from list
                bannedIps.remove(ipAddress);
                return false;
            }
        }
        return false;
    }
    
    /**
     * Track a request from an IP address and check if it exceeds the threshold.
     * 
     * @param ipAddress The IP address making the request
     * @return true if request is allowed, false if it should be blocked
     */
    public boolean trackRequest(String ipAddress) {
        if (isBanned(ipAddress)) {
            return false;
        }
        
        int count = requestCounts.compute(ipAddress, (k, v) -> v == null ? 1 : v + 1);
        
        if (count > ddosThreshold) {
            log.warn("Possible DDoS attack detected from IP: {}. Request count: {}", ipAddress, count);
            banIp(ipAddress, ddosBanDurationSeconds);
            return false;
        }
        
        return true;
    }
    
    /**
     * Ban an IP address for a specified duration.
     * 
     * @param ipAddress The IP address to ban
     * @param durationSeconds Duration of the ban in seconds
     */
    public void banIp(String ipAddress, int durationSeconds) {
        LocalDateTime expirationTime = LocalDateTime.now().plusSeconds(durationSeconds);
        bannedIps.put(ipAddress, expirationTime);
        log.info("Banned IP {} until {}", ipAddress, expirationTime);
    }
    
    /**
     * Reset all request counts (called periodically).
     */
    private void resetRequestCounts() {
        requestCounts.clear();
    }
    
    /**
     * Set the DDoS detection threshold (requests per interval).
     * 
     * @param threshold The new threshold
     */
    public void setDdosThreshold(int threshold) {
        this.ddosThreshold = threshold;
    }
    
    /**
     * Set the duration for which IPs are banned when a DDoS attack is detected.
     * 
     * @param banDurationSeconds Ban duration in seconds
     */
    public void setDdosBanDurationSeconds(int banDurationSeconds) {
        this.ddosBanDurationSeconds = banDurationSeconds;
    }
    
    /**
     * Set the interval at which request counts are reset.
     * 
     * @param resetIntervalSeconds Interval in seconds
     */
    public void setCountResetIntervalSeconds(int resetIntervalSeconds) {
        this.countResetIntervalSeconds = resetIntervalSeconds;
    }
    
    /**
     * Get the current ban duration in seconds.
     * 
     * @return Ban duration in seconds
     */
    public int getBanDurationSeconds() {
        return ddosBanDurationSeconds;
    }
} 