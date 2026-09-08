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
import org.luckyraven.keystone.item.ItemParser;
import org.luckyraven.keystone.item.ItemSerializerRegistry;

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
	private final ItemSerializerRegistry     serializers;
	@Getter(AccessLevel.NONE)
	private final ItemParser                 itemParser;
	@Getter(AccessLevel.NONE)
	private final UserManager<Player>        userManager;
	@Getter(AccessLevel.NONE)
	private final UserManager<OfflinePlayer> offlineUserManager;
	@Getter(AccessLevel.NONE)
	private final DependencyContainer        container;
	@Getter(AccessLevel.NONE)
	private final LegacySignRewriter         legacyAliasRewriter;

	public SignManager(Gangland gangland,
	                   String shortPrefix,
	                   SignTypeRegistry registry,
	                   SignInteraction signInteraction,
	                   UniqueItemAddon uniqueItemAddon,
	                   ItemSerializerRegistry serializers,
	                   ItemParser itemParser,
	                   UserManager<Player> userManager,
	                   UserManager<OfflinePlayer> offlineUserManager,
	                   DependencyContainer container,
	                   LegacySignRewriter legacyAliasRewriter) {
		super(registry, signInteraction);

		this.gangland            = gangland;
		this.shortPrefix         = shortPrefix;
		this.formatRegistry      = signInteraction.getFormatterService().getFormatRegistry();
		this.uniqueItemAddon     = uniqueItemAddon;
		this.serializers         = serializers;
		this.itemParser          = itemParser;
		this.userManager         = userManager;
		this.offlineUserManager  = offlineUserManager;
		this.container           = container;
		this.legacyAliasRewriter = legacyAliasRewriter;
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
		Sign     buy     = new BuySign(userManager, uniqueItemAddon, serializers, itemParser, buyType);

		formatRegistry.register(buy.createFormat());

		definitions.add(buy.createDefinition());

		// sell (vanilla materials + unique items)
		String   sellKey  = signPrefix + "sell";
		SignType sellType = new SignType(sellKey, "SELL");
		Sign     sell     = new SellSign(userManager, uniqueItemAddon, serializers, itemParser, sellType);

		formatRegistry.register(sell.createFormat());

		definitions.add(sell.createDefinition());

		// item-buy / item-sell (generic: any item definition string — weapon:rifle, unique:x, car:y, money:…) —
		// same BuySign/SellSign classes as buy/sell above, registered under new keys/generated names (PICK refinement
		// 3). The existing buy/sell types stay registered too: placed signs of both eras keep working.
		String   itemBuyKey  = signPrefix + "item-buy";
		SignType itemBuyType = new SignType(itemBuyKey, "ITEM-BUY");
		Sign     itemBuy     = new BuySign(userManager, uniqueItemAddon, serializers, itemParser, itemBuyType);

		formatRegistry.register(itemBuy.createFormat());

		SignTypeDefinition itemBuyDefinition = itemBuy.createDefinition();
		definitions.add(itemBuyDefinition);

		String   itemSellKey  = signPrefix + "item-sell";
		SignType itemSellType = new SignType(itemSellKey, "ITEM-SELL");
		Sign     itemSell     = new SellSign(userManager, uniqueItemAddon, serializers, itemParser, itemSellType);

		formatRegistry.register(itemSell.createFormat());

		SignTypeDefinition itemSellDefinition = itemSell.createDefinition();
		definitions.add(itemSellDefinition);

		// legacy weapon/ammo/wearable headers (pre-0.9.0) — read-time redirection onto item-buy/item-sell above,
		// so a sign placed before this stream keeps resolving once Bartizan installs the matching vocabulary (T-G4b)
		definitions.addAll(legacyAliasDefinitions(itemBuyDefinition, itemSellDefinition, signPrefix));

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

	/**
	 * Redirects the six pre-0.9.0 weapon/ammo/wearable trade-sign headers onto the generic item-buy/item-sell
	 * definitions built above (T-G4b): a sign placed under the old system keeps resolving once its content line is
	 * prefixed with the alias namespace at read time — no event interception, no world scan, no mutation of the
	 * sign text on the block. An alias whose configured header is unrecognised is skipped; {@link LegacySignRewriter}
	 * never guesses.
	 */
	private List<SignTypeDefinition> legacyAliasDefinitions(SignTypeDefinition itemBuyDefinition,
	                                                        SignTypeDefinition itemSellDefinition,
	                                                        String signPrefix) {
		List<SignTypeDefinition> legacyDefinitions = new ArrayList<>();

		for (String legacyHeader : List.of("weapon-buy", "weapon-sell", "ammo-buy", "ammo-sell", "wearable-buy",
		                                   "wearable-sell")) {
			LegacySignRewriter.Rewritten rewritten = legacyAliasRewriter.rewrite(legacyHeader);

			if (rewritten == null) continue;

			SignTypeDefinition delegate = "item-buy".equals(rewritten.headerKey()) ? itemBuyDefinition
			                                                                      : itemSellDefinition;
			SignType legacyType = new SignType(signPrefix + legacyHeader, legacyHeader.toUpperCase());

			LegacyAliasSignAdapter adapter = new LegacyAliasSignAdapter(legacyType, delegate.getSignType(),
					rewritten.definitionPrefix(), delegate.getSignParser(), delegate.getSignValidator());

			SignTypeDefinition legacyDefinition = SignTypeDefinition.builder()
			                                                        .signType(legacyType)
			                                                        .signValidator(adapter)
			                                                        .signParser(adapter)
			                                                        .handler(delegate.getHandler())
			                                                        .bulkHandler(delegate.getBulkHandler())
			                                                        .build();
			legacyDefinition.addAllAspects(delegate.getAspects());

			legacyDefinitions.add(legacyDefinition);
		}

		return legacyDefinitions;
	}

}
