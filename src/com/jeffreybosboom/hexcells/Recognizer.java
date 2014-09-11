package com.jeffreybosboom.hexcells;

import com.google.common.collect.ImmutableMap;
import com.jeffreybosboom.hexcells.Recognizer.Result.ConstraintKind;
import com.jeffreybosboom.hexcells.Recognizer.Result.ConstraintPosition;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

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
					for (int fontSize = 16; fontSize < 26; ++fontSize) {
						builder.put(render(s, fontSize, p, true), r);
						builder.put(render(s, fontSize, p, false), r);
					}
				}
		Result qr = new Result(Result.QUESTION_MARK, ConstraintKind.NORMAL, ConstraintPosition.TOP);
		for (int fontSize = 16; fontSize < 26; ++fontSize) {
			builder.put(render("?", fontSize, qr.pos, true), qr);
			builder.put(render("?", fontSize, qr.pos, false), qr);
		}
		this.references = builder.build();
	}

	public Result recognizeCell(BufferedImage image) {
		throw new UnsupportedOperationException();
	}

	public Result recognizeBoardEdge(BufferedImage image) {
		throw new UnsupportedOperationException();
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
		System.out.println(r.references.size());
	}
}
