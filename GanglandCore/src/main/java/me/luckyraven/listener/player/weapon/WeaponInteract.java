package me.luckyraven.listener.player.weapon;

import com.google.common.util.concurrent.AtomicDouble;
import me.luckyraven.Gangland;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.projectile.type.Bullet;
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

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class WeaponInteract implements Listener {

	private final Gangland gangland;
	private final Random   random;

	public WeaponInteract(Gangland gangland) {
		this.gangland = gangland;
		this.random   = new Random();
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
		Weapon weapon = gangland.getInitializer().getWeaponAddon().getWeapon(weaponName);

		// left-click does nothing
		boolean leftClick = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;
		if (leftClick) { }

		// right-click shoots
		boolean rightClick = event.getAction() == Action.RIGHT_CLICK_AIR ||
							 event.getAction() == Action.RIGHT_CLICK_BLOCK;
		if (rightClick) {
			Bullet bullet = new Bullet(player, weapon);

			bullet.launchProjectile();

			float screenRecoil = 0.05F;

			gangland.getInitializer()
					.getCompatibilityWorker()
					.getRecoilCompatibility()
					.modifyCameraRotation(player, screenRecoil, screenRecoil, true);

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
//		// Recoil logic
//		float pushVelocity = 0.05F;
//
//		if (!player.isSneaking()) push(player, .0002F, pushVelocity);
//		else push(player, 0, 0);
//
//		if (!player.isSneaking()) applyRecoil(player, screenRecoil, screenRecoil);
//		else applyRecoil(player, screenRecoil / 4, screenRecoil / 4);
//
//		timer.start(false);
	}

	private Vector applySpread(Vector originalVector, double spreadFactor) {
		double offsetX = (random.nextDouble() - 0.5) * spreadFactor;
		double offsetY = (random.nextDouble() - 0.5) * spreadFactor;
		double offsetZ = (random.nextDouble() - 0.5) * spreadFactor;

		return originalVector.add(new Vector(offsetX, offsetY, offsetZ)).normalize();
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

	private void push(Player player, double powerUp, double push) {
		if (push > 0) push *= -1;

		Location location = player.getLocation();
		Vector   vector   = new Vector(0, powerUp, 0);

		if (location.getBlock().getRelative(BlockFace.DOWN).getType() != Material.AIR) player.setVelocity(
				location.getDirection().multiply(push).add(vector));
	}

}
