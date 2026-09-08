package org.luckyraven.gangland.civilians;

import lombok.CustomLog;
import org.luckyraven.bartizan.api.combat.CombatEligibility;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.civilians.integration.GanglandCivilianSpawnConfigProvider;
import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.combat.BartizanNpcWeapons;
import org.luckyraven.gangland.civilians.npc.combat.DownedTargetFilter;
import org.luckyraven.gangland.civilians.npc.combat.GanglandCombatEligibility;
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
 * {@code CopsNCrooksModuleConfig} "Civilians + entity marks" section (T-H2) plus the four new Bartizan/Keystone SPI
 * implementations this stream introduces (T-H3): {@link BartizanNpcWeapons} (a factory hook, not itself an
 * {@code NpcRangedAttack}), {@link DownedTargetFilter}, {@link GanglandMarkDefaults} and
 * {@link GanglandCombatEligibility} — the wave's one direction reversal, published on the {@code ServicesManager}
 * under Bartizan's {@link CombatEligibility} interface so Bartizan can pull it.
 */
@CustomLog
@Configuration
public class CiviliansModuleConfig {

	private final Gangland gangland;

	public CiviliansModuleConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Civilians config + entity marks
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public CiviliansLoader civiliansLoader(ItemParser itemParser, CivilianSettings civilianSettings,
	                                       FileManager fileManager) {
		CiviliansLoader loader = new CiviliansLoader(gangland, itemParser, civilianSettings, false, null,
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
		return new NpcMarkManager(gangland, npcMarkDefaults);
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

	/**
	 * Published under the declared return type ({@link CombatEligibility}, Bartizan's interface — never the
	 * concrete class), per {@code BeanFactory}'s {@code publishToServicesManager} contract: Bartizan resolves it
	 * lazily via {@code Bukkit.getServicesManager().getRegistration(CombatEligibility.class)}.
	 */
	@Bean(publishToServicesManager = true)
	public CombatEligibility combatEligibility() {
		return new GanglandCombatEligibility();
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
		return new CivilianNpcFactory(gangland, npcMarkManager, itemParser, bartizanNpcWeapons, downedTargetFilter,
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
		return new CivilianService(gangland, civiliansLoader, npcMarkManager, civilianSettings, civilianNpcFactory,
		                           civilianSpawnManager, civilianNpcRegistry);
	}
}
