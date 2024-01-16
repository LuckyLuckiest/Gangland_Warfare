package me.luckyraven.util.timer;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

public class SequenceTimer extends Timer {

	private final Queue<IntervalTaskPair> intervalTaskQueue;
	private final List<IntervalTaskPair>  intervalTaskPairs;

	private @Getter long currentInterval, totalInterval;
	private @Getter @Setter Mode             mode;
	private                 IntervalTaskPair currentTask;
	private                 boolean          added;

	public SequenceTimer(JavaPlugin plugin) {
		this(plugin, 0L, 20L);
	}

	public SequenceTimer(JavaPlugin plugin, long delay, long period) {
		this(plugin, delay, period, Mode.NORMAL);
	}

	public SequenceTimer(JavaPlugin plugin, long delay, long period, Mode mode) {
		super(plugin, delay, period);

		this.intervalTaskQueue = new LinkedList<>();
		this.intervalTaskPairs = new ArrayList<>();
		this.mode = mode;
		this.currentInterval = this.totalInterval = 0L;
		this.added = false;
	}

	public void addIntervalTaskPair(long interval, Consumer<SequenceTimer> task) {
		IntervalTaskPair intervalTaskPair = new IntervalTaskPair(interval, task);

		intervalTaskQueue.add(intervalTaskPair);
		intervalTaskPairs.add(intervalTaskPair);
		if (currentTask == null) currentTask = intervalTaskQueue.poll();
		totalInterval += interval;
	}

	@Override
	public void run() {
		currentInterval = (currentInterval + 1) % totalInterval;

		// cancel the timer if it was stopped
		if (!isRunning() || intervalTaskQueue.isEmpty() || currentTask == null) {
			stop();
			return;
		}

		++currentTask.currentInterval;

		// run the task only when it reaches its interval
		if (currentTask.getInterval() == currentTask.getCurrentInterval()) {
			do {
				// run the task
				currentTask.runTask();

				// check if the task was completed
				if (currentTask.isCompleted()) {
					// change the state of the currentTask
					currentTask.reset();
					// get the head of the queue and remove it
					currentTask = intervalTaskQueue.poll();

					if (mode == Mode.CIRCULAR)
						// move the task at the end (important for circular timer)
						intervalTaskQueue.add(currentTask);
				}
			} while (currentTask != null && currentTask.getInterval() == 0);
		}
	}

	@Override
	public void start(boolean async) {
		super.start(async);

		// add the currentTask to the queue
		if (!added) {
			intervalTaskQueue.add(currentTask);
			added = true;
		}
	}

	public void reset() {
		intervalTaskQueue.clear();
		intervalTaskQueue.addAll(intervalTaskPairs);
		currentTask = intervalTaskQueue.poll();
		currentInterval = 0L;
		added = false;
	}

	public long getTaskInterval() {
		return currentTask.getInterval();
	}

	public enum Mode {
		NORMAL,
		CIRCULAR
	}

	private class IntervalTaskPair {

		private final @Getter long                    interval;
		private final         Consumer<SequenceTimer> task;

		private @Getter boolean completed;
		private @Getter long    currentInterval;

		public IntervalTaskPair(long interval, Consumer<SequenceTimer> task) {
			this.interval = interval;
			this.task = task;
			this.completed = false;
			this.currentInterval = 0L;
		}

		public void runTask() {
			task.accept(SequenceTimer.this);
			completed = true;
		}

		public void reset() {
			completed = false;
			currentInterval = 0L;
		}

	}

}
