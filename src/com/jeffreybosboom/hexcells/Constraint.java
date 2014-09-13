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
	private final ImmutableList<Cell> region;
	private final int target;
	private final boolean contiguous, discontiguous;
	public Constraint(List<Cell> region, int target, boolean contiguous, boolean discontiguous) {
		checkArgument(!(contiguous && discontiguous));
		checkArgument(target > -1 && target <= region.size());
		this.region = ImmutableList.copyOf(region);
		this.target = target;
		this.contiguous = contiguous;
		this.discontiguous = discontiguous;
	}

	public ImmutableList<Cell> region() {
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
		return String.format("%d: %s", target(), region());
	}
}
