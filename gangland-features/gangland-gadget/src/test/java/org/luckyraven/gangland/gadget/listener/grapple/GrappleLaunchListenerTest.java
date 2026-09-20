package org.luckyraven.gangland.gadget.listener.grapple;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.gadget.grapple.Grapple;
import org.luckyraven.gangland.gadget.grapple.GrappleKey;
import org.luckyraven.gangland.gadget.grapple.GrappleService;
import org.luckyraven.gangland.gadget.grapple.config.GrappleAddon;
import org.luckyraven.gangland.gadget.grapple.message.GrappleMessages;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.testkit.RecordingNbtAccessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins WS8 G2+G3 (+ fix round 1): {@link GrappleLaunchListener} only reacts to a grapple item's fishing hook,
 * denies a launch the player lacks permission for, refuses one outside {@code Max_Distance}, the world border or a
 * blocked line of sight, and starts the pull on a permitted, in-range, unobstructed landed hook.
 */
@DisplayName("GrappleLaunchListener — grapple launch off PlayerFishEvent (WS8 G2+G3)")
class GrappleLaunchListenerTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
	}

	@BeforeEach
	void setUp() {
		NbtBridge.install(new RecordingNbtAccessor());
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	private static ItemStack grappleItem(String id) {
		ItemStack stack = new ItemStack(Material.FISHING_ROD);
		new ItemBuilder(stack).addTag(GrappleKey.GRAPPLE_ID.getKey(), id);
		return stack;
	}

	private static PlayerFishEvent fishEvent(Player player, PlayerFishEvent.State state) {
		PlayerFishEvent event = mock(PlayerFishEvent.class);
		when(event.getPlayer()).thenReturn(player);
		when(event.getState()).thenReturn(state);
		return event;
	}

	private static Player playerWithMainHand(ItemStack item) {
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(inventory.getItemInMainHand()).thenReturn(item);

		Player player = mock(Player.class);
		when(player.getInventory()).thenReturn(inventory);
		return player;
	}

	/**
	 * A mocked World whose WorldBorder#isInside always answers {@code insideBorder}, wired for {@code
	 * GrappleLaunchListener#isWithinWorldBorder}.
	 */
	private static World worldWithBorder(boolean insideBorder) {
		World       world       = mock(World.class);
		WorldBorder worldBorder = mock(WorldBorder.class);
		when(world.getWorldBorder()).thenReturn(worldBorder);
		when(worldBorder.isInside(any(Location.class))).thenReturn(insideBorder);
		return world;
	}

	@Test
	@DisplayName("a non-grapple item in hand leaves the event untouched")
	void onPlayerFish_nonGrappleItem_untouched() {
		GrappleService  grappleService  = mock(GrappleService.class);
		GrappleAddon    grappleAddon    = mock(GrappleAddon.class);
		GrappleMessages grappleMessages = mock(GrappleMessages.class);

		Player player = playerWithMainHand(new ItemStack(Material.FISHING_ROD));
		PlayerFishEvent event = fishEvent(player, PlayerFishEvent.State.IN_GROUND);

		new GrappleLaunchListener(grappleService, grappleAddon, grappleMessages).onPlayerFish(event);

		verifyNoInteractions(grappleAddon);
		verify(grappleService, never()).start(any(), any(), any());
		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("IN_GROUND with no permission sends the denial message and never starts a pull")
	void onPlayerFish_inGroundNoPermission_sendsMessageNoStart() {
		GrappleService  grappleService  = mock(GrappleService.class);
		GrappleAddon    grappleAddon    = mock(GrappleAddon.class);
		GrappleMessages grappleMessages = mock(GrappleMessages.class);

		Grapple grapple = Grapple.builder().grappleId("test").build();
		when(grappleAddon.getGrapple("test")).thenReturn(grapple);
		when(grappleMessages.noPermission()).thenReturn("no-permission-message");

		Player player = playerWithMainHand(grappleItem("test"));
		when(player.hasPermission(grapple.getPermission())).thenReturn(false);

		FishHook hook = mock(FishHook.class);
		when(hook.getLocation()).thenReturn(new Location(null, 1, 2, 3));

		PlayerFishEvent event = fishEvent(player, PlayerFishEvent.State.IN_GROUND);
		when(event.getHook()).thenReturn(hook);

		new GrappleLaunchListener(grappleService, grappleAddon, grappleMessages).onPlayerFish(event);

		verify(player).sendMessage("no-permission-message");
		verify(grappleService, never()).start(any(), any(), any());
	}

	@Test
	@DisplayName("IN_GROUND with permission, inside the border and clear LOS starts the pull (WS8 G3 checks wired in)")
	void onPlayerFish_inGroundHasPermission_startsPull() {
		GrappleService  grappleService  = mock(GrappleService.class);
		GrappleAddon    grappleAddon    = mock(GrappleAddon.class);
		GrappleMessages grappleMessages = mock(GrappleMessages.class);

		// requireLineOfSight(true) deliberately, unlike the earlier G2 tests: this re-proves the happy path with
		// BOTH new G3 checks (world-border + LOS) actually exercised, not just short-circuited by the Lombok
		// @Builder boolean default (false) that a lower-effort choice here would have hidden behind.
		// maxDistance(25) (fix round 1, F1): the Lombok @Builder default for an int is 0, which would make every
		// one of these tests fail the new Max_Distance gate at a real (non-zero) distance unless set explicitly.
		Grapple grapple = Grapple.builder().grappleId("test").requireLineOfSight(true).maxDistance(25).build();
		when(grappleAddon.getGrapple("test")).thenReturn(grapple);

		World world = worldWithBorder(true);
		when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble())).thenReturn(null);

		Player player = playerWithMainHand(grappleItem("test"));
		when(player.hasPermission(grapple.getPermission())).thenReturn(true);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, 0, 0, 0));
		when(player.getEyeLocation()).thenReturn(new Location(world, 0, 0, 0));

		Location anchor = new Location(world, 10, 0, 0);
		FishHook hook = mock(FishHook.class);
		when(hook.getLocation()).thenReturn(anchor);

		PlayerFishEvent event = fishEvent(player, PlayerFishEvent.State.IN_GROUND);
		when(event.getHook()).thenReturn(hook);

		new GrappleLaunchListener(grappleService, grappleAddon, grappleMessages).onPlayerFish(event);

		verify(grappleService).start(player, grapple, anchor);
	}

	@Test
	@DisplayName("blocked LOS (a RayTraceResult hit) refuses the launch and sends Grapple_Blocked")
	void onPlayerFish_inGroundHasPermission_blockedLos_refusesLaunch() {
		GrappleService  grappleService  = mock(GrappleService.class);
		GrappleAddon    grappleAddon    = mock(GrappleAddon.class);
		GrappleMessages grappleMessages = mock(GrappleMessages.class);
		when(grappleMessages.blocked()).thenReturn("blocked-message");

		Grapple grapple = Grapple.builder().grappleId("test").requireLineOfSight(true).maxDistance(25).build();
		when(grappleAddon.getGrapple("test")).thenReturn(grapple);

		World          world  = worldWithBorder(true);   // inside the border — LOS is the only thing failing here
		RayTraceResult hit    = mock(RayTraceResult.class);
		when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble())).thenReturn(hit);

		Player player = playerWithMainHand(grappleItem("test"));
		when(player.hasPermission(grapple.getPermission())).thenReturn(true);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, 0, 0, 0));
		when(player.getEyeLocation()).thenReturn(new Location(world, 0, 0, 0));

		Location anchor = new Location(world, 10, 0, 0);
		FishHook hook = mock(FishHook.class);
		when(hook.getLocation()).thenReturn(anchor);

		PlayerFishEvent event = fishEvent(player, PlayerFishEvent.State.IN_GROUND);
		when(event.getHook()).thenReturn(hook);

		new GrappleLaunchListener(grappleService, grappleAddon, grappleMessages).onPlayerFish(event);

		verify(grappleService, never()).start(any(), any(), any());
		verify(player).sendMessage("blocked-message");
	}

	@Test
	@DisplayName("outside the world border refuses the launch and sends Grapple_Blocked, regardless of LOS")
	void onPlayerFish_inGroundHasPermission_outsideBorder_refusesLaunch() {
		GrappleService  grappleService  = mock(GrappleService.class);
		GrappleAddon    grappleAddon    = mock(GrappleAddon.class);
		GrappleMessages grappleMessages = mock(GrappleMessages.class);
		when(grappleMessages.blocked()).thenReturn("blocked-message");

		// requireLineOfSight left at its (false) default — the border check must refuse the launch on its own,
		// before LOS is ever consulted.
		Grapple grapple = Grapple.builder().grappleId("test").maxDistance(25).build();
		when(grappleAddon.getGrapple("test")).thenReturn(grapple);

		World world = worldWithBorder(false);

		Player player = playerWithMainHand(grappleItem("test"));
		when(player.hasPermission(grapple.getPermission())).thenReturn(true);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, 0, 0, 0));

		Location anchor = new Location(world, 10, 0, 0);
		FishHook hook = mock(FishHook.class);
		when(hook.getLocation()).thenReturn(anchor);

		PlayerFishEvent event = fishEvent(player, PlayerFishEvent.State.IN_GROUND);
		when(event.getHook()).thenReturn(hook);

		new GrappleLaunchListener(grappleService, grappleAddon, grappleMessages).onPlayerFish(event);

		verify(grappleService, never()).start(any(), any(), any());
		verify(player).sendMessage("blocked-message");
	}

	@Test
	@DisplayName("F1: an anchor at Max_Distance + 1 is refused and sends Grapple_Blocked")
	void onPlayerFish_inGroundHasPermission_beyondMaxDistance_refusesLaunch() {
		GrappleService  grappleService  = mock(GrappleService.class);
		GrappleAddon    grappleAddon    = mock(GrappleAddon.class);
		GrappleMessages grappleMessages = mock(GrappleMessages.class);
		when(grappleMessages.blocked()).thenReturn("blocked-message");

		Grapple grapple = Grapple.builder().grappleId("test").maxDistance(25).build();
		when(grappleAddon.getGrapple("test")).thenReturn(grapple);

		// Inside the border, LOS not required — isolates the Max_Distance gate specifically.
		World world = worldWithBorder(true);

		Player player = playerWithMainHand(grappleItem("test"));
		when(player.hasPermission(grapple.getPermission())).thenReturn(true);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, 0, 0, 0));

		Location anchor = new Location(world, 26, 0, 0);   // Max_Distance (25) + 1
		FishHook hook = mock(FishHook.class);
		when(hook.getLocation()).thenReturn(anchor);

		PlayerFishEvent event = fishEvent(player, PlayerFishEvent.State.IN_GROUND);
		when(event.getHook()).thenReturn(hook);

		new GrappleLaunchListener(grappleService, grappleAddon, grappleMessages).onPlayerFish(event);

		verify(grappleService, never()).start(any(), any(), any());
		verify(player).sendMessage("blocked-message");
	}

	@Test
	@DisplayName("F1: an anchor at Max_Distance - 1 is accepted")
	void onPlayerFish_inGroundHasPermission_withinMaxDistance_startsPull() {
		GrappleService  grappleService  = mock(GrappleService.class);
		GrappleAddon    grappleAddon    = mock(GrappleAddon.class);
		GrappleMessages grappleMessages = mock(GrappleMessages.class);

		Grapple grapple = Grapple.builder().grappleId("test").maxDistance(25).build();
		when(grappleAddon.getGrapple("test")).thenReturn(grapple);

		World world = worldWithBorder(true);

		Player player = playerWithMainHand(grappleItem("test"));
		when(player.hasPermission(grapple.getPermission())).thenReturn(true);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, 0, 0, 0));

		Location anchor = new Location(world, 24, 0, 0);   // Max_Distance (25) - 1
		FishHook hook = mock(FishHook.class);
		when(hook.getLocation()).thenReturn(anchor);

		PlayerFishEvent event = fishEvent(player, PlayerFishEvent.State.IN_GROUND);
		when(event.getHook()).thenReturn(hook);

		new GrappleLaunchListener(grappleService, grappleAddon, grappleMessages).onPlayerFish(event);

		verify(grappleService).start(player, grapple, anchor);
	}

	@Test
	@DisplayName("CAUGHT_FISH/CAUGHT_ENTITY on a grapple item cancels the event")
	void onPlayerFish_caughtFishOrEntity_cancelsEvent() {
		GrappleService  grappleService  = mock(GrappleService.class);
		GrappleAddon    grappleAddon    = mock(GrappleAddon.class);
		GrappleMessages grappleMessages = mock(GrappleMessages.class);

		Grapple grapple = Grapple.builder().grappleId("test").build();
		when(grappleAddon.getGrapple("test")).thenReturn(grapple);

		Player player = playerWithMainHand(grappleItem("test"));

		GrappleLaunchListener listener = new GrappleLaunchListener(grappleService, grappleAddon, grappleMessages);

		PlayerFishEvent caughtFish = fishEvent(player, PlayerFishEvent.State.CAUGHT_FISH);
		listener.onPlayerFish(caughtFish);
		verify(caughtFish).setCancelled(true);

		PlayerFishEvent caughtEntity = fishEvent(player, PlayerFishEvent.State.CAUGHT_ENTITY);
		listener.onPlayerFish(caughtEntity);
		verify(caughtEntity).setCancelled(true);

		verify(grappleService, never()).start(any(), any(), any());
	}
}
