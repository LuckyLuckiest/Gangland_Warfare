package org.luckyraven.gangland.gang.support;

import org.luckyraven.gangland.core.user.IdentitySettingsContract;

import java.math.BigDecimal;

/**
 * In-memory {@link IdentitySettingsContract} for domain-module tests. {@code IdentitySettings} is a static facade
 * bound once per JVM by the CONFIG-phase identity config in production; tests that construct a {@code Gang} (whose
 * constructor builds a {@code Bounty}/{@code Level} through {@code IdentitySettings}) must call
 * {@link org.luckyraven.gangland.core.user.IdentitySettings#bind(IdentitySettingsContract)} with an instance of
 * this class first (typically in a {@code @BeforeEach}).
 *
 * <p>Split out of {@code FakeGangSettingsContract} (WS5 G0, B2) alongside the contract itself — duplicated here
 * (rather than shared via a test-jar dependency) since {@code gangland-domain} doesn't otherwise consume
 * gangland-core's test-jar; a small fixture is cheaper than a new cross-module test dependency. Identical fixture
 * for gangland-core tests lives at {@code org.luckyraven.gangland.core.support.FakeIdentitySettingsContract}.
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
