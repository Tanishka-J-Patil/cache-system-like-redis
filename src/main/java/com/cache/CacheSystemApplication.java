package com.cache;

import com.cache.ui.CacheDashboard;
import javax.swing.*;

public class CacheSystemApplication {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new CacheDashboard();
        });
    }
}