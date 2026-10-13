package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseFilter;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseTick;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.PoliceOutfit;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.SuspectDescriptions;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.WearableKeys;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import java.util.function.Supplier;

/** Beans of the masks, outfits and disguises (0.20.0 lane disguise). */
@Configuration
public class DisguiseModuleConfig {

	private final DependencyContainer container;

	public DisguiseModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.container = container;
	}

	/** The live {@code Cops.Disguise} settings (the shipped defaults until the cop config has loaded). */
	static Supplier<DisguiseSettings> settingsOf(CopLoader copLoader) {
		throw new UnsupportedOperationException("TDD stub");
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
		throw new UnsupportedOperationException("TDD stub");
	}

	@Bean
	public DisguiseTick disguiseTick(CopLoader copLoader, SuspectDescriptions descriptions, CopRadio copRadio) {
		return new DisguiseTick(settingsOf(copLoader), descriptions, copRadio);
	}

	/** Registers the lock-on filter, the AI tick hook and the {@code %suspect%} extras. */
	@PostConstruct
	public void installDisguise() {
		throw new UnsupportedOperationException("TDD stub");
	}
}
