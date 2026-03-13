package me.luckyraven.lootchest;

import me.luckyraven.Gangland;
import me.luckyraven.database.repositories.lootchest.LootChestRepository;
import me.luckyraven.lootchest.data.LootChestData;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.util.hologram.HologramService;
import me.luckyraven.util.timer.CountdownTimer;

import java.util.Collection;

public class LootChestManager extends LootChestService {

	private final Gangland gangland;

	public LootChestManager(Gangland gangland, String prefix, HologramService hologramService) {
		super(gangland, hologramService, prefix);

		this.gangland = gangland;
	}

	public void initialize(LootChestRepository repository, boolean reload) {
		repository.setLootChestService(this);

		if (reload) {
			registerLootChests(repository);
		} else {
			// in seconds
			int timeToWait = 5;
			CountdownTimer waitForWorld = new CountdownTimer(gangland, timeToWait, null, null, timer -> {
				registerLootChests(repository);
			});

			// waits for the world to load to spawn the holograms
			waitForWorld.start(false);
		}

		repository.setDataSupplier(this::getAllChests);
	}

	private void registerLootChests(IRepository<LootChestData> repository) {
		Collection<LootChestData> chestDataList = repository.loadAll();

		for (LootChestData chestData : chestDataList) {
			registerChest(chestData);
		}
	}
}
