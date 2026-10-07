package org.luckyraven.gangland.copsncrooks.station;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * The police stations, in memory and in the {@code cop_station} table. Create, remove and link persist at once;
 * {@link #onInitialize} loads (the {@link org.luckyraven.gangland.copsncrooks.jail.JailExitService} shape).
 */
public final class StationRegistry implements BeanLifecycle {

	private final IRepository<Station>      repository;
	private final TreeMap<Integer, Station> stations = new TreeMap<>();

	public StationRegistry(IRepository<Station> repository) {
		this.repository = repository;

		repository.setDataSupplier(stations::values);
	}

	/**
	 * Adds a station at {@code anchor} with the next id (highest stored + 1, so rows of unloaded worlds are never reused).
	 *
	 * @return the station, or {@code null} (nothing stored) when a station of that name already exists
	 */
	public @Nullable Station create(String name, Location anchor) {
		if (byName(name) != null || anchor.getWorld() == null) return null;

		int     id      = stations.isEmpty() ? 1 : stations.lastKey() + 1;
		Station station = new Station(id, name, anchor.getWorld().getName(), anchor.getX(), anchor.getY(),
		                              anchor.getZ(), anchor.getYaw(), null);
		stations.put(id, station);
		repository.save(station);
		return station;
	}

	public boolean remove(int id) {
		Station removed = stations.remove(id);
		if (removed == null) return false;
		repository.delete(removed);
		return true;
	}

	public void linkJail(int id, @Nullable Integer jailId) {
		Station station = stations.get(id);
		if (station == null) return;
		station.setJailId(jailId);
		repository.save(station);
	}

	public @Nullable Station get(int id) {
		return stations.get(id);
	}

	/** Case-insensitive, first match. */
	public @Nullable Station byName(String name) {
		for (Station station : stations.values())
			if (station.getName().equalsIgnoreCase(name)) return station;
		return null;
	}

	/** Every station, by id. */
	public List<Station> all() {
		return new ArrayList<>(stations.values());
	}

	/** The station nearest {@code at} in the same world by horizontal distance, at any distance; {@code null} if none. */
	public @Nullable Station nearest(Location at) {
		if (at.getWorld() == null) return null;

		String  world = at.getWorld().getName();
		Station best  = null;
		double  bestD = Double.MAX_VALUE;
		for (Station station : stations.values()) {
			if (!station.getWorld().equals(world)) continue;
			double dx = station.getX() - at.getX();
			double dz = station.getZ() - at.getZ();
			double d  = dx * dx + dz * dz;
			if (d < bestD) {
				bestD = d;
				best  = station;
			}
		}
		return best;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		stations.clear();
		for (Station station : repository.loadAll()) stations.put(station.getId(), station);
	}

	@Override
	public void onClear() {
		// like JailExitService: repository state survives a reload; onInitialize repopulates
	}
}
