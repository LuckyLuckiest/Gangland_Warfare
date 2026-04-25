package me.luckyraven.mail;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * One row of mail. Persistent record of a request, invitation, or message that may need to outlive the sender's session
 * (e.g., a gang invite to an offline player, or an ally request to a gang whose members are all offline).
 *
 * <p>Either side of the mail can be a player ({@code uuid} field) or a gang ({@code gangId} field). The
 * {@link MailType} implies which fields are populated:
 * <ul>
 *     <li>{@link MailType#GANG_INVITE} — sender is a gang ({@code senderGangId}), recipient is a player
 *         ({@code recipientUuid}).</li>
 *     <li>{@link MailType#GANG_ALLY_REQUEST} — sender and recipient are both gangs.</li>
 *     <li>{@link MailType#GENERIC_MESSAGE} — either side may be a player or a gang.</li>
 * </ul>
 *
 * <p>Unset references use {@code null} for UUIDs and {@code -1} for gang ids.
 */
@Getter
public final class MailItem {

	public static final int NO_GANG = -1;

	private final long     id;
	private final MailType type;

	private final UUID senderUuid;
	private final int  senderGangId;

	private final UUID recipientUuid;
	private final int  recipientGangId;

	private final String subject;

	private final long createdAt;
	private final long expiresAt;

	@Setter
	private MailStatus status;

	@Setter
	private boolean read;

	public MailItem(long id, MailType type,
	                UUID senderUuid, int senderGangId,
	                UUID recipientUuid, int recipientGangId,
	                String subject,
	                long createdAt, long expiresAt,
	                MailStatus status, boolean read) {
		this.id              = id;
		this.type            = type;
		this.senderUuid      = senderUuid;
		this.senderGangId    = senderGangId;
		this.recipientUuid   = recipientUuid;
		this.recipientGangId = recipientGangId;
		this.subject         = subject;
		this.createdAt       = createdAt;
		this.expiresAt       = expiresAt;
		this.status          = status;
		this.read            = read;
	}

	/**
	 * @return {@code true} if {@link #expiresAt} is set and {@code System.currentTimeMillis()} is past it.
	 */
	public boolean isExpired() {
		return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
	}

}
