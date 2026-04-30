package org.luckyraven.gangland.inventory.flow;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.inventory.InventoryHandler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Hosts a sequence of {@link Panel}s that share a single typed {@link FlowSession} and one open inventory window. Use
 * this instead of the legacy chain of {@code player.closeInventory(); next.open(player)} transitions — the session
 * survives panel swaps, and panels navigate via {@link #switchTo(String)}/{@link #back()} rather than opening each
 * other.
 *
 * <h2>Bukkit size constraint</h2>
 * A Bukkit {@code Inventory}'s size is fixed at construction. When {@link #switchTo(String)} targets a panel with a
 * different size or title, the host builds a new {@link InventoryHandler}, re-renders into it, and calls
 * {@link InventoryHandler#open(Player)} — which closes the previous inventory. The {@link #suppressClose()} latch makes
 * the flow's own close listener ignore that handoff so the session is not torn down mid-switch.
 *
 * <h2>Anvil / side-effect detours</h2>
 * Features that briefly open an external UI (AnvilGUI, chat prompt) should wrap the side-effect with {@link #suspend()}
 * and {@link #resume()} so the flow's close listener does not clean up while the side-effect is showing. Re-enter the
 * flow by calling {@link #switchTo(String)} in the side-effect's completion callback.
 */
public final class MultiPanelInventory<S extends FlowSession> implements Listener {

	private final JavaPlugin            plugin;
	private final Player                viewer;
	private final S                     session;
	private final Map<String, Panel<S>> panels    = new LinkedHashMap<>();
	private final Deque<String>         backStack = new ArrayDeque<>();

	private InventoryHandler current;
	private String           currentId;
	@Getter
	private boolean          switching;
	@Getter
	private boolean          suspended;
	private boolean          ended;
	private Consumer<S>      onEnd;

	public MultiPanelInventory(JavaPlugin plugin, Player viewer, S session) {
		this.plugin  = plugin;
		this.viewer  = viewer;
		this.session = session;
	}

	public MultiPanelInventory<S> register(String id, Panel<S> panel) {
		panels.put(id, panel);
		return this;
	}

	public MultiPanelInventory<S> onEnd(Consumer<S> callback) {
		this.onEnd = callback;
		return this;
	}

	public Player viewer() {
		return viewer;
	}

	public S session() {
		return session;
	}

	public InventoryHandler handler() {
		return current;
	}

	public String currentPanelId() {
		return currentId;
	}

	/**
	 * Opens the flow at the given panel id. Registers the close listener if this is the first open.
	 */
	public void openAt(String id) {
		if (ended) return;
		if (current == null) Bukkit.getPluginManager().registerEvents(this, plugin);
		switchInternal(id, false);
	}

	/**
	 * Pushes the current panel onto the back-stack and switches to {@code id}.
	 */
	public void switchTo(String id) {
		switchInternal(id, true);
	}

	/**
	 * Pops the back-stack and switches to the previous panel; if empty, calls {@link #end()}.
	 */
	public void back() {
		if (backStack.isEmpty()) {
			end();
			return;
		}
		String prev = backStack.pop();
		switchInternal(prev, false);
	}

	/**
	 * Re-renders the currently active panel against the same inventory handle.
	 */
	public void rerender() {
		if (currentId == null || current == null) return;
		Panel<S> panel = panels.get(currentId);
		if (panel == null) return;
		current.clear();
		panel.render(this, current, viewer, session);
	}

	/**
	 * Suspends the flow while an external UI (anvil, chat, BarterView, etc.) steals the viewer's screen. Nulls out
	 * {@code current} so the close event Bukkit fires when that external UI opens is a no-op, and so the subsequent
	 * {@link #switchTo(String)} after {@link #resume()} always takes the rebuild path — the old Bukkit inventory handle
	 * is closed and cannot be reopened in-place.
	 */
	public void suspend() {
		suspended = true;
		current   = null;
	}

	/**
	 * Resumes the flow after {@link #suspend()}; call before re-opening a panel from an anvil completion.
	 */
	public void resume() {
		suspended = false;
	}

	/**
	 * Internal latch checked by the close listener — true while the host is swapping inventories or suspended.
	 */
	public boolean suppressClose() {
		return switching || suspended;
	}

	/**
	 * Ends the flow, closes the inventory, fires {@link #onEnd(Consumer)}, and unregisters the close listener.
	 */
	public void end() {
		if (ended) return;
		ended     = true;
		switching = true;
		if (current != null) viewer.closeInventory();
		switching = false;
		cleanup();
	}

	// Bukkit listener — ends the flow when the viewer naturally closes the inventory (ESC, /invsee, etc.). Swaps
	// between panels and side-effect detours set suppressClose() so this callback no-ops.
	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (suppressClose()) return;
		if (!(event.getPlayer() instanceof Player closer) || !closer.equals(viewer)) return;
		if (current == null) return;
		if (!event.getView().getTopInventory().equals(current.getInventory())) return;
		ended = true;
		cleanup();
	}

	private void switchInternal(String id, boolean trackHistory) {
		if (ended) return;
		Panel<S> panel = panels.get(id);
		if (panel == null) return;

		int    size  = panel.size(session);
		String title = panel.title(session);

		if (trackHistory && currentId != null && !id.equals(currentId)) backStack.push(currentId);

		if (current != null && current.getSize() == size && title.equals(current.getDisplayTitle())) {
			currentId = id;
			current.clear();
			panel.render(this, current, viewer, session);
			return;
		}

		switching = true;
		InventoryHandler next = new InventoryHandler(plugin, title, size, viewer);
		currentId = id;
		current   = next;
		panel.render(this, current, viewer, session);
		next.open(viewer);
		switching = false;
	}

	private void cleanup() {
		current   = null;
		currentId = null;
		backStack.clear();
		HandlerList.unregisterAll(this);
		if (onEnd != null) {
			Consumer<S> cb = onEnd;
			onEnd = null;
			cb.accept(session);
		}
	}

}
