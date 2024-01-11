package me.luckyraven.command.argument;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class ArgumentSpecifier {

	private final Map<String, Supplier<List<Player>>> specifiers;
	private final CommandSender                       commandSender;

	public ArgumentSpecifier(CommandSender commandSender) {
		this.specifiers = new HashMap<>();
		this.commandSender = commandSender;
	}

	public void initialize(String target) {
		collectSpecifiers(target);
	}

	public Map<String, Supplier<List<Player>>> getSpecifiers() {
		return Collections.unmodifiableMap(specifiers);
	}

	private void collectSpecifiers(@Nullable final String target) {
		allPlayers();
		randomPlayer();
		nearestPlayer();
		senderSpecifier();

		String modifiedTarget = target;

		if (target == null || target.isEmpty()) modifiedTarget = "@me";
		if (!modifiedTarget.startsWith("@")) modifiedTarget = "@" + target;
		targetSpecifier(modifiedTarget);

		if (!specifiers.containsKey(modifiedTarget))
			throw new IllegalArgumentException("Unable to identify this specifier!");
	}

	private void allPlayers() {
		specifiers.put("@a", () -> new ArrayList<>(Bukkit.getOnlinePlayers()));
	}

	private void randomPlayer() {
		specifiers.put("@r", () -> {
			List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
			if (!players.isEmpty()) {
				Player random = players.get(new Random().nextInt(players.size()));
				return Collections.singletonList(random);
			}
			return Collections.emptyList();
		});
	}

	private void nearestPlayer() {
		specifiers.put("@p", () -> {
			Player nearestPlayer   = null;
			double closestDistance = Double.MAX_VALUE;
			if (commandSender instanceof Player pl) for (Player player : Bukkit.getServer().getOnlinePlayers()) {
				double distance = player.getLocation().distanceSquared(pl.getLocation());
				if (distance < closestDistance) {
					closestDistance = distance;
					nearestPlayer = player;
				}
			}
			List<Player> selectedPlayers = new ArrayList<>();
			if (nearestPlayer != null) selectedPlayers.add(nearestPlayer);
			return selectedPlayers;
		});
	}

	private void senderSpecifier() {
		specifiers.put("@me", () -> {
			List<Player> players = new ArrayList<>();
			if (commandSender instanceof Player player) players.add(player);
			return players;
		});
	}

	private void targetSpecifier(String target) {
		List<Player> players = new ArrayList<>();
		for (Player player : Bukkit.getOnlinePlayers())
			if (player.getName().equalsIgnoreCase(target.substring(1))) {
				players.add(player);
				break;
			}

		if (!players.isEmpty()) specifiers.put(target, () -> players);
	}

}
