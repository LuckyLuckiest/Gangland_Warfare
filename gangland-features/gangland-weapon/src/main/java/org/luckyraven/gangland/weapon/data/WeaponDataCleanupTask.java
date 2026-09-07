package org.luckyraven.gangland.weapon.data;

import lombok.CustomLog;
import org.luckyraven.gangland.data.plugin.DataCleanupTask;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponManager;
import org.luckyraven.gangland.weapon.database.WeaponRepository;
import org.luckyraven.keystone.persistence.repository.IRepository;

/**
 * Clears the weapon table and the live {@link WeaponManager} cache on the scheduled data-cleanup pass. Today's
 * {@code PluginDataCleanupService.resetWeapons()} body verbatim, including the {@code instanceof WeaponRepository}
 * guard — the repository parameter is a plain {@link IRepository} so a test double that is not a
 * {@code WeaponRepository} still exercises the manager-clear path without throwing.
 */
@CustomLog
public final class WeaponDataCleanupTask implements DataCleanupTask {

	private final boolean logDebug = Settings.isAutoSaveDebug();

	private final WeaponManager       weaponManager;
	private final IRepository<Weapon> weaponRepository;

	public WeaponDataCleanupTask(WeaponManager weaponManager, IRepository<Weapon> weaponRepository) {
		this.weaponManager    = weaponManager;
		this.weaponRepository = weaponRepository;
	}

	@Override
	public String name() {
		return "weapons";
	}

	@Override
	public int cleanup() {
		int count = weaponManager.getWeapons().size();

		if (weaponRepository instanceof WeaponRepository repo) {
			repo.deleteAll();
		}

		weaponManager.clear();

		if (logDebug) log.info("Cleared {} weapons from weapon table", count);
		return count;
	}
}
