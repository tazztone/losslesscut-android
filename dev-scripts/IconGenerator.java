import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.Map;
import java.util.HashMap;

public class IconGenerator {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: java IconGenerator <logo-path> <res-dir-path>");
            System.exit(1);
        }

        File logoFile = new File(args[0]);
        File resDir = new File(args[1]);

        if (!logoFile.exists()) {
            System.out.println("Logo file not found: " + logoFile.getAbsolutePath());
            System.exit(1);
        }

        BufferedImage originalImage = ImageIO.read(logoFile);

        Map<String, Integer> configs = new HashMap<>();
        configs.put("mipmap-mdpi", 48);
        configs.put("mipmap-hdpi", 72);
        configs.put("mipmap-xhdpi", 96);
        configs.put("mipmap-xxhdpi", 144);
        configs.put("mipmap-xxxhdpi", 192);

        for (Map.Entry<String, Integer> entry : configs.entrySet()) {
            String dirName = entry.getKey();
            int size = entry.getValue();

            File targetDir = new File(resDir, dirName);
            if (!targetDir.exists()) targetDir.mkdirs();

            // Resize for standard icon
            Image scaledImage = originalImage.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            BufferedImage bufferedImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = bufferedImage.createGraphics();
            g2d.drawImage(scaledImage, 0, 0, null);
            g2d.dispose();

            ImageIO.write(bufferedImage, "png", new File(targetDir, "ic_launcher.png"));
            ImageIO.write(bufferedImage, "png", new File(targetDir, "ic_launcher_round.png"));
            
            System.out.println("Generated icons for " + dirName);
        }

        // Generate Banner
        int bannerWidth = 320;
        int bannerHeight = 180;
        double ratio = Math.min((double)bannerWidth / originalImage.getWidth(), (double)bannerHeight / originalImage.getHeight());
        int newWidth = (int)(originalImage.getWidth() * ratio);
        int newHeight = (int)(originalImage.getHeight() * ratio);

        BufferedImage bannerImage = new BufferedImage(bannerWidth, bannerHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2dBanner = bannerImage.createGraphics();
        g2dBanner.drawImage(originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), (bannerWidth - newWidth) / 2, (bannerHeight - newHeight) / 2, null);
        g2dBanner.dispose();

        File bannerDir = new File(resDir, "mipmap-xhdpi");
        if (!bannerDir.exists()) bannerDir.mkdirs();
        ImageIO.write(bannerImage, "png", new File(bannerDir, "ic_banner.png"));
        System.out.println("Generated ic_banner.png");
    }
}
