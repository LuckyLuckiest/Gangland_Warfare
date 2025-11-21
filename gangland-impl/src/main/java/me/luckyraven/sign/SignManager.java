package me.luckyraven.sign;

import me.luckyraven.Gangland;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.sign.aspect.*;
import me.luckyraven.sign.handler.AspectBasedSignHandler;
import me.luckyraven.sign.handler.SignHandler;
import me.luckyraven.sign.parser.BuySignParser;
import me.luckyraven.sign.parser.SignParser;
import me.luckyraven.sign.parser.ViewSignParser;
import me.luckyraven.sign.registry.SignTypeDefinition;
import me.luckyraven.sign.registry.SignTypeRegistry;
import me.luckyraven.sign.service.SignInteraction;
import me.luckyraven.sign.validation.BuySignValidator;
import me.luckyraven.sign.validation.SignValidator;
import me.luckyraven.sign.validation.ViewSignValidator;
import me.luckyraven.sign.validation.WantedSignValidator;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SignManager extends SignService {

	private final Gangland gangland;

	public SignManager(Gangland plugin, SignTypeRegistry registry, SignInteraction signInteraction) {
		super(registry, signInteraction);

		this.gangland = plugin;
	}

	@Override
	public List<SignTypeDefinition> setupSigns() {
		WeaponService       weaponService   = gangland.getInitializer().getWeaponManager();
		AmmunitionAddon     ammunitionAddon = gangland.getInitializer().getAmmunitionAddon();
		UserManager<Player> userManager     = gangland.getInitializer().getUserManager();

		List<SignTypeDefinition> definitions = new ArrayList<>();

		definitions.add(registerBuySign(weaponService, ammunitionAddon, userManager));
		definitions.add(registerSellSign(weaponService, ammunitionAddon, userManager));
		definitions.add(registerViewSign(weaponService, ammunitionAddon));
		definitions.add(registerWantedSign(userManager));

		return definitions;
	}

	private SignTypeDefinition registerBuySign(WeaponService weaponService, AmmunitionAddon ammunitionAddon,
											   UserManager<Player> userManager) {
		SignType buyType = new SignType("glw-buy", "BUY");

		// Create validator & parser
		SignValidator validator = new BuySignValidator(buyType, weaponService, ammunitionAddon);
		SignParser    parser    = new BuySignParser(buyType);

		// Create aspects
		SignAspect moneyAspect = new MoneyAspect(userManager, MoneyAspect.TransactionType.WITHDRAW);

		SignAspect itemAspect = new ItemTransferAspect(sign -> getWeaponItem(weaponService, sign.getContent()),
													   ItemTransferAspect.TransferType.GIVE);

		// Create handler
		SignHandler handler = new AspectBasedSignHandler(List.of(moneyAspect, itemAspect));

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

		return definition;
	}

	private SignTypeDefinition registerSellSign(WeaponService weaponService, AmmunitionAddon ammunitionAddon,
												UserManager<Player> userManager) {
		SignType sellType = new SignType("glw-sell", "SELL");

		// validate & parser
		SignValidator validator = new BuySignValidator(sellType, weaponService, ammunitionAddon);
		SignParser    parser    = new BuySignParser(sellType);

		// aspect
		SignAspect itemAspect = new ItemTransferAspect(sign -> getWeaponItem(weaponService, sign.getContent()),
													   ItemTransferAspect.TransferType.TAKE);

		SignAspect moneyAspect = new MoneyAspect(userManager, MoneyAspect.TransactionType.DEPOSIT);

		// handler
		SignHandler handler = new AspectBasedSignHandler(List.of(itemAspect, moneyAspect));

		SignTypeDefinition definition = SignTypeDefinition.builder()
														  .signType(sellType)
														  .signValidator(validator)
														  .signParser(parser)
														  .handler(handler)
														  .displayFormat("&8[&aSELL&8]")
														  .build();

		definition.addAspect(itemAspect);
		definition.addAspect(moneyAspect);

		return definition;
	}

	private SignTypeDefinition registerWantedSign(UserManager<Player> userManager) {
		SignType wantedType = new SignType("glw-wanted", "WANTED");

		// validator & parser
		SignValidator validator = new WantedSignValidator(wantedType);
		SignParser    parser    = new BuySignParser(wantedType);

		// aspect
		SignAspect wantedAspectInc   = new WantedLevelAspect(userManager, WantedLevelAspect.WantedType.INCREASE);
		SignAspect wantedAspectDec   = new WantedLevelAspect(userManager, WantedLevelAspect.WantedType.DECREASE);
		SignAspect wantedAspectClear = new WantedLevelAspect(userManager, WantedLevelAspect.WantedType.CLEAR);

		// handler
		SignHandler handler = new AspectBasedSignHandler(List.of(wantedAspectInc, wantedAspectDec, wantedAspectClear));

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

		return definition;
	}

	private SignTypeDefinition registerViewSign(WeaponService weaponService, AmmunitionAddon ammunitionAddon) {
		SignType viewType = new SignType("glw-view", "VIEW");

		// Create validator & parser
		SignValidator validator = new ViewSignValidator(viewType, weaponService, ammunitionAddon);
		SignParser    parser    = new ViewSignParser(viewType);

		// aspect
		SignAspect viewAspect = new ViewInventoryAspect(gangland, weaponService, ammunitionAddon);

		// handler
		SignHandler handler = new AspectBasedSignHandler(List.of(viewAspect));

		SignTypeDefinition definition = SignTypeDefinition.builder()
														  .signType(viewType)
														  .signValidator(validator)
														  .signParser(parser)
														  .handler(handler)
														  .displayFormat("&8[&3VIEW&8]")
														  .build();

		definition.addAspect(viewAspect);

		return definition;
	}

	private ItemStack getWeaponItem(WeaponService weaponService, String itemName) {
		Collection<Weapon> values = weaponService.getWeapons().values();

		return values.stream()
				.filter(w -> w.getName().equalsIgnoreCase(itemName))
				.findFirst()
				.map(Weapon::buildItem)
				.orElse(null);
	}
}
