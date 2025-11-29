package me.luckyraven.sign.aspect;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.data.economy.EconomyException;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.inventory.multi.MultiInventory;
import me.luckyraven.inventory.part.ButtonTags;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.sign.model.ParsedSign;
import me.luckyraven.sign.type.BountySign;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static me.luckyraven.inventory.multi.MultiInventoryCreation.dynamicMultiInventory;

@RequiredArgsConstructor
public class BountyAspect implements SignAspect {

	private final JavaPlugin                 plugin;
	private final UserManager<OfflinePlayer> offlinePlayerUserManager;
	private final UserManager<Player>        onlinePlayerUserManager;

	@Override
	public AspectResult execute(Player player, ParsedSign sign) {
		User<Player> user = onlinePlayerUserManager.getUser(player);

		BountySign.BountyType bountyType = BountySign.BountyType.valueOf(sign.getContent().toUpperCase());

		switch (bountyType) {
			case VIEW -> {
				openBountyView(player);
				return AspectResult.success("Opened bounty view!");
			}
			case CLEAR -> {
				double amount = user.getBounty().getAmount();

				try {
					user.getEconomy().withdraw(amount);
				} catch (EconomyException exception) {
					return AspectResult.failure(exception.getMessage());
				}

				user.getBounty().resetBounty();

				String withdrawn = MessageAddon.WITHDRAW_MONEY_PLAYER.toString(MessageAddon.Type.NO_CHANGE);

				return AspectResult.success(withdrawn.replace("%amount%", SettingAddon.formatDouble(amount)));
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

		// create a multi inventory
		String title = "&c&lBounties";
		Fill   fill  = new Fill(SettingAddon.getInventoryFillName(), SettingAddon.getInventoryFillItem());

		ButtonTags buttonTags = new ButtonTags(SettingAddon.getPreviousPage(), SettingAddon.getHomePage(),
											   SettingAddon.getNextPage());

		MultiInventory multiInventory = dynamicMultiInventory(plugin, player, heads, title, false, false, fill,
															  buttonTags, null);

		if (multiInventory == null) return;

		multiInventory.open(player);
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

		lore.add(String.format("&7&lBounty: &a%s&e%s", SettingAddon.getMoneySymbol(),
							   SettingAddon.formatDouble(user.getBounty().getAmount())));
		lore.add("&7&lStatus: " + status);

		headBuilder.setLore(lore);

		heads.add(headBuilder.build());
	}

}
