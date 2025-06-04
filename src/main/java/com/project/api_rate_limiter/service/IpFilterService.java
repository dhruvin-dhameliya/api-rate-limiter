package com.project.api_rate_limiter.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.project.api_rate_limiter.config.RateLimitConfig;

import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing IP address whitelist and blacklist.
 * Whitelisted IPs bypass rate limiting, blacklisted IPs are denied access.
 */
@Service
@Slf4j
public class IpFilterService {
    
    private final Set<String> whitelistedIps = new HashSet<>();
    private final Set<String> blacklistedIps = new HashSet<>();
    
    @Autowired
    private RateLimitConfig config;
    
    @PostConstruct
    public void init() {
        // Initialize whitelist and blacklist from configuration
        if (config.getWhitelistedIps() != null) {
            whitelistedIps.addAll(config.getWhitelistedIps());
            log.info("Initialized IP whitelist with {} entries", whitelistedIps.size());
        }
        
        if (config.getBlacklistedIps() != null) {
            blacklistedIps.addAll(config.getBlacklistedIps());
            log.info("Initialized IP blacklist with {} entries", blacklistedIps.size());
        }
    }
    
    /**
     * Check if an IP address is whitelisted.
     * Whitelisted IPs bypass rate limiting.
     * 
     * @param ipAddress The IP address to check
     * @return true if the IP is whitelisted, false otherwise
     */
    public boolean isWhitelisted(String ipAddress) {
        return whitelistedIps.contains(ipAddress);
    }
    
    /**
     * Check if an IP address is blacklisted.
     * Blacklisted IPs are denied access.
     * 
     * @param ipAddress The IP address to check
     * @return true if the IP is blacklisted, false otherwise
     */
    public boolean isBlacklisted(String ipAddress) {
        return blacklistedIps.contains(ipAddress);
    }
    
    /**
     * Add an IP address to the whitelist.
     * 
     * @param ipAddress The IP address to whitelist
     * @return true if added, false if already whitelisted
     */
    public boolean addToWhitelist(String ipAddress) {
        // Remove from blacklist if present to avoid conflicts
        blacklistedIps.remove(ipAddress);
        boolean added = whitelistedIps.add(ipAddress);
        if (added) {
            log.info("Added IP {} to whitelist", ipAddress);
        }
        return added;
    }
    
    /**
     * Remove an IP address from the whitelist.
     * 
     * @param ipAddress The IP address to remove from whitelist
     * @return true if removed, false if not in whitelist
     */
    public boolean removeFromWhitelist(String ipAddress) {
        boolean removed = whitelistedIps.remove(ipAddress);
        if (removed) {
            log.info("Removed IP {} from whitelist", ipAddress);
        }
        return removed;
    }
    
    /**
     * Add an IP address to the blacklist.
     * 
     * @param ipAddress The IP address to blacklist
     * @return true if added, false if already blacklisted
     */
    public boolean addToBlacklist(String ipAddress) {
        // Remove from whitelist if present to avoid conflicts
        whitelistedIps.remove(ipAddress);
        boolean added = blacklistedIps.add(ipAddress);
        if (added) {
            log.info("Added IP {} to blacklist", ipAddress);
        }
        return added;
    }
    
    /**
     * Remove an IP address from the blacklist.
     * 
     * @param ipAddress The IP address to remove from blacklist
     * @return true if removed, false if not in blacklist
     */
    public boolean removeFromBlacklist(String ipAddress) {
        boolean removed = blacklistedIps.remove(ipAddress);
        if (removed) {
            log.info("Removed IP {} from blacklist", ipAddress);
        }
        return removed;
    }
    
    /**
     * Get all whitelisted IP addresses.
     * 
     * @return Set of whitelisted IP addresses
     */
    public Set<String> getWhitelistedIps() {
        return new HashSet<>(whitelistedIps);
    }
    
    /**
     * Get all blacklisted IP addresses.
     * 
     * @return Set of blacklisted IP addresses
     */
    public Set<String> getBlacklistedIps() {
        return new HashSet<>(blacklistedIps);
    }
} 