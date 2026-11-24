package com.eye.app;

import com.eye.core.Config;
import com.eye.core.DataPaths;
import com.eye.core.CycleEngine;
import com.eye.plan.PlanManager;
import com.eye.reminder.ReminderManager;
import com.eye.ui.TrayApp;
import com.eye.ui.theme.Theme;
import com.eye.win.StartupRegistration;

import javax.swing.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;
import com.eye.core.AtomicProperties;
import com.eye.core.ComputerUsageLimits;
import com.eye.core.UsageLimitMonitor;
import com.eye.ui.UsageLimitOverlay;
import com.eye.win.DesktopAvailability;
import com.eye.win.SessionWatcher;
import java.time.Clock;

public class Main {
    private static FileChannel instanceChannel;
    private static FileLock instanceLock;
    public static void main(String[] args) {
        com.eye.win.WindowsAppIdentity.initialize();
        configureInstalledRuntime();
        try {
            Path lockPath = DataPaths.instanceLock();
            Files.createDirectories(lockPath.getParent());
            instanceChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            instanceLock = instanceChannel.tryLock();
            if (instanceLock == null) {
                JOptionPane.showMessageDialog(null, "EyeRelax 已在运行，请从右下角托盘图标打开。", "EyeRelax", JOptionPane.INFORMATION_MESSAGE);
                instanceChannel.close(); return;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "无法取得本地状态锁："+ex.getMessage(), "EyeRelax", JOptionPane.ERROR_MESSAGE);return;
        }
        DataPaths.initialize();
        com.eye.tutorial.TutorialState launchTutorial=null;
        try{launchTutorial=com.eye.tutorial.TutorialState.capture(DataPaths.root());}catch(Exception ex){System.err.println("Tutorial initialization: "+ex.getMessage());}
        final com.eye.tutorial.TutorialState tutorialState=launchTutorial;
        Config initialConfig = new Config(); initialConfig.loadIfExists();
        com.eye.i18n.I18n.configure(initialConfig.language);
        boolean startupInvocation = Arrays.asList(args).contains("--startup");
        if (startupInvocation) {
            boolean registered = StartupRegistration.isEnabled();
            if (!shouldStartForLogin(initialConfig, registered)) return;
            // Installer upgrades can enable Startup-folder launch while an older config still says false.
            if (registered && !initialConfig.launchAtLogin) {
                initialConfig.launchAtLogin = true;
                initialConfig.save();
            }
        }
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            Config config = new Config();
            config.loadIfExists();

            PlanManager planManager = new PlanManager(config);
            planManager.loadToday();
            ReminderManager reminderManager = new ReminderManager();

            CycleEngine engine = new CycleEngine(config);
            TrayApp tray = new TrayApp(config, engine, planManager, reminderManager);
            tray.init();
            tray.configureTutorial(tutorialState);
            tray.startUpdateChecks();
            Clock usageClock=Clock.systemDefaultZone();
            ComputerUsageLimits usage=new ComputerUsageLimits(config,DataPaths.file("computer-usage.properties"),usageClock.instant(),System.nanoTime());
            UsageLimitOverlay limitOverlay=new UsageLimitOverlay(()->{if(engine.isInWork())engine.stop();});
            UsageLimitMonitor limits=new UsageLimitMonitor(usage,usageClock,System::nanoTime,limitOverlay);
            limitOverlay.setShutdownAction(limits::shutdownComputer);
            tray.setUsageMonitor(limits);
            com.eye.core.ComputerActivity computerActivity=new com.eye.core.ComputerActivity(DataPaths.file("computer-activity.properties"),usageClock.getZone(),()->config.dayBoundarySeconds);
            com.eye.win.ForegroundActivity foreground=new com.eye.win.ForegroundActivity();
            tray.setComputerActivity(computerActivity);
            var applicationUsage=new com.eye.core.ApplicationLimits(DataPaths.file("application-limits-usage.properties"),usageClock.getZone(),()->config.dayBoundarySeconds,()->config.applicationLimits);
            var applicationProtection=new com.eye.win.ApplicationProtection(config,applicationUsage,()->limits.blocked()||engine.isForcedRest()||engine.isReminderActive());
            tray.setApplicationProtection(applicationProtection);applicationProtection.start();
            Runtime.getRuntime().addShutdownHook(new Thread(applicationProtection::close,"EyeRelax-app-limits-save"));
            com.eye.sync.CloudSync.configure(config,engine,planManager,reminderManager,computerActivity);
            var recorder=new com.eye.core.ComputerActivityRecorder(computerActivity,usageClock,foreground::sample,com.eye.win.RunningApplications::sample);
            recorder.start();
            Runtime.getRuntime().addShutdownHook(new Thread(recorder::close,"EyeRelax-computer-statistics-save"));
            engine.setSessionObserver(event->SwingUtilities.invokeLater(()->{
                boolean available=DesktopAvailability.isAvailable();
                if(event==SessionWatcher.WTS_SESSION_LOCK||event==SessionWatcher.SYSTEM_SUSPEND||event==SessionWatcher.WTS_CONSOLE_DISCONNECT||event==SessionWatcher.WTS_REMOTE_DISCONNECT)available=false;
                computerActivity.sessionAvailable(available);
                if(event==SessionWatcher.SYSTEM_RESUME) {
                    limits.sessionEvent(DesktopAvailability.isAvailable()?SessionWatcher.WTS_SESSION_UNLOCK:SessionWatcher.WTS_SESSION_LOCK);
                } else limits.sessionEvent(event);
            }));
            javax.swing.Timer usageTimer=new javax.swing.Timer(1000,e->limits.tick());
            usageTimer.start();
            Path installerClose=DataPaths.bootstrapRoot().resolve("installer-close.request");
            javax.swing.Timer installerTimer=new javax.swing.Timer(500,e->{
                try {
                    if(Files.isRegularFile(installerClose)) {
                        String requested=Files.readString(installerClose).trim();
                        String installed=System.getProperty("eyerelax.installDir","");
                        if(!installed.isBlank()&&Path.of(requested).toAbsolutePath().normalize().equals(Path.of(installed).toAbsolutePath().normalize())) {
                            Files.deleteIfExists(installerClose);config.save();engine.persistForShutdown();limits.close();System.exit(0);
                        }
                    }
                }catch(Exception ex){System.err.println("Installer close: "+ex.getMessage());}
            });
            installerTimer.start();
            Runtime.getRuntime().addShutdownHook(new Thread(limits::close,"EyeRelax-usage-save"));
            engine.setOnStateChanged(tray::refresh);
            Runtime.getRuntime().addShutdownHook(new Thread(engine::persistForShutdown, "EyeRelax-state-save"));

            tray.startOnLaunch();
            tray.refresh();
            if(config.hasUsageLimits()&&!DesktopAvailability.isAvailable())limits.sessionEvent(SessionWatcher.WTS_SESSION_LOCK);
            limits.start();
        });
    }
    public static boolean shouldStartForLogin(Config config, boolean registered) {
        return registered || config.launchAtLogin || config.hasUsageLimits() || config.hasApplicationLimits() || com.eye.sync.StrictSettings.locked();
    }
    private static void configureInstalledRuntime() {
        try {
            Path jar = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path app = jar.getParent();
            if (app == null || !"app".equalsIgnoreCase(app.getFileName().toString())) return;
            Path root = app.getParent();
            if (!Files.isRegularFile(root.resolve("install.properties"))) return;
            System.setProperty("eyerelax.installDir", root.toString());
            System.setProperty("jna.library.path", app.toString());
            Properties installed = AtomicProperties.read(root.resolve("install.properties"));
            System.setProperty("eyerelax.defaultAutostart", installed.getProperty("autostartDefault", "false"));
            String defaultDataDir = installed.getProperty("defaultDataDir", "").trim();
            if (!defaultDataDir.isBlank()) System.setProperty("eyerelax.defaultDataDir", defaultDataDir);
            if (Boolean.parseBoolean(installed.getProperty("sharedRest", "false"))) {
                String pd = System.getenv("ProgramData");
                if (pd != null && !pd.isBlank()) System.setProperty("eyerelax.sharedRestDir", Paths.get(pd,"EyeRelax","rest-state").toString());
            }
        } catch (Exception ex) { System.err.println("Installation metadata: "+ex.getMessage()); }
    }
}
