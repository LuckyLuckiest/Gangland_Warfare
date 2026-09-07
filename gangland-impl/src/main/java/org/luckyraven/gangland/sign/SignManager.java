package org.luckyraven.gangland.sign;

import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.sign.extension.SignContributions;
import org.luckyraven.gangland.sign.registry.SignFormatRegistry;
import org.luckyraven.gangland.sign.registry.SignTypeDefinition;
import org.luckyraven.gangland.sign.registry.SignTypeRegistry;
import org.luckyraven.gangland.sign.service.SignInteraction;
import org.luckyraven.gangland.sign.type.BountySign;
import org.luckyraven.gangland.sign.type.Sign;
import org.luckyraven.gangland.sign.type.ViewSign;
import org.luckyraven.gangland.sign.type.WantedSign;
import org.luckyraven.gangland.sign.type.trade.BuySign;
import org.luckyraven.gangland.sign.type.trade.SellSign;
import org.luckyraven.gangland.sign.type.trade.ammo.AmmoBuySign;
import org.luckyraven.gangland.sign.type.trade.ammo.AmmoSellSign;
import org.luckyraven.gangland.sign.type.trade.weapon.WeaponBuySign;
import org.luckyraven.gangland.sign.type.trade.weapon.WeaponSellSign;
import org.luckyraven.gangland.sign.type.trade.wearable.WearableBuySign;
import org.luckyraven.gangland.sign.type.trade.wearable.WearableSellSign;
import org.luckyraven.gangland.sign.validation.SignValidationException;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;
import org.luckyraven.gangland.weapon.wearable.WearableService;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the core sign catalogue and, through {@link SignContributions}, whatever car/weapon/wearable sign types
 * runtime modules contribute. {@code setupSigns()} resolves {@link SignContributions#from(DependencyContainer)}
 * exactly once, at the top of the method — not in the constructor, because this bean is constructed in the CONFIG
 * phase while module beans may not exist yet, and {@code setupSigns()} itself only runs later, from Keystone's
 * convention {@code initialize()} pass, by which point every module bean is guaranteed to exist.
 */
@Getter
public class SignManager extends SignService {

	@Getter(AccessLevel.NONE)
	private final Gangland                   gangland;
	private final String                     shortPrefix;
	private final SignFormatRegistry         formatRegistry;
	@Getter(AccessLevel.NONE)
	private final WeaponService              weaponService;
	@Getter(AccessLevel.NONE)
	private final AmmunitionManager          ammunitionManager;
	@Getter(AccessLevel.NONE)
	private final UniqueItemAddon            uniqueItemAddon;
	@Getter(AccessLevel.NONE)
	private final UserManager<Player>        userManager;
	@Getter(AccessLevel.NONE)
	private final UserManager<OfflinePlayer> offlineUserManager;
	@Getter(AccessLevel.NONE)
	private final WearableService            wearableService;
	@Getter(AccessLevel.NONE)
	private final DependencyContainer        container;

	public SignManager(Gangland gangland,
	                   String shortPrefix,
	                   SignTypeRegistry registry,
	                   SignInteraction signInteraction,
	                   WeaponService weaponService,
	                   AmmunitionManager ammunitionManager,
	                   UniqueItemAddon uniqueItemAddon,
	                   UserManager<Player> userManager,
	                   UserManager<OfflinePlayer> offlineUserManager,
	                   WearableService wearableService,
	                   DependencyContainer container) {
		super(registry, signInteraction);

		this.gangland           = gangland;
		this.shortPrefix        = shortPrefix;
		this.formatRegistry     = signInteraction.getFormatterService().getFormatRegistry();
		this.weaponService      = weaponService;
		this.ammunitionManager  = ammunitionManager;
		this.uniqueItemAddon    = uniqueItemAddon;
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.wearableService    = wearableService;
		this.container          = container;
	}

	@Override
	public List<SignTypeDefinition> setupSigns() throws SignValidationException {
		List<SignTypeDefinition> definitions = new ArrayList<>();

		String signPrefix = shortPrefix + "-";

		// Resolved here, not in the constructor: this bean is built in the CONFIG phase, but setupSigns() runs in
		// Keystone's convention initialize() pass, the first moment every module bean is guaranteed to exist.
		SignContributions contributions = SignContributions.from(container);

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
		Sign view = new ViewSign(gangland, weaponService, ammunitionManager, contributions, wearableService,
		                         uniqueItemAddon, viewType);

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

		// signs contributed by runtime modules (e.g. gadget's car-buy / car-sell)
		for (Sign contributed : contributions.createSigns(signPrefix)) {
			formatRegistry.register(contributed.createFormat());
			definitions.add(contributed.createDefinition());
		}

		return definitions;
	}

}
