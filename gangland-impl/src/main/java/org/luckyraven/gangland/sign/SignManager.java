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
import org.luckyraven.gangland.sign.validation.SignValidationException;
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
	private final UniqueItemAddon            uniqueItemAddon;
	@Getter(AccessLevel.NONE)
	private final UserManager<Player>        userManager;
	@Getter(AccessLevel.NONE)
	private final UserManager<OfflinePlayer> offlineUserManager;
	@Getter(AccessLevel.NONE)
	private final DependencyContainer        container;

	public SignManager(Gangland gangland,
	                   String shortPrefix,
	                   SignTypeRegistry registry,
	                   SignInteraction signInteraction,
	                   UniqueItemAddon uniqueItemAddon,
	                   UserManager<Player> userManager,
	                   UserManager<OfflinePlayer> offlineUserManager,
	                   DependencyContainer container) {
		super(registry, signInteraction);

		this.gangland           = gangland;
		this.shortPrefix        = shortPrefix;
		this.formatRegistry     = signInteraction.getFormatterService().getFormatRegistry();
		this.uniqueItemAddon    = uniqueItemAddon;
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.container          = container;
	}

	@Override
	public List<SignTypeDefinition> setupSigns() throws SignValidationException {
		List<SignTypeDefinition> definitions = new ArrayList<>();

		String signPrefix = shortPrefix + "-";

		// Resolved here, not in the constructor: this bean is built in the CONFIG phase, but setupSigns() runs in
		// Keystone's convention initialize() pass, the first moment every module bean is guaranteed to exist.
		SignContributions contributions = SignContributions.from(container);

		// buy (vanilla materials + unique items)
		String   buyKey  = signPrefix + "buy";
		SignType buyType = new SignType(buyKey, "BUY");
		Sign     buy     = new BuySign(userManager, uniqueItemAddon, buyType);

		formatRegistry.register(buy.createFormat());

		definitions.add(buy.createDefinition());

		// sell (vanilla materials + unique items)
		String   sellKey  = signPrefix + "sell";
		SignType sellType = new SignType(sellKey, "SELL");
		Sign     sell     = new SellSign(userManager, uniqueItemAddon, sellType);

		formatRegistry.register(sell.createFormat());

		definitions.add(sell.createDefinition());

		// view
		String   viewKey  = signPrefix + "view";
		SignType viewType = new SignType(viewKey, "VIEW");
		Sign view = new ViewSign(gangland, contributions, viewType);

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

		// signs contributed by runtime modules (e.g. gadget's car-buy / car-sell, weapon's weapon/ammo/wearable)
		for (Sign contributed : contributions.createSigns(signPrefix)) {
			formatRegistry.register(contributed.createFormat());
			definitions.add(contributed.createDefinition());
		}

		return definitions;
	}

}
