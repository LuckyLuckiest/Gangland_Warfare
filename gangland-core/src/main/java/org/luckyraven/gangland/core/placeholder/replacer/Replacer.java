package org.luckyraven.gangland.core.placeholder.replacer;

import lombok.Getter;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.core.placeholder.PlaceholderRequest;

public interface Replacer {

	String apply(OfflinePlayer player, @NotNull String text, PlaceholderRequest request);

	@Getter
	enum Closure {

		PERCENT('%', '%'),
		BRACKET('{', '}');

		private final char head, tail;

		Closure(char head, char tail) {
			this.head = head;
			this.tail = tail;
		}

	}

}
