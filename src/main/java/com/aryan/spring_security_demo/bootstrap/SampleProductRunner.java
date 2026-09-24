package com.aryan.spring_security_demo.bootstrap;

import com.aryan.spring_security_demo.config.StartupProperties;
import com.aryan.spring_security_demo.model.Category;
import com.aryan.spring_security_demo.model.Image;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.repository.CategoryRepository;
import com.aryan.spring_security_demo.repository.ProductRepository;
import com.aryan.spring_security_demo.service.image.ImagePersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.sql.rowset.serial.SerialBlob;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds a small set of demo <b>products</b>, each with one generated placeholder
 * image, so a fresh database exposes a browsable catalog (with downloadable
 * images) out of the box. Runs at {@link Order @Order(25)} — after
 * {@link DefaultDataRunner} (@Order(20)) has committed the categories these
 * products reference, and before {@code CacheWarmupRunner} (@Order(40)) so the
 * warmed {@code products} cache already includes these rows.
 *
 * <p>The image is a PNG rendered in memory (a coloured tile with the product
 * name) and stored as a {@link Blob}, exactly like a real upload — so no network
 * access is needed at boot and no binary fixtures are committed. Each image is
 * reachable through the normal download endpoint via its {@code URL}.
 *
 * <p>Idempotent: a product already present (matched on name + brand) is skipped,
 * so restarts never duplicate the catalog. Disable per-run with {@code --skip-seed}
 * or globally via {@code app.startup.seed.products-enabled=false} (or by turning
 * off {@code app.startup.seed.enabled}).
 */
@Component
@Order(25)
@RequiredArgsConstructor
@Slf4j
public class SampleProductRunner implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StartupProperties startupProperties;

    /** A demo product to seed, paired with the category it belongs to. */
    private record SampleProduct(String name, String brand, BigDecimal price,
                                 int inventory, String category, String description) {
    }

    private static final List<SampleProduct> SAMPLES = List.of(
            new SampleProduct("Aurora Wireless Headphones", "SoundWave", new BigDecimal("129.99"),
                    40, "Electronics", "Over-ear Bluetooth headphones with active noise cancellation."),
            new SampleProduct("Nimbus Mechanical Keyboard", "KeyForge", new BigDecimal("89.50"),
                    75, "Electronics", "Hot-swappable 75% mechanical keyboard with RGB backlight."),
            new SampleProduct("Clean Code", "Prentice Hall", new BigDecimal("34.99"),
                    120, "Books", "A handbook of agile software craftsmanship by Robert C. Martin."),
            new SampleProduct("Merino Crew Sweater", "NorthThread", new BigDecimal("64.00"),
                    60, "Clothing", "Lightweight 100% merino wool crew-neck sweater."),
            new SampleProduct("Ceramic Pour-Over Set", "Brewly", new BigDecimal("42.75"),
                    35, "Home & Kitchen", "Single-cup ceramic pour-over dripper with matching carafe."),
            new SampleProduct("Trailhead Running Shoes", "Stride", new BigDecimal("98.00"),
                    50, "Sports", "Cushioned trail runners with a grippy all-terrain outsole.")
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        StartupProperties.Seed seed = startupProperties.getSeed();
        if (!seed.isEnabled() || !seed.isProductsEnabled() || args.containsOption("skip-seed")) {
            log.info("[seed] Product seeding skipped (enabled={}, products-enabled={}, --skip-seed={})",
                    seed.isEnabled(), seed.isProductsEnabled(), args.containsOption("skip-seed"));
            return;
        }

        int created = 0;
        int skippedMissingCategory = 0;
        for (SampleProduct sample : SAMPLES) {
            if (productRepository.existsByNameAndBrand(sample.name(), sample.brand())) {
                continue;
            }

            Category category = categoryRepository.findByName(sample.category());
            if (category == null) {
                // Category seeding is what supplies these; if it was disabled or
                // customized away, skip the product rather than fail the whole boot.
                log.warn("[seed] Skipping product '{}' — category '{}' not found",
                        sample.name(), sample.category());
                skippedMissingCategory++;
                continue;
            }

            Product product = new Product(sample.name(), sample.price(), sample.description(),
                    sample.brand(), sample.inventory(), category);
            product.setImageList(new ArrayList<>());
            attachPlaceholderImage(product);

            // Cascade ALL persists the image with the product and assigns its id;
            // set the download URL from that id and save again (dirty-checked flush).
            Product saved = productRepository.save(product);
            Image image = saved.getImageList().get(0);
            image.setURL(ImagePersistenceService.DOWNLOAD_URL_PREFIX + image.getId());
            productRepository.save(saved);
            created++;
        }

        log.info("[seed] Sample products ready — {} created, {} already present, {} skipped (missing category)",
                created, SAMPLES.size() - created - skippedMissingCategory, skippedMissingCategory);
    }

    private void attachPlaceholderImage(Product product) {
        String fileName = slug(product.getName()) + ".png";
        Image image = new Image();
        image.setFileName(fileName);
        image.setFileType("image/png");
        image.setImage(toBlob(renderPlaceholderPng(product.getName())));
        image.setProduct(product);
        product.getImageList().add(image);
    }

    /** Renders a 600×600 PNG: the product name centred on a colour derived from the name. */
    private byte[] renderPlaceholderPng(String label) {
        int size = 600;
        BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Stable, pleasant background from the name's hue; white text on top.
            float hue = (Math.abs(label.hashCode()) % 360) / 360f;
            g.setColor(Color.getHSBColor(hue, 0.55f, 0.75f));
            g.fillRect(0, 0, size, size);

            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
            drawWrappedCentred(g, label, size);
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(canvas, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            // In-memory PNG encoding does not do real I/O; a failure here is fatal to seeding.
            throw new IllegalStateException("Failed to render placeholder image for '" + label + "'", e);
        }
    }

    /** Word-wraps {@code text} to the canvas width and draws it vertically centred. */
    private void drawWrappedCentred(Graphics2D g, String text, int size) {
        int maxWidth = size - 80;
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (g.getFontMetrics().stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        lines.add(line.toString());

        int lineHeight = g.getFontMetrics().getHeight();
        int totalHeight = lineHeight * lines.size();
        int y = (size - totalHeight) / 2 + g.getFontMetrics().getAscent();
        for (String l : lines) {
            int x = (size - g.getFontMetrics().stringWidth(l)) / 2;
            g.drawString(l, x, y);
            y += lineHeight;
        }
    }

    private Blob toBlob(byte[] bytes) {
        try {
            return new SerialBlob(bytes);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to wrap image bytes in a Blob", e);
        }
    }

    private String slug(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
