package org.luckyraven.gangland.turf.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.PlayerInventory;
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
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.selection.Selection;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code /glw turf wand}: giving a wand starts a new selection, so a corner in another world than the old selection is
 * accepted after the wand is given (TF-29 follow-up, NEW-W1-T-1).
 */
@DisplayName("TurfWandCommand - a new wand starts a new selection")
class TurfWandCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> settings;
	private WandSelectionManager   selections;
	private TurfMessages           text;
	private TurfSettings           turfSettings;

	@BeforeEach
	void setUp() throws IOException {
		settings     = TurfTextFixture.stubMoneySymbol();
		selections   = new WandSelectionManager();
		text         = TurfTextFixture.messages(tempDir, mock(TurfMessageContract.class));
		turfSettings = mock(TurfSettings.class);
		when(turfSettings.getWandItemType()).thenReturn("BLAZE_ROD");
		// The action replies with Messages.TURF_WAND_GIVEN; any text will do, the test is about the selection.
		MessageProvider provider = mock(MessageProvider.class);
		when(provider.getString(anyString())).thenReturn("&6Turf wand given.");
		Messages.init(provider);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
		settings.close();
	}

	private static World world(String name) {
		World world = mock(World.class);
		when(world.getName()).thenReturn(name);
		return world;
	}

	@Test
	@DisplayName("both corners in world A, the wand action, then a corner in world B is accepted")
	void wand_clearsSelection_otherWorldCornerAccepted() throws IOException {
		World a = world("a");
		World b = world("b");
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.hasPermission(WandSelectionManager.ADMIN_PERMISSION)).thenReturn(true);
		when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
		Selection old = selections.get(player);
		old.set(new Location(a, 0, 64, 0), true);
		old.set(new Location(a, 5, 64, 5), false);
		assertTrue(old.isComplete());

		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		Tree<Argument> tree = new Tree<>();
		Argument parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("gangland.command.turf");
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			// XSeries resolves BLAZE_ROD from the server version; a bare Bukkit mock has none.
			bukkit.statics().when(Bukkit::getBukkitVersion).thenReturn("1.16.5-R0.1-SNAPSHOT");
			bukkit.statics().when(Bukkit::getVersion).thenReturn("git-Spigot-test (MC: 1.16.5)");
			// ItemBuilder sets the display name and lore through ItemMeta, so the item factory must hand one back.
			ItemFactory factory = mock(ItemFactory.class);
			ItemMeta    meta    = mock(ItemMeta.class);
			when(factory.getItemMeta(any())).thenReturn(meta);
			when(factory.asMetaFor(any(), any(Material.class))).thenReturn(meta);
			bukkit.statics().when(Bukkit::getItemFactory).thenReturn(factory);
			// NBTAPI is on the test classpath but its authlib is not; the wand's tag is not under test, so drop it.
			NbtBridge.install(null);
			TurfWandCommand command = new TurfWandCommand(plugin, tree, parent, selections, turfSettings, text);
			command.action().accept(null, player, new String[]{"turf", "wand"});
		}

		Selection fresh = selections.get(player);
		assertNull(fresh.getPos1());
		assertTrue(fresh.set(new Location(b, 0, 64, 0), true), "a corner in world B must start a new selection");
	}
}
