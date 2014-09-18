package com.jeffreybosboom.hexcells;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.google.common.io.CharStreams;
import com.jeffreybosboom.hexcells.Recognizer.Result.ConstraintKind;
import com.jeffreybosboom.hexcells.Recognizer.Result.ConstraintPosition;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 9/7/2014
 */
public final class Effector {
	private final Robot robot;
	private final Recognizer recognizer = new Recognizer();
	private final Rectangle hexcellsRect;
	public Effector() throws AWTException, InterruptedException, IOException {
		this.robot = new Robot();
		robot.setAutoDelay(100);
		this.hexcellsRect = locateHexcells();
	}

	private static Rectangle locateHexcells() throws InterruptedException, IOException {
		ProcessBuilder pb = new ProcessBuilder("cmdow.exe Hexcells /B /P".split(" "));
		Process p = pb.start();
		p.waitFor();
		Reader r = new InputStreamReader(p.getInputStream());
		List<String> readLines = CharStreams.readLines(r);
		if (readLines.size() != 1)
			throw new RuntimeException(readLines.toString());
		String[] fields = readLines.get(0).trim().split("\\h+");
		System.out.println(Arrays.toString(fields));
		//These include window decorations, whose size varies by computer, but
		//they shouldn't affect our parsing.
		int windowLeft = Integer.parseInt(fields[fields.length-6]);
		int windowTop = Integer.parseInt(fields[fields.length-5]);
		int windowWidth = Integer.parseInt(fields[fields.length-4]);
		int windowHeight = Integer.parseInt(fields[fields.length-3]);
		return new Rectangle(windowLeft, windowTop, windowWidth, windowHeight);
	}

	//<editor-fold defaultstate="collapsed" desc="Image parsing">
	private Pair<Puzzle, Map<Coordinate, Region.Point>> fromImage(BufferedImage image) {
		ImmutableSet<Region> regions = Region.connectedComponents(image,
				Colors.HEXAGON_BORDER_COLORS.keySet().stream().map(Color::getRGB).collect(Collectors.toSet()));
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
		Map<Coordinate, CellState> cells = new LinkedHashMap<>();
		Map<Coordinate, Region.Point> hexCenters = new LinkedHashMap<>();
		Map<Coordinate, Recognizer.Result> constraintImages = new LinkedHashMap<>();
		for (Region hex : hexagons) {
			int q = cols.indexOf(colRanges.rangeContaining(hex.centroid().x()));
			int r = rows.indexOf(rowRanges.rangeContaining(hex.centroid().y()));
			//We count every row, but our link counts only rows present in the column,
			//so divide r by 2.
			//http://www.redblobgames.com/grids/hexagons/#conversions
			int x = q, z = r/2 - (q + (evenQ ? q & 1 : -(q&1)))/2, y = -x - z;
			Coordinate coordinate = Coordinate.at(x, y, z);
//			System.out.println(coordinate);
			CellState state = Colors.HEXAGON_BORDER_COLORS.get(hex.color());
			cells.put(coordinate, state);
			hexCenters.put(coordinate, hex.centroid());

			Rectangle exteriorBox = hex.boundingBox();
			if (state != CellState.UNKNOWN) {
				BufferedImage subimage = image.getSubimage(exteriorBox.x, exteriorBox.y, exteriorBox.width, exteriorBox.height);
				recognizer.recognizeCell(subimage, state)
						.ifPresent(i -> constraintImages.put(coordinate, i));
			}
			//help out board-edge constraint parsing
			for (int i = exteriorBox.x; i < exteriorBox.x + exteriorBox.width; ++i)
				for (int j = exteriorBox.y; j < exteriorBox.y + exteriorBox.height; ++j)
					image.setRGB(i, j, Color.WHITE.getRGB());
		}

		for (Coordinate c : cells.keySet()) {
			if (!cells.containsKey(c.up()))
				recognizer.recognizeBoardEdge(subimageCenteredAt(image, hexCenters.get(c).x, hexCenters.get(c).y - hexHeight, hexWidth, hexHeight))
						.map(i -> i.pos == ConstraintPosition.TOP ? i : null)
						.ifPresent(i -> constraintImages.put(c.up(), i));
			if (!cells.containsKey(c.upRight()))
				recognizer.recognizeBoardEdge(subimageCenteredAt(image, hexCenters.get(c).x + hexWidth, hexCenters.get(c).y - hexHeight/2, hexWidth, hexHeight))
						.map(i -> i.pos == ConstraintPosition.LEFT ? i : null)
						.ifPresent(i -> constraintImages.put(c.upRight(), i));
			if (!cells.containsKey(c.upLeft()))
				recognizer.recognizeBoardEdge(subimageCenteredAt(image, hexCenters.get(c).x - hexWidth, hexCenters.get(c).y - hexHeight/2, hexWidth, hexHeight))
						.map(i -> i.pos == ConstraintPosition.RIGHT ? i : null)
						.ifPresent(i -> constraintImages.put(c.upLeft(), i));
		}

		ImmutableSet.Builder<Constraint> constraints = ImmutableSet.builder();
		for (Map.Entry<Coordinate, Recognizer.Result> e : constraintImages.entrySet())
			constraints.add(makeConstraint(e.getKey(), e.getValue(), cells));

		return new Pair<>(new Puzzle(cells, constraints.build()), hexCenters);
	}

	private static Constraint makeConstraint(Coordinate c, Recognizer.Result result, Map<Coordinate, CellState> grid) {
		int target = result.number;
		boolean contiguous = result.kind == ConstraintKind.CONNECTED,
				discontiguous = result.kind == ConstraintKind.DISCONNECTED;
		if (grid.containsKey(c)) {
			List<Coordinate> region = c.neighbors()
					.filter(grid::containsKey)
					.collect(Collectors.toList());
			return new CellConstraint(c, region, target, contiguous, discontiguous);
		}
		else {
			ToIntFunction<Coordinate> axisExtractor;
			if (result.pos == ConstraintPosition.TOP)
				axisExtractor = Coordinate::x;
			else if (result.pos == ConstraintPosition.RIGHT)
				axisExtractor = Coordinate::y;
			else
				axisExtractor = Coordinate::z;
			List<Coordinate> region = grid.keySet().stream()
					.filter(x -> axisExtractor.applyAsInt(x) == axisExtractor.applyAsInt(c))
					.sorted(Comparator.comparingInt(axisExtractor))
					.filter(grid::containsKey)
					.collect(Collectors.toList());
			return new AxisConstraint(axisExtractor, region, target, contiguous, discontiguous);
		}
	}

	private static BufferedImage subimageCenteredAt(BufferedImage image, int x, int y, int width, int height) {
		return image.getSubimage(x - width/2, y - height/2, width, height);
	}
	//</editor-fold>

	public boolean playPuzzle() {
		while (true) {
			Pair<Puzzle, Map<Coordinate, Region.Point>> p = fromImage(capture());
			Map<Coordinate, Region.Point> hexCenters = p.second;
			Puzzle p1 = p.first;
			p1.constraints().forEachOrdered(System.out::println);
			Puzzle p2 = Deducer.deduce(p1);
			List<Coordinate> deductions = p2.cells()
					.filter(c -> p2.isKnown(c) && p1.isUnknown(c))
					.collect(Collectors.toList());
			for (Coordinate c : deductions)
				if (p2.isPresent(c))
					leftClick(hexCenters.get(c));
				else if (p2.isAbsent(c))
					rightClick(hexCenters.get(c));

			if (deductions.isEmpty())
				return false;
			if (p2.isSolved())
				return true;
		}
	}

	private BufferedImage capture() {
		BufferedImage capture = robot.createScreenCapture(hexcellsRect);
		Graphics2D g = capture.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 32, 32); //cover up hexcells icon lest we think it's a hex
		g.dispose();
		return capture;
	}

	private void leftClick(Region.Point p) {
		int x = p.x + hexcellsRect.x, y = p.y + hexcellsRect.y;
		if (!hexcellsRect.contains(x, y)) throw new RuntimeException();
		robot.mouseMove(x, y);
		robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
		robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
	}

	private void rightClick(Region.Point p) {
		int x = p.x + hexcellsRect.x, y = p.y + hexcellsRect.y;
		if (!hexcellsRect.contains(x, y)) throw new RuntimeException();
		robot.mouseMove(x, y);
		robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
		robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
	}

	public static void main(String[] args) throws Throwable {
		Effector e = new Effector();
		System.out.println(e.playPuzzle());
	}
}
