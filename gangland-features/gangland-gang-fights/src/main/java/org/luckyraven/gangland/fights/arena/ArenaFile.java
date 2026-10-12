package org.luckyraven.gangland.fights.arena;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.luckyraven.gangland.data.region.RegionShape;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code fight/arenas.yml} format: reading hand-edited files and writing atomically.
 *
 * <pre>
 * Arenas:
 *   Pit:
 *     World: world
 *     Enabled: true
 *     Box:
 *       Min: {X, Y, Z}
 *       Max: {X, Y, Z}
 *     Spawns:
 *       A: [ {X, Y, Z, Yaw, Pitch}, ... ]
 *       B: [ ... ]
 *     Exit: {World, X, Y, Z, Yaw, Pitch}
 * </pre>
 */
public final class ArenaFile {

	private static final String HEADER = "Fight arenas. Built with /glw arena; after a hand edit run /glw arena reload.";

	/** The arenas that parsed and one sentence per entry that did not. */
	public record Loaded(List<Arena> arenas, List<String> skipped) {
	}

	private ArenaFile() {
	}

	/** Parses every entry of {@code Arenas}; an entry that does not parse is skipped with one sentence. */
	public static Loaded read(FileConfiguration config) {
		List<Arena>  arenas  = new ArrayList<>();
		List<String> skipped = new ArrayList<>();

		ConfigurationSection section = config.getConfigurationSection("Arenas");
		if (section == null) {
			return new Loaded(arenas, skipped);
		}
		for (String name : section.getKeys(false)) {
			try {
				arenas.add(parse(name, section.getConfigurationSection(name)));
			} catch (RuntimeException exception) {
				skipped.add("Arena '" + name + "' skipped: " + exception.getMessage());
			}
		}
		return new Loaded(arenas, skipped);
	}

	private static Arena parse(String name, ConfigurationSection entry) {
		if (entry == null) {
			throw new IllegalArgumentException("not a section");
		}
		String world = required(entry.getString("World"), "World");
		ArenaBox box = new ArenaBox(world,
		                            RegionShape.Cuboid.of(
				                            integer(entry, "Box.Min.X"), integer(entry, "Box.Min.Y"),
				                            integer(entry, "Box.Min.Z"), integer(entry, "Box.Max.X"),
				                            integer(entry, "Box.Max.Y"), integer(entry, "Box.Max.Z")));

		List<ArenaPoint> spawnsA = points(entry, "Spawns.A", world);
		List<ArenaPoint> spawnsB = points(entry, "Spawns.B", world);
		if (spawnsA.isEmpty() || spawnsB.isEmpty()) {
			throw new IllegalArgumentException("each side needs at least one spawn");
		}
		if (!entry.isConfigurationSection("Exit")) {
			throw new IllegalArgumentException("Exit is missing");
		}
		ArenaPoint exit = point(entry.getConfigurationSection("Exit"), required(entry.getString("Exit.World"),
		                                                                       "Exit.World"));
		return new Arena(name, box, spawnsA, spawnsB, exit, entry.getBoolean("Enabled", true));
	}

	private static String required(String value, String key) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(key + " is missing");
		}
		return value;
	}

	private static int integer(ConfigurationSection entry, String key) {
		if (!entry.isInt(key)) {
			throw new IllegalArgumentException(key + " is not a whole number");
		}
		return entry.getInt(key);
	}

	private static double number(ConfigurationSection entry, String key) {
		if (!(entry.isInt(key) || entry.isDouble(key))) {
			throw new IllegalArgumentException(key + " is not a number");
		}
		return entry.getDouble(key);
	}

	private static ArenaPoint point(ConfigurationSection section, String world) {
		return new ArenaPoint(world, number(section, "X"), number(section, "Y"), number(section, "Z"),
		                      (float) section.getDouble("Yaw", 0), (float) section.getDouble("Pitch", 0));
	}

	private static List<ArenaPoint> points(ConfigurationSection entry, String key, String world) {
		List<ArenaPoint> points = new ArrayList<>();
		for (Map<?, ?> raw : entry.getMapList(key)) {
			YamlConfiguration holder = new YamlConfiguration();
			raw.forEach((k, v) -> holder.set(String.valueOf(k), v));
			points.add(point(holder, world));
		}
		return points;
	}

	/** The in-memory YAML of {@code arenas}, keys in file order. */
	public static YamlConfiguration toConfig(Collection<Arena> arenas) {
		YamlConfiguration config = new YamlConfiguration();
		config.options().header(HEADER);
		for (Arena arena : arenas) {
			String base = "Arenas." + arena.name() + ".";
			var    cuboid = arena.box().cuboid();
			config.set(base + "World", arena.box().world());
			config.set(base + "Enabled", arena.enabled());
			config.set(base + "Box.Min.X", cuboid.minX());
			config.set(base + "Box.Min.Y", cuboid.minY());
			config.set(base + "Box.Min.Z", cuboid.minZ());
			config.set(base + "Box.Max.X", cuboid.maxX());
			config.set(base + "Box.Max.Y", cuboid.maxY());
			config.set(base + "Box.Max.Z", cuboid.maxZ());
			config.set(base + "Spawns.A", maps(arena.spawnsA()));
			config.set(base + "Spawns.B", maps(arena.spawnsB()));
			config.set(base + "Exit", exit(arena.exit()));
		}
		return config;
	}

	private static List<Map<String, Object>> maps(List<ArenaPoint> points) {
		List<Map<String, Object>> list = new ArrayList<>();
		for (ArenaPoint point : points) {
			list.add(coordinates(point));
		}
		return list;
	}

	private static Map<String, Object> exit(ArenaPoint exit) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("World", exit.world());
		map.putAll(coordinates(exit));
		return map;
	}

	private static Map<String, Object> coordinates(ArenaPoint point) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("X", point.x());
		map.put("Y", point.y());
		map.put("Z", point.z());
		map.put("Yaw", (double) point.yaw());
		map.put("Pitch", (double) point.pitch());
		return map;
	}

	/** Writes to a temp file beside {@code file}, then moves it over: a crash never leaves half a file. */
	public static void write(Path file, Collection<Arena> arenas) throws IOException {
		String text = toConfig(arenas).saveToString();

		Path parent = file.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		Path temp = Files.createTempFile(parent, "arenas", ".tmp");
		try {
			Files.writeString(temp, text, StandardCharsets.UTF_8);
			try {
				Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temp);
		}
	}
}
