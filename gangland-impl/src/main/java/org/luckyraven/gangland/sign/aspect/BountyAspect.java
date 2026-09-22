package org.luckyraven.gangland.sign.aspect;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.economy.exception.EconomyException;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.menu.InventoryBuilder;
import org.luckyraven.gangland.menu.SimplePagedMenu;
import org.luckyraven.gangland.menu.part.ButtonTags;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.type.BountySign;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
public class BountyAspect implements SignAspect {

	private final JavaPlugin                 plugin;
	private final UserManager<OfflinePlayer> offlinePlayerUserManager;
	private final UserManager<Player>        onlinePlayerUserManager;
	private final InventoryService           inventoryService;

	@Override
	public AspectResult execute(Player player, ParsedSign sign) {
		User<Player> user = onlinePlayerUserManager.getUser(player);

		if (user == null) return AspectResult.failure(Messages.PLAYER_NOT_FOUND.toString());

		BountySign.BountyType bountyType = BountySign.BountyType.valueOf(sign.getContent().toUpperCase());

		switch (bountyType) {
			case VIEW -> {
				openBountyView(player);
				return AspectResult.success("Opened bounty view!");
			}
			case CLEAR -> {
				BigDecimal amount = user.getBounty().getAmount();

				try {
					user.getEconomy().withdrawAmount(amount);
				} catch (EconomyException exception) {
					return AspectResult.failure(exception.getMessage());
				}

				user.getBounty().resetBounty();

				String withdrawn = Messages.WITHDRAW_MONEY_PLAYER.toString(Messages.Type.NO_CHANGE);

				return AspectResult.success(withdrawn.replace("%amount%", Settings.formatAmount(amount)));
			}
			default -> {
				return AspectResult.failure("Unknown bounty operation type");
			}
		}
	}

	@Override
	public boolean canExecute(Player player, ParsedSign sign) {
		User<Player> user = onlinePlayerUserManager.getUser(player);

		if (user == null) {
			return false;
		}

		BountySign.BountyType bountyType = BountySign.BountyType.valueOf(sign.getContent().toUpperCase());

		if (bountyType == BountySign.BountyType.CLEAR) {
			return user.getBounty().hasBounty();
		}

		return true;
	}

	@Override
	public String getName() {
		return "BountyAspect";
	}

	private void openBountyView(Player player) {
		// view all the players who have a bounty on them
		List<User<OfflinePlayer>> offlinePlayers = new ArrayList<>(offlinePlayerUserManager.getUsers().values());
		List<User<Player>>        onlinePlayers  = new ArrayList<>(onlinePlayerUserManager.getUsers().values());

		// convert the uuids to a list of items
		List<ItemStack> heads = new ArrayList<>();

		Material type = Objects.requireNonNull(XMaterial.PLAYER_HEAD.get());
		for (User<Player> user : onlinePlayers) {
			if (!user.getBounty().hasBounty()) continue;

			generateData(user, type, heads, "&aONLINE");
		}

		for (User<OfflinePlayer> user : offlinePlayers) {
			if (!user.getBounty().hasBounty()) continue;

			generateData(user, type, heads, "&cOFFLINE");
		}

		SimplePagedMenu.open(inventoryService, player, heads, "&c&lBounties", InventoryBuilder.DEFAULT_FILL_ITEM,
		                     InventoryBuilder.DEFAULT_FILL_NAME, ButtonTags.DEFAULT);
	}

	private void generateData(User<? extends OfflinePlayer> user, Material type, List<ItemStack> heads, String status) {
		ItemStack   headItem    = new ItemStack(type);
		ItemBuilder headBuilder = new ItemBuilder(headItem);

		OfflinePlayer offlinePlayer = user.getUser();
		UUID          uniqueId      = offlinePlayer.getUniqueId();

		headBuilder.customHead(uniqueId);

		// add a description
		headBuilder.setDisplayName("&8&l[&c&lWANTED&8&l] &c" + Bukkit.getOfflinePlayer(uniqueId).getName());

		List<String> lore = new ArrayList<>();

		lore.add(String.format("&7&lBounty: &a%s&e%s", Settings.getMoneySymbol(),
		                       Settings.formatAmount(user.getBounty().getAmount())));
		lore.add("&7&lStatus: " + status);

		headBuilder.setLore(lore);

		heads.add(headBuilder.build());
	}

}
