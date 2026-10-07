package org.luckyraven.gangland.copsncrooks.place;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** The admin-placed setup points, in memory and in the {@code cop_point} table; create and remove persist at once. */
public final class SetupPointRegistry implements BeanLifecycle {

	private final IRepository<SetupPoint>      repository;
	private final TreeMap<Integer, SetupPoint> points = new TreeMap<>();

	public SetupPointRegistry(IRepository<SetupPoint> repository) {
		this.repository = repository;

		repository.setDataSupplier(points::values);
	}

	public SetupPoint create(String kind, String name, Location at) {
		if (at.getWorld() == null) throw new IllegalArgumentException("the point needs a world");

		int        id    = points.isEmpty() ? 1 : points.lastKey() + 1;
		SetupPoint point = new SetupPoint(id, kind, name, at.getWorld().getName(), at.getX(), at.getY(), at.getZ());
		points.put(id, point);
		repository.save(point);
		return point;
	}

	public boolean remove(int id) {
		SetupPoint removed = points.remove(id);
		if (removed == null) return false;
		repository.delete(removed);
		return true;
	}

	public @Nullable SetupPoint get(int id) {
		return points.get(id);
	}

	/** Every point, by id. */
	public List<SetupPoint> all() {
		return new ArrayList<>(points.values());
	}

	public List<SetupPoint> ofKind(String kind) {
		List<SetupPoint> result = new ArrayList<>();
		for (SetupPoint point : points.values()) if (point.getKind().equals(kind)) result.add(point);
		return result;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		points.clear();
		for (SetupPoint point : repository.loadAll()) points.put(point.getId(), point);
	}

	@Override
	public void onClear() {
		// like JailExitService: repository state survives a reload; onInitialize repopulates
	}
}
