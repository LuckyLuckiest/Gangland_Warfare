package org.luckyraven.gangland.turf.command;

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
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /glw turf rename <name>} (TF-39): renames and persists the active turf, refuses a duplicate name (TF-29
 * family) and refuses a non-admin (TF-30).
 */
@DisplayName("TurfRenameCommand - rename, duplicate check, admin gate (TF-39)")
class TurfRenameCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> settings;
	private TurfManager            turfs;
	private WandSelectionManager   selections;
	private TurfMessages           text;
	private TurfRenameCommand      command;
	private Turf                   dup;
	private Turf                   other;

	@BeforeEach
	void setUp() throws IOException {
		settings = TurfTextFixture.stubMoneySymbol();
		turfs    = mock(TurfManager.class);
		selections = new WandSelectionManager();
		text       = TurfTextFixture.messages(tempDir, mock(TurfMessageContract.class));

		dup   = new Turf(5, "Dup", new CuboidRegion("world", 0, 0, 10, 10), null, BigDecimal.TEN, 0L, 0L);
		other = new Turf(6, "Docks", new CuboidRegion("world", 100, 100, 110, 110), null, BigDecimal.TEN, 0L, 0L);
		when(turfs.get(5)).thenReturn(dup);
		when(turfs.getAll()).thenReturn(List.of(dup, other));

		JavaPlugin     plugin = PluginMocks.plugin(tempDir);
		Tree<Argument> tree   = new Tree<>();
		Argument       parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("gangland.command.turf");
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			command = new TurfRenameCommand(plugin, tree, parent, turfs, selections,
			                                mock(TurfMessageContract.class), text);
		}
	}

	@AfterEach
	void tearDown() {
		settings.close();
	}

	private Player player(boolean admin) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.hasPermission(WandSelectionManager.ADMIN_PERMISSION)).thenReturn(admin);
		selections.get(player).setActiveTurfId(5);
		return player;
	}

	@Test
	@DisplayName("renames the active turf and persists it")
	void rename_renamesAndPersists() {
		Player player = player(true);

		command.rename(player, new String[]{"turf", "rename", "Iron", "Yard"});

		assertEquals("Iron Yard", dup.getDisplayName());
		verify(turfs).persist(dup);
	}

	@Test
	@DisplayName("a name taken by another turf, ignoring case, is refused and nothing changes")
	void rename_duplicate_refused() {
		Player player = player(true);

		command.rename(player, new String[]{"turf", "rename", "docks"});

		assertEquals("Dup", dup.getDisplayName());
		verify(turfs, never()).persist(any());
		verify(player).sendMessage(text.text("Turf.Name_Taken", "&cA turf named %turf% already exists.",
		                                     "turf", "docks"));
	}

	@Test
	@DisplayName("T-243: a rename to a name empty after colour stripping (&a&l) is refused and nothing changes")
	void rename_colourOnlyName_refused() {
		Player player = player(true);

		command.rename(player, new String[]{"turf", "rename", "&a&l"});

		assertEquals("Dup", dup.getDisplayName());
		verify(turfs, never()).persist(any());
		verify(player).sendMessage(text.text("Turf.Name_Empty", "&cA turf name needs at least one letter or digit."));
	}

	@Test
	@DisplayName("a sender without gangland.turf.admin is refused and nothing changes")
	void rename_nonAdmin_refused() {
		Player player = player(false);

		command.rename(player, new String[]{"turf", "rename", "Iron"});

		assertEquals("Dup", dup.getDisplayName());
		verify(turfs, never()).persist(any());
		verify(player).sendMessage(text.text("Turf.Admin_Only", "&cOnly turf admins can do that."));
	}
}
