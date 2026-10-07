package org.luckyraven.gangland.copsncrooks.command.cops.spawner;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DispatchSettings;
import org.luckyraven.gangland.copsncrooks.station.Station;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;

import java.util.Objects;

class CopSpawnerSetCommand extends SubArgument {

	private final CopSpawnManager copSpawnManager;
	private final StationRegistry stations;
	private final CopLoader       copLoader;

	CopSpawnerSetCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, CopSpawnManager copSpawnManager,
	                     StationRegistry stations, CopLoader copLoader) {
		super(plugin, "set", tree, parent);
		this.copSpawnManager = copSpawnManager;
		this.stations        = stations;
		this.copLoader       = copLoader;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}

			copSpawnManager.setSpawnerLocation(player.getLocation());
			assignToNearestStation(player.getLocation());

			sender.sendMessage(Messages.COP_SPAWNER_SET.toString());
		};
	}

	/** The new spawner (the manager's last id) joins the nearest station when that one is within Station_Radius. */
	private void assignToNearestStation(Location at) {
		Station station = stations.nearest(at);
		Location anchor = station == null ? null : station.getLocation();
		if (anchor == null) return;

		CopConfigProvider provider = copLoader.getLoadedProvider();
		DispatchSettings  dispatch = Objects.requireNonNullElse(
				provider == null ? null : provider.getDispatchSettings(), DispatchSettings.DEFAULT);
		double dx = anchor.getX() - at.getX();
		double dz = anchor.getZ() - at.getZ();
		if (dx * dx + dz * dz <= dispatch.stationRadius() * dispatch.stationRadius())
			copSpawnManager.assignStation(copSpawnManager.ID, station.getId());
	}
}
