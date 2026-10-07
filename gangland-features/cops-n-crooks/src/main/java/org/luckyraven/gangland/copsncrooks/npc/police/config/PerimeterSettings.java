package org.luckyraven.gangland.copsncrooks.npc.police.config;

import java.util.List;

/**
 * The containment perimeter ({@code cops.yml}'s {@code Cops.Perimeter}): while a suspect of at least {@code minLevel}
 * is out of sight, {@code posts} cops hold posts on a ring around the zone and watch for him.
 *
 * @param enabled     {@code false} keeps the 0.15 behaviour.
 * @param minLevel    lowest wanted level that sets a perimeter.
 * @param posts       most cops posted at once.
 * @param roles       role names posted first (the rest of the squad fills in when none match).
 * @param maxSeconds  longest a perimeter holds.
 * @param laneLength  blocks of clear lane kept toward the centre.
 * @param sightRange  how far a posted cop sees.
 * @param leashRadius how far a posted cop may stray from its post.
 * @since 0.16.0
 */
public record PerimeterSettings(boolean enabled, int minLevel, int posts, List<String> roles, int maxSeconds,
                                double laneLength, double sightRange, double leashRadius) {

	/** Matches the shipped cops.yml: from 3 stars, 2 posts (Marksman, Defender), 60 s, lane 16, sight 40, leash 4. */
	public static final PerimeterSettings DEFAULT = new PerimeterSettings(true, 3, 2, List.of("Marksman", "Defender"),
	                                                                      60, 16.0, 40.0, 4.0);

	public PerimeterSettings {
		roles = List.copyOf(roles);
	}
}
