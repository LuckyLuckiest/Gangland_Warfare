package org.luckyraven.gangland.database.tables.turf;

import org.luckyraven.gangland.persistence.database.component.Attribute;
import org.luckyraven.gangland.persistence.database.component.Table;
import org.luckyraven.gangland.turf.data.Turf;

import java.sql.Types;
import java.util.Map;

public class TurfTable extends Table<Turf> {

	public TurfTable() {
		super("turf");

		Attribute<Integer> id                   = new Attribute<>("id", true, Integer.class);
		Attribute<String>  displayName          = new Attribute<>("display_name", false, String.class);
		Attribute<String>  world                = new Attribute<>("world", false, String.class);
		Attribute<Integer> minX                 = new Attribute<>("min_x", false, Integer.class);
		Attribute<Integer> maxX                 = new Attribute<>("max_x", false, Integer.class);
		Attribute<Integer> minZ                 = new Attribute<>("min_z", false, Integer.class);
		Attribute<Integer> maxZ                 = new Attribute<>("max_z", false, Integer.class);
		Attribute<Integer> ownerGangId          = new Attribute<>("owner_gang_id", false, Integer.class);
		Attribute<Double>  incomeAmount         = new Attribute<>("income_amount", false, Double.class);
		Attribute<Long>    createdAt            = new Attribute<>("created_at", false, Long.class);
		Attribute<Long>    lastCaptureTimestamp = new Attribute<>("last_capture_timestamp", false, Long.class);

		incomeAmount.setDefaultValue(0D);
		lastCaptureTimestamp.setDefaultValue(0L);
		// Unclaimed turfs persist with owner_gang_id = NULL; allow that at the SQL level.
		ownerGangId.setCanBeNull(true);

		this.addAttribute(id);
		this.addAttribute(displayName);
		this.addAttribute(world);
		this.addAttribute(minX);
		this.addAttribute(maxX);
		this.addAttribute(minZ);
		this.addAttribute(maxZ);
		this.addAttribute(ownerGangId);
		this.addAttribute(incomeAmount);
		this.addAttribute(createdAt);
		this.addAttribute(lastCaptureTimestamp);
	}

	@Override
	public Object[] getData(Turf data) {
		return new Object[]{
				data.getId(),
				data.getDisplayName(),
				data.getRegion().getWorld(),
				data.getRegion().getMinX(),
				data.getRegion().getMaxX(),
				data.getRegion().getMinZ(),
				data.getRegion().getMaxZ(),
				data.getOwnerGangId(),
				data.getIncomeAmount() == null ? 0D : data.getIncomeAmount().doubleValue(),
				data.getCreatedAt(),
				data.getLastCaptureTimestamp()
		};
	}

	@Override
	public Map<String, Object> searchCriteria(Turf data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
