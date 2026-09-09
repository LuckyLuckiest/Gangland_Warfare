package org.luckyraven.gangland.gadget.config;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
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
import org.luckyraven.gangland.gadget.item.GadgetItemPredicates;
import org.luckyraven.gangland.gadget.jetpack.JetpackService;
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

	private final Gangland gangland;

	public GadgetModuleConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	@Bean
	public CarMessageContract carMessageContract() {
		return new GanglandCarMessages();
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
		CarService carService = new CarService(carAddon, new VehicleRegistry(), gangland, parkedCarRepository,
		                                       fuelService, gadgetPhysicsConfig);
		carService.reloadParkedVehicles();
		return carService;
	}

	@Bean
	public JetpackService jetpackService(FuelService fuelService, GadgetPhysicsConfig gadgetPhysicsConfig) {
		return new JetpackService(fuelService, gangland, gadgetPhysicsConfig);
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
	public CarSignContribution carSignContribution(@Qualifier("online") UserManager<Player> userManager,
	                                               CarAddon carAddon) {
		return new CarSignContribution(userManager, carAddon);
	}

	@Bean
	public CarSignViewProvider carSignViewProvider(CarAddon carAddon) {
		return new CarSignViewProvider(gangland, carAddon);
	}
}
