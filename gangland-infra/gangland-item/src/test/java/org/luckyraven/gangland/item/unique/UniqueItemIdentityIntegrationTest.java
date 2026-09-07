package org.luckyraven.gangland.item.unique;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.item.contract.UniqueItemRegistry;
import org.luckyraven.gangland.item.event.PlayerItemInitEvent;
import org.luckyraven.gangland.item.listener.unique.LoadUniqueItem;
import org.luckyraven.gangland.item.support.PerStackNbtAccessor;
import org.luckyraven.keystone.item.nbt.NbtBridge;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test for the unique-item identity cluster IT-01, IT-02 and IT-03, exercising the real
 * collaborators together with nothing about the subject stubbed: {@link UniqueItem#buildItem(Player)} stamps
 * the {@code uniqueItem} NBT tag onto a real {@link ItemStack}, {@link UniqueItemUtil#hasUniqueItem} reads it
 * back, and the real {@link LoadUniqueItem} listener drives the join / downed / respawn flow over a live
 * inventory.
 *
 * <p>The unit suites cover each class in isolation against hand-stubbed stacks. This one closes the gap that
 * let all three bugs ship together: {@code buildItem} rendered {@code &6Phone} to {@code §6Phone} while
 * identity compared against the raw config name, so nothing a player actually held could ever match itself.
 * The scenarios below are the ones players hit — join twice, then go down holding the item.
 *
 * <p>The inventory is a Mockito mock backed by a real {@code ItemStack[]}, so {@code getContents} /
 * {@code getItem} / {@code setItem} behave like a real inventory rather than returning canned values.
 * {@link BukkitRegistryFixture} supplies the {@code ItemFactory} and {@code Registry} statics that
 * {@code buildItem} needs (see {@code documentation/TESTING.md} section 4a); {@link PerStackNbtAccessor}
 * gives each stack its own tags, which testkit's name-keyed {@code RecordingNbtAccessor} cannot.
 */
@DisplayName("Unique-item identity — join, duplicate guard and downed removal end to end")
class UniqueItemIdentityIntegrationTest {

	// The lockpick deliberately sits BEFORE the phone: the inverted removeItem scanned from slot 0 and nulled
	// the first stack that did not match, so with the phone first it would have destroyed the right slot for the
	// wrong reason. With a different unique item ahead of it, the bug destroys the lockpick and spares the phone.
	private static final int LOCKPICK_SLOT = 0;
	private static final int PHONE_SLOT    = 1;

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// buildItem -> ItemBuilder.setDisplayName reaches Bukkit.getItemFactory(); Material.isAir() reaches Registry.
		BukkitRegistryFixture.install();
	}

	private PerStackNbtAccessor accessor;
	private UniqueItemRegistry  registry;
	private LoadUniqueItem      listener;

	private UniqueItem  phone;
	private UniqueItem  lockpick;
	private ItemStack[] contents;
	private Player      player;

	@BeforeEach
	void setUp() {
		accessor = new PerStackNbtAccessor();
		NbtBridge.install(accessor);

		phone = UniqueItem.builder()
				.uniqueItem("phone").material(Material.IRON_INGOT).customModelData(0)
				.name("&6Phone")
				.addOnJoin(true).addOnRespawn(true).dropOnDeath(false).allowDuplicates(false).addToInventory(true)
				.inventorySlot(PHONE_SLOT).overridesSlot(false).droppable(true)
				.build();

		lockpick = UniqueItem.builder()
				.uniqueItem("lockpick").material(Material.IRON_INGOT).customModelData(0)
				.name("&bLockpick")
				.addOnJoin(true).addOnRespawn(false).dropOnDeath(false).allowDuplicates(false).addToInventory(true)
				.inventorySlot(LOCKPICK_SLOT).overridesSlot(false).droppable(false)
				.build();

		Map<String, UniqueItem> items = new LinkedHashMap<>();
		items.put("phone", phone);
		items.put("lockpick", lockpick);

		registry = mock(UniqueItemRegistry.class);
		when(registry.getUniqueItems()).thenReturn(items);

		JavaPlugin plugin = mock(JavaPlugin.class);
		listener = new LoadUniqueItem(registry, plugin);

		contents = new ItemStack[36];
		player   = playerWithBackingInventory(contents);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	@Test
	@DisplayName("IT-03: a stack built by buildItem is recognised as its own unique item")
	void builtItem_isRecognisedAsItself() {
		ItemStack built = phone.buildItem(player);

		assertEquals("phone", UniqueItemUtil.getUniqueItemKey(built), "buildItem must stamp the identity tag");
		assertTrue(phone.matches(built), "a freshly built stack must match the item that built it");
		assertFalse(lockpick.matches(built), "and must not match a different unique item of the same material");
	}

	@Test
	@DisplayName("the first join grants both configured items into their configured slots")
	void firstJoin_grantsBothItems() {
		listener.onJoinGiveItem(new PlayerItemInitEvent(player, false));

		assertTrue(phone.matches(contents[PHONE_SLOT]), "the phone belongs in its configured slot");
		assertTrue(lockpick.matches(contents[LOCKPICK_SLOT]), "the lockpick belongs in its configured slot");
		assertEquals(2, occupiedSlots(), "exactly the two configured items were granted");
	}

	@Test
	@DisplayName("IT-01: a second join grants no duplicates — the duplicate guard fires for items already held")
	void secondJoin_grantsNoDuplicates() {
		listener.onJoinGiveItem(new PlayerItemInitEvent(player, false));
		listener.onJoinGiveItem(new PlayerItemInitEvent(player, false));
		listener.onJoinGiveItem(new PlayerItemInitEvent(player, false));

		assertEquals(2, occupiedSlots(),
		             "unique items multiplied on every join while hasUniqueItem was inverted");
		assertEquals(1, countMatching(phone));
		assertEquals(1, countMatching(lockpick));
	}

	@Test
	@DisplayName("IT-02: going down removes the droppable phone and leaves everything else alone")
	void downed_removesOnlyTheDroppablePhone() {
		listener.onJoinGiveItem(new PlayerItemInitEvent(player, false));

		// An unrelated item the player picked up — before the fix this was the slot that got nulled.
		ItemStack plainDirt = new ItemStack(Material.DIRT);
		contents[5] = plainDirt;

		listener.onPlayerDowned(new org.luckyraven.gangland.core.downed.PlayerDownedEvent(player));

		assertNull(contents[PHONE_SLOT], "the droppable phone is the one item that must be taken");
		assertTrue(lockpick.matches(contents[LOCKPICK_SLOT]), "the non-droppable lockpick must survive");
		assertSame(plainDirt, contents[5], "an unrelated inventory slot must never be destroyed");
	}

	@Test
	@DisplayName("respawn re-grants exactly one phone and does not duplicate the lockpick")
	void respawn_regrantsPhoneOnce() {
		listener.onJoinGiveItem(new PlayerItemInitEvent(player, false));
		listener.onPlayerDowned(new org.luckyraven.gangland.core.downed.PlayerDownedEvent(player));

		assertNull(contents[PHONE_SLOT]);

		listener.onPlayerUndowned(new org.luckyraven.gangland.core.downed.PlayerUndownedEvent(player));

		assertEquals(1, countMatching(phone), "Add_On_Respawn must restore exactly one phone");
		assertEquals(1, countMatching(lockpick), "the lockpick is not Add_On_Respawn and must not be re-granted");
	}

	// -------------------------------------------------------------- fixtures

	private long occupiedSlots() {
		return java.util.Arrays.stream(contents).filter(java.util.Objects::nonNull).count();
	}

	private long countMatching(UniqueItem uniqueItem) {
		return java.util.Arrays.stream(contents).filter(uniqueItem::matches).count();
	}

	private static Player playerWithBackingInventory(ItemStack[] backing) {
		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);

		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getSize()).thenReturn(backing.length);
		when(inventory.getContents()).thenAnswer(invocation -> backing);
		when(inventory.getItem(org.mockito.ArgumentMatchers.anyInt()))
				.thenAnswer(invocation -> backing[invocation.getArgument(0, Integer.class)]);
		org.mockito.Mockito.doAnswer(invocation -> {
			backing[invocation.getArgument(0, Integer.class)] = invocation.getArgument(1, ItemStack.class);
			return null;
		}).when(inventory).setItem(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());

		return player;
	}

}
