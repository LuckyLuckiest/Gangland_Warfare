package me.luckyraven.inventory.condition;

import lombok.Getter;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.InventoryOpener;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents conditional data for a slot that can have True/False branches
 */
@Getter
public class ConditionalSlotData {

	private final SlotCondition condition;
	private final BranchData    trueData;
	private final BranchData    falseData;

	public ConditionalSlotData(SlotCondition condition, BranchData trueData, BranchData falseData) {
		this.condition = condition;
		this.trueData  = trueData;
		this.falseData = falseData;
	}

	/**
	 * Resolves which branch to use based on condition evaluation
	 */
	public BranchData resolve(Player player, ConditionEvaluator evaluator) {
		return condition.evaluate(player, evaluator) ? trueData : falseData;
	}

	/**
	 * Represents a click action which can be command, inventory, or anvil
	 */
	public interface ClickAction {
		void execute(Player player, InventoryHandler handler, ItemBuilder builder, InventoryOpener opener);
	}

	/**
	 * Represents a branch (True or False) which can have its own nested conditions
	 */
	@Getter
	public static class BranchData {
		private final ItemBuilder         item;
		private final String              name;
		private final List<String>        lore;
		private final boolean             clickable;
		private final boolean             draggable;
		private final ClickAction         clickAction;
		private final ConditionalSlotData nestedCondition;

		public BranchData(ItemBuilder item, String name, List<String> lore, boolean clickable, boolean draggable,
						  @Nullable ClickAction clickAction, @Nullable ConditionalSlotData nestedCondition) {
			this.item            = item;
			this.name            = name;
			this.lore            = lore;
			this.clickable       = clickable;
			this.draggable       = draggable;
			this.clickAction     = clickAction;
			this.nestedCondition = nestedCondition;
		}

		/**
		 * Resolves the final data, following nested conditions if present
		 */
		public BranchData resolveFinal(Player player, ConditionEvaluator evaluator) {
			if (nestedCondition != null) {
				return nestedCondition.resolve(player, evaluator).resolveFinal(player, evaluator);
			}
			return this;
		}
	}

	public record CommandAction(String command) implements ClickAction {
		@Override
		public void execute(Player player, InventoryHandler handler, ItemBuilder builder, InventoryOpener opener) {
			String cmd = command.startsWith("/") ? command.substring(1) : command;
			player.performCommand(cmd);
		}
	}

	public record InventoryAction(String inventoryName) implements ClickAction {
		@Override
		public void execute(Player player, InventoryHandler handler, ItemBuilder builder, InventoryOpener opener) {
			opener.openInventory(player, inventoryName);
		}
	}

	public record AnvilAction(String title, String text, String successCommand) implements ClickAction {
		@Override
		public void execute(Player player, InventoryHandler handler, ItemBuilder builder, InventoryOpener opener) {
			// Will be handled by InventoryBuilder with AnvilGUI
		}
	}
}
