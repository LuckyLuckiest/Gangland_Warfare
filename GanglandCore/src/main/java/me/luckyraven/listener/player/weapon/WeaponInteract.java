package me.luckyraven.listener.player.weapon;

import com.google.common.util.concurrent.AtomicDouble;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.WeaponTag;
import me.luckyraven.feature.weapon.events.WeaponShootEvent;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
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
		ItemStack item = event.getItem();

		if (item == null) return;
		if (!Weapon.isWeapon(item)) return;

		String weaponName = Weapon.getHeldWeaponName(item);
		if (weaponName == null) return;

		// get the weapon information
		Player      player   = event.getPlayer();
		ItemBuilder tempItem = new ItemBuilder(item);
		String      value    = String.valueOf(tempItem.getStringTagData(Weapon.getTagProperName(WeaponTag.UUID)));
		UUID        uuid     = null;

		if (!(value == null || value.equals("null") || value.isEmpty())) {
			uuid = UUID.fromString(value);
		}

		// Need to change how the weapons are got, basically there can be a repeated pattern of similar weapons sharing
		// similar traits but are different fundamentally.
		// A solution for this is to have all the weapons loaded and stored in a map.
		// The weapons acquired by the user are created in that instance as a new weapon, which takes all the traits
		// stored.
		Weapon weapon = gangland.getInitializer().getWeaponManager().getWeapon(uuid, weaponName);

		if (weapon == null) return;

		// left-click does nothing
		boolean leftClick = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;
		if (leftClick) {

			return;
		}

		// pre-shot event


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

			Bullet           bullet           = new Bullet(player, weapon);
			WeaponShootEvent weaponShootEvent = new WeaponShootEvent(weapon, bullet);

			// launch the projectile
			if (!weaponShootEvent.isCancelled()) bullet.launchProjectile();

			gangland.getServer().getPluginManager().callEvent(weaponShootEvent);

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

			return;
		}

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
