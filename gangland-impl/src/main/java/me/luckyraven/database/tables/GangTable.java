package me.luckyraven.database.tables;

import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class GangTable extends Table<Gang> {

	public GangTable() {
		super("gang");

		Attribute<Integer> id          = new Attribute<>("id", true, Integer.class);
		Attribute<String>  name        = new Attribute<>("name", false, String.class);
		Attribute<String>  displayName = new Attribute<>("display_name", false, String.class);
		Attribute<String>  description = new Attribute<>("description", false, String.class);
		Attribute<String>  color       = new Attribute<>("color", false, String.class);
		Attribute<Double>  balance     = new Attribute<>("balance", false, Double.class);
		Attribute<Integer> level       = new Attribute<>("level", false, Integer.class);
		Attribute<Double>  experience  = new Attribute<>("experience", false, Double.class);
		Attribute<Double>  bounty      = new Attribute<>("bounty", false, Double.class);
		Attribute<Long>    created     = new Attribute<>("created", false, Long.class);

		balance.setDefaultValue(0D);
		level.setDefaultValue(0);
		experience.setDefaultValue(0D);
		bounty.setDefaultValue(0D);

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
	}

	@Override
	public Object[] getData(Gang data) {
		return new Object[]{data.getId(), data.getName(), data.getDisplayName(), data.getDescription(), data.getColor(),
							data.getEconomy().getBalance(), data.getLevel().getLevelValue(),
							data.getLevel().getExperience(), data.getBounty().getAmount(), data.getCreated()};
	}

	@Override
	public Map<String, Object> searchCriteria(Gang data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
