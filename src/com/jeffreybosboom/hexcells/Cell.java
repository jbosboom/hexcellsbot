package com.jeffreybosboom.hexcells;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 9/6/2014
 */
public final class Cell {
	public enum Kind {PRESENT, ABSENT, UNKNOWN};
	private final Coordinate coordinate;
	private final Kind kind;
	private final Region.Point pixelCentroid;
	//TODO: if generated a constraint, a field for the constraint?

	public Cell(Coordinate coordinate, Kind kind, Region.Point centroid) {
		this.coordinate = coordinate;
		this.kind = kind;
		this.pixelCentroid = centroid;
	}

	public Coordinate where() {
		return coordinate;
	}

	public Kind kind() {
		return kind;
	}

	public Region.Point pixelCentroid() {
		return pixelCentroid;
	}

	public Cell refine(Kind kind) {
		checkState(kind() == Kind.UNKNOWN);
		checkArgument(kind != Kind.UNKNOWN);
		return new Cell(where(), kind, pixelCentroid());
	}

	@Override
	public String toString() {
		return String.format("%s%s", where(), kind().toString().charAt(0));
	}
}
