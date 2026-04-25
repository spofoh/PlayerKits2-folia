package pk.ajneb97.managers;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.configs.PlayersConfigManager;
import pk.ajneb97.database.MySQLConnection;
import pk.ajneb97.model.PlayerData;
import pk.ajneb97.model.PlayerDataKit;
import pk.ajneb97.model.internal.GenericCallback;
import pk.ajneb97.model.internal.PlayerKitsMessageResult;
import pk.ajneb97.utils.OtherUtils;
import pk.ajneb97.utils.TaskUtils;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private PlayerKits2 plugin;
    private Map<UUID, PlayerData> players;
    private Map<String,UUID> playerNames;
    private Set<UUID> loadingPlayers;

    public PlayerDataManager(PlayerKits2 plugin){
        this.plugin = plugin;
        this.players = new ConcurrentHashMap<>();
        this.playerNames = new ConcurrentHashMap<>();
        this.loadingPlayers = ConcurrentHashMap.newKeySet();
    }

    public Map<UUID,PlayerData> getPlayers() {
        return players;
    }

    public void addPlayer(PlayerData p){
        players.put(p.getUuid(),p);
        playerNames.put(p.getName(), p.getUuid());
    }

    public PlayerData getPlayer(Player player, boolean create){
        PlayerData playerData = players.get(player.getUniqueId());
        if(playerData == null && create){
            playerData = new PlayerData(player.getUniqueId(),player.getName());
            addPlayer(playerData);
        }
        return playerData;
    }

    private void updatePlayerName(String oldName,String newName,UUID uuid){
        if(oldName != null){
            playerNames.remove(oldName);
        }
        playerNames.put(newName,uuid);
    }

    public PlayerData getPlayerByUUID(UUID uuid){
        return players.get(uuid);
    }

    private UUID getPlayerUUID(String name){
        UUID uuid = playerNames.get(name);
        if(uuid != null){
            return uuid;
        }
        for(Map.Entry<String,UUID> entry : playerNames.entrySet()){
            String currentName = entry.getKey();
            if(currentName != null && currentName.equalsIgnoreCase(name)){
                return entry.getValue();
            }
        }
        return null;
    }

    public PlayerData getPlayerByName(String name){
        UUID uuid = getPlayerUUID(name);
        return players.get(uuid);
    }

    public void removePlayer(PlayerData playerData){
        players.remove(playerData.getUuid());
        playerNames.remove(playerData.getName());
    }

    public void removePlayerByUUID(UUID uuid){
        PlayerData playerData = players.remove(uuid);
        if(playerData != null){
            playerNames.remove(playerData.getName());
        }
        loadingPlayers.remove(uuid);
    }

    private void setPlayerLoading(UUID uuid, boolean loading){
        if(loading){
            loadingPlayers.add(uuid);
        }else{
            loadingPlayers.remove(uuid);
        }
    }

    public boolean isPlayerDataLoading(Player player){
        return loadingPlayers.contains(player.getUniqueId());
    }

    private void publishKitState(PlayerData playerData, String kitName){
        RedisSyncManager redisSyncManager = plugin.getRedisSyncManager();
        if(redisSyncManager == null || !redisSyncManager.isActive()){
            return;
        }
        PlayerDataKit playerDataKit = playerData.getKit(kitName);
        if(playerDataKit != null){
            redisSyncManager.publishKitState(playerData.getUuid(),playerDataKit);
        }
    }

    private void publishResetPlayer(UUID uuid, String kitName){
        RedisSyncManager redisSyncManager = plugin.getRedisSyncManager();
        if(redisSyncManager != null && redisSyncManager.isActive()){
            redisSyncManager.publishResetPlayer(uuid,kitName);
        }
    }

    private void publishResetAll(String kitName){
        RedisSyncManager redisSyncManager = plugin.getRedisSyncManager();
        if(redisSyncManager != null && redisSyncManager.isActive()){
            redisSyncManager.publishResetAll(kitName);
        }
    }

    public void setKitCooldown(Player player,String kitName,long cooldown){
        PlayerData playerData = getPlayer(player,true);
        playerData.setKitCooldown(kitName,cooldown);
        playerData.setModified(true);
        MySQLConnection mySQLConnection = plugin.getActiveMySQLConnection();
        if(mySQLConnection != null){
            mySQLConnection.updateKit(playerData,playerData.getKit(kitName));
            publishKitState(playerData,kitName);
        }
    }

    public long getKitCooldown(Player player,String kitName){
        PlayerData playerData = getPlayerByUUID(player.getUniqueId());
        if(playerData == null){
            return 0;
        }else{
            return playerData.getKitCooldown(kitName);
        }
    }

    public String getKitCooldownString(long playerCooldown){
        long currentMillis = System.currentTimeMillis();
        long millisDif = playerCooldown-currentMillis;
        String timeStringMillisDif = OtherUtils.getTime(millisDif/1000, plugin.getMessagesManager());
        return timeStringMillisDif;
    }

    public void setKitOneTime(Player player,String kitName){
        PlayerData playerData = getPlayer(player,true);
        playerData.setKitOneTime(kitName);
        playerData.setModified(true);
        MySQLConnection mySQLConnection = plugin.getActiveMySQLConnection();
        if(mySQLConnection != null){
            mySQLConnection.updateKit(playerData,playerData.getKit(kitName));
            publishKitState(playerData,kitName);
        }
    }

    public boolean isKitOneTime(Player player,String kitName){
        PlayerData playerData = getPlayerByUUID(player.getUniqueId());
        if(playerData == null){
            return false;
        }else{
            return playerData.getKitOneTime(kitName);
        }
    }

    public void setKitBought(Player player,String kitName){
        PlayerData playerData = getPlayer(player,true);
        playerData.setKitBought(kitName);
        playerData.setModified(true);
        MySQLConnection mySQLConnection = plugin.getActiveMySQLConnection();
        if(mySQLConnection != null){
            mySQLConnection.updateKit(playerData,playerData.getKit(kitName));
            publishKitState(playerData,kitName);
        }
    }

    public boolean isKitBought(Player player,String kitName){
        PlayerData playerData = getPlayerByUUID(player.getUniqueId());
        if(playerData == null){
            return false;
        }else{
            return playerData.getKitHasBought(kitName);
        }
    }

    public void resetKitForTarget(String target, String kitName, GenericCallback<PlayerKitsMessageResult> callback){
        TaskUtils.runAsync(plugin, () -> {
            MySQLConnection mySQLConnection = plugin.getActiveMySQLConnection();
            if(mySQLConnection != null){
                resetKitForTargetMySQL(target, kitName, mySQLConnection, callback);
            }else{
                resetKitForTargetFile(target, kitName, callback);
            }
        });
    }

    private void resetKitForTargetMySQL(String target, String kitName, MySQLConnection mySQLConnection, GenericCallback<PlayerKitsMessageResult> callback){
        UUID uuid = tryParseUUID(target);
        if(uuid != null){
            mySQLConnection.getPlayer(uuid.toString(), playerData -> {
                if(playerData == null){
                    callback.onDone(getPlayerDataNotFoundResult(target));
                    return;
                }
                resetLoadedPlayerData(uuid, kitName);
                mySQLConnection.resetKit(uuid.toString(),kitName,false, () -> {
                    publishResetPlayer(uuid,kitName);
                    callback.onDone(PlayerKitsMessageResult.success());
                });
            });
            return;
        }

        UUID cachedUuid = getPlayerUUID(target);
        if(cachedUuid != null){
            resetLoadedPlayerData(cachedUuid, kitName);
            mySQLConnection.resetKit(cachedUuid.toString(),kitName,false, () -> {
                publishResetPlayer(cachedUuid,kitName);
                callback.onDone(PlayerKitsMessageResult.success());
            });
            return;
        }

        mySQLConnection.getPlayerUUIDByName(target, resolvedUuid -> {
            if(resolvedUuid == null){
                callback.onDone(getPlayerDataNotFoundResult(target));
                return;
            }
            resetLoadedPlayerData(resolvedUuid, kitName);
            mySQLConnection.resetKit(resolvedUuid.toString(),kitName,false, () -> {
                publishResetPlayer(resolvedUuid,kitName);
                callback.onDone(PlayerKitsMessageResult.success());
            });
        });
    }

    private void resetKitForTargetFile(String target, String kitName, GenericCallback<PlayerKitsMessageResult> callback){
        UUID uuid = tryParseUUID(target);
        if(uuid == null){
            uuid = getPlayerUUID(target);
        }
        if(uuid == null){
            callback.onDone(getPlayerDataNotFoundResult(target));
            return;
        }

        boolean foundPlayerData = resetLoadedPlayerData(uuid, kitName);
        PlayersConfigManager playersConfigManager = plugin.getConfigsManager().getPlayersConfigManager();
        boolean foundPlayerFile = playersConfigManager.resetKitForPlayer(uuid, kitName);

        if(!foundPlayerData && !foundPlayerFile){
            callback.onDone(getPlayerDataNotFoundResult(target));
            return;
        }
        callback.onDone(PlayerKitsMessageResult.success());
    }

    private boolean resetLoadedPlayerData(UUID uuid, String kitName){
        PlayerData playerData = getPlayerByUUID(uuid);
        if(playerData != null){
            playerData.resetKit(kitName);
            return true;
        }
        return false;
    }

    private UUID tryParseUUID(String target){
        try{
            return UUID.fromString(target);
        }catch(Exception ignored){
            return null;
        }
    }

    private PlayerKitsMessageResult getPlayerDataNotFoundResult(String target){
        FileConfiguration messagesConfig = plugin.getConfigsManager().getMessagesConfigManager().getConfig();
        String message = messagesConfig.getString("playerDataNotFound");
        if(message == null){
            message = "&cNo data found for player &7%player%&c.";
        }
        return PlayerKitsMessageResult.error(message.replace("%player%",target));
    }


    public void resetKitForAllPlayers(String kitName, GenericCallback<PlayerKitsMessageResult> callback){
        TaskUtils.runAsync(plugin, () -> {
            MySQLConnection mySQLConnection = plugin.getActiveMySQLConnection();
            if (mySQLConnection == null) {
                PlayersConfigManager playerConfigsManager = plugin.getConfigsManager().getPlayersConfigManager();
                playerConfigsManager.resetKitForAllPlayers(kitName);

                players.values().forEach(p -> p.resetKit(kitName));
                callback.onDone(PlayerKitsMessageResult.success());
                return;
            }

            players.values().forEach(p -> p.resetKit(kitName));
            mySQLConnection.resetKit(null,kitName,true, () -> {
                publishResetAll(kitName);
                callback.onDone(PlayerKitsMessageResult.success());
            });
        });
    }

    public void applySyncedKitState(UUID uuid, String kitName, long cooldown, boolean oneTime, boolean bought){
        PlayerData playerData = getPlayerByUUID(uuid);
        if(playerData == null){
            return;
        }
        playerData.setKitState(kitName,cooldown,oneTime,bought);
    }

    public void applySyncedResetKit(UUID uuid, String kitName){
        PlayerData playerData = getPlayerByUUID(uuid);
        if(playerData == null){
            return;
        }
        playerData.removeKit(kitName,false);
    }

    public void applySyncedResetAll(String kitName){
        players.values().forEach(playerData -> playerData.removeKit(kitName,false));
    }

    public void manageJoin(Player player){
        UUID uuid = player.getUniqueId();
        setPlayerLoading(uuid,true);

        MySQLConnection mySQLConnection = plugin.getActiveMySQLConnection();
        if(mySQLConnection != null){
            mySQLConnection.getPlayer(uuid.toString(), playerData -> {
                TaskUtils.runEntity(plugin, player, () -> {
                    if (!player.isOnline()) {
                        setPlayerLoading(uuid,false);
                        return;
                    }
                    if(playerData != null) {
                        addPlayer(playerData);
                        //Update name if different
                        if (playerData.getName() == null || !playerData.getName().equals(player.getName())) {
                            updatePlayerName(playerData.getName(), player.getName(), player.getUniqueId());
                            playerData.setName(player.getName());
                            mySQLConnection.updatePlayerName(playerData);
                        }
                        setPlayerLoading(uuid,false);
                    }else {
                        PlayerData newPlayerData = new PlayerData(uuid, player.getName());
                        addPlayer(newPlayerData);

                        //Create if it doesn't exist + first join kit
                        mySQLConnection.createPlayer(newPlayerData, () -> TaskUtils.runEntity(plugin, player, () -> {
                            if (player.isOnline()) {
                                setPlayerLoading(uuid,false);
                                plugin.getKitsManager().giveFirstJoinKit(player);
                            }else{
                                setPlayerLoading(uuid,false);
                            }
                        }));
                    }
                });
            });
        }else{
            // Load player data from file if exists
            plugin.getConfigsManager().getPlayersConfigManager().loadConfig(uuid, playerData -> {
                TaskUtils.runEntity(plugin, player, () -> {
                    if (!player.isOnline()) {
                        setPlayerLoading(uuid,false);
                        return;
                    }
                    if(playerData != null){
                        addPlayer(playerData);
                        if(playerData.getName() == null || !playerData.getName().equals(player.getName())){
                            updatePlayerName(playerData.getName(),player.getName(),player.getUniqueId());
                            playerData.setName(player.getName());
                            playerData.setModified(true);
                        }
                        setPlayerLoading(uuid,false);
                    }else{
                        // Create it if it doesn't exist.
                        PlayerData newPlayerData = new PlayerData(player.getUniqueId(),player.getName());
                        newPlayerData.setModified(true);
                        addPlayer(newPlayerData);
                        setPlayerLoading(uuid,false);

                        // First join kit
                        plugin.getKitsManager().giveFirstJoinKit(player);
                    }
                });
            });
        }
    }

    public void manageLeave(Player player){
        // Save player data into file and remove from map
        PlayerData playerData = getPlayer(player,false);
        if(playerData != null){
            if(!plugin.isMySQLActive()) {
                if(playerData.isModified()){
                    TaskUtils.runAsync(plugin, () -> {
                        plugin.getConfigsManager().getPlayersConfigManager().saveConfig(playerData);
                    });
                }
            }

            removePlayer(playerData);
        }
        setPlayerLoading(player.getUniqueId(),false);
    }
}
