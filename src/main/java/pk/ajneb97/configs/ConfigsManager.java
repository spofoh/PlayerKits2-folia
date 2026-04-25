package pk.ajneb97.configs;

import org.bukkit.Bukkit;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.managers.MessagesManager;

public class ConfigsManager {

    private PlayerKits2 plugin;

    private KitsConfigManager kitsConfigManager;
    private MessagesConfigManager messagesConfigManager;
    private MainConfigManager mainConfigManager;
    private PlayersConfigManager playersConfigManager;
    private InventoryConfigManager inventoryConfigManager;
    private volatile boolean storageBackendChangeRequiresRestart;

    public ConfigsManager(PlayerKits2 plugin){
        this.plugin = plugin;
        this.kitsConfigManager = new KitsConfigManager(plugin,"kits");
        this.messagesConfigManager = new MessagesConfigManager(plugin);
        this.mainConfigManager = new MainConfigManager(plugin);
        this.playersConfigManager = new PlayersConfigManager(plugin,"players");
        this.inventoryConfigManager = new InventoryConfigManager(plugin);
        this.storageBackendChangeRequiresRestart = false;
    }

    public void configure(){
        this.kitsConfigManager.configure();
        this.messagesConfigManager.configure();
        this.mainConfigManager.configure();
        this.playersConfigManager.configure();
        this.inventoryConfigManager.configure();
    }

    public KitsConfigManager getKitsConfigManager() {
        return kitsConfigManager;
    }

    public MessagesConfigManager getMessagesConfigManager() {
        return messagesConfigManager;
    }

    public MainConfigManager getMainConfigManager() {
        return mainConfigManager;
    }

    public PlayersConfigManager getPlayersConfigManager() {
        return playersConfigManager;
    }

    public InventoryConfigManager getInventoryConfigManager() {
        return inventoryConfigManager;
    }

    public boolean reload(){
        boolean previousMySQLEnabled = mainConfigManager.isMySQL();
        boolean previousRedisEnabled = mainConfigManager.isRedisSyncEnabled();
        String previousRedisHost = mainConfigManager.getRedisSyncHost();
        int previousRedisPort = mainConfigManager.getRedisSyncPort();
        String previousRedisPassword = mainConfigManager.getRedisSyncPassword();
        int previousRedisDatabase = mainConfigManager.getRedisSyncDatabase();
        int previousRedisTimeout = mainConfigManager.getRedisSyncTimeout();
        boolean previousRedisSSL = mainConfigManager.isRedisSyncSSL();
        String previousRedisChannel = mainConfigManager.getRedisSyncChannel();
        if(!messagesConfigManager.reloadConfig()){
            return false;
        }
        if(!mainConfigManager.reloadConfig()){
            return false;
        }
        if(!inventoryConfigManager.reloadConfig()){
            return false;
        }
        kitsConfigManager.loadConfigs();
        storageBackendChangeRequiresRestart = previousMySQLEnabled != mainConfigManager.isMySQL() ||
                previousRedisEnabled != mainConfigManager.isRedisSyncEnabled() ||
                !equalsNullable(previousRedisHost, mainConfigManager.getRedisSyncHost()) ||
                previousRedisPort != mainConfigManager.getRedisSyncPort() ||
                !equalsNullable(previousRedisPassword, mainConfigManager.getRedisSyncPassword()) ||
                previousRedisDatabase != mainConfigManager.getRedisSyncDatabase() ||
                previousRedisTimeout != mainConfigManager.getRedisSyncTimeout() ||
                previousRedisSSL != mainConfigManager.isRedisSyncSSL() ||
                !equalsNullable(previousRedisChannel, mainConfigManager.getRedisSyncChannel());
        if(storageBackendChangeRequiresRestart){
            Bukkit.getConsoleSender().sendMessage(MessagesManager.getLegacyColoredMessage(
                    PlayerKits2.prefix+"&eStorage/sync settings changed. &cA restart is required to apply them."));
        }
        if(!plugin.isMySQLActive()){
            plugin.reloadPlayerDataSaveTask();
        }

        plugin.getVerifyManager().verify();

        return true;
    }

    public boolean isStorageBackendChangeRequiresRestart() {
        return storageBackendChangeRequiresRestart;
    }

    private boolean equalsNullable(String text1, String text2){
        if(text1 == null){
            return text2 == null;
        }
        return text1.equals(text2);
    }
}
