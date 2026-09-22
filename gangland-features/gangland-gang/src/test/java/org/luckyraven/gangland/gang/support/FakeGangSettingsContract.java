package org.luckyraven.gangland.gang.support;

import org.luckyraven.gangland.gang.contract.GangSettingsContract;

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

	public FakeGangSettingsContract withRankHead(String head) {
		this.rankHead = head;
		return this;
	}

	public FakeGangSettingsContract withRankTail(String tail) {
		this.rankTail = tail;
		return this;
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
