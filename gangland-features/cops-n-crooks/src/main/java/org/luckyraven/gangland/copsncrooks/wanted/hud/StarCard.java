package org.luckyraven.gangland.copsncrooks.wanted.hud;

import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages.Key;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.events.wanted.EvasionState;

import java.util.Map;

/**
 * The text and geometry of the chase HUD: the star card, the boss bar title and colour, the way-out arrow and the
 * zone-edge point it and the compass aim at. Pure, so every line is testable without a server.
 *
 * @since 0.15.0
 */
public final class StarCard {

	/** Straight ahead first, then clockwise in 45 degree steps. */
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←",
	                                        "↖"};

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

	/** The searching bar's way out: an arrow seen from where {@code from} faces, and the blocks to the exit point. */
	public static String wayHint(WantedMessages messages, Location centre, double radius, Location from) {
		Location exit = exitPoint(centre, radius, from);
		double   dx   = exit.getX() - from.getX();
		double   dz   = exit.getZ() - from.getZ();

		return messages.format(Key.BAR_WAY, Map.of("arrow", arrow(from.getYaw(), dx, dz), "distance",
		                                           String.valueOf(Math.round(Math.sqrt(dx * dx + dz * dz)))));
	}

	/** One of eight arrows for the direction ({@code dx}, {@code dz}) to someone facing {@code yaw} (0 = +Z). */
	static String arrow(float yaw, double dx, double dz) {
		double bearing  = Math.toDegrees(Math.atan2(-dx, dz));
		double relative = ((bearing - yaw) % 360 + 360) % 360;

		return ARROWS[(int) Math.round(relative / 45) % ARROWS.length];
	}

	/** Red when seen, yellow and white alternating while searching, green once a star is lost. */
	public static BarColor barColor(EvasionState state, boolean flashOn) {
		return switch (state) {
			case SEARCHING -> flashOn ? BarColor.YELLOW : BarColor.WHITE;
			case EVADED -> BarColor.GREEN;
			default -> BarColor.RED;
		};
	}

	/**
	 * The way out of the zone: the edge point towards {@code from} while inside, and once outside a point a zone radius
	 * further along the same line (away from the centre). At {@code from}'s height; from the centre itself, +X.
	 */
	public static Location exitPoint(Location centre, double radius, Location from) {
		double dx    = from.getX() - centre.getX();
		double dz    = from.getZ() - centre.getZ();
		double len   = Math.sqrt(dx * dx + dz * dz);
		double unitX = len < 1.0E-9 ? 1.0 : dx / len;
		double unitZ = len < 1.0E-9 ? 0.0 : dz / len;

		double reach = len > radius ? len + radius : radius;

		return new Location(centre.getWorld(), centre.getX() + unitX * reach, from.getY(),
		                    centre.getZ() + unitZ * reach);
	}
}
