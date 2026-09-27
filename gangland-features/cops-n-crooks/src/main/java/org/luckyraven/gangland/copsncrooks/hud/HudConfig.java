package org.luckyraven.gangland.copsncrooks.hud;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

/**
 * Immutable view of the {@code Hud:} section of {@code npc/cops.yml} (0.12 F3): boss bar, flashing stars, the
 * search-zone particle ring and the escape compass.
 *
 * <p>Every key is optional: a missing section yields {@link #defaults()}, a missing key its default below, so a
 * server running an older {@code cops.yml} keeps loading.
 * <ul>
 *     <li>{@code Enable} ({@code true}) — the whole service no-ops when {@code false}.</li>
 *     <li>{@code Boss_Bar} ({@code true}) — the wanted boss bar.</li>
 *     <li>{@code Search_Zone_Ring} ({@code true}) — the red-dust ring while SEARCHING (the wanted player only).</li>
 *     <li>{@code Escape_Compass} ({@code true}) — points the compass outside the search zone while SEARCHING.</li>
 *     <li>{@code Star_Gain_Title} ({@code true}) — title/subtitle + sound on every star gained.</li>
 *     <li>{@code Messages.*} — boss bar titles ({@code %stars%}/{@code %time%}) and the star-gain title/subtitle
 *     ({@code %stars%}).</li>
 * </ul>
 */
@Getter
public final class HudConfig {

	public static final String DEFAULT_IN_SIGHT           = "%stars% &c&lIN SIGHT";
	public static final String DEFAULT_SEARCHING          = "%stars% &e&lSEARCHING %time%";
	public static final String DEFAULT_WANTED             = "%stars% &c&lWANTED";
	public static final String DEFAULT_STAR_LOST          = "%stars% &a&lSTAR LOST";
	public static final String DEFAULT_STAR_GAINED_TITLE    = "&c&lWANTED";
	public static final String DEFAULT_STAR_GAINED_SUBTITLE = "%stars%";

	private final boolean enabled;
	private final boolean bossBar;
	private final boolean searchZoneRing;
	private final boolean escapeCompass;
	private final boolean starGainTitle;

	private final String inSightMessage;
	private final String searchingMessage;
	private final String wantedMessage;
	private final String starLostMessage;
	private final String starGainedTitle;
	private final String starGainedSubtitle;

	public HudConfig(boolean enabled, boolean bossBar, boolean searchZoneRing, boolean escapeCompass,
	                 boolean starGainTitle, @Nullable String inSightMessage, @Nullable String searchingMessage,
	                 @Nullable String wantedMessage, @Nullable String starLostMessage,
	                 @Nullable String starGainedTitle, @Nullable String starGainedSubtitle) {
		this.enabled            = enabled;
		this.bossBar            = bossBar;
		this.searchZoneRing     = searchZoneRing;
		this.escapeCompass      = escapeCompass;
		this.starGainTitle      = starGainTitle;
		this.inSightMessage     = orDefault(inSightMessage, DEFAULT_IN_SIGHT);
		this.searchingMessage   = orDefault(searchingMessage, DEFAULT_SEARCHING);
		this.wantedMessage      = orDefault(wantedMessage, DEFAULT_WANTED);
		this.starLostMessage    = orDefault(starLostMessage, DEFAULT_STAR_LOST);
		this.starGainedTitle    = orDefault(starGainedTitle, DEFAULT_STAR_GAINED_TITLE);
		this.starGainedSubtitle = orDefault(starGainedSubtitle, DEFAULT_STAR_GAINED_SUBTITLE);
	}

	/**
	 * @return the shipped defaults, used when {@code cops.yml} has no {@code Hud:} section
	 */
	public static HudConfig defaults() {
		return new HudConfig(true, true, true, true, true, DEFAULT_IN_SIGHT, DEFAULT_SEARCHING, DEFAULT_WANTED,
		                     DEFAULT_STAR_LOST, DEFAULT_STAR_GAINED_TITLE, DEFAULT_STAR_GAINED_SUBTITLE);
	}

	/**
	 * Parses the {@code Hud:} section of the {@code cops.yml} root.
	 *
	 * @param root positional reader over the {@code cops.yml} root mapping
	 * @param report issue collector drained by the enclosing loader
	 *
	 * @return the parsed config; {@link #defaults()} when the section is absent
	 */
	public static HudConfig parse(@Nullable NodeReader root, ConfigReport report) {
		if (root == null) return defaults();

		MappingNode section = root.get("Hud").asMapping().orNull();
		if (section == null) return defaults();

		NodeReader hud = NodeReader.of(section, report);

		boolean enabled        = hud.get("Enable").asBool().orDefault(true);
		boolean bossBar        = hud.get("Boss_Bar").asBool().orDefault(true);
		boolean searchZoneRing = hud.get("Search_Zone_Ring").asBool().orDefault(true);
		boolean escapeCompass  = hud.get("Escape_Compass").asBool().orDefault(true);
		boolean starGainTitle  = hud.get("Star_Gain_Title").asBool().orDefault(true);

		String inSight           = DEFAULT_IN_SIGHT;
		String searching          = DEFAULT_SEARCHING;
		String wanted             = DEFAULT_WANTED;
		String starLost           = DEFAULT_STAR_LOST;
		String starGainedTitle    = DEFAULT_STAR_GAINED_TITLE;
		String starGainedSubtitle = DEFAULT_STAR_GAINED_SUBTITLE;

		MappingNode messages = hud.get("Messages").asMapping().orNull();
		if (messages != null) {
			NodeReader messageReader = NodeReader.of(messages, report);

			inSight            = messageReader.get("In_Sight").asString().orDefault(DEFAULT_IN_SIGHT);
			searching          = messageReader.get("Searching").asString().orDefault(DEFAULT_SEARCHING);
			wanted             = messageReader.get("Wanted").asString().orDefault(DEFAULT_WANTED);
			starLost           = messageReader.get("Star_Lost").asString().orDefault(DEFAULT_STAR_LOST);
			starGainedTitle    = messageReader.get("Star_Gained_Title").asString().orDefault(DEFAULT_STAR_GAINED_TITLE);
			starGainedSubtitle = messageReader.get("Star_Gained_Subtitle").asString()
			                                  .orDefault(DEFAULT_STAR_GAINED_SUBTITLE);
		}

		return new HudConfig(enabled, bossBar, searchZoneRing, escapeCompass, starGainTitle, inSight, searching,
		                     wanted, starLost, starGainedTitle, starGainedSubtitle);
	}

	private static String orDefault(@Nullable String value, String fallback) {
		return value == null || value.isEmpty() ? fallback : value;
	}
}
