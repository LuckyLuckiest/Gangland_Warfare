package org.luckyraven.gangland.turf.place;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.data.region.PlaceRegion;
import org.luckyraven.gangland.data.region.RegionProvider;
import org.luckyraven.gangland.data.region.RegionShape;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;

import java.util.List;
import java.util.Set;

/**
 * Publishes owned and unclaimed turfs as named regions. @since 0.16.0
 */
public final class TurfRegionProvider implements RegionProvider {

	private final TurfManager turfs;

	public TurfRegionProvider(TurfManager turfs) {
		this.turfs = turfs;
	}

	@Override
	public String source() {
		return "turf";
	}

	@Override
	public List<PlaceRegion> regionsAt(@Nullable Location at) {
		@Nullable Turf turf = turfs.findAt(at);
		if (turf == null) {
			return List.of();
		}

		CuboidRegion region = turf.getRegion();
		@Nullable Integer owner = turf.getOwnerGangId();
		Set<String> tags = owner == null ? Set.of(PlaceRegion.TAG_TURF)
		                                  : Set.of(PlaceRegion.TAG_TURF, PlaceRegion.TAG_HIDEOUT);

		return List.of(new PlaceRegion("turf:" + turf.getId(), turf.getDisplayName(), region.getWorld(),
		                               RegionShape.Cuboid.column(region.getMinX(), region.getMinZ(),
		                                                         region.getMaxX(), region.getMaxZ()),
		                               owner == null ? PlaceRegion.NO_OWNER : owner, tags));
	}
}
