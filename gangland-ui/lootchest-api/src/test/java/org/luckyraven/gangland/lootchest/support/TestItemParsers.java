package org.luckyraven.gangland.lootchest.support;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.item.ItemConverterRegistry;
import org.luckyraven.gangland.item.ItemParser;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Builds a minimal, real {@link ItemParser} for loot-chest tests. Registers only a "material"
 * converter (the same fallback path {@code ItemParser.getConverter} uses for a bare
 * {@code Material} name), so a plain string like {@code "STONE"} or {@code "DIAMOND_SWORD"}
 * resolves through the exact production code path, while anything unregistered (e.g.
 * {@code "totally_bogus_type"}) deterministically fails to parse — useful for pinning the
 * "every entry fails to parse" branches of {@code LootTable#generateLoot}.
 *
 * <p>{@code LootTable#createItemFromReference} calls {@code ItemStack#getMaxStackSize()}, which on
 * this Spigot API version (1.21.11) resolves through a live server registry
 * ({@code Material#asItemType()} -&gt; {@code Registry.ITEM}) that does not exist in a plain unit
 * test — and cannot be faked with plain Mockito either, because {@code ItemType}'s own static
 * initializer rejects a mocked instance of itself. Rather than stand up a fake server, every
 * {@code ItemStack} handed back here is a {@code Mockito.spy} of a real instance with only
 * {@code getMaxStackSize()} stubbed from {@link #maxStackSizeOf(Material)} — every other method
 * (type, amount, equality) runs the genuine Bukkit code.
 */
public final class TestItemParsers {

	private TestItemParsers() { }

	public static ItemParser materialOnly() {
		ItemConverterRegistry registry = new ItemConverterRegistry();

		registry.register("material", (type, modifier, attributes) -> {
			try {
				Material material = Material.valueOf(type);
				ItemStack item = spy(new ItemStack(material));
				// doReturn(...).when(spy) — NOT when(spy.getMaxStackSize()) — because the latter would
				// call the real method during stubbing itself, hitting the same unavailable registry.
				doReturn(maxStackSizeOf(material)).when(item).getMaxStackSize();
				return item;
			} catch (IllegalArgumentException exception) {
				return null;
			}
		});

		return new ItemParser(registry);
	}

	/**
	 * Test-only stand-in for Bukkit's real per-material stack cap (normally resolved through the
	 * live server registry — see the class javadoc). Extend as new materials are needed by tests;
	 * everything not listed defaults to the common 64 stack.
	 */
	private static int maxStackSizeOf(Material material) {
		return switch (material) {
			case DIAMOND_SWORD -> 1;
			default -> 64;
		};
	}

}
