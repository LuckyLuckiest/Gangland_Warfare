package me.luckyraven.weapon.projectile;

import com.cryptomorin.xseries.XSound;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.weapon.modifiers.BlockBreakModifier;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages block damage states for weapon projectile impacts. Handles crack animation progression and block
 * regeneration.
 */
public class BlockDamageManager {

	private static final int REGENERATION_DELAY_TICKS = 100;
	private static final int REGENERATION_STEP_TICKS  = 4;
	private static final int MAX_DAMAGE_STAGE         = 9;

	/**
	 * Materials that should actually break when hit enough times. Other blocks will just show max crack state without
	 * breaking.
	 */
	private static final Set<String> BREAKABLE_MATERIALS = Set.of("GLASS", "GLASS_PANE", "STAINED_GLASS",
																  "STAINED_GLASS_PANE", "ICE", "PACKED_ICE", "BLUE_ICE",
																  "FROSTED_ICE", "GLOWSTONE", "SEA_LANTERN",
																  "REDSTONE_LAMP", "MELON", "PUMPKIN", "JACK_O_LANTERN",
																  "TERRACOTTA", "GLAZED_TERRACOTTA");

	private final JavaPlugin                      plugin;
	private final Map<Location, BlockDamageState> damagedBlocks;

	private int entityIdCounter;

	public BlockDamageManager(JavaPlugin plugin) {
		this.plugin          = plugin;
		this.damagedBlocks   = new ConcurrentHashMap<>();
		this.entityIdCounter = Integer.MAX_VALUE - 100000;
	}

	/**
	 * Applies damage to a block from a projectile hit.
	 *
	 * @param block The block that was hit
	 * @param modifier The break block modifier configuration
	 *
	 * @return true if the block was broken, false otherwise
	 */
	public boolean applyDamage(Block block, BlockBreakModifier modifier) {
		Location location = block.getLocation();
		Material material = block.getType();

		if (!modifier.appliesTo(material)) {
			return false;
		}

		var state = damagedBlocks.computeIfAbsent(location, loc -> new BlockDamageState(block.getBlockData().clone(),
																						generateEntityId(), material));

		// Cancel any ongoing regeneration
		state.cancelRegeneration();

		// Increment hit count
		state.incrementHits();

		// Calculate damage stage based on hits
		int hitsRequired = modifier.hitsRequired();
		int currentHits  = state.getHitCount();
		int damageStage  = Math.min(MAX_DAMAGE_STAGE, (currentHits * MAX_DAMAGE_STAGE) / hitsRequired);

		state.setCurrentStage(damageStage);

		// Send crack animation to nearby players
		sendBlockDamage(location, damageStage, state.getEntityId());

		// Check if block should break
		if (currentHits >= hitsRequired) {
			if (shouldBreakBlock(material)) {
				breakBlock(block, location);
				return true;
			} else {
				// Block reached max damage but shouldn't break
				// Keep it at max crack state and start regeneration
				scheduleRegeneration(location, state);
				return false;
			}
		}

		// Schedule regeneration for later
		scheduleRegeneration(location, state);

		return false;
	}

	/**
	 * Clears all block damage states (useful for plugin disable).
	 */
	public void clearAll() {
		for (Map.Entry<Location, BlockDamageState> entry : damagedBlocks.entrySet()) {
			BlockDamageState state = entry.getValue();
			state.cancelRegeneration();
			clearBlockDamage(entry.getKey(), state.getEntityId());
		}
		damagedBlocks.clear();
	}

	/**
	 * Checks if a material should actually break or just show crack effect.
	 */
	private boolean shouldBreakBlock(Material material) {
		String name = material.name();

		// Check against breakable materials list
		for (String breakable : BREAKABLE_MATERIALS) {
			if (name.contains(breakable)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Sends block damage animation to all players within render distance.
	 */
	private void sendBlockDamage(Location location, int stage, int entityId) {
		float progress = Math.min(1.0f, Math.max(0.0f, stage / (float) MAX_DAMAGE_STAGE));

		World world = Objects.requireNonNull(location.getWorld());
		for (Player player : world.getPlayers()) {
			if (player.getLocation().distanceSquared(location) > 64 * 64) continue;
			player.sendBlockDamage(location, progress, entityId);
		}
	}

	/**
	 * Clears the block damage animation for a location.
	 */
	private void clearBlockDamage(Location location, int entityId) {
		World world = Objects.requireNonNull(location.getWorld());
		for (Player player : world.getPlayers()) {
			if (player.getLocation().distanceSquared(location) > 64 * 64) continue;
			player.sendBlockDamage(location, 0.0f, entityId);
		}
	}

	/**
	 * Schedules smooth block regeneration after the regeneration delay.
	 */
	private void scheduleRegeneration(Location location, BlockDamageState state) {
		// Cancel any existing regeneration task
		state.cancelRegeneration();

		// Schedule the start of smooth regeneration
		BukkitTask delayTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
			startSmoothRegeneration(location, state);
		}, REGENERATION_DELAY_TICKS);

		state.setRegenerationTask(delayTask);
	}

	/**
	 * Starts the smooth regeneration process that gradually reduces crack stage.
	 */
	private void startSmoothRegeneration(Location location, BlockDamageState state) {
		// Create a repeating task that reduces damage stage one step at a time
		BukkitTask regenTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			int currentStage = state.getCurrentStage();

			if (currentStage <= 0) {
				// Fully regenerated
				state.cancelRegeneration();
				damagedBlocks.remove(location);
				clearBlockDamage(location, state.getEntityId());
				return;
			}

			// Reduce stage by 1
			int newStage = currentStage - 1;
			state.setCurrentStage(newStage);
			state.setHitCount(Math.max(0, state.getHitCount() - 1));

			// Update the visual
			sendBlockDamage(location, newStage, state.getEntityId());

		}, 0L, REGENERATION_STEP_TICKS);

		state.setRegenerationTask(regenTask);
	}

	/**
	 * Breaks the block and plays appropriate effects.
	 */
	private void breakBlock(Block block, Location location) {
		BlockDamageState state = damagedBlocks.remove(location);
		if (state != null) {
			state.cancelRegeneration();
			clearBlockDamage(location, state.getEntityId());
		}

		Material material = block.getType();
		World    world    = block.getWorld();

		// Play break sound
		XSound.Record breakSound = getBlockBreakSound(material);
		breakSound.soundPlayer().atLocation(location).play();

		// Spawn break particles
		world.spawnParticle(Particle.BLOCK, location.clone().add(0.5, 0.5, 0.5), 25, 0.3, 0.3, 0.3, 0.05,
							block.getBlockData());

		// Set to air (doesn't drop items)
		block.setType(Material.AIR);
	}

	/**
	 * Gets the appropriate break sound for a block material using XSound.
	 */
	private XSound.Record getBlockBreakSound(Material material) {
		String name = material.name();

		if (name.contains("GLASS")) return XSound.BLOCK_GLASS_BREAK.record();
		if (name.contains("STONE") || name.contains("COBBLE") || name.contains("BRICK") ||
			(name.contains("CONCRETE") && !name.contains("POWDER"))) {
			return XSound.BLOCK_STONE_BREAK.record();
		}
		if (name.contains("WOOD") || name.contains("PLANKS") || name.contains("LOG") || name.contains("FENCE") ||
			name.contains("DOOR")) {
			return XSound.BLOCK_WOOD_BREAK.record();
		}
		if (name.contains("GRAVEL") || name.contains("SAND") || name.contains("CONCRETE_POWDER")) {
			return XSound.BLOCK_GRAVEL_BREAK.record();
		}
		if (name.contains("WOOL") || name.contains("CARPET")) {
			return XSound.BLOCK_WOOL_BREAK.record();
		}
		if (name.contains("IRON") || name.contains("GOLD") || name.contains("COPPER") || name.contains("NETHERITE") ||
			name.contains("CHAIN") || name.contains("LANTERN")) {
			return XSound.BLOCK_METAL_BREAK.record();
		}
		if (name.contains("TERRACOTTA")) return XSound.BLOCK_STONE_BREAK.record();
		if (name.contains("ICE")) return XSound.BLOCK_GLASS_BREAK.record();
		if (name.contains("LEAVES")) return XSound.BLOCK_GRASS_BREAK.record();

		return XSound.BLOCK_STONE_BREAK.record();
	}

	/**
	 * Generates a unique entity ID for block damage animation.
	 */
	private synchronized int generateEntityId() {
		return entityIdCounter--;
	}

	/**
	 * Holds the damage state for a single block.
	 */
	@Getter
	private static class BlockDamageState {
		private final BlockData originalData;
		private final int       entityId;
		private final Material  material;

		@Setter
		private int        hitCount;
		@Setter
		private int        currentStage;
		@Setter
		private BukkitTask regenerationTask;

		public BlockDamageState(BlockData originalData, int entityId, Material material) {
			this.originalData = originalData;
			this.entityId     = entityId;
			this.material     = material;
			this.hitCount     = 0;
			this.currentStage = 0;
		}

		public void incrementHits() {
			hitCount++;
		}

		public void cancelRegeneration() {
			if (regenerationTask != null && !regenerationTask.isCancelled()) {
				regenerationTask.cancel();
				regenerationTask = null;
			}
		}
	}

}
