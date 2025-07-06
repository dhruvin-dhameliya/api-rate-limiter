package com.project.api_rate_limiter.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.project.api_rate_limiter.config.RateLimitConfig;

import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class IpFilterService {
    
    private final Set<String> whitelistedIps = new HashSet<>();
    private final Set<String> blacklistedIps = new HashSet<>();
    
    @Autowired
    private RateLimitConfig config;
    
    @PostConstruct
    public void init() {
        if (config.getWhitelistedIps() != null) {
            whitelistedIps.addAll(config.getWhitelistedIps());
            log.info("Initialized IP whitelist with {} entries", whitelistedIps.size());
        }
        if (config.getBlacklistedIps() != null) {
            blacklistedIps.addAll(config.getBlacklistedIps());
            log.info("Initialized IP blacklist with {} entries", blacklistedIps.size());
        }
    }

    public boolean isWhitelisted(String ipAddress) {
        return whitelistedIps.contains(ipAddress);
    }

    public boolean isBlacklisted(String ipAddress) {
        return blacklistedIps.contains(ipAddress);
    }

    public boolean addToWhitelist(String ipAddress) {
        blacklistedIps.remove(ipAddress);
        boolean added = whitelistedIps.add(ipAddress);
        if (added) log.info("Added IP {} to whitelist", ipAddress);
        return added;
    }

    public boolean removeFromWhitelist(String ipAddress) {
        boolean removed = whitelistedIps.remove(ipAddress);
        if (removed) log.info("Removed IP {} from whitelist", ipAddress);
        return removed;
    }

    public boolean addToBlacklist(String ipAddress) {
        whitelistedIps.remove(ipAddress);
        boolean added = blacklistedIps.add(ipAddress);
        if (added) log.info("Added IP {} to blacklist", ipAddress);
        return added;
    }

    public boolean removeFromBlacklist(String ipAddress) {
        boolean removed = blacklistedIps.remove(ipAddress);
        if (removed) log.info("Removed IP {} from blacklist", ipAddress);
        return removed;
    }

    public Set<String> getWhitelistedIps() {
        return new HashSet<>(whitelistedIps);
    }
    public Set<String> getBlacklistedIps() {
        return new HashSet<>(blacklistedIps);
    }
} 