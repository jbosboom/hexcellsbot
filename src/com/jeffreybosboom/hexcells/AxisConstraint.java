package com.jeffreybosboom.hexcells;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 9/17/2014
 */
public class AxisConstraint extends Constraint {
	private final ToIntFunction<Coordinate> axisExtractor;
	private final ToIntFunction<Coordinate> sortAxisExtractor;
	public AxisConstraint(ToIntFunction<Coordinate> axisExtractor, ToIntFunction<Coordinate> sortAxisExtractor,
			List<Coordinate> region, int target, boolean contiguous, boolean discontiguous) {
		super(region, target, contiguous, discontiguous);
		this.axisExtractor = axisExtractor;
		this.sortAxisExtractor = sortAxisExtractor;
	}

	public ToIntFunction<Coordinate> axisExtractor() {
		return axisExtractor;
	}

	public ToIntFunction<Coordinate> sortAxisExtractor() {
		return sortAxisExtractor;
	}
}
