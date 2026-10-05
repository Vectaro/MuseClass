package ua.museclass.klass;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import ua.museclass.common.ApiException;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClassService {

    private static final int CODE_ATTEMPTS = 20;

    private final ClassRepository classes;
    private final SecureRandom random = new SecureRandom();

    public ClassService(ClassRepository classes) {
        this.classes = classes;
    }

    public ClassRepository.ClassRow create(UUID me, String name, String rawPrefix) {
        Optional<String> prefix = parsePrefix(rawPrefix);
        String cleanName = name.trim();
        for (int i = 0; i < CODE_ATTEMPTS; i++) {
            // Якщо з префіксом викладача все зайнято (їх лише 192), переходимо на випадковий.
            String p = i < CODE_ATTEMPTS / 2 ? prefix.orElse(null) : null;
            Optional<UUID> id = classes.insert(ClassCodes.generate(p, random), cleanName, me);
            if (id.isPresent()) return classes.view(id.get(), me).orElseThrow();
        }
        throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Не вдалося підібрати вільний код класу. Спробуй ще раз.");
    }

    public ClassRepository.ClassRow join(UUID me, String rawCode) {
        String code = ClassCodes.normalizeInput(rawCode)
                .orElseThrow(() -> ApiException.badRequest("Код класу має вигляд PNO-3A."));
        UUID classId = classes.findIdByCode(code)
                .orElseThrow(() -> ApiException.notFound("Клас з таким кодом"));
        UUID teacher = classes.teacherOf(classId).orElseThrow();
        if (teacher.equals(me)) {
            throw ApiException.conflict("Це твій власний клас — ти в ньому викладач.");
        }
        classes.addMember(classId, me);
        return classes.view(classId, me).orElseThrow();
    }

    public List<ClassRepository.ClassRow> mine(UUID me) {
        return classes.mine(me);
    }

    public ClassRepository.ClassRow view(UUID me, UUID classId) {
        requireMember(me, classId);
        return classes.view(classId, me).orElseThrow();
    }

    public List<ClassRepository.MemberRow> members(UUID me, UUID classId) {
        requireMember(me, classId);
        return classes.members(classId);
    }

    public ClassRepository.ClassRow regenerateCode(UUID me, UUID classId, String rawPrefix) {
        requireTeacher(me, classId);
        Optional<String> prefix = parsePrefix(rawPrefix);
        for (int i = 0; i < CODE_ATTEMPTS; i++) {
            String p = i < CODE_ATTEMPTS / 2 ? prefix.orElse(null) : null;
            try {
                if (classes.updateCode(classId, ClassCodes.generate(p, random))) {
                    return classes.view(classId, me).orElseThrow();
                }
            } catch (DuplicateKeyException raced) {
                // хтось щойно зайняв той самий код — пробуємо інший
            }
        }
        throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Не вдалося підібрати вільний код класу. Спробуй ще раз.");
    }

    public ClassRepository.ClassRow rename(UUID me, UUID classId, String name) {
        requireTeacher(me, classId);
        classes.rename(classId, name.trim());
        return classes.view(classId, me).orElseThrow();
    }

    public void delete(UUID me, UUID classId) {
        requireTeacher(me, classId);
        classes.delete(classId);
    }

    /** Викладач виключає учня, або учень виходить сам. */
    public void removeMember(UUID me, UUID classId, UUID userId) {
        String role = requireMember(me, classId);
        if (!"teacher".equals(role) && !me.equals(userId)) {
            throw ApiException.forbidden("Виключати учнів може тільки викладач.");
        }
        if (classes.removeMember(classId, userId) == 0) {
            throw ApiException.notFound("Учня в цьому класі");
        }
    }

    /** Повертає роль; для чужих і неіснуючих класів — 404, щоб не світити їхнє існування. */
    public String requireMember(UUID me, UUID classId) {
        String role = classes.roleOf(classId, me).orElse("none");
        if ("none".equals(role)) throw ApiException.notFound("Клас");
        return role;
    }

    public void requireTeacher(UUID me, UUID classId) {
        String role = requireMember(me, classId);
        if (!"teacher".equals(role)) {
            throw ApiException.forbidden("Це може тільки викладач класу.");
        }
    }

    private static Optional<String> parsePrefix(String raw) {
        try {
            return ClassCodes.normalizePrefix(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }
}
