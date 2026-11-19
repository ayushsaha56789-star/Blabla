package com.saathchalo.auth.staff;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;

/** Draws a QR code as a PNG data URL, for scanning the 2FA secret into an authenticator app. */
public final class QrCodes {

	private static final int SCALE = 6;

	private QrCodes() {
	}

	public static String pngDataUrl(String text) {
		try {
			BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
					Map.of(EncodeHintType.MARGIN, 2, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
			int size = matrix.getWidth() * SCALE;
			BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_BINARY);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					image.setRGB(x, y, matrix.get(x / SCALE, y / SCALE) ? 0xFF000000 : 0xFFFFFFFF);
				}
			}
			ByteArrayOutputStream png = new ByteArrayOutputStream();
			ImageIO.write(image, "png", png);
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
		}
		catch (WriterException | IOException e) {
			throw new IllegalStateException("Could not draw QR code", e);
		}
	}
}
