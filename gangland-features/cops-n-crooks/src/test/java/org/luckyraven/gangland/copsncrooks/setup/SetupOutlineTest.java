package org.luckyraven.gangland.copsncrooks.setup;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.keystone.item.ItemBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The selection outline: which blocks are drawn, the 256 cap, who sees it and when the repeating task stops. */
@DisplayName("SetupOutline")
class SetupOutlineTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private SetupFixture                fx;
	private final List<Player>          online = new ArrayList<>();
	private final Map<Player, Integer>  drawn  = new HashMap<>();
	private SetupOutline                outline;

	@BeforeEach
	void setUp() throws IOException {
		fx      = new SetupFixture(tempDir);
		outline = new SetupOutline(mock(JavaPlugin.class), fx.selections, fx.messages, () -> online,
		                           (player, at) -> drawn.merge(player, 1, Integer::sum));
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	private SetupSelection cuboid(int x1, int y1, int z1, int x2, int y2, int z2) {
		SetupSelection selection = new SetupSelection();
		selection.setMode(SetupMode.DISTRICT);
		selection.set(fx.at(x1, y1, z1), true);
		selection.set(fx.at(x2, y2, z2), false);
		return selection;
	}

	private static Set<List<Integer>> blocks(List<Location> points) {
		Set<List<Integer>> blocks = new HashSet<>();
		for (Location at : points) blocks.add(List.of(at.getBlockX(), at.getBlockY(), at.getBlockZ()));
		return blocks;
	}

	/** An admin in the online list: holds a wand (or a stick), may or may not use the tools, has a selection. */
	private Player admin(boolean wand, boolean permitted) {
		Player          player    = fx.admin();
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack       held      = new ItemStack(Material.STICK);
		if (wand) new ItemBuilder(held).addTag(SetupSelections.WAND_NBT_KEY, true);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getItemInMainHand()).thenReturn(held);
		when(player.hasPermission(SetupSelections.PERMISSION)).thenReturn(permitted);
		UUID id = player.getUniqueId();
		SetupSelection selection = fx.selections.get(id);
		selection.setMode(SetupMode.DISTRICT);
		selection.set(fx.at(0, 0, 0), true);
		selection.set(fx.at(2, 2, 2), false);
		online.add(player);
		return player;
	}

	@Test
	@DisplayName("a cuboid is its 12 edges: 8 corners and 12 edge middles of a 3x3x3 box, no face or inside")
	void cuboid_drawsTwelveEdges() {
		Set<List<Integer>> blocks = blocks(SetupOutline.points(cuboid(0, 0, 0, 2, 2, 2)));

		assertEquals(20, blocks.size());
		for (int x : new int[]{0, 2})
			for (int y : new int[]{0, 2})
				for (int z : new int[]{0, 2}) assertTrue(blocks.contains(List.of(x, y, z)));
		assertTrue(blocks.contains(List.of(1, 0, 0)));
		assertTrue(blocks.contains(List.of(2, 1, 2)));
		assertTrue(blocks.contains(List.of(0, 2, 1)));
		assertFalse(blocks.contains(List.of(1, 1, 0)), "face centre");
		assertFalse(blocks.contains(List.of(1, 1, 1)), "inside");
	}

	@Test
	@DisplayName("two corners given in any order draw the same box")
	void cuboid_isOrderIndependent() {
		assertEquals(blocks(SetupOutline.points(cuboid(0, 0, 0, 2, 2, 2))),
		             blocks(SetupOutline.points(cuboid(2, 2, 2, 0, 0, 0))));
	}

	@Test
	@DisplayName("a huge cuboid is thinned to at most 256 particles")
	void bigCuboid_capsAt256Particles() {
		int size = SetupOutline.points(cuboid(0, 0, 0, 999, 999, 999)).size();

		assertTrue(size <= SetupOutline.MAX_PARTICLES, "size " + size);
		assertTrue(size >= 128, "still a recognisable outline, size " + size);
	}

	@Test
	@DisplayName("a mis-clicked selection millions of blocks wide costs the cap, not the edge count")
	void hugeSelection_isBoundedByTheCap_notTheEdgeSet() {
		SetupSelection huge = cuboid(-1_000_000, 0, -1_000_000, 1_000_000, 255, 1_000_000);

		List<Location> points = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> SetupOutline.points(huge));

		assertTrue(points.size() <= SetupOutline.MAX_PARTICLES, "size " + points.size());
		assertTrue(points.size() >= 128, "size " + points.size());
		assertTrue(blocks(points).contains(List.of(-1_000_000, 0, -1_000_000)), "a corner is drawn");
	}

	@Test
	@DisplayName("a point mode draws one marker on the anchor block, whatever pos2 holds")
	void point_drawsOneMarker() {
		SetupSelection selection = new SetupSelection();
		selection.setMode(SetupMode.PICKUP);
		selection.set(fx.at(4, 70, 4), true);
		selection.set(fx.at(9, 9, 9), false);

		List<Location> points = SetupOutline.points(selection);

		assertEquals(1, points.size());
		assertEquals(List.of(4, 70, 4), List.of(points.get(0).getBlockX(), points.get(0).getBlockY(),
		                                        points.get(0).getBlockZ()));
	}

	@Test
	@DisplayName("only admins holding the wand, with the permission, are shown the outline")
	void onlyAdminsHoldingTheWand_seeIt() {
		Player holder     = admin(true, true);
		Player emptyHand  = admin(false, true);
		Player unpermited = admin(true, false);

		outline.tick();

		assertEquals(20, drawn.get(holder));
		assertFalse(drawn.containsKey(emptyHand));
		assertFalse(drawn.containsKey(unpermited));
	}

	@Test
	@DisplayName("the repeating task is cancelled on the first tick nobody holds the wand, and restarts on demand")
	void taskStops_whenNobodyHoldsTheWand() {
		BukkitTask task = mock(BukkitTask.class);
		when(fx.bukkit.scheduler().runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(task);
		admin(false, true);

		outline.ensureRunning();
		assertTrue(outline.isRunning());
		outline.ensureRunning();
		outline.tick();

		verify(task).cancel();
		assertFalse(outline.isRunning());
		assertTrue(drawn.isEmpty());

		outline.ensureRunning();
		assertTrue(outline.isRunning());
	}
}
