package org.luckyraven.gangland.fights.arena;

import java.util.List;

/** A finished arena: its box, a spawn list per side, where fighters return to, and whether it is open. */
public record Arena(String name, ArenaBox box, List<ArenaPoint> spawnsA, List<ArenaPoint> spawnsB, ArenaPoint exit,
                    boolean enabled) {
}
