package org.luckyraven.gangland.copsncrooks.config;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.copsncrooks.integration.config.BankerSettingsImpl;
import org.luckyraven.gangland.copsncrooks.integration.config.GanglandBankerEconomy;
import org.luckyraven.gangland.copsncrooks.integration.config.GanglandBankerMessages;
import org.luckyraven.gangland.copsncrooks.npc.banker.BankerData;
import org.luckyraven.gangland.copsncrooks.npc.banker.BankerManager;
import org.luckyraven.gangland.copsncrooks.npc.banker.config.BankerSettings;
import org.luckyraven.gangland.copsncrooks.npc.banker.economy.BankerEconomyContract;
import org.luckyraven.gangland.copsncrooks.npc.banker.message.BankerMessageContract;
import org.luckyraven.gangland.copsncrooks.npc.banker.tier.BankTierRegistry;
import org.luckyraven.gangland.copsncrooks.npc.banker.tier.BankTiersLoader;
import org.luckyraven.gangland.copsncrooks.npc.banker.view.*;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

/**
 * Bean wiring for the Banker NPC feature. Mirrors the trader wiring in {@code TraderModuleConfig}, minus trait-,
 * mood- and shop-related beans. Any NPC infrastructure lives in {@code cops-n-crooks}; this module only supplies
 * the glue.
 *
 * <p>Moved from the core {@code BankerConfig} (T13, module split sprint 2026-09-07). The two
 * {@code permissionManager.addPermission(BankCommand.*)} lines that used to run inside {@link #bankerSettings}
 * stay in core — {@code DataConfig.registerGanglandPermissions()} registers them (T14) so the two bank permission
 * nodes exist whether or not this module is installed. The {@code PermissionManager} parameter below stays
 * (memory rule: bean-ordering-via-params) even though it is now unused in the body.
 */
@CustomLog
@Configuration
public class BankerModuleConfig {

	private final Gangland gangland;

	public BankerModuleConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	@Bean
	public BankerSettings bankerSettings(@SuppressWarnings("unused") Settings settings,
	                                     PermissionManager permissionManager) {
		return new BankerSettingsImpl();
	}

	@Bean
	public BankerMessageContract bankerMessageContract() {
		return new GanglandBankerMessages();
	}

	// ── Tier catalogue (loaded from plugin/npc/bank_tiers.yml) ───────────

	@Bean
	public BankTierRegistry bankTierRegistry() {
		return new BankTierRegistry();
	}

	@Bean
	public BankTiersLoader bankTiersLoader(BankTierRegistry registry, FileManager fileManager) {
		return new BankTiersLoader(registry, fileManager);
	}

	// ── Economy bridge ──────────────────────────────────────────────────

	@Bean
	public BankerEconomyContract bankerEconomyContract(@Qualifier("online") UserManager<Player> userManager,
	                                                   BankTierRegistry tierRegistry,
	                                                   BankerSettings settings,
	                                                   RepositoryRegistry repositoryRegistry) {
		IRepository<Bank> bankRepository = repositoryRegistry.getRepository(Bank.class);
		return new GanglandBankerEconomy(userManager, tierRegistry, settings, bankRepository);
	}

	// ── Views (assembled in dependency order; cross-references wired via setters) ──

	@Bean
	public BankerAmountView bankerAmountView(BankerEconomyContract economy, BankerMessageContract messages) {
		return new BankerAmountView(gangland, economy, messages);
	}

	@Bean
	public BankerUpgradeView bankerUpgradeView(BankerSettings settings,
	                                           BankerEconomyContract economy,
	                                           BankerMessageContract messages) {
		return new BankerUpgradeView(gangland, settings, economy, messages);
	}

	@Bean
	public BankerCreateAccountView bankerCreateAccountView(BankerSettings settings,
	                                                       BankerEconomyContract economy,
	                                                       BankerMessageContract messages) {
		return new BankerCreateAccountView(gangland, settings, economy, messages);
	}

	@Bean
	public BankerRenameAccountView bankerRenameAccountView(BankerEconomyContract economy,
	                                                       BankerMessageContract messages) {
		return new BankerRenameAccountView(gangland, economy, messages);
	}

	@Bean
	public BankerClaimView bankerClaimView(BankerSettings settings,
	                                       BankerEconomyContract economy,
	                                       BankerMessageContract messages) {
		return new BankerClaimView(gangland, settings, economy, messages);
	}

	@Bean
	public BankerMenuView bankerMenuView(BankerSettings settings,
	                                     BankerEconomyContract economy,
	                                     BankerMessageContract messages,
	                                     BankerRenameAccountView renameView) {
		BankerMenuView view = new BankerMenuView(gangland, settings, economy, messages);
		view.setSubViews(renameView);
		return view;
	}

	@Bean
	public BankerFlow bankerFlow(BankerMenuView menuPanel, BankerUpgradeView upgradePanel, BankerClaimView claimPanel,
	                             BankerAmountView amountPanel, BankerCreateAccountView createPanel) {
		return new BankerFlow(gangland, menuPanel, upgradePanel, claimPanel, amountPanel, createPanel);
	}

	// ── NPC lifecycle ───────────────────────────────────────────────────

	@Bean
	public BankerManager bankerManager(BankerSettings settings, RepositoryRegistry repositoryRegistry) {
		IRepository<BankerData> repo = repositoryRegistry.getRepository(BankerData.class);
		return new BankerManager(gangland, repo, settings);
	}

}
