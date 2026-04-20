package me.luckyraven.file.configuration.copsncrooks;

import me.luckyraven.copsncrooks.detainment.message.DetainmentMessageContract;
import me.luckyraven.file.configuration.Messages;

import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link DetainmentMessageContract} implementation. Routes every call through a {@link Messages} enum key so
 * cops-n-crooks detainment code stays decoupled from the Messages enum.
 */
public final class GanglandDetainmentMessages implements DetainmentMessageContract {

	private static List<String> substitute(List<String> source, String... replacements) {
		List<String> out = new ArrayList<>(source.size());
		for (String line : source) {
			String replaced = line;
			for (int i = 0; i + 1 < replacements.length; i += 2) {
				replaced = replaced.replace(replacements[i], replacements[i + 1]);
			}
			out.add(replaced);
		}
		return out;
	}

	@Override
	public String handcuffedTitle() {
		return Messages.DETAINMENT_HANDCUFFED_TITLE.toString();
	}

	@Override
	public String handcuffedSubtitle() {
		return Messages.DETAINMENT_HANDCUFFED_SUBTITLE.toString();
	}

	@Override
	public String handcuffedActionBar() {
		return Messages.DETAINMENT_HANDCUFFED_ACTION_BAR.toString();
	}

	@Override
	public String jailedTitle() {
		return Messages.DETAINMENT_JAILED_TITLE.toString();
	}

	@Override
	public String jailedSubtitle() {
		return Messages.DETAINMENT_JAILED_SUBTITLE.toString();
	}

	@Override
	public String jailedActionBar() {
		return Messages.DETAINMENT_JAILED_ACTION_BAR.toString();
	}

	@Override
	public String releasedTitle() {
		return Messages.DETAINMENT_RELEASED_TITLE.toString();
	}

	@Override
	public String releasedSubtitle() {
		return Messages.DETAINMENT_RELEASED_SUBTITLE.toString();
	}

	@Override
	public String cuffingTitle() {
		return Messages.DETAINMENT_CUFFING_TITLE.toString();
	}

	@Override
	public String cuffingSubtitle(long secondsRemaining) {
		return Messages.DETAINMENT_CUFFING_SUBTITLE.toString()
		                                           .replace("%seconds%", Long.toString(secondsRemaining));
	}

	@Override
	public String cuffedTitle() {
		return Messages.DETAINMENT_CUFFED_TITLE.toString();
	}

	@Override
	public String cuffedSubtitle() {
		return Messages.DETAINMENT_CUFFED_SUBTITLE.toString();
	}

	@Override
	public String handcuffedRestraintTick() {
		return Messages.DETAINMENT_HANDCUFFED_TICK.toString();
	}

	@Override
	public String jailedRestraintTick() {
		return Messages.DETAINMENT_JAILED_TICK.toString();
	}

	@Override
	public String transitStartingActionBar(long secondsRemaining) {
		return Messages.DETAINMENT_TRANSIT_STARTING_ACTION_BAR.toString()
		                                                      .replace("%seconds%", Long.toString(secondsRemaining));
	}

	@Override
	public String transitCommittedTitle() {
		return Messages.DETAINMENT_TRANSIT_COMMITTED_TITLE.toString();
	}

	@Override
	public String transitCommittedSubtitle() {
		return Messages.DETAINMENT_TRANSIT_COMMITTED_SUBTITLE.toString();
	}

	@Override
	public String handcuffBribeGuiTitle() {
		return Messages.DETAINMENT_HANDCUFF_BRIBE_GUI_TITLE.toString();
	}

	@Override
	public String handcuffBribeButtonLabel(String cost) {
		return Messages.DETAINMENT_HANDCUFF_BRIBE_BUTTON_LABEL.toString().replace("%cost%", cost);
	}

	@Override
	public List<String> handcuffBribeButtonLore(String cost, String balance) {
		return substitute(Messages.DETAINMENT_HANDCUFF_BRIBE_BUTTON_LORE.toStringList(),
		                  "%cost%", cost, "%balance%", balance);
	}

	@Override
	public String handcuffBribeSuccessTitle() {
		return Messages.DETAINMENT_HANDCUFF_BRIBE_SUCCESS_TITLE.toString();
	}

	@Override
	public String handcuffBribeSuccessSubtitle() {
		return Messages.DETAINMENT_HANDCUFF_BRIBE_SUCCESS_SUBTITLE.toString();
	}

	@Override
	public String handcuffBribeInsufficient() {
		return Messages.DETAINMENT_HANDCUFF_BRIBE_INSUFFICIENT.toString();
	}

	@Override
	public String paperworkItemName() {
		return Messages.DETAINMENT_PAPERWORK_ITEM_NAME.toString();
	}

	@Override
	public List<String> paperworkItemLore() {
		return new ArrayList<>(Messages.DETAINMENT_PAPERWORK_ITEM_LORE.toStringList());
	}

	@Override
	public String paperworkGuiTitle() {
		return Messages.DETAINMENT_PAPERWORK_GUI_TITLE.toString();
	}

	@Override
	public String paperworkBailLabel(String cost) {
		return Messages.DETAINMENT_PAPERWORK_BAIL_LABEL.toString().replace("%cost%", cost);
	}

	@Override
	public List<String> paperworkBailLore(String cost, String balance) {
		return substitute(Messages.DETAINMENT_PAPERWORK_BAIL_LORE.toStringList(),
		                  "%cost%", cost, "%balance%", balance);
	}

	@Override
	public String paperworkBribeLabel(String cost) {
		return Messages.DETAINMENT_PAPERWORK_BRIBE_LABEL.toString().replace("%cost%", cost);
	}

	@Override
	public List<String> paperworkBribeLore(String cost, String chancePercent) {
		return substitute(Messages.DETAINMENT_PAPERWORK_BRIBE_LORE.toStringList(),
		                  "%cost%", cost, "%chance%", chancePercent);
	}

	@Override
	public String paperworkSentenceLabel() {
		return Messages.DETAINMENT_PAPERWORK_SENTENCE_LABEL.toString();
	}

	@Override
	public List<String> paperworkSentenceLore(long secondsRemaining) {
		return substitute(Messages.DETAINMENT_PAPERWORK_SENTENCE_LORE.toStringList(),
		                  "%seconds%", Long.toString(secondsRemaining));
	}

	@Override
	public String paperworkInfoLabel() {
		return Messages.DETAINMENT_PAPERWORK_INFO_LABEL.toString();
	}

	@Override
	public List<String> paperworkInfoLore(int wantedAtArrest, long secondsRemaining, String balance) {
		return substitute(Messages.DETAINMENT_PAPERWORK_INFO_LORE.toStringList(),
		                  "%wanted%", Integer.toString(wantedAtArrest),
		                  "%seconds%", Long.toString(secondsRemaining),
		                  "%balance%", balance);
	}

	@Override
	public String bailSuccess() {
		return Messages.DETAINMENT_BAIL_SUCCESS.toString();
	}

	@Override
	public String bailInsufficient() {
		return Messages.DETAINMENT_BAIL_INSUFFICIENT.toString();
	}

	@Override
	public String jailBribeSuccess() {
		return Messages.DETAINMENT_JAIL_BRIBE_SUCCESS.toString();
	}

	@Override
	public String jailBribeFail(long extraSeconds) {
		return Messages.DETAINMENT_JAIL_BRIBE_FAIL.toString().replace("%seconds%", Long.toString(extraSeconds));
	}

	@Override
	public String jailBribeInsufficient() {
		return Messages.DETAINMENT_JAIL_BRIBE_INSUFFICIENT.toString();
	}

	@Override
	public String sentenceTickActionBar(long secondsRemaining) {
		return Messages.DETAINMENT_SENTENCE_TICK_ACTION_BAR.toString()
		                                                   .replace("%seconds%", Long.toString(secondsRemaining));
	}

	@Override
	public String sentenceCompleteTitle() {
		return Messages.DETAINMENT_SENTENCE_COMPLETE_TITLE.toString();
	}

	@Override
	public String sentenceCompleteSubtitle() {
		return Messages.DETAINMENT_SENTENCE_COMPLETE_SUBTITLE.toString();
	}

	@Override
	public String breakFreeProgressActionBar(int current, int required) {
		return Messages.DETAINMENT_BREAK_FREE_PROGRESS.toString()
		                                              .replace("%current%", Integer.toString(current))
		                                              .replace("%required%", Integer.toString(required));
	}

	@Override
	public String breakFreeSuccessTitle() {
		return Messages.DETAINMENT_BREAK_FREE_SUCCESS_TITLE.toString();
	}

	@Override
	public String breakFreeSuccessSubtitle() {
		return Messages.DETAINMENT_BREAK_FREE_SUCCESS_SUBTITLE.toString();
	}
}
