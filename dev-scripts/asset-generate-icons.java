import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.Map;
import java.util.HashMap;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Consolidated icon generation tool for LosslessCut Android.
 * This file is directly executable via 'java --source 21 asset-generate-icons.java' 
 * or './asset-generate-icons.java' if chmod +x is set.
 */
public class AssetGenerateIcons {
    public static void main(String[] args) throws IOException {
        // Calculate paths relative to the script location
        Path scriptPath = Paths.get("dev-scripts", "asset-generate-icons.java");
        Path projectRoot = Paths.get("").toAbsolutePath();
        
        File logoFile = projectRoot.resolve("docs/logo.png").toFile();
        File resDir = projectRoot.resolve("app/src/main/res").toFile();

        if (!logoFile.exists()) {
            System.err.println("❌ Error: Logo file not found at " + logoFile.getAbsolutePath());
            System.exit(1);
        }

        System.out.println("🚀 Generating Android icons from: " + logoFile.getName());
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
            
            System.out.println("  ✅ Generated: " + dirName);
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
        System.out.println("✨ Generated ic_banner.png in mipmap-xhdpi");
        System.out.println("Done!");
    }
}
