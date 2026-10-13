package org.luckyraven.gangland.copsncrooks.scanner;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerMapSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.place.AdminRegion;
import org.luckyraven.gangland.copsncrooks.place.AdminRegionRegistry;
import org.luckyraven.gangland.events.wanted.EvasionState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
		List<Pointer> pointers = new ArrayList<>();
		for (Location cop : cops) {
			addPointer(pointers, Kind.COP, centre, cop, direction(cop.getYaw()), blocksPerPixel);
		}
		for (Location hideout : hideouts) {
			addPointer(pointers, Kind.HIDEOUT, centre, hideout, (byte) 0, blocksPerPixel);
		}
		return new MapSnapshot(pointers, circlePixels(centre, zone, blocksPerPixel));
	}

	private static void addPointer(List<Pointer> out, Kind kind, Location centre, Location at, byte direction,
	                               int blocksPerPixel) {
		if (at.getWorld() == null || !at.getWorld().equals(centre.getWorld())) {
			return;
		}
		// Cursor units are two per pixel; the canvas is centred on the holder.
		long x = Math.round((at.getX() - centre.getX()) * 2 / blocksPerPixel);
		long y = Math.round((at.getZ() - centre.getZ()) * 2 / blocksPerPixel);
		if (x < -128 || x > 127 || y < -128 || y > 127) {
			return;
		}
		out.add(new Pointer(kind, (byte) x, (byte) y, direction));
	}

	/** Pixels of the search circle; empty unless the zone is SEARCHING with a centre, same world and radius. */
	private static List<int[]> circlePixels(Location centre, SearchZoneCache.Zone zone, int blocksPerPixel) {
		List<int[]> pixels = new ArrayList<>();
		if (zone == null || zone.state() != EvasionState.SEARCHING || zone.centre() == null
		    || zone.radius() <= 0 || !Objects.equals(zone.centre().getWorld(), centre.getWorld())) {
			return pixels;
		}
		double cx     = CANVAS / 2.0 + (zone.centre().getX() - centre.getX()) / blocksPerPixel;
		double cy     = CANVAS / 2.0 + (zone.centre().getZ() - centre.getZ()) / blocksPerPixel;
		double radius = zone.radius() / blocksPerPixel;
		for (int py = 0; py < CANVAS; py++) {
			for (int px = 0; px < CANVAS; px++) {
				if (Math.abs(Math.hypot(px - cx, py - cy) - radius) <= 0.5) {
					pixels.add(new int[]{px, py});
				}
			}
		}
		return pixels;
	}

	/** Cursor direction 0..15 for a yaw in degrees (0 = south, 180 = north). */
	public static byte direction(float yaw) {
		double normalised = yaw % 360.0;
		if (normalised < 0) {
			normalised += 360.0;
		}
		return (byte) (((int) Math.floor(normalised / 22.5 + 0.5)) & 15);
	}

	/** Centre points of the regions that lie in {@code world}. */
	public static List<Location> hideoutCentres(List<AdminRegion> regions, World world) {
		List<Location> centres = new ArrayList<>();
		if (world == null) {
			return centres;
		}
		for (AdminRegion region : regions) {
			if (!world.getName().equals(region.getWorld())) {
				continue;
			}
			centres.add(new Location(world, (region.getMinX() + region.getMaxX() + 1) / 2.0,
			                         (region.getMinY() + region.getMaxY() + 1) / 2.0,
			                         (region.getMinZ() + region.getMaxZ() + 1) / 2.0));
		}
		return centres;
	}

	/** Locations of the spawned cops; cops without an entity are skipped. */
	public static List<Location> copLocations(List<CopNpc> cops) {
		List<Location> locations = new ArrayList<>();
		for (CopNpc cop : cops) {
			LivingEntity entity = cop.getEntity();
			if (entity != null) {
				locations.add(entity.getLocation());
			}
		}
		return locations;
	}

	/** The {@code SCANNER_MAP} debug line. */
	public static String debugLine(String player, int cops, boolean circle, int hideouts) {
		return "SCANNER_MAP " + player + " cops=" + cops + " circle=" + circle + " hideouts=" + hideouts;
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
