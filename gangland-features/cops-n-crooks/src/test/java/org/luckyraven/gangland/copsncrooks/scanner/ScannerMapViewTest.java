package org.luckyraven.gangland.copsncrooks.scanner;

import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lane scanmap: {@link ScannerMapView} creates one view per world and attaches the renderer to whichever view a held
 * item points at, so a map from before a restart works again (P-MAP-2).
 */
@DisplayName("ScannerMapView")
class ScannerMapViewTest {

	private ScannerMapRenderer renderer;
	private World              world;
	private int                created;
	private ScannerMapView     view;
	private final List<MapView> madeViews = new ArrayList<>();

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		renderer = mock(ScannerMapRenderer.class);
		world    = mock(World.class);
		Function<World, MapView> factory = w -> {
			created++;
			MapView made = fakeView();
			madeViews.add(made);
			return made;
		};
		view = new ScannerMapView(renderer, factory);
	}

	/** A MapView whose renderer list is real, with a stock vanilla renderer pre-installed. */
	private static MapView fakeView() {
		MapView           mapView   = mock(MapView.class);
		List<MapRenderer> renderers = new ArrayList<>();
		renderers.add(mock(MapRenderer.class));
		when(mapView.getRenderers()).thenReturn(renderers);
		doAnswer(call -> renderers.add(call.getArgument(0))).when(mapView).addRenderer(any());
		doAnswer(call -> renderers.remove(call.<MapRenderer>getArgument(0))).when(mapView).removeRenderer(any());
		return mapView;
	}

	private ItemStack mapItem(ItemMeta meta) {
		ItemStack item = mock(ItemStack.class);
		when(item.getItemMeta()).thenReturn(meta);
		return item;
	}

	@Test
	@DisplayName("attach leaves our renderer as the only renderer and turns position tracking off")
	void attachReplacesRenderers() {
		MapView mapView = fakeView();

		view.attach(mapView);

		assertEquals(List.of(renderer), mapView.getRenderers());
		verify(mapView).setTrackingPosition(false);
		verify(mapView).setUnlimitedTracking(false);
	}

	@Test
	@DisplayName("attach twice still leaves our renderer exactly once")
	void attachIsIdempotent() {
		MapView mapView = fakeView();

		view.attach(mapView);
		view.attach(mapView);

		assertEquals(List.of(renderer), mapView.getRenderers());
	}

	@Test
	@DisplayName("viewFor creates a view once per world and reuses it")
	void viewForCachesPerWorld() {
		MapView first  = view.viewFor(world);
		MapView second = view.viewFor(world);
		view.viewFor(mock(World.class));

		assertSame(first, second);
		assertEquals(2, created);
		assertEquals(List.of(renderer), first.getRenderers());
	}

	@Test
	@DisplayName("ensure binds a new view to a map item that has none")
	void ensureBindsNewView() {
		MapMeta meta = mock(MapMeta.class);
		when(meta.hasMapView()).thenReturn(false);
		ItemStack item = mapItem(meta);

		assertTrue(view.ensure(item, world));

		verify(meta).setMapView(madeViews.get(0));
		verify(item).setItemMeta(meta);
	}

	@Test
	@DisplayName("a map from before a restart (view with the vanilla renderer) gets our renderer re-attached, once")
	void holdingMapReattachesRenderer() {
		MapView old = fakeView();
		MapMeta meta = mock(MapMeta.class);
		when(meta.hasMapView()).thenReturn(true);
		when(meta.getMapView()).thenReturn(old);
		ItemStack item = mapItem(meta);

		assertTrue(view.ensure(item, world));
		assertTrue(view.ensure(item, world));

		assertEquals(List.of(renderer), old.getRenderers());
		assertEquals(0, created);
		verify(meta, never()).setMapView(any());
	}

	@Test
	@DisplayName("a map whose saved view is gone (deleted map file) is rebound to a fresh view")
	void missingViewRebound() {
		MapMeta meta = mock(MapMeta.class);
		when(meta.hasMapView()).thenReturn(true);
		when(meta.getMapView()).thenReturn(null);
		ItemStack item = mapItem(meta);

		assertTrue(view.ensure(item, world));

		verify(meta).setMapView(madeViews.get(0));
	}

	@Test
	@DisplayName("an item that is not a map is left alone")
	void nonMapIgnored() {
		ItemStack item = mapItem(null);
		assertFalse(view.ensure(item, world));

		ItemStack plain = mapItem(mock(ItemMeta.class));
		assertFalse(view.ensure(plain, world));

		assertEquals(0, created);
		verify(plain, never()).setItemMeta(any());
	}

	@Test
	@DisplayName("ensure passes the renderer's forget through for a quitting player")
	void forgetDelegatesToRenderer() {
		java.util.UUID id = java.util.UUID.randomUUID();

		view.forget(id);

		verify(renderer, times(1)).forget(id);
	}
}
