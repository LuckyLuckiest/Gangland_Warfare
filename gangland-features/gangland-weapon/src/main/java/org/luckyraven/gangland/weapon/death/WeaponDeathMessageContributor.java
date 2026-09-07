package org.luckyraven.gangland.weapon.death;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.listener.death.DeathMessageContributor;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponManager;
import org.luckyraven.gangland.weapon.types.throwable.ThrowableAction;

/**
 * The weapon-kill half of the core's death-message builder, moved out of {@code PlayerDeathListener} when the
 * death-message seam was introduced. Skeleton created in T15; body (today's
 * {@code ThrowableAction.pendingKillerWeapon} / {@code weaponManager.getWeaponTemplate} /
 * {@code validateAndGetWeapon} logic) filled in T17.
 */
public final class WeaponDeathMessageContributor implements DeathMessageContributor {

	private final WeaponManager weaponManager;

	public WeaponDeathMessageContributor(WeaponManager weaponManager) {
		this.weaponManager = weaponManager;
	}

	@Override
	@Nullable
	public Resolved resolve(Player victim, Player killer) {
		// check if a throwable weapon was responsible (killer may have switched items since throwing)
		String throwableName = ThrowableAction.pendingKillerWeapon.remove(victim.getUniqueId());

		Weapon weapon;
		if (throwableName != null) {
			weapon = weaponManager.getWeaponTemplate(throwableName);
		} else {
			ItemStack heldItem = killer.getInventory().getItemInMainHand();
			weapon = weaponManager.validateAndGetWeapon(killer, heldItem);
		}

		if (weapon == null) {
			// use the throwable's name if we at least know which weapon it was
			return throwableName != null ? new Resolved(null, throwableName) : null;
		}

		return new Resolved(weapon.pickDeathMessage().orElse(null), weapon.getDisplayName());
	}
}
