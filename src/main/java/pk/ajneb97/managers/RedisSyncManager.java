package pk.ajneb97.managers;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.configs.MainConfigManager;
import pk.ajneb97.model.Kit;
import pk.ajneb97.model.internal.GenericCallback;
import pk.ajneb97.model.internal.GiveKitInstructions;
import pk.ajneb97.model.internal.PlayerKitsMessageResult;
import pk.ajneb97.model.inventory.InventoryPlayer;
import pk.ajneb97.model.PlayerDataKit;
import pk.ajneb97.utils.TaskUtils;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPubSub;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RedisSyncManager {

    private static final String MESSAGE_PREFIX = "PK2";
    private static final String TYPE_KIT_STATE = "KIT_STATE";
    private static final String TYPE_RESET_PLAYER = "RESET_PLAYER";
    private static final String TYPE_RESET_ALL = "RESET_ALL";
    private static final String TYPE_ONLINE_SNAPSHOT = "ONLINE_SNAPSHOT";
    private static final String TYPE_COMMAND_REQUEST = "COMMAND_REQUEST";
    private static final String TYPE_COMMAND_RESPONSE = "COMMAND_RESPONSE";

    public static final String COMMAND_TYPE_GIVE = "GIVE";
    public static final String COMMAND_TYPE_OPEN = "OPEN";
    public static final String COMMAND_TYPE_PREVIEW = "PREVIEW";

    private static final long NETWORK_PLAYER_STALE_MILLIS = 30_000L;
    private static final long REMOTE_COMMAND_TIMEOUT_TICKS = 100L;

    private final PlayerKits2 plugin;
    private final String instanceId;

    private volatile boolean active;
    private volatile boolean shutdownRequested;

    private String host;
    private int port;
    private String password;
    private int database;
    private int timeout;
    private boolean ssl;
    private String channel;

    private volatile Thread subscriberThread;
    private volatile Jedis subscriberJedis;
    private volatile JedisPubSub subscriber;

    // lowerCaseName -> instanceId
    private final Map<String, String> networkPlayerInstances;
    // lowerCaseName -> displayName
    private final Map<String, String> networkPlayerNames;
    // instanceId -> lowerCase names
    private final Map<String, Set<String>> networkInstancePlayers;
    // instanceId -> last snapshot millis
    private final Map<String, Long> networkInstanceLastSeen;
    private final Map<String, PendingCommandRequest> pendingCommandRequests;

    public RedisSyncManager(PlayerKits2 plugin){
        this.plugin = plugin;
        this.instanceId = UUID.randomUUID().toString();
        this.active = false;
        this.shutdownRequested = false;
        this.networkPlayerInstances = new ConcurrentHashMap<>();
        this.networkPlayerNames = new ConcurrentHashMap<>();
        this.networkInstancePlayers = new ConcurrentHashMap<>();
        this.networkInstanceLastSeen = new ConcurrentHashMap<>();
        this.pendingCommandRequests = new ConcurrentHashMap<>();
    }

    public void setup(){
        MainConfigManager config = plugin.getConfigsManager().getMainConfigManager();
        if(!config.isRedisSyncEnabled()){
            return;
        }

        this.host = config.getRedisSyncHost();
        this.port = config.getRedisSyncPort();
        this.password = config.getRedisSyncPassword();
        this.database = config.getRedisSyncDatabase();
        this.timeout = config.getRedisSyncTimeout();
        this.ssl = config.isRedisSyncSSL();
        this.channel = config.getRedisSyncChannel();
        if(this.channel == null || this.channel.trim().isEmpty()){
            this.channel = "playerkits2:sync";
        }

        try(Jedis jedis = createJedis()){
            jedis.ping();
        }catch(Exception e){
            this.active = false;
            Bukkit.getConsoleSender().sendMessage(MessagesManager.getLegacyColoredMessage(
                    PlayerKits2.prefix+" &cError while connecting to Redis sync."));
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred in PlayerKits2", e);
            return;
        }

        this.active = true;
        this.shutdownRequested = false;
        startSubscriber();
        startPresencePublisher();
        publishOnlineSnapshot();

        Bukkit.getConsoleSender().sendMessage(MessagesManager.getLegacyColoredMessage(
                PlayerKits2.prefix+" &aRedis sync enabled on channel &7"+channel+"&a."));
    }

    private Jedis createJedis(){
        JedisClientConfig jedisClientConfig = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(timeout)
                .socketTimeoutMillis(timeout)
                .password(password != null && !password.isEmpty() ? password : null)
                .database(database)
                .ssl(ssl)
                .build();
        return new Jedis(new HostAndPort(host, port), jedisClientConfig);
    }

    private void startSubscriber(){
        subscriberThread = new Thread(() -> {
            while(active && !shutdownRequested){
                try(Jedis jedis = createJedis()){
                    subscriberJedis = jedis;
                    RedisSubscriber redisSubscriber = new RedisSubscriber();
                    subscriber = redisSubscriber;
                    jedis.subscribe(redisSubscriber, channel);
                }catch(Exception e){
                    if(!shutdownRequested){
                        plugin.getLogger().log(java.util.logging.Level.WARNING,
                                "Redis sync subscriber disconnected, retrying in 3 seconds.", e);
                        sleep(3000L);
                    }
                } finally {
                    subscriber = null;
                    subscriberJedis = null;
                }
            }
        }, "PlayerKits2-RedisSync");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    public void disable(){
        if(isActive()){
            publishOnlineSnapshotMessage("");
        }

        shutdownRequested = true;
        active = false;

        JedisPubSub currentSubscriber = subscriber;
        if(currentSubscriber != null){
            try{
                currentSubscriber.unsubscribe();
            }catch(Exception ignored){
            }
        }

        Jedis currentSubscriberJedis = subscriberJedis;
        if(currentSubscriberJedis != null){
            try{
                currentSubscriberJedis.close();
            }catch(Exception ignored){
            }
        }

        Thread thread = subscriberThread;
        if(thread != null){
            thread.interrupt();
            try{
                thread.join(1000L);
            }catch(InterruptedException e){
                Thread.currentThread().interrupt();
            }
            subscriberThread = null;
        }

        networkPlayerInstances.clear();
        networkPlayerNames.clear();
        networkInstancePlayers.clear();
        networkInstanceLastSeen.clear();
        pendingCommandRequests.clear();
    }

    public boolean isActive() {
        return active && !shutdownRequested;
    }

    public String getOnlinePlayerRemoteInstance(String playerName){
        if(playerName == null){
            return null;
        }
        cleanupStaleNetworkPlayers();
        return networkPlayerInstances.get(playerName.toLowerCase(Locale.ROOT));
    }

    public List<String> getNetworkOnlinePlayerCompletions(String currentArg){
        String lowerArg = currentArg.toLowerCase(Locale.ROOT);
        Set<String> completions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for(Player player : Bukkit.getOnlinePlayers()){
            String playerName = player.getName();
            if(currentArg.isEmpty() || playerName.toLowerCase(Locale.ROOT).startsWith(lowerArg)){
                completions.add(playerName);
            }
        }

        cleanupStaleNetworkPlayers();
        for(String playerName : networkPlayerNames.values()){
            if(playerName == null){
                continue;
            }
            if(currentArg.isEmpty() || playerName.toLowerCase(Locale.ROOT).startsWith(lowerArg)){
                completions.add(playerName);
            }
        }

        if(completions.isEmpty()){
            return null;
        }
        return new ArrayList<>(completions);
    }

    public void publishOnlineSnapshot(){
        if(!isActive()){
            return;
        }
        TaskUtils.runSync(plugin, () -> {
            if(!isActive()){
                return;
            }
            StringBuilder playersBuilder = new StringBuilder();
            for(Player player : Bukkit.getOnlinePlayers()){
                if(playersBuilder.length() > 0){
                    playersBuilder.append(",");
                }
                playersBuilder.append(player.getName());
            }
            publishOnlineSnapshotMessage(playersBuilder.toString());
        });
    }

    public void publishKitState(UUID uuid, PlayerDataKit playerDataKit){
        if(!isActive() || playerDataKit == null){
            return;
        }
        String message = MESSAGE_PREFIX + "|" + instanceId + "|" + TYPE_KIT_STATE + "|" +
                uuid + "|" + encode(playerDataKit.getName()) + "|" +
                playerDataKit.getCooldown() + "|" + playerDataKit.isOneTime() + "|" + playerDataKit.isBought();
        publish(message);
    }

    public void publishResetPlayer(UUID uuid, String kitName){
        if(!isActive()){
            return;
        }
        String message = MESSAGE_PREFIX + "|" + instanceId + "|" + TYPE_RESET_PLAYER + "|" +
                uuid + "|" + encode(kitName);
        publish(message);
    }

    public void publishResetAll(String kitName){
        if(!isActive()){
            return;
        }
        String message = MESSAGE_PREFIX + "|" + instanceId + "|" + TYPE_RESET_ALL + "|" + encode(kitName);
        publish(message);
    }

    public void sendRemoteCommandRequest(String targetInstanceId, String commandType, String arg1, String arg2,
                                         GenericCallback<RemoteCommandResponse> callback){
        if(!isActive()){
            if(callback != null){
                callback.onDone(RemoteCommandResponse.error("&cCross-server sync is not available."));
            }
            return;
        }
        if(targetInstanceId == null || targetInstanceId.isEmpty()){
            if(callback != null){
                callback.onDone(RemoteCommandResponse.error("&cTarget server is not available."));
            }
            return;
        }

        String requestId = UUID.randomUUID().toString();
        if(callback != null){
            pendingCommandRequests.put(requestId, new PendingCommandRequest(callback));
            TaskUtils.runSyncLater(plugin, () -> {
                PendingCommandRequest pending = pendingCommandRequests.remove(requestId);
                if(pending != null){
                    deliverPendingResponse(pending, RemoteCommandResponse.error("&cCross-server request timed out."));
                }
            }, REMOTE_COMMAND_TIMEOUT_TICKS);
        }

        String message = MESSAGE_PREFIX + "|" + instanceId + "|" + TYPE_COMMAND_REQUEST + "|" +
                targetInstanceId + "|" + requestId + "|" + commandType + "|" + encode(arg1) + "|" + encode(arg2);
        publish(message);
    }

    private void publish(String message){
        TaskUtils.runAsync(plugin, () -> {
            try(Jedis jedis = createJedis()){
                jedis.publish(channel, message);
            }catch(Exception e){
                if(!shutdownRequested){
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "Error publishing Redis sync event.", e);
                }
            }
        });
    }

    private void publishOnlineSnapshotMessage(String playerNames){
        if(!isActive()){
            return;
        }
        String message = MESSAGE_PREFIX + "|" + instanceId + "|" + TYPE_ONLINE_SNAPSHOT + "|" + encode(playerNames);
        publish(message);
    }

    private void startPresencePublisher(){
        TaskUtils.runAsyncTimer(plugin, () -> {
            if(!isActive()){
                return;
            }
            publishOnlineSnapshot();
            cleanupStaleNetworkPlayers();
        }, 100L, 100L);
    }

    private void cleanupStaleNetworkPlayers(){
        long now = System.currentTimeMillis();
        for(Map.Entry<String,Long> entry : networkInstanceLastSeen.entrySet()){
            String remoteInstanceId = entry.getKey();
            Long lastSeen = entry.getValue();
            if(lastSeen == null || (now - lastSeen) <= NETWORK_PLAYER_STALE_MILLIS){
                continue;
            }
            removeInstancePlayers(remoteInstanceId);
        }
    }

    private void removeInstancePlayers(String remoteInstanceId){
        networkInstanceLastSeen.remove(remoteInstanceId);
        Set<String> players = networkInstancePlayers.remove(remoteInstanceId);
        if(players == null){
            return;
        }
        for(String lowerName : players){
            if(remoteInstanceId.equals(networkPlayerInstances.get(lowerName))){
                networkPlayerInstances.remove(lowerName);
                networkPlayerNames.remove(lowerName);
            }
        }
    }

    private void processMessage(String message){
        if(message == null || message.isEmpty()){
            return;
        }
        String[] args = message.split("\\|",-1);
        if(args.length < 4){
            return;
        }
        if(!MESSAGE_PREFIX.equals(args[0])){
            return;
        }
        if(instanceId.equals(args[1])){
            return;
        }

        String messageType = args[2];
        switch(messageType){
            case TYPE_KIT_STATE:
                if(args.length < 8){
                    return;
                }
                UUID uuidState = parseUUID(args[3]);
                String kitNameState = decode(args[4]);
                Long cooldown = parseLong(args[5]);
                if(uuidState == null || kitNameState == null || cooldown == null){
                    return;
                }
                boolean oneTime = Boolean.parseBoolean(args[6]);
                boolean bought = Boolean.parseBoolean(args[7]);
                plugin.getPlayerDataManager().applySyncedKitState(uuidState,kitNameState,cooldown,oneTime,bought);
                break;
            case TYPE_RESET_PLAYER:
                if(args.length < 5){
                    return;
                }
                UUID uuidReset = parseUUID(args[3]);
                String kitNameReset = decode(args[4]);
                if(uuidReset == null || kitNameReset == null){
                    return;
                }
                plugin.getPlayerDataManager().applySyncedResetKit(uuidReset,kitNameReset);
                break;
            case TYPE_RESET_ALL:
                String kitNameResetAll = decode(args[3]);
                if(kitNameResetAll == null){
                    return;
                }
                plugin.getPlayerDataManager().applySyncedResetAll(kitNameResetAll);
                break;
            case TYPE_ONLINE_SNAPSHOT:
                if(args.length < 4){
                    return;
                }
                processOnlineSnapshot(args[1], decode(args[3]));
                break;
            case TYPE_COMMAND_REQUEST:
                if(args.length < 8){
                    return;
                }
                processCommandRequest(args[1], args[3], args[4], args[5], decode(args[6]), decode(args[7]));
                break;
            case TYPE_COMMAND_RESPONSE:
                if(args.length < 7){
                    return;
                }
                processCommandResponse(args[3], args[4], args[5], decode(args[6]));
                break;
            default:
                break;
        }
    }

    private void processOnlineSnapshot(String remoteInstanceId, String playerNamesRaw){
        if(playerNamesRaw == null){
            return;
        }
        Set<String> newNames = new HashSet<>();
        Map<String,String> displayNames = new ConcurrentHashMap<>();

        if(!playerNamesRaw.isEmpty()){
            String[] players = playerNamesRaw.split(",");
            for(String playerName : players){
                if(playerName == null || playerName.isEmpty()){
                    continue;
                }
                String lowerName = playerName.toLowerCase(Locale.ROOT);
                newNames.add(lowerName);
                displayNames.put(lowerName, playerName);
            }
        }

        Set<String> oldNames = networkInstancePlayers.get(remoteInstanceId);
        if(oldNames != null){
            for(String oldName : oldNames){
                if(!newNames.contains(oldName) && remoteInstanceId.equals(networkPlayerInstances.get(oldName))){
                    networkPlayerInstances.remove(oldName);
                    networkPlayerNames.remove(oldName);
                }
            }
        }

        for(Map.Entry<String,String> entry : displayNames.entrySet()){
            String lowerName = entry.getKey();
            String displayName = entry.getValue();
            networkPlayerInstances.put(lowerName, remoteInstanceId);
            networkPlayerNames.put(lowerName, displayName);
        }

        networkInstancePlayers.put(remoteInstanceId, newNames);
        networkInstanceLastSeen.put(remoteInstanceId, System.currentTimeMillis());
    }

    private void processCommandRequest(String sourceInstanceId, String targetInstanceId, String requestId,
                                       String commandType, String arg1, String arg2){
        if(!instanceId.equals(targetInstanceId)){
            return;
        }
        String safeArg1 = arg1 != null ? arg1 : "";
        String safeArg2 = arg2 != null ? arg2 : "";

        TaskUtils.runSync(plugin, () -> {
            RemoteCommandResponse response;
            switch(commandType){
                case COMMAND_TYPE_GIVE:
                    response = executeRemoteGive(safeArg1, safeArg2);
                    break;
                case COMMAND_TYPE_OPEN:
                    response = executeRemoteOpen(safeArg1, safeArg2);
                    break;
                case COMMAND_TYPE_PREVIEW:
                    response = executeRemotePreview(safeArg1, safeArg2);
                    break;
                default:
                    response = RemoteCommandResponse.error("&cUnknown cross-server command.");
                    break;
            }
            publishCommandResponse(sourceInstanceId, requestId, response.isSuccess(), response.getMessage());
        });
    }

    private void processCommandResponse(String targetInstanceId, String requestId, String successText, String responseMessage){
        if(!instanceId.equals(targetInstanceId)){
            return;
        }
        PendingCommandRequest pending = pendingCommandRequests.remove(requestId);
        if(pending == null){
            return;
        }
        boolean success = Boolean.parseBoolean(successText);
        deliverPendingResponse(pending, new RemoteCommandResponse(success, responseMessage));
    }

    private void deliverPendingResponse(PendingCommandRequest pending, RemoteCommandResponse response){
        GenericCallback<RemoteCommandResponse> callback = pending.callback;
        if(callback == null){
            return;
        }
        TaskUtils.runSync(plugin, () -> callback.onDone(response));
    }

    private void publishCommandResponse(String targetInstanceId, String requestId, boolean success, String responseMessage){
        if(!isActive()){
            return;
        }
        String message = MESSAGE_PREFIX + "|" + instanceId + "|" + TYPE_COMMAND_RESPONSE + "|" +
                targetInstanceId + "|" + requestId + "|" + success + "|" + encode(responseMessage);
        publish(message);
    }

    private RemoteCommandResponse executeRemoteGive(String kitName, String playerName){
        FileConfiguration messagesConfig = plugin.getConfigsManager().getMessagesConfigManager().getConfig();
        Player player = getOnlinePlayer(playerName);
        if(player == null){
            String msg = getMessage(messagesConfig, "playerNotOnline", "&cPlayer &7%player% &cis not online.");
            return RemoteCommandResponse.error(msg.replace("%player%", playerName));
        }

        PlayerKitsMessageResult result = plugin.getKitsManager().giveKit(player, kitName, new GiveKitInstructions(true,false,false,false));
        if(result.isError()){
            String msg = getMessage(messagesConfig, "commandGiveError2", "&cThere was an error giving the kit: &7%error%");
            String error = result.getMessage() != null ? result.getMessage() : "";
            return RemoteCommandResponse.error(msg.replace("%error%", error));
        }

        String msg = getMessage(messagesConfig, "commandGiveCorrect", "&aKit &7%kit% &agiven to &e%player%&a!");
        return RemoteCommandResponse.success(msg.replace("%kit%", kitName).replace("%player%", player.getName()));
    }

    private RemoteCommandResponse executeRemoteOpen(String inventoryName, String playerName){
        FileConfiguration messagesConfig = plugin.getConfigsManager().getMessagesConfigManager().getConfig();
        if(plugin.getInventoryManager().getInventory(inventoryName) == null){
            return RemoteCommandResponse.error(getMessage(messagesConfig, "inventoryNotExists", "&cThat inventory doesn't exists."));
        }

        Player player = getOnlinePlayer(playerName);
        if(player == null){
            String msg = getMessage(messagesConfig, "playerNotOnline", "&cPlayer &7%player% &cis not online.");
            return RemoteCommandResponse.error(msg.replace("%player%", playerName));
        }

        InventoryPlayer inventoryPlayer = new InventoryPlayer(player, inventoryName);
        TaskUtils.runEntity(plugin, player, () -> {
            if(!player.isOnline()){
                return;
            }
            plugin.getInventoryManager().openInventory(inventoryPlayer);
        });

        String msg = getMessage(messagesConfig, "commandOpenCorrect", "&aOpening inventory &7%inventory% &afor &e%player%&a.");
        return RemoteCommandResponse.success(msg.replace("%inventory%", inventoryName).replace("%player%", player.getName()));
    }

    private RemoteCommandResponse executeRemotePreview(String kitName, String playerName){
        FileConfiguration messagesConfig = plugin.getConfigsManager().getMessagesConfigManager().getConfig();
        MainConfigManager mainConfigManager = plugin.getConfigsManager().getMainConfigManager();
        if(!mainConfigManager.isKitPreview()){
            return RemoteCommandResponse.error(getMessage(messagesConfig, "kitPreviewDisabled", "&cKit preview is disabled."));
        }

        Kit kit = plugin.getKitsManager().getKitByName(kitName);
        if(kit == null){
            String msg = getMessage(messagesConfig, "kitDoesNotExists", "&cThe kit &7%kit% &cdoesn't exists.");
            return RemoteCommandResponse.error(msg.replace("%kit%", kitName));
        }

        Player player = getOnlinePlayer(playerName);
        if(player == null){
            String msg = getMessage(messagesConfig, "playerNotOnline", "&cPlayer &7%player% &cis not online.");
            return RemoteCommandResponse.error(msg.replace("%player%", playerName));
        }

        InventoryPlayer inventoryPlayer = new InventoryPlayer(player,"preview_inventory");
        inventoryPlayer.setKitName(kitName);
        inventoryPlayer.setPreviousInventoryName("main_inventory");
        TaskUtils.runEntity(plugin, player, () -> {
            if(!player.isOnline()){
                return;
            }
            plugin.getInventoryManager().openInventory(inventoryPlayer);
        });

        String msg = getMessage(messagesConfig, "commandPreviewOtherCorrect", "&aPreviewing kit &7%kit% &ato &e%player%&a.");
        return RemoteCommandResponse.success(msg.replace("%kit%", kitName).replace("%player%", player.getName()));
    }

    private Player getOnlinePlayer(String playerName){
        Player exactPlayer = Bukkit.getPlayerExact(playerName);
        if(exactPlayer != null){
            return exactPlayer;
        }
        for(Player player : Bukkit.getOnlinePlayers()){
            if(player.getName().equalsIgnoreCase(playerName)){
                return player;
            }
        }
        return null;
    }

    private String getMessage(FileConfiguration messagesConfig, String path, String fallback){
        String msg = messagesConfig.getString(path);
        if(msg == null || msg.isEmpty()){
            return fallback;
        }
        return msg;
    }

    private UUID parseUUID(String uuid){
        try{
            return UUID.fromString(uuid);
        }catch(Exception ignored){
            return null;
        }
    }

    private Long parseLong(String value){
        try{
            return Long.parseLong(value);
        }catch(Exception ignored){
            return null;
        }
    }

    private String encode(String value){
        if(value == null){
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value){
        try{
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            return new String(bytes, StandardCharsets.UTF_8);
        }catch(Exception ignored){
            return null;
        }
    }

    private void sleep(long millis){
        try{
            Thread.sleep(millis);
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }

    private class RedisSubscriber extends JedisPubSub {
        @Override
        public void onMessage(String channel, String message) {
            processMessage(message);
        }
    }

    private static class PendingCommandRequest {
        private final GenericCallback<RemoteCommandResponse> callback;

        private PendingCommandRequest(GenericCallback<RemoteCommandResponse> callback){
            this.callback = callback;
        }
    }

    public static class RemoteCommandResponse {
        private final boolean success;
        private final String message;

        public RemoteCommandResponse(boolean success, String message){
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public static RemoteCommandResponse success(String message){
            return new RemoteCommandResponse(true, message);
        }

        public static RemoteCommandResponse error(String message){
            return new RemoteCommandResponse(false, message);
        }
    }
}
