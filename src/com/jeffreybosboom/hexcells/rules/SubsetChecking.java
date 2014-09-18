package com.jeffreybosboom.hexcells.rules;

import com.google.common.collect.Sets;
import com.jeffreybosboom.hexcells.AxisConstraint;
import com.jeffreybosboom.hexcells.CellConstraint;
import com.jeffreybosboom.hexcells.CellState;
import com.jeffreybosboom.hexcells.Constraint;
import com.jeffreybosboom.hexcells.Coordinate;
import com.jeffreybosboom.hexcells.Puzzle;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Check all N-subsets of a region needing N hexes to reach its target.  If any
 * hexes are always present or absent, mark them present or absent.
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 9/13/2014
 */
public final class SubsetChecking implements Function<Puzzle, Puzzle> {
	@Override
	public Puzzle apply(Puzzle t) {
		for (Iterator<Constraint> iter = t.constraints().iterator(); iter.hasNext();) {
			Constraint cons = iter.next();
			if (!(cons.isContiguous() || cons.isDiscontiguous())) continue;
			Set<Coordinate> present = cons.region().stream().filter(t::isPresent).collect(Collectors.toSet());
			Set<Coordinate> unknown = cons.region().stream().filter(t::isUnknown).collect(Collectors.toSet());
			int deficit = cons.target() - present.size();

			Set<Coordinate> alwaysPresent = new HashSet<>(unknown), alwaysAbsent = new HashSet<>(unknown);
			for (Set<Coordinate> s : Sets.powerSet(unknown)) {
				if (s.size() != deficit) continue;
				if (isContiguous(cons, Sets.union(present, s)) != cons.isContiguous()) continue;
				alwaysAbsent.removeAll(s);
				alwaysPresent.removeAll(Sets.difference(unknown, s));
			}

			for (Coordinate c : alwaysPresent)
				t = t.refine(c, CellState.PRESENT);
			for (Coordinate c : alwaysAbsent)
				t = t.refine(c, CellState.ABSENT);
		}
		return t;
	}

	private boolean isContiguous(Constraint cons, Set<Coordinate> present) {
		if (cons instanceof CellConstraint) {
			//set is contiguous if we can get anywhere from anywhere, so flood fill
			//from an arbitrary point
			Set<Coordinate> closed = new HashSet<>();
			Deque<Coordinate> frontier = new ArrayDeque<>();
			frontier.add(present.iterator().next());
			while (!frontier.isEmpty()) {
				Coordinate c = frontier.pop();
				if (closed.add(c))
					c.neighbors().filter(present::contains).forEachOrdered(frontier::add);
			}
			return closed.equals(present);
		} else if (cons instanceof AxisConstraint) {
			throw new UnsupportedOperationException("TODO");
		} else
			throw new UnsupportedOperationException(cons.getClass().getSimpleName()+" "+cons);
	}
}
