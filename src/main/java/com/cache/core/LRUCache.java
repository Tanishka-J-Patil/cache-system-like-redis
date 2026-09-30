package com.cache.core;

import com.cache.model.CacheEntry;
import com.cache.model.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LRUCache {

    private final Map<String, Node> cache;
    private final Node head;
    private final Node tail;
    private final int capacity;
    private int size;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private long hitCount = 0;
    private long missCount = 0;
    private long evictionCount = 0;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.size = 0;
        this.head = new Node(null, null);
        this.tail = new Node(null, null);
        head.next = tail;
        tail.prev = head;
    }

    public CacheEntry get(String key) {
        lock.readLock().lock();
        try {
            Node node = cache.get(key);

            if (node == null) {
                missCount++;
                System.out.println("❌ MISS: " + key);
                return null;
            }

            if (node.entry.isExpired()) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    removeNode(node);
                    cache.remove(key);
                    size--;
                    missCount++;
                    System.out.println("⏰ EXPIRED: " + key);
                    return null;
                } finally {
                    lock.readLock().lock();
                    lock.writeLock().unlock();
                }
            }

            hitCount++;
            System.out.println("✅ HIT: " + key);
            node.entry.markAccessed();

            lock.readLock().unlock();
            lock.writeLock().lock();
            try {
                moveToHead(node);
                return node.entry;
            } finally {
                lock.readLock().lock();
                lock.writeLock().unlock();
            }

        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(String key, CacheEntry entry) {
        lock.writeLock().lock();
        try {
            Node node = cache.get(key);

            if (node != null) {
                node.entry = entry;
                moveToHead(node);
                System.out.println("🔄 UPDATE: " + key);
            } else {
                if (size >= capacity) {
                    evictLRU();
                }
                Node newNode = new Node(key, entry);
                cache.put(key, newNode);
                addToHead(newNode);
                size++;
                System.out.println("➕ ADD: " + key + " (size: " + size + "/" + capacity + ")");
            }

        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean delete(String key) {
        lock.writeLock().lock();
        try {
            Node node = cache.get(key);
            if (node == null) return false;
            removeNode(node);
            cache.remove(key);
            size--;
            System.out.println("🗑️ DELETE: " + key);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean exists(String key) {
        lock.readLock().lock();
        try {
            Node node = cache.get(key);
            if (node == null) return false;
            if (node.entry.isExpired()) return false;
            return true;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            head.next = tail;
            tail.prev = head;
            size = 0;
            System.out.println("🧹 CLEARED all entries");
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String, CacheEntry> getAll() {
        lock.readLock().lock();
        try {
            Map<String, CacheEntry> result = new HashMap<>();
            for (Map.Entry<String, Node> entry : cache.entrySet()) {
                if (!entry.getValue().entry.isExpired()) {
                    result.put(entry.getKey(), entry.getValue().entry);
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    // ── NEW: walk linked list for UI table ───────────────────
    public List<Node> getAllNodes() {
        lock.readLock().lock();
        try {
            List<Node> nodes = new ArrayList<>();
            Node current = head.next;
            while (current != tail) {
                nodes.add(current);
                current = current.next;
            }
            return nodes;
        } finally {
            lock.readLock().unlock();
        }
    }

    // ── Internal helpers ─────────────────────────────────────

    private void moveToHead(Node node) {
        removeNode(node);
        addToHead(node);
    }

    private void addToHead(Node node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void evictLRU() {
        Node lru = tail.prev;
        if (lru == head) return;
        removeNode(lru);
        cache.remove(lru.key);
        size--;
        evictionCount++;
        System.out.println("🔄 EVICTED (LRU): " + lru.key);
    }

    // ── Statistics ───────────────────────────────────────────

    public int size()                { return size; }
    public int capacity()            { return capacity; }
    public long getHitCount()        { return hitCount; }
    public long getMissCount()       { return missCount; }
    public long getEvictionCount()   { return evictionCount; }

    public double getHitRate() {
        long total = hitCount + missCount;
        if (total == 0) return 0;
        return (double) hitCount / total * 100;
    }

    public void printState() {
        System.out.println("\n========== CACHE STATE ==========");
        System.out.println("Size: " + size + "/" + capacity);
        System.out.println("Hit Rate: " + String.format("%.2f%%", getHitRate()));
        System.out.println("Hits: " + hitCount + " | Misses: " + missCount);
        System.out.println("Evictions: " + evictionCount);
        System.out.println("\nOrder (most → least recent):");
        Node current = head.next;
        int position = 1;
        while (current != tail) {
            System.out.println("  " + position + ". " + current.key +
                " (accessed: " + current.entry.getAccessCount() + " times)");
            current = current.next;
            position++;
        }
        System.out.println("=================================\n");
    }
}