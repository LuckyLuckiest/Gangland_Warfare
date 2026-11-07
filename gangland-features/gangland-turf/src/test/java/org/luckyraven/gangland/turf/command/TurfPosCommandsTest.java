package org.luckyraven.gangland.turf.command;

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
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.selection.Selection;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.util.TriConsumer;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code /glw turf pos1} and {@code /glw turf pos2}: each sets its own corner at the admin's block, answers with the
 * corner name and block coordinates, and refuses a corner in another world than the other corner (TF-29).
 */
@DisplayName("TurfPos1Command / TurfPos2Command - set one corner")
class TurfPosCommandsTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> settings;
	private WandSelectionManager   selections;
	private TurfMessages           text;
	private TriConsumer<Argument, CommandSender, String[]> pos1;
	private TriConsumer<Argument, CommandSender, String[]> pos2;

	@BeforeEach
	void setUp() throws IOException {
		settings   = TurfTextFixture.stubMoneySymbol();
		selections = new WandSelectionManager();
		text       = TurfTextFixture.messages(tempDir, mock(TurfMessageContract.class));
		MessageProvider provider = mock(MessageProvider.class);
		when(provider.getString(anyString())).thenReturn("%corner%|%x%|%y%|%z%|%world%");
		Messages.init(provider);

		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		Argument   parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("gangland.command.turf");
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			pos1 = new TurfPos1Command(plugin, new Tree<>(), parent, selections, text).action();
			pos2 = new TurfPos2Command(plugin, new Tree<>(), parent, selections, text).action();
		}
	}

	@AfterEach
	void tearDown() {
		settings.close();
	}

	private static World world(String name) {
		World world = mock(World.class);
		when(world.getName()).thenReturn(name);
		return world;
	}

	private static Player admin(Location location) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.hasPermission(TurfPermissions.ADMIN)).thenReturn(true);
		when(player.getLocation()).thenReturn(location);
		return player;
	}

	/** The one message sent to the player carries the filled-in template (the message type may add a prefix around it). */
	private static void assertReplyHas(Player player, String expected) {
		ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
		verify(player).sendMessage(sent.capture());
		assertTrue(sent.getValue().contains(expected), sent.getValue());
	}

	@Test
	@DisplayName("pos1 sets only corner one, clears the selected turf and replies with its name and block coordinates")
	void pos1_setsCornerOne() {
		Location location = new Location(world("w"), 1.5, 64.9, -3.2);
		Player   player   = admin(location);
		selections.get(player).setActiveTurfId(9);

		pos1.accept(null, player, new String[]{"turf", "pos1"});

		Selection selection = selections.get(player);
		assertSame(location, selection.getPos1());
		assertNull(selection.getPos2());
		assertNull(selection.getActiveTurfId());
		assertReplyHas(player, "pos1|1|64|-4|w");
	}

	@Test
	@DisplayName("pos2 sets only corner two, clears the selected turf and replies with its name and block coordinates")
	void pos2_setsCornerTwo() {
		Location location = new Location(world("w"), -0.5, 70.0, 8.9);
		Player   player   = admin(location);
		selections.get(player).setActiveTurfId(9);

		pos2.accept(null, player, new String[]{"turf", "pos2"});

		Selection selection = selections.get(player);
		assertSame(location, selection.getPos2());
		assertNull(selection.getPos1());
		assertNull(selection.getActiveTurfId());
		assertReplyHas(player, "pos2|-1|70|8|w");
	}

	@Test
	@DisplayName("a corner in another world than the other corner is refused with the other-world line and keeps the selection")
	void otherWorldCorner_refused() {
		Location kept = new Location(world("a"), 0, 64, 0);
		Player   one  = admin(new Location(world("b"), 5, 64, 5));
		selections.get(one).set(kept, false);
		selections.get(one).setActiveTurfId(4);

		pos1.accept(null, one, new String[]{"turf", "pos1"});

		assertNull(selections.get(one).getPos1());
		assertSame(kept, selections.get(one).getPos2());
		assertEquals(4, selections.get(one).getActiveTurfId());
		verify(one).sendMessage(text.text("Turf.Selection_Other_World", Selection.OTHER_WORLD_FALLBACK));

		Player two = admin(new Location(world("b"), 5, 64, 5));
		selections.get(two).set(kept, true);

		pos2.accept(null, two, new String[]{"turf", "pos2"});

		assertNull(selections.get(two).getPos2());
		assertSame(kept, selections.get(two).getPos1());
		verify(two).sendMessage(text.text("Turf.Selection_Other_World", Selection.OTHER_WORLD_FALLBACK));
	}

	@Test
	@DisplayName("a sender without the admin node gets the admin-only line and no corner is set")
	void nonAdmin_refused() {
		Player player = admin(new Location(world("w"), 1, 64, 1));
		when(player.hasPermission(TurfPermissions.ADMIN)).thenReturn(false);

		pos1.accept(null, player, new String[]{"turf", "pos1"});
		pos2.accept(null, player, new String[]{"turf", "pos2"});

		assertNull(selections.get(player).getPos1());
		assertNull(selections.get(player).getPos2());
		verify(player, org.mockito.Mockito.times(2)).sendMessage(text.text("Turf.Admin_Only", "&cOnly turf admins can do that."));
	}

	@Test
	@DisplayName("an admin sender that is not a player is ignored silently")
	void nonPlayer_ignored() {
		CommandSender console = mock(CommandSender.class);
		when(console.hasPermission(TurfPermissions.ADMIN)).thenReturn(true);

		pos1.accept(null, console, new String[]{"turf", "pos1"});
		pos2.accept(null, console, new String[]{"turf", "pos2"});

		verify(console, never()).sendMessage(anyString());
		verify(console, org.mockito.Mockito.times(2)).hasPermission(TurfPermissions.ADMIN);
		verifyNoMoreInteractions(console);
	}
}
