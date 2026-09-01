package org.luckyraven.gangland.database.tables.copsncrooks;

import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class DetainmentTable extends Table<DetainedPlayer> {

	public DetainmentTable(JailTable jailTable) {
		super("detainment");

		Attribute<String>  playerUuid        = new Attribute<>("player_uuid", true, String.class);
		Attribute<Integer> jailId            = new Attribute<>("jail_id", false, Integer.class);
		Attribute<String>  state             = new Attribute<>("state", false, String.class);
		Attribute<Long>    transitExpiresAt  = new Attribute<>("transit_expires_at", false, Long.class);
		Attribute<Long>    sentenceExpiresAt = new Attribute<>("sentence_expires_at", false, Long.class);
		Attribute<Integer> wantedAtArrest    = new Attribute<>("wanted_at_arrest", false, Integer.class);

		jailId.setUnique(true);
		jailId.setCanBeNull(true);
		jailId.setForeignKey(jailTable.get("id"), jailTable);

		transitExpiresAt.setCanBeNull(true);
		sentenceExpiresAt.setCanBeNull(true);
		wantedAtArrest.setCanBeNull(true);

		this.addAttribute(playerUuid);
		this.addAttribute(jailId);
		this.addAttribute(state);
		this.addAttribute(transitExpiresAt);
		this.addAttribute(sentenceExpiresAt);
		this.addAttribute(wantedAtArrest);
	}

	@Override
	public Object[] getData(DetainedPlayer data) {
		return new Object[]{data.getPlayerId().toString(), data.getJailId(), data.getState().name(),
		                    data.getTransitExpiresAt(), data.getSentenceExpiresAt(), data.getWantedAtArrest()};
	}

	@Override
	public Map<String, Object> searchCriteria(DetainedPlayer data) {
		return createSearchCriteria("player_uuid = ?", new Object[]{data.getPlayerId().toString()},
		                            new int[]{Types.VARCHAR}, new int[]{0});
	}
}
