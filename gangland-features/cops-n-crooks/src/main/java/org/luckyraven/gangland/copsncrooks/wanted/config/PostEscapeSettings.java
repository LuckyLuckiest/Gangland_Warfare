package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.NodeReader;

/**
 * {@code Wanted.Post_Escape} and {@code Wanted.Hud.Bounty} of {@code copsncrooks/wanted.yml}: the search that goes on after
 * an evasion escape, and the bounty bar that shows it. Loaded beside {@link ChaseConfig} by {@link ChaseConfigLoader}.
 *
 * @since 0.16.1
 */
public record PostEscapeSettings(boolean enabled, int searchSeconds, boolean bountyHud, String barColor,
                                 boolean announce, int spottedStars) {

	/**
	 * The shipped {@code Wanted.Post_Escape} and {@code Wanted.Hud.Bounty}. A searched player the cops sight is raised by one
	 * star, which ends the search (0.16.1 wanted-1); 0 keeps the search harmless.
	 */
	public static final PostEscapeSettings DEFAULT = new PostEscapeSettings(true, 120, true, "YELLOW", true, 1);

	/** Reads the {@code Wanted} section; a null section, or a missing block or key, is its default. */
	public static PostEscapeSettings parse(@Nullable NodeReader wanted) {
		if (wanted == null) return DEFAULT;

		PostEscapeSettings d      = DEFAULT;
		NodeReader         escape = block(wanted, "Post_Escape");
		NodeReader         hud    = block(wanted, "Hud");
		NodeReader         bounty = hud == null ? null : block(hud, "Bounty");

		return new PostEscapeSettings(
				escape == null ? d.enabled() : escape.get("Enable").asBool().orDefault(d.enabled()),
				escape == null ? d.searchSeconds()
				               : (int) escape.get("Search_Seconds").asDouble().min(1).orDefault(d.searchSeconds()),
				bounty == null ? d.bountyHud() : bounty.get("Enable").asBool().orDefault(d.bountyHud()),
				bounty == null ? d.barColor() : bounty.get("Bar_Color").asString().orDefault(d.barColor()),
				escape == null ? d.announce() : escape.get("Announce").asBool().orDefault(d.announce()),
				escape == null ? d.spottedStars()
				               : (int) escape.get("Spotted_Stars").asDouble().min(0).orDefault(d.spottedStars()));
	}

	private static @Nullable NodeReader block(NodeReader parent, String key) {
		return parent.get(key).asMapping().reader();
	}
}
