package org.luckyraven.gangland.copsncrooks.wanted.hud;

import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages.Key;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.events.wanted.EvasionState;

import java.util.Map;

/**
 * The text and geometry of the chase HUD: the star card, the boss bar title and colour, and the zone-edge point the
 * compass aims at. Pure, so every line is testable without a server.
 *
 * @since 0.15.0
 */
public final class StarCard {

	private StarCard() {
	}

	/** Why a star was raised: the crime, the tier now coming, and whether that tier wants cuffs or shoots first. */
	public static String raiseCard(WantedMessages messages, String crimeName, String tierName, boolean skipCuffing) {
		String stance = messages.format(skipCuffing ? Key.STANCE_SHOOT : Key.STANCE_CUFFS, Map.of());

		return messages.format(Key.CARD_RAISE, Map.of("crime", crimeName, "tier", tierName, "stance", stance));
	}

	/** Why a star was lost. */
	public static String dropCard(WantedMessages messages, WantedCause cause) {
		Key key = switch (cause) {
			case EVASION -> Key.CARD_DROP_EVASION;
			case DECAY -> Key.CARD_DROP_DECAY;
			default -> Key.CARD_DROP_OTHER;
		};

		return messages.format(key, Map.of());
	}

	/** The boss bar title for {@code state}; OFF reads as SEEN (still wanted, nobody searching). */
	public static String barTitle(WantedMessages messages, EvasionState state, String stars, int secondsLeft) {
		return switch (state) {
			case SEARCHING -> messages.format(Key.BAR_SEARCHING, Map.of("stars", stars, "time",
			                                                           WantedMessages.duration(secondsLeft)));
			case EVADED -> messages.format(Key.BAR_EVADED, Map.of("stars", stars));
			default -> messages.format(Key.BAR_SEEN, Map.of("stars", stars));
		};
	}

	/** Red when seen, yellow and white alternating while searching, green once a star is lost. */
	public static BarColor barColor(EvasionState state, boolean flashOn) {
		return switch (state) {
			case SEARCHING -> flashOn ? BarColor.YELLOW : BarColor.WHITE;
			case EVADED -> BarColor.GREEN;
			default -> BarColor.RED;
		};
	}

	/** The point on the zone edge towards {@code from}, at {@code from}'s height; from the centre itself, +X. */
	public static Location exitPoint(Location centre, double radius, Location from) {
		double dx    = from.getX() - centre.getX();
		double dz    = from.getZ() - centre.getZ();
		double len   = Math.sqrt(dx * dx + dz * dz);
		double unitX = len < 1.0E-9 ? 1.0 : dx / len;
		double unitZ = len < 1.0E-9 ? 0.0 : dz / len;

		return new Location(centre.getWorld(), centre.getX() + unitX * radius, from.getY(),
		                    centre.getZ() + unitZ * radius);
	}
}
