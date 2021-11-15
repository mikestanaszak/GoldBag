package goldbag.goldbag;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
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
        if(getDataFolder().listFiles().length != 2){
            getLogger().info("Missing files!");
            File n = new File(getDataFolder(), "/config.yml");
            if(!n.exists()){
                getLogger().info("Creating default config");
                this.saveDefaultConfig();
            }
            n = new File(getDataFolder(), "/values.json");
            if(!n.exists()){
                try {
                    getLogger().info("Creating default values file");
                    n.createNewFile();
                    InputStream defaultValues = this.getResource("defaultValues.json");
                    copyFileUsingStream(defaultValues, n);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        config = new File(getDataFolder(), "config.yml");
        configConfig = YamlConfiguration.loadConfiguration(config);
        setupMySQL();
        getLogger().info("GoldBag has been enabled :)");
        this.getCommand("balance").setExecutor(new GoldBagCommands());
        this.getCommand("purse").setExecutor(new GoldBagCommands());
        this.getCommand("balance").setTabCompleter(new GoldBagTabComplete());
        this.getCommand("purse").setTabCompleter(new GoldBagTabComplete());
        this.getCommand("pursetop").setExecutor(new GoldBagCommands());
        this.getCommand("withdraw").setExecutor(new GoldBagCommands());
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
                DatabaseHandler db = new DatabaseHandler();
                try {
                    db.CreateTables();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                if(configConfig.getBoolean("interest.enabled")){
                    final boolean[] firstTime = {true};
                    Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
                        @Override
                        public void run() {
                            if(firstTime[0]){
                                firstTime[0] = false;
                            }
                            else{
                                db.IncurrInterest(configConfig.getDouble("interest.rate"), getLogger());
                            }
                        }
                    }, 100, 86400*20);
                }
            }
        } catch (Exception e) {
                e.printStackTrace();
        }
    }

    private static void copyFileUsingStream(InputStream source, File dest) throws IOException {
        InputStream is = source;
        OutputStream os = null;
        try {
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            is.close();
            os.close();
        }
    }
}

