package org.luckyraven.gangland.sign.type.trade;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.aspect.AspectResult;
import org.luckyraven.gangland.sign.aspect.ItemTransferAspect;
import org.luckyraven.gangland.sign.aspect.MoneyAspect;
import org.luckyraven.gangland.sign.aspect.SignAspect;
import org.luckyraven.gangland.sign.bulk.BulkActionPreview;
import org.luckyraven.gangland.sign.bulk.BulkSignHandler;
import org.luckyraven.gangland.sign.handler.AspectBasedSignHandler;
import org.luckyraven.gangland.sign.handler.SignHandler;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.model.SignFormat;
import org.luckyraven.gangland.sign.parser.SignParser;
import org.luckyraven.gangland.sign.parser.TradeSignParser;
import org.luckyraven.gangland.sign.registry.SignTypeDefinition;
import org.luckyraven.gangland.sign.validation.SignValidator;
import org.luckyraven.gangland.sign.validation.trade.ItemSignValidator;
import org.luckyraven.keystone.item.ItemParser;
import org.luckyraven.keystone.item.ItemSerializerRegistry;

import java.util.List;

import org.luckyraven.keystone.bean.Qualifier;

public class SellSign extends BaseTradeSign implements BulkSignHandler {

	private final UserManager<Player>    userManager;
	private final UniqueItemAddon        uniqueItemAddon;
	private final ItemSerializerRegistry serializers;
	private final ItemParser             itemParser;
	private final SignType               signType;

	/**
	 * Cached after {@link #createDefinition()} is called; used by {@link #executeBulkAction}.
	 */
	private SignHandler handler;

	public SellSign(@Qualifier("online") UserManager<Player> userManager, UniqueItemAddon uniqueItemAddon,
	                ItemSerializerRegistry serializers, ItemParser itemParser, SignType signType) {
		this.userManager     = userManager;
		this.uniqueItemAddon = uniqueItemAddon;
		this.serializers     = serializers;
		this.itemParser      = itemParser;
		this.signType        = signType;
	}

	@Override
	public SignTypeDefinition createDefinition() {
		SignValidator validator = new ItemSignValidator(signType, uniqueItemAddon, itemParser);
		SignParser    parser    = new TradeSignParser(signType);

		SignAspect itemAspect = new ItemTransferAspect(
				sign -> getDefinedItem(sign.getContent(), uniqueItemAddon, itemParser),
				ItemTransferAspect.TransferType.TAKE,
				(player, a, b) -> sameTradeDefinition(serializers, a, b));

		SignAspect moneyAspect = new MoneyAspect(userManager, MoneyAspect.TransactionType.DEPOSIT);

		List<SignAspect> aspects = List.of(moneyAspect, itemAspect);

		this.handler = new AspectBasedSignHandler(aspects);

		var definition = SignTypeDefinition.builder()
		                                   .signType(signType)
		                                   .signValidator(validator)
		                                   .signParser(parser)
		                                   .handler(handler)
		                                   .bulkHandler(this)
		                                   .build();

		definition.addAllAspects(aspects);

		return definition;
	}

	// ── BulkSignHandler ───────────────────────────────────────────────────────

	@Override
	public BulkActionPreview previewBulk(ParsedSign sign) {
		return new BulkActionPreview(sign.getAmount(), sign.getPrice(), sign.getContent());
	}

	/**
	 * Executes the bulk sell by running the same aspect chain used for single sales. The sign's configured
	 * {@code amount} and {@code price} define the transaction.
	 */
	@Override
	public List<AspectResult> executeBulkAction(Player player, ParsedSign sign) {
		if (handler == null) {
			return List.of(AspectResult.failure("Sign not fully initialized - please contact an admin."));
		}

		return handler.handle(player, sign);
	}

	// ── SignFormat ────────────────────────────────────────────────────────────

	@Override
	public SignFormat createFormat() {
		return TradeSignFormat.create(signType);
	}

}
