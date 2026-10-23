package org.luckyraven.gangland.copsncrooks.npc.police.patrol;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PatrolSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.WarningSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;

import java.util.Collection;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Everything the beat cops' {@code PATROL} and {@code WARNING} behaviours need (0.20.0 lane patrol), handed to them
 * through {@code CopSpawnManager#setPatrolServices} so the behaviour factory keeps its shape. Settings are suppliers:
 * a config reload is picked up on the next tick.
 *
 * @param wanted whether a player is wanted (the targeting manager)
 * @param groupOf the squad group hunting a player, or {@code null}
 * @param players the players a cop may scan
 * @param isNpc whether an entity is a Citizens NPC (never warned, never reported)
 */
public record PatrolServices(Supplier<PatrolSettings> patrol, Supplier<WarningSettings> warning,
                             CopPerception perception, Predicate<Player> wanted, Function<UUID, CopGroup> groupOf,
                             BrandishWarnings warnings, CopRadio radio,
                             Supplier<Collection<? extends Player>> players, Predicate<Entity> isNpc,
                             LongSupplier clock, Random random) {
}
