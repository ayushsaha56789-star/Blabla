package com.saathchalo.files;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.Crypto;
import com.saathchalo.config.AppProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Public photos of rental cars. Each one is decoded and re-encoded as a JPEG (no hidden location data) at most
 * {@link #MAX_SIDE} pixels on the longer side, with a name chosen by the server, and served by {@link FileController}.
 */
@Component
public class CarPhotoStorage {

	private static final Logger log = LoggerFactory.getLogger(CarPhotoStorage.class);

	static final int MAX_SIDE = 1600;

	private static final Pattern FILE_NAME = Pattern.compile("[0-9a-f]{32}\\.jpg");

	private final Path dir;

	public CarPhotoStorage(AppProperties props) {
		this.dir = Path.of(props.storage().dir()).toAbsolutePath().normalize().resolve("cars");
	}

	/** @return the path relative to the storage root, e.g. {@code cars/<name>.jpg} */
	public String store(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw ApiException.badRequest("file_missing", "Please choose a photo of the car.");
		}
		BufferedImage source;
		try (InputStream in = file.getInputStream()) {
			source = AvatarStorage.readImage(in);
		}
		catch (IOException e) {
			throw AvatarStorage.unreadable();
		}
		String name = Crypto.randomHex(16) + ".jpg";
		try {
			Files.createDirectories(dir);
			Path tmp = dir.resolve(name + ".tmp");
			Files.write(tmp, jpeg(DocumentVault.fit(source, MAX_SIDE)));
			Files.move(tmp, dir.resolve(name));
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not save the car photo", e);
		}
		return "cars/" + name;
	}

	public Optional<Path> find(String fileName) {
		if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
			return Optional.empty();
		}
		Path p = dir.resolve(fileName);
		return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
	}

	public void deleteQuietly(String relativePath) {
		if (relativePath == null || !relativePath.startsWith("cars/")) {
			return;
		}
		find(relativePath.substring("cars/".length())).ifPresent(p -> {
			try {
				Files.deleteIfExists(p);
			}
			catch (IOException e) {
				log.warn("Could not delete car photo {}", p.getFileName(), e);
			}
		});
	}

	/** The public address of a stored photo. */
	public static String url(String relativePath) {
		return relativePath == null ? null : "/api/files/" + relativePath;
	}

	private static byte[] jpeg(BufferedImage image) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(bytes)) {
			writer.setOutput(ios);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(0.85f);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		finally {
			writer.dispose();
		}
		return bytes.toByteArray();
	}
}
