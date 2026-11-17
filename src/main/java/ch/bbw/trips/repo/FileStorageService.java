package ch.bbw.trips.repo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService {

	private static final Map<String, String> ALLOWED_TYPES = Map.of("image/jpeg", ".jpg", "image/png", ".png");

	private final String bucket;
	private final Path location;
	private S3Client s3;

	public FileStorageService(@Value("${app.storage.s3-bucket:}") String bucket,
							  @Value("${app.storage.location:./uploads}") String location) {
		this.bucket = bucket;
		this.location = Path.of(location).toAbsolutePath().normalize();
	}

	public String store(MultipartFile file) throws IOException {
		String extension = ALLOWED_TYPES.get(file.getContentType());
		if (file.isEmpty() || extension == null) {
			throw new IllegalArgumentException("Only non-empty JPEG or PNG files are allowed");
		}
		String key = UUID.randomUUID() + extension;
		if (useS3()) {
			s3().putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(file.getContentType()).build(),
					RequestBody.fromBytes(file.getBytes()));
		} else {
			Files.createDirectories(location);
			file.transferTo(location.resolve(key));
		}
		return key;
	}

	public byte[] load(String key) throws IOException {
		if (useS3()) {
			return s3().getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
		}
		Path file = location.resolve(key).normalize();
		if (!file.startsWith(location)) {
			throw new IllegalArgumentException("Invalid file name");
		}
		return Files.readAllBytes(file);
	}

	private boolean useS3() {
		return !bucket.isBlank();
	}

	private synchronized S3Client s3() {
		if (s3 == null) {
			s3 = S3Client.create();
		}
		return s3;
	}
}
