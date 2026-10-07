package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.region.PlaceNames;
import org.luckyraven.gangland.data.region.PlaceRegion;

import java.util.UUID;

/**
 * The hideouts a player may use: places tagged {@code hideout} that are nobody's or his own gang's. Rival hideouts are
 * open ground to him.
 *
 * @since 0.16.0
 */
public final class Hideouts {

	private final PlaceNames     places;
	private final GangMembership gangs;

	public Hideouts(PlaceNames places, GangMembership gangs) {
		this.places = places;
		this.gangs  = gangs;
	}

	/** The smallest hideout the player stands in that is open to him, or {@code null}. */
	public @Nullable PlaceRegion at(Player player) {
		return open(player.getLocation(), player.getUniqueId());
	}

	/** The id of the hideout at {@code at} that is open to {@code player}, or {@code null}. */
	public @Nullable String idAt(Location at, UUID player) {
		PlaceRegion region = open(at, player);
		return region == null ? null : region.id();
	}

	/** {@code regionsAt} is smallest footprint first, so the first open hideout is the smallest one. */
	private @Nullable PlaceRegion open(Location at, UUID player) {
		int gang = gangs.gangIdOf(player);
		for (PlaceRegion region : places.regionsAt(at)) {
			if (region.hasTag(PlaceRegion.TAG_HIDEOUT) &&
			    (region.ownerGangId() == PlaceRegion.NO_OWNER || region.ownerGangId() == gang)) return region;
		}
		return null;
	}
}
