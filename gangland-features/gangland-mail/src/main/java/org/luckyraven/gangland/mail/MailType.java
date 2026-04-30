package org.luckyraven.gangland.mail;

/**
 * Categorizes a {@link MailItem}. New types can be added without touching the persistence layer — the type is stored by
 * the enum name. Code that handles a specific type does so by switching on the value.
 */
public enum MailType {

	/**
	 * A gang has invited a player to join. Sender is identified by {@code senderGangId}; recipient by
	 * {@code recipientUuid}.
	 */
	GANG_INVITE,

	/**
	 * A gang has requested an alliance with another gang. Sender and recipient are both gang ids.
	 */
	GANG_ALLY_REQUEST,

	/**
	 * A free-form message. Sender / recipient may be either a player or a gang.
	 */
	GENERIC_MESSAGE

}
