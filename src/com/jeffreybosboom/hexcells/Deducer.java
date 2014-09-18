package com.jeffreybosboom.hexcells;

import com.jeffreybosboom.hexcells.rules.*;
import java.util.function.Function;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 9/13/2014
 */
public final class Deducer {
	private Deducer() {}

	private static final Function<Puzzle, Puzzle> RULES = fixpoint(Function.<Puzzle>identity()
			.andThen(fixpoint(new BasicRule()))
			.andThen(fixpoint(new SubsetChecking()))
	);

	public static Puzzle deduce(Puzzle puzzle) {
		return RULES.apply(puzzle);
	}

	private static <T, R extends T> Function<T, R> fixpoint(Function<T, R> f) {
		return (t) -> {
			T current = t;
			while (true) {
				R next = f.apply(current);
				if (current.equals(next)) return next;
				current = next;
			}
		};
	}
}
