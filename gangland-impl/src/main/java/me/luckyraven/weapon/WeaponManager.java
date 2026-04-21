package me.luckyraven.weapon;

import me.luckyraven.core.autowire.bean.BeanLifecycle;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.repositories.weapon.WeaponRepository;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.weapon.configuration.WeaponAddon;

import java.util.Collection;

public class WeaponManager extends WeaponService implements BeanLifecycle {

	private final WeaponAddon      weaponAddon;
	private final GanglandDatabase database;

	public WeaponManager(WeaponAddon weaponAddon, GanglandDatabase database) {
		super(weaponAddon);
		this.weaponAddon = weaponAddon;
		this.database    = database;
	}

	public void initialize() {
		IRepository<Weapon> repository = database.getRepositoryRegistry().getRepository(Weapon.class);

		if (repository instanceof WeaponRepository weaponRepository) {
			weaponRepository.setWeaponAddon(weaponAddon);
		}

		Collection<Weapon> loaded = repository.loadAll();

		for (Weapon weapon : loaded) {
			getWeapons().put(weapon.getUuid(), weapon);
		}

		repository.setDataSupplier(() -> getWeapons().values());
	}

	@Override
	public void onClear() {
		clear();
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		initialize();
	}

}
