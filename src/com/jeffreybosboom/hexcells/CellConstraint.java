package com.jeffreybosboom.hexcells;

import java.util.List;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 9/17/2014
 */
public class CellConstraint extends Constraint {
	//Not always (or usually) a center, but all cells in the region are radius 1 from here.
	private final Coordinate center;
	public CellConstraint(Coordinate center, List<Coordinate> region, int target, boolean contiguous, boolean discontiguous) {
		super(region, target, contiguous, discontiguous);
		this.center = center;
	}

	public Coordinate center() {
		return center;
	}
}
