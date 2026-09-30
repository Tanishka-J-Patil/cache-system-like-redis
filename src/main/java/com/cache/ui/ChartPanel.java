package com.cache.ui;

import com.cache.core.RedisCache;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.LinkedList;

public class ChartPanel extends JPanel {

    private final RedisCache cache;

    private final LinkedList<Double>  hitRateHistory = new LinkedList<>();
    private final LinkedList<Integer> keysHistory    = new LinkedList<>();
    private static final int MAX_POINTS = 30;

    private final GraphCanvas graph;

    public ChartPanel(RedisCache cache) {
        this.cache = cache;
        setLayout(new BorderLayout());
        setBorder(new CompoundBorder(
            new TitledBorder(null, " 📈 Live Charts (last 30s)",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 13)),
            new EmptyBorder(8, 8, 8, 8)
        ));

        graph = new GraphCanvas();
        add(graph, BorderLayout.CENTER);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 2));
        legend.setOpaque(false);
        legend.add(legendDot(new Color(39, 174, 96),  "Hit Rate (%)"));
        legend.add(legendDot(new Color(52, 152, 219), "Keys Count"));
        add(legend, BorderLayout.SOUTH);
    }

    public void refresh() {
        hitRateHistory.addLast(cache.getHitRate());
        keysHistory.addLast(cache.dbsize());
        if (hitRateHistory.size() > MAX_POINTS) hitRateHistory.removeFirst();
        if (keysHistory.size()    > MAX_POINTS) keysHistory.removeFirst();
        graph.repaint();
    }

    // ── Canvas ────────────────────────────────────────────────

    private class GraphCanvas extends JPanel {

        GraphCanvas() {
            setBackground(new Color(250, 252, 255));
            setBorder(new LineBorder(new Color(220, 220, 220)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (hitRateHistory.size() < 2) return;

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth()  - 40;
            int h = getHeight() - 30;
            int ox = 30, oy = 10;

            // Grid lines
            g2.setColor(new Color(230, 230, 230));
            g2.setStroke(new BasicStroke(0.5f));
            for (int i = 0; i <= 4; i++) {
                int y = oy + (h - (h * i / 4));
                g2.drawLine(ox, y, ox + w, y);
                g2.setColor(Color.GRAY);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                g2.drawString(String.valueOf(25 * i), 2, y + 4);
                g2.setColor(new Color(230, 230, 230));
            }

            drawLine(g2, hitRateHistory, 100.0, w, h, ox, oy,
                new Color(39, 174, 96), 2.5f);

            int maxKeys = keysHistory.stream().mapToInt(Integer::intValue).max().orElse(1);
            double keysScale = Math.max(maxKeys, 10);
            LinkedList<Double> keysAsDouble = new LinkedList<>();
            keysHistory.forEach(k -> keysAsDouble.add((double) k));
            drawLine(g2, keysAsDouble, keysScale, w, h, ox, oy,
                new Color(52, 152, 219), 2.0f);
        }

        private void drawLine(Graphics2D g2, LinkedList<Double> data,
                              double maxVal, int w, int h,
                              int ox, int oy, Color color, float stroke) {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            Double[] arr = data.toArray(new Double[0]);
            int n = arr.length;
            int[] xs = new int[n];
            int[] ys = new int[n];

            for (int i = 0; i < n; i++) {
                xs[i] = ox + (int) ((double) i / (MAX_POINTS - 1) * w);
                ys[i] = oy + h - (int) (arr[i] / maxVal * h);
            }
            for (int i = 0; i < n - 1; i++)
                g2.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);

            g2.fillOval(xs[n - 1] - 4, ys[n - 1] - 4, 8, 8);
        }
    }

    private JPanel legendDot(Color color, String label) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        JLabel dot = new JLabel("●");
        dot.setForeground(color);
        p.add(dot);
        p.add(new JLabel(label));
        return p;
    }
}