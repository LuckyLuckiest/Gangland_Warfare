package org.luckyraven.gangland.lootchest;

import lombok.CustomLog;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.core.bean.BeanLifecycle;
import org.luckyraven.gangland.core.timer.CountdownTimer;
import org.luckyraven.gangland.database.repositories.lootchest.LootChestRepository;
import org.luckyraven.gangland.exception.PluginException;
import org.luckyraven.gangland.hologram.HologramService;
import org.luckyraven.gangland.item.ItemParser;
import org.luckyraven.gangland.lootchest.config.LootChestMessagesProvider;
import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.gangland.persistence.repository.IRepository;
import org.luckyraven.gangland.persistence.repository.RepositoryRegistry;

import java.util.Collection;

@CustomLog
public class LootChestManager extends LootChestService implements BeanLifecycle {

	private final Gangland           gangland;
	private final RepositoryRegistry repositoryRegistry;

	private CountdownTimer pendingStartupTimer;

	public LootChestManager(Gangland gangland, String prefix, HologramService hologramService,
	                        RepositoryRegistry repositoryRegistry,
	                        ItemParser itemParser,
	                        LootChestMessagesProvider messagesProvider) {
		super(gangland, hologramService, prefix, itemParser, messagesProvider);

		this.gangland           = gangland;
		this.repositoryRegistry = repositoryRegistry;
	}

	public void initialize(LootChestRepository repository, boolean reload) {
		repository.setLootChestService(this);

		if (reload) {
			registerLootChests(repository);
		} else {
			// in seconds
			int timeToWait = 5;
			// Held on the instance so onClear() can cancel it if a reload lands
			// before the world-load grace period expires, otherwise the timer would
			// re-register every chest on top of the reload's fresh state.
			pendingStartupTimer = new CountdownTimer(gangland, timeToWait, null, null, timer -> {
				pendingStartupTimer = null;
				registerLootChests(repository);
			});

			// waits for the world to load to spawn the holograms
			pendingStartupTimer.start(false);
		}

		repository.setDataSupplier(this::getAllChests);
	}

	/**
	 * Clears chest instance data and active sessions, but preserves config data (tiers, loot tables) which are reloaded
	 * from YAML files by {@code filesReload()} before this method runs. The parent {@code clear()} wipes everything
	 * including tiers and loot tables — calling it here would destroy the config that was just reloaded.
	 */
	@Override
	public void onClear() {
		if (pendingStartupTimer != null) {
			pendingStartupTimer.stop();
			pendingStartupTimer = null;
		}

		cancelSessions();
		clearChests();
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		IRepository<LootChestData> repo = repositoryRegistry.getRepository(LootChestData.class);

		if (!(repo instanceof LootChestRepository castedRepo)) {
			String message = "LootChestData repository is not initialized!";
			log.error(message);
			throw new PluginException(message);
		}

		initialize(castedRepo, !firstLoad);
	}

	private void registerLootChests(IRepository<LootChestData> repository) {
		Collection<LootChestData> chestDataList = repository.loadAll();

		for (LootChestData chestData : chestDataList) {
			registerChest(chestData);
		}
	}
}
