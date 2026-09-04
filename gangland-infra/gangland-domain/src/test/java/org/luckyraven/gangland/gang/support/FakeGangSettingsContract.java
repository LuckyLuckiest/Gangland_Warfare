package org.luckyraven.gangland.gang.support;

import org.luckyraven.gangland.gang.contract.GangSettingsContract;

import java.math.BigDecimal;

/**
 * In-memory {@link GangSettingsContract} for domain-module tests. {@code GangSettings} is a static facade bound
 * once per JVM by {@code GangModuleConfig} in production; tests that construct a {@code Gang}, {@code User}, or
 * {@code RankManager} must call {@link org.luckyraven.gangland.gang.GangSettings#bind(GangSettingsContract)} with
 * an instance of this class first (typically in a {@code @BeforeEach}).
 */
public final class FakeGangSettingsContract implements GangSettingsContract {

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
	private String     displayNameChar  = "*";
	private String     rankHead         = "member";
	private String     rankTail         = "owner";

	public FakeGangSettingsContract withRankHead(String head) {
		this.rankHead = head;
		return this;
	}

	public FakeGangSettingsContract withRankTail(String tail) {
		this.rankTail = tail;
		return this;
	}

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

	@Override
	public String getGangDisplayNameChar() {
		return displayNameChar;
	}

	@Override
	public String getGangRankHead() {
		return rankHead;
	}

	@Override
	public String getGangRankTail() {
		return rankTail;
	}

}
