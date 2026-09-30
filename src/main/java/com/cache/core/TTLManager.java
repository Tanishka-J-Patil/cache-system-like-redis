package com.cache.core;

import java.util.*;
import java.util.concurrent.*;

/**
 * TTL (Time To Live) Manager
 * 
 * Runs in background thread and:
 * 1. Checks for expired entries every 100ms
 * 2. Deletes expired entries automatically
 * 3. Tracks expiration times efficiently
 * 
 * Like Redis's active expiration!
 */
public class TTLManager {
    
    private final LRUCache cache;
    private final ConcurrentSkipListMap<Long, Set<String>> expirationMap;
    private final ConcurrentHashMap<String, Long> keyExpirations;
    
    // Background thread executor
    private final ScheduledExecutorService scheduler;
    
    // Is the cleaner running?
    private volatile boolean running = false;
    
    // Statistics
    private long expiredKeysCount = 0;
    private long cleanupCycles = 0;
    
    public TTLManager(LRUCache cache) {
        this.cache = cache;
        this.expirationMap = new ConcurrentSkipListMap<>();
        this.keyExpirations = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TTL-Cleaner");
            t.setDaemon(true); // Daemon thread - won't prevent JVM shutdown
            return t;
        });
    }
    
    /**
     * Start the background cleaner
     * Runs every 100ms (Redis does something similar)
     */
    public void start() {
        if (running) {
            System.out.println("⚠️ TTL Manager already running");
            return;
        }
        
        running = true;
        
        scheduler.scheduleAtFixedRate(
            this::cleanupExpiredKeys,
            0,              // Initial delay
            100,            // Period
            TimeUnit.MILLISECONDS
        );
        
        System.out.println("🚀 TTL Manager started (checking every 100ms)");
    }
    
    /**
     * Stop the background cleaner
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            System.out.println("🛑 TTL Manager stopped");
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Register a key with TTL
     * Called when a key is SET with expiration
     */
    public void registerExpiration(String key, long ttlMillis) {
        if (ttlMillis <= 0) {
            return; // No expiration
        }
        
        long expirationTime = System.currentTimeMillis() + ttlMillis;
        
        // Remove old expiration if exists
        Long oldExpiration = keyExpirations.get(key);
        if (oldExpiration != null) {
            Set<String> keys = expirationMap.get(oldExpiration);
            if (keys != null) {
                keys.remove(key);
                if (keys.isEmpty()) {
                    expirationMap.remove(oldExpiration);
                }
            }
        }
        
        // Add new expiration
        keyExpirations.put(key, expirationTime);
        expirationMap.computeIfAbsent(expirationTime, k -> ConcurrentHashMap.newKeySet())
                     .add(key);
        
        System.out.println("⏰ Registered: " + key + " expires in " + ttlMillis + "ms");
    }
    
    /**
     * Unregister a key (when deleted manually)
     */
    public void unregisterExpiration(String key) {
        Long expirationTime = keyExpirations.remove(key);
        
        if (expirationTime != null) {
            Set<String> keys = expirationMap.get(expirationTime);
            if (keys != null) {
                keys.remove(key);
                if (keys.isEmpty()) {
                    expirationMap.remove(expirationTime);
                }
            }
        }
    }
    
    /**
     * Main cleanup logic - runs every 100ms
     */
    private void cleanupExpiredKeys() {
        if (!running) {
            return;
        }
        
        cleanupCycles++;
        long now = System.currentTimeMillis();
        int expiredCount = 0;
        
        // Get all expiration times up to now (expired keys)
        Map<Long, Set<String>> expiredEntries = expirationMap.headMap(now, true);
        
        if (expiredEntries.isEmpty()) {
            return; // Nothing expired
        }
        
        // Process expired keys
        List<String> keysToDelete = new ArrayList<>();
        
        for (Map.Entry<Long, Set<String>> entry : expiredEntries.entrySet()) {
            keysToDelete.addAll(entry.getValue());
        }
        
        // Delete expired keys from cache
        for (String key : keysToDelete) {
            if (cache.delete(key)) {
                keyExpirations.remove(key);
                expiredCount++;
                expiredKeysCount++;
            }
        }
        
        // Remove expiration times from map
        for (Long expirationTime : expiredEntries.keySet()) {
            expirationMap.remove(expirationTime);
        }
        
        if (expiredCount > 0) {
            System.out.println("🧹 Cleanup: Removed " + expiredCount + " expired key(s)");
        }
    }
    
    /**
     * Get remaining TTL for a key (in seconds)
     * Returns:
     *  -2 if key doesn't exist or no TTL set
     *  -1 if key exists but has no expiration
     *  N if key expires in N seconds
     */
    public long getTTL(String key) {
        Long expirationTime = keyExpirations.get(key);
        
        if (expirationTime == null) {
            // Check if key exists in cache
            if (cache.exists(key)) {
                return -1; // Exists but no TTL
            } else {
                return -2; // Doesn't exist
            }
        }
        
        long now = System.currentTimeMillis();
        long remaining = expirationTime - now;
        
        if (remaining <= 0) {
            return -2; // Expired
        }
        
        return remaining / 1000; // Convert to seconds
    }
    
    /**
     * Make a key persistent (remove expiration)
     */
    public void persist(String key) {
        unregisterExpiration(key);
        System.out.println("♾️ PERSIST: " + key + " (now permanent)");
    }
    
    /**
     * Set/update expiration on existing key
     */
    public void expire(String key, long ttlMillis) {
        if (!cache.exists(key)) {
            System.out.println("⚠️ EXPIRE failed: Key doesn't exist: " + key);
            return;
        }
        
        registerExpiration(key, ttlMillis);
        System.out.println("⏰ EXPIRE: " + key + " in " + ttlMillis + "ms");
    }
    
    // ==================== STATISTICS ====================
    
    public long getExpiredKeysCount() {
        return expiredKeysCount;
    }
    
    public long getCleanupCycles() {
        return cleanupCycles;
    }
    
    public int getPendingExpirationsCount() {
        return keyExpirations.size();
    }
    
    public void printStats() {
        System.out.println("\n========== TTL MANAGER STATS ==========");
        System.out.println("Running: " + running);
        System.out.println("Cleanup cycles: " + cleanupCycles);
        System.out.println("Expired keys cleaned: " + expiredKeysCount);
        System.out.println("Pending expirations: " + getPendingExpirationsCount());
        
        if (!keyExpirations.isEmpty()) {
            System.out.println("\nUpcoming expirations:");
            expirationMap.entrySet().stream()
                .limit(5)
                .forEach(entry -> {
                    long timeUntil = entry.getKey() - System.currentTimeMillis();
                    System.out.println("  In " + (timeUntil/1000) + "s: " + entry.getValue());
                });
        }
        
        System.out.println("=======================================\n");
    }
}