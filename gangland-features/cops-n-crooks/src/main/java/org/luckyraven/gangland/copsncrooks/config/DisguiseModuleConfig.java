package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
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

	/** Whether the player's squad has a fresh sighting; false when no squad chases them. */
	static Predicate<Player> freshSightingOf(CopManager copManager) {
		return player -> {
			CopGroup group = copManager.groupOf(player.getUniqueId());
			return group != null && group.getSquad().hasFreshSighting();
		};
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
		return new DisguiseFilter(settingsOf(copLoader), descriptions, outfit, levelOf(users),
		                          freshSightingOf(copManager));
	}

	@Bean
	public DisguiseTick disguiseTick(CopLoader copLoader, SuspectDescriptions descriptions, CopRadio copRadio) {
		return new DisguiseTick(settingsOf(copLoader), descriptions, copRadio);
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
