package me.luckyraven.feature.weapon;

import me.luckyraven.feature.weapon.events.WeaponShootEvent;
import me.luckyraven.feature.weapon.projectile.WeaponProjectile;
import me.luckyraven.file.configuration.SoundConfiguration;
import me.luckyraven.util.timer.Timer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class FullAutoTask extends Timer {

	/**
	 * These values are implemented as a result of not having hardcoded values from FullAutoTask class implemented by
	 * CJCrafter.
	 *
	 * @see <a
	 * 		href="https://github.com/WeaponMechanics/MechanicsMain/blob/master/WeaponMechanics/src/main/java/me/deecaad/weaponmechanics/weapon/shoot/FullAutoTask.java">FullAutoTask
	 * 		class</a>
	 */
	private static final boolean[][] AUTO = new boolean[21][20];

	static {
		for (int shotsPerSecond = 1; shotsPerSecond < AUTO.length; shotsPerSecond++) {
			double    accumulate = 0D;
			boolean[] collection = new boolean[AUTO[shotsPerSecond].length];

			for (int i = 0; i < AUTO[shotsPerSecond].length; i++) {
				accumulate += shotsPerSecond / 20.0 + 0.00000000001;

				if (accumulate >= 1.0) {
					accumulate -= 1.0;
					collection[i] = true;
				}
			}

			while (!collection[0]) {
				rotateArray(collection);
			}

			System.arraycopy(collection, 0, AUTO[shotsPerSecond], 0, AUTO[shotsPerSecond].length);
		}
	}

	private final JavaPlugin plugin;
	private final Weapon     weapon;
	private final Player     player;
	private final ItemStack  itemStack;
	private final Runnable   onCancel;
	private       int        tickIndex;

	public FullAutoTask(JavaPlugin plugin, Weapon weapon, Player player, ItemStack weaponItem, Runnable onCancel) {
		super(plugin);

		this.plugin    = plugin;
		this.weapon    = weapon;
		this.player    = player;
		this.itemStack = weaponItem;
		this.tickIndex = 0;
		this.onCancel  = onCancel;
	}

	private static void rotateArray(boolean[] array) {
		boolean first = array[0];

		System.arraycopy(array, 1, array, 0, array.length - 1);
		array[array.length - 1] = first;
	}

	@Override
	public void run() {
		if (!itemStack.hasItemMeta() || weapon.isReloading()) {
			cancel();
			return;
		}

		int shotsPerSecond = Math.max(1, Math.min(20, 20 / Math.max(1, weapon.getProjectileCooldown())));

		if (AUTO[shotsPerSecond][tickIndex]) {
			// Try to consume a bullet
			if (!weapon.consumeShot()) {
				SoundConfiguration.playSounds(player, weapon.getEmptyMagCustomSound(),
											  weapon.getEmptyMagDefaultSound());
				cancel();
				return;
			}

			WeaponProjectile<?> weaponProjectile = weapon.getProjectileType().createInstance(plugin, player, weapon);
			WeaponShootEvent    weaponShootEvent = new WeaponShootEvent(weapon, weaponProjectile);
			plugin.getServer().getPluginManager().callEvent(weaponShootEvent);

			if (!weaponShootEvent.isCancelled()) {
				weaponProjectile.launchProjectile();
				SoundConfiguration.playSounds(player, weapon.getShotCustomSound(), weapon.getShotDefaultSound());
			} else {
				weapon.addAmmunition(1);
			}

			// Reload if empty
			if (weapon.getCurrentMagCapacity() == 0) {
				weapon.reload(plugin, player, true);
				cancel();
				return;
			}
		}

		// Advance tick
		tickIndex = (tickIndex + 1) % 20;
	}

	@Override
	public synchronized void cancel() throws IllegalStateException {
		onCancel.run();
		super.cancel();
	}
}
