package me.luckyraven.util.hologram;

import lombok.Getter;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a hologram made of invisible armor stands
 */
@Getter
public class Hologram {

	private static final double LINE_HEIGHT = 0.25;

	private final UUID             id;
	private final Location         baseLocation;
	private final List<ArmorStand> lines;

	private boolean spawned;

	public Hologram(Location location) {
		this.id           = UUID.randomUUID();
		this.baseLocation = location.clone();
		this.lines        = new ArrayList<>();
		this.spawned      = false;
	}

	/**
	 * Spawns the hologram with the given lines
	 *
	 * @param text lines of text (first line is at the top)
	 */
	public void spawn(String... text) {
		if (spawned) return;
		if (baseLocation.getWorld() == null) return;

		for (int i = 0; i < text.length; i++) {
			Location   lineLocation = baseLocation.clone().add(0, (text.length - 1 - i) * LINE_HEIGHT, 0);
			ArmorStand armorStand   = createArmorStand(lineLocation, text[i]);

			lines.add(armorStand);
		}

		spawned = true;
	}

	/**
	 * Updates all lines of the hologram
	 */
	public void update(String... text) {
		if (!spawned) {
			spawn(text);
			return;
		}

		// If line count changed, respawn
		if (lines.size() != text.length) {
			despawn();
			spawn(text);
			return;
		}

		// Update existing lines
		for (int i = 0; i < text.length; i++) {
			ArmorStand armorStand = lines.get(i);
			if (isArmorStandDead(armorStand)) continue;

			armorStand.setCustomName(ChatUtil.color(text[i]));
		}
	}

	/**
	 * Updates a specific line
	 */
	public void updateLine(int lineIndex, String text) {
		if (!spawned || lineIndex < 0 || lineIndex >= lines.size()) return;

		ArmorStand armorStand = lines.get(lineIndex);

		if (isArmorStandDead(armorStand)) return;

		armorStand.setCustomName(ChatUtil.color(text));
	}

	/**
	 * Despawns and removes the hologram
	 */
	public void despawn() {
		if (!spawned) return;

		for (ArmorStand armorStand : lines) {
			if (isArmorStandDead(armorStand)) continue;

			armorStand.remove();
		}

		lines.clear();
		spawned = false;
	}

	/**
	 * Teleports the hologram to a new location
	 */
	public void teleport(Location newLocation) {
		if (!spawned) {
			baseLocation.setWorld(newLocation.getWorld());
			baseLocation.setX(newLocation.getX());
			baseLocation.setY(newLocation.getY());
			baseLocation.setZ(newLocation.getZ());
			return;
		}

		for (int i = 0; i < lines.size(); i++) {
			ArmorStand armorStand = lines.get(i);

			if (isArmorStandDead(armorStand)) continue;

			Location lineLocation = newLocation.clone().add(0, (lines.size() - 1 - i) * LINE_HEIGHT, 0);
			armorStand.teleport(lineLocation);
		}

		baseLocation.setWorld(newLocation.getWorld());
		baseLocation.setX(newLocation.getX());
		baseLocation.setY(newLocation.getY());
		baseLocation.setZ(newLocation.getZ());
	}

	public int getLineCount() {
		return lines.size();
	}

	private boolean isArmorStandDead(ArmorStand armorStand) {
		return armorStand == null || armorStand.isDead();
	}

	private ArmorStand createArmorStand(Location location, String text) {
		World      world      = Objects.requireNonNull(location.getWorld());
		ArmorStand armorStand = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);

		armorStand.setVisible(false);
		armorStand.setGravity(false);
		armorStand.setCustomName(ChatUtil.color(text));
		armorStand.setCustomNameVisible(true);
		// Makes it non-collidable and non-interactable
		armorStand.setMarker(true);
		armorStand.setInvulnerable(true);
		armorStand.setSmall(true);
		armorStand.setSilent(true);
		// Won't save to disk
		armorStand.setPersistent(false);

		// Disable all equipment slots to prevent item placement
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			armorStand.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
			armorStand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
		}

		// Prevent any arms from showing (and thus being interactable)
		armorStand.setArms(false);
		// Make it so the base plate doesn't show
		armorStand.setBasePlate(false);

		return armorStand;
	}

}
