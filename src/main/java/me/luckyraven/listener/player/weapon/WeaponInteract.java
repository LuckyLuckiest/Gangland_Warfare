package me.luckyraven.listener.player.weapon;

import com.google.common.util.concurrent.AtomicDouble;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.util.timer.RepeatingTimer;
import net.minecraft.network.protocol.game.PacketPlayOutPosition;
import net.minecraft.world.entity.RelativeMovement;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class WeaponInteract implements Listener {

	private final Gangland              gangland;
	private final Random                random         = new Random();
	private final Set<RelativeMovement> RELATIVE_FLAGS = new HashSet<>(
			Arrays.asList(RelativeMovement.a, RelativeMovement.b, RelativeMovement.c, RelativeMovement.e,
						  RelativeMovement.d));

	public WeaponInteract(Gangland gangland) {
		this.gangland = gangland;
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getItem() == null) return;

		ItemBuilder item = new ItemBuilder(event.getItem());

		if (!item.hasNBTTag("uniqueItem")) return;

		String type = (String) item.getTagData("uniqueItem");
		if (!type.equals("weapon")) return;

		Player player = event.getPlayer();

		// projectile logic
		Projectile projectile = player.launchProjectile(Snowball.class);
		Vector     velocity   = player.getLocation().getDirection();

		// Introduce spread by modifying the projectile velocity
		double spreadAngle = Math.toRadians(5);
		velocity = applySpread(velocity, spreadAngle);

		projectile.setVelocity(velocity.multiply(2));
		projectile.setGravity(false);

		RepeatingTimer timer = applyGravity(projectile);

		// Recoil logic
		float screenRecoil = 0.05F;
		float pushVelocity = 0.05F;

		if (!player.isSneaking()) push(player, .0002F, pushVelocity);
		else push(player, 0, 0);

		if (!player.isSneaking()) applyRecoil(player, screenRecoil, screenRecoil);
		else applyRecoil(player, screenRecoil / 4, screenRecoil / 4);

		timer.start(false);
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

	private Vector applySpread(Vector originalVector, double spreadFactor) {
		double offsetX = (random.nextDouble() - 0.5) * spreadFactor;
		double offsetY = (random.nextDouble() - 0.5) * spreadFactor;
		double offsetZ = (random.nextDouble() - 0.5) * spreadFactor;

		return originalVector.add(new Vector(offsetX, offsetY, offsetZ)).normalize();
	}

	private void applyRecoil(Player player, float yaw, float pitch) {
		float newYaw   = -yaw + 1;
		float newPitch = pitch - 1;

		// Need to use NMS for smooth player movements
		// Exclusive for 1.20.4
		(((CraftPlayer) player).getHandle()).c.b(
				new PacketPlayOutPosition(0D, 0D, 0D, newYaw, newPitch, RELATIVE_FLAGS, 0));

		// Use Entity#setRotation for future updates that are still not updated to avoid issues
//		player.setRotation(player.getLocation().getYaw() + newYaw, player.getLocation().getPitch() + newPitch);
	}

	private void push(Player player, double powerUp, double push) {
		if (push > 0) push *= -1;

		Location location = player.getLocation();
		Vector   vector   = new Vector(0, powerUp, 0);

		if (location.getBlock().getRelative(BlockFace.DOWN).getType() != Material.AIR)
			player.setVelocity(location.getDirection().multiply(push).add(vector));
	}

}
