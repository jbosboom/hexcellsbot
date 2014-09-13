package com.jeffreybosboom.hexcells;

import com.google.common.collect.ImmutableBiMap;
import java.awt.Color;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 9/11/2014
 */
public final class Colors {
	private Colors() {}

	public static final ImmutableBiMap<Color, Cell.Kind> HEXAGON_BORDER_COLORS = ImmutableBiMap.of(
			//These are the colors of the hexagon colored borders, not their
			//centers, as the present hexagons centers are the same color as the
			//remaining/mistake boxes.
			new Color(255, 159, 0), Cell.Kind.UNKNOWN,
			new Color(44, 47, 49), Cell.Kind.ABSENT,
			new Color(20, 156, 216), Cell.Kind.PRESENT
	);
	public static final ImmutableBiMap<Color, Cell.Kind> HEXAGON_INTERIOR_COLORS = ImmutableBiMap.of(
			new Color(255, 175, 41), Cell.Kind.UNKNOWN,
			new Color(62, 62, 62), Cell.Kind.ABSENT,
			new Color(5, 164, 235), Cell.Kind.PRESENT
	);
	public static final Color REMAINING_BOX = new Color(5, 164, 235); //also the mistake box
}
