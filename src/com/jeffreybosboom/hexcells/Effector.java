package com.jeffreybosboom.hexcells;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 9/7/2014
 */
public final class Effector {
	private static final Recognizer recognizer = new Recognizer();
	public Effector() {}

	public static Puzzle fromImage(BufferedImage image) {
		ImmutableSet<Region> regions = Region.connectedComponents(image, ContiguousSet.create(Range.all(), DiscreteDomain.integers()));
		List<Region> hexagons = regions.stream()
				.filter(r -> Colors.HEXAGON_BORDER_COLORS.containsKey(r.color()))
				.collect(Collectors.toList());
		int hexWidth = (int)Math.round(hexagons.stream()
				.mapToInt(r -> r.boundingBox().width)
				.average().getAsDouble());
		int hexHeight = (int)Math.round(hexagons.stream()
				.mapToInt(r -> r.boundingBox().height)
				.average().getAsDouble());
//		System.out.println(hexWidth);
//		System.out.println(hexHeight);

		RangeSet<Integer> rowRanges = TreeRangeSet.create();
		hexagons.stream()
				.map(Region::centroid)
				.mapToInt(Region.Point::y)
				.mapToObj(i -> Range.closed(i - hexHeight/4, i + hexHeight/4))
				.forEachOrdered(rowRanges::add);
		List<Range<Integer>> rows = rowRanges.asRanges().stream().collect(Collectors.toList());
		RangeSet<Integer> colRanges = TreeRangeSet.create();
		hexagons.stream()
				.map(Region::centroid)
				.mapToInt(Region.Point::x)
				.mapToObj(i -> Range.closed(i - hexWidth/4, i + hexWidth/4))
				.forEachOrdered(colRanges::add);
		List<Range<Integer>> cols = colRanges.asRanges().stream().collect(Collectors.toList());
//		System.out.println(rows);
//		System.out.println(cols);

		//Missing rows and columns still count, so insert placeholders.
		for (int i = 0; i < rows.size()-1; ++i) {
			int mid1 = (rows.get(i).lowerEndpoint() + rows.get(i).upperEndpoint())/2;
			int mid2 = (rows.get(i+1).lowerEndpoint() + rows.get(i+1).upperEndpoint())/2;
			//hexHeight/2 as we're using flat-topped hexes
			int placeholders = (mid2 - mid1)/(hexHeight/2) - 1;
			rows.addAll(i+1, Collections.nCopies(placeholders, null));
			i += placeholders;
		}
		for (int i = 0; i < cols.size()-1; ++i) {
			int mid1 = (cols.get(i).lowerEndpoint() + cols.get(i).upperEndpoint())/2;
			int mid2 = (cols.get(i+1).lowerEndpoint() + cols.get(i+1).upperEndpoint())/2;
			int placeholders = (mid2 - mid1)/hexWidth;
			cols.addAll(i+1, Collections.nCopies(placeholders, null));
			i += placeholders;
		}
//		System.out.println(rows);
//		System.out.println(cols);

		//Is a top-most (row 0) hex in an even or odd column?
		int topMostCol = hexagons.stream()
				.filter(h -> rows.indexOf(rowRanges.rangeContaining(h.centroid().y())) == 0)
				.limit(1)
				.mapToInt(h -> cols.indexOf(colRanges.rangeContaining(h.centroid().x())))
				.iterator().nextInt();
		boolean evenQ = (topMostCol & 1) != 0; //yes, this seems backwards.
		List<Cell> cells = new ArrayList<>(hexagons.size());
		Map<Coordinate, Recognizer.Result> constraints = new HashMap<>();
		for (Region hex : hexagons) {
			int q = cols.indexOf(colRanges.rangeContaining(hex.centroid().x()));
			int r = rows.indexOf(rowRanges.rangeContaining(hex.centroid().y()));
			//We count every row, but our link counts only rows present in the column,
			//so divide r by 2.
			//http://www.redblobgames.com/grids/hexagons/#conversions
			int x = q, z = r/2 - (q + (evenQ ? q & 1 : -(q&1)))/2, y = -x - z;
			Coordinate coordinate = Coordinate.at(x, y, z);
//			System.out.println(coordinate);
			Cell cell = new Cell(coordinate, Colors.HEXAGON_BORDER_COLORS.get(hex.color()), hex.centroid());
			cells.add(cell);

			Rectangle exteriorBox = hex.boundingBox();
			if (cell.kind() != Cell.Kind.UNKNOWN) {
				BufferedImage subimage = image.getSubimage(exteriorBox.x, exteriorBox.y, exteriorBox.width, exteriorBox.height);
				recognizer.recognizeCell(subimage, cell.kind())
						.ifPresent(i -> constraints.put(coordinate, i));
			}
			//help out board-edge constraint parsing
			for (int i = exteriorBox.x; i < exteriorBox.x + exteriorBox.width; ++i)
				for (int j = exteriorBox.y; j < exteriorBox.y + exteriorBox.height; ++j)
					image.setRGB(i, j, Color.WHITE.getRGB());
		}
		Map<Coordinate, Cell> grid = cells.stream().collect(Collectors.toMap(Cell::where, Function.identity()));

		for (Cell c : cells) {
			if (!grid.containsKey(c.where().up()))
				recognizer.recognizeBoardEdge(subimageCenteredAt(image, c.pixelCentroid().x, c.pixelCentroid().y - hexHeight, hexWidth, hexHeight))
						.ifPresent(i -> constraints.put(c.where().up(), i));
			if (!grid.containsKey(c.where().upRight()))
				recognizer.recognizeBoardEdge(subimageCenteredAt(image, c.pixelCentroid().x + hexWidth, c.pixelCentroid().y - hexHeight/2, hexWidth, hexHeight))
						.ifPresent(i -> constraints.put(c.where().upRight(), i));
			if (!grid.containsKey(c.where().upLeft()))
				recognizer.recognizeBoardEdge(subimageCenteredAt(image, c.pixelCentroid().x - hexWidth, c.pixelCentroid().y - hexHeight/2, hexWidth, hexHeight))
						.ifPresent(i -> constraints.put(c.where().upLeft(), i));
		}
		System.out.println(constraints);

		return null;
	}

	private static BufferedImage subimageCenteredAt(BufferedImage image, int x, int y, int width, int height) {
		return image.getSubimage(x - width/2, y - height/2, width, height);
	}

	public static void main(String[] args) throws Throwable {
		BufferedImage image = ImageIO.read(new File("Hexcells Plus 2014-05-10 22-49-38-58.bmp"));
		fromImage(image);
//		BufferedImage image = ImageIO.read(new File("Hexcells Plus 2014-05-11 00-51-47-56.bmp"));
//		try (DirectoryStream<Path> files = Files.newDirectoryStream(Paths.get("."), "*.bmp")) {
//			for (Path p : files) {
//				System.out.println(p);
//				fromImage(ImageIO.read(p.toFile()));
//			}
//		}
	}
}
