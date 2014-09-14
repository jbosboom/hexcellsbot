package com.jeffreybosboom.hexcells;

import static com.google.common.base.Preconditions.checkArgument;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 9/13/2014
 */
public class Constraint {
	private final ImmutableList<Coordinate> region;
	private final int target;
	private final boolean contiguous, discontiguous;
	public Constraint(List<Coordinate> region, int target, boolean contiguous, boolean discontiguous) {
		checkArgument(!(contiguous && discontiguous));
		checkArgument(target > -1 && target <= region.size(), "%s %s", target, region);
		this.region = ImmutableList.copyOf(region);
		this.target = target;
		this.contiguous = contiguous;
		this.discontiguous = discontiguous;
	}

	public ImmutableList<Coordinate> region() {
		return region;
	}

	public int target() {
		return target;
	}

	public boolean isContiguous() {
		return contiguous;
	}

	public boolean isDiscontiguous() {
		return discontiguous;
	}

	@Override
	public String toString() {
		if (isDiscontiguous())
			return String.format("-%d-: %s", target(), region());
		if (isContiguous())
			return String.format("{%d}: %s", target(), region());
		return String.format("%d: %s", target(), region());
	}
}
