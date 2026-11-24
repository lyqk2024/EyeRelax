package com.eye.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** Persisted EyeRelax preferences. New duration values are stored in seconds. */
public class Config {
    public volatile String language = "zh-CN";
    public enum ForceMode { A_SOFT, B_STRONG, C_ULTRA }
    public enum CloseAction { ASK, MINIMIZE_TO_TRAY, EXIT }
    public enum TimeInputMode { SEGMENTED, WHEEL }
    public enum PlanSelectionAction { SELECT_ONLY, SELECT_AND_START }
    public enum HistoryDateInput { CALENDAR, MANUAL }

    public volatile int workSeconds = 45 * 60;
    public volatile int relaxSeconds = 5 * 60 + 1;
    public volatile ForceMode mode = ForceMode.B_STRONG;
    public volatile boolean adminExtrasEnabled = false;

    // Time / plan behavior preferences.
    public volatile boolean showPlanChooserOnLaunch = false;
    public volatile boolean autoStartAfterPlanSelection = false;
    public volatile boolean autoContinueCycles = true;
    public volatile boolean showTaskCompletionNotification = true;
    /** Default forced reminder overlay duration. Per-reminder values may override it; minimum is 5 seconds. */
    public volatile int reminderDefaultMaskSeconds = 60;

    // UI / interaction preferences.
    public volatile boolean sidebarCollapsed = false;
    public volatile boolean showStatusOverlay = true;
    public volatile boolean overlayShowSwitchButton = true;
    public volatile boolean overlayShowButtonText = false;
    public volatile CloseAction closeAction = CloseAction.MINIMIZE_TO_TRAY;
    public volatile TimeInputMode timeInputMode = TimeInputMode.WHEEL;

    public volatile PlanSelectionAction planManagementSelectionAction = PlanSelectionAction.SELECT_ONLY;
    public volatile HistoryDateInput historyDateInput = HistoryDateInput.CALENDAR;
    public volatile boolean launchAtLogin = Boolean.parseBoolean(System.getProperty("eyerelax.defaultAutostart", "true"));
    public volatile boolean updateAutoCheck = true;
    public volatile boolean updateAutoRequest = false;
    public volatile String updateFeedUrl = "";
    public volatile boolean usageGuardEnabled = false;
    public volatile String usageGuardAccountSid = "";
    public volatile boolean dailyUsageLimitEnabled = false;
    public volatile int dailyUsageLimitSeconds = 8 * 3600;
    public volatile boolean usageWindowEnabled = false;
    public volatile int usageWindowStartMinute = 8 * 60;
    public volatile int usageWindowEndMinute = 23 * 60;
    public volatile String applicationLimits = "{}";
    public boolean hasApplicationLimits() { return ApplicationLimitRule.parse(applicationLimits).values().stream().anyMatch(ApplicationLimitRule::enabled); }
    public boolean hasUsageLimits() { return dailyUsageLimitEnabled || usageWindowEnabled; }

    // Logical-day / recovery policy. 04:00 means 00:00-03:59 belongs to the previous study day.
    public volatile int dayBoundarySeconds = StudyDay.DEFAULT_BOUNDARY_SECONDS;
    public volatile boolean resetAfterLongPause = true;
    public volatile int longPauseResetSeconds = 60 * 60;

    public volatile boolean overlayCustomPosition = true;
    public volatile int overlayX = 1344;
    public volatile int overlayY = 95;

    private static Path configPath() { return DataPaths.file("config.properties"); }

    public void loadIfExists() {
        Properties p = new Properties();
        try {
            p = com.eye.sync.LocalStrictSettings.get().recover(AtomicProperties.read(configPath()));

            // v2 stores seconds. Fall back to the v1 minute properties so existing users migrate in place.
            this.workSeconds = parsePositiveInt(
                    p.getProperty("workSeconds"),
                    parsePositiveInt(p.getProperty("workMinutes"), Math.max(1, this.workSeconds / 60)) * 60
            );
            this.relaxSeconds = parsePositiveInt(
                    p.getProperty("relaxSeconds"),
                    parsePositiveInt(p.getProperty("relaxMinutes"), Math.max(1, this.relaxSeconds / 60)) * 60
            );

            String m = p.getProperty("mode");
            if (m != null) {
                try { this.mode = ForceMode.valueOf(m); } catch (IllegalArgumentException ignored) {}
            }

            this.adminExtrasEnabled = Boolean.parseBoolean(p.getProperty("adminExtrasEnabled", "false"));
            this.showPlanChooserOnLaunch = Boolean.parseBoolean(p.getProperty("showPlanChooserOnLaunch", String.valueOf(this.showPlanChooserOnLaunch)));
            this.autoStartAfterPlanSelection = Boolean.parseBoolean(p.getProperty("autoStartAfterPlanSelection", String.valueOf(this.autoStartAfterPlanSelection)));
            this.autoContinueCycles = Boolean.parseBoolean(p.getProperty("autoContinueCycles", String.valueOf(this.autoContinueCycles)));
            this.showTaskCompletionNotification = Boolean.parseBoolean(p.getProperty("showTaskCompletionNotification", String.valueOf(this.showTaskCompletionNotification)));
            this.reminderDefaultMaskSeconds = Math.max(5, parsePositiveInt(p.getProperty("reminderDefaultMaskSeconds"), this.reminderDefaultMaskSeconds));

            this.sidebarCollapsed = Boolean.parseBoolean(p.getProperty("sidebarCollapsed", "false"));
            this.showStatusOverlay = Boolean.parseBoolean(p.getProperty("showStatusOverlay", String.valueOf(this.showStatusOverlay)));
            this.overlayShowSwitchButton = Boolean.parseBoolean(p.getProperty("overlayShowSwitchButton", String.valueOf(this.overlayShowSwitchButton)));
            this.overlayShowButtonText = Boolean.parseBoolean(p.getProperty("overlayShowButtonText", String.valueOf(this.overlayShowButtonText)));
            language = "en".equals(p.getProperty("language")) ? "en" : "zh-CN";
            String inputMode = p.getProperty("timeInputMode");
            if (inputMode != null) {
                try { this.timeInputMode = TimeInputMode.valueOf(inputMode); } catch (IllegalArgumentException ignored) {}
            }

            String close = p.getProperty("closeAction");
            if (close != null) {
                try { this.closeAction = CloseAction.valueOf(close); } catch (IllegalArgumentException ignored) {}
            }

            try { planManagementSelectionAction = PlanSelectionAction.valueOf(p.getProperty("planManagementSelectionAction", planManagementSelectionAction.name())); } catch (IllegalArgumentException ignored) {}
            try { historyDateInput = HistoryDateInput.valueOf(p.getProperty("historyDateInput", historyDateInput.name())); } catch (IllegalArgumentException ignored) {}
            launchAtLogin = Boolean.parseBoolean(p.getProperty("launchAtLogin", String.valueOf(launchAtLogin)));
            updateAutoCheck=Boolean.parseBoolean(p.getProperty("updateAutoCheck","true"));
            updateAutoRequest=Boolean.parseBoolean(p.getProperty("updateAutoRequest","false"));
            updateFeedUrl=p.getProperty("updateFeedUrl","").trim();
            usageGuardEnabled=Boolean.parseBoolean(p.getProperty("usageGuardEnabled","false"));
            usageGuardAccountSid=p.getProperty("usageGuardAccountSid","");
            dailyUsageLimitEnabled = Boolean.parseBoolean(p.getProperty("dailyUsageLimitEnabled", "false"));
            dailyUsageLimitSeconds = Math.max(60, Math.min(86400, parsePositiveInt(p.getProperty("dailyUsageLimitSeconds"), dailyUsageLimitSeconds)));
            usageWindowEnabled = Boolean.parseBoolean(p.getProperty("usageWindowEnabled", "false"));
            usageWindowStartMinute = Math.max(0, Math.min(1439, parseInt(p.getProperty("usageWindowStartMinute"), usageWindowStartMinute)));
            usageWindowEndMinute = Math.max(0, Math.min(1439, parseInt(p.getProperty("usageWindowEndMinute"), usageWindowEndMinute)));
            applicationLimits = p.getProperty("applicationLimits", "{}");
            ApplicationLimitRule.parse(applicationLimits);
            dayBoundarySeconds = StudyDay.sanitizeBoundarySeconds(parseInt(p.getProperty("dayBoundarySeconds"), dayBoundarySeconds));
            resetAfterLongPause = Boolean.parseBoolean(p.getProperty("resetAfterLongPause", String.valueOf(resetAfterLongPause)));
            longPauseResetSeconds = Math.max(60, parsePositiveInt(p.getProperty("longPauseResetSeconds"), longPauseResetSeconds));

            this.overlayCustomPosition = Boolean.parseBoolean(p.getProperty("overlayCustomPosition", String.valueOf(this.overlayCustomPosition)));
            this.overlayX = parseInt(p.getProperty("overlayX"), this.overlayX);
            this.overlayY = parseInt(p.getProperty("overlayY"), this.overlayY);
        } catch (IOException ignored) {}
    }

    public boolean save() {
        if (!com.eye.sync.StrictSettings.allow(this)) { loadIfExists(); return false; }
        try {
            Files.createDirectories(configPath().getParent());
            Properties p = new Properties();
            p.setProperty("workSeconds", String.valueOf(workSeconds));
            p.setProperty("relaxSeconds", String.valueOf(relaxSeconds));
            // Keep legacy minute values readable by older releases without sacrificing the new second values.
            p.setProperty("workMinutes", String.valueOf(Math.max(1, (workSeconds + 59) / 60)));
            p.setProperty("relaxMinutes", String.valueOf(Math.max(1, (relaxSeconds + 59) / 60)));
            p.setProperty("mode", mode.name());
            p.setProperty("adminExtrasEnabled", String.valueOf(adminExtrasEnabled));

            p.setProperty("showPlanChooserOnLaunch", String.valueOf(showPlanChooserOnLaunch));
            p.setProperty("autoStartAfterPlanSelection", String.valueOf(autoStartAfterPlanSelection));
            p.setProperty("autoContinueCycles", String.valueOf(autoContinueCycles));
            p.setProperty("showTaskCompletionNotification", String.valueOf(showTaskCompletionNotification));
            p.setProperty("reminderDefaultMaskSeconds", String.valueOf(Math.max(5, reminderDefaultMaskSeconds)));

            p.setProperty("sidebarCollapsed", String.valueOf(sidebarCollapsed));
            p.setProperty("showStatusOverlay", String.valueOf(showStatusOverlay));
            p.setProperty("overlayShowSwitchButton", String.valueOf(overlayShowSwitchButton));
            p.setProperty("overlayShowButtonText", String.valueOf(overlayShowButtonText));
            p.setProperty("closeAction", closeAction.name());
            p.setProperty("language",language);
            p.setProperty("timeInputMode", timeInputMode.name());
            p.setProperty("planManagementSelectionAction", planManagementSelectionAction.name());
            p.setProperty("historyDateInput", historyDateInput.name());
            p.setProperty("launchAtLogin", String.valueOf(launchAtLogin));
            p.setProperty("updateAutoCheck",String.valueOf(updateAutoCheck));
            p.setProperty("updateAutoRequest",String.valueOf(updateAutoRequest));
            p.setProperty("updateFeedUrl",updateFeedUrl);
            p.setProperty("usageGuardEnabled",String.valueOf(usageGuardEnabled));
            p.setProperty("usageGuardAccountSid",usageGuardAccountSid);
            p.setProperty("dailyUsageLimitEnabled", String.valueOf(dailyUsageLimitEnabled));
            p.setProperty("dailyUsageLimitSeconds", String.valueOf(dailyUsageLimitSeconds));
            p.setProperty("usageWindowEnabled", String.valueOf(usageWindowEnabled));
            p.setProperty("usageWindowStartMinute", String.valueOf(usageWindowStartMinute));
            p.setProperty("usageWindowEndMinute", String.valueOf(usageWindowEndMinute));
            p.setProperty("applicationLimits", applicationLimits);
            p.setProperty("dayBoundarySeconds", String.valueOf(StudyDay.sanitizeBoundarySeconds(dayBoundarySeconds)));
            p.setProperty("resetAfterLongPause", String.valueOf(resetAfterLongPause));
            p.setProperty("longPauseResetSeconds", String.valueOf(Math.max(60, longPauseResetSeconds)));
            p.setProperty("overlayCustomPosition", String.valueOf(overlayCustomPosition));
            p.setProperty("overlayX", String.valueOf(overlayX));
            p.setProperty("overlayY", String.valueOf(overlayY));

            com.eye.sync.LocalStrictSettings.get().accepted(p);
            AtomicProperties.write(configPath(), p);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static int parsePositiveInt(String s, int fallback) {
        if (s == null) return fallback;
        try {
            int v = Integer.parseInt(s.trim());
            return v > 0 ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(String s, int fallback) {
        if (s == null) return fallback;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
