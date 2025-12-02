package me.luckyraven.inventory.multi;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public interface ItemSourceProvider {

	List<ItemStack> getItems(Player player, String source);

}
