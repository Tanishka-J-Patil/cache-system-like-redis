package com.cache.core;

import com.cache.model.CacheEntry;
import com.cache.model.DataType;
import com.cache.model.Node;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 */
public class RedisCache {

    // ── Storage ───────────────────────────────────────────────
    private final int capacity;
    private final Map<String, Node> map;        
    private final Node head, tail;               
    // ── Stats ─────────────────────────────────────────────────
    private final AtomicLong hitCount      = new AtomicLong(0);
    private final AtomicLong missCount     = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);

    // ── TTL expiry thread ─────────────────────────────────────
    private final ScheduledExecutorService expirer =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cache-expirer");
            t.setDaemon(true);
            return t;
        });

    public RedisCache(int capacity) {
        this.capacity = capacity;
        this.map      = new ConcurrentHashMap<>();

        // Sentinel nodes — never hold real data
        head = new Node("HEAD", null);
        tail = new Node("TAIL", null);
        head.next = tail;
        tail.prev = head;

        // Passive expiry scan every second
        expirer.scheduleAtFixedRate(this::evictExpired, 1, 1, TimeUnit.SECONDS);
    }

    public synchronized void set(String key, String value) {
        if (map.containsKey(key)) {
            Node node = map.get(key);
            node.entry.setValue(value);
            moveToFront(node);
        } else {
            CacheEntry entry = new CacheEntry(key, value, DataType.STRING);
            Node node = new Node(key, entry);
            map.put(key, node);
            addToFront(node);
            if (map.size() > capacity) evictLRU();
        }
    }

    /** SET key value EX seconds */
    public synchronized void setex(String key, String value, long ttlSeconds) {
        long ttlMillis = ttlSeconds * 1000L;
        if (map.containsKey(key)) {
            Node node = map.get(key);
            node.entry.setValue(value);
            node.entry.setTtl(ttlMillis);
            moveToFront(node);
        } else {
            CacheEntry entry = new CacheEntry(key, value, DataType.STRING, ttlMillis);
            Node node = new Node(key, entry);
            map.put(key, node);
            addToFront(node);
            if (map.size() > capacity) evictLRU();
        }
    }

    /** GET key → value or null */
    public synchronized String get(String key) {
        if (!map.containsKey(key)) {
            missCount.incrementAndGet();
            return null;
        }
        Node node = map.get(key);
        if (node.entry.isExpired()) {
            removeNode(node);
            map.remove(key);
            missCount.incrementAndGet();
            return null;
        }
        node.entry.markAccessed();
        moveToFront(node);
        hitCount.incrementAndGet();
        return node.entry.getValue().toString();
    }

    /** DEL key → true if existed */
    public synchronized boolean del(String key) {
        if (!map.containsKey(key)) return false;
        Node node = map.get(key);
        removeNode(node);
        map.remove(key);
        return true;
    }

    /** TTL key → seconds remaining, -1 no expiry, -2 not found */
    public synchronized long ttl(String key) {
        if (!map.containsKey(key)) return -2;
        Node node = map.get(key);
        if (node.entry.isExpired()) {
            removeNode(node);
            map.remove(key);
            return -2;
        }
        return node.entry.getRemainingTTL();
    }

    /** INCR key (treats value as long, starts at 0 if missing) */
    public synchronized long incr(String key) {
        return incrby(key, 1);
    }

    /** INCRBY key delta */
    public synchronized long incrby(String key, long delta) {
        String val = get(key);
        long current = (val == null) ? 0L : Long.parseLong(val);
        long next = current + delta;
        set(key, String.valueOf(next));
        return next;
    }

    /** DECR key */
    public synchronized long decr(String key) {
        return decrby(key, 1);
    }

    /** DECRBY key delta */
    public synchronized long decrby(String key, long delta) {
        return incrby(key, -delta);
    }

    /** FLUSHALL — wipe everything */
    public synchronized void flushAll() {
        map.clear();
        head.next = tail;
        tail.prev = head;
        hitCount.set(0);
        missCount.set(0);
        evictionCount.set(0);
    }

    /** DBSIZE */
    public int dbsize() {
        return map.size();
    }

    /** Returns all non-expired entries as a list of attribute maps for the UI table. */
    public synchronized List<Map<String, Object>> getAllEntries() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Node> e : map.entrySet()) {
            Node node = e.getValue();
            if (node.entry.isExpired()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key",         node.entry.getKey());
            row.put("value",       node.entry.getValue().toString());
            row.put("ttl",         node.entry.getRemainingTTL());
            row.put("accessCount", node.entry.getAccessCount());
            result.add(row);
        }
        return result;
    }

    // ── Stats ─────────────────────────────────────────────────

    public long getHitCount()      { return hitCount.get(); }
    public long getMissCount()     { return missCount.get(); }
    public long getEvictionCount() { return evictionCount.get(); }

    public double getHitRate() {
        long total = hitCount.get() + missCount.get();
        return total == 0 ? 0.0 : (hitCount.get() * 100.0 / total);
    }

    // ── LRU list helpers ──────────────────────────────────────

    private void addToFront(Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToFront(Node node) {
        removeNode(node);
        addToFront(node);
    }

    private void evictLRU() {
        Node lru = tail.prev;
        if (lru == head) return;
        removeNode(lru);
        map.remove(lru.key);
        evictionCount.incrementAndGet();
    }

    // ── Passive TTL eviction ──────────────────────────────────

    private synchronized void evictExpired() {
        Iterator<Map.Entry<String, Node>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Node> e = it.next();
            if (e.getValue().entry.isExpired()) {
                removeNode(e.getValue());
                it.remove();
            }
        }
    }

    /** Call on shutdown */
    public void shutdown() {
        expirer.shutdownNow();
    }
}