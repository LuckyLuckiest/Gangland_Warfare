public class GeneralTester {

	public static void main(String[] args) {
		String[] values1 = {
				"gangland.command.balance", "gangland.command.bank", "gangland.command.bank.balance",
				"gangland.command.bank.create", "gangland.command.bank.delete", "gangland.command.bank.deposit",
				"gangland.command.bank.withdraw", "gangland.command.bounty", "gangland.command.debug",
				"gangland.command.economy", "gangland.command.economy.deposit", "gangland.command.economy.reset",
				"gangland.command.economy.set", "gangland.command.economy.withdraw", "gangland.command.gang",
				"gangland.command.gang.ally", "gangland.command.gang.ally.abandon", "gangland.command.gang.ally.accept",
				"gangland.command.gang.ally.reject", "gangland.command.gang.ally.request",
				"gangland.command.gang.balance", "gangland.command.gang.color", "gangland.command.gang.create",
				"gangland.command.gang.delete", "gangland.command.gang.demote", "gangland.command.gang.deposit",
				"gangland.command.gang.description", "gangland.command.gang.display",
				"gangland.command.gang.force_rank", "gangland.command.gang.invite",
				"gangland.command.gang.invite.accept", "gangland.command.gang.kick", "gangland.command.gang.leave",
				"gangland.command.gang.promote", "gangland.command.gang.rename", "gangland.command.gang.withdraw",
				"gangland.command.help", "gangland.command.level", "gangland.command.level.experience",
				"gangland.command.level.experience.add", "gangland.command.level.experience.remove",
				"gangland.command.level.level", "gangland.command.level.level.add",
				"gangland.command.level.level.remove", "gangland.command.main", "gangland.command.nbt",
				"gangland.command.option", "gangland.command.rank", "gangland.command.rank.create",
				"gangland.command.rank.delete", "gangland.command.rank.info", "gangland.command.rank.list",
				"gangland.command.rank.parent", "gangland.command.rank.parent.add",
				"gangland.command.rank.parent.remove", "gangland.command.rank.permission",
				"gangland.command.rank.permission.add", "gangland.command.rank.permission.remove",
				"gangland.command.rank.traverse", "gangland.command.reload", "gangland.command.teleport",
				"gangland.command.teleport.cooldown_bypass", "gangland.command.waypoint",
				"gangland.command.waypoint.cooldown", "gangland.command.waypoint.cost",
				"gangland.command.waypoint.create", "gangland.command.waypoint.delete",
				"gangland.command.waypoint.deselect", "gangland.command.waypoint.gang_id",
				"gangland.command.waypoint.info", "gangland.command.waypoint.list", "gangland.command.waypoint.radius",
				"gangland.command.waypoint.select", "gangland.command.waypoint.shield",
				"gangland.command.waypoint.timer", "gangland.command.waypoint.type", "gangland.waypoint.1",
				"gangland.waypoint.2"
		};

		String[] values2 = {
				"gangland.command.balance", "gangland.command.bank", "gangland.command.bank.balance",
				"gangland.command.bank.create", "gangland.command.bank.delete", "gangland.command.bank.deposit",
				"gangland.command.bank.withdraw", "gangland.command.bounty", "gangland.command.debug",
				"gangland.command.economy", "gangland.command.economy.deposit", "gangland.command.economy.reset",
				"gangland.command.economy.set", "gangland.command.economy.withdraw", "gangland.command.gang",
				"gangland.command.gang.ally", "gangland.command.gang.ally.abandon", "gangland.command.gang.ally.accept",
				"gangland.command.gang.ally.reject", "gangland.command.gang.ally.request",
				"gangland.command.gang.balance", "gangland.command.gang.color", "gangland.command.gang.create",
				"gangland.command.gang.delete", "gangland.command.gang.demote", "gangland.command.gang.deposit",
				"gangland.command.gang.description", "gangland.command.gang.display",
				"gangland.command.gang.force_rank", "gangland.command.gang.invite",
				"gangland.command.gang.invite.accept", "gangland.command.gang.kick", "gangland.command.gang.leave",
				"gangland.command.gang.promote", "gangland.command.gang.rename", "gangland.command.gang.withdraw",
				"gangland.command.help", "gangland.command.level", "gangland.command.level.experience",
				"gangland.command.level.experience.add", "gangland.command.level.experience.remove",
				"gangland.command.level.level", "gangland.command.level.level.add",
				"gangland.command.level.level.remove", "gangland.command.main", "gangland.command.nbt",
				"gangland.command.option", "gangland.command.rank", "gangland.command.rank.create",
				"gangland.command.rank.delete", "gangland.command.rank.info", "gangland.command.rank.list",
				"gangland.command.rank.parent", "gangland.command.rank.parent.add",
				"gangland.command.rank.parent.remove", "gangland.command.rank.permission",
				"gangland.command.rank.permission.add", "gangland.command.rank.permission.remove",
				"gangland.command.rank.traverse", "gangland.command.reload", "gangland.command.teleport",
				"gangland.command.teleport.cooldown_bypass", "gangland.command.waypoint",
				"gangland.command.waypoint.cooldown", "gangland.command.waypoint.cost",
				"gangland.command.waypoint.create", "gangland.command.waypoint.delete",
				"gangland.command.waypoint.deselect", "gangland.command.waypoint.gang_id",
				"gangland.command.waypoint.info", "gangland.command.waypoint.list", "gangland.command.waypoint.radius",
				"gangland.command.waypoint.select", "gangland.command.waypoint.shield",
				"gangland.command.waypoint.timer", "gangland.command.waypoint.type", "gangland.waypoint.1",
				"gangland.waypoint.2"
		};

		boolean equal = true;

		System.out.println(values1.length + " " + values2.length);

		for (int i = 0; i < values1.length; i++)
			if (!values1[i].equals(values2[i])) {
				equal = false;
				System.out.println(values1[i] + " " + values2[i]);
				break;
			}

		System.out.println(equal);
	}

}
