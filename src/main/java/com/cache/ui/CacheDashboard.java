package com.cache.ui;

import com.cache.core.RedisCache;
import com.cache.db.CacheLogger;
import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


public class CacheDashboard extends JFrame {

    private final RedisCache      cache;
    private final StatsPanel      statsPanel;
    private final OperationsPanel opsPanel;
    private final ChartPanel      chartPanel;
    private final LogPanel        logPanel;
    private       Timer           refreshTimer;

    public CacheDashboard() {
        cache = new RedisCache(100);

        setTitle("Redis-Like Cache System — Live Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 780);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (refreshTimer != null) refreshTimer.stop();
                CacheLogger.getInstance().close();
                cache.shutdown();
            }
        });

        statsPanel  = new StatsPanel(cache);
        opsPanel    = new OperationsPanel(cache, this::refreshAll);
        chartPanel  = new ChartPanel(cache);
        logPanel    = new LogPanel();

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            statsPanel, chartPanel);
        rightSplit.setResizeWeight(0.35);
        rightSplit.setDividerSize(5);

        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            opsPanel, rightSplit);
        topSplit.setResizeWeight(0.45);
        topSplit.setDividerSize(5);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            topSplit, logPanel);
        mainSplit.setResizeWeight(0.68);
        mainSplit.setDividerSize(6);

        setContentPane(mainSplit);

        // 1-second refresh tick
        refreshTimer = new Timer(1000, e -> refreshAll());
        refreshTimer.start();

        setVisible(true);
    }

    private void refreshAll() {
        statsPanel.refresh();
        opsPanel.refreshTable();
        chartPanel.refresh();
        logPanel.refresh();
    }
}