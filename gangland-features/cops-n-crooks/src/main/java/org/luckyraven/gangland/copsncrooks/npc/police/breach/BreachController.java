package org.luckyraven.gangland.copsncrooks.npc.police.breach;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;

import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Stack up and breach (0.21.0): a suspect hidden indoors with a door near his last-known spot gets up to
 * {@code Stack_Size} hunting cops holding the cell outside the door, then the entry (Defender first, the rest
 * {@code Entry_Gap_Ticks} apart, a flashbang first at {@code Flashbang.Min_Stars}). No block is ever changed.
 * Main thread only, like the cop AI tick that drives {@link #tick}.
 *
 * @since 0.21.0
 */
public class BreachController {

	/** What the controller reads from the world; the production one is built on {@link DoorFinder}. */
	public interface Surroundings {

		boolean indoors(Location at, int roofBlocks);

		Optional<DoorFinder.Door> doorNear(Location at, int radius);
	}

	/** Blinds and slows one player for {@code ticks}. */
	@FunctionalInterface
	public interface Flashbang {

		void blind(Player player, int ticks);
	}

	private final Supplier<CopConfigProvider> provider;
	private final CopRadio                    radio;
	private final LongSupplier                clock;
	private final Surroundings                surroundings;
	private final Flashbang                   flashbang;

	public BreachController(Supplier<CopConfigProvider> provider, CopRadio radio, LongSupplier clock) {
		this(provider, radio, clock, worldSurroundings(), BreachController::applyFlashbang);
	}

	BreachController(Supplier<CopConfigProvider> provider, CopRadio radio, LongSupplier clock,
	                 Surroundings surroundings, Flashbang flashbang) {
		this.provider     = provider;
		this.radio        = radio;
		this.clock        = clock;
		this.surroundings = surroundings;
		this.flashbang    = flashbang;
	}

	/** Per cop AI tick: advances this player's breach, or (at most once a second) looks for one to start. */
	public void tick(Player player, @Nullable CopGroup group) {
		throw new UnsupportedOperationException("TDD stub");
	}

	public boolean isActive(UUID playerId) {
		return false;
	}

	/** Releases every stack (reload, disable). */
	public void endAll() {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Blindness and Slowness for {@code ticks} through XPotion. */
	static void applyFlashbang(Player player, int ticks) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The production surroundings: {@link DoorFinder#indoors} and {@link DoorFinder#find} over the suspect's world. */
	static Surroundings worldSurroundings() {
		return new Surroundings() {

			@Override
			public boolean indoors(Location at, int roofBlocks) {
				throw new UnsupportedOperationException("TDD stub");
			}

			@Override
			public Optional<DoorFinder.Door> doorNear(Location at, int radius) {
				throw new UnsupportedOperationException("TDD stub");
			}
		};
	}
}
