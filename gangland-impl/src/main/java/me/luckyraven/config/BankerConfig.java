package me.luckyraven.config;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.command.sub.bank.BankCommand;
import me.luckyraven.copsncrooks.npc.banker.BankerData;
import me.luckyraven.copsncrooks.npc.banker.BankerManager;
import me.luckyraven.copsncrooks.npc.banker.config.BankerSettings;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract;
import me.luckyraven.copsncrooks.npc.banker.message.BankerMessageContract;
import me.luckyraven.copsncrooks.npc.banker.tier.BankTierRegistry;
import me.luckyraven.copsncrooks.npc.banker.tier.BankTiersLoader;
import me.luckyraven.copsncrooks.npc.banker.view.*;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.economy.bank.Bank;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.file.configuration.copsncrooks.BankerSettingsImpl;
import me.luckyraven.file.configuration.copsncrooks.GanglandBankerEconomy;
import me.luckyraven.file.configuration.copsncrooks.GanglandBankerMessages;
import me.luckyraven.npc.banker.BankerViewOpenerImpl;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import me.luckyraven.util.autowire.bean.Bean;
import me.luckyraven.util.autowire.bean.Configuration;
import me.luckyraven.util.autowire.bean.Qualifier;
import org.bukkit.entity.Player;

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
	public BankerSettings bankerSettings(Settings settings, PermissionManager permissionManager) {
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
	public BankerAmountView bankerAmountView(BankerSettings settings,
	                                         BankerEconomyContract economy,
	                                         BankerMessageContract messages) {
		return new BankerAmountView(gangland, settings, economy, messages);
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
	public BankerDeleteAccountView bankerDeleteAccountView(BankerSettings settings,
	                                                       BankerEconomyContract economy,
	                                                       BankerMessageContract messages) {
		return new BankerDeleteAccountView(gangland, settings, economy, messages);
	}

	@Bean
	public BankerRenameAccountView bankerRenameAccountView(BankerSettings settings,
	                                                       BankerEconomyContract economy,
	                                                       BankerMessageContract messages) {
		return new BankerRenameAccountView(gangland, settings, economy, messages);
	}

	@Bean
	public BankerMenuView bankerMenuView(BankerSettings settings,
	                                     BankerEconomyContract economy,
	                                     BankerMessageContract messages,
	                                     BankerAmountView amountView,
	                                     BankerUpgradeView upgradeView,
	                                     BankerCreateAccountView createView,
	                                     BankerDeleteAccountView deleteView,
	                                     BankerRenameAccountView renameView) {
		BankerMenuView view = new BankerMenuView(gangland, settings, economy, messages);
		view.setSubViews(amountView, upgradeView, createView, deleteView, renameView);
		amountView.setMenuView(view);
		upgradeView.setMenuView(view);
		createView.setMenuView(view);
		deleteView.setMenuView(view);
		renameView.setMenuView(view);
		return view;
	}

	// ── NPC lifecycle ───────────────────────────────────────────────────

	@Bean
	public BankerManager bankerManager(BankerSettings settings, RepositoryRegistry repositoryRegistry) {
		IRepository<BankerData> repo = repositoryRegistry.getRepository(BankerData.class);
		return new BankerManager(gangland, repo, settings);
	}

	@Bean
	public BankerViewOpener bankerViewOpener(BankerMenuView menuView) {
		return new BankerViewOpenerImpl(menuView);
	}

}
