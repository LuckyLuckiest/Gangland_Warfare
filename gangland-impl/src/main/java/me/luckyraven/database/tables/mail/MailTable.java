package me.luckyraven.database.tables.mail;

import me.luckyraven.mail.MailItem;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;
import java.util.UUID;

public class MailTable extends Table<MailItem> {

	public MailTable() {
		super("mail");

		Attribute<Long>    id              = new Attribute<>("id", true, Long.class);
		Attribute<String>  type            = new Attribute<>("type", false, String.class);
		Attribute<String>  status          = new Attribute<>("status", false, String.class);
		Attribute<String>  senderUuid      = new Attribute<>("sender_uuid", false, String.class);
		Attribute<Integer> senderGangId    = new Attribute<>("sender_gang_id", false, Integer.class);
		Attribute<String>  recipientUuid   = new Attribute<>("recipient_uuid", false, String.class);
		Attribute<Integer> recipientGangId = new Attribute<>("recipient_gang_id", false, Integer.class);
		Attribute<String>  subject         = new Attribute<>("subject", false, String.class);
		Attribute<Long>    createdAt       = new Attribute<>("created_at", false, Long.class);
		Attribute<Long>    expiresAt       = new Attribute<>("expires_at", false, Long.class);
		Attribute<Long>    pausedAt        = new Attribute<>("paused_at", false, Long.class);
		Attribute<Integer> readFlag        = new Attribute<>("read_flag", false, Integer.class);

		// Sized tighter than the default 255 — UUID strings are exactly 36 chars.
		// (Attribute auto-sizes UUID.class to 36 but we're using String.class here so set it explicitly.)
		senderUuid.setCanBeNull(true);
		recipientUuid.setCanBeNull(true);
		subject.setCanBeNull(true);

		senderGangId.setDefaultValue(MailItem.NO_GANG);
		recipientGangId.setDefaultValue(MailItem.NO_GANG);
		expiresAt.setDefaultValue(-1L);
		pausedAt.setDefaultValue(0L);
		readFlag.setDefaultValue(0);

		this.addAttribute(id);
		this.addAttribute(type);
		this.addAttribute(status);
		this.addAttribute(senderUuid);
		this.addAttribute(senderGangId);
		this.addAttribute(recipientUuid);
		this.addAttribute(recipientGangId);
		this.addAttribute(subject);
		this.addAttribute(createdAt);
		this.addAttribute(expiresAt);
		this.addAttribute(pausedAt);
		this.addAttribute(readFlag);
	}

	/**
	 * Helper used by the repository's {@code doLoadAll} — converts the nullable {@code sender_uuid} /
	 * {@code recipient_uuid} columns back into {@link UUID} or {@code null}.
	 */
	public static UUID parseUuid(Object value) {
		if (value == null) return null;
		String s = value.toString();
		if (s.isEmpty() || "null".equals(s)) return null;
		return UUID.fromString(s);
	}

	@Override
	public Object[] getData(MailItem data) {
		String senderUuid    = data.getSenderUuid() == null ? null : data.getSenderUuid().toString();
		String recipientUuid = data.getRecipientUuid() == null ? null : data.getRecipientUuid().toString();

		return new Object[]{data.getId(), data.getType().name(), data.getStatus().name(), senderUuid,
		                    data.getSenderGangId(), recipientUuid, data.getRecipientGangId(), data.getSubject(),
		                    data.getCreatedAt(), data.getExpiresAt(), data.getPausedAt(), data.isRead() ? 1 : 0};
	}

	@Override
	public Map<String, Object> searchCriteria(MailItem data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId()}, new int[]{Types.BIGINT}, new int[]{0});
	}

}
