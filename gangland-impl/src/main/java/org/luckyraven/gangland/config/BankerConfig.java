package org.luckyraven.gangland.config;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.sub.bank.BankCommand;
import org.luckyraven.gangland.copsncrooks.npc.banker.BankerData;
import org.luckyraven.gangland.copsncrooks.npc.banker.BankerManager;
import org.luckyraven.gangland.copsncrooks.npc.banker.config.BankerSettings;
import org.luckyraven.gangland.copsncrooks.npc.banker.economy.BankerEconomyContract;
import org.luckyraven.gangland.copsncrooks.npc.banker.message.BankerMessageContract;
import org.luckyraven.gangland.copsncrooks.npc.banker.tier.BankTierRegistry;
import org.luckyraven.gangland.copsncrooks.npc.banker.tier.BankTiersLoader;
import org.luckyraven.gangland.copsncrooks.npc.banker.view.*;
import org.luckyraven.gangland.core.bean.Bean;
import org.luckyraven.gangland.core.bean.Configuration;
import org.luckyraven.gangland.core.bean.Qualifier;
import org.luckyraven.gangland.data.permission.PermissionManager;
import org.luckyraven.gangland.economy.bank.Bank;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.file.configuration.copsncrooks.BankerSettingsImpl;
import org.luckyraven.gangland.file.configuration.copsncrooks.GanglandBankerEconomy;
import org.luckyraven.gangland.file.configuration.copsncrooks.GanglandBankerMessages;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.persistence.FileManager;
import org.luckyraven.gangland.persistence.repository.IRepository;
import org.luckyraven.gangland.persistence.repository.RepositoryRegistry;

/**
 * Bean wiring for the Banker NPC feature. Mirrors the trader wiring in {@link ShopConfig}, minus trait-, mood- and
 * shop-related beans. Any NPC infrastructure lives in {@code cops-n-crooks}; this module only supplies the glue.
 */
@CustomLog
@Configuration
public class BankerConfig {

	private final Gangland gangland;

	public BankerConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	@Bean
	public BankerSettings bankerSettings(@SuppressWarnings("unused") Settings settings,
	                                     PermissionManager permissionManager) {
		// Register the bypass-cap permission once, alongside settings load. Exposing it through PermissionManager
		// surfaces it in rank / tab-completion flows the same way other gangland permissions are discovered.
		permissionManager.addPermission(BankCommand.BYPASS_CAP_PERMISSION);
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
