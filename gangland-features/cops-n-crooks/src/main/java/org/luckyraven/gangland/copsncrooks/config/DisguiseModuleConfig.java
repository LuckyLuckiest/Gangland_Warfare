package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolManager;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseFilter;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseTick;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.PoliceOutfit;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.SuspectDescriptions;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.WearableKeys;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.npc.NpcDifficulty;

import java.util.List;
import java.util.function.BiPredicate;
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

	/** Whether a valid cop of {@code group} sees the player now at the alert range; false before the config loads. */
	static BiPredicate<Player, CopGroup> eyesOf(CopPerception perception, CopLoader copLoader,
	                                            SuspectDescriptions descriptions, Supplier<List<CopNpc>> witnesses) {
		// ponytail: one ray per valid group cop per chased player per AI tick; making Keystone's package-private
		// NpcSquad.anyEyesOn public would reuse the pursuit marks if this ever shows in timings
		return (player, group) -> {
			CopConfigProvider provider = copLoader.getLoadedProvider();
			if (provider == null) return false;

			double range = provider.getAlertRange();
			synchronized (group.getCops()) {
				for (CopNpc cop : group.getCops()) {
					if (cop.isValid() && perception.sees(cop, player, range)) return true;
				}
			}
			return false;
		};
	}

	/** The cops that can witness a disguise change; the squads only for now. */
	static Supplier<List<CopNpc>> witnessesOf(CopManager copManager, PatrolManager patrol) {
		return copManager::liveCops;
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
	                                 WantedMessages messages, CopManager copManager, PatrolManager patrol) {
		return new DisguiseTick(settingsOf(copLoader), descriptions, filter, outfit,
		                        eyesOf(perception, copLoader, descriptions, witnessesOf(copManager, patrol)), copRadio,
		                        messages);
	}

	/** Registers the lock-on filter, the AI tick hook and the {@code %suspect%} extras. */
	@PostConstruct
	public void installDisguise() {
		DisguiseFilter filter = container.getInstance(DisguiseFilter.class);
		container.getInstance(CopPerception.class).addLockOnFilter(filter);

		DisguiseTick tick = container.getInstance(DisguiseTick.class);
		tick.onForget(filter::forget);
		container.getInstance(CopManager.class).addAiTickHook(tick::tick);
		container.getInstance(CopRadio.class).setExtraPlaceholders(tick::extras);
	}
}
