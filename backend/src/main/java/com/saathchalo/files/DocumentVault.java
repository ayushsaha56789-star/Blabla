package com.saathchalo.files;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.Crypto;
import com.saathchalo.common.FieldEncryptor;
import com.saathchalo.config.AppProperties;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
 * Private documents such as ID photos. Each image is decoded and re-encoded (which drops hidden data like GPS
 * location), scaled to a sensible size, and stored encrypted under {@code <storage>/documents}. Never served
 * directly: staff fetch them through an audited API.
 */
@Component
public class DocumentVault {

	private static final Logger log = LoggerFactory.getLogger(DocumentVault.class);

	static final int MAX_SIDE = 2000;

	private static final Pattern FILE_NAME = Pattern.compile("[0-9a-f]{32}\\.bin");

	private final Path dir;

	private final FieldEncryptor encryptor;

	public DocumentVault(AppProperties props, FieldEncryptor encryptor) {
		this.dir = Path.of(props.storage().dir()).toAbsolutePath().normalize().resolve("documents");
		this.encryptor = encryptor;
	}

	/** @return the stored file's name, chosen by the server */
	public String store(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw ApiException.badRequest("file_missing", "Please choose a photo of your ID.");
		}
		BufferedImage source;
		try (InputStream in = file.getInputStream()) {
			source = AvatarStorage.readImage(in);
		}
		catch (IOException e) {
			throw AvatarStorage.unreadable();
		}
		String name = Crypto.randomHex(16) + ".bin";
		try {
			Files.createDirectories(dir);
			Path tmp = dir.resolve(name + ".tmp");
			Files.write(tmp, encryptor.encryptBytes(jpeg(fit(source, MAX_SIDE))));
			Files.move(tmp, dir.resolve(name));
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not save the document", e);
		}
		return name;
	}

	/** The decrypted JPEG, if the file still exists. */
	public Optional<byte[]> read(String name) {
		return path(name).map(p -> {
			try {
				return encryptor.decryptBytes(Files.readAllBytes(p));
			}
			catch (IOException e) {
				throw new IllegalStateException("Could not read the document", e);
			}
		});
	}

	public void deleteQuietly(String name) {
		path(name).ifPresent(p -> {
			try {
				Files.deleteIfExists(p);
			}
			catch (IOException e) {
				log.warn("Could not delete document {}", p.getFileName(), e);
			}
		});
	}

	private Optional<Path> path(String name) {
		if (name == null || !FILE_NAME.matcher(name).matches()) {
			return Optional.empty();
		}
		Path p = dir.resolve(name);
		return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
	}

	/** Scales down (never up) so the longer side is at most {@code max}, flattening transparency onto white. */
	static BufferedImage fit(BufferedImage src, int max) {
		double scale = Math.min(1.0, (double) max / Math.max(src.getWidth(), src.getHeight()));
		int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
		int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, w, h);
			g.drawImage(src, 0, 0, w, h, null);
		}
		finally {
			g.dispose();
		}
		return out;
	}

	private static byte[] jpeg(BufferedImage image) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(bytes)) {
			writer.setOutput(ios);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(0.88f);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		finally {
			writer.dispose();
		}
		return bytes.toByteArray();
	}
}
