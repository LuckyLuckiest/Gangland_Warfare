package org.luckyraven.gangland.copsncrooks.setup;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.jail.Jail;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DispatchSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.place.AdminRegion;
import org.luckyraven.gangland.copsncrooks.place.AdminRegionRegistry;
import org.luckyraven.gangland.copsncrooks.place.SetupPoint;
import org.luckyraven.gangland.copsncrooks.place.SetupPointRegistry;
import org.luckyraven.gangland.copsncrooks.setup.SetupMessages.Key;
import org.luckyraven.gangland.copsncrooks.station.Station;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;
import org.luckyraven.keystone.item.ItemBuilder;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * What {@code /glw cop setup} does, behind the argument tree: wand, mode, save, list, remove, tp, link. Saves write
 * ONLY through the C5 registries ({@link StationRegistry}, {@link AdminRegionRegistry}, {@link SetupPointRegistry});
 * the spawner grouping goes through {@link CopSpawnManager}.
 *
 * @since 0.16.0
 */
public final class SetupCommands {

	public static final String STATION = "station";
	public static final String REGION  = "region";
	public static final String POINT   = "point";

	private final StationRegistry             stations;
	private final AdminRegionRegistry         regions;
	private final SetupPointRegistry          points;
	private final CopSpawnManager             spawns;
	private final JailRegistry                jails;
	private final SetupSelections             selections;
	private final SetupMessages               messages;
	private final SetupOutline                outline;
	/** {@code copLoader::getLoadedProvider}: read per call, never held (CopLoader replaces it on every load). */
	private final Supplier<CopConfigProvider> provider;

	public SetupCommands(StationRegistry stations, AdminRegionRegistry regions, SetupPointRegistry points,
	                     CopSpawnManager spawns, JailRegistry jails, SetupSelections selections,
	                     SetupMessages messages, SetupOutline outline, Supplier<CopConfigProvider> provider) {
		this.stations   = stations;
		this.regions    = regions;
		this.points     = points;
		this.spawns     = spawns;
		this.jails      = jails;
		this.selections = selections;
		this.messages   = messages;
		this.outline    = outline;
		this.provider   = provider;
	}

	// ---- gates and suggestions (used by the argument tree) ----

	/** True when {@code sender} may use the tools; otherwise tells them. */
	public boolean permitted(CommandSender sender) {
		if (sender.hasPermission(SetupSelections.PERMISSION)) return true;
		sender.sendMessage(messages.format(Key.NO_PERMISSION, Map.of()));
		return false;
	}

	/** The permitted player behind {@code sender}, or {@code null} after telling them why not. */
	public @Nullable Player admin(CommandSender sender) {
		if (!permitted(sender)) return null;
		if (sender instanceof Player player) return player;
		sender.sendMessage(messages.format(Key.NOT_PLAYER, Map.of()));
		return null;
	}

	/** The usage line for a missing or bad operand; {@code fullUsage} starts with {@code /glw} so its command is coloured. */
	public void usage(CommandSender sender, String fullUsage) {
		sender.sendMessage(messages.usage(fullUsage));
	}

	public List<String> modes() {
		return Arrays.stream(SetupMode.values()).map(SetupMode::id).toList();
	}

	public List<String> kinds() {
		return List.of(STATION, REGION, POINT);
	}

	/** Every id of every kind: the typed kind is not visible to a tab-completion source. */
	public List<String> ids() {
		TreeSet<Integer> ids = new TreeSet<>();
		stations.all().forEach(station -> ids.add(station.getId()));
		regions.all().forEach(region -> ids.add(region.getId()));
		points.all().forEach(point -> ids.add(point.getId()));
		return ids.stream().map(String::valueOf).toList();
	}

	public List<String> stationIds() {
		return stations.all().stream().map(station -> String.valueOf(station.getId())).toList();
	}

	/** Jail ids from the jail registry, then {@code none}. */
	public List<String> jailChoices() {
		List<String> choices = new ArrayList<>();
		for (Jail jail : jails.getCells()) choices.add(String.valueOf(jail.getId()));
		choices.add("none");
		return choices;
	}

	// ---- actions ----

	public void give(Player admin) {
		Material material = Objects.requireNonNullElse(
				XMaterial.matchXMaterial(messages.wandItem()).orElse(XMaterial.BLAZE_ROD).get(), Material.BLAZE_ROD);
		ItemStack wand = new ItemBuilder(material)
				.setDisplayName(messages.format(Key.WAND_NAME, Map.of()))
				.setLore(messages.format(Key.WAND_LORE, Map.of()))
				.addTag(SetupSelections.WAND_NBT_KEY, true)
				.build();

		admin.getInventory().addItem(wand);
		admin.sendMessage(messages.format(Key.WAND_GIVEN,
		                                  Map.of("mode", selections.get(admin.getUniqueId()).getMode().id())));
		outline.ensureRunning();
	}

	public void mode(Player admin, String name) {
		SetupMode mode = SetupMode.byName(name);
		if (mode == null) {
			admin.sendMessage(messages.format(Key.MODE_UNKNOWN, Map.of("modes", String.join(", ", modes()))));
			return;
		}

		selections.get(admin.getUniqueId()).setMode(mode);
		admin.sendMessage(messages.format(Key.MODE_SET, Map.of("mode", mode.id())));
		outline.ensureRunning();
	}

	/** Stores the admin's selection under {@code name} according to its mode. */
	public void save(Player admin, String name) {
		String         label     = name.trim();
		SetupSelection selection = selections.get(admin.getUniqueId());
		SetupMode      mode      = selection.getMode();
		Location       pos1      = selection.getPos1();

		if (label.isEmpty()) {
			admin.sendMessage(messages.format(Key.NAME_MISSING, Map.of()));
			return;
		}
		if (!selection.isComplete() || pos1 == null) {
			admin.sendMessage(messages.format(Key.INCOMPLETE,
			                                  Map.of("needed", mode.isPoint() ? "pos1" : "pos1 and pos2",
			                                         "mode", mode.id())));
			return;
		}

		switch (mode) {
			case STATION -> saveStation(admin, label, pos1);
			case PICKUP -> savePoint(admin, SetupPoint.PICKUP, label, pos1);
			default -> saveRegion(admin, label, selection, mode);
		}
	}

	private void saveStation(Player admin, String label, Location anchor) {
		Station station = stations.create(label, anchor);
		if (station == null) {
			admin.sendMessage(messages.format(Key.STATION_DUPLICATE, Map.of("name", label)));
			return;
		}

		CopConfigProvider loaded   = provider.get();
		DispatchSettings  dispatch = Objects.requireNonNullElse(loaded == null ? null : loaded.getDispatchSettings(),
		                                                        DispatchSettings.DEFAULT);
		int joined = spawns.assignNearby(station, dispatch.stationRadius());
		admin.sendMessage(messages.format(Key.STATION_SAVED, Map.of("name", label,
		                                                            "id", String.valueOf(station.getId()),
		                                                            "spawners", String.valueOf(joined))));
	}

	private void saveRegion(Player admin, String label, SetupSelection selection, SetupMode mode) {
		String tag = Objects.requireNonNull(mode.getTag());
		AdminRegion region;
		try {
			region = regions.create(label, Objects.requireNonNull(selection.getPos1()),
			                        Objects.requireNonNull(selection.getPos2()), tag);
		} catch (IllegalArgumentException crossWorld) {
			admin.sendMessage(messages.format(Key.INCOMPLETE, Map.of("needed", "pos1 and pos2 in one world",
			                                                         "mode", mode.id())));
			return;
		}
		admin.sendMessage(messages.format(Key.REGION_SAVED, Map.of("name", label,
		                                                           "id", String.valueOf(region.getId()),
		                                                           "tag", tag)));

		if (mode == SetupMode.BREAKER) savePoint(admin, SetupPoint.BREAKER_TRIGGER, label, admin.getLocation());
	}

	private void savePoint(Player admin, String kind, String label, Location at) {
		SetupPoint point = points.create(kind, label, at);
		admin.sendMessage(messages.format(Key.POINT_SAVED, Map.of("name", label,
		                                                          "id", String.valueOf(point.getId()),
		                                                          "kind", point.getKind())));
	}

	/** One row per placed item under a header, {@code kind} filters, null = all. */
	public void list(CommandSender sender, @Nullable String kind) {
		String wanted = kind == null ? null : kind.toLowerCase(Locale.ROOT);
		if (wanted != null && !kinds().contains(wanted)) {
			sender.sendMessage(messages.format(Key.UNKNOWN_KIND, Map.of()));
			return;
		}

		List<ListRow> rows = new ArrayList<>();
		if (wanted == null || wanted.equals(STATION)) {
			for (Station s : stations.all())
				rows.add(new ListRow(STATION, s.getId(), s.getName(), s.getWorld(), s.getX(), s.getY(), s.getZ(), ""));
		}
		if (wanted == null || wanted.equals(REGION)) {
			for (AdminRegion r : regions.all())
				rows.add(new ListRow(REGION, r.getId(), r.getName(), r.getWorld(), r.getMinX(), r.getMinY(), r.getMinZ(),
				                     " [" + String.join(",", r.getTags()) + "]"));
		}
		if (wanted == null || wanted.equals(POINT)) {
			for (SetupPoint p : points.all())
				rows.add(new ListRow(POINT, p.getId(), p.getName(), p.getWorld(), p.getX(), p.getY(), p.getZ(),
				                     " [" + p.getKind() + "]"));
		}

		if (rows.isEmpty()) {
			sender.sendMessage(messages.format(Key.LIST_EMPTY, Map.of()));
			return;
		}
		String kindLabel = wanted == null ? messages.format(Key.KIND_ALL, Map.of()) : wanted;
		sender.sendMessage(messages.format(Key.LIST_HEADER, Map.of("kind", kindLabel,
		                                                           "count", String.valueOf(rows.size()))));
		for (ListRow row : rows) sendRow(sender, row);
	}

	/**
	 * A player gets a clickable {@code tp} that runs {@code /glw cop setup tp <kind> <id>}, with the place in the hover.
	 * The console cannot see a hover, so it gets the label and the place as one plain line.
	 */
	private void sendRow(CommandSender sender, ListRow row) {
		String label = messages.format(Key.ROW_LABEL, Map.of("kind", row.kind(),
		                                                     "id", String.valueOf(row.id()),
		                                                     "name", row.name()));
		String where = messages.format(Key.ROW_WHERE, Map.of("world", row.world(),
		                                                     "x", String.valueOf(block(row.x())),
		                                                     "y", String.valueOf(block(row.y())),
		                                                     "z", String.valueOf(block(row.z())),
		                                                     "tags", row.tags()));
		if (!(sender instanceof Player)) {
			sender.sendMessage(label + where);
			return;
		}
		String tpCommand = "/glw cop setup tp " + row.kind() + " " + row.id();

		var message = new ComponentBuilder(label)
				.append(messages.format(Key.ROW_TP, Map.of()))
				.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
				.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(where)))
				.create();

		sender.spigot().sendMessage(message);
	}

	private static int block(double coordinate) {
		return (int) Math.floor(coordinate);
	}

	/** One placed item as the list shows it; {@code tags} is the already-formatted suffix (empty or {@code " [a,b]"}). */
	private record ListRow(String kind, int id, String name, String world, double x, double y, double z, String tags) {
	}

	public void remove(CommandSender sender, String kind, String idText) {
		String lower = kind.toLowerCase(Locale.ROOT);
		if (!kinds().contains(lower)) {
			sender.sendMessage(messages.format(Key.UNKNOWN_KIND, Map.of()));
			return;
		}
		Integer id = parseId(sender, idText);
		if (id == null) return;

		boolean removed;
		switch (lower) {
			case STATION -> {
				removed = stations.remove(id);
				if (removed) spawns.unassignStation(id);
			}
			case REGION -> removed = regions.remove(id);
			default -> removed = points.remove(id);
		}

		sender.sendMessage(messages.format(removed ? Key.REMOVED : Key.NOT_FOUND, Map.of("kind", lower, "id", idText)));
	}

	/** To the station anchor, the point, or the region's centre (top block + 1). */
	public void teleport(Player admin, String kind, String idText) {
		String lower = kind.toLowerCase(Locale.ROOT);
		if (!kinds().contains(lower)) {
			admin.sendMessage(messages.format(Key.UNKNOWN_KIND, Map.of()));
			return;
		}
		Integer id = parseId(admin, idText);
		if (id == null) return;

		boolean  found;
		Location to = null;
		switch (lower) {
			case STATION -> {
				Station station = stations.get(id);
				found = station != null;
				if (found) to = station.getLocation();
			}
			case REGION -> {
				AdminRegion region = regions.get(id);
				found = region != null;
				if (found) to = centre(region);
			}
			default -> {
				SetupPoint point = points.get(id);
				found = point != null;
				if (found) to = point.getLocation();
			}
		}

		if (!found) {
			admin.sendMessage(messages.format(Key.NOT_FOUND, Map.of("kind", lower, "id", idText)));
		} else if (to == null) {
			admin.sendMessage(messages.format(Key.NO_LOCATION, Map.of()));
		} else {
			admin.teleport(to);
			admin.sendMessage(messages.format(Key.TELEPORTED, Map.of("kind", lower, "id", idText)));
		}
	}

	private static @Nullable Location centre(AdminRegion region) {
		World world = Bukkit.getWorld(region.getWorld());
		if (world == null) return null;

		double x = (region.getMinX() + region.getMaxX() + 1) / 2.0;
		double z = (region.getMinZ() + region.getMaxZ() + 1) / 2.0;
		int    y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z));
		return new Location(world, x, y + 1, z);
	}

	/** {@code jail} is a jail id or {@code none}; an unknown station or jail refuses and changes nothing. */
	public void link(CommandSender sender, String stationText, String jail) {
		Integer stationId = parseId(sender, stationText);
		if (stationId == null) return;
		if (stations.get(stationId) == null) {
			sender.sendMessage(messages.format(Key.NOT_FOUND, Map.of("kind", STATION, "id", stationText)));
			return;
		}

		if (jail.equalsIgnoreCase("none")) {
			stations.linkJail(stationId, null);
			sender.sendMessage(messages.format(Key.UNLINKED, Map.of("station", stationText)));
			return;
		}

		Integer jailId = parseId(sender, jail);
		if (jailId == null) return;
		if (jails.getJail(jailId) == null) {
			sender.sendMessage(messages.format(Key.UNKNOWN_JAIL, Map.of("id", jail)));
			return;
		}

		stations.linkJail(stationId, jailId);
		sender.sendMessage(messages.format(Key.LINKED, Map.of("station", stationText, "jail", jail)));
	}

	private @Nullable Integer parseId(CommandSender sender, String text) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException exception) {
			sender.sendMessage(messages.format(Key.BAD_ID, Map.of("value", text)));
			return null;
		}
	}
}
