package me.luckyraven.inventory.villager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player tracker of open {@link VillagerInventory} wrappers. Created once as a bean by {@code KernelConfig};
 * threaded into the {@link VillagerInventoryListener} and into every {@link VillagerInventory} that needs to register
 * itself on open and unregister on close.
 */
public final class VillagerInventoryRegistry {

	private final Map<UUID, VillagerInventory> openByPlayer = new ConcurrentHashMap<>();

	public void register(@NotNull UUID playerId, @NotNull VillagerInventory inventory) {
		openByPlayer.put(playerId, inventory);
	}

	public void unregister(@NotNull UUID playerId) {
		openByPlayer.remove(playerId);
	}

	@Nullable
	public VillagerInventory find(@NotNull UUID playerId) {
		return openByPlayer.get(playerId);
	}
}
