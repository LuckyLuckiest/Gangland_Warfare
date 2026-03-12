package me.luckyraven.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.repositories.weapon.WeaponRepository;
import me.luckyraven.persistence.repository.IRepository;

import java.util.Collection;

public class WeaponManager extends WeaponService {

	private final Gangland gangland;

	public WeaponManager(Gangland gangland) {
		super(gangland.getInitializer().getWeaponAddon());

		this.gangland = gangland;
	}

	public void initialize() {
		GanglandDatabase    database   = gangland.getInitializer().getGanglandDatabase();
		IRepository<Weapon> repository = database.getRepositoryRegistry().getRepository(Weapon.class);

		if (repository instanceof WeaponRepository weaponRepository) {
			weaponRepository.setWeaponAddon(gangland.getInitializer().getWeaponAddon());
		}

		Collection<Weapon> loaded = repository.loadAll();

		for (Weapon weapon : loaded) {
			getWeapons().put(weapon.getUuid(), weapon);
		}

		repository.setDataSupplier(() -> getWeapons().values());
	}

}
