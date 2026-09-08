package org.luckyraven.gangland.npcshops.config;

import lombok.CustomLog;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.bootstrap.GanglandContext;
import org.luckyraven.gangland.data.economy.BankTiers;
import org.luckyraven.gangland.npcshops.banker.tier.BankTier;
import org.luckyraven.gangland.npcshops.banker.tier.BankTierRegistry;
import org.luckyraven.gangland.npcshops.banker.view.BankerFlow;
import org.luckyraven.gangland.npcshops.command.bank.BankMenuContribution;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;

/**
 * CONFIG-phase wiring that this module used to share with cops-n-crooks (T-J3, gangland-0.9.0.md group J).
 *
 * <p>{@link #bankMenuContribution(Gangland, BankerFlow)} moved verbatim, with its own {@code @Bean}, from
 * {@code CopsNCrooksModuleConfig} — without its own bean, {@code CommandContributions.from(container)} (which
 * resolves via {@code getAllInstances(CommandContribution.class)}) never sees it, and {@code
 * BankCommand.initializeArguments()}'s {@code contributions.createFor("bank", …)} call returns nothing.
 *
 * <p>{@link #installBankTiers()} moved out of {@code CopsNCrooksModuleConfig.installCoreSeams()} — seam 2 of the
 * core's always-present holder beans (see documentation/module-loader.md, "Core seams"). It runs inside {@code
 * BeanFactory.instantiate()}, before {@code GanglandContext.runListenerPhase()}/{@code runCommandPhase()}, so every
 * consumer of {@link BankTiers} (the {@code /glw bank} tree, the death penalty, the bank placeholders) sees the
 * installed lookup once this module is present; without this module {@link BankTiers} stays inert (T-J3's "Watch
 * out": the core holder bean itself stays in {@code gangland-impl}, pinned by {@code HolderSeamBeanTypeTest}).
 */
@CustomLog
@Configuration
public class NpcShopsModuleConfig {

	private final GanglandContext context;

	public NpcShopsModuleConfig(GanglandContext context) {
		this.context = context;
	}

	@Bean
	public BankMenuContribution bankMenuContribution(Gangland gangland, BankerFlow bankerFlow) {
		return new BankMenuContribution(gangland, bankerFlow);
	}

	@PostConstruct
	public void installBankTiers() {
		BankTierRegistry tiers = context.get(BankTierRegistry.class);
		context.get(BankTiers.class).install(bank -> {
			BankTier tier = tiers.get(bank.getTierId());
			return tier != null ? tier : tiers.first();
		});
	}

}
