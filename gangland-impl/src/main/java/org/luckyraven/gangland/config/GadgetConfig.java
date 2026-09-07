package org.luckyraven.gangland.config;

import lombok.CustomLog;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.gangland.file.configuration.gadget.GanglandCarMessages;
import org.luckyraven.gangland.gadget.GanglandCarGangs;
import org.luckyraven.gangland.gadget.car.CarService;
import org.luckyraven.gangland.gadget.car.ParkedCar;
import org.luckyraven.gangland.gadget.car.access.CarAccessPolicy;
import org.luckyraven.gangland.gadget.car.config.CarAddon;
import org.luckyraven.gangland.gadget.car.message.CarMessageContract;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleRegistry;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.gangland.gadget.fuel.FuelService;
import org.luckyraven.gangland.gadget.jetpack.JetpackService;
import org.luckyraven.gangland.gadget.wearable.WearableAddon;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.gangland.weapon.WeaponService;

/**
 * CONFIG-phase wiring for the gadget services (car, jetpack, fuel). Cops-n-crooks beans that used to live in this
 * class (as {@code CopsAndGadgetsConfig}) moved to {@code CopsNCrooksModuleConfig} in the cops-n-crooks runtime
 * module (T13/T14, module split sprint 2026-09-07); the money drop classifier moved to {@code DataConfig}.
 */
@CustomLog
@Configuration
public class GadgetConfig {

	private final Gangland gangland;

	public GadgetConfig(Gangland gangland) {
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
	public JetpackService jetpackService(FuelService fuelService,
	                                     GadgetPhysicsConfig gadgetPhysicsConfig,
	                                     WearableAddon wearableAddon,
	                                     WeaponService weaponService) {
		return new JetpackService(fuelService, gangland, gadgetPhysicsConfig, wearableAddon, weaponService);
	}
}
