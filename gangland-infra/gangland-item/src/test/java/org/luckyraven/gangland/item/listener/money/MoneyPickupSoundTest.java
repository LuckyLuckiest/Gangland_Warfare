package org.luckyraven.gangland.item.listener.money;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.gangland.item.money.MoneyItem;
import org.luckyraven.gangland.item.money.MoneyItemUtil;
import org.luckyraven.gangland.item.support.PerStackNbtAccessor;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.sound.SoundEffect;

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterisation of the pickup sound that follows a successful cash deposit, through all three places that play it:
 * {@link MoneyPickupListener}, {@link MoneyInteractListener} and {@link MoneyProximityPickupTask}. Each skips the sound
 * silently for a stack with no variation id, for a variation the addon does not know and for a variation without a
 * pickup sound; otherwise it plays the configured {@link SoundEffect} to the depositing player exactly once.
 */
@DisplayName("Money pickup sound - the three deposit paths play the variation's sound the same way")
class MoneyPickupSoundTest {

	private static final String VARIATION  = "small";
	private static final String SOUND_NAME = "gangland:test.pickup";

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
	}

	private MoneyAddon          addon;
	private MoneyDepositService deposits;
	private Player             player;
	private Location           location;
	private SoundEffect        sound;
	private MoneyItem          variation;

	@BeforeEach
	void setUp() {
		NbtBridge.install(new PerStackNbtAccessor());

		// SoundEffect is a final record Mockito cannot instrument; a CUSTOM one reaches Player#playSound(Location, String, ..)
		sound     = new SoundEffect(SoundEffect.SoundType.CUSTOM, SOUND_NAME, 0.7f, 1.3f);
		variation = mock(MoneyItem.class);
		when(variation.getPickupSound()).thenReturn(new MoneyItem.PickupSound(sound));

		addon = mock(MoneyAddon.class);
		when(addon.isEnabled()).thenReturn(true);
		when(addon.getVariation(VARIATION)).thenReturn(variation);

		deposits = mock(MoneyDepositService.class);
		when(deposits.depositPickup(any(), anyDouble(), any())).thenReturn(true);

		player   = mock(Player.class);
		location = mock(Location.class);
		when(player.getLocation()).thenReturn(location);
		when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	/** A cash stack worth 100; {@code variationId} {@code null} leaves the variation tag off. */
	private ItemStack cash(String variationId) {
		ItemBuilder builder = new ItemBuilder(new ItemStack(Material.PAPER));
		builder.addTag(MoneyItemUtil.MARKER_TAG, (byte) 1);
		if (variationId != null) builder.addTag(MoneyItemUtil.VARIATION_TAG, variationId);
		builder.addTag(MoneyItemUtil.AMOUNT_TAG, 100);
		return builder.build();
	}

	private void viaPickupListener(ItemStack stack) {
		Item entity = mock(Item.class);
		when(entity.getItemStack()).thenReturn(stack);
		EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
		when(event.getEntity()).thenReturn(player);
		when(event.getItem()).thenReturn(entity);
		new MoneyPickupListener(addon, deposits).onMoneyPickup(event);
	}

	private void viaInteractListener(ItemStack stack) {
		PlayerInteractEvent event = mock(PlayerInteractEvent.class);
		when(event.getHand()).thenReturn(EquipmentSlot.HAND);
		when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
		when(event.getItem()).thenReturn(stack);
		when(event.getPlayer()).thenReturn(player);
		new MoneyInteractListener(addon, deposits).onMoneyInteract(event);
	}

	private void viaProximityTask(ItemStack stack) {
		Item entity = mock(Item.class);
		when(entity.isValid()).thenReturn(true);
		when(entity.isDead()).thenReturn(false);
		when(entity.getItemStack()).thenReturn(stack);
		when(player.getNearbyEntities(1.5, 1.5, 1.5)).thenReturn(List.of(entity));
		new MoneyProximityPickupTask(addon, deposits).scanAndDeposit(player);
	}

	private void plays(Consumer<ItemStack> path) {
		path.accept(cash(VARIATION));

		verify(player, times(1)).playSound(location, SOUND_NAME, 0.7f, 1.3f);
		verify(addon, times(1)).getVariation(VARIATION);
	}

	private void silentWithoutVariationTag(Consumer<ItemStack> path) {
		path.accept(cash(null));

		verify(addon, never()).getVariation(any());
		verify(player, never()).playSound(any(Location.class), anyString(), anyFloat(), anyFloat());
	}

	private void silentForUnknownVariation(Consumer<ItemStack> path) {
		path.accept(cash("vanished"));

		verify(addon, times(1)).getVariation("vanished");
		verify(player, never()).playSound(any(Location.class), anyString(), anyFloat(), anyFloat());
	}

	private void silentWithoutPickupSound(Consumer<ItemStack> path) {
		when(variation.getPickupSound()).thenReturn(null);

		path.accept(cash(VARIATION));

		verify(addon, times(1)).getVariation(VARIATION);
		verify(player, never()).playSound(any(Location.class), anyString(), anyFloat(), anyFloat());
	}

	// ---------------------------------------------------------------- MoneyPickupListener

	@Test
	@DisplayName("pickup listener: plays the variation's sound to the player once")
	void pickupListener_plays() {
		plays(this::viaPickupListener);
		verify(deposits).depositPickup(eq(player), eq(100.0), eq(VARIATION));
	}

	@Test
	@DisplayName("pickup listener: no variation tag, no lookup and no sound")
	void pickupListener_noVariationTag() {
		silentWithoutVariationTag(this::viaPickupListener);
		verify(deposits).depositPickup(eq(player), eq(100.0), eq(null));
	}

	@Test
	@DisplayName("pickup listener: unknown variation, no sound")
	void pickupListener_unknownVariation() {
		silentForUnknownVariation(this::viaPickupListener);
	}

	@Test
	@DisplayName("pickup listener: variation without a pickup sound, no sound")
	void pickupListener_noPickupSound() {
		silentWithoutPickupSound(this::viaPickupListener);
	}

	// ---------------------------------------------------------------- MoneyInteractListener

	@Test
	@DisplayName("interact listener: plays the variation's sound to the player once")
	void interactListener_plays() {
		plays(this::viaInteractListener);
		verify(deposits).deposit(player, 100, VARIATION);
	}

	@Test
	@DisplayName("interact listener: no variation tag, no lookup and no sound")
	void interactListener_noVariationTag() {
		silentWithoutVariationTag(this::viaInteractListener);
		verify(deposits).deposit(player, 100, null);
	}

	@Test
	@DisplayName("interact listener: unknown variation, no sound")
	void interactListener_unknownVariation() {
		silentForUnknownVariation(this::viaInteractListener);
	}

	@Test
	@DisplayName("interact listener: variation without a pickup sound, no sound")
	void interactListener_noPickupSound() {
		silentWithoutPickupSound(this::viaInteractListener);
	}

	// ---------------------------------------------------------------- MoneyProximityPickupTask

	@Test
	@DisplayName("proximity task: plays the variation's sound to the player once")
	void proximityTask_plays() {
		plays(this::viaProximityTask);
		verify(deposits).depositPickup(eq(player), eq(100.0), eq(VARIATION));
	}

	@Test
	@DisplayName("proximity task: no variation tag, no lookup and no sound")
	void proximityTask_noVariationTag() {
		silentWithoutVariationTag(this::viaProximityTask);
		verify(deposits).depositPickup(eq(player), eq(100.0), eq(null));
	}

	@Test
	@DisplayName("proximity task: unknown variation, no sound")
	void proximityTask_unknownVariation() {
		silentForUnknownVariation(this::viaProximityTask);
	}

	@Test
	@DisplayName("proximity task: variation without a pickup sound, no sound")
	void proximityTask_noPickupSound() {
		silentWithoutPickupSound(this::viaProximityTask);
	}

}
