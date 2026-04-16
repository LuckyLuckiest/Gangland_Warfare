package me.luckyraven.config;

import me.luckyraven.Gangland;
import me.luckyraven.database.repositories.market.MarketItemStateRepository;
import me.luckyraven.database.repositories.market.MarketLedgerRepository;
import me.luckyraven.database.repositories.market.MarketSnapshotRepository;
import me.luckyraven.market.GanglandMarketMessages;
import me.luckyraven.market.GanglandMarketSettings;
import me.luckyraven.market.bank.EconomyService;
import me.luckyraven.market.bootstrap.MarketPriceTicker;
import me.luckyraven.market.contract.MarketMessageContract;
import me.luckyraven.market.contract.MarketPriceContract;
import me.luckyraven.market.contract.MarketSettingsContract;
import me.luckyraven.market.event.MarketShockLoader;
import me.luckyraven.market.event.MarketShockRegistry;
import me.luckyraven.market.ledger.TransactionLedger;
import me.luckyraven.market.price.*;
import me.luckyraven.market.registry.MarketItemRegistry;
import me.luckyraven.market.snapshot.SnapshotService;
import me.luckyraven.market.contract.MarketSnapshotRepositoryContract;
import me.luckyraven.market.view.LedgerAdminView;
import me.luckyraven.market.view.MarketDetailView;
import me.luckyraven.market.view.MarketOverviewView;
import me.luckyraven.util.autowire.bean.Bean;
import me.luckyraven.util.autowire.bean.Configuration;

import java.util.List;

/**
 * CONFIG-phase wiring for the market subsystem. Dependencies the constructor-injected concrete repository types
 * (produced by the DATABASE phase) so contract-level consumers never import {@code AbstractRepository}.
 *
 * <p>The {@link MarketShockLoader} is declared as a parameter on {@link #marketPriceTicker} only to force the bean
 * graph to instantiate it before the ticker starts — the loader populates the registry during the FILE phase.
 */
@Configuration
public class MarketConfig {

	@Bean
	public MarketSettingsContract marketSettings() {
		return new GanglandMarketSettings();
	}

	@Bean
	public MarketMessageContract marketMessages() {
		return new GanglandMarketMessages();
	}

	@Bean
	public CategoryResolver marketCategoryResolver() {
		// Default: no category mapping. A trader-aware resolver is wired in later via the shop-api bridge.
		return CategoryResolver.NONE;
	}

	@Bean
	public MarketItemRegistry marketItemRegistry(MarketItemStateRepository repository,
	                                             MarketSettingsContract settings) {
		MarketItemRegistry registry = new MarketItemRegistry(repository, settings);
		registry.hydrate();
		// Periodic auto-save walks every repo's data supplier; hand it the live in-memory states.
		repository.setDataSupplier(registry::all);
		return registry;
	}

	@Bean
	public TransactionLedger marketTransactionLedger(MarketLedgerRepository repository) {
		// Ledger rows are write-through on each append; periodic flush has nothing to re-emit.
		repository.setDataSupplier(List::of);
		return new TransactionLedger(repository);
	}

	@Bean
	public EconomyService marketEconomyService(Gangland gangland, TransactionLedger ledger) {
		return new EconomyService(gangland, ledger);
	}

	@Bean
	public SupplyDemandFactor marketSupplyDemandFactor() {
		return new SupplyDemandFactor();
	}

	@Bean
	public MeanReversionFactor marketMeanReversionFactor() {
		return new MeanReversionFactor();
	}

	@Bean
	public RandomWalkFactor marketRandomWalkFactor() {
		return new RandomWalkFactor();
	}

	@Bean
	public PriceBounds marketPriceBounds() {
		return new PriceBounds();
	}

	@Bean
	public ShockFactor marketShockFactorBean(MarketShockRegistry registry) {
		return new ShockFactor(registry);
	}

	@Bean
	public PriceModel marketPriceModel(SupplyDemandFactor supplyDemand,
	                                   MeanReversionFactor meanReversion,
	                                   RandomWalkFactor randomWalk,
	                                   ShockFactor shock,
	                                   PriceBounds bounds) {
		return new PriceModel(supplyDemand, meanReversion, randomWalk, shock, bounds);
	}

	@Bean
	public PriceChangeDispatcher marketPriceChangeDispatcher(Gangland gangland) {
		return new PriceChangeDispatcher(gangland, 0.001D);
	}

	@Bean
	public MarketPriceEngine marketPriceEngine(MarketItemRegistry registry,
	                                           TransactionLedger ledger,
	                                           PriceModel model,
	                                           PriceChangeDispatcher dispatcher,
	                                           MarketShockRegistry shockRegistry,
	                                           MarketSettingsContract settings,
	                                           CategoryResolver categoryResolver) {
		return new MarketPriceEngine(registry, ledger, model, dispatcher, shockRegistry, settings, categoryResolver);
	}

	@Bean
	public MarketPriceContract marketPriceContract(MarketItemRegistry registry,
	                                               MarketSnapshotRepository snapshotRepository,
	                                               MarketSettingsContract settings) {
		return new MarketPriceService(registry, snapshotRepository, settings);
	}

	@Bean
	public SnapshotService marketSnapshotService(MarketItemRegistry registry,
	                                             TransactionLedger ledger,
	                                             MarketSnapshotRepository repository,
	                                             MarketSettingsContract settings) {
		// Snapshot rows are written directly by the snapshot tick; nothing to re-flush periodically.
		repository.setDataSupplier(List::of);
		return new SnapshotService(registry, ledger, repository, settings);
	}

	@Bean
	public MarketPriceTicker marketPriceTicker(Gangland gangland,
	                                           MarketPriceEngine engine,
	                                           SnapshotService snapshot,
	                                           MarketSettingsContract settings,
	                                           MarketShockLoader ignoredOrderingDep) {
		return new MarketPriceTicker(gangland, engine, snapshot, settings);
	}

	// ── views ────────────────────────────────────────────────────────────────────────────────────────────────────

	@Bean
	public MarketDetailView marketDetailView(Gangland gangland,
	                                         MarketPriceContract market,
	                                         MarketSnapshotRepository snapshotRepository) {
		// Takes the concrete repo (DATABASE phase) then narrows to the contract type for the view.
		MarketSnapshotRepositoryContract contract = snapshotRepository;
		return new MarketDetailView(gangland, market, contract);
	}

	@Bean
	public MarketOverviewView marketOverviewView(Gangland gangland,
	                                             MarketPriceContract market,
	                                             MarketDetailView detailView) {
		return new MarketOverviewView(gangland, market, detailView);
	}

	@Bean
	public LedgerAdminView marketLedgerAdminView(Gangland gangland, TransactionLedger ledger) {
		return new LedgerAdminView(gangland, ledger);
	}
}
