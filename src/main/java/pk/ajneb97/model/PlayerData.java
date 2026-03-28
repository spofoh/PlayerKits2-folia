package pk.ajneb97.model;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlayerData {

    private String name;
    private UUID uuid;

    private List<PlayerDataKit> kits;
    private boolean modified;

    public PlayerData(UUID uuid, String name){
        this.name = name;
        this.uuid = uuid;
        this.kits = new CopyOnWriteArrayList<>();
        this.modified = false;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<PlayerDataKit> getKits() {
        return kits;
    }

    public void setKits(List<PlayerDataKit> kits) {
        this.kits = new CopyOnWriteArrayList<>(kits);
    }

    public void addKit(PlayerDataKit kit){
        this.kits.add(kit);
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public PlayerDataKit getKit(String kitName){
        for(PlayerDataKit kit : kits){
            if(kit.getName().equals(kitName)){
                return kit;
            }
        }
        return null;
    }

    public boolean setKitCooldown(String kitName, long cooldown){
        PlayerDataKit playerDataKit = getKit(kitName);
        boolean creating = false;
        if(playerDataKit == null){
            playerDataKit = new PlayerDataKit(kitName);
            kits.add(playerDataKit);
            creating = true;
        }

        playerDataKit.setCooldown(cooldown);
        return creating;
    }

    public long getKitCooldown(String kitName){
        PlayerDataKit playerDataKit = getKit(kitName);
        if(playerDataKit == null){
            return 0;
        }else{
            return playerDataKit.getCooldown();
        }
    }

    public boolean setKitOneTime(String kitName){
        PlayerDataKit playerDataKit = getKit(kitName);
        boolean creating = false;
        if(playerDataKit == null){
            playerDataKit = new PlayerDataKit(kitName);
            kits.add(playerDataKit);
            creating = true;
        }

        playerDataKit.setOneTime(true);
        return creating;
    }

    public boolean getKitOneTime(String kitName){
        PlayerDataKit playerDataKit = getKit(kitName);
        if(playerDataKit == null){
            return false;
        }else{
            return playerDataKit.isOneTime();
        }
    }

    public boolean setKitBought(String kitName){
        PlayerDataKit playerDataKit = getKit(kitName);
        boolean creating = false;
        if(playerDataKit == null){
            playerDataKit = new PlayerDataKit(kitName);
            kits.add(playerDataKit);
            creating = true;
        }

        playerDataKit.setBought(true);
        return creating;
    }

    public boolean getKitHasBought(String kitName){
        PlayerDataKit playerDataKit = getKit(kitName);
        if(playerDataKit == null){
            return false;
        }else{
            return playerDataKit.isBought();
        }
    }

    public void resetKit(String kitName){
        kits.removeIf(kit -> kit.getName().equals(kitName));
        modified = true;
    }
}
