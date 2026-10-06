package org.luckyraven.gangland.gang.support;

import org.luckyraven.gangland.gang.contract.GangSettingsContract;

import java.math.BigDecimal;

/**
 * In-memory {@link GangSettingsContract} for domain-module tests. {@code GangSettings} is a static facade bound
 * once per JVM by {@code GangModuleConfig} in production; tests that construct a {@code Gang} or {@code RankManager}
 * must call {@link org.luckyraven.gangland.gang.GangSettings#bind(GangSettingsContract)} with
 * an instance of this class first (typically in a {@code @BeforeEach}).
 *
 * <p>Split down to these 3 gang-display getters (WS5 G0, B2) — see {@link FakeIdentitySettingsContract} for the
 * identity-slice getters that moved off this fixture.
 */
public final class FakeGangSettingsContract implements GangSettingsContract {

	private String displayNameChar = "*";
	private String rankHead        = "member";
	private String rankTail        = "owner";

	private BigDecimal createFee        = new BigDecimal("100000");
	private BigDecimal maxBalance       = new BigDecimal("100000000000");
	private double     contributionRate = 1_000;

	public FakeGangSettingsContract withRankHead(String head) {
		this.rankHead = head;
		return this;
	}

	public FakeGangSettingsContract withRankTail(String tail) {
		this.rankTail = tail;
		return this;
	}

	public FakeGangSettingsContract withCreateFee(BigDecimal fee) {
		this.createFee = fee;
		return this;
	}

	public FakeGangSettingsContract withMaxBalance(BigDecimal max) {
		this.maxBalance = max;
		return this;
	}

	public FakeGangSettingsContract withContributionRate(double rate) {
		this.contributionRate = rate;
		return this;
	}

	@Override
	public boolean isGangNameDuplicates() {
		return false;
	}

	@Override
	public BigDecimal getGangInitialBalance() {
		return BigDecimal.ZERO;
	}

	@Override
	public BigDecimal getGangCreateFee() {
		return createFee;
	}

	@Override
	public BigDecimal getGangMaxBalance() {
		return maxBalance;
	}

	@Override
	public double getGangContributionRate() {
		return contributionRate;
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
