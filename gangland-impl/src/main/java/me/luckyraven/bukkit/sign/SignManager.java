package me.luckyraven.bukkit.sign;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.sign.aspect.ItemTransferAspect;
import me.luckyraven.bukkit.sign.aspect.MoneyAspect;
import me.luckyraven.bukkit.sign.aspect.ViewInventoryAspect;
import me.luckyraven.bukkit.sign.aspect.WantedLevelAspect;
import me.luckyraven.bukkit.sign.handler.AspectBasedSignHandler;
import me.luckyraven.bukkit.sign.parser.impl.BuySignParser;
import me.luckyraven.bukkit.sign.parser.impl.ViewSignParser;
import me.luckyraven.bukkit.sign.registry.SignTypeDefinition;
import me.luckyraven.bukkit.sign.registry.SignTypeRegistry;
import me.luckyraven.bukkit.sign.service.SignInteractionService;
import me.luckyraven.bukkit.sign.validation.impl.BuySignValidator;
import me.luckyraven.bukkit.sign.validation.impl.ViewSignValidator;
import me.luckyraven.bukkit.sign.validation.impl.WantedSignValidator;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;

public class SignManager {

	private final Gangland               gangland;
	@Getter
	private final SignTypeRegistry       registry;
	@Getter
	private final SignInteractionService signService;

	public SignManager(Gangland plugin) {
		this.gangland    = plugin;
		this.registry    = new SignTypeRegistry();
		this.signService = new SignInteractionService(registry);
	}

	/**
	 * Initialize and register all sign types
	 */
	public void initialize() {
		WeaponService       weaponService   = gangland.getInitializer().getWeaponManager();
		AmmunitionAddon     ammunitionAddon = gangland.getInitializer().getAmmunitionAddon();
		UserManager<Player> userManager     = gangland.getInitializer().getUserManager();

		// Register Buy Sign
		registerBuySign(weaponService, ammunitionAddon, userManager);

		// Register Sell Sign
		registerSellSign(weaponService, ammunitionAddon, userManager);

		// Register Wanted Sign
		registerWantedSign(userManager);

		// Register View Sign
		registerViewSign(weaponService, ammunitionAddon);
	}

	private void registerBuySign(WeaponService weaponService, AmmunitionAddon ammunitionAddon,
								 UserManager<Player> userManager) {
		SignType buyType = new SignType("glw-buy", "BUY");

		// Create validator
		BuySignValidator validator = new BuySignValidator(buyType, weaponService, ammunitionAddon);

		// Create parser
		BuySignParser parser = new BuySignParser(buyType);

		// Create aspects
		MoneyAspect moneyAspect = new MoneyAspect(userManager, MoneyAspect.TransactionType.WITHDRAW);

		ItemTransferAspect itemAspect = new ItemTransferAspect(
				sign -> getWeaponItem(weaponService, ammunitionAddon, sign.getContent()),
				ItemTransferAspect.TransferType.GIVE);

		// Create handler
		AspectBasedSignHandler handler = new AspectBasedSignHandler(java.util.Arrays.asList(moneyAspect, itemAspect));

		// Build and register definition
		SignTypeDefinition definition = SignTypeDefinition.builder()
														  .signType(buyType)
														  .signValidator(validator)
														  .signParser(parser)
														  .handler(handler)
														  .displayFormat("&8[&3BUY&8]")
														  .build();

		definition.addAspect(moneyAspect);
		definition.addAspect(itemAspect);

		registry.register(definition);
	}

	private void registerSellSign(WeaponService weaponService, AmmunitionAddon ammunitionAddon,
								  UserManager<Player> userManager) {
		SignType sellType = new SignType("glw-sell", "SELL");

		BuySignValidator validator = new BuySignValidator(sellType, weaponService, ammunitionAddon);
		BuySignParser    parser    = new BuySignParser(sellType);

		ItemTransferAspect itemAspect = new ItemTransferAspect(
				sign -> getWeaponItem(weaponService, ammunitionAddon, sign.getContent()),
				ItemTransferAspect.TransferType.TAKE);

		MoneyAspect moneyAspect = new MoneyAspect(userManager, MoneyAspect.TransactionType.DEPOSIT);

		AspectBasedSignHandler handler = new AspectBasedSignHandler(List.of(itemAspect, moneyAspect));

		SignTypeDefinition definition = SignTypeDefinition.builder()
														  .signType(sellType)
														  .signValidator(validator)
														  .signParser(parser)
														  .handler(handler)
														  .displayFormat("&8[&aSELL&8]")
														  .build();

		definition.addAspect(itemAspect);
		definition.addAspect(moneyAspect);

		registry.register(definition);
	}

	private void registerWantedSign(UserManager<Player> userManager) {
		SignType wantedType = new SignType("glw-wanted", "WANTED");

		WantedSignValidator validator = new WantedSignValidator(wantedType);

		// Wanted sign parser (simplified)
		BuySignParser parser = new BuySignParser(wantedType);

		WantedLevelAspect wantedAspectInc   = new WantedLevelAspect(userManager, WantedLevelAspect.WantedType.INCREASE);
		WantedLevelAspect wantedAspectDec   = new WantedLevelAspect(userManager, WantedLevelAspect.WantedType.DECREASE);
		WantedLevelAspect wantedAspectClear = new WantedLevelAspect(userManager, WantedLevelAspect.WantedType.CLEAR);

		AspectBasedSignHandler handler = new AspectBasedSignHandler(
				List.of(wantedAspectInc, wantedAspectDec, wantedAspectClear));

		SignTypeDefinition definition = SignTypeDefinition.builder()
														  .signType(wantedType)
														  .signValidator(validator)
														  .signParser(parser)
														  .handler(handler)
														  .displayFormat("&8[&cWANTED&8]")
														  .build();

		definition.addAspect(wantedAspectInc);
		definition.addAspect(wantedAspectDec);
		definition.addAspect(wantedAspectClear);

		registry.register(definition);
	}

	private void registerViewSign(WeaponService weaponService, AmmunitionAddon ammunitionAddon) {
		SignType viewType = new SignType("glw-view", "VIEW");

		// Create validator
		ViewSignValidator validator = new ViewSignValidator(viewType, weaponService, ammunitionAddon);

		// Create parser
		ViewSignParser parser = new ViewSignParser(viewType);

		// Create aspect - opens inventory
		ViewInventoryAspect viewAspect = new ViewInventoryAspect(gangland, weaponService, ammunitionAddon);

		// Create handler
		AspectBasedSignHandler handler = new AspectBasedSignHandler(List.of(viewAspect));

		// Build and register definition
		SignTypeDefinition definition = SignTypeDefinition.builder()
														  .signType(viewType)
														  .signValidator(validator)
														  .signParser(parser)
														  .handler(handler)
														  .displayFormat("&8[&3VIEW&8]")
														  .build();

		definition.addAspect(viewAspect);

		registry.register(definition);
	}

	private ItemStack getWeaponItem(WeaponService weaponService, AmmunitionAddon ammunitionAddon, String itemName) {
		// This is a simplified example
		Collection<Weapon> values = weaponService.getWeapons().values();

		return values.stream()
				.filter(w -> w.getName().equalsIgnoreCase(itemName))
				.findFirst()
				.map(Weapon::buildItem)
				.orElse(null);
	}
}
