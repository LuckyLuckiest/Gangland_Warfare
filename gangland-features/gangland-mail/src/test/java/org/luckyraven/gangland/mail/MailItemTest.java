package org.luckyraven.gangland.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins {@link MailItem#isExpired()} / {@link MailItem#isPaused()}, per the gangs-ranks-mail.md Test Surface bullet
 * "MailItem.isExpired() / isPaused() truth table including expiresAt == 0 (never expires) and the
 * paused-overrides-deadline rule".
 */
@DisplayName("MailItem - expiry and pause truth table")
class MailItemTest {

	private MailItem newMail(long expiresAt) {
		return new MailItem(1L, MailType.GANG_INVITE,
				UUID.randomUUID(), MailItem.NO_GANG,
				UUID.randomUUID(), MailItem.NO_GANG,
				null,
				System.currentTimeMillis(), expiresAt,
				MailStatus.PENDING, false);
	}

	@Test
	@DisplayName("expiresAt == 0 means the mail never expires (offline gang invites, per gangs-ranks-mail.md W3)")
	void isExpired_falseWhenExpiresAtIsZero() {
		MailItem mail = newMail(0L);

		assertFalse(mail.isExpired());
	}

	@Test
	void isExpired_falseWhenDeadlineIsInTheFuture() {
		MailItem mail = newMail(System.currentTimeMillis() + 60_000L);

		assertFalse(mail.isExpired());
	}

	@Test
	void isExpired_trueOnceDeadlineHasPassed() {
		MailItem mail = newMail(System.currentTimeMillis() - 1L);

		assertTrue(mail.isExpired());
	}

	@Test
	@DisplayName("paused overrides the deadline: isExpired() is false even with a deadline far in the past")
	void isExpired_falseWhenPaused_evenPastDeadline() {
		MailItem mail = newMail(System.currentTimeMillis() - 100_000L);
		mail.setPausedAt(System.currentTimeMillis());

		assertTrue(mail.isPaused());
		assertFalse(mail.isExpired(), "isPaused() short-circuits isExpired() to false regardless of the deadline");
	}

	@Test
	void isPaused_falseByDefault_trueOncePausedAtIsSet() {
		MailItem mail = newMail(0L);

		assertFalse(mail.isPaused());

		mail.setPausedAt(System.currentTimeMillis());
		assertTrue(mail.isPaused());

		mail.setPausedAt(0L);
		assertFalse(mail.isPaused(), "resuming clears pausedAt back to 0");
	}

}
