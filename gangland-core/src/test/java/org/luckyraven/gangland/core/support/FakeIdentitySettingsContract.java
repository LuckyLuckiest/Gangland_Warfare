package org.luckyraven.gangland.core.support;

import org.luckyraven.gangland.core.user.IdentitySettingsContract;

import java.math.BigDecimal;

/**
 * In-memory {@link IdentitySettingsContract} for gangland-core tests. {@code IdentitySettings} is a static facade
 * bound once per JVM by the CONFIG-phase identity config in production; tests that construct a {@code User} or
 * {@code Level} must call {@link org.luckyraven.gangland.core.user.IdentitySettings#bind(IdentitySettingsContract)}
 * with an instance of this class first (typically in a {@code @BeforeEach}).
 *
 * <p>Split out of {@code FakeGangSettingsContract} (WS5 G0, B2) alongside the contract itself — this fixture carries
 * the 11 getters that moved off the gang-module contract.
 */
public final class FakeIdentitySettingsContract implements IdentitySettingsContract {

	private boolean    autoSave         = true;
	private int        userMaxLevel     = 100;
	private int        userLevelBase    = 1000;
	private String     userLevelFormula = "base * level ^ 1.5";
	private BigDecimal bountyEachKill   = BigDecimal.valueOf(100);
	private double     bountyMultiple   = 0.1;
	private double     bountyTimerMax   = 100_000;
	private boolean    bountyTimerOn    = false;
	private int        wantedIncrement  = 1;
	private int        wantedMaxLevel   = 5;
	private boolean    wantedTimerOn    = false;

	@Override
	public boolean isAutoSave() {
		return autoSave;
	}

	@Override
	public int getUserMaxLevel() {
		return userMaxLevel;
	}

	@Override
	public int getUserLevelBaseAmount() {
		return userLevelBase;
	}

	@Override
	public String getUserLevelFormula() {
		return userLevelFormula;
	}

	@Override
	public BigDecimal getBountyEachKillValue() {
		return bountyEachKill;
	}

	@Override
	public double getBountyTimerMultiple() {
		return bountyMultiple;
	}

	@Override
	public double getBountyTimerMax() {
		return bountyTimerMax;
	}

	@Override
	public boolean isBountyTimerEnabled() {
		return bountyTimerOn;
	}

	@Override
	public int getWantedLevelIncrement() {
		return wantedIncrement;
	}

	@Override
	public int getWantedMaximumLevel() {
		return wantedMaxLevel;
	}

	@Override
	public boolean isWantedTimerEnabled() {
		return wantedTimerOn;
	}

}
