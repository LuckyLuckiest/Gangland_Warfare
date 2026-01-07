package me.luckyraven.loot.handler;

import java.util.List;
import java.util.function.Consumer;

public abstract class LootChestHandler<T> {

	private final List<Consumer<T>> handlers;

	public LootChestHandler(List<Consumer<T>> handlers) {
		this.handlers = handlers;
	}

	public void addHandler(Consumer<T> handler) {
		handlers.add(handler);
	}

	public void removeHandler(Consumer<T> handler) {
		handlers.remove(handler);
	}

	public void handle(T session) {
		handlers.forEach(handler -> handler.accept(session));
	}

}
