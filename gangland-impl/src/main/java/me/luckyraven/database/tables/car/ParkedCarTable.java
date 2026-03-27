package me.luckyraven.database.tables.car;

import me.luckyraven.gadget.car.ParkedCar;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class ParkedCarTable extends Table<ParkedCar> {

	public ParkedCarTable() {
		super("parked_car");

		Attribute<String>  id         = new Attribute<>("id", true, String.class);
		Attribute<String>  carId      = new Attribute<>("car_id", false, String.class);
		Attribute<String>  world      = new Attribute<>("world", false, String.class);
		Attribute<Double>  x          = new Attribute<>("x", false, Double.class);
		Attribute<Double>  y          = new Attribute<>("y", false, Double.class);
		Attribute<Double>  z          = new Attribute<>("z", false, Double.class);
		Attribute<Float>   yaw        = new Attribute<>("yaw", false, Float.class);
		Attribute<Integer> fuel       = new Attribute<>("fuel", false, Integer.class);
		Attribute<Integer> durability = new Attribute<>("durability", false, Integer.class);
		Attribute<String>  placerUuid = new Attribute<>("placer_uuid", false, String.class);

		x.setDefaultValue(0D);
		y.setDefaultValue(0D);
		z.setDefaultValue(0D);
		yaw.setDefaultValue(0F);
		fuel.setDefaultValue(0);
		durability.setDefaultValue(0);
		placerUuid.setCanBeNull(true);

		this.addAttribute(id);
		this.addAttribute(carId);
		this.addAttribute(world);
		this.addAttribute(x);
		this.addAttribute(y);
		this.addAttribute(z);
		this.addAttribute(yaw);
		this.addAttribute(fuel);
		this.addAttribute(durability);
		this.addAttribute(placerUuid);
	}

	@Override
	public Object[] getData(ParkedCar data) {
		return new Object[]{data.getDbId(), data.getCarId(), data.getWorld(), data.getX(), data.getY(), data.getZ(),
		                    (double) data.getYaw(), data.getFuel(), data.getDurability(),
		                    data.getPlacerUUID() != null ? data.getPlacerUUID().toString() : null};
	}

	@Override
	public Map<String, Object> searchCriteria(ParkedCar data) {
		return createSearchCriteria("id = ?", new Object[]{data.getDbId()}, new int[]{Types.VARCHAR}, new int[]{0});
	}
}
