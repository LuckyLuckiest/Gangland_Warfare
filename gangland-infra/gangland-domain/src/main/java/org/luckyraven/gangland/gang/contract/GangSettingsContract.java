package org.luckyraven.gangland.gang.contract;

/**
 * Configuration values the gang / rank / member domain reads at runtime. Implemented in gangland-impl by a bean
 * that delegates to the Settings class, so domain code never imports Settings directly.
 *
 * <p>Split down to these 3 gang-display getters (WS5 G0, B2) — the other 11 identity getters (user level/bounty/
 * wanted) moved to {@code IdentitySettingsContract} in gangland-api, since {@code User}/{@code Level} are core, not
 * gang, types.
 */
public interface GangSettingsContract {

	String getGangDisplayNameChar();

	String getGangRankHead();

	String getGangRankTail();
}
