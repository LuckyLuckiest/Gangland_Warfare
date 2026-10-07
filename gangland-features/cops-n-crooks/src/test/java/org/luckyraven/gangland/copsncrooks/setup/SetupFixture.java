package org.luckyraven.gangland.copsncrooks.setup;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.place.AdminRegionRegistry;
import org.luckyraven.gangland.copsncrooks.place.SetupPointRegistry;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared wiring of the setup tests: real registries over mocked repositories, a mocked spawn manager and outline, the
 * real {@link SetupMessages} reading an empty {@code setup.yml} (so every line is its in-code fallback), and Bukkit
 * statics with one loaded world named {@code world}.
 */
@SuppressWarnings("unchecked")
final class SetupFixture implements AutoCloseable {

	final BukkitStatics             bukkit     = BukkitStatics.install();
	final World                     world      = world("world");
	final StationRegistry           stations;
	final AdminRegionRegistry       regions;
	final SetupPointRegistry        points;
	final CopSpawnManager           spawns     = mock(CopSpawnManager.class);
	final JailRegistry              jails      = new JailRegistry();
	final SetupSelections           selections = new SetupSelections();
	final SetupOutline              outline    = mock(SetupOutline.class);
	final SetupMessages             messages;
	final SetupCommands             commands;
	Supplier<CopConfigProvider> provider = () -> null;

	SetupFixture(Path tempDir) throws IOException {
		NbtBridge.install(new StackTags());
		primeMoneySymbol();
		bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);

		stations = new StationRegistry(mock(IRepository.class));
		regions  = new AdminRegionRegistry(mock(IRepository.class));
		points   = new SetupPointRegistry(mock(IRepository.class));

		Files.writeString(tempDir.resolve("setup.yml"), "Setup:\n", StandardCharsets.UTF_8);
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, tempDir.resolve("setup.yml").toFile()), false);
		messages = new SetupMessages(fileManager);

		commands = new SetupCommands(stations, regions, points, spawns, jails, selections, messages, outline,
		                             () -> provider.get());
	}

	static World world(String name) {
		World w = mock(World.class);
		when(w.getName()).thenReturn(name);
		return w;
	}

	Location at(double x, double y, double z) {
		return new Location(world, x, y, z);
	}

	/** An admin standing at (5, 64, 5) of {@code world}. */
	Player admin() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getLocation()).thenReturn(at(5, 64, 5));
		return player;
	}

	String text(SetupMessages.Key key, Map<String, String> values) {
		return messages.format(key, values);
	}

	String text(SetupMessages.Key key) {
		return messages.format(key, Map.of());
	}

	private static void primeMoneySymbol() {
		try {
			Field field = Settings.class.getDeclaredField("moneySymbol");
			field.setAccessible(true);
			if (field.get(null) == null) field.set(null, "$");
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}

	@Override
	public void close() {
		NbtBridge.reset();
		bukkit.close();
	}
}
