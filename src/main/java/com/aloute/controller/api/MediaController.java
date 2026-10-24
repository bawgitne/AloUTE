package com.aloute.controller.api;

import com.aloute.common.api.ApiResponse;
import com.aloute.common.exception.ApiException;
import com.aloute.dto.response.MediaQuotaResponse;
import com.aloute.dto.response.MediaResponse;
import com.aloute.entity.MediaPurpose;
import com.aloute.security.currentuser.CurrentUserProvider;
import com.aloute.service.MediaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/media")
@Validated
public class MediaController {
    private final MediaService mediaService;
    private final CurrentUserProvider currentUserProvider;
    private final String uploadDir;
    private final boolean localMediaFallbackEnabled;

    @Autowired
    public MediaController(MediaService mediaService,
                           CurrentUserProvider currentUserProvider,
                           @Value("${aloute.media.upload-dir:uploads}") String uploadDir,
                           @Value("${aloute.media.local-storage:false}") boolean localMediaFallbackRequested,
                           Environment environment) {
        this.mediaService = mediaService;
        this.currentUserProvider = currentUserProvider;
        this.uploadDir = uploadDir;
        // Fail closed: the plain-read fallback requires BOTH the opt-in property AND the
        // dev runtime profile. Flipping the property alone (e.g. in a promoted production
        // deploy) must not activate local-filesystem reads.
        this.localMediaFallbackEnabled = localMediaFallbackRequested
                && environment.acceptsProfiles(Profiles.of("dev"));
    }

    /** Direct/test instantiation: the fallback flag is taken as given, without profile checks. */
    public MediaController(MediaService mediaService, CurrentUserProvider currentUserProvider,
                           String uploadDir, boolean localMediaFallbackEnabled) {
        this.mediaService = mediaService;
        this.currentUserProvider = currentUserProvider;
        this.uploadDir = uploadDir;
        this.localMediaFallbackEnabled = localMediaFallbackEnabled;
    }

    public MediaController(MediaService mediaService, CurrentUserProvider currentUserProvider) {
        this(mediaService, currentUserProvider, "uploads", false);
    }

    public MediaController(MediaService mediaService, CurrentUserProvider currentUserProvider, String uploadDir) {
        this(mediaService, currentUserProvider, uploadDir, false);
    }

    /** Test visibility: whether the dev-only plain-read fallback is active. */
    boolean localMediaFallbackActive() {
        return localMediaFallbackEnabled;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MediaResponse>> upload(@RequestPart("file") MultipartFile file,
                                                            @RequestPart("purpose") String purpose) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(mediaService.upload(currentUserProvider.requireUserId(), file, parsePurpose(purpose))));
    }

    /**
     * Accepts a media purpose sent either as a natural multipart text part (for example {@code USER_AVATAR},
     * as the Settings page sends it) or as a JSON string part (for example {@code "POST"}, as the post
     * composer sends it). The two client shapes reach the controller differently: the text part arrives raw
     * while the JSON part may keep its surrounding quotes, so the value is normalized before parsing.
     */
    static MediaPurpose parsePurpose(String rawPurpose) {
        if (rawPurpose == null || rawPurpose.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MEDIA_PURPOSE_REQUIRED", "Media purpose is required");
        }
        String normalized = rawPurpose.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        try {
            return MediaPurpose.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MEDIA_PURPOSE_INVALID", "Unsupported media purpose");
        }
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long mediaId) {
        mediaService.delete(currentUserProvider.requireUserId(), mediaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/quota")
    public ApiResponse<MediaQuotaResponse> quota() {
        return ApiResponse.success(mediaService.quota(currentUserProvider.requireUserId()));
    }

    @GetMapping("/file/**")
    public ResponseEntity<Resource> serveMediaFile(HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String prefix = "/api/media/file/";
        if (!fullPath.contains(prefix)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }
        String rawKey = fullPath.substring(fullPath.indexOf(prefix) + prefix.length());
        if (rawKey.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        String storageKey;
        try {
            storageKey = UriUtils.decode(rawKey, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        if (storageKey.contains("\0") || storageKey.contains(":")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        if (rawKey.startsWith("//") || rawKey.startsWith("\\\\")
                || storageKey.startsWith("//") || storageKey.startsWith("\\\\")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        String sanitizedKey = storageKey.replace('\\', '/');
        while (sanitizedKey.startsWith("/")) {
            sanitizedKey = sanitizedKey.substring(1);
        }

        if (sanitizedKey.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        Path baseDir;
        Path candidate;
        try {
            baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            candidate = baseDir.resolve(sanitizedKey).normalize();
        } catch (InvalidPathException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        if (!candidate.startsWith(baseDir) || candidate.equals(baseDir)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        Path relativePath = baseDir.relativize(candidate);
        Path fsRoot = baseDir.getRoot();
        if (fsRoot == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        DirectoryStream<Path> fsRootStream;
        try {
            fsRootStream = Files.newDirectoryStream(fsRoot);
        } catch (IOException | SecurityException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        if (!(fsRootStream instanceof SecureDirectoryStream<Path> sdsFsRoot)) {
            try {
                fsRootStream.close();
            } catch (IOException ignored) {}
            // SecureDirectoryStream is unsupported on some filesystem providers (e.g. Windows NTFS).
            // Fail closed in production; in the dev-only local-media mode the candidate path has
            // already passed normalize/startsWith/char checks, so a plain read is acceptable.
            if (localMediaFallbackEnabled) {
                return serveLocalFallback(baseDir, candidate, relativePath);
            }
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        List<SecureDirectoryStream<Path>> openedStreams = new ArrayList<>();
        openedStreams.add(sdsFsRoot);
        SeekableByteChannel fileChannel;
        try {
            Path rootRelative = fsRoot.relativize(baseDir);
            SecureDirectoryStream<Path> sdsUploadRoot = openSecureDirectory(sdsFsRoot, rootRelative, openedStreams);
            fileChannel = openSecureFileChannel(sdsUploadRoot, relativePath, openedStreams);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        } finally {
            closeStreams(openedStreams);
        }

        long contentLength;
        try {
            contentLength = fileChannel.size();
        } catch (IOException ex) {
            try {
                fileChannel.close();
            } catch (IOException ignored) {}
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        String filename = relativePath.getFileName().toString();
        String contentType = null;
        try {
            contentType = Files.probeContentType(Paths.get(filename));
        } catch (IOException | SecurityException ignored) {}

        InputStream inputStream = Channels.newInputStream(fileChannel);
        Resource resource = new InputStreamResource(inputStream) {
            @Override
            public long contentLength() {
                return contentLength;
            }

            @Override
            public String getFilename() {
                return filename;
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType != null ? contentType : "application/octet-stream")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .body(resource);
    }

    /**
     * Plain-read fallback for filesystems without SecureDirectoryStream (e.g. Windows NTFS),
     * reachable only in dev with aloute.media.local-storage=true. The caller has already
     * passed normalize/startsWith/character confinement; this re-checks the candidate without
     * following links and confirms real-path containment so a planted symlink (file or parent
     * directory) can never steer the read outside the upload root.
     */
    ResponseEntity<Resource> serveLocalFallback(Path baseDir, Path candidate, Path relativePath) {
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(candidate, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | SecurityException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }
        if (!attrs.isRegularFile() || attrs.isSymbolicLink()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }
        Path realBase;
        Path realCandidate;
        try {
            realBase = baseDir.toRealPath();
            realCandidate = candidate.toRealPath();
        } catch (IOException | SecurityException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }
        if (!realCandidate.startsWith(realBase) || realCandidate.equals(realBase)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }
        if (relativePath.getFileName() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }
        long contentLength = attrs.size();
        String filename = relativePath.getFileName().toString();
        String contentType = null;
        try {
            contentType = Files.probeContentType(Paths.get(filename));
        } catch (IOException | SecurityException ignored) {}
        try {
            InputStream inputStream = Files.newInputStream(candidate, StandardOpenOption.READ);
            Resource resource = new InputStreamResource(inputStream) {
                @Override
                public long contentLength() {
                    return contentLength;
                }
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType != null ? contentType : "application/octet-stream")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                    .body(resource);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }
    }

    SecureDirectoryStream<Path> openSecureDirectory(SecureDirectoryStream<Path> startStream,
                                                    Path relativePath,
                                                    List<SecureDirectoryStream<Path>> openedStreams) throws IOException {        if (relativePath == null) {
            return startStream;
        }
        String pathStr = relativePath.toString();
        if (pathStr.isEmpty() || pathStr.equals(".")) {
            return startStream;
        }

        SecureDirectoryStream<Path> currentDir = startStream;
        int nameCount = relativePath.getNameCount();

        for (int i = 0; i < nameCount; i++) {
            Path segment = relativePath.getName(i);
            if (segment.toString().isEmpty() || segment.toString().equals(".")) {
                continue;
            }
            BasicFileAttributeView view = currentDir.getFileAttributeView(segment, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (view == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
            }
            BasicFileAttributes attrs = view.readAttributes();
            if (!attrs.isDirectory() || attrs.isSymbolicLink()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
            }
            SecureDirectoryStream<Path> nextDir = currentDir.newDirectoryStream(segment, LinkOption.NOFOLLOW_LINKS);
            openedStreams.add(nextDir);
            currentDir = nextDir;
        }

        return currentDir;
    }

    SeekableByteChannel openSecureFileChannel(SecureDirectoryStream<Path> sdsRoot,
                                              Path relativePath,
                                              List<SecureDirectoryStream<Path>> openedStreams) throws IOException {
        Path parent = relativePath.getParent();
        SecureDirectoryStream<Path> leafDir = parent != null
                ? openSecureDirectory(sdsRoot, parent, openedStreams)
                : sdsRoot;

        Path leaf = relativePath.getFileName();
        if (leaf == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }
        BasicFileAttributeView view = leafDir.getFileAttributeView(leaf, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }
        BasicFileAttributes attrs = view.readAttributes();
        if (!attrs.isRegularFile() || attrs.isSymbolicLink()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
        }

        return leafDir.newByteChannel(leaf, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
    }

    private void closeStreams(List<SecureDirectoryStream<Path>> streams) {
        if (streams == null) {
            return;
        }
        for (int i = streams.size() - 1; i >= 0; i--) {
            try {
                streams.get(i).close();
            } catch (IOException ignored) {}
        }
    }
}
