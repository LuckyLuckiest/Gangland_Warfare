package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** The latest suspect description per player, and how to build, compare and speak one (0.20.0 lane disguise). */
public class SuspectDescriptions {

	/** The weapon text of a suspect holding nothing. */
	public static final String EMPTY_HANDS = "empty hands";

	/** The vanilla dye colours and their approximate RGB; a dyed leather jacket is named after the nearest one. */
	private static final String[] DYE_NAMES = {
			"white", "orange", "magenta", "light blue", "yellow", "lime", "pink", "gray",
			"light gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
	};
	private static final int[][]  DYE_RGB  = {
			{ 249, 255, 254 }, { 249, 128, 29 }, { 199, 78, 189 }, { 58, 179, 218 },
			{ 254, 216, 61 }, { 128, 199, 31 }, { 243, 139, 170 }, { 71, 79, 82 },
			{ 157, 157, 151 }, { 22, 156, 156 }, { 137, 50, 184 }, { 60, 68, 170 },
			{ 131, 84, 50 }, { 94, 124, 22 }, { 176, 46, 38 }, { 29, 29, 33 }
	};

	private final Supplier<DisguiseSettings>        settings;
	private final WearableKeys                      keys;
	private final WantedMessages                    messages;
	private final Map<UUID, SuspectDescription> byPlayer = new ConcurrentHashMap<>();

	public SuspectDescriptions(Supplier<DisguiseSettings> settings, WearableKeys keys, WantedMessages messages) {
		this.settings = settings;
		this.keys     = keys;
		this.messages = messages;
	}

	/** What {@code player} looks like right now. */
	public SuspectDescription describe(Player player) {
		DisguiseSettings settings = this.settings.get();
		ItemStack        helmet   = player.getInventory().getHelmet();
		String           wearable = keys.keyOf(helmet);

		boolean maskWearable = wearable != null && containsIgnoreCase(settings.maskWearables(), wearable);
		boolean maskItem     = helmet != null && containsIgnoreCase(settings.maskItems(), helmet.getType().name());
		boolean masked       = maskWearable || maskItem;

		String head = helmet == null ? "" : words(maskWearable ? wearable : helmet.getType().name());
		String hand = weaponText(player.getInventory().getItemInMainHand());

		return new SuspectDescription(head, chestText(player.getInventory().getChestplate()), hand,
		                              player.getVehicle() != null, masked);
	}

	/** At least {@code needed} of head, chest and vehicle of {@code player} right now equal those of {@code description}. */
	public boolean matches(Player player, SuspectDescription description, int needed) {
		SuspectDescription now   = describe(player);
		int                equal = 0;

		if (now.head().equals(description.head())) equal++;
		if (now.chest().equals(description.chest())) equal++;
		if (now.inCar() == description.inCar()) equal++;

		return equal >= needed;
	}

	/** The radio text, e.g. {@code unknown suspect in a carved pumpkin, red jacket, on foot}. */
	public String format(SuspectDescription description) {
		StringBuilder line = new StringBuilder(text(description.masked() ? WantedMessages.Key.SUSPECT_UNKNOWN
		                                                                  : WantedMessages.Key.SUSPECT_BARE));

		if (description.masked()) line.append(" in a ").append(description.head());
		if (!description.chest().isEmpty()) line.append(", ").append(description.chest());
		line.append(", ").append(text(description.inCar() ? WantedMessages.Key.SUSPECT_IN_CAR
		                                                  : WantedMessages.Key.SUSPECT_ON_FOOT));

		return line.toString();
	}

	public void store(UUID playerId, SuspectDescription description) {
		byPlayer.put(playerId, description);
	}

	public @Nullable SuspectDescription latest(UUID playerId) {
		return byPlayer.get(playerId);
	}

	public void clear(UUID playerId) {
		byPlayer.remove(playerId);
	}

	private String weaponText(@Nullable ItemStack hand) {
		if (hand == null || hand.getType() == Material.AIR) {
			return EMPTY_HANDS;
		}
		return words(hand.getType().name());
	}

	private String chestText(@Nullable ItemStack chest) {
		if (chest == null) {
			return "";
		}
		if (chest.getType() == Material.LEATHER_CHESTPLATE && chest.getItemMeta() instanceof LeatherArmorMeta meta
		    && meta.getColor() != null) {
			return nearestDye(meta.getColor()) + " jacket";
		}
		return words(chest.getType().name());
	}

	private String text(WantedMessages.Key key) {
		return messages.format(key, Map.of());
	}

	private static String nearestDye(Color colour) {
		int  best         = 0;
		long bestDistance = Long.MAX_VALUE;

		for (int i = 0; i < DYE_RGB.length; i++) {
			long dr       = colour.getRed() - DYE_RGB[i][0];
			long dg       = colour.getGreen() - DYE_RGB[i][1];
			long db       = colour.getBlue() - DYE_RGB[i][2];
			long distance = dr * dr + dg * dg + db * db;

			if (distance < bestDistance) {
				bestDistance = distance;
				best         = i;
			}
		}
		return DYE_NAMES[best];
	}

	/** An enum or key name as lowercase words: {@code CARVED_PUMPKIN} reads {@code carved pumpkin}. */
	private static String words(String name) {
		return name.replace('_', ' ').toLowerCase(Locale.ROOT);
	}

	private static boolean containsIgnoreCase(List<String> names, String name) {
		for (String candidate : names) {
			if (candidate.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}
}
