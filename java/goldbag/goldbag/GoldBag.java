package goldbag.goldbag;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


//Todo: Get redis working locally as well as MySQL,
// flat file will not work due to restraints in performance being
// having to open the file anytime I would need to access...
// which would be a lot
public final class GoldBag extends JavaPlugin {
    private File config;
    private FileConfiguration configConfig;
    private Connection connection;

    public String host, database, username, password;

    @Override
    public void onEnable() {
        config = new File(getDataFolder(), "config.yml");
        configConfig = YamlConfiguration.loadConfiguration(config);
        setupMySQL();
        getLogger().info("GoldBag has been enabled :)");
        this.getCommand("balance").setExecutor(new GoldBagCommands());
        this.getCommand("purse").setExecutor(new GoldBagCommands());
        this.getCommand("balance").setTabCompleter(new GoldBagTabComplete());
        this.getCommand("purse").setTabCompleter(new GoldBagTabComplete());
        this.getCommand("pursetop").setExecutor(new GoldBagCommands());
        getServer().getPluginManager().registerEvents(new EventHandlers(), this);
        super.onEnable();
    }

    public Connection getConnection() {
        return connection;
    }

    public FileConfiguration getConfigConfig() {
        return configConfig;
    }

    @Override
    public void onDisable() {
        if(configConfig.getString("savetype").equalsIgnoreCase("mysql")){
            try {
                if(connection != null && !connection.isClosed()){
                    connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        getLogger().info("GoldBag has been disabled :(");
        super.onDisable();
    }

    public void setupMySQL(){
        try{
            synchronized (this){
                host = configConfig.getString("databaseDetails.url");
                database = configConfig.getString("databaseDetails.database");
                username = configConfig.getString("databaseDetails.user");
                password = configConfig.getString("databaseDetails.password");
                String url = "jdbc:mysql://" + host + "/" + database;
                connection = DriverManager.getConnection(url, username, password);
            }
        } catch (Exception e) {
                e.printStackTrace();
        }
    }
}

