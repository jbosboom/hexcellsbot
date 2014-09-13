package com.jeffreybosboom.hexcells;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.jeffreybosboom.hexcells.Recognizer.Result.ConstraintKind;
import com.jeffreybosboom.hexcells.Recognizer.Result.ConstraintPosition;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 9/10/2014
 */
public final class Recognizer {
	public static final class Result {
		public static enum ConstraintKind {
			NORMAL, CONNECTED, DISCONNECTED
		}
		public static enum ConstraintPosition {
			TOP, LEFT, RIGHT
		}
		public static final int QUESTION_MARK = -1;
		public final int number;
		public final ConstraintKind kind;
		public final ConstraintPosition pos;
		public Result(int number, ConstraintKind kind, ConstraintPosition pos) {
			this.number = number;
			this.kind = kind;
			this.pos = pos;
		}
		@Override
		public String toString() {
			return String.format("%d %s %s", number, kind, pos);
		}
	}

	private final ImmutableMap<BufferedImage, Result> references;
	public Recognizer() {
		ImmutableMap.Builder<BufferedImage, Result> builder = ImmutableMap.builder();
		for (ConstraintPosition p : ConstraintPosition.values())
			for (ConstraintKind k : ConstraintKind.values())
				for (int n = (k == ConstraintKind.NORMAL ? 0 : 2); n < 10; ++n) {
					String s = Integer.toString(n);
					if (k == ConstraintKind.CONNECTED)
						s = '{' + s + '}';
					else if (k == ConstraintKind.DISCONNECTED)
						s = '-' + s + '-';
					Result r = new Result(n, k, p);
					for (int fontSize = 16; fontSize < 27; ++fontSize) {
						builder.put(render(s, fontSize, p, true), r);
//						builder.put(render(s, fontSize, p, false), r);
					}
				}
		Result qr = new Result(Result.QUESTION_MARK, ConstraintKind.NORMAL, ConstraintPosition.TOP);
		for (int fontSize = 16; fontSize < 26; ++fontSize) {
			builder.put(render("?", fontSize, qr.pos, true), qr);
			builder.put(render("?", fontSize, qr.pos, false), qr);
		}
		this.references = builder.build();
	}

	public Optional<Result> recognizeCell(BufferedImage image, Cell.Kind cellKind) {
		return cleanCellConstraintImage(cellKind, image).map(cleaned ->
				compare(cleaned, references.entrySet().stream()
						.filter(e -> e.getValue().pos == ConstraintPosition.TOP)
						.filter(e -> e.getValue().number <= 6).iterator()));
	}

	private static Optional<BufferedImage> cleanCellConstraintImage(Cell.Kind cellKind, BufferedImage subimage) {
		Color interiorColor = Colors.HEXAGON_INTERIOR_COLORS.inverse().get(cellKind);
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
		return Optional.of(maskOrInvert(copyImage(subimage), interiorColor));
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

	public Optional<Result> recognizeBoardEdge(BufferedImage image) {
		return cleanBoardEdgeConstraintImage(image).map(cleaned ->
				compare(cleaned, references.entrySet().stream()
						.filter(e -> e.getValue().number != Result.QUESTION_MARK).iterator()));
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

	private static Result compare(BufferedImage needle, Iterator<Map.Entry<BufferedImage, Result>> haystack) {
		int bestScore = Integer.MIN_VALUE;
		Result result = null;
		while (haystack.hasNext()) {
			Map.Entry<BufferedImage, Result> e = haystack.next();
			int score = bestFitCompare(needle, e.getKey());
			if (score > bestScore) {
				bestScore = score;
				result = e.getValue();
			}
		}
		return result;
	}

	private static int bestFitCompare(BufferedImage a, BufferedImage b) {
		if (b.getWidth() < a.getWidth() && b.getHeight() < a.getHeight())
			return bestFitCompare(b, a);
		else if (!(a.getWidth() <= b.getWidth() && a.getHeight() <= b.getHeight()))
			return Integer.MIN_VALUE; //images do not fit inside each other

		int score = 0;
		if (a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight()) {
			for (int x = 0; x < a.getWidth(); ++x)
				for (int y = 0; y < a.getHeight(); ++y)
					score += (a.getRGB(x, y) != Color.WHITE.getRGB()) == (b.getRGB(x, y) != Color.WHITE.getRGB())
							? 1 : -2;
		} else {
			//Try each a-sized subimage of b, penalizing for the size difference.
			int difference = b.getWidth() * b.getHeight() - a.getWidth() * a.getHeight();
			for (int x = 0; x < b.getWidth() - a.getWidth(); ++x)
				for (int y = 0; y < b.getHeight() - a.getHeight(); ++y) {
					int subscore = bestFitCompare(a, b.getSubimage(x, y, a.getWidth(), a.getHeight()));
					score = Math.max(score, subscore - difference);
				}
		}
		return score;
	}

	private static final Font HARABARA = new Font("Harabara", Font.PLAIN, 16);
	private static BufferedImage render(String string, int fontSize, ConstraintPosition orientation, boolean antialiased) {
		//It's too hard to compute how big an image to make, so make a super-big
		//one and crop it later.
		BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				antialiased ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, 256, 256);
		graphics.setColor(Color.BLACK);
		graphics.setFont(HARABARA.deriveFont((float)fontSize));
		graphics.translate(32, 128);
		if (orientation == ConstraintPosition.LEFT)
			graphics.rotate(-Math.PI/3);
		else if (orientation == ConstraintPosition.RIGHT)
			graphics.rotate(Math.PI/3);
		graphics.drawString(string, 0, 0);
		graphics.dispose();
		return crop(image);
	}

	private static BufferedImage crop(BufferedImage image) {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE,
				minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		for (int x = 0; x < image.getWidth(); ++x)
			for (int y = 0; y < image.getHeight(); ++y)
				if (image.getRGB(x, y) != Color.WHITE.getRGB()) {
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
		return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	public static void main(String[] args) throws Throwable {
		Recognizer r = new Recognizer();
		System.out.println(compare(ImageIO.read(
				new File("constraints/f65281a4a12175cd659ea4e26bbf9361abe0611c.png")),
				r.references.entrySet().stream()
				.filter(e -> e.getValue().pos == ConstraintPosition.TOP)
				.filter(e -> e.getValue().number <= 6).iterator()));
	}
}
