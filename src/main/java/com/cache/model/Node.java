package com.cache.model;

public class Node {

    public String     key;
    public CacheEntry entry;
    public Node       prev;
    public Node       next;

    public Node(String key, CacheEntry entry) {
        this.key   = key;
        this.entry = entry;
    }

    @Override
    public String toString() {
        return "Node{key='" + key + "'}";
    }
}