package com.cache.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedded H2 JDBC logger.
 *
 * FIX: Added connection-null guard with a loud error message so startup
 * failures are immediately visible in the console.
 */
public class CacheLogger {

    // ── Singleton ─────────────────────────────────────────────
    private static CacheLogger instance;

    public static synchronized CacheLogger getInstance() {
        if (instance == null) instance = new CacheLogger();
        return instance;
    }

    // ── JDBC ──────────────────────────────────────────────────
    private static final String JDBC_URL =
        "jdbc:h2:mem:cache_log;DB_CLOSE_DELAY=-1;MODE=MySQL";

    private Connection conn;

    private CacheLogger() {
        try {
            Class.forName("org.h2.Driver");
            conn = DriverManager.getConnection(JDBC_URL, "sa", "");
            createTable();
            System.out.println("[CacheLogger] H2 in-memory DB initialised OK.");
        } catch (Exception e) {
            System.err.println("[CacheLogger] INIT FAILED — H2 jar missing from classpath? " + e.getMessage());
            conn = null;
        }
    }

    private void createTable() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS cache_ops (
                    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                    op          VARCHAR(20)   NOT NULL,
                    cache_key   VARCHAR(512)  NOT NULL,
                    cache_value VARCHAR(2048),
                    duration_ms BIGINT        NOT NULL,
                    hit         BOOLEAN       DEFAULT FALSE,
                    ts          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
                )
            """);
        }
    }

    // ── Public API ────────────────────────────────────────────

    /**
     * Record a completed cache operation.
     *
     * @param op         operation name  (SET, GET, DEL, INCR, …)
     * @param key        cache key
     * @param value      result value (may be null)
     * @param durationMs wall-clock time in ms
     * @param hit        true only when a GET returns a value
     */
    public void log(String op, String key, String value,
                    long durationMs, boolean hit) {
        if (conn == null) {
            System.err.println("[CacheLogger] Cannot log — connection is null.");
            return;
        }
        String sql = "INSERT INTO cache_ops(op,cache_key,cache_value,duration_ms,hit) " +
                     "VALUES(?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString (1, op);
            ps.setString (2, key);
            ps.setString (3, value == null ? "" : value);
            ps.setLong   (4, durationMs);
            ps.setBoolean(5, hit);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[CacheLogger] Insert failed: " + e.getMessage());
        }
    }

    /**
     * Returns the last {@code limit} log rows, newest first.
     * Row keys: id, op, key, value, duration_ms, hit, ts
     */
    public List<Map<String, Object>> getRecentLogs(int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (conn == null) return rows;
        String sql = "SELECT id,op,cache_key,cache_value,duration_ms,hit,ts " +
                     "FROM cache_ops ORDER BY id DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id",          rs.getLong     ("id"));
                row.put("op",          rs.getString   ("op"));
                row.put("key",         rs.getString   ("cache_key"));
                row.put("value",       rs.getString   ("cache_value"));
                row.put("duration_ms", rs.getLong     ("duration_ms"));
                row.put("hit",         rs.getBoolean  ("hit"));
                row.put("ts",          rs.getTimestamp("ts").toString());
                rows.add(row);
            }
        } catch (SQLException e) {
            System.err.println("[CacheLogger] getRecentLogs failed: " + e.getMessage());
        }
        return rows;
    }

    /** Average latency (ms) over the last {@code n} operations. */
    public double avgLatency(int n) {
        if (conn == null) return 0;
        String sql = "SELECT AVG(duration_ms) FROM " +
                     "(SELECT duration_ms FROM cache_ops ORDER BY id DESC LIMIT ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, n);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            System.err.println("[CacheLogger] avgLatency failed: " + e.getMessage());
        }
        return 0;
    }

    /** Total operations ever logged in this session. */
    public long totalOps() {
        if (conn == null) return 0;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM cache_ops")) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException ignored) {}
        return 0;
    }

    /** Call on application shutdown to release the H2 connection. */
    public void close() {
        try {
            if (conn != null) conn.close();
        } catch (SQLException ignored) {}
    }
}