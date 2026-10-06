package org.luckyraven.gangland.gang.contract;

import java.math.BigDecimal;

/**
 * Configuration values the gang / rank / member domain reads at runtime. Implemented in this module by
 * {@code GanglandGangSettings}, which reads the module's own {@code gang/gang_settings.yml} (0.15.1), so domain code
 * never imports the core Settings class.
 *
 * <p>The identity-slice getters (user level/bounty/wanted) live in {@code IdentitySettingsContract} in gangland-api,
 * since {@code User}/{@code Level} are core, not gang, types.
 */
public interface GangSettingsContract {

	boolean isGangNameDuplicates();

	String getGangDisplayNameChar();

	String getGangRankHead();

	String getGangRankTail();

	BigDecimal getGangInitialBalance();

	BigDecimal getGangCreateFee();

	BigDecimal getGangMaxBalance();

	double getGangContributionRate();
}
