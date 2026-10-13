package org.luckyraven.gangland.copsncrooks.scanner;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerMapSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.place.AdminRegion;
import org.luckyraven.gangland.copsncrooks.place.AdminRegionRegistry;
import org.luckyraven.gangland.copsncrooks.scanner.ScannerMapRenderer.Kind;
import org.luckyraven.gangland.copsncrooks.scanner.ScannerMapRenderer.MapSnapshot;
import org.luckyraven.gangland.copsncrooks.scanner.ScannerMapRenderer.Pointer;
import org.luckyraven.gangland.copsncrooks.support.FakeRepository;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lane scanmap: {@link ScannerMapRenderer} (P-MAP-1). The projection and the cursor/circle snapshot are pure and tested
 * directly; {@code render} is tested against a mock canvas.
 */
@DisplayName("ScannerMapRenderer")
class ScannerMapRendererTest {

	private World  world;
	private World  otherWorld;
	private Location centre;

	@BeforeEach
	void setUp() {
		world      = mock(World.class);
		otherWorld = mock(World.class);
		centre     = new Location(world, 0, 64, 0);
	}

	private Location at(double dx, double dz) {
		return new Location(world, dx, 64, dz);
	}

	private static SearchZoneCache.Zone zone(EvasionState state, Location at, double radius) {
		return new SearchZoneCache.Zone(state, at, radius);
	}

	private MapSnapshot snapshot(List<Location> cops, SearchZoneCache.Zone zone, List<Location> hideouts, int bpp) {
		return ScannerMapRenderer.build(centre, cops, zone, hideouts, bpp);
	}

	// ----------------------------------------------------------------------------------------------- projection

	@Test
	@DisplayName("a cop 32 blocks east at 1 block per pixel is a blue pointer at cursor x 64")
	void copEastOfPlayerProjectsRight() {
		MapSnapshot snap = snapshot(List.of(at(32, 0)), null, List.of(), 1);

		assertEquals(1, snap.pointers().size());
		Pointer pointer = snap.pointers().get(0);
		assertEquals(Kind.COP, pointer.kind());
		assertEquals(64, pointer.x());
		assertEquals(0, pointer.y());
	}

	@Test
	@DisplayName("a cop 16 blocks south projects down (cursor y 32), 16 north up")
	void copSouthProjectsDown() {
		MapSnapshot snap = snapshot(List.of(at(0, 16), at(0, -16)), null, List.of(), 1);

		assertEquals(32, snap.pointers().get(0).y());
		assertEquals(-32, snap.pointers().get(1).y());
	}

	@Test
	@DisplayName("a cop 100 blocks east at 1 block per pixel is off the map and dropped")
	void offMapCopDropped() {
		MapSnapshot snap = snapshot(List.of(at(100, 0)), null, List.of(), 1);

		assertTrue(snap.pointers().isEmpty());
	}

	@Test
	@DisplayName("Blocks_Per_Pixel 2 halves the offset, so +100 blocks fits at cursor 100")
	void blocksPerPixelScalesOffset() {
		MapSnapshot snap = snapshot(List.of(at(32, 0), at(100, 0)), null, List.of(), 2);

		assertEquals(2, snap.pointers().size());
		assertEquals(32, snap.pointers().get(0).x());
		assertEquals(100, snap.pointers().get(1).x());
	}

	@Test
	@DisplayName("the map edge is inclusive on the west (-128) and exclusive on the east (128)")
	void edgesOfTheCursorRange() {
		MapSnapshot snap = snapshot(List.of(at(63, 0), at(64, 0), at(-64, 0), at(-65, 0)), null, List.of(), 1);

		assertEquals(2, snap.pointers().size());
		assertEquals(126, snap.pointers().get(0).x());
		assertEquals(-128, snap.pointers().get(1).x());
	}

	@Test
	@DisplayName("a cop in another world, or in no world, is dropped")
	void otherWorldAndNullWorldDropped() {
		MapSnapshot snap = snapshot(List.of(new Location(otherWorld, 5, 64, 5), new Location(null, 5, 64, 5)), null,
		                            List.of(), 1);

		assertTrue(snap.pointers().isEmpty());
	}

	@Test
	@DisplayName("direction maps yaw to 0..15 sixteenths (0 south, 90 west, 180 north, -90 east)")
	void yawToDirection() {
		assertEquals(0, ScannerMapRenderer.direction(0f));
		assertEquals(4, ScannerMapRenderer.direction(90f));
		assertEquals(8, ScannerMapRenderer.direction(180f));
		assertEquals(12, ScannerMapRenderer.direction(-90f));
		assertEquals(0, ScannerMapRenderer.direction(360f));
	}

	@Test
	@DisplayName("a cop's yaw becomes the pointer direction")
	void pointerCarriesCopYaw() {
		MapSnapshot snap = snapshot(List.of(new Location(world, 8, 64, 0, 180f, 0f)), null, List.of(), 1);

		assertEquals(8, snap.pointers().get(0).direction());
	}

	// ------------------------------------------------------------------------------------------------- circle

	@Test
	@DisplayName("a SEEN zone draws no circle; a SEARCHING zone does")
	void circleOnlyWhileSearching() {
		assertTrue(snapshot(List.of(), zone(EvasionState.SEEN, centre, 30), List.of(), 1).circlePixels().isEmpty());
		assertTrue(snapshot(List.of(), zone(EvasionState.EVADED, centre, 30), List.of(), 1).circlePixels().isEmpty());
		assertFalse(snapshot(List.of(), zone(EvasionState.SEARCHING, centre, 30), List.of(), 1).circlePixels().isEmpty());
	}

	@Test
	@DisplayName("a radius-30 circle sits 30 pixels from the holder-centred pixel (64, 64)")
	void circlePixelsAtRadius() {
		List<int[]> pixels = snapshot(List.of(), zone(EvasionState.SEARCHING, centre, 30), List.of(), 1).circlePixels();

		for (int[] pixel : pixels) {
			double distance = Math.hypot(pixel[0] - 64, pixel[1] - 64);
			assertTrue(Math.abs(distance - 30) <= 0.75, "pixel " + pixel[0] + "," + pixel[1] + " is " + distance);
		}
		assertTrue(contains(pixels, 94, 64));
		assertTrue(contains(pixels, 34, 64));
		assertTrue(contains(pixels, 64, 94));
		assertTrue(contains(pixels, 64, 34));
	}

	@Test
	@DisplayName("the circle follows its own centre, not the holder: centre 10 east puts the east edge at pixel 104")
	void circleCentreOffsetFromHolder() {
		List<int[]> pixels = snapshot(List.of(), zone(EvasionState.SEARCHING, at(10, 0), 30), List.of(), 1).circlePixels();

		assertTrue(contains(pixels, 104, 64));
		assertTrue(contains(pixels, 44, 64));
	}

	@Test
	@DisplayName("Blocks_Per_Pixel 2 halves the circle radius in pixels")
	void circleScalesWithBlocksPerPixel() {
		List<int[]> pixels = snapshot(List.of(), zone(EvasionState.SEARCHING, centre, 30), List.of(), 2).circlePixels();

		assertTrue(contains(pixels, 79, 64));
		assertFalse(contains(pixels, 94, 64));
	}

	@Test
	@DisplayName("circle pixels outside the 128x128 canvas are clipped, never emitted")
	void circleClippedToCanvas() {
		List<int[]> pixels = snapshot(List.of(), zone(EvasionState.SEARCHING, centre, 70), List.of(), 1).circlePixels();

		assertFalse(pixels.isEmpty());
		for (int[] pixel : pixels) {
			assertTrue(pixel[0] >= 0 && pixel[0] < 128 && pixel[1] >= 0 && pixel[1] < 128);
		}
	}

	@Test
	@DisplayName("no circle for a zone without a centre, in another world, or with no radius")
	void noCircleWithoutUsableZone() {
		assertTrue(snapshot(List.of(), zone(EvasionState.SEARCHING, null, 30), List.of(), 1).circlePixels().isEmpty());
		assertTrue(snapshot(List.of(), zone(EvasionState.SEARCHING, new Location(otherWorld, 0, 64, 0), 30), List.of(),
		                    1).circlePixels().isEmpty());
		assertTrue(snapshot(List.of(), zone(EvasionState.SEARCHING, centre, 0), List.of(), 1).circlePixels().isEmpty());
		assertTrue(snapshot(List.of(), null, List.of(), 1).circlePixels().isEmpty());
	}

	private static boolean contains(List<int[]> pixels, int x, int y) {
		for (int[] pixel : pixels) {
			if (pixel[0] == x && pixel[1] == y) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------ hideouts

	@Test
	@DisplayName("hideouts are green pointers at the region centre")
	void hideoutsArePointers() {
		MapSnapshot snap = snapshot(List.of(), null, List.of(at(20, -10)), 1);

		assertEquals(1, snap.pointers().size());
		assertEquals(Kind.HIDEOUT, snap.pointers().get(0).kind());
		assertEquals(40, snap.pointers().get(0).x());
		assertEquals(-20, snap.pointers().get(0).y());
	}

	@Test
	@DisplayName("hideoutCentres keeps only the regions of the given world and returns each cuboid's centre")
	void hideoutCentresFiltersByWorld() {
		when(world.getName()).thenReturn("world");
		AdminRegion here  = new AdminRegion(1, "a", "world", 10, 60, 10, 20, 70, 30, java.util.Set.of("hideout"));
		AdminRegion there = new AdminRegion(2, "b", "nether", 10, 60, 10, 20, 70, 30, java.util.Set.of("hideout"));

		List<Location> centres = ScannerMapRenderer.hideoutCentres(List.of(here, there), world);

		assertEquals(1, centres.size());
		assertEquals(15.5, centres.get(0).getX(), 0.51);
		assertEquals(20.5, centres.get(0).getZ(), 0.51);
		assertEquals(world, centres.get(0).getWorld());
	}

	@Test
	@DisplayName("copLocations skips a cop with no spawned entity")
	void copLocationsSkipsUnspawned() {
		CopNpc spawned   = mock(CopNpc.class);
		CopNpc unspawned = mock(CopNpc.class);
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getLocation()).thenReturn(at(3, 4));
		when(spawned.getEntity()).thenReturn(entity);
		when(unspawned.getEntity()).thenReturn(null);

		List<Location> locations = ScannerMapRenderer.copLocations(List.of(spawned, unspawned));

		assertEquals(1, locations.size());
		assertEquals(3, locations.get(0).getX());
	}

	@Test
	@DisplayName("debugLine is 'SCANNER_MAP <player> cops=<n> circle=<bool> hideouts=<n>'")
	void debugLineFormat() {
		assertEquals("SCANNER_MAP Lucky cops=2 circle=true hideouts=1", ScannerMapRenderer.debugLine("Lucky", 2, true, 1));
	}

	// -------------------------------------------------------------------------------------------------- render

	private final AtomicReference<ScannerMapSettings> settings = new AtomicReference<>(ScannerMapSettings.DEFAULT);
	private final AtomicLong                          ticks    = new AtomicLong();

	private SearchZoneCache       cache;
	private CopManager            copManager;
	private AdminRegionRegistry   regions;
	private ScannerMapRenderer    renderer;
	private Player                player;
	private MapCanvas             canvas;
	private final UUID            playerId = UUID.randomUUID();
	private final List<String>    debug    = new ArrayList<>();

	private void renderFixture() {
		cache      = new SearchZoneCache();
		copManager = mock(CopManager.class);
		regions    = org.mockito.Mockito.spy(new AdminRegionRegistry(new FakeRepository<>()));
		renderer   = new ScannerMapRenderer(settings::get, cache, copManager, regions, ticks::get);
		renderer.onDebug(debug::add);
		player = mock(Player.class);
		canvas = mock(MapCanvas.class);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getName()).thenReturn("Lucky");
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(centre);
		when(world.getName()).thenReturn("world");
		when(canvas.getCursors()).thenReturn(new MapCursorCollection());
	}

	private CopNpc copAt(double dx, double dz) {
		CopNpc cop    = mock(CopNpc.class);
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getLocation()).thenReturn(at(dx, dz));
		when(cop.getEntity()).thenReturn(entity);
		return cop;
	}

	private void searching(double radius) {
		cache.record(new WantedEvasionStateEvent(player, EvasionState.SEARCHING, 3, 20, centre, radius));
	}

	private MapCursorCollection cursors() {
		ArgumentCaptor<MapCursorCollection> captor = ArgumentCaptor.forClass(MapCursorCollection.class);
		verify(canvas, org.mockito.Mockito.atLeastOnce()).setCursors(captor.capture());
		return captor.getValue();
	}

	@Test
	@DisplayName("render draws one blue pointer per squad cop")
	void renderDrawsBlueCops() {
		renderFixture();
		List<CopNpc> squad = List.of(copAt(10, 0), copAt(-10, 0));
		when(copManager.getCopsForPlayer(playerId)).thenReturn(squad);

		renderer.render(mock(MapView.class), canvas, player);

		MapCursorCollection cursors = cursors();
		assertEquals(2, cursors.size());
		assertEquals(MapCursor.Type.BLUE_POINTER, cursors.getCursor(0).getType());
	}

	@Test
	@DisplayName("render draws green pointers for admin regions tagged hideout when Show_Hideouts is on")
	void hideoutsGreenWhenEnabled() {
		renderFixture();
		regions.create("den", new Location(world, 10, 60, 10), new Location(world, 20, 70, 20), "hideout");
		when(copManager.getCopsForPlayer(playerId)).thenReturn(List.of());

		renderer.render(mock(MapView.class), canvas, player);

		MapCursorCollection cursors = cursors();
		assertEquals(1, cursors.size());
		assertEquals(MapCursor.Type.GREEN_POINTER, cursors.getCursor(0).getType());
	}

	@Test
	@DisplayName("Show_Hideouts false draws no hideout pointers and never reads the regions")
	void hideoutsHiddenWhenDisabled() {
		renderFixture();
		settings.set(new ScannerMapSettings(true, 1, 10, false, "n", List.of()));
		regions.create("den", new Location(world, 10, 60, 10), new Location(world, 20, 70, 20), "hideout");
		when(copManager.getCopsForPlayer(playerId)).thenReturn(List.of());

		renderer.render(mock(MapView.class), canvas, player);

		assertEquals(0, cursors().size());
		verify(regions, never()).withTag(any());
	}

	@Test
	@DisplayName("a SEARCHING zone paints red circle pixels on the canvas")
	void renderPaintsCircleWhileSearching() {
		renderFixture();
		when(copManager.getCopsForPlayer(playerId)).thenReturn(List.of());
		searching(30);

		renderer.render(mock(MapView.class), canvas, player);

		verify(canvas).setPixel(org.mockito.ArgumentMatchers.eq(94), org.mockito.ArgumentMatchers.eq(64), anyByte());
	}

	@Test
	@DisplayName("when the search ends the old circle pixels are erased on the next refresh")
	void clearsPreviousCircle() {
		renderFixture();
		when(copManager.getCopsForPlayer(playerId)).thenReturn(List.of());
		searching(30);
		renderer.render(mock(MapView.class), canvas, player);

		cache.record(new WantedEvasionStateEvent(player, EvasionState.OFF, 0, 0, null, 0));
		ticks.set(10);
		renderer.render(mock(MapView.class), canvas, player);

		verify(canvas).setPixel(94, 64, (byte) 0);
	}

	@Test
	@DisplayName("Enable false draws nothing and reads no cops")
	void disabledDrawsNothing() {
		renderFixture();
		settings.set(new ScannerMapSettings(false, 1, 10, true, "n", List.of()));

		renderer.render(mock(MapView.class), canvas, player);

		verify(canvas, never()).setCursors(any());
		verify(canvas, never()).setPixel(anyInt(), anyInt(), anyByte());
		verify(copManager, never()).getCopsForPlayer(any());
	}

	@Test
	@DisplayName("two renders inside Refresh_Ticks build one snapshot; one after it builds a second")
	void refreshThrottled() {
		renderFixture();
		when(copManager.getCopsForPlayer(playerId)).thenReturn(List.of());

		renderer.render(mock(MapView.class), canvas, player);
		ticks.set(5);
		renderer.render(mock(MapView.class), canvas, player);
		verify(copManager, times(1)).getCopsForPlayer(playerId);

		ticks.set(10);
		renderer.render(mock(MapView.class), canvas, player);
		verify(copManager, times(2)).getCopsForPlayer(playerId);
	}

	@Test
	@DisplayName("forget resets the throttle, so the next render builds again at once")
	void forgetResetsThrottle() {
		renderFixture();
		when(copManager.getCopsForPlayer(playerId)).thenReturn(List.of());
		renderer.render(mock(MapView.class), canvas, player);

		renderer.forget(playerId);
		renderer.render(mock(MapView.class), canvas, player);

		verify(copManager, times(2)).getCopsForPlayer(playerId);
	}

	@Test
	@DisplayName("a holder in no world is skipped without touching cops or canvas")
	void nullWorldHolderSkipped() {
		renderFixture();
		when(player.getWorld()).thenReturn(null);
		when(player.getLocation()).thenReturn(new Location(null, 0, 64, 0));

		renderer.render(mock(MapView.class), canvas, player);

		verify(copManager, never()).getCopsForPlayer(any());
		verify(canvas, never()).setCursors(any());
	}

	@Test
	@DisplayName("the SCANNER_MAP debug line is throttled to one per player per 5 seconds (100 ticks)")
	void debugThrottled() {
		renderFixture();
		List<CopNpc> squad = List.of(copAt(5, 5));
		when(copManager.getCopsForPlayer(playerId)).thenReturn(squad);

		renderer.render(mock(MapView.class), canvas, player);
		ticks.set(20);
		renderer.render(mock(MapView.class), canvas, player);
		assertEquals(List.of("SCANNER_MAP Lucky cops=1 circle=false hideouts=0"), debug);

		ticks.set(100);
		renderer.render(mock(MapView.class), canvas, player);
		assertEquals(2, debug.size());
	}
}
