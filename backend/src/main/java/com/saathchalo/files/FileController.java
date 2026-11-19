package com.saathchalo.files;

import java.time.Duration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FileController {

	private final AvatarStorage avatars;

	public FileController(AvatarStorage avatars) {
		this.avatars = avatars;
	}

	/** Avatar file names are unique per upload, so browsers may cache them forever. */
	@GetMapping("/avatars/{fileName}")
	public ResponseEntity<Resource> avatar(@PathVariable String fileName) {
		return avatars.find(fileName)
			.<ResponseEntity<Resource>>map(path -> ResponseEntity.ok()
				.contentType(MediaType.IMAGE_JPEG)
				.cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
				.header("X-Content-Type-Options", "nosniff")
				.body(new FileSystemResource(path)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
