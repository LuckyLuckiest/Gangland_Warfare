package org.luckyraven.gangland.lootchest;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.ItemNbtAccessor;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.item.nbt.NbtType;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins docket LS-30: the loot-chest admin wand must resolve which item to edit from the exact hotbar slot its
 * config menu was opened from ({@code wandSlot}, captured once by {@link LootChestWand#openConfigInventory} and
 * threaded through every nested write), never from whatever the player currently has selected. Before the fix, the
 * write path re-resolved its target via {@code getItemInMainHand()} on every save, so a player who put the wand
 * away and picked up something else after opening the config menu had their edit silently misdirected onto — or
 * away from — the wrong item.
 *
 * <p>Every write in {@link LootChestWand} funnels through the private {@code setWandNBT}, which calls the real
 * {@code de.tr7zw.nbtapi.NBT.modify(...)} directly (not through Keystone's {@link ItemBuilder}/{@code NbtBridge}
 * seam) — confirmed experimentally to need a live NMS server; it cannot run in this unit test, and {@code
 * mockStatic(NBT.class)} also fails here (Byte Buddy cannot retransform the class without {@code
 * com.mojang.authlib.GameProfile}, which isn't a dependency of this module). So this test pins the part of the fix
 * that <em>is</em> reachable without touching NBT-API: {@link LootChestWand#updateWandLore}'s target-resolution
 * guard, which every write (including the {@code setWandNBT} call this test never reaches) sits behind. The slot
 * that opened the config session no longer holds a wand (simulating the player having put it away), while the
 * player's current main hand — a different slot — now holds a perfectly valid one; the fixed code must resolve its
 * target from {@code getItem(wandSlot)} alone and bail out, never falling back to {@code getItemInMainHand()}.
 */
@DisplayName("LootChestWand — LS-30: target resolution stays pinned to the captured wandSlot")
class LootChestWandTest {

	private static final int WAND_SLOT         = 3;
	private static final int CURRENT_HELD_SLOT = 7;

	// Not needed for the fix's own early-return path (it bails out before any ItemMeta touch), but keeps a
	// reverted-code red run from dying on an unrelated Bukkit.getItemFactory() NPE instead of the actual
	// getItemInMainHand()/setItem(wandSlot) interaction assertions below.
	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
	}

	@BeforeEach
	void setUp() {
		NbtBridge.install(new PerStackNbtAccessor());
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	@Test
	@DisplayName("updateWandLore resolves its target from the captured wandSlot, never from the current main hand")
	void updateWandLore_resolvesTargetFromCapturedWandSlot_notFromCurrentMainHand() throws Exception {
		LootChestWand wand = new LootChestWand(mock(JavaPlugin.class), mock(LootChestManager.class), "glw");

		// wandSlot (where the config menu was opened) no longer holds a wand — the player put it away.
		ItemStack notAWand = new ItemStack(Material.DIRT);
		// Whatever the player currently has selected IS a valid wand — the buggy pre-fix code would target this.
		ItemStack wandInMainHand = taggedWandItem();

		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getItem(WAND_SLOT)).thenReturn(notAWand);
		when(inventory.getHeldItemSlot()).thenReturn(CURRENT_HELD_SLOT);
		when(inventory.getItemInMainHand()).thenReturn(wandInMainHand);

		invokeUpdateWandLore(wand, player, WAND_SLOT);

		// The fixed code resolves its target purely from getItem(wandSlot); since that slot no longer holds a
		// wand it must bail out immediately and never consult — let alone write to — the current main hand.
		verify(inventory).getItem(WAND_SLOT);
		verify(inventory, never()).getItemInMainHand();
		verify(inventory, never()).setItemInMainHand(any());
		verify(inventory, never()).setItem(eq(WAND_SLOT), any());
	}

	private static void invokeUpdateWandLore(LootChestWand wand, Player player, int wandSlot) throws Exception {
		Method method = LootChestWand.class.getDeclaredMethod("updateWandLore", Player.class, int.class);
		method.setAccessible(true);
		method.invoke(wand, player, wandSlot);
	}

	/** Same tag shape {@link LootChestWand#createWand()} stamps, built by hand to dodge {@code XMaterial}. */
	private static ItemStack taggedWandItem() {
		ItemStack stack = new ItemStack(Material.STICK);
		new ItemBuilder(stack).addTag(LootChestWandTag.WAND_KEY.toString(), true)
		                      .addTag(LootChestWandTag.CONFIGURED.toString(), false)
		                      .addTag(LootChestWandTag.LOOT_TABLE_ID.toString(), "")
		                      .addTag(LootChestWandTag.TIER_ID.toString(), "")
		                      .addTag(LootChestWandTag.RESPAWN_TIME.toString(), 300L)
		                      .addTag(LootChestWandTag.INVENTORY_SIZE.toString(), 27)
		                      .addTag(LootChestWandTag.DISPLAY_NAME.toString(), "&eLoot Chest");
		return stack;
	}

	/**
	 * Minimal {@link ItemNbtAccessor} double keyed by stack <em>identity</em>, unlike {@code keystone-testkit}'s
	 * {@code RecordingNbtAccessor}, which keys by tag name alone and so cannot keep {@code notAWand} and {@code
	 * wandInMainHand}'s tag state apart — this test needs two independently-built stacks to carry independent tag
	 * state at once. See {@code ItemDefinitionSimilarityTest}'s javadoc for the same finding.
	 */
	private static final class PerStackNbtAccessor implements ItemNbtAccessor {

		private final Map<ItemStack, Map<String, Object>> storage = new IdentityHashMap<>();

		private Map<String, Object> tagsOf(ItemStack stack) {
			return storage.computeIfAbsent(stack, s -> new HashMap<>());
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public boolean has(ItemStack stack, String tag) {
			return tagsOf(stack).containsKey(tag);
		}

		@Override
		@Nullable
		public Object get(ItemStack stack, String tag) {
			return tagsOf(stack).get(tag);
		}

		@Override
		@Nullable
		public String getString(ItemStack stack, String tag) {
			Object value = tagsOf(stack).get(tag);
			return value == null ? null : String.valueOf(value);
		}

		@Override
		public int getInt(ItemStack stack, String tag) {
			Object value = tagsOf(stack).get(tag);
			return value instanceof Number number ? number.intValue() : 0;
		}

		@Override
		public void set(ItemStack stack, String tag, NbtType type, @Nullable Object value) {
			tagsOf(stack).put(tag, value);
		}

		@Override
		public void remove(ItemStack stack, String tag) {
			tagsOf(stack).remove(tag);
		}

		@Override
		@Nullable
		public String describe(ItemStack stack) {
			return "recorded" + tagsOf(stack);
		}
	}

}
