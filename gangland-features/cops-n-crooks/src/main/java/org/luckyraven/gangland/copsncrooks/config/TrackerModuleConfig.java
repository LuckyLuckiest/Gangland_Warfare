package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.TrackerTags;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.TrackerSettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.keystone.util.ActionBarManager;
import java.util.Map;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** Beans of the tracker tags (0.20.0): the tag book and its install into the cop AI tick. */
@Configuration
public class TrackerModuleConfig {

	/** The cops-owned PDC key of a tagged vehicle. */
	static final NamespacedKey KEY = new NamespacedKey("copsncrooks", "tracker");

	private final DependencyContainer container;

	public TrackerModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.container = container;
	}

	@Bean
	public TrackerTags trackerTags(CopLoader copLoader, @Qualifier("online") UserManager<Player> users, CopRadio copRadio,
	                               WantedMessages messages, JavaPlugin plugin) {
		return new TrackerTags(settingsOf(copLoader), levelOf(users), copRadio, System::currentTimeMillis,
		                       notifier(plugin, messages), KEY);
	}

	/** Live settings: re-reads the loaded provider on every get (DEFAULT while none). */
	static Supplier<TrackerSettings> settingsOf(CopLoader copLoader) {
		return () -> {
			CopConfigProvider provider = copLoader.getLoadedProvider();
			TrackerSettings   settings = provider == null ? null : provider.getTrackerSettings();
			return settings == null ? TrackerSettings.DEFAULT : settings;
		};
	}

	/** Wanted level of a player: 0 when he has no user. */
	static ToIntFunction<Player> levelOf(UserManager<Player> users) {
		return player -> {
			User<Player> user = users.getUser(player);
			return user == null ? 0 : user.getWanted().getLevel();
		};
	}

	/** Player-facing side: chat line on tag, action bar HUD. */
	static TrackerTags.Notifier notifier(JavaPlugin plugin, WantedMessages messages) {
		return new TrackerTags.Notifier() {

			@Override
			public void tagged(Player player) {
				player.sendMessage(messages.format(WantedMessages.Key.TRACKER_TAGGED, Map.of()));
			}

			@Override
			public void hud(Player player, int secondsLeft) {
				ActionBarManager.send(plugin, player,
				                      messages.format(WantedMessages.Key.TRACKER_HUD,
				                                      Map.of("seconds", String.valueOf(secondsLeft))), 40L);
			}
		};
	}

	@PostConstruct
	public void installTracker() {
		TrackerTags tags = container.getInstance(TrackerTags.class);
		container.getInstance(CopManager.class).addAiTickHook(tags::tick);
	}
}
