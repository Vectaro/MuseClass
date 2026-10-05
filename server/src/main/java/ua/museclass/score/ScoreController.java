package ua.museclass.score;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ua.museclass.auth.TokenService;
import ua.museclass.common.ApiException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
public class ScoreController {

    static final MediaType MUSICXML = MediaType.parseMediaType("application/vnd.recordare.musicxml+xml");
    static final MediaType MXL = MediaType.parseMediaType("application/vnd.recordare.musicxml");

    private final ScoreService service;

    public ScoreController(ScoreService service) {
        this.service = service;
    }

    // ---------------------------------------------------------------- партитури

    /**
     * multipart/form-data: file (обов'язково, .musicxml або .mxl) і необов'язкові
     * title, composer, arranger, kind, rights, visibility. Порожнє — береться з файлу.
     */
    @PostMapping(path = "/api/scores", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ScoreService.ScoreView upload(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam("file") MultipartFile file,
                                         @RequestParam(required = false) String title,
                                         @RequestParam(required = false) String composer,
                                         @RequestParam(required = false) String arranger,
                                         @RequestParam(required = false) String kind,
                                         @RequestParam(required = false) String rights,
                                         @RequestParam(required = false) String visibility) {
        return service.upload(TokenService.userId(jwt), bytes(file), file.getOriginalFilename(),
                new ScoreService.Metadata(title, composer, arranger, kind, rights, visibility));
    }

    @GetMapping("/api/scores/mine")
    public List<ScoreRepository.Summary> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.mine(TokenService.userId(jwt));
    }

    @GetMapping("/api/scores/{id}")
    public ScoreService.ScoreView view(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.view(TokenService.userId(jwt), id);
    }

    /**
     * Оригінальний файл. ETag — SHA-256 вмісту: клієнт шле If-None-Match і
     * отримує 304, якщо його офлайн-копія актуальна.
     */
    @GetMapping("/api/scores/{id}/file")
    public ResponseEntity<byte[]> file(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                       @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        ScoreRepository.FileRow f = service.file(TokenService.userId(jwt), id);
        String etag = "\"" + f.sha256() + "\"";
        CacheControl cache = CacheControl.noCache().cachePrivate();
        if (ifNoneMatch != null && ifNoneMatch.contains(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
        }
        boolean mxl = "mxl".equals(f.format());
        String name = safeFileName(f.title()) + (mxl ? ".mxl" : ".musicxml");
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(cache)
                .contentType(mxl ? MXL : MUSICXML)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(f.content());
    }

    /** Нова версія файлу (збереження з редактора). */
    @PutMapping(path = "/api/scores/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ScoreService.ScoreView replaceFile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                              @RequestParam("file") MultipartFile file) {
        return service.replaceFile(TokenService.userId(jwt), id, bytes(file));
    }

    @PatchMapping("/api/scores/{id}")
    public ScoreService.ScoreView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @RequestBody ScoreService.Metadata patch) {
        return service.updateMeta(TokenService.userId(jwt), id, patch);
    }

    @DeleteMapping("/api/scores/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(TokenService.userId(jwt), id);
    }

    // ---------------------------------------------------------------- каталог

    @GetMapping("/api/catalog")
    public List<ScoreRepository.Summary> catalog(@AuthenticationPrincipal Jwt jwt,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(required = false) String kind,
                                                 @RequestParam(defaultValue = "30") int limit,
                                                 @RequestParam(defaultValue = "0") int offset) {
        return service.catalog(TokenService.userId(jwt), q, kind, limit, offset);
    }

    // ---------------------------------------------------------------- видача класу

    @GetMapping("/api/classes/{classId}/scores")
    public List<ScoreRepository.LibraryEntry> classLibrary(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable UUID classId) {
        return service.classLibrary(TokenService.userId(jwt), classId);
    }

    @PutMapping("/api/classes/{classId}/scores/{scoreId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID classId, @PathVariable UUID scoreId) {
        service.assign(TokenService.userId(jwt), classId, scoreId);
    }

    @DeleteMapping("/api/classes/{classId}/scores/{scoreId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID classId, @PathVariable UUID scoreId) {
        service.unassign(TokenService.userId(jwt), classId, scoreId);
    }

    // ---------------------------------------------------------------- допоміжне

    private static byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Файл порожній.");
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Не вдалося прочитати файл.");
        }
    }

    static String safeFileName(String title) {
        String s = title == null ? "" : title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (s.isEmpty()) s = "score";
        return s.length() > 100 ? s.substring(0, 100) : s;
    }
}
