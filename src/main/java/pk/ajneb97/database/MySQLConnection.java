package pk.ajneb97.database;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.managers.MessagesManager;
import pk.ajneb97.model.PlayerData;
import pk.ajneb97.model.PlayerDataKit;
import pk.ajneb97.model.internal.GenericCallback;
import pk.ajneb97.model.internal.SimpleCallback;
import pk.ajneb97.utils.TaskUtils;

import java.sql.DatabaseMetaData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class MySQLConnection {

    private PlayerKits2 plugin;
    private HikariConnection connection;
    private volatile boolean active;

    private static final String PLAYER_KIT_UNIQUE_KEY = "pk_playerkits_uuid_name_unique";
    private static final int UUID_LENGTH = 36;
    private static final int KIT_NAME_LENGTH = 100;

    public MySQLConnection(PlayerKits2 plugin){
        this.plugin = plugin;
        this.active = false;
    }

    public void setupMySql(){
        FileConfiguration config = plugin.getConfigsManager().getMainConfigManager().getConfig();
        try {
            connection = new HikariConnection(config);
            try(Connection ignored = connection.getHikari().getConnection()){
                // Connection validation
            }
            active = true;
            if(!createTables()){
                active = false;
                disable();
                Bukkit.getConsoleSender().sendMessage(MessagesManager.getLegacyColoredMessage(plugin.prefix+" &cError while connecting to the Database."));
                return;
            }
            Bukkit.getConsoleSender().sendMessage(MessagesManager.getLegacyColoredMessage(plugin.prefix+" &aSuccessfully connected to the Database."));
        }catch(Exception e) {
            active = false;
            disable();
            Bukkit.getConsoleSender().sendMessage(MessagesManager.getLegacyColoredMessage(plugin.prefix+" &cError while connecting to the Database."));
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred in PlayerKits2", e);
        }
    }

    public Connection getConnection() {
        if(!isActive()){
            return null;
        }
        try {
            return connection.getHikari().getConnection();
        } catch (Exception e) {
            active = false;
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred in PlayerKits2", e);
            return null;
        }
    }

    public boolean isActive() {
        return active && connection != null;
    }

    public boolean createTables() {
        try(Connection connection = getConnection()){
            if(connection == null){
                return false;
            }
            PreparedStatement statement1 = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS playerkits_players" +
                    " (UUID CHAR(" + UUID_LENGTH + ") NOT NULL, " +
                    " PLAYER_NAME varchar(50), " +
                    " PRIMARY KEY ( UUID ))"
            );
            statement1.executeUpdate();
            PreparedStatement statement2 = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS playerkits_players_kits" +
                    " (ID int NOT NULL AUTO_INCREMENT, " +
                    " UUID CHAR(" + UUID_LENGTH + ") NOT NULL, " +
                    " NAME varchar(" + KIT_NAME_LENGTH + "), " +
                    " COOLDOWN BIGINT, " +
                    " ONE_TIME BOOLEAN, " +
                    " BOUGHT BOOLEAN, " +
                    " PRIMARY KEY ( ID ), " +
                    " FOREIGN KEY (UUID) REFERENCES playerkits_players(UUID))");
            statement2.executeUpdate();

            removeDuplicatePlayerKitRows(connection);
            ensurePlayerKitUniqueConstraint(connection);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred in PlayerKits2", e);
            return false;
        }
    }

    private void removeDuplicatePlayerKitRows(Connection connection) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                "DELETE older FROM playerkits_players_kits older " +
                        "INNER JOIN playerkits_players_kits newer " +
                        "ON older.UUID = newer.UUID AND older.NAME = newer.NAME AND older.ID < newer.ID");
        statement.executeUpdate();
        statement.close();
    }

    private void ensurePlayerKitUniqueConstraint(Connection connection) throws SQLException {
        if(hasIndex(connection, "playerkits_players_kits", PLAYER_KIT_UNIQUE_KEY)) {
            return;
        }

        try(PreparedStatement statement = connection.prepareStatement(
                "ALTER TABLE playerkits_players_kits " +
                        "ADD CONSTRAINT " + PLAYER_KIT_UNIQUE_KEY + " UNIQUE (UUID, NAME)")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            if(!isKeyTooLongError(e)) {
                throw e;
            }
            // Compatibility fallback for old schemas/databases with low index byte limits.
            try(PreparedStatement statement = connection.prepareStatement(
                    "ALTER TABLE playerkits_players_kits " +
                            "ADD CONSTRAINT " + PLAYER_KIT_UNIQUE_KEY + " UNIQUE (UUID(" + UUID_LENGTH + "), NAME(" + KIT_NAME_LENGTH + "))")) {
                statement.executeUpdate();
            }
        }
    }

    private boolean isKeyTooLongError(SQLException e) {
        return e.getErrorCode() == 1071 ||
                (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("key was too long"));
    }

    private boolean hasIndex(Connection connection, String tableName, String indexName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        ResultSet resultSet = metaData.getIndexInfo(catalog, null, tableName, false, false);
        while(resultSet.next()) {
            String currentIndexName = resultSet.getString("INDEX_NAME");
            if(currentIndexName != null && currentIndexName.equalsIgnoreCase(indexName)) {
                resultSet.close();
                return true;
            }
        }
        resultSet.close();
        return false;
    }

    public void getPlayer(String uuid, GenericCallback<PlayerData> callback){
        TaskUtils.runAsync(plugin, () -> {
            PlayerData player = null;
            try(Connection connection = getConnection()){
                if(connection == null){
                    callback.onDone(null);
                    return;
                }
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT playerkits_players.UUID, playerkits_players.PLAYER_NAME, " +
                                "playerkits_players_kits.NAME, " +
                                "playerkits_players_kits.COOLDOWN, " +
                                "playerkits_players_kits.ONE_TIME, " +
                                "playerkits_players_kits.BOUGHT " +
                                "FROM playerkits_players LEFT JOIN playerkits_players_kits " +
                                "ON playerkits_players.UUID = playerkits_players_kits.UUID " +
                                "WHERE playerkits_players.UUID = ?");

                statement.setString(1, uuid);
                ResultSet result = statement.executeQuery();

                while(result.next()){
                    UUID uuid1 = UUID.fromString(result.getString("UUID"));
                    String playerName = result.getString("PLAYER_NAME");
                    String kitName = result.getString("NAME");
                    long cooldown = result.getLong("COOLDOWN");
                    boolean oneTime = result.getBoolean("ONE_TIME");
                    boolean bought = result.getBoolean("BOUGHT");
                    if(player == null){
                        player = new PlayerData(uuid1,playerName);
                    }
                    if(kitName != null){
                        PlayerDataKit playerDataKit = new PlayerDataKit(kitName);
                        playerDataKit.setCooldown(cooldown);
                        playerDataKit.setOneTime(oneTime);
                        playerDataKit.setBought(bought);
                        player.addKit(playerDataKit);
                    }
                }

                callback.onDone(player);
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred in PlayerKits2", e);
                callback.onDone(null);
            }
        });
    }

    public void createPlayer(PlayerData player, SimpleCallback callback){
        TaskUtils.runAsync(plugin, () -> {
            try(Connection connection = getConnection()){
                if(connection == null){
                    return;
                }
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO playerkits_players " +
                                "(UUID, PLAYER_NAME) VALUES (?,?) " +
                                "ON DUPLICATE KEY UPDATE PLAYER_NAME=VALUES(PLAYER_NAME)");

                statement.setString(1, player.getUuid().toString());
                statement.setString(2, player.getName());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred in PlayerKits2", e);
            } finally {
                callback.onDone();
            }
        });
    }

    public void updatePlayerName(PlayerData player){
        TaskUtils.runAsync(plugin, () -> {
            try(Connection connection = getConnection()){
                if(connection == null){
                    return;
                }
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE playerkits_players SET " +
                                "PLAYER_NAME=? WHERE UUID=?");

                statement.setString(1, player.getName());
                statement.setString(2, player.getUuid().toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred in PlayerKits2", e);
            }
        });
    }

    public void updateKit(PlayerData player,PlayerDataKit kit){
        TaskUtils.runAsync(plugin, () -> {
            try(Connection connection = getConnection()){
                if(connection == null){
                    return;
                }
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO playerkits_players_kits " +
                                "(UUID, NAME, COOLDOWN, ONE_TIME, BOUGHT) VALUES (?,?,?,?,?) " +
                                "ON DUPLICATE KEY UPDATE " +
                                "COOLDOWN=VALUES(COOLDOWN), ONE_TIME=VALUES(ONE_TIME), BOUGHT=VALUES(BOUGHT)");

                statement.setString(1, player.getUuid().toString());
                statement.setString(2, kit.getName());
                statement.setLong(3, kit.getCooldown());
                statement.setBoolean(4, kit.isOneTime());
                statement.setBoolean(5, kit.isBought());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred in PlayerKits2", e);
            }
        });
    }

    public void resetKit(String uuid,String kitName,boolean all){
        resetKit(uuid,kitName,all,null);
    }

    public void resetKit(String uuid,String kitName,boolean all, SimpleCallback callback){
        TaskUtils.runAsync(plugin, () -> {
            try(Connection connection = getConnection()){
                if(connection == null){
                    return;
                }
                PreparedStatement statement;
                if(all){
                    statement = connection.prepareStatement(
                            "DELETE FROM playerkits_players_kits " +
                                    "WHERE NAME=?");
                    statement.setString(1, kitName);
                }else{
                    statement = connection.prepareStatement(
                            "DELETE FROM playerkits_players_kits " +
                                    "WHERE UUID=? AND NAME=?");

                    statement.setString(1, uuid);
                    statement.setString(2, kitName);
                }
                statement.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred in PlayerKits2", e);
            } finally {
                if(callback != null){
                    callback.onDone();
                }
            }
        });
    }

    public void getPlayerUUIDByName(String playerName, GenericCallback<UUID> callback){
        TaskUtils.runAsync(plugin, () -> {
            UUID uuid = null;
            try(Connection connection = getConnection()){
                if(connection == null){
                    callback.onDone(null);
                    return;
                }
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT UUID FROM playerkits_players WHERE LOWER(PLAYER_NAME)=LOWER(?) LIMIT 1");
                statement.setString(1, playerName);
                ResultSet result = statement.executeQuery();
                if(result.next()){
                    uuid = UUID.fromString(result.getString("UUID"));
                }
            } catch (SQLException | IllegalArgumentException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred in PlayerKits2", e);
            }
            callback.onDone(uuid);
        });
    }

    public void getAllPlayerNames(GenericCallback<List<String>> callback){
        TaskUtils.runAsync(plugin, () -> {
            List<String> names = new ArrayList<>();
            try(Connection connection = getConnection()){
                if(connection == null){
                    callback.onDone(names);
                    return;
                }
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT PLAYER_NAME FROM playerkits_players WHERE PLAYER_NAME IS NOT NULL AND PLAYER_NAME <> ''");
                ResultSet result = statement.executeQuery();
                while(result.next()){
                    String playerName = result.getString("PLAYER_NAME");
                    if(playerName != null && !playerName.isEmpty()){
                        names.add(playerName);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred in PlayerKits2", e);
            }
            callback.onDone(names);
        });
    }

    public void disable() {
        active = false;
        if(connection != null){
            connection.disable();
            connection = null;
        }
    }
}
