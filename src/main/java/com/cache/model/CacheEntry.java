package com.cache.model;

import java.io.Serializable;

public class CacheEntry implements Serializable {

    private String   key;
    private Object   value;
    private DataType type;
    private long     createdAt;
    private long     lastAccessedAt;
    private Long     ttlMillis;       // null = no expiry
    private int      accessCount;

    public CacheEntry(String key, Object value, DataType type) {
        this.key            = key;
        this.value          = value;
        this.type           = type;
        this.createdAt      = System.currentTimeMillis();
        this.lastAccessedAt = this.createdAt;
        this.ttlMillis      = null;
        this.accessCount    = 0;
    }

    public CacheEntry(String key, Object value, DataType type, long ttlMillis) {
        this(key, value, type);
        this.ttlMillis = ttlMillis;
    }

    // ── Expiry ────────────────────────────────────────────────

    public boolean isExpired() {
        if (ttlMillis == null) return false;
        return (System.currentTimeMillis() - createdAt) > ttlMillis;
    }

    /**
     * @return seconds remaining, -1 if no expiry, -2 if already expired
     */
    public long getRemainingTTL() {
        if (ttlMillis == null) return -1;
        long remaining = ttlMillis - (System.currentTimeMillis() - createdAt);
        if (remaining <= 0) return -2;
        return remaining / 1000;
    }

    // ── Access tracking ───────────────────────────────────────

    public void markAccessed() {
        this.lastAccessedAt = System.currentTimeMillis();
        this.accessCount++;
    }

    // ── Getters ───────────────────────────────────────────────

    public String   getKey()            { return key; }
    public Object   getValue()          { return value; }
    public DataType getType()           { return type; }
    public long     getCreatedAt()      { return createdAt; }
    public long     getLastAccessedAt() { return lastAccessedAt; }
    public Long     getTtlMillis()      { return ttlMillis; }
    public int      getAccessCount()    { return accessCount; }

    // ── Setters ───────────────────────────────────────────────

    public void setValue(Object value) {
        this.value = value;
        markAccessed();
    }

    public void setTtl(long ttlMillis) {
        this.ttlMillis = ttlMillis;
        this.createdAt = System.currentTimeMillis(); // reset timer
    }

    public void persist() {
        this.ttlMillis = null;
    }

    @Override
    public String toString() {
        return "CacheEntry{key='" + key + "', type=" + type +
               ", accessCount=" + accessCount +
               ", ttl=" + getRemainingTTL() + "s}";
    }
}