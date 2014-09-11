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
	public Effector() {}

	private static final ImmutableBiMap<Color, Cell.Kind> HEXAGON_BORDER_COLORS = ImmutableBiMap.of(
			//These are the colors of the hexagon colored borders, not their
			//centers, as the present hexagons centers are the same color as the
			//remaining/mistake boxes.
			new Color(255, 159, 0), Cell.Kind.UNKNOWN,
			new Color(44, 47, 49), Cell.Kind.ABSENT,
			new Color(20, 156, 216), Cell.Kind.PRESENT
	);
	private static final ImmutableBiMap<Color, Cell.Kind> HEXAGON_INTERIOR_COLORS = ImmutableBiMap.of(
			new Color(255, 175, 41), Cell.Kind.UNKNOWN,
			new Color(62, 62, 62), Cell.Kind.ABSENT,
			new Color(5, 164, 235), Cell.Kind.PRESENT
	);
	private static final Color REMAINING_BOX = new Color(5, 164, 235); //also the mistake box
	public static Puzzle fromImage(BufferedImage image) {
		ImmutableSet<Region> regions = Region.connectedComponents(image, ContiguousSet.create(Range.all(), DiscreteDomain.integers()));
		List<Region> hexagons = regions.stream()
				.filter(r -> HEXAGON_BORDER_COLORS.containsKey(r.color()))
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
		Map<Coordinate, BufferedImage> constraintImages = new HashMap<>();
		for (Region hex : hexagons) {
			int q = cols.indexOf(colRanges.rangeContaining(hex.centroid().x()));
			int r = rows.indexOf(rowRanges.rangeContaining(hex.centroid().y()));
			//We count every row, but our link counts only rows present in the column,
			//so divide r by 2.
			//http://www.redblobgames.com/grids/hexagons/#conversions
			int x = q, z = r/2 - (q + (evenQ ? q & 1 : -(q&1)))/2, y = -x - z;
			Coordinate coordinate = Coordinate.at(x, y, z);
//			System.out.println(coordinate);
			Cell cell = new Cell(coordinate, HEXAGON_BORDER_COLORS.get(hex.color()), hex.centroid());
			cells.add(cell);

			Rectangle exteriorBox = hex.boundingBox();
			if (cell.kind() != Cell.Kind.UNKNOWN) {
				BufferedImage subimage = image.getSubimage(exteriorBox.x, exteriorBox.y, exteriorBox.width, exteriorBox.height);
				cleanCellConstraintImage(cell, subimage).ifPresent(i -> constraintImages.put(coordinate, i));
			}
			//help out board-edge constraint parsing
			for (int i = exteriorBox.x; i < exteriorBox.x + exteriorBox.width; ++i)
				for (int j = exteriorBox.y; j < exteriorBox.y + exteriorBox.height; ++j)
					image.setRGB(i, j, Color.WHITE.getRGB());
		}
		Map<Coordinate, Cell> grid = cells.stream().collect(Collectors.toMap(Cell::where, Function.identity()));

		for (Cell c : cells) {
			if (!grid.containsKey(c.where().up()))
				cleanBoardEdgeConstraintImage(subimageCenteredAt(image, c.pixelCentroid().x, c.pixelCentroid().y - hexHeight, hexWidth, hexHeight))
						.ifPresent(i -> constraintImages.put(c.where().up(), i));
			if (!grid.containsKey(c.where().upRight()))
				cleanBoardEdgeConstraintImage(subimageCenteredAt(image, c.pixelCentroid().x + hexWidth, c.pixelCentroid().y - hexHeight/2, hexWidth, hexHeight))
						.ifPresent(i -> constraintImages.put(c.where().upRight(), i));
			if (!grid.containsKey(c.where().upLeft()))
				cleanBoardEdgeConstraintImage(subimageCenteredAt(image, c.pixelCentroid().x - hexWidth, c.pixelCentroid().y - hexHeight/2, hexWidth, hexHeight))
						.ifPresent(i -> constraintImages.put(c.where().upLeft(), i));
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		for (Map.Entry<Coordinate, BufferedImage> c : constraintImages.entrySet())
			try {
				HashingOutputStream hashStream = new HashingOutputStream(Hashing.sha1(), baos);
				ImageIO.write(c.getValue(), "PNG", hashStream);
				String hash = hashStream.hash().toString();
				try (OutputStream fos = Files.newOutputStream(Paths.get("constraints/"+hash+".png"))) {
					baos.writeTo(fos);
				}
				baos.reset();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}

		return null;
	}

	private static BufferedImage subimageCenteredAt(BufferedImage image, int x, int y, int width, int height) {
		return image.getSubimage(x - width/2, y - height/2, width, height);
	}

	private static Optional<BufferedImage> cleanBoardEdgeConstraintImage(BufferedImage subimage) {
		int nonWhiteMinX = Integer.MAX_VALUE, nonWhiteMinY = Integer.MAX_VALUE;
		for (int x = 0; x < subimage.getWidth(); ++x)
			for (int y = 0; y < subimage.getHeight(); ++y) {
				Color rgb = new Color(subimage.getRGB(x, y));
				if (rgb.getRed() != rgb.getGreen() ||
						rgb.getRed() != rgb.getBlue() ||
						rgb.getGreen() != rgb.getBlue() ||
						rgb.getRed() >= 220)
					subimage.setRGB(x, y, Color.WHITE.getRGB());
				else {
					nonWhiteMinX = Math.min(nonWhiteMinX, x);
					nonWhiteMinY = Math.min(nonWhiteMinY, y);
				}
			}
		if (nonWhiteMinX == Integer.MAX_VALUE) return Optional.empty();

		int nonWhiteMaxX = nonWhiteMinX, nonWhiteMaxY = nonWhiteMinY;
		outer: while (nonWhiteMaxX < subimage.getWidth()) {
			for (int y = 0; y < subimage.getHeight(); ++y)
				if (subimage.getRGB(nonWhiteMaxX, y) != Color.WHITE.getRGB()) {
					++nonWhiteMaxX;
					continue outer;
				}
			break;
		}
		outer: while (nonWhiteMaxY < subimage.getHeight()) {
			for (int x = 0; x < subimage.getWidth(); ++x)
				if (subimage.getRGB(x, nonWhiteMaxY) != Color.WHITE.getRGB()) {
					++nonWhiteMaxY;
					continue outer;
				}
			break;
		}
		subimage = subimage.getSubimage(nonWhiteMinX, nonWhiteMinY,
				nonWhiteMaxX - nonWhiteMinX, nonWhiteMaxY - nonWhiteMinY);

		for (int x = 0; x < subimage.getWidth(); ++x)
			for (int y = 0; y < subimage.getHeight(); ++y)
				if (new Color(subimage.getRGB(x, y)).getRed() <= 70)
					return Optional.of(subimage);
		return Optional.empty();
	}

	private static Optional<BufferedImage> cleanCellConstraintImage(Cell cell, BufferedImage subimage) {
		Color interiorColor = HEXAGON_INTERIOR_COLORS.inverse().get(cell.kind());
		Region cellRegion = Region.connectedComponents(subimage, ImmutableSet.of(interiorColor.getRGB()))
				.stream().sorted(Comparator.comparingInt((Region r_) -> r_.points().size()).reversed()).findFirst().get();
		Rectangle interiorBox = cellRegion.boundingBox();
		int constraintMinX = Integer.MAX_VALUE, constraintMaxX = Integer.MIN_VALUE,
				constraintMinY = Integer.MAX_VALUE, constraintMaxY = Integer.MIN_VALUE;
		for (int ex = interiorBox.x; ex < interiorBox.x + interiorBox.width; ++ex) {
			final int ex_ = ex;
			IntSummaryStatistics rowExtrema = cellRegion.points().stream()
					.filter(p -> p.x == ex_)
					.collect(Collectors.summarizingInt(Region.Point::y));
			for (int ey = rowExtrema.getMin(); ey < rowExtrema.getMax(); ++ey)
				if (!cellRegion.points().contains(new Region.Point(ex, ey))) {
					constraintMinX = Math.min(constraintMinX, ex);
					constraintMaxX = Math.max(constraintMaxX, ex);
					constraintMinY = Math.min(constraintMinY, ey);
					constraintMaxY = Math.max(constraintMaxY, ey);
				}
		}
		if (constraintMinX == Integer.MAX_VALUE) return Optional.empty();
		subimage = subimage.getSubimage(constraintMinX, constraintMinY,
				constraintMaxX - constraintMinX + 1, constraintMaxY - constraintMinY + 1);
		subimage = maskOrInvert(subimage, interiorColor);
		return Optional.of(copyImage(subimage));
	}

	private static BufferedImage maskOrInvert(BufferedImage image, Color maskToWhite) {
		for (int x = 0; x < image.getWidth(); ++x)
			for (int y = 0; y < image.getHeight(); ++y) {
				int rgb = image.getRGB(x, y);
				if (rgb == maskToWhite.getRGB())
					image.setRGB(x, y, Color.WHITE.getRGB());
				else
					image.setRGB(x, y, ~rgb | (255 << 24));
			}
		return image;
	}

	//https://stackoverflow.com/a/19327237/3614835
	private static BufferedImage copyImage(BufferedImage source) {
		BufferedImage b = new BufferedImage(source.getWidth(), source.getHeight(), source.getType());
		Graphics g = b.getGraphics();
		g.drawImage(source, 0, 0, null);
		g.dispose();
		return b;
	}

	public static void main(String[] args) throws Throwable {
//		BufferedImage image = ImageIO.read(new File("Hexcells Plus 2014-05-10 22-49-38-58.bmp"));
//		BufferedImage image = ImageIO.read(new File("Hexcells Plus 2014-05-11 00-51-47-56.bmp"));
		try (DirectoryStream<Path> files = Files.newDirectoryStream(Paths.get("."), "*.bmp")) {
			for (Path p : files) {
				System.out.println(p);
				fromImage(ImageIO.read(p.toFile()));
			}
		}
	}
}
