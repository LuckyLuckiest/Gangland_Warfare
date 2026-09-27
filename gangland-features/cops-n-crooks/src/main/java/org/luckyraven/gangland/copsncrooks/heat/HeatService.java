package org.luckyraven.gangland.copsncrooks.heat;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.npc.entity.EntityMark;
import org.luckyraven.gangland.civilians.npc.entity.EntityMarks;
import org.luckyraven.gangland.copsncrooks.combo.KillCombo;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The 0.12 heat ledger. Every crime adds its heat weight (see {@link HeatConfig}) to the offender's running heat;
 * crossing a {@code Star_Thresholds} entry asks the host, through the star trigger registered via
 * {@link #setStarTrigger}, to raise the offender's wanted level. The host path
 * ({@code EntityDamageListener.handleWanted(user, targetLevel)}) is the single place stars are gained, so timers,
 * bounty and the wanted events all run as before.
 *
 * <p>Heat never drops below the threshold of the offender's current star ({@link HeatConfig#floorFor}), and is
 * lowered to that floor whenever stars are lost ({@link #lowerTo}), so a star gained outside the ledger or lost to
 * evasion never leaves stale heat behind. All state is main-thread only.
 */
public class HeatService {

	private final KillCombo            killCombo;
	private final NpcMarkManager       markManager;
	private final Predicate<Location>  inContestedTurf;
	private final Supplier<HeatConfig> config;
	private final LongSupplier         clock;

	private final Map<UUID, Integer>         heat;
	private final Map<UUID, Map<UUID, Long>> assaultCooldowns;

	private @Nullable BiConsumer<Player, Integer> starTrigger;

	public HeatService(KillCombo killCombo, NpcMarkManager markManager, Predicate<Location> inContestedTurf,
	                   Supplier<HeatConfig> config) {
		this(killCombo, markManager, inContestedTurf, config, System::currentTimeMillis);
	}

	/**
	 * Test seam: {@code clock} supplies the epoch milliseconds the assault cooldown is measured against.
	 */
	HeatService(KillCombo killCombo, NpcMarkManager markManager, Predicate<Location> inContestedTurf,
	            Supplier<HeatConfig> config, LongSupplier clock) {
		this.killCombo        = killCombo;
		this.markManager      = markManager;
		this.inContestedTurf  = inContestedTurf != null ? inContestedTurf : location -> false;
		this.config           = config;
		this.clock            = clock;
		this.heat             = new HashMap<>();
		this.assaultCooldowns = new HashMap<>();
	}

	/**
	 * Builds the "victim stands inside a contested turf" check the turf-war multiplier uses.
	 *
	 * @param turfs the turf manager; {@code null} yields a check that is always {@code false}
	 *
	 * @return {@code true} for a location inside a turf whose runtime state is {@link TurfState#CONTESTING}
	 */
	public static Predicate<Location> contestedTurf(@Nullable TurfManager turfs) {
		if (turfs == null) return location -> false;

		return location -> {
			if (location == null) return false;

			Turf turf = turfs.findAt(location);
			if (turf == null) return false;

			TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
			return state != null && state.getState() == TurfState.CONTESTING;
		};
	}

	public boolean isEnabled() {
		return getConfig().isEnabled();
	}

	/**
	 * @return the loaded {@code Heat:} config, or {@link HeatConfig#defaults()} while none is loaded
	 */
	public HeatConfig getConfig() {
		HeatConfig loaded = config != null ? config.get() : null;
		return loaded != null ? loaded : HeatConfig.defaults();
	}

	/**
	 * Classifies a kill by the victim's entity mark.
	 *
	 * @param victim the killed entity
	 *
	 * @return {@link Crime#KILL_COP} for a POLICE mark, {@link Crime#KILL_CIVILIAN} for a CIVILIAN mark, otherwise
	 * {@link Crime#KILL_PLAYER}
	 */
	public Crime classifyKill(Entity victim) {
		EntityMark mark = EntityMarks.of(markManager.getMark(victim));

		if (mark == EntityMark.POLICE) return Crime.KILL_COP;
		if (mark == EntityMark.CIVILIAN) return Crime.KILL_CIVILIAN;
		return Crime.KILL_PLAYER;
	}

	public boolean isCop(Entity entity) {
		return EntityMarks.of(markManager.getMark(entity)) == EntityMark.POLICE;
	}

	/**
	 * The heat a crime is worth.
	 *
	 * @param crime the crime
	 * @param streak whether the offender's kill combo is running
	 * @param scene where the victim stood, for the turf-war multiplier (kills only); may be {@code null}
	 *
	 * @return the rounded heat points
	 */
	public int weigh(Crime crime, boolean streak, @Nullable Location scene) {
		HeatConfig current = getConfig();

		double points = current.weightOf(crime);
		if (streak) points *= current.getStreakBonus();
		if (crime.isKill() && scene != null && inContestedTurf.test(scene)) points *= current.getTurfWarMultiplier();

		return (int) Math.round(points);
	}

	/**
	 * Scores a crime against the offender and raises their stars if a threshold is crossed.
	 *
	 * @param offender the player who committed the crime
	 * @param wanted the offender's wanted state
	 * @param crime the crime
	 * @param scene where the victim stood; may be {@code null}
	 *
	 * @return the heat points added
	 */
	public int recordCrime(Player offender, Wanted wanted, Crime crime, @Nullable Location scene) {
		boolean streak = killCombo != null && killCombo.getTracker(offender.getUniqueId()) != null;
		int     points = weigh(crime, streak, scene);

		addHeat(offender, wanted, points);
		return points;
	}

	/**
	 * Scores {@link Crime#ASSAULT_COP}, at most once per offender and cop per {@code Assault_Cop_Cooldown_Seconds}.
	 *
	 * @param offender the attacking player
	 * @param wanted the attacker's wanted state
	 * @param cop the damaged cop
	 *
	 * @return {@code false} when the ledger is disabled or the pair is still on cooldown
	 */
	public boolean recordAssault(Player offender, Wanted wanted, Entity cop) {
		if (!isEnabled()) return false;

		long now        = clock.getAsLong();
		long cooldownMs = getConfig().getAssaultCopCooldownSeconds() * 1000L;

		Map<UUID, Long> perCop = assaultCooldowns.computeIfAbsent(offender.getUniqueId(), id -> new HashMap<>());
		Long            last   = perCop.get(cop.getUniqueId());

		if (last != null && now - last < cooldownMs) return false;

		perCop.put(cop.getUniqueId(), now);
		recordCrime(offender, wanted, Crime.ASSAULT_COP, cop.getLocation());
		return true;
	}

	/**
	 * Adds heat on top of the floor of the offender's current star, then fires the star trigger when the new total
	 * reaches a higher star.
	 */
	public void addHeat(Player offender, Wanted wanted, int points) {
		HeatConfig current  = getConfig();
		UUID       playerId = offender.getUniqueId();
		int        maxLevel = wanted.getMaxLevel();

		int  base    = Math.max(heat.getOrDefault(playerId, 0), current.floorFor(wanted.getLevel(), maxLevel));
		long total   = (long) base + Math.max(0, points);
		int  newHeat = (int) Math.min(Integer.MAX_VALUE, total);

		heat.put(playerId, newHeat);

		int target = current.starsFor(newHeat, maxLevel);
		if (target > wanted.getLevel() && starTrigger != null) starTrigger.accept(offender, target);
	}

	public int getHeat(UUID playerId) {
		return heat.getOrDefault(playerId, 0);
	}

	/**
	 * Caps the player's heat at the floor of {@code newLevel}; called when stars are lost.
	 */
	public void lowerTo(UUID playerId, int newLevel) {
		lowerTo(playerId, newLevel, getConfig().getStarThresholds().size());
	}

	/**
	 * {@link #lowerTo(UUID, int)} against thresholds stretched to {@code maxLevel}.
	 */
	public void lowerTo(UUID playerId, int newLevel, int maxLevel) {
		Integer current = heat.get(playerId);
		if (current == null) return;

		heat.put(playerId, Math.min(current, getConfig().floorFor(newLevel, maxLevel)));
	}

	/**
	 * Forgets the player's heat and assault cooldowns (chase over, quit).
	 */
	public void clear(UUID playerId) {
		heat.remove(playerId);
		assaultCooldowns.remove(playerId);
	}

	/**
	 * @param trigger receives the offender and the star level their heat now reaches
	 */
	public void setStarTrigger(@Nullable BiConsumer<Player, Integer> trigger) {
		this.starTrigger = trigger;
	}

}
