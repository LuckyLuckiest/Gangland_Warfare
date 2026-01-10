package me.luckyraven.copsncrooks.police;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.compatibility.pathfinding.PathfindingHandler;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import org.bukkit.plugin.java.JavaPlugin;

@RequiredArgsConstructor
public class PoliceService {

	private final JavaPlugin plugin;

	@Getter
	private PoliceManager policeManager;

	public PoliceManager initialize(EntityMarkManager entityMarkManager, PathfindingHandler pathfindingHandler) {
		this.policeManager = new PoliceManager(plugin, entityMarkManager, pathfindingHandler);

		return policeManager;
	}

	public void shutdown() {
		if (policeManager == null) return;

		policeManager.shutdown();
	}

}
