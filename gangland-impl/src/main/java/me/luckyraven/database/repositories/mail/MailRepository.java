package me.luckyraven.database.repositories.mail;

import me.luckyraven.database.tables.mail.MailTable;
import me.luckyraven.mail.MailItem;
import me.luckyraven.mail.MailStatus;
import me.luckyraven.mail.MailType;
import me.luckyraven.mail.contract.MailRepositoryContract;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Repository(MailItem.class)
public class MailRepository extends AbstractRepository<MailItem> implements MailRepositoryContract {

	private final MailTable mailTable;

	public MailRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);
		this.mailTable = new MailTable();
	}

	@Override
	protected Collection<MailItem> doLoadAll() throws SQLException {
		List<MailItem> mails = new ArrayList<>();
		List<Object[]> rows  = mailTable.selectAllTableQuery(getDatabase());

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
			int        readFlag        = ((Number) result[v]).intValue();

			mails.add(new MailItem(id, type, senderUuid, senderGangId, recipientUuid, recipientGangId, subject,
			                       createdAt, expiresAt, status, readFlag != 0));
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
		Database table = getDatabase().table(mailTable.getName());
		table.delete("id", data.getId(), Types.BIGINT);
	}
}
