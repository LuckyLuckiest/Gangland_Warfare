package org.luckyraven.gangland.gang.database.fk;

import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.util.Map;
import java.util.UUID;

/**
 * Foreign-key reference stand-in for gangland-impl's real {@code UserTable} (table {@code user}, primary key
 * {@code uuid}). The module cannot import gangland-impl's persistence classes directly (module → impl is
 * forbidden), but {@code MemberTable.uuid} needs a real {@code Table<?>}/{@code Attribute<?>} pair to declare its
 * foreign key against. This reproduces {@code UserTable}'s name and {@code uuid} column exactly (type
 * {@link UUID}, primary key) so the backend's FK DDL is identical to what the real table produces — table names
 * and schema unchanged, no migration (WS5 G1 step 7, C7). Never used for actual persistence:
 * {@link #getData}/{@link #searchCriteria} are unreachable in practice, since {@code MemberRepository} never
 * saves through this reference — only {@code MemberTable}'s constructor reads {@link #get(String)} off it.
 */
public final class ForeignUserTable extends Table<Object> {

	public ForeignUserTable() {
		super("user");

		addAttribute(new Attribute<>("uuid", true, UUID.class));
	}

	@Override
	public Object[] getData(Object data) {
		throw new UnsupportedOperationException("ForeignUserTable is a foreign-key reference only");
	}

	@Override
	public Map<String, Object> searchCriteria(Object data) {
		throw new UnsupportedOperationException("ForeignUserTable is a foreign-key reference only");
	}
}
