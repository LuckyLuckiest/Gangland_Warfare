package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.NodeReader;

/**
 * {@code Wanted.Pursuit_Report} of {@code copsncrooks/wanted.yml}.
 *
 * @since 0.21.0
 */
public record PursuitReportSettings(boolean enabled, int minPeakStars, double perCopDown, double perHeat,
                                    boolean newsEnabled, int newsMinStars, int newsMinSeconds,
                                    int newsCooldownSeconds) {

	/** The shipped {@code Wanted.Pursuit_Report}. */
	public static final PursuitReportSettings DEFAULT = new PursuitReportSettings(true, 1, 2500.0, 10.0, true, 4, 120,
	                                                                              300);

	/** Reads the {@code Wanted} section; a null section, or a missing block or key, is its default. */
	public static PursuitReportSettings parse(@Nullable NodeReader wanted) {
		if (wanted == null) {
			return DEFAULT;
		}
		NodeReader report = block(wanted, "Pursuit_Report");
		if (report == null) {
			return DEFAULT;
		}

		PursuitReportSettings d    = DEFAULT;
		NodeReader            city = block(report, "City_Cost");
		NodeReader            news = block(report, "Breaking_News");

		return new PursuitReportSettings(
				report.get("Enable").asBool().orDefault(d.enabled()),
				(int) Math.max(0, report.get("Min_Peak_Stars").asDouble().orDefault(d.minPeakStars())),
				city == null ? d.perCopDown() : Math.max(0, city.get("Per_Cop_Down").asDouble().orDefault(d.perCopDown())),
				city == null ? d.perHeat() : Math.max(0, city.get("Per_Heat").asDouble().orDefault(d.perHeat())),
				news == null ? d.newsEnabled() : news.get("Enable").asBool().orDefault(d.newsEnabled()),
				news == null ? d.newsMinStars()
				             : (int) Math.max(0, news.get("Min_Stars").asDouble().orDefault(d.newsMinStars())),
				news == null ? d.newsMinSeconds()
				             : (int) Math.max(0, news.get("Min_Seconds").asDouble().orDefault(d.newsMinSeconds())),
				news == null ? d.newsCooldownSeconds()
				             : (int) Math.max(0, news.get("Cooldown_Seconds").asDouble().orDefault(d.newsCooldownSeconds())));
	}

	private static @Nullable NodeReader block(NodeReader parent, String key) {
		return parent.get(key).asMapping().reader();
	}
}
