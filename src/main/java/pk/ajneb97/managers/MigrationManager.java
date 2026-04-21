package pk.ajneb97.managers;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.configs.KitsConfigManager;
import pk.ajneb97.configs.PlayersConfigManager;
import pk.ajneb97.model.*;
import pk.ajneb97.model.item.KitItem;
import pk.ajneb97.model.item.KitItemSkullData;
import pk.ajneb97.utils.TaskUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MigrationManager {

    private PlayerKits2 plugin;
    public MigrationManager(PlayerKits2 plugin){
        this.plugin = plugin;
    }

    public void migrate(CommandSender sender){
        TaskUtils.runAsync(plugin, () -> {
            migrateKits(sender);
            migratePlayers(sender);

            sendMessage(sender, PlayerKits2.prefix+MessagesManager.getLegacyColoredMessage(" &aMigration completed."));
        });
    }

    public void migrateKits(CommandSender sender){
        File bStatsFolder = new File(plugin.getDataFolder().getParentFile(), "PlayerKits");
        File configFile = new File(bStatsFolder, "kits.yml");
        if(!configFile.exists()){
            sendMessage(sender, PlayerKits2.prefix+MessagesManager.getLegacyColoredMessage(" &cPlayerKits1 kits.yml file not found."));
            return;
        }

        KitItemManager kitItemManager = plugin.getKitItemManager();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        Kit defaultValues = plugin.getConfigsManager().getMainConfigManager().getNewKitDefault();
        KitsConfigManager kitsConfigManager = plugin.getConfigsManager().getKitsConfigManager();
        org.bukkit.configuration.ConfigurationSection kitsSection = config.getConfigurationSection("Kits");
        if(kitsSection != null){
            for(String kitName : kitsSection.getKeys(false)){
                try{
                    String path = "Kits."+kitName;
                    Kit kit = new Kit(kitName);

                    boolean oneTime = config.getBoolean(path+".one_time");
                    int cooldown = config.contains(path+".cooldown") ? config.getInt(path+".cooldown") : 0;
                    boolean permissionRequired = config.contains(path+".permission");
                    boolean autoArmor = config.getBoolean(path+".auto_armor");

                    ArrayList<KitAction> claimActions = new ArrayList<>();
                    if(config.contains(path+".Commands")){
                        for(String command : config.getStringList(path+".Commands")){
                            claimActions.add(new KitAction("console_command: "+command,null,false,false));
                        }
                    }

                    ArrayList<KitItem> items = new ArrayList<>();
                    org.bukkit.configuration.ConfigurationSection itemsSection = config.getConfigurationSection(path+".Items");
                    if(itemsSection != null){
                        for(String key : itemsSection.getKeys(false)){
                            String pathItem = path+".Items."+key;
                            KitItem kitItem = kitItemManager.getKitItemFromV1Config(config,pathItem);
                            items.add(kitItem);
                        }
                    }

                    KitRequirements kitRequirements = null;
                    if(config.contains(path+".price")){
                        kitRequirements = new KitRequirements();
                        kitRequirements.setPrice(config.getInt(path+".price"));
                        boolean oneTimeBuy = config.getBoolean(path+".one_time_buy");
                        kitRequirements.setOneTimeRequirements(oneTimeBuy);
                    }

                    kit.setDisplayItemDefault(getDisplayItem(config,path));
                    if(config.contains(path+".noPermissionsItem")){
                        kit.setDisplayItemNoPermission(getDisplayItem(config,path+".noPermissionsItem"));
                    }
                    kit.setDisplayItemCooldown(defaultValues.getDisplayItemCooldown());
                    kit.setDisplayItemOneTime(defaultValues.getDisplayItemOneTime());
                    kit.setCooldown(cooldown);
                    kit.setAutoArmor(autoArmor);
                    kit.setOneTime(oneTime);
                    kit.setPermissionRequired(permissionRequired);
                    kit.setItems(items);
                    kit.setClaimActions(claimActions);
                    kit.setErrorActions(new ArrayList<>());

                    kit.setRequirements(kitRequirements);

                    kitsConfigManager.saveConfig(kit);

                    sendMessage(sender, PlayerKits2.prefix+MessagesManager.getLegacyColoredMessage(" &aKit &7"+kitName+" &amigrated."));
                }catch(Exception e){
                    sendMessage(sender, PlayerKits2.prefix+MessagesManager.getLegacyColoredMessage(" &cError while trying to migrate kit &7"+kitName+"&c, check console."));
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred in PlayerKits2", e);
                }

            }
        }else{
            sendMessage(sender, PlayerKits2.prefix+MessagesManager.getLegacyColoredMessage(" &cNo kits found."));
            return;
        }

        kitsConfigManager.loadConfigs();
        plugin.getVerifyManager().verify();
    }

    public void migratePlayers(CommandSender sender){
        File bStatsFolder = new File(plugin.getDataFolder().getParentFile(), "PlayerKits");
        File configFile = new File(bStatsFolder, "players.yml");
        if(!configFile.exists()){
            sendMessage(sender, PlayerKits2.prefix+MessagesManager.getLegacyColoredMessage(" &cPlayerKits1 players.yml file not found."));
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        PlayersConfigManager playersConfigManager = plugin.getConfigsManager().getPlayersConfigManager();

        org.bukkit.configuration.ConfigurationSection playersSection = config.getConfigurationSection("Players");
        if(playersSection != null) {
            for (String uuid : playersSection.getKeys(false)) {
                try {
                    String path = "Players."+uuid;
                    String name = config.getString(path+".name");
                    PlayerData playerData = new PlayerData(UUID.fromString(uuid),name);
                    org.bukkit.configuration.ConfigurationSection playerSection = config.getConfigurationSection(path);
                    if(playerSection != null) {
                        for(String kitName : playerSection.getKeys(false)){
                        if(kitName.equals("name")){
                           continue;
                        }

                        PlayerDataKit playerDataKit = new PlayerDataKit(kitName);
                        playerDataKit.setBought(config.getBoolean(path+"."+kitName+".buyed"));
                        playerDataKit.setCooldown(config.getLong(path+"."+kitName+".cooldown"));
                        playerDataKit.setOneTime(config.getBoolean(path+"."+kitName+".one_time"));
                        playerData.addKit(playerDataKit);
                        }
                    }

                    playersConfigManager.saveConfig(playerData);

                    sendMessage(sender, PlayerKits2.prefix+MessagesManager.getLegacyColoredMessage(" &aPlayer &7"+name+" &amigrated."));
                } catch (Exception e) {
                    sendMessage(sender, PlayerKits2.prefix + MessagesManager.getLegacyColoredMessage(" &cError while trying to migrate player data with uuid &7" + uuid + "&c, check console."));
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred in PlayerKits2", e);
                }
            }
        }

        playersConfigManager.configure();
    }

    private void sendMessage(CommandSender sender, String message) {
        TaskUtils.runCommandSender(plugin, sender, () -> sender.sendMessage(message));
    }

    public KitItem getDisplayItem(YamlConfiguration config,String path){
        KitItem kitItem = new KitItem(config.getString(path+".display_item"));
        String name = config.contains(path+".display_name") ? config.getString(path+".display_name") : null;
        List<String> lore = config.contains(path+".display_lore") ? config.getStringList(path+".display_lore") : null;
        int customModelData = config.contains(path+".display_item_custom_model_data") ? config.getInt(path+".display_item_custom_model_data") : 0;

        KitItemSkullData skullData = null;
        if(config.contains(path+".display_item_skulldata")) {
            String[] fullSkull = config.getString(path+".display_item_skulldata").split(";");
            skullData = new KitItemSkullData(null,fullSkull[1],fullSkull[0]);
        }

        kitItem.setName(name);
        kitItem.setLore(lore);
        kitItem.setCustomModelData(customModelData);
        kitItem.setSkullData(skullData);
        return kitItem;
    }
}
