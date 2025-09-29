package me.luckyraven.database.tables;

import me.luckyraven.bukkit.sign.Sign;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class SignTable extends Table<Sign> {

	public SignTable() {
		super("sign");

		Attribute<Integer> id           = new Attribute<>("id", true, Integer.class);
		Attribute<String>  signType     = new Attribute<>("signType", false, String.class);
		Attribute<String>  world        = new Attribute<>("world", false, String.class);
		Attribute<Double>  x            = new Attribute<>("x", false, Double.class);
		Attribute<Double>  y            = new Attribute<>("y", false, Double.class);
		Attribute<Double>  z            = new Attribute<>("z", false, Double.class);
		Attribute<Long>    lastTimeUsed = new Attribute<>("lastTimeUsed", false, Long.class);

		x.setDefaultValue(0D);
		y.setDefaultValue(0D);
		z.setDefaultValue(0D);
		lastTimeUsed.setDefaultValue(-1L);

		this.addAttribute(id);
		this.addAttribute(signType);
		this.addAttribute(world);
		this.addAttribute(x);
		this.addAttribute(y);
		this.addAttribute(z);
	}

	@Override
	public Object[] getData(Sign data) {
		return new Object[]{data.getUsedId(), data.getSignType(), data.getWorld(), data.getX(), data.getY(),
							data.getZ(), data.getLastTimeUsed()};
	}

	@Override
	public Map<String, Object> searchCriteria(Sign data) {
		return createSearchCriteria("id = ?", new Object[]{data.getUsedId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
