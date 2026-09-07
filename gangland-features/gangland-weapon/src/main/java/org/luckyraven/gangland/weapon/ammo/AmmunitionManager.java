package org.luckyraven.gangland.weapon.ammo;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AmmunitionManager implements Comparator<Ammunition> {

	private final Map<String, Ammunition> ammunition = new HashMap<>();

	public void register(String key, Ammunition ammo) {
		ammunition.put(key, ammo);
	}

	public Ammunition getAmmunition(String key) {
		return ammunition.get(key);
	}

	public Set<String> getAmmunitionKeys() {
		return ammunition.keySet();
	}

	public void clear() {
		ammunition.clear();
	}

	@Override
	public int compare(Ammunition a, Ammunition b) {
		return a.compareTo(b);
	}

}
