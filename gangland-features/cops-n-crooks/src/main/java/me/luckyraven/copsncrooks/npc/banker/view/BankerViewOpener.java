package me.luckyraven.copsncrooks.npc.banker.view;

import me.luckyraven.copsncrooks.npc.banker.BankerNpc;
import org.bukkit.entity.Player;

public interface BankerViewOpener {

	void openFor(Player player, BankerNpc banker);

}
