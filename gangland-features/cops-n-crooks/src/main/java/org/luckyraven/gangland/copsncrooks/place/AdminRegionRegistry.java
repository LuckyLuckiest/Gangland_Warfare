package org.luckyraven.gangland.copsncrooks.place;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.data.region.PlaceRegion;
import org.luckyraven.gangland.data.region.RegionProvider;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/**
 * The admin regions, in memory and in the {@code cop_region} table, published to {@code PlaceNames} as the
 * {@code copsncrooks} {@link RegionProvider}. Create and remove persist at once.
 */
public final class AdminRegionRegistry implements BeanLifecycle, RegionProvider {

	public static final String SOURCE = "copsncrooks";

	private final IRepository<AdminRegion>      repository;
	private final TreeMap<Integer, AdminRegion> regions = new TreeMap<>();

	public AdminRegionRegistry(IRepository<AdminRegion> repository) {
		this.repository = repository;

		repository.setDataSupplier(regions::values);
	}

	/**
	 * Adds the cuboid between two corners (both in one world) carrying {@code tag}.
	 *
	 * @throws IllegalArgumentException when a corner has no world or the corners are in different worlds
	 */
	public AdminRegion create(String name, Location corner1, Location corner2, String tag) {
		if (corner1.getWorld() == null || corner2.getWorld() == null ||
		    !corner1.getWorld().equals(corner2.getWorld()))
			throw new IllegalArgumentException("both corners must be in the same world");

		int id = regions.isEmpty() ? 1 : regions.lastKey() + 1;
		AdminRegion region = new AdminRegion(id, name, corner1.getWorld().getName(), corner1.getBlockX(),
		                                     corner1.getBlockY(), corner1.getBlockZ(), corner2.getBlockX(),
		                                     corner2.getBlockY(), corner2.getBlockZ(), Set.of(tag == null ? "" : tag));
		regions.put(id, region);
		repository.save(region);
		return region;
	}

	public boolean remove(int id) {
		AdminRegion removed = regions.remove(id);
		if (removed == null) return false;
		repository.delete(removed);
		return true;
	}

	public @Nullable AdminRegion get(int id) {
		return regions.get(id);
	}

	/** Every region, by id. */
	public List<AdminRegion> all() {
		return new ArrayList<>(regions.values());
	}

	public List<AdminRegion> withTag(String tag) {
		String            wanted = tag.trim().toLowerCase(Locale.ROOT);
		List<AdminRegion> result = new ArrayList<>();
		for (AdminRegion region : regions.values()) if (region.getTags().contains(wanted)) result.add(region);
		return result;
	}

	@Override
	public String source() {
		return SOURCE;
	}

	// ponytail: linear scan of every region, like TurfManager.findAt; a per-world index if servers draw thousands
	@Override
	public List<PlaceRegion> regionsAt(Location at) {
		List<PlaceRegion> result = new ArrayList<>();
		if (at.getWorld() == null) return result;

		String world = at.getWorld().getName();
		for (AdminRegion region : regions.values()) {
			if (!region.getWorld().equals(world)) continue;
			PlaceRegion place = region.toPlace();
			if (place.contains(at)) result.add(place);
		}
		return result;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		regions.clear();
		for (AdminRegion region : repository.loadAll()) regions.put(region.getId(), region);
	}

	@Override
	public void onClear() {
		// like JailExitService: repository state survives a reload; onInitialize repopulates
	}
}
