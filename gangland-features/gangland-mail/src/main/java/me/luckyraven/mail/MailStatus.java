package me.luckyraven.mail;

/**
 * Lifecycle state of a {@link MailItem}.
 *
 * <p>Once a mail leaves {@code PENDING} it is typically deleted from storage; the terminal values exist for in-flight
 * code that needs to dispatch on the outcome before the row is removed.
 */
public enum MailStatus {

	PENDING,
	ACCEPTED,
	REJECTED,
	CANCELLED,
	EXPIRED,
	READ

}
