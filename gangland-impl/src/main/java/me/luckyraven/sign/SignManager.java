package me.luckyraven.sign;

import lombok.AccessLevel;
import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.sign.registry.SignFormatRegistry;
import me.luckyraven.sign.registry.SignTypeDefinition;
import me.luckyraven.sign.registry.SignTypeRegistry;
import me.luckyraven.sign.service.SignInteraction;
import me.luckyraven.sign.type.BountySign;
import me.luckyraven.sign.type.Sign;
import me.luckyraven.sign.type.ViewSign;
import me.luckyraven.sign.type.WantedSign;
import me.luckyraven.sign.type.trade.BuySign;
import me.luckyraven.sign.type.trade.SellSign;
import me.luckyraven.sign.validation.SignValidationException;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

@Getter
public class SignManager extends SignService {

	@Getter(AccessLevel.NONE)
	private final Gangland           gangland;
	private final SignFormatRegistry formatRegistry;

	public SignManager(Gangland gangland, SignTypeRegistry registry, SignInteraction signInteraction) {
		super(registry, signInteraction);

		this.gangland       = gangland;
		this.formatRegistry = signInteraction.getFormatterService().getFormatRegistry();
	}

	@Override
	public List<SignTypeDefinition> setupSigns() throws SignValidationException {
		WeaponService              weaponService      = gangland.getInitializer().getWeaponManager();
		AmmunitionAddon            ammunitionAddon    = gangland.getInitializer().getAmmunitionAddon();
		UserManager<Player>        userManager        = gangland.getInitializer().getUserManager();
		UserManager<OfflinePlayer> offlineUserManager = gangland.getInitializer().getOfflineUserManager();

		List<SignTypeDefinition> definitions = new ArrayList<>();

		String signPrefix = gangland.getShortPrefix() + "-";

		// buy
		String   buyKey  = signPrefix + "buy";
		SignType buyType = new SignType(buyKey, "BUY");
		Sign     buy     = new BuySign(userManager, weaponService, ammunitionAddon, buyType);

		formatRegistry.register(buy.createFormat());

		definitions.add(buy.createDefinition());

		// sell
		String   sellKey  = signPrefix + "sell";
		SignType sellType = new SignType(sellKey, "SELL");
		Sign     sell     = new SellSign(userManager, weaponService, ammunitionAddon, sellType);

		formatRegistry.register(sell.createFormat());

		definitions.add(sell.createDefinition());

		// view
		String   viewKey  = signPrefix + "view";
		SignType viewType = new SignType(viewKey, "VIEW");
		Sign     view     = new ViewSign(gangland, weaponService, ammunitionAddon, viewType);

		formatRegistry.register(view.createFormat());

		definitions.add(view.createDefinition());

		// wanted
		String   wantedKey  = signPrefix + "wanted";
		SignType wantedType = new SignType(wantedKey, "WANTED");
		Sign     wanted     = new WantedSign(userManager, wantedType);

		formatRegistry.register(wanted.createFormat());

		definitions.add(wanted.createDefinition());

		// bounty
		String   bountyKey  = signPrefix + "bounty";
		SignType bountyType = new SignType(bountyKey, "BOUNTY");
		Sign     bounty     = new BountySign(gangland, offlineUserManager, userManager, bountyType);

		formatRegistry.register(bounty.createFormat());

		definitions.add(bounty.createDefinition());

		return definitions;
	}

}
