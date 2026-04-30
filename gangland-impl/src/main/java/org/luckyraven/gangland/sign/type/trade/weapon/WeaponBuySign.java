package org.luckyraven.gangland.sign.type.trade.weapon;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.core.color.Color;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.aspect.ItemTransferAspect;
import org.luckyraven.gangland.sign.aspect.MoneyAspect;
import org.luckyraven.gangland.sign.aspect.SignAspect;
import org.luckyraven.gangland.sign.handler.AspectBasedSignHandler;
import org.luckyraven.gangland.sign.handler.SignHandler;
import org.luckyraven.gangland.sign.model.SignFormat;
import org.luckyraven.gangland.sign.model.SignLineFormat;
import org.luckyraven.gangland.sign.parser.SignParser;
import org.luckyraven.gangland.sign.parser.TradeSignParser;
import org.luckyraven.gangland.sign.registry.SignTypeDefinition;
import org.luckyraven.gangland.sign.type.trade.BaseTradeSign;
import org.luckyraven.gangland.sign.validation.SignValidator;
import org.luckyraven.gangland.sign.validation.trade.weapon.WeaponSignValidator;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;

import java.util.List;

public class WeaponBuySign extends BaseTradeSign {

	private final UserManager<Player> userManager;
	private final SignType            signType;

	public WeaponBuySign(UserManager<Player> userManager, WeaponService weaponService,
	                     AmmunitionManager ammunitionManager, SignType signType) {
		super(weaponService, ammunitionManager);

		this.userManager = userManager;
		this.signType    = signType;
	}

	@Override
	public SignTypeDefinition createDefinition() {
		SignValidator validator = new WeaponSignValidator(signType, getWeaponService());
		SignParser    parser    = new TradeSignParser(signType);

		SignAspect moneyAspect = new MoneyAspect(userManager, MoneyAspect.TransactionType.WITHDRAW);

		SignAspect itemAspect = new ItemTransferAspect(sign -> getWeaponItem(sign.getContent()),
		                                               ItemTransferAspect.TransferType.GIVE, weaponSimilarityChecker());

		List<SignAspect> aspects = List.of(moneyAspect, itemAspect);
		SignHandler      handler = new AspectBasedSignHandler(aspects);

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
