package org.luckyraven.gangland.gang.database.tables.gang;

import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class GangTable extends Table<Gang> {

	public GangTable() {
		super("gang");

		Attribute<Integer> id                 = new Attribute<>("id", true, Integer.class);
		Attribute<String>  name               = new Attribute<>("name", false, String.class);
		Attribute<String>  displayName        = new Attribute<>("display_name", false, String.class);
		Attribute<String>  description        = new Attribute<>("description", false, String.class);
		Attribute<String>  color              = new Attribute<>("color", false, String.class);
		Attribute<Double>  balance            = new Attribute<>("balance", false, Double.class);
		Attribute<Integer> level              = new Attribute<>("level", false, Integer.class);
		Attribute<Double>  experience         = new Attribute<>("experience", false, Double.class);
		Attribute<Double>  bounty             = new Attribute<>("bounty", false, Double.class);
		Attribute<Long>    created            = new Attribute<>("created", false, Long.class);
		Attribute<Long>    lastMemberOnlineAt = new Attribute<>("last_member_online_at", false, Long.class);
		Attribute<String>  state              = new Attribute<>("state", false, String.class);
		Attribute<String>  notice             = new Attribute<>("notice", false, String.class);
		Attribute<Boolean> friendlyFire       = new Attribute<>("friendly_fire", false, Boolean.class);

		balance.setDefaultValue(0D);
		level.setDefaultValue(0);
		experience.setDefaultValue(0D);
		bounty.setDefaultValue(0D);
		state.setDefaultValue(Gang.State.INVITE.name());
		lastMemberOnlineAt.setDefaultValue(0L);
		friendlyFire.setDefaultValue(false);
		// no notice by default; a NULL column also lets the diff engine add it to a table that already has rows
		notice.setCanBeNull(true);

		this.addAttribute(id);
		this.addAttribute(name);
		this.addAttribute(displayName);
		this.addAttribute(description);
		this.addAttribute(color);
		this.addAttribute(balance);
		this.addAttribute(level);
		this.addAttribute(experience);
		this.addAttribute(bounty);
		this.addAttribute(created);
		this.addAttribute(lastMemberOnlineAt);
		this.addAttribute(state);
		this.addAttribute(notice);
		this.addAttribute(friendlyFire);
	}

	@Override
	public Object[] getData(Gang data) {
		return new Object[]{data.getId(), data.getName(), data.getDisplayName(), data.getDescription(), data.getColor(),
		                    data.getEconomy().getAmount().doubleValue(), data.getLevel().getLevelValue(),
		                    data.getLevel().getExperience(), data.getBounty().getAmount().doubleValue(),
		                    data.getCreated(), data.getLastMemberOnlineAt(), data.getState().name(),
		                    data.getNotice(), data.isFriendlyFire()};
	}

	@Override
	public Map<String, Object> searchCriteria(Gang data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
