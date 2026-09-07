package org.luckyraven.gangland.database.tables.turf;

import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.gangland.turf.powerups.ActiveTurfBuff;

import java.sql.Types;
import java.util.Map;

public class ActiveTurfBuffTable extends Table<ActiveTurfBuff> {

	public ActiveTurfBuffTable() {
		super("turf_active_buff");

		Attribute<Long>    id         = new Attribute<>("id", true, Long.class);
		Attribute<Integer> turfId     = new Attribute<>("turf_id", false, Integer.class);
		Attribute<String>  powerupId  = new Attribute<>("powerup_id", false, 64, String.class);
		Attribute<String>  effectType = new Attribute<>("effect_type", false, 32, String.class);
		Attribute<Double>  magnitude  = new Attribute<>("magnitude", false, Double.class);
		Attribute<Long>    expiresAt  = new Attribute<>("expires_at", false, Long.class);

		this.addAttribute(id);
		this.addAttribute(turfId);
		this.addAttribute(powerupId);
		this.addAttribute(effectType);
		this.addAttribute(magnitude);
		this.addAttribute(expiresAt);
	}

	@Override
	public Object[] getData(ActiveTurfBuff data) {
		return new Object[]{
				data.getId(),
				data.getTurfId(),
				data.getPowerupId(),
				data.getEffectType().name(),
				data.getMagnitude(),
				data.getExpiresAt()
		};
	}

	@Override
	public Map<String, Object> searchCriteria(ActiveTurfBuff data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId()},
		                            new int[]{Types.BIGINT}, new int[]{0});
	}
}
