package org.luckyraven.gangland.mail.database;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailStatus;
import org.luckyraven.gangland.mail.MailType;
import org.luckyraven.gangland.mail.contract.MailRepositoryContract;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@CustomLog
@Repository(MailItem.class)
public class MailRepository extends AbstractRepository<MailItem> implements MailRepositoryContract {

	private final MailTable mailTable;

	public MailRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
		this.mailTable = new MailTable();
	}

	@Override
	protected Collection<MailItem> doLoadAll() throws SQLException {
		return fromRows(tableBackend().selectAll());
	}

	/**
	 * Decodes every row. A row that cannot be read (an unknown type or status, a null column) is skipped with a
	 * warning, so one corrupt row does not abort the whole mail load (GR-31).
	 */
	static List<MailItem> fromRows(List<Object[]> rows) {
		List<MailItem> mails = new ArrayList<>();
		for (int index = 0; index < rows.size(); index++) {
			try {
				mails.add(fromRow(rows.get(index)));
			} catch (IllegalArgumentException | ClassCastException | NullPointerException |
			         ArrayIndexOutOfBoundsException exception) {
				log.warn("Skipping unreadable mail row {}: {}", index, exception.getMessage());
			}
		}
		return mails;
	}

	static MailItem fromRow(Object[] result) {
		int v = 0;

		long       id              = ((Number) result[v++]).longValue();
		MailType   type            = MailType.valueOf(String.valueOf(result[v++]));
		MailStatus status          = MailStatus.valueOf(String.valueOf(result[v++]));
		UUID       senderUuid      = MailTable.parseUuid(result[v++]);
		int        senderGangId    = ((Number) result[v++]).intValue();
		UUID       recipientUuid   = MailTable.parseUuid(result[v++]);
		int        recipientGangId = ((Number) result[v++]).intValue();
		Object     subjectRaw      = result[v++];
		String     subject         = subjectRaw == null ? null : subjectRaw.toString();
		long       createdAt       = ((Number) result[v++]).longValue();
		long       expiresAt       = ((Number) result[v++]).longValue();
		long       pausedAt        = ((Number) result[v++]).longValue();
		int        readFlag        = ((Number) result[v]).intValue();

		MailItem mail = new MailItem(id, type, senderUuid, senderGangId, recipientUuid, recipientGangId, subject,
		                             createdAt, expiresAt, status, readFlag != 0);
		mail.setPausedAt(pausedAt);
		return mail;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<MailItem> getTable() {
		return mailTable;
	}

	@Override
	protected void doDelete(MailItem data) throws SQLException {
		tableBackend().delete("id = ?", data.getId());
	}
}
