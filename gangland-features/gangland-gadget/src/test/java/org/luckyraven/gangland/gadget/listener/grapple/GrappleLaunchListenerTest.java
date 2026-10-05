package org.luckyraven.gangland.gadget.listener.grapple;

import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.gadget.grapple.Grapple;
import org.luckyraven.gangland.gadget.grapple.GrappleKey;
import org.luckyraven.gangland.gadget.grapple.GrappleSession;
import org.luckyraven.gangland.gadget.grapple.GrappleService;
import org.luckyraven.gangland.gadget.grapple.config.GrappleAddon;
import org.luckyraven.gangland.gadget.grapple.message.GrappleMessages;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.testkit.RecordingNbtAccessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the grapple's click mapping onto {@link PlayerFishEvent}: the cast ({@code FISHING}) fires the web-shot when
 * permitted and off cooldown, otherwise no hook is spawned at all; a reel/ground/catch click on the session's own hook
 * lets go (cancelled, so vanilla never fishes or wears the rod) whichever hand holds the rod; a bite or a failed
 * attempt is ignored; non-grapple rods are untouched.
 */
@DisplayName("GrappleLaunchListener — fire on cast, release on the next click")
class GrappleLaunchListenerTest {

	private final GrappleService  grappleService  = mock(GrappleService.class);
	private final GrappleAddon    grappleAddon    = mock(GrappleAddon.class);
	private final GrappleMessages grappleMessages = mock(GrappleMessages.class);
	private final Grapple         grapple         = Grapple.builder().grappleId("test").build();

	private final GrappleLaunchListener listener = new GrappleLaunchListener(grappleService, grappleAddon,
	                                                                         grappleMessages);

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
	}

	@BeforeEach
	void setUp() {
		NbtBridge.install(new RecordingNbtAccessor());
		when(grappleAddon.getGrapple("test")).thenReturn(grapple);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	private static ItemStack grappleItem() {
		ItemStack stack = new ItemStack(Material.FISHING_ROD);
		new ItemBuilder(stack).addTag(GrappleKey.GRAPPLE_ID.getKey(), "test");
		return stack;
	}

	private static Player playerWithMainHand(ItemStack item) {
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(inventory.getItemInMainHand()).thenReturn(item);

		Player player = mock(Player.class);
		when(player.getInventory()).thenReturn(inventory);
		return player;
	}

	private static PlayerFishEvent fishEvent(Player player, PlayerFishEvent.State state, FishHook hook) {
		PlayerFishEvent event = mock(PlayerFishEvent.class);
		when(event.getPlayer()).thenReturn(player);
		when(event.getState()).thenReturn(state);
		when(event.getHook()).thenReturn(hook);
		return event;
	}

	@Test
	@DisplayName("a non-grapple rod leaves the event untouched")
	void nonGrappleItem_untouched() {
		Player          player = playerWithMainHand(new ItemStack(Material.FISHING_ROD));
		PlayerFishEvent event  = fishEvent(player, PlayerFishEvent.State.FISHING, mock(FishHook.class));

		new GrappleLaunchListener(grappleService, mock(GrappleAddon.class), grappleMessages).onPlayerFish(event);

		verify(grappleService, never()).fire(any(), any(), any());
		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("casting without permission sends the denial, spawns no hook and never fires")
	void cast_noPermission_denied() {
		when(grappleMessages.noPermission()).thenReturn("no-permission-message");
		Player player = playerWithMainHand(grappleItem());
		when(player.hasPermission(grapple.getPermission())).thenReturn(false);
		PlayerFishEvent event = fishEvent(player, PlayerFishEvent.State.FISHING, mock(FishHook.class));

		listener.onPlayerFish(event);

		verify(player).sendMessage("no-permission-message");
		verify(event).setCancelled(true);
		verify(grappleService, never()).fire(any(), any(), any());
	}

	@Test
	@DisplayName("casting with permission fires the web-shot with the vanilla hook and lets the cast through")
	void cast_permitted_fires() {
		Player player = playerWithMainHand(grappleItem());
		when(player.hasPermission(grapple.getPermission())).thenReturn(true);
		FishHook        hook  = mock(FishHook.class);
		PlayerFishEvent event = fishEvent(player, PlayerFishEvent.State.FISHING, hook);
		when(grappleService.fire(player, grapple, hook)).thenReturn(true);

		listener.onPlayerFish(event);

		verify(grappleService).fire(player, grapple, hook);
		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("a refused fire (cooldown or session already running) cancels the cast silently")
	void cast_refused_cancelledSilently() {
		Player player = playerWithMainHand(grappleItem());
		when(player.hasPermission(grapple.getPermission())).thenReturn(true);
		PlayerFishEvent event = fishEvent(player, PlayerFishEvent.State.FISHING, mock(FishHook.class));
		when(grappleService.fire(any(), any(), any())).thenReturn(false);

		listener.onPlayerFish(event);

		verify(event).setCancelled(true);
		verify(player, never()).sendMessage(anyString());
	}

	/** A running session whose hook is {@code hook}. */
	private GrappleSession sessionWith(Player player, FishHook hook) {
		GrappleSession session = mock(GrappleSession.class);
		when(session.getHook()).thenReturn(hook);
		when(grappleService.getSession(player)).thenReturn(session);
		return session;
	}

	@Test
	@DisplayName("a reel/ground/catch click on the session's own hook lets go: cancelled, hook removed")
	void secondClick_releases() {
		for (PlayerFishEvent.State state : new PlayerFishEvent.State[]{PlayerFishEvent.State.REEL_IN,
		                                                               PlayerFishEvent.State.IN_GROUND,
		                                                               PlayerFishEvent.State.CAUGHT_FISH,
		                                                               PlayerFishEvent.State.CAUGHT_ENTITY}) {
			Player          player = playerWithMainHand(grappleItem());
			FishHook        hook   = mock(FishHook.class);
			PlayerFishEvent event  = fishEvent(player, state, hook);
			sessionWith(player, hook);

			listener.onPlayerFish(event);

			verify(event).setCancelled(true);
			verify(hook).remove();
			verify(grappleService).cancel(player);
		}
	}

	@Test
	@DisplayName("G10: release resolves by hook identity, so a grapple held in the off hand still lets go")
	void release_offHandGrapple_byHookIdentity() {
		Player          player = playerWithMainHand(new ItemStack(Material.AIR));
		FishHook        hook   = mock(FishHook.class);
		PlayerFishEvent event  = fishEvent(player, PlayerFishEvent.State.REEL_IN, hook);
		sessionWith(player, hook);

		listener.onPlayerFish(event);

		verify(event).setCancelled(true);
		verify(hook).remove();
		verify(grappleService).cancel(player);
	}

	@Test
	@DisplayName("G10: a reel of some other hook (a plain rod) never ends the grapple session")
	void release_otherHook_untouched() {
		Player          player = playerWithMainHand(grappleItem());
		FishHook        hook   = mock(FishHook.class);
		PlayerFishEvent event  = fishEvent(player, PlayerFishEvent.State.REEL_IN, hook);
		sessionWith(player, mock(FishHook.class));

		listener.onPlayerFish(event);

		verify(event, never()).setCancelled(true);
		verify(hook, never()).remove();
		verify(grappleService, never()).cancel(any());
	}

	@Test
	@DisplayName("G10: FAILED_ATTEMPT and BITE on the session's hook are no-ops, not releases")
	void failedAttemptAndBite_ignored() {
		for (PlayerFishEvent.State state : new PlayerFishEvent.State[]{PlayerFishEvent.State.FAILED_ATTEMPT,
		                                                               PlayerFishEvent.State.BITE}) {
			Player          player = playerWithMainHand(grappleItem());
			FishHook        hook   = mock(FishHook.class);
			PlayerFishEvent event  = fishEvent(player, state, hook);
			sessionWith(player, hook);

			listener.onPlayerFish(event);

			verify(event, never()).setCancelled(true);
			verify(hook, never()).remove();
			verify(grappleService, never()).cancel(any());
			verifyNoInteractions(grappleMessages);
		}
	}
}
