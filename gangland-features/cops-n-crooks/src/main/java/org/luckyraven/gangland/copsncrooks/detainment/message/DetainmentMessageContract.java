package org.luckyraven.gangland.copsncrooks.detainment.message;

import java.util.List;

/**
 * Detainment-scoped message contract. Covers the title / subtitle / action-bar strings emitted during the cuff → jail →
 * release flow. Integrations (gangland-impl) implement this interface by routing each call to their preferred
 * {@code Messages} enum entry so cops-n-crooks stays decoupled from the Messages enum.
 */
public interface DetainmentMessageContract {

	String handcuffedTitle();

	String handcuffedSubtitle();

	String handcuffedActionBar();

	String jailedTitle();

	String jailedSubtitle();

	String jailedActionBar();

	String releasedTitle();

	String releasedSubtitle();

	String cuffingTitle();

	String cuffingSubtitle(long secondsRemaining);

	String cuffedTitle();

	String cuffedSubtitle();

	String handcuffedRestraintTick();

	String jailedRestraintTick();

	// ── Transit ───────────────────────────────────────────────────────────────

	String transitStartingActionBar(long secondsRemaining);

	String transitCommittedTitle();

	String transitCommittedSubtitle();

	// ── Handcuff bribe ────────────────────────────────────────────────────────

	String handcuffBribeGuiTitle();

	String handcuffBribeButtonLabel(String cost);

	List<String> handcuffBribeButtonLore(String cost, String balance);

	String handcuffBribeSuccessTitle();

	String handcuffBribeSuccessSubtitle();

	String handcuffBribeInsufficient();

	// ── Paperwork GUI ─────────────────────────────────────────────────────────

	String paperworkItemName();

	List<String> paperworkItemLore();

	String paperworkGuiTitle();

	String paperworkBailLabel(String cost);

	List<String> paperworkBailLore(String cost, String balance);

	String paperworkBribeLabel(String cost);

	List<String> paperworkBribeLore(String cost, String chancePercent);

	String paperworkSentenceLabel();

	List<String> paperworkSentenceLore(long secondsRemaining);

	String paperworkInfoLabel();

	List<String> paperworkInfoLore(int wantedAtArrest, long secondsRemaining, String balance);

	// ── Bail / jail bribe / sentence ──────────────────────────────────────────

	String bailSuccess();

	String bailInsufficient();

	String jailBribeSuccess();

	String jailBribeFail(long extraSeconds);

	String jailBribeInsufficient();

	String sentenceTickActionBar(long secondsRemaining);

	String sentenceCompleteTitle();

	String sentenceCompleteSubtitle();

	// ── Break-free minigame ───────────────────────────────────────────────────

	String breakFreeProgressActionBar(int current, int required);

	String breakFreeSuccessTitle();

	String breakFreeSuccessSubtitle();

}
