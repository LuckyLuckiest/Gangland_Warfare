package org.luckyraven.gangland.database.repositories.mail;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.database.tables.mail.MailTable;
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

@Repository(MailItem.class)
public class MailRepository extends AbstractRepository<MailItem> implements MailRepositoryContract {

	private final MailTable mailTable;

	public MailRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
		this.mailTable = new MailTable();
	}

	@Override
	protected Collection<MailItem> doLoadAll() throws SQLException {
		List<MailItem> mails = new ArrayList<>();
		List<Object[]> rows  = tableBackend().selectAll();

		for (Object[] result : rows) {
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
			mails.add(mail);
		}

		return mails;
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
