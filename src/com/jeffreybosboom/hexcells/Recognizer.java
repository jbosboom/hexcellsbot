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

	private static final class ImageData {
		//grayscale images, so this is one (any one) of the RGB values.
		private final byte[] data;
		private final byte width, height;
		ImageData(byte[] data, byte width, byte height) {
			this.data = data;
			this.width = width;
			this.height = height;
		}
		ImageData(BufferedImage image) {
			this(image, 0, 0, image.getWidth(), image.getHeight());
		}
		ImageData(BufferedImage image, int x, int y, int width, int height) {
			this(new byte[width*height], (byte)width, (byte)height);
			for (int yp = 0; yp < height; ++yp)
				for (int xp = 0; xp < width; ++xp) {
					//TODO: assert grayscale image
					data[yp * width + xp] = (byte)(image.getRGB(x + xp, y + yp) & 0xFF);
				}
		}
		ImageData(ImageData image, int x, int y, int width, int height) {
			this(new byte[width*height], (byte)width, (byte)height);
			for (int yp = 0; yp < height; ++yp)
				for (int xp = 0; xp < width; ++xp)
					data[yp * width + xp] = image.at(x + xp, y + yp);
		}
		public byte width() {
			return width;
		}
		public byte height() {
			return height;
		}
		public byte at(int x, int y) {
			return data[y * width() + x];
		}
	}

	private static final int WHITE_RGB = Color.WHITE.getRGB();
	private static final byte WHITE = (byte)(WHITE_RGB & 0xFF);
	private final ImmutableMap<ImageData, Result> references;
	public Recognizer() {
		ImmutableMap.Builder<ImageData, Result> builder = ImmutableMap.builder();
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

	private static Optional<ImageData> cleanCellConstraintImage(Cell.Kind cellKind, BufferedImage subimage) {
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
		return Optional.of(new ImageData(maskOrInvert(copyImage(subimage), interiorColor)));
	}

	private static BufferedImage maskOrInvert(BufferedImage image, Color maskToWhite) {
		for (int x = 0; x < image.getWidth(); ++x)
			for (int y = 0; y < image.getHeight(); ++y) {
				int rgb = image.getRGB(x, y);
				if (rgb == maskToWhite.getRGB())
					image.setRGB(x, y, WHITE_RGB);
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

	private static Optional<ImageData> cleanBoardEdgeConstraintImage(BufferedImage buffer) {
		int nonWhiteMinX = Integer.MAX_VALUE, nonWhiteMinY = Integer.MAX_VALUE;
		for (int x = 0; x < buffer.getWidth(); ++x)
			for (int y = 0; y < buffer.getHeight(); ++y) {
				//still need getRGB here to check it's grayscale
				Color rgb = new Color(buffer.getRGB(x, y));
				if (rgb.getRed() != rgb.getGreen() ||
						rgb.getRed() != rgb.getBlue() ||
						rgb.getGreen() != rgb.getBlue() ||
						rgb.getRed() >= 220)
					buffer.setRGB(x, y, WHITE_RGB);
				else {
					nonWhiteMinX = Math.min(nonWhiteMinX, x);
					nonWhiteMinY = Math.min(nonWhiteMinY, y);
				}
			}
		if (nonWhiteMinX == Integer.MAX_VALUE) return Optional.empty();

		ImageData data = new ImageData(buffer);
		int nonWhiteMaxX = nonWhiteMinX, nonWhiteMaxY = nonWhiteMinY;
		outer: while (nonWhiteMaxX < data.width()) {
			for (int y = 0; y < data.height(); ++y)
				if (data.at(nonWhiteMaxX, y) != WHITE) {
					++nonWhiteMaxX;
					continue outer;
				}
			break;
		}
		outer: while (nonWhiteMaxY < data.height()) {
			for (int x = 0; x < data.width(); ++x)
				if (data.at(x, nonWhiteMaxY) != WHITE) {
					++nonWhiteMaxY;
					continue outer;
				}
			break;
		}
		data = new ImageData(data, nonWhiteMinX, nonWhiteMinY,
				nonWhiteMaxX - nonWhiteMinX, nonWhiteMaxY - nonWhiteMinY);

		for (int x = 0; x < data.width(); ++x)
			for (int y = 0; y < data.height(); ++y)
				if (Byte.toUnsignedInt(data.at(x, y)) <= 70)
					return Optional.of(data);
		return Optional.empty();
	}

	private static Result compare(ImageData needle, Iterator<Map.Entry<ImageData, Result>> haystack) {
		int bestScore = Integer.MIN_VALUE;
		Result result = null;
		while (haystack.hasNext()) {
			Map.Entry<ImageData, Result> e = haystack.next();
			int score = bestFitCompare(needle, e.getKey());
			if (score > bestScore) {
				bestScore = score;
				result = e.getValue();
			}
		}
		return result;
	}

	private static int bestFitCompare(ImageData needle, ImageData haystack) {
		if (haystack.width() < needle.width() && haystack.height() < needle.height())
			return bestFitCompare(haystack, needle);
		else if (!(needle.width() <= haystack.width() && needle.height() <= haystack.height()))
			return Integer.MIN_VALUE; //images do not fit inside each other

		int score = 0;
		if (needle.width() == haystack.width() && needle.height() == haystack.height()) {
			score = bestFitCompare(needle, haystack, 0, 0);
		} else {
			//Try each a-sized subimage of b, penalizing for the size difference.
			int difference = haystack.width() * haystack.height() - needle.width() * needle.height();
			for (int x = 0; x < haystack.width() - needle.width(); ++x)
				for (int y = 0; y < haystack.height() - needle.height(); ++y) {
					int subscore = bestFitCompare(needle, haystack, x, y);
					score = Math.max(score, subscore - difference);
				}
		}
		return score;
	}

	private static int bestFitCompare(ImageData a, ImageData b, int bx, int by) {
		int score = 0;
		for (int x = 0; x < a.width(); ++x)
			for (int y = 0; y < a.height(); ++y)
				score += (a.at(x, y) != WHITE) == (b.at(bx + x, by + y) != WHITE)
						? 1 : -2;
		return score;
	}

	private static final Font HARABARA = new Font("Harabara", Font.PLAIN, 16);
	private static ImageData render(String string, int fontSize, ConstraintPosition orientation, boolean antialiased) {
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

	private static ImageData crop(BufferedImage image) {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE,
				minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		for (int x = 0; x < image.getWidth(); ++x)
			for (int y = 0; y < image.getHeight(); ++y)
				if (image.getRGB(x, y) != WHITE_RGB) {
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
		return new ImageData(image, minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	public static void main(String[] args) throws Throwable {
		Recognizer r = new Recognizer();
		System.out.println(compare(new ImageData(ImageIO.read(
				new File("constraints/f65281a4a12175cd659ea4e26bbf9361abe0611c.png"))),
				r.references.entrySet().stream()
				.filter(e -> e.getValue().pos == ConstraintPosition.TOP)
				.filter(e -> e.getValue().number <= 6).iterator()));
	}
}
