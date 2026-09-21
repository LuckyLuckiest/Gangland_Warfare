package org.luckyraven.gangland.gadget.config;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gadget.car.CarService;
import org.luckyraven.gangland.gadget.car.ParkedCar;
import org.luckyraven.gangland.gadget.car.access.CarAccessPolicy;
import org.luckyraven.gangland.gadget.car.access.GanglandCarGangs;
import org.luckyraven.gangland.gadget.car.config.CarAddon;
import org.luckyraven.gangland.gadget.car.message.CarMessageContract;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleRegistry;
import org.luckyraven.gangland.gadget.contract.GanglandCarMessages;
import org.luckyraven.gangland.gadget.item.CarConverter;
import org.luckyraven.gangland.gadget.item.CarItemRefresher;
import org.luckyraven.gangland.gadget.item.CarItemSerializer;
import org.luckyraven.gangland.gadget.grapple.GrappleService;
import org.luckyraven.gangland.gadget.grapple.config.GrappleAddon;
import org.luckyraven.gangland.gadget.item.GadgetItemPredicates;
import org.luckyraven.gangland.gadget.item.GrappleConverter;
import org.luckyraven.gangland.gadget.item.GrappleItemSerializer;
import org.luckyraven.gangland.gadget.item.JetpackConverter;
import org.luckyraven.gangland.gadget.item.JetpackItemRefresher;
import org.luckyraven.gangland.gadget.item.JetpackItemSerializer;
import org.luckyraven.gangland.gadget.jetpack.JetpackService;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.gadget.listener.car.CarDamageState;
import org.luckyraven.gangland.gadget.sign.CarSignContribution;
import org.luckyraven.gangland.gadget.sign.CarSignViewProvider;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.item.ItemKind;
import org.luckyraven.keystone.item.ItemConverterRegistry;
import org.luckyraven.keystone.item.ItemRefresherRegistry;
import org.luckyraven.keystone.item.ItemSerializerRegistry;
import org.luckyraven.gangland.item.fuel.FuelService;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

/**
 * CONFIG-phase wiring for the gadget module: the {@code carMessageContract}, {@code carAccessPolicy},
 * {@code carService} and {@code jetpackService} beans moved out of the core's (former
 * {@code CopsAndGadgetsConfig}, now deleted) {@code GadgetConfig}, copied verbatim. Also registers the car item
 * converter/serializer/refresher into the core's existing {@link ItemConverterRegistry} / {@link
 * ItemSerializerRegistry} / {@link ItemRefresherRegistry} beans — the registry parameters on those three bean
 * methods are the {@code BeanGraph} ordering edges, so this module's beans run after the core's registry beans in
 * the same CONFIG phase. The serializer relies on {@link ItemSerializerRegistry}'s stable priority sort (this
 * module registers at the default priority) to outrank the core's {@code MATERIAL} catch-all, which registers at
 * {@link ItemSerializerRegistry#CATCH_ALL_PRIORITY}. Finally, {@code carSignContribution} and
 * {@code carSignViewProvider} are how the {@code car-buy}/{@code car-sell} signs and the {@code view} sign's car
 * branch reach {@code SignManager} — one {@code @Bean} per concrete contribution type, mirroring
 * {@code MailModuleConfig}, so {@code SignContributions.from(container)} actually sees them.
 */
@CustomLog
@Configuration
public class GadgetModuleConfig {

	private final JavaPlugin plugin;

	public GadgetModuleConfig(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	private boolean isJetpackFuelSink(ItemStack stack) {
		return GadgetItemPredicates.JETPACK.test(stack);
	}

	@Bean
	public CarMessageContract carMessageContract() {
		return new GanglandCarMessages();
	}

	/**
	 * Shared mutable state crossing the {@code CarDamageListener}/{@code CarWeaponDamageListener} split (WS7 G5,
	 * B6) — constructed once here and injected into both listeners.
	 */
	@Bean
	public CarDamageState carDamageState() {
		return new CarDamageState();
	}

	/**
	 * GD-06: mount, refuel and pickup are gated on the placer's UUID. The gang half of the rule comes from
	 * {@link MemberManager}, which the gadget module cannot see — hence the contract.
	 */
	@Bean
	public CarAccessPolicy carAccessPolicy(MemberManager memberManager, PermissionManager permissionManager) {
		permissionManager.addPermission(CarAccessPolicy.BYPASS_PERMISSION);

		return new CarAccessPolicy(new GanglandCarGangs(memberManager));
	}

	@Bean
	public CarService carService(CarAddon carAddon,
	                             RepositoryRegistry repositoryRegistry,
	                             FuelService fuelService,
	                             GadgetPhysicsConfig gadgetPhysicsConfig) {
		IRepository<ParkedCar> parkedCarRepository = repositoryRegistry.getRepository(ParkedCar.class);
		CarService carService = new CarService(carAddon, new VehicleRegistry(), plugin, parkedCarRepository,
		                                       fuelService, gadgetPhysicsConfig);
		carService.reloadParkedVehicles();
		return carService;
	}

	/**
	 * T-KR2 (review B2, moved by B-1): installs the jetpack-refuel sink predicate onto the shared {@link FuelService}
	 * so {@code FuelRefuelListener}'s container-to-sink click transfers fuel from a container (e.g. gasoline) into a
	 * worn jetpack again. Since WS7 (G3), this is a pure {@link GadgetItemPredicates#JETPACK} NBT-tag check — no
	 * Bartizan/{@code ServicesManager} lookup involved any more.
	 * Done here rather than in a {@code @PostConstruct} on the {@code @Configuration} constructor: every
	 * {@code @Configuration} is instantiated before any bean phase runs, when only
	 * {@code GanglandContext}/{@code DependencyContainer}/{@code JavaPlugin}/{@code ModuleLoader} are in the container —
	 * a {@code FuelService} constructor parameter there throws {@code IllegalStateException} on bootstrap.
	 */
	@Bean
	public JetpackService jetpackService(FuelService fuelService, GadgetPhysicsConfig gadgetPhysicsConfig,
	                                     JetpackAddon jetpackAddon) {
		fuelService.setFuelSinkPredicate(this::isJetpackFuelSink);
		return new JetpackService(fuelService, plugin, gadgetPhysicsConfig, jetpackAddon);
	}

	@Bean
	public GrappleService grappleService() {
		return new GrappleService(plugin);
	}

	@Bean
	public CarConverter carConverter(CarAddon carAddon, ItemConverterRegistry itemConverterRegistry) {
		CarConverter converter = new CarConverter(carAddon);
		itemConverterRegistry.register(ItemKind.CAR, converter);
		return converter;
	}

	@Bean
	public CarItemSerializer carItemSerializer(ItemSerializerRegistry itemSerializerRegistry) {
		CarItemSerializer serializer = new CarItemSerializer();
		itemSerializerRegistry.register(GadgetItemPredicates.CAR, serializer);
		return serializer;
	}

	@Bean
	public CarItemRefresher carItemRefresher(CarAddon carAddon, ItemRefresherRegistry itemRefresherRegistry) {
		CarItemRefresher refresher = new CarItemRefresher(carAddon);
		itemRefresherRegistry.register(refresher);
		return refresher;
	}

	@Bean
	public JetpackConverter jetpackConverter(JetpackAddon jetpackAddon, ItemConverterRegistry itemConverterRegistry) {
		JetpackConverter converter = new JetpackConverter(jetpackAddon);
		itemConverterRegistry.register(ItemKind.JETPACK, converter);
		return converter;
	}

	@Bean
	public JetpackItemSerializer jetpackItemSerializer(ItemSerializerRegistry itemSerializerRegistry) {
		JetpackItemSerializer serializer = new JetpackItemSerializer();
		// Priority 20: above Bartizan's wearable serializer (priority 0) so a Bartizan-tagged jetpack (WS7-D4's
		// conditional "wearable" NBT tag) is always claimed by gadget's own serializer first, never Bartizan's —
		// closes the WS7-D4 review's Important-3 finding on the gadget side.
		itemSerializerRegistry.register(GadgetItemPredicates.JETPACK, serializer, 20);
		return serializer;
	}

	@Bean
	public JetpackItemRefresher jetpackItemRefresher(JetpackAddon jetpackAddon,
	                                                 ItemRefresherRegistry itemRefresherRegistry) {
		JetpackItemRefresher refresher = new JetpackItemRefresher(jetpackAddon);
		// Priority 20: above Bartizan's wearable refresher (priority 10) — same reason as the serializer above.
		itemRefresherRegistry.register(refresher, 20);
		return refresher;
	}

	@Bean
	public GrappleConverter grappleConverter(GrappleAddon grappleAddon, ItemConverterRegistry itemConverterRegistry) {
		GrappleConverter converter = new GrappleConverter(grappleAddon);
		itemConverterRegistry.register(ItemKind.GRAPPLE, converter);
		return converter;
	}

	@Bean
	public GrappleItemSerializer grappleItemSerializer(ItemSerializerRegistry itemSerializerRegistry) {
		GrappleItemSerializer serializer = new GrappleItemSerializer();
		itemSerializerRegistry.register(GadgetItemPredicates.GRAPPLE, serializer);
		return serializer;
	}

	@Bean
	public CarSignContribution carSignContribution(@Qualifier("online") UserManager<Player> userManager,
	                                               CarAddon carAddon) {
		return new CarSignContribution(userManager, carAddon);
	}

	@Bean
	public CarSignViewProvider carSignViewProvider(InventoryService inventoryService, CarAddon carAddon) {
		return new CarSignViewProvider(plugin, inventoryService, carAddon);
	}
}
