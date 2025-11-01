package me.luckyraven.feature.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.util.timer.Timer;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * I will not claim this class as an invention from my side, but it is made by CJCrafter from WeaponMechanics. A
 * brilliant team that created sophisticated and efficient algorithms to make the user experience smooth.
 */
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

	private final Gangland  gangland;
	private final Weapon    weapon;
	private final Player    player;
	private final ItemStack itemStack;
	private final Runnable  onCancel;
	private       int       tickIndex;

	public FullAutoTask(Gangland gangland, Weapon weapon, Player player, ItemStack weaponItem, Runnable onCancel) {
		super(gangland);

		this.gangland  = gangland;
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
			WeaponAction weaponAction = new WeaponAction(gangland, weapon);

			weaponAction.weaponShoot(player);
		}

		// Advance tick
		tickIndex = (tickIndex + 1) % 20;
	}

	@Override
	public synchronized void cancel() throws IllegalStateException {
		onCancel.run();
		super.cancel();
	}

	@Override
	public void stop() {
		onCancel.run();
		super.stop();
	}

	private void applyRecoil() {
		float recoil = (float) weapon.getRecoilAmount();

		if (!player.isSneaking()) {
			recoil(player, recoil, recoil);
		} else {
			float newValue = recoil / 2;

			if (weapon.isScoped()) recoil(player, newValue, newValue);
			else recoil(player, newValue / 2, newValue / 2);
		}
	}

	private void applyPush() {
		if (!player.isSneaking()) {
			push(player, weapon.getPushPowerUp(), weapon.getPushVelocity());
		} else {
			if (weapon.isScoped()) push(player, weapon.getPushPowerUp() / 2, weapon.getPushVelocity() / 2);
			else push(player, 0, 0);
		}
	}

	private void recoil(Player player, float recoil, float recoilVelocity) {
		gangland.getInitializer()
				.getCompatibilityWorker()
				.getRecoilCompatibility()
				.modifyCameraRotation(player, recoil, recoilVelocity, false);
	}

	private void push(Player player, double powerUp, double push) {
		if (push > 0) push *= -1;

		Location location = player.getLocation();
		Vector   vector   = new Vector(0, powerUp, 0);

		if (location.getBlock().getRelative(BlockFace.DOWN).getType() != org.bukkit.Material.AIR) {
			player.setVelocity(location.getDirection().multiply(push).add(vector));
		}
	}
}
