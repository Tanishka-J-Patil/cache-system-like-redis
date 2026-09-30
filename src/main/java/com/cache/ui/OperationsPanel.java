package com.cache.ui;

import com.cache.core.RedisCache;
import com.cache.db.CacheLogger;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * OperationsPanel — cache CRUD UI + live latency feedback.
 *
 * FIXES APPLIED:
 *  1. doGet() was double-logging (once in timed(), once manually).
 *     Now GET is timed manually so the hit/miss flag is correct in ONE log entry.
 *  2. All other ops still use timed() which logs exactly once — correct.
 *  3. updateSpeed() is always called after every operation.
 */
public class OperationsPanel extends JPanel {

    private final RedisCache  cache;
    private final Runnable    onUpdate;
    private final CacheLogger logger = CacheLogger.getInstance();

    // ── Form fields ───────────────────────────────────────────
    private final JTextField keyField   = new JTextField();
    private final JTextField valueField = new JTextField();
    private final JTextField ttlField   = new JTextField("0");
    private final JTextField incrField  = new JTextField("1");

    // ── Status + speed bar ────────────────────────────────────
    private final JLabel        statusLbl = new JLabel(" ");
    private final JLabel        speedLbl  = new JLabel("Speed: —");
    private final JLabel        speedDot  = new JLabel("●");
    private final JProgressBar  speedBar  = new JProgressBar(0, 100);

    // ── Cache contents table ──────────────────────────────────
    private DefaultTableModel tableModel;
    private JTable            table;

    public OperationsPanel(RedisCache cache, Runnable onUpdate) {
        this.cache    = cache;
        this.onUpdate = onUpdate;

        setLayout(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(5, 5, 5, 5));

        add(buildFormPanel(),  BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);

        // Seed some demo data so the table isn't empty on first open
        timed(() -> cache.set("user:101", "Alice"),               "SET",   "user:101",   "Alice",     false);
        timed(() -> cache.setex("session:abc", "token_xyz", 120), "SETEX", "session:abc","token_xyz", false);
        timed(() -> cache.set("counter", "0"),                    "SET",   "counter",    "0",         false);
        timed(() -> cache.incr("counter"),                        "INCR",  "counter",    null,        false);
        timed(() -> cache.incr("counter"),                        "INCR",  "counter",    null,        false);
        refreshTable();
    }

    // ── Form builder ──────────────────────────────────────────

    private JPanel buildFormPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder(null, " ⚙️ Cache Operations",
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("SansSerif", Font.BOLD, 13)));

        panel.add(formRow("Key",               keyField));
        panel.add(formRow("Value",             valueField));
        panel.add(formRow("TTL (sec, 0=none)", ttlField));
        panel.add(formRow("Incr/Decr by",      incrField));
        panel.add(Box.createVerticalStrut(8));
        panel.add(buildButtonRow1());
        panel.add(Box.createVerticalStrut(4));
        panel.add(buildButtonRow2());
        panel.add(Box.createVerticalStrut(8));
        panel.add(buildSpeedPanel());
        panel.add(Box.createVerticalStrut(4));

        statusLbl.setFont(new Font("SansSerif", Font.ITALIC, 12));
        statusLbl.setForeground(new Color(52, 152, 219));
        statusLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLbl.setBorder(new EmptyBorder(0, 6, 0, 0));
        panel.add(statusLbl);

        return panel;
    }

    private JPanel buildSpeedPanel() {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setBorder(new EmptyBorder(0, 6, 0, 6));
        row.setOpaque(false);

        speedDot.setFont(new Font("SansSerif", Font.PLAIN, 16));
        speedDot.setForeground(Color.GRAY);

        speedLbl.setFont(new Font("Monospaced", Font.BOLD, 11));
        speedLbl.setForeground(new Color(44, 44, 44));

        speedBar.setStringPainted(false);
        speedBar.setForeground(new Color(39, 174, 96));
        speedBar.setBackground(new Color(220, 220, 220));
        speedBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        speedBar.setBorderPainted(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.add(speedDot);
        left.add(speedLbl);

        row.add(left,     BorderLayout.WEST);
        row.add(speedBar, BorderLayout.CENTER);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        wrapper.setBorder(new TitledBorder(null, " ⚡ Operation Speed",
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("SansSerif", Font.BOLD, 11)));
        wrapper.setOpaque(false);
        wrapper.add(row, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel formRow(String label, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        row.setOpaque(false);
        JLabel lbl = new JLabel(label + ":");
        lbl.setPreferredSize(new Dimension(130, 24));
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        row.add(lbl,   BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        row.setBorder(new EmptyBorder(2, 6, 2, 6));
        return row;
    }

    private JPanel buildButtonRow1() {
        JPanel row = new JPanel(new GridLayout(1, 3, 6, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        row.setBorder(new EmptyBorder(0, 6, 0, 6));
        row.setOpaque(false);
        row.add(makeButton("SET",    new Color(39, 174, 96),  this::doSet));
        row.add(makeButton("GET",    new Color(52, 152, 219), this::doGet));
        row.add(makeButton("DELETE", new Color(231, 76, 60),  this::doDelete));
        return row;
    }

    private JPanel buildButtonRow2() {
        JPanel row = new JPanel(new GridLayout(1, 4, 6, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        row.setBorder(new EmptyBorder(0, 6, 0, 6));
        row.setOpaque(false);
        row.add(makeButton("INCR",      new Color(155, 89, 182), this::doIncr));
        row.add(makeButton("DECR",      new Color(230, 126, 34), this::doDecr));
        row.add(makeButton("TTL",       new Color(26, 188, 156), this::doTTL));
        row.add(makeButton("FLUSH ALL", new Color(127, 140, 141),this::doFlush));
        return row;
    }

    // ── Table builder ─────────────────────────────────────────

    private JScrollPane buildTablePanel() {
        String[] cols = {"Key", "Value", "TTL (s)", "Accesses"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(tableModel);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.setRowHeight(22);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Click row → fill key field
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() >= 0)
                keyField.setText((String) tableModel.getValueAt(table.getSelectedRow(), 0));
        });

        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(60);
        table.getColumnModel().getColumn(3).setPreferredWidth(60);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new TitledBorder(null, " 🗂️ Cache Contents",
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("SansSerif", Font.BOLD, 13)));
        return scroll;
    }

    // ── Operations ────────────────────────────────────────────

    private void doSet() {
        String key = keyField.getText().trim();
        String val = valueField.getText().trim();
        if (key.isEmpty() || val.isEmpty()) { status("⚠ Key and Value required", -1); return; }
        long ttl = parseLong(ttlField.getText().trim(), 0);
        if (ttl > 0) {
            long ms = timed(() -> cache.setex(key, val, ttl), "SETEX", key, val, false);
            status("✅ SETEX " + key + " (TTL=" + ttl + "s)", ms);
        } else {
            long ms = timed(() -> cache.set(key, val), "SET", key, val, false);
            status("✅ SET " + key, ms);
        }
        onUpdate.run();
    }

    /**
     * FIX: previously timed() logged once (hit=false) and then logger.log()
     * was called a second time with the correct hit flag — causing two rows
     * per GET in the DB, and the first row always showing hit=false.
     *
     * Now we time manually so we can pass the correct hit flag in a single
     * logger.log() call.
     */
    private void doGet() {
        String key = keyField.getText().trim();
        if (key.isEmpty()) { status("⚠ Key required", -1); return; }

        // Time the operation manually
        long   start  = System.nanoTime();
        String result = cache.get(key);
        long   ms     = (System.nanoTime() - start) / 1_000_000;

        boolean hit = (result != null);

        // Single log entry with the correct hit flag
        logger.log("GET", key, hit ? result : null, ms, hit);
        updateSpeed(ms);

        if (hit) {
            valueField.setText(result);
            status("✅ GET " + key + " → " + result + "  (HIT)", ms);
        } else {
            status("❌ GET " + key + " → (nil)  MISS", ms);
        }
        onUpdate.run();
    }

    private void doDelete() {
        String key = keyField.getText().trim();
        if (key.isEmpty()) { status("⚠ Key required", -1); return; }
        final boolean[] deleted = {false};
        long ms = timed(() -> deleted[0] = cache.del(key), "DEL", key, null, false);
        status(deleted[0] ? "✅ DEL " + key : "❌ Key not found: " + key, ms);
        onUpdate.run();
    }

    private void doIncr() {
        String key = keyField.getText().trim();
        if (key.isEmpty()) { status("⚠ Key required", -1); return; }
        long by = parseLong(incrField.getText().trim(), 1);
        final long[] result = {0};
        try {
            long ms = timed(() -> result[0] = cache.incrby(key, by), "INCRBY", key, null, false);
            status("✅ INCRBY " + key + " " + by + " → " + result[0], ms);
        } catch (Exception e) {
            status("❌ Value is not a number", -1);
        }
        onUpdate.run();
    }

    private void doDecr() {
        String key = keyField.getText().trim();
        if (key.isEmpty()) { status("⚠ Key required", -1); return; }
        long by = parseLong(incrField.getText().trim(), 1);
        final long[] result = {0};
        try {
            long ms = timed(() -> result[0] = cache.decrby(key, by), "DECRBY", key, null, false);
            status("✅ DECRBY " + key + " " + by + " → " + result[0], ms);
        } catch (Exception e) {
            status("❌ Value is not a number", -1);
        }
        onUpdate.run();
    }

    private void doTTL() {
        String key = keyField.getText().trim();
        if (key.isEmpty()) { status("⚠ Key required", -1); return; }
        final long[] ttl = {0};
        long ms = timed(() -> ttl[0] = cache.ttl(key), "TTL", key, null, false);
        if      (ttl[0] == -2) status("❌ Key does not exist: " + key, ms);
        else if (ttl[0] == -1) status("ℹ️ Key has no expiry: " + key, ms);
        else                   status("⏱ TTL for " + key + " = " + ttl[0] + "s", ms);
        onUpdate.run();
    }

    private void doFlush() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to clear the entire cache?",
            "Confirm FLUSH ALL", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            long ms = timed(cache::flushAll, "FLUSHALL", "*", null, false);
            status("🗑 FLUSH ALL — cache cleared", ms);
            onUpdate.run();
        }
    }

    // ── Table refresh ─────────────────────────────────────────

    public void refreshTable() {
        tableModel.setRowCount(0);
        List<Map<String, Object>> entries = cache.getAllEntries();
        for (Map<String, Object> e : entries) {
            tableModel.addRow(new Object[]{
                e.get("key"),
                e.get("value"),
                e.get("ttl"),
                e.get("accessCount")
            });
        }
    }

    // ── Timing helper ─────────────────────────────────────────

    /**
     * Run {@code action}, measure wall-clock time, write ONE row to H2,
     * update the speed indicator, and return the elapsed ms.
     *
     * Use this for all ops EXCEPT GET (which needs the hit flag).
     */
    private long timed(Runnable action, String op, String key,
                       String value, boolean hit) {
        long start = System.nanoTime();
        action.run();
        long ms = (System.nanoTime() - start) / 1_000_000;
        logger.log(op, key, value, ms, hit);
        updateSpeed(ms);
        return ms;
    }

    /**
     * Update the speed dot + label + progress bar.
     * Green ≤1 ms, yellow ≤5 ms, red >5 ms.
     */
    private void updateSpeed(long ms) {
        SwingUtilities.invokeLater(() -> {
            speedLbl.setText(String.format("Speed: %d ms  (avg: %.1f ms over last 20 ops)",
                ms, logger.avgLatency(20)));
            Color color;
            int   barVal;
            if (ms <= 1) {
                color  = new Color(39, 174, 96);
                barVal = 100;
            } else if (ms <= 5) {
                color  = new Color(241, 196, 15);
                barVal = 70;
            } else {
                color  = new Color(231, 76, 60);
                barVal = 30;
            }
            speedDot.setForeground(color);
            speedBar.setForeground(color);
            speedBar.setValue(barVal);
        });
    }

    // ── Status bar ────────────────────────────────────────────

    private void status(String msg, long ms) {
        String suffix = (ms >= 0) ? "  [" + ms + " ms]" : "";
        statusLbl.setText(msg + suffix);
    }

    // ── Misc helpers ──────────────────────────────────────────

    private long parseLong(String s, long fallback) {
        try   { return Long.parseLong(s); }
        catch (NumberFormatException e) { return fallback; }
    }

    private JButton makeButton(String text, Color bg, Runnable action) {
        JButton btn = new JButton(text) {
             @Override
            public void updateUI() {
                setUI(new javax.swing.plaf.basic.BasicButtonUI());   // ignore Windows skin
            }
            
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = bg;
                if (getModel().isPressed())        c = darken(bg, 0.35f);
                else if (getModel().isRollover())  c = darken(bg, 0.18f);
                g2.setColor(c);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setRolloverEnabled(true);
        btn.setBorder(new EmptyBorder(5, 8, 5, 8));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> action.run());
        return btn;
    }

    private static Color lighten(Color c, float amount) {
        int r = Math.min(255, (int) (c.getRed()   + (255 - c.getRed())   * amount));
        int g = Math.min(255, (int) (c.getGreen() + (255 - c.getGreen()) * amount));
        int b = Math.min(255, (int) (c.getBlue()  + (255 - c.getBlue())  * amount));
        return new Color(r, g, b);
    }

    private static Color darken(Color c, float amount) {
        int r = Math.max(0, (int) (c.getRed()   * (1 - amount)));
        int g = Math.max(0, (int) (c.getGreen() * (1 - amount)));
        int b = Math.max(0, (int) (c.getBlue()  * (1 - amount)));
        return new Color(r, g, b);
    }
}