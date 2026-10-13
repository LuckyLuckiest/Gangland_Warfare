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

	// TDD stub: neutral values, the green step sets the shipped defaults
	public static final PursuitReportSettings DEFAULT = new PursuitReportSettings(false, 0, 0.0, 0.0, false, 0, 0, 0);

	/** Reads the {@code Wanted} section; a null section, or a missing block or key, is its default. */
	public static PursuitReportSettings parse(@Nullable NodeReader wanted) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
