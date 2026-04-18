package me.luckyraven.file.configuration.copsncrooks;

import me.luckyraven.copsncrooks.detainment.message.DetainmentMessageContract;
import me.luckyraven.file.configuration.Messages;

/**
 * Default {@link DetainmentMessageContract} implementation. Routes every call through a {@link Messages} enum key so
 * cops-n-crooks detainment code stays decoupled from the Messages enum.
 */
public final class GanglandDetainmentMessages implements DetainmentMessageContract {

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

}
