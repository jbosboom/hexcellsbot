package com.jeffreybosboom.hexcells;

import static com.google.common.base.Preconditions.checkState;
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
	private final ImmutableMap<Coordinate, CellState> cells;
	private final ImmutableSet<Constraint> constraints;
	public Puzzle(Map<Coordinate, CellState> cells, Set<Constraint> constraints) {
		this.cells = ImmutableMap.copyOf(cells);
		this.constraints = ImmutableSet.copyOf(constraints);
	}

	public Stream<Coordinate> cells() {
		return cells.keySet().stream();
	}
	public CellState at(Coordinate coordinate) {
		return cells.get(coordinate);
	}
	public Stream<Constraint> constraints() {
		return constraints.stream();
	}

	//for Stream.filter
	public boolean isPresent(Coordinate c) {
		return at(c) == CellState.PRESENT;
	}
	public boolean isAbsent(Coordinate c) {
		return at(c) == CellState.ABSENT;
	}
	public boolean isUnknown(Coordinate c) {
		return at(c) == CellState.UNKNOWN;
	}
	public boolean isKnown(Coordinate c) {
		return !isUnknown(c);
	}

	public boolean isSolved() {
		return cells().allMatch(this::isKnown);
	}

	public Puzzle refine(Coordinate cell, CellState kind) {
		checkState(isUnknown(cell));
		ImmutableMap.Builder<Coordinate, CellState> newCells = ImmutableMap.builder();
		cells().filter(c -> !c.equals(cell))
				.forEach(c -> newCells.put(c, at(c)));
		newCells.put(cell, kind);
		return new Puzzle(newCells.build(), constraints);
	}

	public Puzzle constrain(Constraint constraint) {
		return new Puzzle(cells, ImmutableSet.<Constraint>builder().addAll(constraints).add(constraint).build());
	}

	//TODO: discharge constraints when satisfied?
}
