package com.saathchalo.files;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.Crypto;
import com.saathchalo.config.AppProperties;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores profile photos on disk. Every upload is decoded and re-encoded as a fresh 400×400 JPEG,
 * which strips metadata such as GPS location and neutralises files that only pretend to be images.
 */
@Component
public class AvatarStorage {

	private static final Logger log = LoggerFactory.getLogger(AvatarStorage.class);

	static final int SIZE = 400;

	private static final long MAX_PIXELS = 40_000_000L;

	private static final Pattern FILE_NAME = Pattern.compile("[0-9a-f-]{36}-[0-9a-f]{16}\\.jpg");

	private final Path avatarDir;

	public AvatarStorage(AppProperties props) {
		this.avatarDir = Path.of(props.storage().dir()).toAbsolutePath().normalize().resolve("avatars");
	}

	/** @return the stored path relative to the storage root, e.g. {@code avatars/<name>.jpg} */
	public String store(UUID userId, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw ApiException.badRequest("file_missing", "Please choose a photo to upload.");
		}
		BufferedImage source;
		try (InputStream in = file.getInputStream()) {
			source = readImage(in);
		}
		catch (IOException e) {
			throw unreadable();
		}
		BufferedImage avatar = cropSquare(source, SIZE);
		String name = userId + "-" + Crypto.randomHex(8) + ".jpg";
		try {
			Files.createDirectories(avatarDir);
			writeJpeg(avatar, avatarDir.resolve(name));
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not save avatar", e);
		}
		return "avatars/" + name;
	}

	public Optional<Path> find(String fileName) {
		if (!FILE_NAME.matcher(fileName).matches()) {
			return Optional.empty();
		}
		Path path = avatarDir.resolve(fileName);
		return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
	}

	public void deleteQuietly(String relativePath) {
		if (relativePath == null || !relativePath.startsWith("avatars/")) {
			return;
		}
		find(relativePath.substring("avatars/".length())).ifPresent(path -> {
			try {
				Files.deleteIfExists(path);
			}
			catch (IOException e) {
				log.warn("Could not delete old avatar {}", path, e);
			}
		});
	}

	/** Decodes a JPG or PNG, refusing anything else and images that are too big or too small. */
	static BufferedImage readImage(InputStream in) throws IOException {
		try (ImageInputStream iis = ImageIO.createImageInputStream(in)) {
			Iterator<ImageReader> readers = iis == null ? null : ImageIO.getImageReaders(iis);
			if (readers == null || !readers.hasNext()) {
				throw ApiException.badRequest("unsupported_image", "Please upload a JPG or PNG photo.");
			}
			ImageReader reader = readers.next();
			try {
				String format = reader.getFormatName().toLowerCase(Locale.ROOT);
				if (!format.equals("jpeg") && !format.equals("jpg") && !format.equals("png")) {
					throw ApiException.badRequest("unsupported_image", "Please upload a JPG or PNG photo.");
				}
				reader.setInput(iis, true, true);
				long width = reader.getWidth(0);
				long height = reader.getHeight(0);
				if (width * height > MAX_PIXELS) {
					throw ApiException.badRequest("image_too_big", "That photo is too large. Please choose a smaller one.");
				}
				if (width < 100 || height < 100) {
					throw ApiException.badRequest("image_too_small", "That photo is too small. Please choose a clearer one.");
				}
				BufferedImage image = reader.read(0);
				if (image == null) {
					throw unreadable();
				}
				return image;
			}
			finally {
				reader.dispose();
			}
		}
	}

	/** Centre-crops to a square and scales it to {@code size}, flattening transparency onto white. */
	static BufferedImage cropSquare(BufferedImage src, int size) {
		int side = Math.min(src.getWidth(), src.getHeight());
		int x = (src.getWidth() - side) / 2;
		int y = (src.getHeight() - side) / 2;
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, size, size);
			g.drawImage(src, 0, 0, size, size, x, y, x + side, y + side, null);
		}
		finally {
			g.dispose();
		}
		return out;
	}

	private static void writeJpeg(BufferedImage image, Path target) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
		try (OutputStream os = Files.newOutputStream(tmp); ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
			writer.setOutput(ios);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(0.85f);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		finally {
			writer.dispose();
		}
		Files.move(tmp, target);
	}

	static ApiException unreadable() {
		return ApiException.badRequest("unreadable_image", "We couldn't read that photo. Please try a different one.");
	}
}
