package me.luckyraven.database.tables;

import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;

import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GangTable extends Table<Gang> {

	public GangTable() {
		super("gang");

		Attribute<Integer> id          = new Attribute<>("id", true);
		Attribute<String>  name        = new Attribute<>("name", false);
		Attribute<String>  displayName = new Attribute<>("display_name", false);
		Attribute<String>  description = new Attribute<>("description", false);
		Attribute<String>  color       = new Attribute<>("color", false);
		Attribute<Double>  balance     = new Attribute<>("balance", false);
		Attribute<Integer> level       = new Attribute<>("level", false);
		Attribute<Double>  experience  = new Attribute<>("experience", false);
		Attribute<Double>  bounty      = new Attribute<>("bounty", false);
		Attribute<Long>    created     = new Attribute<>("created", false);

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
		Map<String, Object> search = new HashMap<>();

		search.put("search", "id = ?");
		search.put("info", new Object[]{data.getId()});
		search.put("type", new int[]{Types.INTEGER});
		search.put("index", new int[]{0});

		return Collections.unmodifiableMap(search);
	}
}
