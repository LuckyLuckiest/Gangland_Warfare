package org.luckyraven.gangland.civilians;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.civilians.integration.GanglandCivilianSpawnConfigProvider;
import org.luckyraven.gangland.civilians.message.CivilianMessages;
import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.combat.BartizanNpcWeapons;
import org.luckyraven.gangland.civilians.npc.combat.DownedTargetFilter;
import org.luckyraven.gangland.civilians.npc.config.CivilianSettings;
import org.luckyraven.gangland.civilians.npc.config.CiviliansLoader;
import org.luckyraven.gangland.civilians.npc.entity.GanglandMarkDefaults;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpcFactory;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawner;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.item.ItemParser;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

/**
 * CONFIG-phase wiring for the civilians module (T-H1/T-H3), moved out of cops-n-crooks's
 * {@code CopsNCrooksModuleConfig} "Civilians + entity marks" section (T-H2) plus three of the four new
 * Bartizan/Keystone SPI implementations this stream introduces (T-H3): {@link BartizanNpcWeapons} (a factory hook,
 * not itself an {@code NpcRangedAttack}), {@link DownedTargetFilter} and {@link GanglandMarkDefaults}. The fourth,
 * {@code GanglandCombatEligibility}, is wired in the sibling {@link CombatEligibilityConfig} instead (WS7 G5b) —
 * its bean return type names a Bartizan type directly, and this class must stay Bartizan-free so Keystone's
 * per-class {@code ReflectionGuard.orSkip} never has cause to skip these 8 unrelated beans on a Bartizan-less
 * server.
 */
@CustomLog
@Configuration
public class CiviliansModuleConfig {

	private final JavaPlugin plugin;

	public CiviliansModuleConfig(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Messages (WS6 G3 worked example)
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public CivilianMessages civilianMessages(FileManager fileManager) {
		CivilianMessages messages = new CivilianMessages(fileManager);
		fileManager.registerInitializer(messages);
		return messages;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Civilians config + entity marks
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public CiviliansLoader civiliansLoader(ItemParser itemParser, CivilianSettings civilianSettings,
	                                       FileManager fileManager) {
		CiviliansLoader loader = new CiviliansLoader(plugin, itemParser, civilianSettings, false, null,
		                                             fileManager);
		fileManager.registerInitializer(loader);
		fileManager.initializeAll();
		return loader;
	}

	@Bean
	public GanglandMarkDefaults npcMarkDefaults(CiviliansLoader civiliansLoader) {
		return new GanglandMarkDefaults(civiliansLoader);
	}

	@Bean
	public NpcMarkManager npcMarkManager(GanglandMarkDefaults npcMarkDefaults) {
		return new NpcMarkManager(plugin, npcMarkDefaults);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Bartizan/Keystone NPC combat SPI (T-H3)
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public BartizanNpcWeapons bartizanNpcWeapons() {
		return new BartizanNpcWeapons();
	}

	@Bean
	public DownedTargetFilter npcTargetFilter() {
		return new DownedTargetFilter();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Civilian NPC lifecycle
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public CivilianNpcRegistry civilianNpcRegistry() {
		return new CivilianNpcRegistry();
	}

	@Bean
	public CivilianNpcFactory civilianNpcFactory(NpcMarkManager npcMarkManager, ItemParser itemParser,
	                                             BartizanNpcWeapons bartizanNpcWeapons,
	                                             DownedTargetFilter downedTargetFilter,
	                                             CivilianSettings civilianSettings) {
		return new CivilianNpcFactory(plugin, npcMarkManager, itemParser, bartizanNpcWeapons, downedTargetFilter,
		                              civilianSettings);
	}

	@Bean
	public CivilianSpawnManager civilianSpawnManager(CivilianNpcFactory civilianNpcFactory,
	                                                 CivilianNpcRegistry civilianNpcRegistry,
	                                                 CiviliansLoader civiliansLoader,
	                                                 GanglandCivilianSpawnConfigProvider spawnConfigProvider,
	                                                 RepositoryRegistry repositoryRegistry) {
		IRepository<CivilianSpawner> repo = repositoryRegistry.getRepository(CivilianSpawner.class);
		return new CivilianSpawnManager(spawnConfigProvider, repo, civilianNpcFactory, civilianNpcRegistry,
		                                civiliansLoader);
	}

	@Bean
	public CivilianService civilianService(CiviliansLoader civiliansLoader, NpcMarkManager npcMarkManager,
	                                       CivilianSettings civilianSettings, CivilianNpcFactory civilianNpcFactory,
	                                       CivilianSpawnManager civilianSpawnManager,
	                                       CivilianNpcRegistry civilianNpcRegistry) {
		return new CivilianService(plugin, civiliansLoader, npcMarkManager, civilianSettings, civilianNpcFactory,
		                           civilianSpawnManager, civilianNpcRegistry);
	}
}
