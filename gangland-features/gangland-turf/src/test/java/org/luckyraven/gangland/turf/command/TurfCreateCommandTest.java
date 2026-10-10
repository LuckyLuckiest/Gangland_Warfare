package org.luckyraven.gangland.turf.command;

import org.bukkit.Location;
import org.bukkit.World;
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
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.Selection;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.mockito.ArgumentCaptor;
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
 * {@code /glw turf create}: size limits (TF-21), chunk snapping (D3), duplicate names (TF-29 family) and the admin
 * refusal (TF-30). Logic lives in {@link TurfCreateCommand#create}, which the optional argument calls.
 */
@DisplayName("TurfCreateCommand - limits, snap, duplicate names, admin gate")
class TurfCreateCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> settings;
	private TurfManager            turfs;
	private WandSelectionManager   selections;
	private TurfMessageContract    legacy;
	private TurfMessages           text;
	private TurfSettings           turfSettings;
	private TurfCreateCommand      command;

	@BeforeEach
	void setUp() throws IOException {
		settings = TurfTextFixture.stubMoneySymbol();
		turfs    = mock(TurfManager.class);
		when(turfs.getAll()).thenReturn(List.of());
		when(turfs.allocateId()).thenReturn(7);
		selections   = new WandSelectionManager();
		legacy       = mock(TurfMessageContract.class);
		text         = TurfTextFixture.messages(tempDir, legacy);
		turfSettings = mock(TurfSettings.class);
		when(turfSettings.getDefaultIncomeAmount()).thenReturn(BigDecimal.TEN);
	}

	@AfterEach
	void tearDown() {
		settings.close();
	}

	private void buildCommand(String rulesYaml) throws IOException {
		buildCommand(rulesYaml, text);
	}

	private void buildCommand(String rulesYaml, TurfMessages messages) throws IOException {
		TurfRules rules = TurfTextFixture.rules(tempDir, rulesYaml);
		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		Tree<Argument> tree  = new Tree<>();
		Argument       parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("gangland.command.turf");
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			command = new TurfCreateCommand(plugin, tree, parent, turfs, selections, legacy, turfSettings, rules,
			                                messages);
		}
	}

	private static Location at(World world, int x, int z) {
		return new Location(world, x, 64, z);
	}

	private Player player(boolean admin, int x1, int z1, int x2, int z2) {
		World world = mock(World.class);
		when(world.getName()).thenReturn("world");
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.hasPermission(WandSelectionManager.ADMIN_PERMISSION)).thenReturn(admin);
		Selection selection = selections.get(player);
		selection.set(at(world, x1, z1), true);
		selection.set(at(world, x2, z2), false);
		return player;
	}

	private static String[] create(String name) {
		return new String[]{"turf", "create", name};
	}

	@Test
	@DisplayName("TF-21: an 8 x 8 selection with snapping off is refused as too small")
	void create_tooSmall_refused() throws IOException {
		buildCommand("Create:\n   Snap_To_Chunks: false\n   Min_Side_Blocks: 16\n   Max_Side_Blocks: 512\n");
		Player player = player(true, 0, 0, 7, 7);

		command.create(player, create("Docks"));

		verify(turfs, never()).create(any());
		verify(player).sendMessage(text.text("Turf.Create_Too_Small", "&cThat turf is too small: %x% x %z% blocks. The minimum side is %min% blocks.",
		                                     "min", "16", "max", "512", "x", "8", "z", "8"));
	}

	@Test
	@DisplayName("TF-21: a 600-block-wide selection is refused as too large")
	void create_tooLarge_refused() throws IOException {
		buildCommand("Create:\n   Snap_To_Chunks: false\n   Min_Side_Blocks: 16\n   Max_Side_Blocks: 512\n");
		Player player = player(true, 0, 0, 599, 20);

		command.create(player, create("Docks"));

		verify(turfs, never()).create(any());
		verify(player).sendMessage(text.text("Turf.Create_Too_Large", "&cThat turf is too large: %x% x %z% blocks. The maximum side is %max% blocks.",
		                                     "min", "16", "max", "512", "x", "600", "z", "21"));
	}

	@Test
	@DisplayName("a name equal to an existing turf's name ignoring case is refused")
	void create_duplicateName_refused() throws IOException {
		buildCommand("Create:\n   Snap_To_Chunks: false\n   Min_Side_Blocks: 16\n   Max_Side_Blocks: 512\n");
		Turf docks = new Turf(1, "Docks", new CuboidRegion("world", 500, 500, 600, 600), null, BigDecimal.ONE,
		                      0L, 0L);
		when(turfs.getAll()).thenReturn(List.of(docks));
		Player player = player(true, 0, 0, 31, 31);

		command.create(player, create("docks"));

		verify(turfs, never()).create(any());
		verify(player).sendMessage(text.text("Turf.Name_Taken", "&cA turf named %turf% already exists.",
		                                     "turf", "docks"));
	}

	@Test
	@DisplayName("D3: a snapped create stores the snapped bounds and says so")
	void create_snapped_storesSnappedBounds() throws IOException {
		buildCommand("Create:\n   Snap_To_Chunks: true\n   Min_Side_Blocks: 16\n   Max_Side_Blocks: 512\n");
		Player player = player(true, 3, 5, 20, 40);

		command.create(player, create("Docks"));

		ArgumentCaptor<Turf> captor = ArgumentCaptor.forClass(Turf.class);
		verify(turfs).create(captor.capture());
		CuboidRegion region = captor.getValue().getRegion();
		assertEquals(0, region.getMinX());
		assertEquals(0, region.getMinZ());
		assertEquals(31, region.getMaxX());
		assertEquals(47, region.getMaxZ());
		verify(player).sendMessage(text.text("Turf.Create_Snapped", "&7Snapped to chunks: (%minx%, %minz%) to (%maxx%, %maxz%)",
		                                     "minx", "0", "minz", "0", "maxx", "31", "maxz", "47"));
	}

	@Test
	@DisplayName("a server whose turf_messages.yml lacks the create keys sees the same wording as the shipped file")
	void create_missingYamlKeys_fallbacksMatchShipped() throws IOException {
		TurfMessages bare = TurfTextFixture.emptyMessages(tempDir, legacy);
		buildCommand("Create:\n   Snap_To_Chunks: false\n   Min_Side_Blocks: 16\n   Max_Side_Blocks: 512\n", bare);
		Player small = player(true, 0, 0, 7, 7);
		Player large = player(true, 0, 0, 599, 20);

		command.create(small, create("Docks"));
		command.create(large, create("Docks"));

		verify(small).sendMessage(text.text("Turf.Create_Too_Small", "",
		                                    "min", "16", "max", "512", "x", "8", "z", "8"));
		verify(large).sendMessage(text.text("Turf.Create_Too_Large", "",
		                                    "min", "16", "max", "512", "x", "600", "z", "21"));
	}

	@Test
	@DisplayName("a server whose turf_messages.yml lacks Create_Snapped sees the shipped snap line")
	void create_snapped_missingYamlKey_fallbackMatchesShipped() throws IOException {
		buildCommand("Create:\n   Snap_To_Chunks: true\n   Min_Side_Blocks: 16\n   Max_Side_Blocks: 512\n",
		             TurfTextFixture.emptyMessages(tempDir, legacy));
		Player player = player(true, 3, 5, 20, 40);

		command.create(player, create("Docks"));

		verify(player).sendMessage(text.text("Turf.Create_Snapped", "",
		                                     "minx", "0", "minz", "0", "maxx", "31", "maxz", "47"));
	}

	@Test
	@DisplayName("TF-30: a sender without gangland.turf.admin gets Admin_Only and creates nothing")
	void create_nonAdmin_refused() throws IOException {
		buildCommand("Create:\n   Snap_To_Chunks: false\n   Min_Side_Blocks: 16\n   Max_Side_Blocks: 512\n");
		Player player = player(false, 0, 0, 31, 31);

		command.create(player, create("Docks"));

		verify(turfs, never()).create(any());
		verify(player).sendMessage(text.text("Turf.Admin_Only", "&cOnly turf admins can do that."));
	}
}
