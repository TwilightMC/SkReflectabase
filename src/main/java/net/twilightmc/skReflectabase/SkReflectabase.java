package net.twilightmc.skReflectabase;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class SkReflectabase extends JavaPlugin {
    private static SkReflectabase instance;
    private DatabaseManager databaseManager;
    private String databaseUrl;
    private String username;
    private String password;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadConfig();

        try {
            databaseManager = new DatabaseManager(this, databaseUrl, username, password);
            getLogger().info("Successfully connected to database!");
        } catch (SQLException e) {
            getLogger().severe("Failed to connect to database: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();

        config.addDefault("database.url", "jdbc:mysql://localhost:3306/database");
        config.addDefault("database.username", "root");
        config.addDefault("database.password", "password");
        config.options().copyDefaults(true);
        saveConfig();

        databaseUrl = config.getString("database.url");
        username = config.getString("database.username");
        password = config.getString("database.password");
    }

    public static SkReflectabase getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
