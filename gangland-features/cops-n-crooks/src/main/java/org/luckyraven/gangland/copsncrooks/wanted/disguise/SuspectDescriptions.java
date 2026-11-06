package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** The latest suspect description per player, and how to build, compare and speak one (0.20.0 lane disguise). */
public class SuspectDescriptions {

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

	private static final Set<Material> LEATHER_PIECES = Set.of(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
	                                                           Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);

	private final Supplier<DisguiseSettings>        settings;
	private final WearableKeys                      keys;
	private final WantedMessages                    messages;
	private final Map<UUID, SuspectDescription> byPlayer = new ConcurrentHashMap<>();
	private final Set<UUID>                     recognised = ConcurrentHashMap.newKeySet();

	public SuspectDescriptions(Supplier<DisguiseSettings> settings, WearableKeys keys, WantedMessages messages) {
		this.settings = settings;
		this.keys     = keys;
		this.messages = messages;
	}

	/** What {@code player} looks like right now. */
	public SuspectDescription describe(Player player) {
		DisguiseSettings settings  = this.settings.get();
		PlayerInventory  inventory = player.getInventory();
		ItemStack        helmet    = inventory.getHelmet();
		if (helmet != null && helmet.getType() == Material.AIR) {
			helmet = null;
		}
		String           wearable = keys.keyOf(helmet);

		boolean maskWearable = wearable != null && containsIgnoreCase(settings.maskWearables(), wearable);
		boolean maskItem     = helmet != null && containsIgnoreCase(settings.maskItems(), helmet.getType().name());
		boolean masked       = maskWearable || maskItem;

		return new SuspectDescription(pieceText(helmet), pieceText(inventory.getChestplate()),
		                              pieceText(inventory.getLeggings()), pieceText(inventory.getBoots()),
		                              weaponText(inventory.getItemInMainHand()), player.getVehicle() != null, masked);
	}

	/** Head, chest, legs, feet and vehicle: the most pieces that can differ. */
	public static final int COMPARED_PIECES = 5;

	/**
	 * Fewer than {@code changesNeeded} of head, chest, legs, feet and vehicle of {@code player} right now differ from
	 * those of {@code description}; the weapon is described but never compared.
	 */
	public boolean matches(Player player, SuspectDescription description, int changesNeeded) {
		SuspectDescription now     = describe(player);
		int                changes = 0;

		if (!now.head().equals(description.head())) changes++;
		if (!now.chest().equals(description.chest())) changes++;
		if (!now.legs().equals(description.legs())) changes++;
		if (!now.feet().equals(description.feet())) changes++;
		if (now.inCar() != description.inCar()) changes++;

		return changes < changesNeeded;
	}

	/** The radio text, e.g. {@code masked in a carved pumpkin, wearing red jacket, on foot}. */
	public String format(SuspectDescription description) {
		List<String> parts = new ArrayList<>();

		if (description.masked()) {
			parts.add(messages.format(WantedMessages.Key.SUSPECT_MASKED, Map.of("head", description.head())));
		}

		List<String> worn = new ArrayList<>();
		if (!description.masked() && !description.head().isEmpty()) {
			worn.add(description.head());
		}
		for (String piece : List.of(description.chest(), description.legs(), description.feet())) {
			if (!piece.isEmpty()) {
				worn.add(piece);
			}
		}
		if (!worn.isEmpty()) {
			parts.add(messages.format(WantedMessages.Key.SUSPECT_WEARING, Map.of("items", String.join(", ", worn))));
		} else if (!description.masked()) {
			parts.add(text(WantedMessages.Key.SUSPECT_PLAIN));
		}

		if (!description.weapon().isEmpty()) {
			parts.add(messages.format(WantedMessages.Key.SUSPECT_ARMED, Map.of("weapon", description.weapon())));
		}
		parts.add(text(description.inCar() ? WantedMessages.Key.SUSPECT_IN_CAR : WantedMessages.Key.SUSPECT_ON_FOOT));

		// '' in a template drops that part
		return parts.stream().filter(part -> !part.isBlank()).collect(Collectors.joining(", "));
	}

	public void store(UUID playerId, SuspectDescription description) {
		byPlayer.put(playerId, description);
	}

	public @Nullable SuspectDescription latest(UUID playerId) {
		return byPlayer.get(playerId);
	}

	public void clear(UUID playerId) {
		byPlayer.remove(playerId);
		recognised.remove(playerId);
	}

	/** A cop saw {@code playerId} and knew them within the memory for their stars (0.21.6). */
	public boolean isRecognised(UUID playerId) {
		return recognised.contains(playerId);
	}

	public void setRecognised(UUID playerId, boolean known) {
		if (known) {
			recognised.add(playerId);
		} else {
			recognised.remove(playerId);
		}
	}

	/** The Bartizan wearable key, else a dyed leather piece by colour, else the material; empty for no piece. */
	private String pieceText(@Nullable ItemStack piece) {
		if (piece == null || piece.getType() == Material.AIR) {
			return "";
		}
		String key = keys.keyOf(piece);
		if (key != null) {
			return words(key);
		}

		Material type = piece.getType();
		if (LEATHER_PIECES.contains(type) && piece.getItemMeta() instanceof LeatherArmorMeta meta
		    && meta.getColor() != null) {
			String dye = nearestDye(meta.getColor());
			return type == Material.LEATHER_CHESTPLATE ? dye + " jacket" : dye + " " + words(type.name());
		}
		return words(type.name());
	}

	/** A Bartizan weapon by its clean name, else a held sword, axe or bow; anything else is not described. */
	private String weaponText(@Nullable ItemStack hand) {
		if (hand == null || hand.getType() == Material.AIR) {
			return "";
		}
		String name = keys.weaponNameOf(hand);
		if (name != null && !name.isBlank()) {
			return name.strip();
		}

		String type = hand.getType().name();
		boolean weapon = type.endsWith("_SWORD") || type.endsWith("_AXE") || type.equals("BOW")
		                 || type.equals("CROSSBOW") || type.equals("TRIDENT");
		return weapon ? words(type) : "";
	}

	/** The Unknown word alone, for when no description is known. */
	public String unknownText() {
		return text(WantedMessages.Key.SUSPECT_UNKNOWN);
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
