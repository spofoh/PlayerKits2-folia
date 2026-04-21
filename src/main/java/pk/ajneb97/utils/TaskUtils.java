package pk.ajneb97.utils;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public class TaskUtils {
    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void runAsync(JavaPlugin plugin, Runnable runnable) {
        if (isFolia()) {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    public static void runSync(JavaPlugin plugin, Runnable runnable) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, runnable);
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public static void runSyncTimer(JavaPlugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> runnable.run(), Math.max(1, delayTicks), periodTicks);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        }
    }

    public static void runAsyncTimer(JavaPlugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        if (isFolia()) {
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> runnable.run(), Math.max(1, delayTicks * 50), periodTicks * 50, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks);
        }
    }

    public static void runSyncLater(JavaPlugin plugin, Runnable runnable, long delayTicks) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), Math.max(1, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        }
    }

    public static void runEntity(JavaPlugin plugin, Entity entity, Runnable runnable) {
        if (isFolia()) {
            entity.getScheduler().execute(plugin, runnable, null, 1L);
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public static void runEntityLater(JavaPlugin plugin, Entity entity, Runnable runnable, long delayTicks) {
        if (isFolia()) {
            entity.getScheduler().execute(plugin, runnable, null, Math.max(1, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        }
    }

    public static void runCommandSender(JavaPlugin plugin, CommandSender sender, Runnable runnable) {
        if (isFolia() && sender instanceof Entity entity) {
            runEntity(plugin, entity, runnable);
            return;
        }
        runSync(plugin, runnable);
    }
}
