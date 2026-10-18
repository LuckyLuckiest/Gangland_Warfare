package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lane bag: {@link BagSpill}. A spilled bag drops whole dollars as hot money items (P-BAG-7, 8); the cents remainder is
 * dropped (D-W2-16).
 */
@DisplayName("BagSpill - the hot bills of a spilled bag")
class BagSpillTest {

	@TempDir
	Path tempDir;

	private final List<Integer> made = new ArrayList<>();
	private       BagSpill      spill;
	private final List<ItemMeta> metas = new ArrayList<>();

	@BeforeEach
	void setUp() {
		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		spill = new BagSpill(plugin, dollars -> {
			made.add(dollars);
			ItemStack stack = mock(ItemStack.class);
			ItemMeta  meta  = mock(ItemMeta.class);
			when(meta.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
			when(stack.getItemMeta()).thenReturn(meta);
			metas.add(meta);
			return stack;
		});
	}

	@Test
	@DisplayName("1003.75 over 4 stacks is 250, 250, 250, 253 (whole dollars, the last takes the remainder)")
	void splitsWholeDollarsIntoStacks() {
		assertEquals(List.of(250, 250, 250, 253), BagSpill.split(new BigDecimal("1003.75"), 4));
	}

	@Test
	@DisplayName("a bag smaller than the stack count still sums to its whole dollars")
	void tinyBagStillSumsToTheDollars() {
		List<Integer> parts = BagSpill.split(new BigDecimal("3"), 4);

		assertEquals(3, parts.stream().mapToInt(Integer::intValue).sum());
		assertTrue(parts.stream().allMatch(p -> p >= 0));
	}

	@Test
	@DisplayName("under one dollar there is nothing to drop")
	void underOneDollarSplitsToNothing() {
		assertTrue(BagSpill.split(new BigDecimal("0.99"), 4).stream().mapToInt(Integer::intValue).sum() == 0);
	}

	@Test
	@DisplayName("every built stack is made for its share and tagged hot")
	void builtStacksAreTaggedHot() {
		List<ItemStack> stacks = spill.build(new BigDecimal("1003.75"), 4);

		assertEquals(4, stacks.size());
		assertEquals(List.of(250, 250, 250, 253), made);
		for (ItemMeta meta : metas) {
			verify(meta.getPersistentDataContainer()).set(any(), any(), any());
		}
	}

	@Test
	@DisplayName("isHot reads the tag: false for an item without meta or without the key")
	void isHotReadsTheTag() {
		ItemStack plain = mock(ItemStack.class);
		when(plain.getItemMeta()).thenReturn(null);

		assertFalse(spill.isHot(plain));

		ItemStack untagged = mock(ItemStack.class);
		ItemMeta  meta     = mock(ItemMeta.class);
		when(meta.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
		when(untagged.getItemMeta()).thenReturn(meta);

		assertFalse(spill.isHot(untagged));
	}

	@Test
	@DisplayName("an unknown Spill_Variation falls back to the first variation, a known one is used")
	void unknownVariationUsesFirst() {
		Map<String, String> byId = new LinkedHashMap<>();
		byId.put("small", "S");
		byId.put("medium", "M");

		assertEquals("S", BagSpill.pickVariation("nope", byId));
		assertEquals("M", BagSpill.pickVariation("medium", byId));
	}

	@Test
	@DisplayName("a null world drops nothing and does not throw")
	void nullWorldDropsNothing() {
		assertEquals(0, spill.drop(new Location(null, 1, 2, 3), new BigDecimal("1000"), 4));
		assertTrue(made.isEmpty());
	}

	@Test
	@DisplayName("a world gets one dropItemNaturally per stack")
	void dropsEveryStack() {
		World    world = mock(World.class);
		Location at    = new Location(world, 1, 2, 3);

		assertEquals(4, spill.drop(at, new BigDecimal("1000"), 4));

		verify(world, org.mockito.Mockito.times(4)).dropItemNaturally(any(Location.class), any(ItemStack.class));
	}

	@Test
	@DisplayName("nothing is dropped for a sub-dollar bag")
	void subDollarBagDropsNothing() {
		World world = mock(World.class);

		assertEquals(0, spill.drop(new Location(world, 1, 2, 3), new BigDecimal("0.50"), 4));

		verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
	}
}
