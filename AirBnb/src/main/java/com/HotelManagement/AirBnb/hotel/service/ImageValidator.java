package com.HotelManagement.AirBnb.hotel.service;

import com.HotelManagement.AirBnb.hotel.entity.PhotoBase;
import com.HotelManagement.AirBnb.shared.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Validates uploaded photos before they are stored. The type is detected from the file's
 * magic bytes (the client-supplied Content-Type and extension are not trusted), and JPEG/PNG
 * headers are parsed to reject corrupt files and absurd dimensions without decoding pixels.
 */
@Component
public class ImageValidator {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_PHOTOS = 10;
    private static final int MAX_DIMENSION = 12_000;

    public void checkPhotoLimit(long existing, int added) {
        if (existing + added > MAX_PHOTOS) {
            throw new BadRequestException("You can upload at most " + MAX_PHOTOS + " photos");
        }
    }

    /** Validates the file and copies it with trusted metadata into {@code target}. */
    public <T extends PhotoBase> T toPhoto(MultipartFile file, T target) {
        String name = sanitizeFileName(file.getOriginalFilename());
        if (file.isEmpty()) {
            throw new BadRequestException("'" + name + "' is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("'" + name + "' is larger than 5 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Could not read '" + name + "'");
        }
        String contentType = detectType(bytes);
        if (contentType == null) {
            throw new BadRequestException("'" + name + "' is not a JPEG, PNG or WebP image");
        }
        if (!contentType.equals("image/webp")) {
            checkReadableHeader(bytes, name);
        }
        target.setFileName(name);
        target.setContentType(contentType);
        target.setSizeBytes((long) bytes.length);
        target.setData(bytes);
        return target;
    }

    public List<MultipartFile> nonNull(List<MultipartFile> files) {
        return files == null ? List.of() : files;
    }

    /** "image/jpeg", "image/png" or "image/webp" from the magic bytes, or null for anything else. */
    public static String detectType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        if (b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8), png)) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static void checkReadableHeader(byte[] bytes, String name) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new BadRequestException("'" + name + "' is not a valid image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > MAX_DIMENSION || h > MAX_DIMENSION) {
                    throw new BadRequestException("'" + name + "' has unsupported dimensions");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BadRequestException bre) {
                throw bre;
            }
            throw new BadRequestException("'" + name + "' is not a valid image");
        }
    }

    private static String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "photo";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (name.isEmpty()) {
            return "photo";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }
}
