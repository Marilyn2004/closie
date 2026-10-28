package com.fashionsearch.studio;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "closie.studio.enabled", havingValue = "true")
public class StudioAssets {
  private final Path root;
  private final StudioStore store;

  public StudioAssets(
      StudioStore store, @Value("${closie.studio.asset-path:.local/studio-assets}") String path)
      throws IOException {
    this.store = store;
    root = Path.of(path).toAbsolutePath().normalize();
    Files.createDirectories(root);
  }

  public String save(String owner, byte[] bytes) throws IOException {
    if (bytes.length == 0 || bytes.length > 8 * 1024 * 1024)
      throw new IllegalArgumentException("Images must be under 8 MB.");
    // Inspect dimensions before allocating a decoded raster. Re-encoding strips EXIF/location
    // metadata.
    BufferedImage image;
    try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw new IllegalArgumentException("Use a valid PNG or JPEG image.");
      var reader = readers.next();
      try {
        String format = reader.getFormatName();
        if (!Set.of("png", "jpeg", "jpg").contains(format.toLowerCase(Locale.ROOT)))
          throw new IllegalArgumentException("Use PNG or JPEG.");
        reader.setInput(stream);
        int w = reader.getWidth(0), h = reader.getHeight(0);
        if (w < 32 || h < 32 || (long) w * h > 16000000)
          throw new IllegalArgumentException(
              "Image must be at least 32 pixels and at most 16 megapixels.");
        image = reader.read(0);
      } finally {
        reader.dispose();
      }
    }
    double scale = Math.min(1, 1600.0 / Math.max(image.getWidth(), image.getHeight()));
    var clean =
        new BufferedImage(
            (int) (image.getWidth() * scale),
            (int) (image.getHeight() * scale),
            BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = clean.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, clean.getWidth(), clean.getHeight());
    graphics.drawImage(image, 0, 0, clean.getWidth(), clean.getHeight(), null);
    graphics.dispose();
    UUID id = UUID.randomUUID();
    String file = id + ".jpg";
    Path path = root.resolve(file);
    ImageIO.write(clean, "jpg", path.toFile());
    try {
      store
          .jdbc()
          .update("INSERT INTO studio_assets(id,owner,file_name) VALUES(?,?,?)", id, owner, file);
    } catch (RuntimeException e) {
      Files.deleteIfExists(path);
      throw e;
    }
    return "/api/studio/assets/" + id;
  }

  public byte[] read(String owner, UUID id) throws IOException {
    String file =
        store
            .jdbc()
            .query(
                "SELECT file_name FROM studio_assets WHERE id=? AND owner=?",
                (r, n) -> r.getString(1),
                id,
                owner)
            .stream()
            .findFirst()
            .orElseThrow(StudioStore::missing);
    return Files.readAllBytes(root.resolve(file));
  }

  public byte[] reference(String owner, String ref) throws IOException {
    if (ref.startsWith("/concepts/") && ref.matches("/concepts/[a-z-]+\\.(png|jpg)")) {
      try (var stream = getClass().getResourceAsStream("/static" + ref)) {
        if (stream == null) throw StudioStore.missing();
        return stream.readAllBytes();
      }
    }
    if (ref.startsWith("/api/studio/assets/"))
      return read(owner, UUID.fromString(ref.substring(ref.lastIndexOf('/') + 1)));
    throw new IllegalArgumentException("Unrecognized image reference.");
  }

  public void delete(String owner, UUID id) throws IOException {
    var files =
        store
            .jdbc()
            .query(
                "SELECT file_name FROM studio_assets WHERE owner=? AND id=?",
                (r, n) -> r.getString(1),
                owner,
                id);
    for (String file : files) Files.deleteIfExists(root.resolve(file));
    store.jdbc().update("DELETE FROM studio_assets WHERE owner=? AND id=?", owner, id);
  }
}
