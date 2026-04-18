package me.luckyraven.copsncrooks.detainment.message;

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

}
