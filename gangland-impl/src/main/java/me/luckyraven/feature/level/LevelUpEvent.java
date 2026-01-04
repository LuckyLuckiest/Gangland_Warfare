package me.luckyraven.feature.level;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

@Getter
@RequiredArgsConstructor
public abstract class LevelUpEvent extends Event implements Cancellable {

	private final Level level;

}
