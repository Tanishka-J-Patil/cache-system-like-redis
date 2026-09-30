package com.cache.ui;

import com.cache.db.CacheLogger;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * LogPanel — real-time view of the H2 operation log.
 *
 * Columns: # | Op | Key | Value | Duration (ms) | Hit? | Timestamp
 *
 * FIXES APPLIED:
 *  1. refresh() now guards against an empty result set — it won't wipe
 *     existing rows if nothing new has arrived (prevents blank flicker).
 *  2. Summary bar always shows something even before the first operation.
 *  3. Duration and Hit renderers are unchanged — they were correct.
 */
public class LogPanel extends JPanel {

    private static final int MAX_ROWS = 200;

    private final CacheLogger logger = CacheLogger.getInstance();

    private DefaultTableModel tableModel;
    private JTable            table;
    private final JLabel      summaryLbl = new JLabel("Waiting for operations…");

    public LogPanel() {
        setLayout(new BorderLayout(0, 4));
        setBorder(new TitledBorder(null, " 🗄️ JDBC Operation Log  (H2 in-memory DB)",
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("SansSerif", Font.BOLD, 13)));

        add(buildTable(),      BorderLayout.CENTER);
        add(buildSummaryBar(), BorderLayout.SOUTH);
    }

    // ── Table ─────────────────────────────────────────────────

    private JScrollPane buildTable() {
        String[] cols = {"#", "Op", "Key", "Value", "Duration (ms)", "Hit?", "Timestamp"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return (c == 0 || c == 4) ? Long.class : String.class;
            }
        };

        table = new JTable(tableModel);
        table.setFont(new Font("Monospaced", Font.PLAIN, 11));
        table.setRowHeight(20);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        int[] widths = {40, 70, 130, 130, 90, 55, 170};
        for (int i = 0; i < widths.length; i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        table.getColumnModel().getColumn(4).setCellRenderer(new DurationRenderer());
        table.getColumnModel().getColumn(5).setCellRenderer(new HitRenderer());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(0, 160));
        return scroll;
    }

    private JPanel buildSummaryBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(0, 4, 2, 4));
        summaryLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        summaryLbl.setForeground(new Color(80, 80, 80));
        bar.add(summaryLbl);
        return bar;
    }

    // ── Refresh ───────────────────────────────────────────────

    /**
     * Called every second by the dashboard timer.
     *
     * FIX: Previously the table was wiped even when getRecentLogs() returned
     * nothing, making the panel appear perpetually empty if H2 was not
     * initialised. Now we skip the wipe when the list is empty and show a
     * clear "no data" message instead.
     */
    public void refresh() {
        List<Map<String, Object>> rows = logger.getRecentLogs(MAX_ROWS);

        if (rows.isEmpty()) {
            // Don't clear existing rows — keep them visible
            long total = logger.totalOps();
            summaryLbl.setText(total == 0
                ? "No operations logged yet — perform a cache operation above."
                : "No recent ops found.  Total logged this session: " + total);
            return;
        }

        // Rebuild table from newest-first result set
        tableModel.setRowCount(0);
        for (Map<String, Object> r : rows) {
            tableModel.addRow(new Object[]{
                r.get("id"),
                r.get("op"),
                r.get("key"),
                r.get("value"),
                r.get("duration_ms"),
                Boolean.TRUE.equals(r.get("hit")) ? "HIT" : "—",
                r.get("ts")
            });
        }

        // Scroll to top so the newest entry is always visible
        if (tableModel.getRowCount() > 0)
            table.scrollRectToVisible(table.getCellRect(0, 0, true));

        // Summary bar
        double avg   = logger.avgLatency(50);
        long   total = logger.totalOps();
        summaryLbl.setText(String.format(
            "Total ops logged: %d   |   Avg latency (last 50): %.2f ms   |   Stored in: H2 in-memory (JDBC)",
            total, avg));
    }

    // ── Cell renderers ────────────────────────────────────────

    /** Green ≤1 ms · yellow ≤5 ms · red >5 ms */
    private static class DurationRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            setHorizontalAlignment(CENTER);
            if (v instanceof Long ms) {
                if (!sel) {
                    if      (ms <= 1) setForeground(new Color(27, 153, 73));
                    else if (ms <= 5) setForeground(new Color(180, 120, 0));
                    else              setForeground(new Color(192, 57, 43));
                }
                setText(ms + " ms");
            }
            return this;
        }
    }

    /** Green for HIT · grey for miss */
    private static class HitRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            setHorizontalAlignment(CENTER);
            if (!sel) {
                setForeground("HIT".equals(v)
                    ? new Color(27, 153, 73)
                    : new Color(150, 150, 150));
            }
            return this;
        }
    }
}