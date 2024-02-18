package me.luckyraven.listener.player.weapon;

import com.google.common.util.concurrent.AtomicDouble;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.projectile.type.Bullet;
import me.luckyraven.file.configuration.SoundConfiguration;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class WeaponInteract implements Listener {

	private final Gangland gangland;

	public WeaponInteract(Gangland gangland) {
		this.gangland = gangland;
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		// check if it was a weapon
		if (event.getItem() == null) return;
		if (!Weapon.isWeapon(event.getItem())) return;

		String weaponName = Weapon.getHeldWeaponName(event.getItem());
		if (weaponName == null) return;

		// get the weapon information
		Player player = event.getPlayer();
		// Need to change how the weapons are got, basically there can be a repeated pattern of similar weapons sharing
		// similar traits but are not the same fundamentally.
		// A solution for this is to have all the weapons loaded and stored in a map
		// The weapons acquired by the user are created at that instance as a new weapon, which takes all the traits
		// stored.
		Weapon weapon = gangland.getInitializer().getWeaponAddon().getWeapon(weaponName);

		if (weapon == null) return;

		// left-click does nothing
		boolean leftClick = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;
		if (leftClick) { }

		// right-click shoots
		boolean rightClick = event.getAction() == Action.RIGHT_CLICK_AIR ||
							 event.getAction() == Action.RIGHT_CLICK_BLOCK;
		if (rightClick) {
			// consume bullet
			boolean consumed = weapon.consumeShot();

			// no shot fired
			if (!consumed) {
				// sound
				boolean sound = playSound(player, weapon.getCustomMagSound());
				if (!sound) playSound(player, weapon.getDefaultMagSound());

				return;
			}

			Bullet bullet = new Bullet(player, weapon);

			// launch the projectile
			bullet.launchProjectile();

			// update data
			ItemBuilder heldWeapon = weapon.getHeldWeapon(player);

			if (heldWeapon != null) {
				weapon.updateWeaponData(heldWeapon);
				weapon.updateWeapon(player, heldWeapon, player.getInventory().getHeldItemSlot());
			}

			float recoil = (float) weapon.getRecoilAmount();

			if (!player.isSneaking()) recoil(player, recoil, recoil);
			else recoil(player, recoil / 4, recoil / 4);

			if (!player.isSneaking()) push(player, weapon.getPushPowerUp(), weapon.getPushVelocity());
			else push(player, 0, 0);

			boolean shotSound = playSound(player, weapon.getCustomShotSound());
			if (!shotSound) playSound(player, weapon.getDefaultShotSound());
		}

		// drop reloads the gun

		// pre-shot event

		// check for reload

		// shot event

		// projectile logic
//		Projectile projectile = player.launchProjectile(Snowball.class);
//		Vector     velocity   = player.getLocation().getDirection();
//
//		// Introduce spread by modifying the projectile velocity
//		double spreadAngle = Math.toRadians(5);
//		velocity = applySpread(velocity, spreadAngle);
//
//		projectile.setVelocity(velocity.multiply(2));
//		projectile.setGravity(false);
//
//		RepeatingTimer timer = applyGravity(projectile);
//
//		timer.start(false);
	}

	@NotNull
	private RepeatingTimer applyGravity(Projectile projectile) {
		AtomicReference<Location> initialLocation  = new AtomicReference<>(projectile.getLocation());
		AtomicDouble              furthestDistance = new AtomicDouble(10);
		AtomicBoolean             falling          = new AtomicBoolean();

		return new RepeatingTimer(gangland, 1, t -> {
			if (projectile.isDead()) {
				t.cancel();
				return;
			}

			double distance = initialLocation.get().distance(projectile.getLocation());

			if (distance >= furthestDistance.get() && !falling.get()) falling.set(true);
			if (!falling.get()) return;

			Vector currentVelocity = projectile.getVelocity();
			double newY            = currentVelocity.getY() - 0.001;
			projectile.setVelocity(currentVelocity.setY(newY).normalize());

			if (projectile.getLocation().getChunk().isLoaded() ||
				(projectile.getLocation().getY() > 0 && distance < furthestDistance.get() * 10)) return;

			projectile.remove();
			t.cancel();
		});
	}

	private void recoil(Player player, float yaw, float pitch) {
		gangland.getInitializer()
				.getCompatibilityWorker()
				.getRecoilCompatibility()
				.modifyCameraRotation(player, yaw, pitch, true);
	}

	private void push(Player player, double powerUp, double push) {
		if (push > 0) push *= -1;

		Location location = player.getLocation();
		Vector   vector   = new Vector(0, powerUp, 0);

		if (location.getBlock().getRelative(BlockFace.DOWN).getType() != Material.AIR) player.setVelocity(
				location.getDirection().multiply(push).add(vector));
	}

	private boolean playSound(Player player, SoundConfiguration sound) {
		if (sound == null) return false;

		return sound.playSound(player);
	}

}
