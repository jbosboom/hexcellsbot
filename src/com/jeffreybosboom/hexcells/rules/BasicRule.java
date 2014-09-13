package com.jeffreybosboom.hexcells.rules;

import com.jeffreybosboom.hexcells.CellState;
import com.jeffreybosboom.hexcells.Coordinate;
import com.jeffreybosboom.hexcells.Puzzle;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * If a constraint has met its target, all other hexes are absent; if a group
 * has unknown hexes equal to its target minus present hexes, all other hexes
 * are present.
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 9/13/2014
 */
public final class BasicRule implements Function<Puzzle, Puzzle> {
	@Override
	public Puzzle apply(Puzzle t) {
		Set<Coordinate> markAbsent = new LinkedHashSet<>(), markPresent = new LinkedHashSet<>();
		t.constraints().forEachOrdered(c -> {
			int present = (int)c.region().stream().filter(t::isPresent).count();
			int absent = (int)c.region().stream().filter(t::isAbsent).count();
			int known = present + absent;
			int unknown = c.region().size() - known;

			if (present == c.target())
				c.region().stream().filter(t::isUnknown).forEachOrdered(markAbsent::add);
			else if (present + unknown == c.target())
				c.region().stream().filter(t::isUnknown).forEachOrdered(markPresent::add);
		});

		Puzzle p = t;
		for (Coordinate c : markAbsent)
			p = p.refine(c, CellState.ABSENT);
		for (Coordinate c : markPresent)
			p = p.refine(c, CellState.PRESENT);
		return p;
	}
}
