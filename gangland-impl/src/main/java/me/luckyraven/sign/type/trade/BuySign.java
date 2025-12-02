package me.luckyraven.sign.type.trade;

import me.luckyraven.data.user.UserManager;
import me.luckyraven.sign.SignType;
import me.luckyraven.sign.aspect.ItemTransferAspect;
import me.luckyraven.sign.aspect.MoneyAspect;
import me.luckyraven.sign.aspect.SignAspect;
import me.luckyraven.sign.handler.AspectBasedSignHandler;
import me.luckyraven.sign.handler.SignHandler;
import me.luckyraven.sign.model.SignFormat;
import me.luckyraven.sign.model.SignLineFormat;
import me.luckyraven.sign.parser.SignParser;
import me.luckyraven.sign.parser.TradeSignParser;
import me.luckyraven.sign.registry.SignTypeDefinition;
import me.luckyraven.sign.validation.SignValidator;
import me.luckyraven.sign.validation.TradeSignValidator;
import me.luckyraven.util.color.Color;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import org.bukkit.entity.Player;

import java.util.List;

public class BuySign extends BaseTradeSign {

	private final UserManager<Player> userManager;
	private final SignType            signType;

	public BuySign(UserManager<Player> userManager, WeaponService weaponService, AmmunitionAddon ammunitionAddon,
				   SignType signType) {
		super(weaponService, ammunitionAddon);

		this.userManager = userManager;
		this.signType    = signType;
	}

	@Override
	public SignTypeDefinition createDefinition() {
		// Create validator & parser
		SignValidator validator = new TradeSignValidator(signType, getWeaponService(), getAmmunitionAddon());
		SignParser    parser    = new TradeSignParser(signType);

		// Create aspects
		SignAspect moneyAspect = new MoneyAspect(userManager, MoneyAspect.TransactionType.WITHDRAW);

		SignAspect itemAspect = new ItemTransferAspect(sign -> getRequiredItem(sign.getContent()),
													   ItemTransferAspect.TransferType.GIVE, getWeaponService(),
													   getAmmunitionAddon());

		// Create handler
		List<SignAspect> aspects = List.of(moneyAspect, itemAspect);
		SignHandler      handler = new AspectBasedSignHandler(aspects);

		// Build and register definition
		var definition = SignTypeDefinition.builder()
										   .signType(signType)
										   .signValidator(validator)
										   .signParser(parser)
										   .handler(handler)
										   .build();

		definition.addAllAspects(aspects);

		return definition;
	}

	@Override
	public SignFormat createFormat() {
		String generated = signType.generated();
		var    builder   = SignFormat.builder().formatName(generated.toLowerCase()).signTypePrefix(signType.typed());

		var line1 = SignLineFormat.builder()
								  .lineNumber(0)
								  .required(true)
								  .contentType(SignLineFormat.LineContentType.TITLE)
								  .formatter(s -> "&8[&3" + generated + "&8]")
								  .build();

		var line2 = SignLineFormat.builder()
								  .lineNumber(1)
								  .required(true)
								  .defaultColor(Color.GRAY)
								  .contentType(SignLineFormat.LineContentType.CUSTOM_TEXT)
								  .formatter(s -> "&7" + s)
								  .build();

		var line3 = SignLineFormat.builder()
								  .lineNumber(2)
								  .required(true)
								  .defaultColor(Color.LIME)
								  .contentType(SignLineFormat.LineContentType.PRICE)
								  .formatter(s -> "&a" + s)
								  .build();

		var line4 = SignLineFormat.builder()
								  .lineNumber(3)
								  .required(true)
								  .defaultColor(Color.CYAN)
								  .contentType(SignLineFormat.LineContentType.QUANTITY)
								  .formatter(s -> "&b" + s)
								  .build();

		return builder.lineFormats(List.of(line1, line2, line3, line4)).build();
	}
}
