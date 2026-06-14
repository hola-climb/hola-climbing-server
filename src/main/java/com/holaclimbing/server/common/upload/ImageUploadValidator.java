package com.holaclimbing.server.common.upload;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

public final class ImageUploadValidator {

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES =
            Set.of(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE);

    private ImageUploadValidator() {
    }

    public static ImageUpload validate(MultipartFile image, String label, long maxBytes) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, label + " 파일이 필요합니다.");
        }
        if (image.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    label + "는 " + (maxBytes / 1024 / 1024) + "MB 이하만 업로드할 수 있습니다.");
        }
        String extension = extensionOf(image.getOriginalFilename());
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 이미지 확장자입니다.");
        }
        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 이미지 형식입니다.");
        }
        String normalizedContentType = "png".equals(extension)
                ? MediaType.IMAGE_PNG_VALUE
                : MediaType.IMAGE_JPEG_VALUE;
        return new ImageUpload(extension, normalizedContentType);
    }

    private static String extensionOf(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record ImageUpload(String extension, String contentType) {
    }
}
