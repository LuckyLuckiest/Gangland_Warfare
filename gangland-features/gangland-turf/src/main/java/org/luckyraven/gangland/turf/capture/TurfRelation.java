package org.luckyraven.gangland.turf.capture;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.data.Turf;

/**
 * How a viewer relates to a turf. Colour on the map and the HUD always follows this (5.1: colour = relation).
 */
public enum TurfRelation {
	OWN, ALLY, RIVAL, UNCLAIMED, GANGLESS;

	/** viewerGangId = the viewer's gang id or null when the viewer has no gang. */
	public static TurfRelation of(Turf turf, @Nullable Integer viewerGangId, GangLookupContract gangs) {
		if (turf.isUnclaimed()) {
			return UNCLAIMED;
		}
		if (viewerGangId == null) {
			return GANGLESS;
		}
		int ownerId = turf.getOwnerGangId();
		if (ownerId == viewerGangId) {
			return OWN;
		}
		Gang owner  = gangs.findById(ownerId);
		Gang viewer = gangs.findById(viewerGangId);
		if (owner != null && viewer != null && owner.isAlly(viewer)) {
			return ALLY;
		}
		return RIVAL;
	}
}
