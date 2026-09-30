package com.cache.ui;

import com.cache.core.RedisCache;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

public class StatsPanel extends JPanel {

    private final RedisCache cache;

    private final JLabel keysValue    = makeValueLabel("0");
    private final JLabel hitsValue    = makeValueLabel("0");
    private final JLabel missesValue  = makeValueLabel("0");
    private final JLabel hitRateValue = makeValueLabel("0%");
    private final JLabel evictValue   = makeValueLabel("0");
    private final JLabel memValue     = makeValueLabel("0 B");

    public StatsPanel(RedisCache cache) {
        this.cache = cache;
        setLayout(new BorderLayout(0, 8));
        setBorder(new CompoundBorder(
            new TitledBorder(null, " 📊 Live Statistics",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 13)),
            new EmptyBorder(8, 8, 8, 8)
        ));

        JPanel grid = new JPanel(new GridLayout(2, 3, 10, 10));
        grid.setOpaque(false);
        grid.add(makeCard("Keys Stored",  keysValue,    new Color(52, 152, 219)));
        grid.add(makeCard("Cache Hits",   hitsValue,    new Color(39, 174, 96)));
        grid.add(makeCard("Cache Misses", missesValue,  new Color(231, 76, 60)));
        grid.add(makeCard("Hit Rate",     hitRateValue, new Color(155, 89, 182)));
        grid.add(makeCard("Evictions",    evictValue,   new Color(230, 126, 34)));
        grid.add(makeCard("Est. Memory",  memValue,     new Color(52, 73, 94)));
        add(grid, BorderLayout.CENTER);
    }

    public void refresh() {
        keysValue.setText(String.valueOf(cache.dbsize()));
        hitsValue.setText(String.valueOf(cache.getHitCount()));
        missesValue.setText(String.valueOf(cache.getMissCount()));

        double rate = cache.getHitRate();
        hitRateValue.setText(String.format("%.1f%%", rate));
        hitRateValue.setForeground(rate >= 70
            ? new Color(39, 174, 96) : new Color(231, 76, 60));

        evictValue.setText(String.valueOf(cache.getEvictionCount()));

        long bytes = cache.dbsize() * 200L;
        memValue.setText(bytes < 1024 ? bytes + " B" : (bytes / 1024) + " KB");
    }

    private JPanel makeCard(String title, JLabel valueLabel, Color accent) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(new Color(245, 247, 250));
        card.setBorder(new CompoundBorder(
            new LineBorder(accent, 2, true),
            new EmptyBorder(8, 10, 8, 10)
        ));
        JLabel titleLbl = new JLabel(title, SwingConstants.CENTER);
        titleLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        titleLbl.setForeground(Color.GRAY);
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);
        valueLabel.setForeground(accent);
        card.add(titleLbl,    BorderLayout.NORTH);
        card.add(valueLabel,  BorderLayout.CENTER);
        return card;
    }

    private static JLabel makeValueLabel(String initial) {
        JLabel lbl = new JLabel(initial, SwingConstants.CENTER);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 22));
        return lbl;
    }
}