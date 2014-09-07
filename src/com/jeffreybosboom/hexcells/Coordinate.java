package com.jeffreybosboom.hexcells;

import static com.google.common.base.Preconditions.checkArgument;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * A point on the hex tiling in
 * {@linkplain http://www.redblobgames.com/grids/hexagons/#coordinates cube
 * coordinates}.
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 9/6/2014
 */
public final class Coordinate {
	private final int x, y, z;
	private Coordinate(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
		checkArgument(x + y + z == 0, toString());
	}
	public static Coordinate at(int x, int y, int z) {
		//TODO: consider interning
		return new Coordinate(x, y, z);
	}

	public int x() {
		return x;
	}
	public int y() {
		return y;
	}
	public int z() {
		return z;
	}

	private Coordinate translate(int[] c) {
		assert c.length == 3 : Arrays.toString(c);
		return translate(c[0], c[1], c[2]);
	}
	public Coordinate translate(int dx, int dy, int dz) {
		return at(x() + dx, y() + dy, z() + dz);
	}

	//clockwise from top
	private final int[][] NEIGHBORS = {
		{0, 1, -1}, {1, 0, -1}, {1, -1, 0}, {0, -1, 1}, {-1, 0, 1}, {-1, 1, 0}
	};
	public Stream<Coordinate> neighbors() {
		return Arrays.stream(NEIGHBORS).map(this::translate);
	}
	public Coordinate up() {
		return translate(NEIGHBORS[0]);
	}
	public Coordinate upRight() {
		return translate(NEIGHBORS[1]);
	}
	public Coordinate downRight() {
		return translate(NEIGHBORS[2]);
	}
	public Coordinate down() {
		return translate(NEIGHBORS[3]);
	}
	public Coordinate downLeft() {
		return translate(NEIGHBORS[4]);
	}
	public Coordinate upLeft() {
		return translate(NEIGHBORS[5]);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final Coordinate other = (Coordinate)obj;
		if (this.x != other.x)
			return false;
		if (this.y != other.y)
			return false;
		if (this.z != other.z)
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 89 * hash + this.x;
		hash = 89 * hash + this.y;
		hash = 89 * hash + this.z;
		return hash;
	}

	@Override
	public String toString() {
		return String.format("(%d, %d, %d)", x(), y(), z());
	}
}
