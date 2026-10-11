package org.luckyraven.gangland.turf.map.web;

import org.bukkit.plugin.Plugin;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.config.TurfMapSettings;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.InMemoryTurfRepository;
import org.luckyraven.gangland.turf.support.InMemoryTurfSequence;
import org.luckyraven.keystone.color.Color;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynmapTurfLayerTest {

	private static final int UNCLAIMED_RGB = 0xAAAAAA;

	private MarkerAPI       api;
	private MarkerSet       set;
	private TurfManager     turfs;
	private FakeGangLookup  gangs;
	private DynmapTurfLayer layer;
	private List<Runnable>  queued;

	@BeforeEach
	void setUp() {
		api = mock(MarkerAPI.class);
		set = mock(MarkerSet.class);
		when(api.createMarkerSet(anyString(), anyString(), any(), anyBoolean())).thenReturn(set);

		TurfMapSettings settings = mock(TurfMapSettings.class);
		when(settings.getWebLayerLabel()).thenReturn("Turfs");
		when(settings.getWebFillOpacity()).thenReturn(0.25);
		when(settings.getWebLineOpacity()).thenReturn(0.8);
		when(settings.getWebLineWeight()).thenReturn(2);
		when(settings.getWebUnclaimedRgb()).thenReturn(UNCLAIMED_RGB);

		turfs  = new TurfManager(new InMemoryTurfRepository(), new InMemoryTurfSequence());
		gangs  = new FakeGangLookup();
		queued = new ArrayList<>();
		layer  = new DynmapTurfLayer(() -> api, queued::add, turfs, gangs, settings);
	}

	private Turf turf(int id, String name, Integer owner) {
		Turf turf = new Turf(id, name, new CuboidRegion("world", 0, 0, 15, 15), owner, BigDecimal.ZERO, 0L, 0L);
		turfs.create(turf);
		return turf;
	}

	private void gang(int id, String color, String name) {
		Gang gang = CaptureFixtures.gang(id);
		when(gang.getColor()).thenReturn(color);
		when(gang.getDisplayNameString()).thenReturn(name);
		gangs.register(gang);
	}

	private AreaMarker marker(String id) {
		AreaMarker marker = mock(AreaMarker.class);
		when(set.createAreaMarker(eq(id), anyString(), anyBoolean(), anyString(), any(double[].class),
		                          any(double[].class), anyBoolean())).thenReturn(marker);
		return marker;
	}

	@Test
	@DisplayName("rebuild creates one area marker per turf with the region corners")
	void rebuild_oneMarkerPerTurf() {
		gang(1, "RED", "Reds");
		turf(1, "Docks", 1);
		turf(2, "Alley", null);
		AreaMarker m1 = marker("gangland_turf_1");
		AreaMarker m2 = marker("gangland_turf_2");

		layer.rebuild();

		verify(set).createAreaMarker(eq("gangland_turf_1"), eq("Docks"), eq(false), eq("world"),
		                             eq(new double[]{0, 16}), eq(new double[]{0, 16}), eq(false));
		verify(set).createAreaMarker(eq("gangland_turf_2"), eq("Alley"), eq(false), eq("world"),
		                             any(double[].class), any(double[].class), eq(false));
		verify(m1).setFillStyle(0.25, Color.RED.getBukkitColor().asRGB());
		verify(m1).setLineStyle(2, 0.8, Color.RED.getBukkitColor().asRGB());
		verify(m1).setDescription(contains("Reds"));
		verify(m2).setFillStyle(0.25, UNCLAIMED_RGB);
		verify(m2).setDescription(contains("Unclaimed"));
	}

	@Test
	@DisplayName("an owner change recolours the existing marker and rewrites the description")
	void ownerChange_recolours() {
		gang(1, "RED", "Reds");
		gang(2, "BLUE", "Blues");
		Turf turf = turf(1, "Docks", 1);
		AreaMarker marker = mock(AreaMarker.class);
		when(set.findAreaMarker("gangland_turf_1")).thenReturn(marker);

		turf.setOwnerGangId(2);
		layer.sync(turf);

		verify(set, never()).createAreaMarker(anyString(), anyString(), anyBoolean(), anyString(),
		                                      any(double[].class), any(double[].class), anyBoolean());
		verify(marker).setFillStyle(0.25, Color.BLUE.getBukkitColor().asRGB());
		verify(marker).setDescription(contains("Blues"));
	}

	@Test
	@DisplayName("remove deletes the marker of a deleted turf")
	void delete_removesMarker() {
		AreaMarker marker = mock(AreaMarker.class);
		when(set.findAreaMarker("gangland_turf_7")).thenReturn(marker);

		layer.remove(7);

		verify(marker).deleteMarker();
	}

	@Test
	@DisplayName("an unknown or null colour name falls back to the unclaimed colour")
	void unknownColour_usesUnclaimed() {
		gang(1, "NOT_A_COLOUR", "Odd");
		gang(2, null, "Nameless");
		turf(1, "A", 1);
		turf(2, "B", 2);
		AreaMarker m1 = marker("gangland_turf_1");
		AreaMarker m2 = marker("gangland_turf_2");

		layer.rebuild();

		verify(m1).setFillStyle(0.25, UNCLAIMED_RGB);
		verify(m2).setFillStyle(0.25, UNCLAIMED_RGB);
	}

	@Test
	@DisplayName("rebuild drops markers of turfs that no longer exist")
	void rebuild_dropsStaleMarkers() {
		turf(1, "A", null);
		marker("gangland_turf_1");
		AreaMarker stale = mock(AreaMarker.class);
		when(stale.getMarkerID()).thenReturn("gangland_turf_9");
		when(set.getAreaMarkers()).thenReturn(Set.of(stale));

		layer.rebuild();

		verify(stale).deleteMarker();
	}

	@Test
	@DisplayName("dynmap API not ready: one retry is queued for the next tick and it never loops")
	void apiNotReady_retriesOnce() {
		layer = new DynmapTurfLayer(() -> null, queued::add, turfs, gangs, mock(TurfMapSettings.class));

		layer.rebuild();
		assertEquals(1, queued.size());
		queued.get(0).run();

		assertEquals(1, queued.size(), "a second failure must not queue again");
		verify(api, never()).createMarkerSet(anyString(), anyString(), any(), anyBoolean());
	}

	@Test
	@DisplayName("shutdown deletes the marker set")
	void shutdown_deletesSet() {
		layer.rebuild();
		when(api.getMarkerSet("gangland_turfs")).thenReturn(set);
		layer.shutdown();

		verify(set, times(1)).deleteMarkerSet();
	}

	@Test
	@DisplayName("dynmap absent or Web.Enabled false: the factory hands back a no-op layer")
	void dynmapAbsent_noOpAndNoTypeLoad() {
		TurfMapSettings settings = mock(TurfMapSettings.class);
		when(settings.isWebEnabled()).thenReturn(true);

		TurfWebLayer absent = TurfWebLayers.create(null, turfs, gangs, settings, null);
		absent.rebuild();
		absent.sync(null);
		absent.remove(1);
		absent.shutdown();
		assertInstanceOf(TurfWebLayers.NoOp.class, absent);

		when(settings.isWebEnabled()).thenReturn(false);
		assertInstanceOf(TurfWebLayers.NoOp.class,
		                 TurfWebLayers.create(null, turfs, gangs, settings, mock(Plugin.class)));
	}
}
