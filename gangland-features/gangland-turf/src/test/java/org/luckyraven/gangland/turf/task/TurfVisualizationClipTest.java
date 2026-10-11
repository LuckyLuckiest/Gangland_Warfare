package org.luckyraven.gangland.turf.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TF-43 / TF-21 (viz half): the outline is clipped to the viewer's surroundings and bounded per refresh. */
@DisplayName("TurfVisualization - clipped, bounded outline points")
class TurfVisualizationClipTest {

	private static final double RADIUS = 40.0;
	private static final int    BUDGET = 600;

	@Test
	@DisplayName("a 512 x 512 turf with the viewer on an edge stays within the budget")
	void hugeTurf_pointCountBounded() {
		List<double[]> points = TurfVisualization.edgePoints(0, 55, 0, 512, 75, 512, 256, 65, 0, RADIUS, BUDGET);
		assertTrue(points.size() > 0, "the near part of the edge is drawn");
		assertTrue(points.size() <= BUDGET, "points " + points.size() + " exceed the budget");
	}

	@Test
	@DisplayName("no point lies farther than the radius horizontally")
	void everyPointWithinRadius() {
		List<double[]> points = TurfVisualization.edgePoints(0, 55, 0, 512, 75, 512, 256, 65, 10, RADIUS, BUDGET);
		assertFalse(points.isEmpty());
		for (double[] p : points) {
			assertTrue(Math.hypot(p[0] - 256, p[2] - 10) <= RADIUS + 1e-9, "point out of range");
		}
	}

	@Test
	@DisplayName("a viewer 200 blocks away gets nothing")
	void viewerFarAway_noPoints() {
		assertTrue(TurfVisualization.edgePoints(0, 55, 0, 512, 75, 512, 256, 65, -200, RADIUS, BUDGET).isEmpty());
	}

	@Test
	@DisplayName("a 16 x 16 turf next to the viewer is drawn whole at the 0.5 step")
	void smallTurf_fullOutlineAtMinStep() {
		List<double[]> points = TurfVisualization.edgePoints(0, 55, 0, 16, 75, 16, 8, 65, 8, RADIUS, BUDGET);
		assertTrue(points.stream().anyMatch(p -> p[0] == 0.5 && p[1] == 55 && p[2] == 0), "step 0.5 along an edge");
		assertTrue(points.stream().anyMatch(p -> p[0] == 16 && p[1] == 55 && p[2] == 16), "far corner reached");
		assertTrue(points.stream().anyMatch(p -> p[0] == 0 && p[1] == 60 && p[2] == 0), "pillar drawn");
	}

	@Test
	@DisplayName("a clipped outline over the budget at step 0.5 grows the step to stay within it")
	void clippedOutlineOverBudget_stepGrows() {
		List<double[]> p = TurfVisualization.edgePoints(0, 55, 0, 60, 75, 60, 30, 65, 30, RADIUS, BUDGET);
		assertTrue(p.size() <= BUDGET && p.size() > BUDGET / 2, "points " + p.size());
	}
}
