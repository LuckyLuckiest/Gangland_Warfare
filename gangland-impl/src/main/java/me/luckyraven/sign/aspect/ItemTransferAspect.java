package me.luckyraven.sign.aspect;

import me.luckyraven.sign.model.ParsedSign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ItemTransferAspect implements SignAspect {

	private final ItemProvider itemProvider;
	private final TransferType transferType;

	public ItemTransferAspect(ItemProvider itemProvider, TransferType transferType) {
		this.itemProvider = itemProvider;
		this.transferType = transferType;
	}

	@Override
	public AspectResult execute(Player player, ParsedSign sign) {
		ItemStack item = itemProvider.getItem(sign);

		if (item == null) {
			return AspectResult.failure("Invalid item: " + sign.getContent());
		}

		item.setAmount(sign.getAmount());

		if (transferType == TransferType.GIVE) {
			if (player.getInventory().firstEmpty() == -1) {
				return AspectResult.failure("Your inventory is full!");
			}

			player.getInventory().addItem(item);

			return AspectResult.successContinue("Received " + sign.getAmount() + "x " + sign.getContent());
		} else {
			if (!player.getInventory().containsAtLeast(item, sign.getAmount())) {
				return AspectResult.failure("You don't have enough " + sign.getContent() + "!");
			}

			item.setAmount(sign.getAmount());
			player.getInventory().removeItem(item);

			return AspectResult.successContinue("Sold " + sign.getAmount() + "x " + sign.getContent());
		}
	}

	@Override
	public boolean canExecute(Player player, ParsedSign sign) {
		ItemStack item = itemProvider.getItem(sign);

		if (item == null) {
			return false;
		}

		if (transferType == TransferType.GIVE) {
			return player.getInventory().firstEmpty() != -1;
		} else {
			item.setAmount(sign.getAmount());

			return player.getInventory().containsAtLeast(item, sign.getAmount());
		}
	}

	@Override
	public String getName() {
		return "ItemTransferAspect-" + transferType;
	}

	@Override
	public int getPriority() {
		return 50;
	}

	public enum TransferType {
		GIVE,
		TAKE;
	}

	@FunctionalInterface
	public interface ItemProvider {

		ItemStack getItem(ParsedSign sign);

	}

}
