package me.luckyraven.inventory.multi;

import org.bukkit.entity.Player;

import java.util.List;

public interface ItemSourceProvider {

	List<ItemSourceEntry> getEntries(Player player, String source);

}
