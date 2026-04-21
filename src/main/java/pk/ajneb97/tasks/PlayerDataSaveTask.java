package pk.ajneb97.tasks;

import pk.ajneb97.PlayerKits2;
import pk.ajneb97.utils.TaskUtils;

public class PlayerDataSaveTask {

	private PlayerKits2 plugin;
	private volatile boolean end;
	public PlayerDataSaveTask(PlayerKits2 plugin) {
		this.plugin = plugin;
		this.end = false;
	}
	
	public void end() {
		end = true;
	}
	
	public void start(int seconds) {
		long ticks = seconds* 20L;
		
		TaskUtils.runAsyncTimer(plugin, () -> {
			if(end) {
				// Can't cancel a lambda this easily, but `end` check stops execution
				// Although it keeps running empty, so we should just return
				return;
			}else {
				execute();
			}
		}, 0L, ticks);
	}
	
	public void execute() {
		plugin.getConfigsManager().getPlayersConfigManager().saveConfigs();
	}
}
