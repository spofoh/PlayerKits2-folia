package pk.ajneb97.configs;

import org.bukkit.configuration.file.FileConfiguration;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.configs.model.CommonConfig;
import pk.ajneb97.model.Kit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainConfigManager {

    private PlayerKits2 plugin;
    private CommonConfig configFile;

    //Options
    private Kit newKitDefault;
    private boolean kitPreview;
    private boolean closeInventoryOnClaim;
    private boolean claimKitShortCommand;
    private boolean kitPreviewRequiresKitPermission;
    private boolean newKitDefaultSaveModeOriginal;
    private String firstJoinKit;
    private String newKitDefaultInventory;
    private boolean isMySQL;
    private boolean updateNotify;
    private boolean useMiniMessage;
    private boolean redisSyncEnabled;
    private String redisSyncHost;
    private int redisSyncPort;
    private String redisSyncPassword;
    private int redisSyncDatabase;
    private int redisSyncTimeout;
    private boolean redisSyncSSL;
    private String redisSyncChannel;

    public MainConfigManager(PlayerKits2 plugin){
        this.plugin = plugin;
        this.configFile = new CommonConfig("config.yml",plugin,null, false);
        this.configFile.registerConfig();
        checkUpdate();
    }

    public void configure(){
        FileConfiguration config = configFile.getConfig();
        newKitDefault = KitsConfigManager.getKitFromConfig(config,plugin,null,"new_kit_default_values.");
        kitPreview = config.getBoolean("kit_preview");
        closeInventoryOnClaim = config.getBoolean("close_inventory_on_claim");
        kitPreviewRequiresKitPermission = config.getBoolean("kit_preview_requires_kit_permission");
        firstJoinKit = config.getString("first_join_kit");
        newKitDefaultInventory = config.getString("new_kit_default_inventory");
        isMySQL = config.getBoolean("mysql_database.enabled");
        updateNotify = config.getBoolean("update_notify");
        claimKitShortCommand = config.getBoolean("claim_kit_short_command");
        useMiniMessage = config.getBoolean("use_minimessage");
        newKitDefaultSaveModeOriginal = config.getBoolean("new_kit_default_save_mode_original");
        redisSyncEnabled = config.getBoolean("redis_sync.enabled");
        redisSyncHost = config.getString("redis_sync.host","localhost");
        redisSyncPort = config.getInt("redis_sync.port",6379);
        redisSyncPassword = config.getString("redis_sync.password","");
        redisSyncDatabase = config.getInt("redis_sync.database",0);
        redisSyncTimeout = config.getInt("redis_sync.timeout",2000);
        redisSyncSSL = config.getBoolean("redis_sync.ssl",false);
        redisSyncChannel = config.getString("redis_sync.channel","playerkits2:sync");
    }

    public boolean reloadConfig(){
        if(!configFile.reloadConfig()){
            return false;
        }
        configure();
        return true;
    }

    public FileConfiguration getConfig(){
        return configFile.getConfig();
    }

    public void checkUpdate(){
        Path pathConfig = Paths.get(configFile.getRoute());
        try{
            String text = new String(Files.readAllBytes(pathConfig));
            if(!text.contains("use_minimessage:")){
                getConfig().set("use_minimessage",false);
                configFile.saveConfig();
            }
            if(!text.contains("verifyServerCertificate:")){
                getConfig().set("mysql_database.pool.connectionTimeout",5000);
                getConfig().set("mysql_database.advanced.verifyServerCertificate",false);
                getConfig().set("mysql_database.advanced.useSSL",true);
                getConfig().set("mysql_database.advanced.allowPublicKeyRetrieval",true);
                configFile.saveConfig();
            }
            if(!text.contains("new_kit_default_save_mode_original:")){
                getConfig().set("new_kit_default_save_mode_original", true);
                configFile.saveConfig();
            }
            if(!text.contains("redis_sync:")){
                getConfig().set("redis_sync.enabled", false);
                getConfig().set("redis_sync.host", "localhost");
                getConfig().set("redis_sync.port", 6379);
                getConfig().set("redis_sync.password", "");
                getConfig().set("redis_sync.database", 0);
                getConfig().set("redis_sync.timeout", 2000);
                getConfig().set("redis_sync.ssl", false);
                getConfig().set("redis_sync.channel", "playerkits2:sync");
                configFile.saveConfig();
            }
        }catch(IOException e){
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred in PlayerKits2", e);
        }
    }

    public Kit getNewKitDefault() {
        return newKitDefault;
    }

    public boolean isKitPreview() {
        return kitPreview;
    }

    public boolean isCloseInventoryOnClaim() {
        return closeInventoryOnClaim;
    }

    public boolean isKitPreviewRequiresKitPermission() {
        return kitPreviewRequiresKitPermission;
    }

    public String getFirstJoinKit() {
        return firstJoinKit;
    }

    public String getNewKitDefaultInventory() {
        return newKitDefaultInventory;
    }

    public boolean isMySQL() {
        return isMySQL;
    }

    public boolean isUpdateNotify() {
        return updateNotify;
    }

    public boolean isClaimKitShortCommand() {
        return claimKitShortCommand;
    }

    public boolean isNewKitDefaultSaveModeOriginal() {
        return newKitDefaultSaveModeOriginal;
    }

    public boolean isUseMiniMessage() {
        return useMiniMessage;
    }

    public boolean isRedisSyncEnabled() {
        return redisSyncEnabled;
    }

    public String getRedisSyncHost() {
        return redisSyncHost;
    }

    public int getRedisSyncPort() {
        return redisSyncPort;
    }

    public String getRedisSyncPassword() {
        return redisSyncPassword;
    }

    public int getRedisSyncDatabase() {
        return redisSyncDatabase;
    }

    public int getRedisSyncTimeout() {
        return redisSyncTimeout;
    }

    public boolean isRedisSyncSSL() {
        return redisSyncSSL;
    }

    public String getRedisSyncChannel() {
        return redisSyncChannel;
    }
}
