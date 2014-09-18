package com.jeffreybosboom.hexcells.rules;

import com.google.common.collect.Sets;
import com.jeffreybosboom.hexcells.AxisConstraint;
import com.jeffreybosboom.hexcells.CellConstraint;
import com.jeffreybosboom.hexcells.CellState;
import com.jeffreybosboom.hexcells.Constraint;
import com.jeffreybosboom.hexcells.Coordinate;
import com.jeffreybosboom.hexcells.Puzzle;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
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
				if (isContiguous(t, cons, Sets.union(present, s)) != cons.isContiguous()) continue;
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

	private static boolean isContiguous(Puzzle p, Constraint cons, Set<Coordinate> present) {
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
			ToIntFunction<Coordinate> axisExtractor = ((AxisConstraint)cons).axisExtractor();
			ToIntFunction<Coordinate> sortExtractor = ((AxisConstraint)cons).sortAxisExtractor();
			int axisValue = axisExtractor.applyAsInt(cons.region().get(0));
			List<Coordinate> axis = p.cells()
					.filter(c -> axisExtractor.applyAsInt(c) == axisValue)
					.sorted(Comparator.comparingInt(sortExtractor))
					.collect(Collectors.toList());
			IntSummaryStatistics stats = axis.stream()
					.filter(c -> p.isPresent(c) || present.contains(c))
					.mapToInt(axis::indexOf)
					.summaryStatistics();
			assert stats.getMin() != -1;
			return (stats.getMax() - stats.getMin() + 1) == cons.target();
		} else
			throw new UnsupportedOperationException(cons.getClass().getSimpleName()+" "+cons);
	}
}
