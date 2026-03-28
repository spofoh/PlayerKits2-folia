package pk.ajneb97.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.managers.InventoryRequirementsManager;
import pk.ajneb97.model.item.KitItem;

import java.util.ArrayList;
import java.util.List;

public class MiniMessageUtils {

    public static void messagePrefix(CommandSender sender, String message, boolean isPrefix, String prefix){
        if(isPrefix){
            sender.sendMessage(MiniMessage.miniMessage().deserialize(prefix+message));
        }else{
            sender.sendMessage(MiniMessage.miniMessage().deserialize(message));
        }
    }

    public static void title(Player player, String title, String subtitle, Integer fadeIn, Integer stay, Integer fadeOut){
        player.showTitle(Title.title(
                MiniMessage.miniMessage().deserialize(title),MiniMessage.miniMessage().deserialize(subtitle),
                fadeIn,stay,fadeOut
        ));
    }

    public static void actionbar(Player player, String message){
        player.sendActionBar(MiniMessage.miniMessage().deserialize(message));
    }

    public static void message(Player player,String message){
        player.sendMessage(MiniMessage.miniMessage().deserialize(message));
    }

    public static Inventory createInventory(int slots, String title){
        return Bukkit.createInventory(null,slots, MiniMessage.miniMessage().deserialize(title));
    }

    public static void setCommonItemName(KitItem commonItem, ItemMeta meta){
        if(meta.hasDisplayName() && meta.displayName() != null){
            commonItem.setName(MiniMessage.miniMessage().serialize(meta.displayName()));
        }
    }

    public static void setCommonItemLore(List<String> lore, ItemMeta meta){
        if(meta.hasLore() && meta.lore() != null){
            for (Component line : meta.lore()) {
                lore.add(MiniMessage.miniMessage().serialize(line));
            }
        }
    }

    public static void setCommonItemNameLegacy(KitItem commonItem, ItemMeta meta){
        if(meta.hasDisplayName() && meta.displayName() != null){
            commonItem.setName(LegacyComponentSerializer.legacyAmpersand().serialize(meta.displayName()));
        }
    }

    public static void setCommonItemLoreLegacy(List<String> lore, ItemMeta meta){
        if(meta.hasLore() && meta.lore() != null){
            for (Component line : meta.lore()) {
                lore.add(LegacyComponentSerializer.legacyAmpersand().serialize(line));
            }
        }
    }

    public static void setItemName(ItemMeta meta,String name){
        meta.displayName(MiniMessage.miniMessage().deserialize(name).decoration(TextDecoration.ITALIC, false));
    }

    public static void setItemLore(ItemMeta meta, List<String> lore, Player player, PlayerKits2 plugin){
        List<Component> loreComponent = new ArrayList<>();
        for(int i=0;i<lore.size();i++) {
            String line = OtherUtils.replaceGlobalVariables(lore.get(i),player,plugin);
            loreComponent.add(MiniMessage.miniMessage().deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreComponent);
    }

    public static void setRequirementsMessage(ItemMeta meta, String kitName, Player player, InventoryRequirementsManager inventoryRequirementsManager){
        if(!meta.hasLore() || meta.lore() == null) return;
        List<Component> newLore = new ArrayList<>();
        PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();
        for(Component line : meta.lore()){
            String plainText = plainSerializer.serialize(line);
            if(plainText.contains("%kit_requirements_message%")){
                List<String> message = inventoryRequirementsManager.replaceRequirementsMessageVariable(kitName,player);
                for(String m : message){
                    newLore.add(MiniMessage.miniMessage().deserialize(m).decoration(TextDecoration.ITALIC, false));
                }
            }else{
                newLore.add(line);
            }
        }
        meta.lore(newLore);
    }
}
