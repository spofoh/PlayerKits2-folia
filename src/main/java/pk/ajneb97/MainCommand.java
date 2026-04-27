package pk.ajneb97;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import pk.ajneb97.configs.MainConfigManager;
import pk.ajneb97.managers.MessagesManager;
import pk.ajneb97.managers.PlayerDataManager;
import pk.ajneb97.managers.RedisSyncManager;
import pk.ajneb97.model.Kit;
import pk.ajneb97.model.internal.GiveKitInstructions;
import pk.ajneb97.model.internal.PlayerKitsMessageResult;
import pk.ajneb97.model.inventory.InventoryPlayer;
import pk.ajneb97.model.inventory.KitInventory;
import pk.ajneb97.utils.PlayerUtils;
import pk.ajneb97.utils.TaskUtils;

import java.util.ArrayList;
import java.util.List;

public class MainCommand implements CommandExecutor, TabCompleter {

    private PlayerKits2 plugin;
    public MainCommand(PlayerKits2 plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessagesManager msgManager = plugin.getMessagesManager();
        FileConfiguration messagesConfig = plugin.getConfigsManager().getMessagesConfigManager().getConfig();

        if (!(sender instanceof Player)) {
            if (args.length >= 1) {
                if (args[0].equalsIgnoreCase("reload")) {
                    reload(sender,args,messagesConfig,msgManager);
                }else if(args[0].equalsIgnoreCase("give")) {
                    give(sender,args,messagesConfig,msgManager);
                }else if(args[0].equalsIgnoreCase("delete")) {
                    delete(sender,args,messagesConfig,msgManager);
                }else if(args[0].equalsIgnoreCase("reset")) {
                    reset(sender,args,messagesConfig,msgManager);
                }else if(args[0].equalsIgnoreCase("migrate")) {
                    migrate(sender,args,messagesConfig,msgManager);
                }else if(args[0].equalsIgnoreCase("open")){
                    open(sender,args,messagesConfig,msgManager);
                }else if(args[0].equalsIgnoreCase("preview")){
                    preview(sender,args,messagesConfig,msgManager);
                }else{
                    help(sender,msgManager,messagesConfig);
                }
            }
            return true;
        }

        Player player = (Player) sender;

        boolean claimKitShortCommand = plugin.getConfigsManager().getMainConfigManager().getConfig().getBoolean("claim_kit_short_command");

        if(args.length >= 1){
            if(args[0].equalsIgnoreCase("claim") && !claimKitShortCommand){
                claim(player,args,messagesConfig,msgManager);
            }else if(args[0].equalsIgnoreCase("preview")){
                preview(player,args,messagesConfig,msgManager);
            }else if(args[0].equalsIgnoreCase("create")){
                create(player,args,messagesConfig,msgManager);
            }else if(args[0].equalsIgnoreCase("give")) {
                give(player,args,messagesConfig,msgManager);
            }else if(args[0].equalsIgnoreCase("delete")) {
                delete(player,args,messagesConfig,msgManager);
            }else if(args[0].equalsIgnoreCase("reload")) {
                reload(player,args,messagesConfig,msgManager);
            }else if(args[0].equalsIgnoreCase("reset")) {
                reset(sender,args,messagesConfig,msgManager);
            }else if(args[0].equalsIgnoreCase("edit")) {
                edit(player,args,messagesConfig,msgManager);
            }else if(args[0].equalsIgnoreCase("verify")){
                verify(player,messagesConfig,msgManager);
            }else if(args[0].equalsIgnoreCase("migrate")) {
                migrate(sender,args,messagesConfig,msgManager);
            }else if(args[0].equalsIgnoreCase("open")){
                open(sender,args,messagesConfig,msgManager);
            }
            else{
                // /kit <kit> (short command)
                if(claimKitShortCommand){
                    claimKitShortCommand(player,messagesConfig,msgManager,args[0]);
                    return true;
                }else{
                    help(sender,msgManager,messagesConfig);
                }
            }
        }else{
            // /kit
            if(plugin.getVerifyManager().isCriticalErrors()){
                msgManager.sendMessage(player,messagesConfig.getString("pluginCriticalErrors"),true);
                return true;
            }
            plugin.getInventoryManager().openInventory(new InventoryPlayer(player,"main_inventory"));
        }


        return true;
    }

    public void help(CommandSender sender,MessagesManager msgManager,FileConfiguration messagesConfig){
        if(!PlayerUtils.isPlayerKitsAdmin(sender)){
            msgManager.sendMessage(sender,messagesConfig.getString("commandDoesNotExists"),true);
            return;
        }
        sender.sendMessage(MessagesManager.getLegacyColoredMessage("&7[ [ &8[&bPlayerKits&a²&8] &7] ]"));
        sender.sendMessage(MessagesManager.getLegacyColoredMessage(" "));
        sender.sendMessage(MessagesManager.getLegacyColoredMessage("&6/kit &8Opens the GUI."));
        sender.sendMessage(MessagesManager.getLegacyColoredMessage("&6/kit claim <kit> &8Claims a kit outside the GUI."));
        sender.sendMessage(MessagesManager.getLegacyColoredMessage("&6/kit create <kit> (optional)original &8Creates a new kit using the items in your inventory."));
        sender.sendMessage(MessagesManager.getLegacyColoredMessage("&6/kit edit <kit> &8Edits a kit."));
        sender.sendMessage(MessagesManager.getLegacyColoredMessage("&6/kit give <kit> <player> &8Gives a kit to a player."));
        sender.sendMessage(MessagesManager.getLegacyColoredMessage("&6/kit delete <kit> &8Deletes a kit."));
        sender.sendMessage(MessagesManager.getLegacyColoredMessage("&6/kit reset <kit> <target>/* &8Resets kit data for a player name or UUID."));
        sender.sendMessage(MessagesManager.getLegacyColoredMessage("&6/kit preview <kit> (optional)<player> &8Previews a kit."));
        sender.sendMessage(MessagesManager.getLegacyColoredMessage("&6/kit open <inventory> <player> &8Opens a specific inventory for a player."));
        sender.sendMessage(MessagesManager.getLegacyColoredMessage("&6/kit reload &8Reloads the config."));
        sender.sendMessage(MessagesManager.getLegacyColoredMessage("&6/kit verify &8Checks the plugin for errors."));
        sender.sendMessage(MessagesManager.getLegacyColoredMessage(" "));
        sender.sendMessage(MessagesManager.getLegacyColoredMessage("&7[ [ &8[&bPlayerKits&a²&8] &7] ]"));
    }

    public void migrate(CommandSender sender,String[] args,FileConfiguration messagesConfig,MessagesManager msgManager){
        if(!PlayerUtils.isPlayerKitsAdmin(sender)){
            msgManager.sendMessage(sender,messagesConfig.getString("noPermissions"),true);
            return;
        }

        plugin.getMigrationManager().migrate(sender);
    }

    public void verify(Player player,FileConfiguration messagesConfig,MessagesManager msgManager){
        if(!PlayerUtils.isPlayerKitsAdmin(player)){
            msgManager.sendMessage(player,messagesConfig.getString("noPermissions"),true);
            return;
        }
        plugin.getVerifyManager().sendVerification(player);
    }

    public void reload(CommandSender sender,String[] args,FileConfiguration messagesConfig,MessagesManager msgManager){
        if(!PlayerUtils.isPlayerKitsAdmin(sender)){
            msgManager.sendMessage(sender,messagesConfig.getString("noPermissions"),true);
            return;
        }

        if(!plugin.getConfigsManager().reload()){
            sender.sendMessage(PlayerKits2.prefix+MessagesManager.getLegacyColoredMessage(" &cThere was an error reloading the config, check the console."));
            return;
        }
        msgManager.sendMessage(sender,messagesConfig.getString("commandReload"),true);
        if(plugin.getConfigsManager().isStorageBackendChangeRequiresRestart()){
            String warning = messagesConfig.getString("storageBackendChangeRequiresRestart");
            if(warning == null){
                warning = "&eStorage/sync settings changed. &cRestart the server to apply them.";
            }
            msgManager.sendMessage(sender,warning,true);
        }
    }

    public void reset(CommandSender sender,String[] args,FileConfiguration messagesConfig,MessagesManager msgManager) {
        // /kits reset <kit> <target>
        if(!PlayerUtils.isPlayerKitsAdmin(sender)) {
            msgManager.sendMessage(sender, messagesConfig.getString("noPermissions"), true);
            return;
        }

        if(args.length < 3) {
            msgManager.sendMessage(sender, messagesConfig.getString("commandResetError"), true);
            return;
        }

        String kitName = args[1];
        String playerName = args[2];

        PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
        if(playerName.equals("*")){
            playerDataManager.resetKitForAllPlayers(kitName,result -> {
                String msg = messagesConfig.getString("kitResetCorrectAll");
                if (msg != null) {
                    TaskUtils.runCommandSender(plugin, sender, () ->
                            msgManager.sendMessage(sender, msg.replace("%kit%",kitName), true));
                }
            });
        }else{
            playerDataManager.resetKitForTarget(playerName, kitName, result -> {
                TaskUtils.runCommandSender(plugin, sender, () -> {
                    if(result.isError()){
                        msgManager.sendMessage(sender, result.getMessage(), true);
                    }else{
                        String msg = messagesConfig.getString("kitResetCorrect");
                        if (msg != null) {
                            msgManager.sendMessage(sender, msg.replace("%kit%",kitName).replace("%player%",playerName), true);
                        }
                    }
                });
            });
        }
    }

    public void open(CommandSender sender,String[] args,FileConfiguration messagesConfig,MessagesManager msgManager) {
        // /kits open <inventory> <player>
        if(!PlayerUtils.isPlayerKitsAdmin(sender)) {
            msgManager.sendMessage(sender, messagesConfig.getString("noPermissions"), true);
            return;
        }

        if(args.length < 3) {
            msgManager.sendMessage(sender, messagesConfig.getString("commandOpenError"), true);
            return;
        }

        String inventoryName = args[1];
        String playerName = args[2];

        if(plugin.getInventoryManager().getInventory(inventoryName) == null){
            msgManager.sendMessage(sender, messagesConfig.getString("inventoryNotExists"), true);
            return;
        }

        Player player = getOnlinePlayer(playerName);
        if(player == null){
            if(tryForwardRemoteCommand(sender, playerName, RedisSyncManager.COMMAND_TYPE_OPEN, inventoryName, messagesConfig, msgManager)){
                return;
            }
            String msg = messagesConfig.getString("playerNotOnline");
            if (msg != null) msgManager.sendMessage(sender,msg.replace("%player%",playerName),true);
            return;
        }

        InventoryPlayer inventoryPlayer = new InventoryPlayer(player,inventoryName);
        TaskUtils.runEntity(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.getInventoryManager().openInventory(inventoryPlayer);
        });
    }

    public void give(CommandSender sender,String[] args,FileConfiguration messagesConfig,MessagesManager msgManager){
        // /kits give <kit> <player>
        if(!PlayerUtils.isPlayerKitsAdmin(sender)){
            msgManager.sendMessage(sender,messagesConfig.getString("noPermissions"),true);
            return;
        }

        if(args.length < 3){
            msgManager.sendMessage(sender,messagesConfig.getString("commandGiveError"),true);
            return;
        }

        String kitName = args[1];
        Player player = getOnlinePlayer(args[2]);
        if(player == null){
            if(tryForwardRemoteCommand(sender, args[2], RedisSyncManager.COMMAND_TYPE_GIVE, kitName, messagesConfig, msgManager)){
                return;
            }
            msgManager.sendMessage(sender,messagesConfig.getString("playerNotOnline")
                    .replace("%player%",args[2]),true);
            return;
        }

        TaskUtils.runEntity(plugin, player, () -> {
            if (!player.isOnline()) {
                TaskUtils.runCommandSender(plugin, sender, () -> {
                    String msg = messagesConfig.getString("playerNotOnline");
                    if (msg != null) {
                        msgManager.sendMessage(sender, msg.replace("%player%", args[2]), true);
                    }
                });
                return;
            }
            PlayerKitsMessageResult result = plugin.getKitsManager().giveKit(player,kitName,new GiveKitInstructions(true,false,false,false));
            TaskUtils.runCommandSender(plugin, sender, () -> {
                if(result.isError()){
                    String msg = messagesConfig.getString("commandGiveError2");
                    if (msg != null) {
                        msgManager.sendMessage(sender,msg.replace("%error%",result.getMessage()),true);
                    }
                }else{
                    String msg = messagesConfig.getString("commandGiveCorrect");
                    if (msg != null) {
                        msgManager.sendMessage(sender,msg.replace("%kit%",kitName).replace("%player%",args[2]),true);
                    }
                }
            });
        });
    }

    public void claim(Player player,String[] args,FileConfiguration messagesConfig,MessagesManager msgManager){
        // /kit claim <kit>
        if(args.length < 2){
            msgManager.sendMessage(player,messagesConfig.getString("commandClaimError"),true);
            return;
        }

        String kitName = args[1];
        claimKitShortCommand(player,messagesConfig,msgManager,kitName);
    }

    public void preview(CommandSender sender,String[] args,FileConfiguration messagesConfig,MessagesManager msgManager){
        // /kit preview <kit> (optional)<player>
        MainConfigManager mainConfigManager = plugin.getConfigsManager().getMainConfigManager();
        if(!mainConfigManager.isKitPreview()){
            msgManager.sendMessage(sender,messagesConfig.getString("kitPreviewDisabled"),true);
            return;
        }

        if(args.length < 2){
            msgManager.sendMessage(sender,messagesConfig.getString("commandPreviewError"),true);
            return;
        }

        Kit kit = plugin.getKitsManager().getKitByName(args[1]);
        if(kit == null){
            String msg = messagesConfig.getString("kitDoesNotExists");
            if (msg != null) msgManager.sendMessage(sender,msg.replace("%kit%",args[1]),true);
            return;
        }

        Player player;
        if(args.length > 2){
            // Kit preview for someone else
            if(!PlayerUtils.isPlayerKitsAdmin(sender)){
                msgManager.sendMessage(sender,messagesConfig.getString("noPermissions"),true);
                return;
            }

            player = getOnlinePlayer(args[2]);
            if(player == null){
                if(tryForwardRemoteCommand(sender, args[2], RedisSyncManager.COMMAND_TYPE_PREVIEW, args[1], messagesConfig, msgManager)){
                    return;
                }
                String msg = messagesConfig.getString("playerNotOnline");
                if (msg != null) msgManager.sendMessage(sender,msg.replace("%player%",args[2]),true);
                return;
            }

            String msg2 = messagesConfig.getString("commandPreviewOtherCorrect");
            if (msg2 != null) msgManager.sendMessage(sender,msg2.replace("%kit%",args[1]).replace("%player%",args[2]),true);
        }else{
            if(kit.isPermissionRequired()){
                if(mainConfigManager.isKitPreviewRequiresKitPermission() && !kit.playerHasPermission(sender)){
                    msgManager.sendMessage(sender,messagesConfig.getString("cantPreviewError"),true);
                    return;
                }
            }

            if(!(sender instanceof Player)){
                msgManager.sendMessage(sender,messagesConfig.getString("onlyPlayerCommand"),true);
                return;
            }

            player = (Player)sender;
        }

        InventoryPlayer inventoryPlayer = new InventoryPlayer(player,"preview_inventory");
        inventoryPlayer.setKitName(args[1]);
        inventoryPlayer.setPreviousInventoryName("main_inventory");
        TaskUtils.runEntity(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.getInventoryManager().openInventory(inventoryPlayer);
        });
    }

    public void claimKitShortCommand(Player player,FileConfiguration messagesConfig,MessagesManager msgManager,String kitName){
        // /kit <kit>
        PlayerKitsMessageResult result = plugin.getKitsManager().giveKit(player,kitName,new GiveKitInstructions());
        if(result.isError()){
            msgManager.sendMessage(player,result.getMessage(),true);
        }else{
            if(result.isProceedToBuy()){
                //Open requirements inventory
                InventoryPlayer inventoryPlayer = new InventoryPlayer(player,"buy_requirements_inventory");
                inventoryPlayer.setKitName(kitName);
                inventoryPlayer.setPreviousInventoryName("main_inventory");
                plugin.getInventoryManager().openInventory(inventoryPlayer);
                return;
            }
            String msg = messagesConfig.getString("kitReceived");
            if (msg != null) {
                msgManager.sendMessage(player,msg.replace("%kit%",kitName),true);
            }
        }
    }

    public void create(Player player,String[] args,FileConfiguration messagesConfig,MessagesManager msgManager){
        // /kit create <kit> (optional)<original/configurable>
        if(!PlayerUtils.isPlayerKitsAdmin(player)){
            msgManager.sendMessage(player,messagesConfig.getString("noPermissions"),true);
            return;
        }

        if(args.length < 2){
            msgManager.sendMessage(player,messagesConfig.getString("commandCreateError"),true);
            return;
        }

        boolean saveOriginalItems = plugin.getConfigsManager().getMainConfigManager().isNewKitDefaultSaveModeOriginal();
        if(args.length >= 3){
            if(args[2].equalsIgnoreCase("original")){
                saveOriginalItems = true;
            }else if(args[2].equalsIgnoreCase("configurable")){
                saveOriginalItems = false;
            }else{
                msgManager.sendMessage(player,messagesConfig.getString("commandCreateError"),true);
                return;
            }
        }

        plugin.getKitsManager().createKit(args[1],player,saveOriginalItems);
    }

    public void delete(CommandSender sender,String[] args,FileConfiguration messagesConfig,MessagesManager msgManager){
        // /kit delete <kit>
        if(!PlayerUtils.isPlayerKitsAdmin(sender)){
            msgManager.sendMessage(sender,messagesConfig.getString("noPermissions"),true);
            return;
        }

        if(args.length < 2){
            msgManager.sendMessage(sender,messagesConfig.getString("commandDeleteError"),true);
            return;
        }

        plugin.getKitsManager().deleteKit(args[1],sender);
    }

    public void edit(Player player,String[] args,FileConfiguration messagesConfig,MessagesManager msgManager){
        // /kit edit <kit>
        if(!PlayerUtils.isPlayerKitsAdmin(player)){
            msgManager.sendMessage(player,messagesConfig.getString("noPermissions"),true);
            return;
        }

        if(args.length < 2){
            msgManager.sendMessage(player,messagesConfig.getString("commandEditError"),true);
            return;
        }

        if(plugin.getKitsManager().getKitByName(args[1]) == null){
            String msg = messagesConfig.getString("kitDoesNotExists");
            if (msg != null) msgManager.sendMessage(player,msg.replace("%kit%",args[1]),true);
            return;
        }

        InventoryPlayer inventoryPlayer = new InventoryPlayer(player,null);
        inventoryPlayer.setKitName(args[1]);
        plugin.getInventoryEditManager().openInventory(inventoryPlayer);
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        MainConfigManager mainConfigManager = plugin.getConfigsManager().getMainConfigManager();
        boolean claimKitShortCommand = mainConfigManager.isClaimKitShortCommand();
        boolean kitPreviewEnabled = mainConfigManager.isKitPreview();

        List<String> completions = new ArrayList<>();
        List<String> commands = new ArrayList<>();

        if(args.length == 1) {
            if(claimKitShortCommand){
                List<String> kitCompletions = getKitCompletions(sender,args,0);
                if(kitCompletions != null){
                    commands.addAll(kitCompletions);
                }
            }else{
                commands.add("claim");
            }
            if(kitPreviewEnabled){
                commands.add("preview");
            }
            if(PlayerUtils.isPlayerKitsAdmin(sender)){
                commands.add("give");commands.add("delete");commands.add("create");
                commands.add("reload");commands.add("reset");commands.add("edit");
                commands.add("verify");commands.add("migrate");commands.add("open");
            }
            for(String c : commands) {
                if(args[0].isEmpty() || c.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(c);
                }
            }
            return completions;
        }else {
            if(args.length == 2) {
                if(!claimKitShortCommand){
                    commands.add("claim");
                }
                if(kitPreviewEnabled){
                    commands.add("preview");
                }
                if(PlayerUtils.isPlayerKitsAdmin(sender)){
                    commands.add("give");commands.add("delete");
                    commands.add("reset");commands.add("edit");
                    commands.add("open");
                }
                for(String c : commands) {
                    if(args[0].equalsIgnoreCase(c)){
                        if(c.equals("open")){
                            return getInventoryCompletions(args,1);
                        }else{
                            return getKitCompletions(sender,args,1);
                        }

                    }
                }
            }else if(args.length == 3 && PlayerUtils.isPlayerKitsAdmin(sender)){
                if(args[0].equalsIgnoreCase("create")){
                    commands.add("original");commands.add("configurable");
                    for(String c : commands) {
                        if(args[2].isEmpty() || c.startsWith(args[2].toLowerCase())) {
                            completions.add(c);
                        }
                    }
                    return completions;
                }else if(args[0].equalsIgnoreCase("reset")){
                    return getResetTargetCompletions(args,2);
                }else if(args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("open")
                        || args[0].equalsIgnoreCase("preview")){
                    return getOnlinePlayerCompletions(args,2);
                }
            }
        }

        return null;
    }

    public List<String> getKitCompletions(CommandSender sender,String[] args,int argKitPos){
        List<String> completions = new ArrayList<>();
        String argKit = args[argKitPos];

        List<Kit> kits = plugin.getKitsManager().getKits();
        for(Kit kit : kits) {
            if(argKit.isEmpty() || kit.getName().toLowerCase().startsWith(argKit.toLowerCase())) {
                if(kit.playerHasPermission(sender)){
                    completions.add(kit.getName());
                }
            }
        }

        if(completions.isEmpty()){
            return null;
        }
        return completions;
    }

    public List<String> getInventoryCompletions(String[] args,int argInvPos){
        List<String> completions = new ArrayList<>();
        String argInv = args[argInvPos];

        List<KitInventory> inventories = plugin.getInventoryManager().getInventories();
        for(KitInventory inv : inventories) {
            if((argInv.isEmpty() || inv.getName().toLowerCase().startsWith(argInv.toLowerCase()))
                && !inv.getName().equals("preview_inventory") && !inv.getName().equals("buy_requirements_inventory")) {
                completions.add(inv.getName());
            }
        }

        if(completions.isEmpty()){
            return null;
        }
        return completions;
    }

    public List<String> getOnlinePlayerCompletions(String[] args,int argPlayerPos){
        String argPlayer = args[argPlayerPos];

        RedisSyncManager redisSyncManager = plugin.getRedisSyncManager();
        if(redisSyncManager != null && redisSyncManager.isActive()){
            List<String> completions = redisSyncManager.getNetworkOnlinePlayerCompletions(argPlayer);
            if(completions != null){
                return completions;
            }
        }

        List<String> completions = new ArrayList<>();
        for(Player p : Bukkit.getOnlinePlayers()) {
            if(argPlayer.isEmpty() || p.getName().toLowerCase().startsWith(argPlayer.toLowerCase())){
                completions.add(p.getName());
            }
        }

        if(completions.isEmpty()){
            return null;
        }
        return completions;
    }

    public List<String> getResetTargetCompletions(String[] args,int argTargetPos){
        String argTarget = args[argTargetPos];
        List<String> completions = plugin.getPlayerDataManager().getKnownPlayerNameCompletions(argTarget);
        if(completions == null){
            completions = new ArrayList<>();
        }

        if(argTarget.isEmpty() || "*".startsWith(argTarget.toLowerCase())) {
            completions.add("*");
        }

        if(completions.isEmpty()){
            return null;
        }
        return completions;
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

    private boolean tryForwardRemoteCommand(CommandSender sender, String playerName, String commandType, String arg1,
                                            FileConfiguration messagesConfig, MessagesManager msgManager){
        RedisSyncManager redisSyncManager = plugin.getRedisSyncManager();
        if(redisSyncManager == null || !redisSyncManager.isActive()){
            return false;
        }

        String targetInstanceId = redisSyncManager.getOnlinePlayerRemoteInstance(playerName);
        if(targetInstanceId == null){
            return false;
        }

        String forwardingMessage = messagesConfig.getString("commandCrossServerForwarding");
        if(forwardingMessage == null){
            forwardingMessage = "&eProcessing command for &7%player%&e on another server...";
        }
        msgManager.sendMessage(sender, forwardingMessage.replace("%player%",playerName), true);

        redisSyncManager.sendRemoteCommandRequest(targetInstanceId, commandType, arg1, playerName, result -> {
            TaskUtils.runCommandSender(plugin, sender, () -> {
                String resultMessage = result.getMessage();
                if(resultMessage != null && !resultMessage.isEmpty()){
                    msgManager.sendMessage(sender, resultMessage, true);
                    return;
                }

                if(!result.isSuccess()){
                    String fallbackError = messagesConfig.getString("commandCrossServerFailed");
                    if(fallbackError == null){
                        fallbackError = "&cCould not execute cross-server command.";
                    }
                    msgManager.sendMessage(sender, fallbackError, true);
                }
            });
        });
        return true;
    }
}
