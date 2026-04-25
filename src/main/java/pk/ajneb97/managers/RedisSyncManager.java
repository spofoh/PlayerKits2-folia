package pk.ajneb97.managers;

import org.bukkit.Bukkit;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.configs.MainConfigManager;
import pk.ajneb97.model.PlayerDataKit;
import pk.ajneb97.utils.TaskUtils;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPubSub;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class RedisSyncManager {

    private static final String MESSAGE_PREFIX = "PK2";
    private static final String TYPE_KIT_STATE = "KIT_STATE";
    private static final String TYPE_RESET_PLAYER = "RESET_PLAYER";
    private static final String TYPE_RESET_ALL = "RESET_ALL";

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

    public RedisSyncManager(PlayerKits2 plugin){
        this.plugin = plugin;
        this.instanceId = UUID.randomUUID().toString();
        this.active = false;
        this.shutdownRequested = false;
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
    }

    public boolean isActive() {
        return active && !shutdownRequested;
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
            default:
                break;
        }
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
}
