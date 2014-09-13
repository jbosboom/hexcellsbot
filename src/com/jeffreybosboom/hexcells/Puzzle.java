package com.jeffreybosboom.hexcells;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 9/6/2014
 */
public final class Puzzle {
	private final ImmutableMap<Coordinate, Cell> cells;
	private final ImmutableSet<Constraint> constraints;
	public Puzzle(Map<Coordinate, Cell> cells, Set<Constraint> constraints) {
		this.cells = ImmutableMap.copyOf(cells);
		this.constraints = ImmutableSet.copyOf(constraints);
	}

	public Stream<Cell> cells() {
		return cells.values().stream();
	}
	public Cell at(Coordinate coordinate) {
		return cells.get(coordinate);
	}
	public Stream<Constraint> constraints() {
		return constraints.stream();
	}

	public Puzzle refine(Cell cell, Cell.Kind kind) {
		ImmutableMap.Builder<Coordinate, Cell> newCells = ImmutableMap.builder();
		cells().filter(c -> c.where().equals(cell.where()))
				.forEach(c -> newCells.put(c.where(), c));
		newCells.put(cell.where(), cell.refine(kind));
		return new Puzzle(newCells.build(), constraints);
	}

	public Puzzle constrain(Constraint constraint) {
		return new Puzzle(cells, ImmutableSet.<Constraint>builder().addAll(constraints).add(constraint).build());
	}

	//TODO: discharge constraints when satisfied?
}
