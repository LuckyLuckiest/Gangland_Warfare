package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.bukkit.ChatColor;

import java.util.List;
import java.util.Random;

/**
 * Cop callsigns ({@code Cops.Names}): {@code "Officer ✚ Medic Bob #1592"}. The coloured callsign is the cop's hologram
 * line only; the radio names a cop from {@code Speaker_Name} in {@code copsncrooks/cop_radio_messages.yml}, not from
 * this string. {@link #shortName} is the plain Citizens name, kept at 16 characters or fewer so Citizens never swaps
 * the entity's profile name for its {@code CIT-...} scoreboard team name.
 *
 * @param format {@code %rank%} (the tier's {@code Display_Name}, in its colour), {@code %role%} (the squad role's
 *               coloured display with its symbol, {@code "&c✚ Medic"}; empty for a cop with no role, its colour code
 *               and the doubled space dropped with it), {@code %name%} and {@code %badge%}.
 * @param firstNames the first-name pool; empty means no first name ({@code "Officer #1592"})
 */
public record CopNames(String format, List<String> firstNames) {

	public static final CopNames DEFAULT = new CopNames("%rank% %role% &f%name% &7#%badge%",
	                                                    List.of("Bob", "Jim", "Carl", "Dave", "Frank", "Mike", "Tony",
	                                                            "Steve", "Ray", "Eddie", "Gus", "Hank", "Joe", "Lou",
	                                                            "Marty", "Nick", "Pete", "Rick", "Sam", "Vince", "Walt",
	                                                            "Ann", "Kate", "Linda", "Maria", "Rosa", "Sue", "Tina"));

	/** Citizens' limit before a PLAYER NPC's name moves to a hologram and its profile name to the team name. */
	private static final int MAX_PLAIN_NAME = 16;

	public CopNames {
		firstNames = List.copyOf(firstNames);
	}

	/** Badge number: unique among live NPCs because Citizens ids are. */
	public static int badge(int citizensId) {
		return 1000 + citizensId;
	}

	/** {@code "Bob #1592"}: colour-free and at most 16 characters, the first name cut to fit. */
	public static String shortName(String firstName, int badge) {
		String suffix = "#" + badge;
		String plain  = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', firstName)).trim();
		if (plain.isEmpty()) return suffix;

		int room = MAX_PLAIN_NAME - suffix.length() - 1;
		if (room <= 0) return suffix;
		return plain.substring(0, Math.min(room, plain.length())).trim() + " " + suffix;
	}

	/** A random first name from the pool; empty for an empty pool. */
	public String pickName(Random random) {
		return firstNames.isEmpty() ? "" : firstNames.get(random.nextInt(firstNames.size()));
	}

	/**
	 * The coloured callsign. An empty first name or role takes its own colour code with it and repeated spaces
	 * collapse, so it still reads cleanly.
	 */
	public String callsign(String rank, String role, String firstName, int badge) {
		String text = format;
		if (firstName.isEmpty()) text = text.replaceAll("(&[0-9a-fk-orA-FK-OR])*%name%", "");
		if (role.isEmpty()) text = text.replaceAll("(&[0-9a-fk-orA-FK-OR])*%role%", "");
		return text.replace("%rank%", rank)
		           .replace("%role%", role)
		           .replace("%name%", firstName)
		           .replace("%badge%", String.valueOf(badge))
		           .replaceAll(" {2,}", " ")
		           .trim();
	}
}
