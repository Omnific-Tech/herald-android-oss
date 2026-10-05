import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import javax.imageio.ImageIO;

/**
 * Generates a product's launcher and notification icons from one transparent
 * logo PNG. Needs only a JDK:
 *
 *   java tools/GenerateIcons.java <logo.png> products/<slug>
 *
 * The launcher foreground is padded into the adaptive icon's safe zone. The
 * notification icon is a white silhouette, because the status bar renders
 * small icons from alpha alone; it replaces core's bell for this product only.
 */
public class GenerateIcons {
    static final String[] DENSITIES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    static final double[] SCALES = {1, 1.5, 2, 3, 4};

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: java tools/GenerateIcons.java <logo.png> products/<slug>");
            System.exit(2);
        }
        BufferedImage source = ImageIO.read(new File(args[0]));
        File product = new File(args[1]);
        File res = new File(product, "src/main/res");
        if (source == null || !res.isDirectory()) {
            System.err.println("need a readable PNG and a product with src/main/res");
            System.exit(1);
        }
        String notification = "ic_" + product.getName() + "_notification";

        Rectangle box = alphaBounds(source);
        BufferedImage logo = source.getSubimage(box.x, box.y, box.width, box.height);

        for (int i = 0; i < DENSITIES.length; i++) {
            // 108dp canvas; 60dp of logo stays inside the 66dp safe circle.
            int launcher = (int) Math.round(108 * SCALES[i]);
            write(place(logo, launcher, 60.0 / 108, false),
                new File(res, "drawable-" + DENSITIES[i] + "/ic_launcher_foreground.png"));

            // 24dp canvas with a 22dp silhouette.
            int small = (int) Math.round(24 * SCALES[i]);
            write(place(logo, small, 22.0 / 24, true),
                new File(res, "drawable-" + DENSITIES[i] + "/" + notification + ".png"));
        }

        // The density PNGs replace the template's placeholder vector.
        Files.deleteIfExists(new File(res, "drawable/ic_launcher_foreground.xml").toPath());

        File override = new File(res, "drawable/ic_herald_notification.xml");
        override.getParentFile().mkdirs();
        Files.writeString(override.toPath(), """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- Overrides core's bell with the product mark. Status bar icons use alpha only. -->
            <bitmap xmlns:android="http://schemas.android.com/apk/res/android"
                android:src="@drawable/%s" />
            """.formatted(notification));

        System.out.println("wrote launcher and " + notification + " icons under " + res);
    }

    static Rectangle alphaBounds(BufferedImage image) {
        int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) > 8) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) throw new IllegalArgumentException("logo is fully transparent");
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    static BufferedImage place(BufferedImage logo, int canvas, double heightFraction, boolean silhouette) {
        int height = (int) Math.round(canvas * heightFraction);
        int width = (int) Math.round(height * (double) logo.getWidth() / logo.getHeight());
        if (width > canvas * heightFraction) {
            // A wide logo is bounded by width instead.
            width = (int) Math.round(canvas * heightFraction);
            height = (int) Math.round(width * (double) logo.getHeight() / logo.getWidth());
        }

        BufferedImage out = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = out.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(logo, (canvas - width) / 2, (canvas - height) / 2, width, height, null);
        graphics.dispose();

        if (silhouette) {
            for (int y = 0; y < canvas; y++) {
                for (int x = 0; x < canvas; x++) {
                    out.setRGB(x, y, (out.getRGB(x, y) & 0xFF000000) | 0x00FFFFFF);
                }
            }
        }
        return out;
    }

    static void write(BufferedImage image, File file) throws Exception {
        file.getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
    }
}
