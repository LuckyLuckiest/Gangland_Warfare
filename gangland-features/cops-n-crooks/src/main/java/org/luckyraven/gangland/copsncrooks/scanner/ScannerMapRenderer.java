package org.luckyraven.gangland.copsncrooks.scanner;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerMapSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.place.AdminRegion;
import org.luckyraven.gangland.copsncrooks.place.AdminRegionRegistry;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Contextual map renderer: a minimap centred on the holder with blue cop pointers, a red search circle while he is
 * being searched for and green hideout pointers. The projection and the cursor list are pure ({@link #build}); the
 * {@link #render} method only copies a {@link MapSnapshot} onto the canvas.
 *
 * @since 0.20.0
 */
public class ScannerMapRenderer extends MapRenderer {

	/** Canvas side in pixels. */
	public static final int CANVAS = 128;

	/** What a pointer marks. */
	public enum Kind {
		COP, HIDEOUT
	}

	/** One map cursor: {@code x}/{@code y} are cursor units (-128..127, two per pixel), {@code direction} is 0..15. */
	public record Pointer(Kind kind, byte x, byte y, byte direction) {

	}

	/** The cursors and the circle pixels ({@code int[]{x, y}} in 0..127) of one refresh. */
	public record MapSnapshot(List<Pointer> pointers, List<int[]> circlePixels) {

	}

	public ScannerMapRenderer(Supplier<ScannerMapSettings> settings, SearchZoneCache cache, CopManager copManager,
	                          AdminRegionRegistry regions, LongSupplier nowTicks) {
		super(true);
	}

	/** Pointers and circle for a map centred on {@code centre}; locations in another or no world are dropped. */
	public static MapSnapshot build(Location centre, List<Location> cops, SearchZoneCache.Zone zone,
	                                List<Location> hideouts, int blocksPerPixel) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Cursor direction 0..15 for a yaw in degrees (0 = south, 180 = north). */
	public static byte direction(float yaw) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Centre points of the regions that lie in {@code world}. */
	public static List<Location> hideoutCentres(List<AdminRegion> regions, World world) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Locations of the spawned cops; cops without an entity are skipped. */
	public static List<Location> copLocations(List<CopNpc> cops) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The {@code SCANNER_MAP} debug line. */
	public static String debugLine(String player, int cops, boolean circle, int hideouts) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Redirects the throttled debug line (default: the module log). */
	public void onDebug(Consumer<String> sink) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Forgets the throttle and drawn-pixel state of {@code playerId} (quit). */
	public void forget(UUID playerId) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public void render(MapView map, MapCanvas canvas, Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
