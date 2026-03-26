package me.luckyraven.sign;

import lombok.AccessLevel;
import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.item.configuration.UniqueItemAddon;
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
import me.luckyraven.sign.type.trade.ammo.AmmoBuySign;
import me.luckyraven.sign.type.trade.ammo.AmmoSellSign;
import me.luckyraven.sign.type.trade.weapon.WeaponBuySign;
import me.luckyraven.sign.type.trade.weapon.WeaponSellSign;
import me.luckyraven.sign.type.trade.wearable.WearableBuySign;
import me.luckyraven.sign.type.trade.wearable.WearableSellSign;
import me.luckyraven.sign.validation.SignValidationException;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.wearable.WearableService;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

@Getter
public class SignManager extends SignService {

	@Getter(AccessLevel.NONE)
	private final Gangland           gangland;
	private final String             shortPrefix;
	private final SignFormatRegistry formatRegistry;

	public SignManager(Gangland gangland, String shortPrefix, SignTypeRegistry registry,
	                   SignInteraction signInteraction) {
		super(registry, signInteraction);

		this.gangland       = gangland;
		this.shortPrefix    = shortPrefix;
		this.formatRegistry = signInteraction.getFormatterService().getFormatRegistry();
	}

	@Override
	public List<SignTypeDefinition> setupSigns() throws SignValidationException {
		WeaponService              weaponService      = gangland.getInitializer().getWeaponManager();
		AmmunitionManager          ammunitionManager  = gangland.getInitializer().getAmmunitionManager();
		UniqueItemAddon            uniqueItemAddon    = gangland.getInitializer().getUniqueItemAddon();
		UserManager<Player>        userManager        = gangland.getInitializer().getUserManager();
		UserManager<OfflinePlayer> offlineUserManager = gangland.getInitializer().getOfflineUserManager();
		WearableService            wearableService    = gangland.getInitializer().getWearableAddon();

		List<SignTypeDefinition> definitions = new ArrayList<>();

		String signPrefix = shortPrefix + "-";

		// weapon buy
		String   weaponBuyKey  = signPrefix + "weapon-buy";
		SignType weaponBuyType = new SignType(weaponBuyKey, "WEAPON-BUY");
		Sign     weaponBuy     = new WeaponBuySign(userManager, weaponService, ammunitionManager, weaponBuyType);

		formatRegistry.register(weaponBuy.createFormat());

		definitions.add(weaponBuy.createDefinition());

		// weapon sell
		String   weaponSellKey  = signPrefix + "weapon-sell";
		SignType weaponSellType = new SignType(weaponSellKey, "WEAPON-SELL");
		Sign     weaponSell     = new WeaponSellSign(userManager, weaponService, ammunitionManager, weaponSellType);

		formatRegistry.register(weaponSell.createFormat());

		definitions.add(weaponSell.createDefinition());

		// ammo buy
		String   ammoBuyKey  = signPrefix + "ammo-buy";
		SignType ammoBuyType = new SignType(ammoBuyKey, "AMMO-BUY");
		Sign     ammoBuy     = new AmmoBuySign(userManager, weaponService, ammunitionManager, ammoBuyType);

		formatRegistry.register(ammoBuy.createFormat());

		definitions.add(ammoBuy.createDefinition());

		// ammo sell
		String   ammoSellKey  = signPrefix + "ammo-sell";
		SignType ammoSellType = new SignType(ammoSellKey, "AMMO-SELL");
		Sign     ammoSell     = new AmmoSellSign(userManager, weaponService, ammunitionManager, ammoSellType);

		formatRegistry.register(ammoSell.createFormat());

		definitions.add(ammoSell.createDefinition());

		// buy (vanilla materials + unique items)
		String   buyKey  = signPrefix + "buy";
		SignType buyType = new SignType(buyKey, "BUY");
		Sign     buy     = new BuySign(userManager, weaponService, ammunitionManager, uniqueItemAddon, buyType);

		formatRegistry.register(buy.createFormat());

		definitions.add(buy.createDefinition());

		// sell (vanilla materials + unique items)
		String   sellKey  = signPrefix + "sell";
		SignType sellType = new SignType(sellKey, "SELL");
		Sign     sell     = new SellSign(userManager, weaponService, ammunitionManager, uniqueItemAddon, sellType);

		formatRegistry.register(sell.createFormat());

		definitions.add(sell.createDefinition());

		// view
		String   viewKey  = signPrefix + "view";
		SignType viewType = new SignType(viewKey, "VIEW");
		Sign     view     = new ViewSign(gangland, weaponService, ammunitionManager, viewType);

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

		// wearable buy
		String   wearableBuyKey  = signPrefix + "wearable-buy";
		SignType wearableBuyType = new SignType(wearableBuyKey, "WEARABLE-BUY");
		Sign wearableBuy = new WearableBuySign(userManager, wearableService, weaponService, ammunitionManager,
		                                       wearableBuyType);

		formatRegistry.register(wearableBuy.createFormat());

		definitions.add(wearableBuy.createDefinition());

		// wearable sell
		String   wearableSellKey  = signPrefix + "wearable-sell";
		SignType wearableSellType = new SignType(wearableSellKey, "WEARABLE-SELL");
		Sign wearableSell = new WearableSellSign(userManager, wearableService, weaponService, ammunitionManager,
		                                         wearableSellType);

		formatRegistry.register(wearableSell.createFormat());

		definitions.add(wearableSell.createDefinition());

		return definitions;
	}

}
