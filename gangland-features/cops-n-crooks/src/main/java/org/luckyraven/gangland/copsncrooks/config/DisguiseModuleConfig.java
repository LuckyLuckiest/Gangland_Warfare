package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolManager;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseFilter;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseTick;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.PoliceOutfit;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.SuspectDescriptions;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.WearableKeys;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.npc.NpcDifficulty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** Beans of the masks, outfits and disguises (0.20.0 lane disguise). */
@Configuration
public class DisguiseModuleConfig {

	private final DependencyContainer container;

	public DisguiseModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.container = container;
	}

	/** The live {@code Cops.Disguise} settings (the shipped defaults until the cop config has loaded). */
	static Supplier<DisguiseSettings> settingsOf(CopLoader copLoader) {
		return () -> {
			CopConfigProvider provider = copLoader.getLoadedProvider();
			DisguiseSettings settings  = provider == null ? null : provider.getDisguiseSettings();
			return settings == null ? DisguiseSettings.DEFAULT : settings;
		};
	}

	/** The wanted level of a player; 0 when the user or its wanted record is unknown. */
	static ToIntFunction<Player> levelOf(UserManager<Player> users) {
		return player -> {
			User<Player> user = users.getUser(player);
			if (user == null || user.getWanted() == null) return 0;

			return user.getWanted().getLevel();
		};
	}

	/** The Difficulty of the tier for his stars; NORMAL before the config loads. */
	static Function<Player, NpcDifficulty> difficultyOf(CopLoader copLoader, ToIntFunction<Player> level) {
		return player -> {
			CopConfigProvider provider = copLoader.getLoadedProvider();
			// max(1, ...): getTierConfig(0) answers the TOP tier, and a post-escape search is level 0
			CopTierConfig tier = provider == null ? null : provider.getTierConfig(Math.max(1, level.applyAsInt(player)));

			return tier == null || tier.difficulty() == null ? NpcDifficulty.NORMAL : tier.difficulty();
		};
	}

	/**
	 * Whether a cop has eyes on the player: one hunting them counts to the alert range (a Marksman on overwatch to his
	 * sight range), and while they are known the bare ray is enough, or a look through glass, iron bars and fences
	 * ({@link #seesThroughPanes}). A wall hides them at any range, and a first pick-up needs the ray.
	 * Any other live cop notices as a patrol does, at the warning range with the sight factors on, so the boss bar and
	 * the memory agree. A cop walking home never counts. False before the config loads.
	 */
	static Predicate<Player> eyesOf(CopPerception perception, CopLoader copLoader, SuspectDescriptions descriptions,
	                                Supplier<List<CopNpc>> witnesses) {
		// ponytail: every live squad and beat cop per hunted player per AI tick. The distance check runs first and
		// every factor is at most 1 (SightModel), so light and crowd lookups only run for cops in range. A per-tick
		// snapshot is the upgrade if this ever shows in timings
		return player -> {
			CopConfigProvider provider = copLoader.getLoadedProvider();
			if (provider == null) return false;

			UUID    id       = player.getUniqueId();
			boolean watching = descriptions.isRecognised(id);
			for (CopNpc cop : witnesses.get()) {
				if (!cop.isValid() || cop.getCurrentState() == CopState.RETURNING) continue;

				boolean hunting = id.equals(cop.getTargetPlayerId());
				double  range   = hunting ? huntingRange(provider, cop) : provider.getWarningSettings().range();
				if (cop.distanceTo(player) > range) continue;
				if (hunting && watching ? cop.canSee(player, range) || seesThroughPanes(cop, player)
				                        : perception.sees(cop, player, range)) return true;
			}
			return false;
		};
	}

	/**
	 * Whether only glass, iron bars, fences and open space lie between the cop's eyes and the player's: the eye-to-eye
	 * ray stops at all of them (live DIS-WINDOW, DIS-BARS), so a cop at a window would otherwise be blind (docket 897).
	 */
	static boolean seesThroughPanes(CopNpc cop, Player player) {
		LivingEntity body = cop.getEntity();
		if (body == null) return false;

		Location from  = body.getEyeLocation();
		Location to    = player.getEyeLocation();
		World    world = from.getWorld();
		if (world == null || !world.equals(to.getWorld())) return false;

		// ponytail: a quarter-block march can step past a block corner the real ray would clip; good enough for a look
		int steps = (int) Math.ceil(from.distance(to) * 4);
		for (int step = 1; step < steps; step++) {
			double part  = (double) step / steps;
			Block  block = world.getBlockAt(Location.locToBlock(from.getX() + (to.getX() - from.getX()) * part),
			                                Location.locToBlock(from.getY() + (to.getY() - from.getY()) * part),
			                                Location.locToBlock(from.getZ() + (to.getZ() - from.getZ()) * part));
			if (!block.isPassable() && !isSeeThrough(block.getType())) return false;
		}
		return true;
	}

	private static boolean isSeeThrough(Material material) {
		String name = material.name();

		return name.contains("GLASS") || name.contains("FENCE") || name.equals("IRON_BARS");
	}

	/** How far a cop hunting the player counts: the alert range, or a Marksman's sight range while on overwatch. */
	private static double huntingRange(CopConfigProvider provider, CopNpc cop) {
		double alert = provider.getAlertRange();

		return cop.getCurrentState() == CopState.OVERWATCH ? Math.max(alert, provider.getOverwatchSettings().sightRange())
		                                                   : alert;
	}

	/** The cops that can witness a disguise change: every live squad cop and every beat cop. */
	static Supplier<List<CopNpc>> witnessesOf(CopManager copManager, PatrolManager patrol) {
		return () -> {
			List<CopNpc> all = new ArrayList<>(copManager.liveCops());
			all.addAll(patrol.beatCops());
			return all;
		};
	}

	/** The live {@code Wanted.Witnesses.Cops.Witness_Floor}, read on every call so a reload takes effect. */
	static DoubleSupplier witnessFloorOf(ChaseConfigLoader chaseConfig) {
		return () -> chaseConfig.getWitnesses().cops().witnessFloor();
	}

	/** Whether the cops still hunt the player: stars above 0, or a post-escape search for them. */
	static Predicate<Player> huntedOf(UserManager<Player> users, CopManager copManager) {
		ToIntFunction<Player> level = levelOf(users);

		return player -> level.applyAsInt(player) > 0 || copManager.isSearching(player.getUniqueId());
	}

	@Bean
	public WearableKeys wearableKeys() {
		return PoliceOutfit.bartizanKeys();
	}

	@Bean
	public SuspectDescriptions suspectDescriptions(CopLoader copLoader, WearableKeys keys, WantedMessages messages) {
		return new SuspectDescriptions(settingsOf(copLoader), keys, messages);
	}

	@Bean
	public PoliceOutfit policeOutfit(CopLoader copLoader, WearableKeys keys) {
		return new PoliceOutfit(settingsOf(copLoader), keys);
	}

	@Bean
	public DisguiseFilter disguiseFilter(CopLoader copLoader, SuspectDescriptions descriptions, PoliceOutfit outfit,
	                                     CopManager copManager, @Qualifier("online") UserManager<Player> users) {
		ToIntFunction<Player> level = levelOf(users);

		return new DisguiseFilter(settingsOf(copLoader), descriptions, outfit, level, huntedOf(users, copManager),
		                          difficultyOf(copLoader, level));
	}

	@Bean
	public DisguiseTick disguiseTick(CopLoader copLoader, SuspectDescriptions descriptions, DisguiseFilter filter,
	                                 PoliceOutfit outfit, CopPerception perception, CopRadio copRadio,
	                                 WantedMessages messages, CopManager copManager, PatrolManager patrol,
	                                 ChaseConfigLoader chaseConfig) {
		DoubleSupplier witnessFloor = witnessFloorOf(chaseConfig);

		return new DisguiseTick(settingsOf(copLoader), descriptions, filter, outfit,
		                        eyesOf(perception, copLoader, descriptions, witnessesOf(copManager, patrol)),
		                        copRadio, messages, witnessFloor);
	}

	/** Registers the lock-on filter, the AI tick and cop-attacked hooks and the {@code %suspect%} extras. */
	@PostConstruct
	public void installDisguise() {
		DisguiseFilter filter = container.getInstance(DisguiseFilter.class);
		container.getInstance(CopPerception.class).addLockOnFilter(filter);

		DisguiseTick tick = container.getInstance(DisguiseTick.class);
		tick.onForget(filter::forget);
		CopManager manager = container.getInstance(CopManager.class);
		manager.addAiTickHook(tick::tick);
		manager.addCopAttackedHook(tick::exposed);
		container.getInstance(HeatLedger.class).setReportLanded(tick::reportLanded);
		container.getInstance(CopRadio.class).setExtraPlaceholders(tick::extras);
	}
}
