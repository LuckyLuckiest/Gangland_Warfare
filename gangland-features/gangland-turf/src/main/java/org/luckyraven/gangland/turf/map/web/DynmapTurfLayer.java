package org.luckyraven.gangland.turf.map.web;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMapSettings;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.color.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Draws turfs as dynmap area markers on a marker set. The ONLY class that names dynmap types; reached only through
 * {@link TurfWebLayers}. Every call runs on the main thread (event handlers and the enable hook).
 */
final class DynmapTurfLayer implements TurfWebLayer {

	private static final String SET_ID     = "gangland_turfs";
	private static final String MARKER_PRE = "gangland_turf_";

	private final Supplier<MarkerAPI> api;
	private final Consumer<Runnable>  nextTick;
	private final TurfManager         turfs;
	private final GangLookupContract  gangs;
	private final TurfMapSettings     settings;
	private       boolean             retried;

	DynmapTurfLayer(Supplier<MarkerAPI> api, Consumer<Runnable> nextTick, TurfManager turfs,
	                GangLookupContract gangs, TurfMapSettings settings) {
		this.api      = api;
		this.nextTick = nextTick;
		this.turfs    = turfs;
		this.gangs    = gangs;
		this.settings = settings;
	}

	static TurfWebLayer create(JavaPlugin plugin, Plugin dynmap, TurfManager turfs, GangLookupContract gangs,
	                           TurfMapSettings settings) {
		if (!(dynmap instanceof DynmapAPI dynmapApi)) {
			return new TurfWebLayers.NoOp();
		}
		return new DynmapTurfLayer(dynmapApi::getMarkerAPI, task -> Bukkit.getScheduler().runTask(plugin, task),
		                           turfs, gangs, settings);
	}

	@Override
	public void rebuild() {
		MarkerSet set = markerSet();
		if (set == null) {
			if (!retried) {
				retried = true;
				nextTick.accept(this::rebuild);
			}
			return;
		}
		retried = false;
		List<String> live = new ArrayList<>();
		for (Turf turf : turfs.getAll()) {
			live.add(MARKER_PRE + turf.getId());
			draw(set, turf);
		}
		var existing = set.getAreaMarkers();
		if (existing != null) {
			for (AreaMarker marker : new ArrayList<>(existing)) {
				if (!live.contains(marker.getMarkerID())) {
					marker.deleteMarker();
				}
			}
		}
	}

	@Override
	public void sync(Turf turf) {
		MarkerSet set = markerSet();
		if (set != null) {
			draw(set, turf);
		}
	}

	@Override
	public void remove(int turfId) {
		MarkerSet set = markerSet();
		AreaMarker marker = set == null ? null : set.findAreaMarker(MARKER_PRE + turfId);
		if (marker != null) {
			marker.deleteMarker();
		}
	}

	@Override
	public void shutdown() {
		MarkerAPI markers = api.get();
		MarkerSet set = markers == null ? null : markers.getMarkerSet(SET_ID);
		if (set != null) {
			set.deleteMarkerSet();
		}
	}

	private @Nullable MarkerSet markerSet() {
		MarkerAPI markers = api.get();
		if (markers == null) {
			return null;
		}
		MarkerSet set = markers.getMarkerSet(SET_ID);
		if (set == null) {
			set = markers.createMarkerSet(SET_ID, settings.getWebLayerLabel(), null, false);
		}
		return set;
	}

	private void draw(MarkerSet set, Turf turf) {
		CuboidRegion region = turf.getRegion();
		String id = MARKER_PRE + turf.getId();
		AreaMarker marker = set.findAreaMarker(id);
		double[] x = {region.getMinX(), region.getMaxX() + 1};
		double[] z = {region.getMinZ(), region.getMaxZ() + 1};
		if (marker == null) {
			marker = set.createAreaMarker(id, turf.getDisplayName(), false, region.getWorld(), x, z, false);
			if (marker == null) {
				return; // dynmap does not know this world
			}
		} else {
			marker.setCornerLocations(x, z);
			marker.setLabel(plain(turf.getDisplayName()), false);
		}
		Gang owner = turf.getOwnerGangId() == null ? null : gangs.findById(turf.getOwnerGangId());
		int rgb = rgbOf(owner);
		marker.setFillStyle(settings.getWebFillOpacity(), rgb);
		marker.setLineStyle(settings.getWebLineWeight(), settings.getWebLineOpacity(), rgb);
		marker.setDescription(escape(plain(turf.getDisplayName())) + "<br>Owner: "
				                      + (owner == null ? "Unclaimed" : escape(plain(owner.getDisplayNameString()))));
	}

	private int rgbOf(@Nullable Gang owner) {
		if (owner == null || owner.getColor() == null) {
			return settings.getWebUnclaimedRgb();
		}
		try {
			return Color.valueOf(owner.getColor()).getBukkitColor().asRGB();
		} catch (RuntimeException exception) {
			return settings.getWebUnclaimedRgb();
		}
	}

	private static String plain(String text) {
		return org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', text));
	}

	private static String escape(String text) {
		return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
