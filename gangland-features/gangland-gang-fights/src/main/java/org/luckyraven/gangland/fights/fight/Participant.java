package org.luckyraven.gangland.fights.fight;

import java.util.UUID;

public record Participant(UUID player, int gangId, FightSide side) {
}
