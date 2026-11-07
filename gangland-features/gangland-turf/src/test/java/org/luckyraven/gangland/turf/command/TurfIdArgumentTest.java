package org.luckyraven.gangland.turf.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The {@code <id>} argument of {@code /glw turf select [id]} and {@code /glw turf tp [id]}: a word that is not a number
 * and an id with no turf both answer TURF_NOT_FOUND with the typed word, a known id is acted on, and the tab completion
 * offers every turf id in manager order.
 */
@DisplayName("TurfSelectCommand / TurfTpCommand - the id argument")
class TurfIdArgumentTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> settings;
	private TurfMessages           text;
	private TurfMessageContract    messages;
	private TurfManager            turfs;
	private WandSelectionManager   selections;
	private OptionalArgument       selectId;
	private OptionalArgument       tpId;

	@BeforeEach
	void setUp() throws IOException {
		settings   = TurfTextFixture.stubMoneySymbol();
		messages   = mock(TurfMessageContract.class);
		text       = TurfTextFixture.messages(tempDir, messages);
		turfs      = mock(TurfManager.class);
		selections = new WandSelectionManager();

		JavaPlugin     plugin = PluginMocks.plugin(tempDir);
		Argument       parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("gangland.command.turf");
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			selectId = idArgument(new TurfSelectCommand(plugin, new Tree<>(), parent, turfs, selections, messages, text));
			tpId     = idArgument(new TurfTpCommand(plugin, new Tree<>(), parent, turfs, selections, messages, text));
		}
	}

	@AfterEach
	void tearDown() {
		settings.close();
	}

	/** The one sub-argument a command attaches in its constructor: its {@code <id>} argument. */
	private static OptionalArgument idArgument(Argument command) {
		return (OptionalArgument) command.getNode().getChildren().get(0).getData();
	}

	private static Turf turf(int id, String name) {
		return new Turf(id, name, new CuboidRegion("world", 0, 0, 10, 10), null, BigDecimal.ONE, 0L, 0L);
	}

	private static Player admin() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.hasPermission(TurfPermissions.ADMIN)).thenReturn(true);
		when(player.getLocation()).thenReturn(new Location(null, 0, 0, 0, 12f, 7f));
		return player;
	}

	@Test
	@DisplayName("a word that is not a number answers TURF_NOT_FOUND with the typed word and acts on nothing")
	void notANumber_turfNotFound() {
		for (OptionalArgument argument : List.of(selectId, tpId)) {
			Player player = admin();
			argument.executeArgument(player, new String[]{"turf", "x", "abc"});

			verify(messages).send(player, "TURF_NOT_FOUND", "turf", "abc");
			assertNull(selections.get(player).getActiveTurfId());
		}
	}

	@Test
	@DisplayName("a number with no turf answers TURF_NOT_FOUND with the word as typed, not the parsed id")
	void unknownId_turfNotFoundWithTheTypedWord() {
		for (OptionalArgument argument : List.of(selectId, tpId)) {
			Player player = admin();
			argument.executeArgument(player, new String[]{"turf", "x", "007"});

			verify(messages).send(player, "TURF_NOT_FOUND", "turf", "007");
			assertNull(selections.get(player).getActiveTurfId());
		}
	}

	@Test
	@DisplayName("select with a known id makes it the active turf and says so")
	void select_knownId() {
		when(turfs.get(3)).thenReturn(turf(3, "Docks"));
		Player player = admin();

		selectId.executeArgument(player, new String[]{"turf", "select", "3"});

		assertEquals(3, selections.get(player).getActiveTurfId());
		verify(messages).send(player, "TURF_SELECTED", "turf", "Docks", "id", "3");
	}

	@Test
	@DisplayName("tp with a known id teleports to the turf centre, makes it the active turf and says so")
	void tp_knownId() {
		when(turfs.get(3)).thenReturn(turf(3, "Docks"));
		World world = mock(World.class);
		when(world.getHighestBlockYAt(5, 5)).thenReturn(70);
		Player player = admin();

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);
			tpId.executeArgument(player, new String[]{"turf", "tp", "3"});
		}

		org.mockito.ArgumentCaptor<Location> target = org.mockito.ArgumentCaptor.forClass(Location.class);
		verify(player).teleport(target.capture());
		assertEquals(5.5, target.getValue().getX());
		assertEquals(71.0, target.getValue().getY());
		assertEquals(5.5, target.getValue().getZ());
		assertEquals(12f, target.getValue().getYaw());
		assertEquals(3, selections.get(player).getActiveTurfId());
		verify(messages).send(player, "TURF_TP_SUCCESS", "turf", "Docks", "id", "3");
	}

	@Test
	@DisplayName("a sender without the admin node gets the admin-only line and nothing else happens")
	void nonAdmin_refused() {
		for (OptionalArgument argument : List.of(selectId, tpId)) {
			Player player = admin();
			when(player.hasPermission(TurfPermissions.ADMIN)).thenReturn(false);

			argument.executeArgument(player, new String[]{"turf", "x", "3"});

			verify(player).sendMessage(text.text("Turf.Admin_Only", "&cOnly turf admins can do that."));
		}
		verifyNoInteractions(messages);
	}

	@Test
	@DisplayName("an admin sender that is not a player is ignored silently")
	void nonPlayer_ignored() {
		for (OptionalArgument argument : List.of(selectId, tpId)) {
			CommandSender console = mock(CommandSender.class);
			when(console.hasPermission(TurfPermissions.ADMIN)).thenReturn(true);

			argument.executeArgument(console, new String[]{"turf", "x", "abc"});
		}
		verifyNoInteractions(messages);
	}

	@Test
	@DisplayName("the completion offers every turf id in manager order, in a list the caller may change")
	void completion_idsInManagerOrder() {
		when(turfs.getAll()).thenReturn(List.of(turf(3, "C"), turf(1, "A"), turf(2, "B")));

		for (OptionalArgument argument : List.of(selectId, tpId)) {
			List<String> ids = argument.getArgumentString(mock(CommandSender.class));

			assertEquals(List.of("3", "1", "2"), ids);
			assertDoesNotThrow(() -> ids.add("x"));
		}
	}

	@Test
	@DisplayName("with no turf the completion offers the single empty suggestion")
	void completion_noTurfs() {
		when(turfs.getAll()).thenReturn(List.of());

		assertEquals(List.of(""), selectId.getArgumentString(mock(CommandSender.class)));
		assertEquals(List.of(""), tpId.getArgumentString(mock(CommandSender.class)));
	}
}
