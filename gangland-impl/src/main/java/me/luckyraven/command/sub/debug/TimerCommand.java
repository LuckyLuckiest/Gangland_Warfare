package me.luckyraven.command.sub.debug;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.util.timer.SequenceTimer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class TimerCommand extends CommandHandler {

	private final Map<CommandSender, SequenceTimer> timerMap;
	private final Map<CommandSender, SequenceTimer> startedTimers;

	public TimerCommand(Gangland gangland) {
		super(gangland, "timer", false);

		this.timerMap      = new HashMap<>();
		this.startedTimers = new HashMap<>();
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		SequenceTimer timer = timerMap.get(commandSender);

		if (timer == null) {
			commandSender.sendMessage("No active timer");
			return;
		}

		commandSender.sendMessage("Timer: " + timer, "Running: " + timer.isRunning(), "Mode: " + timer.getMode());
	}

	@Override
	protected void initializeArguments() {
		Argument create = getCreate();

		Argument delete = getDelete();

		Argument mode = getMode();

		Argument start = getStartTimer();

		Argument stop = getStopTimer();

		Argument addInterval = getAddInterval();

		List<Argument> arguments = new ArrayList<>();

		arguments.add(create);
		arguments.add(delete);
		arguments.add(mode);
		arguments.add(start);
		arguments.add(stop);
		arguments.add(addInterval);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) { }

	private @NotNull Argument getCreate() {
		return new Argument(getGangland(), "create", getArgumentTree(), (argument, sender, args) -> {
			SequenceTimer timer = new SequenceTimer(getGangland(), 0L, 20L, SequenceTimer.Mode.CIRCULAR);

			timerMap.put(sender, timer);
			sender.sendMessage("Added " + timer);
		});
	}

	private @NotNull Argument getDelete() {
		return new Argument(getGangland(), "delete", getArgumentTree(), (argument, sender, args) -> {
			SequenceTimer timer = timerMap.remove(sender);

			startedTimers.remove(sender);
			sender.sendMessage("Removed " + timer);
		});
	}

	private @NotNull Argument getMode() {
		Argument mode = new Argument(getGangland(), "mode", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage("Missing argument, <mode>");
		});

		Argument modeOptional = new OptionalArgument(getGangland(), getArgumentTree(), (argument, sender, args) -> {
			SequenceTimer timer = timerMap.get(sender);

			if (timer == null) {
				sender.sendMessage("No active timer");
				return;
			}

			String value = args[2];

			SequenceTimer.Mode md;
			try {
				md = SequenceTimer.Mode.valueOf(value.toUpperCase());
			} catch (IllegalArgumentException exception) {
				md = SequenceTimer.Mode.NORMAL;
			}

			timer.setMode(md);
			sender.sendMessage("Updated type to " + md.name());
		}, sender -> {
			SequenceTimer.Mode[] values = SequenceTimer.Mode.values();
			List<String>         list   = Arrays.stream(values).map(SequenceTimer.Mode::name).toList();

			return new ArrayList<>(list);
		});

		mode.addSubArgument(modeOptional);
		return mode;
	}

	private @NotNull Argument getStartTimer() {
		return new Argument(getGangland(), "start", getArgumentTree(), (argument, sender, args) -> {
			SequenceTimer timer = timerMap.get(sender);

			if (timer == null) {
				sender.sendMessage("No active timer");
				return;
			}

			if (startedTimers.containsKey(sender)) {
				SequenceTimer newTimer = timer.copy(getGangland());

				sender.sendMessage("Created a new timer " + newTimer);

				timerMap.put(sender, newTimer);
				timer = newTimer;
			}

			timer.start(false);
			startedTimers.put(sender, timer);

			sender.sendMessage("Started timer");
		});
	}

	private @NotNull Argument getStopTimer() {
		return new Argument(getGangland(), "stop", getArgumentTree(), (argument, sender, args) -> {
			SequenceTimer timer = timerMap.get(sender);

			if (timer == null) {
				sender.sendMessage("No active timer");
				return;
			}

			timer.stop();
			sender.sendMessage("Stopped timer");
		});
	}

	private @NotNull Argument getAddInterval() {
		Argument addInterval = new Argument(getGangland(), "add", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage("Missing argument, <interval>");
		});

		Argument interval = new OptionalArgument(getGangland(), getArgumentTree(), (argument, sender, args) -> {
			String value = args[2];

			int val;
			try {
				val = Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				sender.sendMessage("Not a number");
				return;
			}

			SequenceTimer timer = timerMap.get(sender);

			if (timer == null) {
				sender.sendMessage("No active timer");
				return;
			}

			timer.addIntervalTaskPair(val, t -> sender.sendMessage(
					"Timer: " + t.getCurrentInterval() + ", task: " + t.getTaskInterval() + ", mod: " +
					(t.getTaskInterval() == 0 ? 0 : t.getCurrentInterval() % t.getTaskInterval())));

			sender.sendMessage("Added a new interval, " + val);
		}, sender -> List.of("<interval>"));

		addInterval.addSubArgument(interval);
		return addInterval;
	}

}
