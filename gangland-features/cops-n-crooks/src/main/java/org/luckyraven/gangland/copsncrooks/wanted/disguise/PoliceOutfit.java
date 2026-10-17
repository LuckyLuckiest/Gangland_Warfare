package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.bartizan.api.BartizanApi;
import org.luckyraven.bartizan.api.wearable.Wearable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** The police outfit: worn, and "blown" by a crime committed in it until it comes off (0.20.0 lane disguise). */
public class PoliceOutfit {

	private final Supplier<DisguiseSettings> settings;
	private final WearableKeys               keys;
	private final Set<UUID>                  blown = ConcurrentHashMap.newKeySet();

	public PoliceOutfit(Supplier<DisguiseSettings> settings, WearableKeys keys) {
		this.settings = settings;
		this.keys = keys;
	}

	/** Every configured outfit key is on the helmet or chest slot of {@code player}. */
	public boolean worn(Player player) {
		Set<String> required = settings.get().policeOutfit().stream()
		                               .map(key -> key.toLowerCase(Locale.ROOT))
		                               .collect(Collectors.toSet());
		if (required.isEmpty()) {
			return false;
		}

		PlayerInventory inventory = player.getInventory();
		Set<String> present = new HashSet<>();
		addKey(present, inventory.getHelmet());
		addKey(present, inventory.getChestplate());
		return present.containsAll(required);
	}

	/** A crime was committed: blown when the outfit is worn. */
	public void onCrime(Player player) {
		if (worn(player)) {
			blown.add(player.getUniqueId());
		}
	}

	/** Worn, not blown, and {@code level} within {@code Outfit_Max_Level}; a removed outfit forgets its blown flag. */
	public boolean hides(Player player, int level) {
		if (!worn(player)) {
			blown.remove(player.getUniqueId());
			return false;
		}
		return !blown.contains(player.getUniqueId()) && level <= settings.get().outfitMaxLevel();
	}

	public void clear(UUID playerId) {
		blown.remove(playerId);
	}

	/** The Bartizan-backed keys; the only place a Bartizan type is named. */
	public static WearableKeys bartizanKeys() {
		return PoliceOutfit::bartizanKeyOf;
	}

	private void addKey(Set<String> present, @Nullable ItemStack item) {
		String key = keys.keyOf(item);
		if (key != null) {
			present.add(key.toLowerCase(Locale.ROOT));
		}
	}

	/** Resolved fresh on every call: Bartizan may enable after this module, or not be installed at all. */
	private static @Nullable String bartizanKeyOf(@Nullable ItemStack item) {
		if (item == null || Bukkit.getServer() == null) {
			return null;
		}
		try {
			ServicesManager services = Bukkit.getServicesManager();
			if (services == null) {
				return null;
			}
			RegisteredServiceProvider<BartizanApi> registration = services.getRegistration(BartizanApi.class);
			if (registration == null) {
				return null;
			}
			return Wearable.getWearableKey(item);
		} catch (NoClassDefFoundError e) {
			return null;
		}
	}
}
