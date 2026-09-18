package app.controller;

import app.service.ProfileImageService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * Setting and clearing profile pictures, for both donors and NGOs.
 *
 * Every method returns the same shape — {"avatarUrl": "..."} — so the frontend has one way to
 * update what it shows afterwards, whether the user uploaded a photo, picked an illustration, or
 * removed their picture entirely. A null value is a legitimate answer meaning "draw the initials".
 *
 * NOTE ON THE TEXT FIELDS: `preset` comes in as @RequestParam, not @RequestPart. For a plain form
 * field @RequestParam reads the value directly, while @RequestPart routes it through content
 * negotiation, which is more machinery than a one-word string needs.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileImageController {

    private final ProfileImageService profileImageService;

    public ProfileImageController(ProfileImageService profileImageService) {
        this.profileImageService = profileImageService;
    }

    /** A donor uploads a photo of themselves. */
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadUserAvatar(@RequestPart("file") MultipartFile file,
                                                Authentication authentication) {
        return avatar(profileImageService.uploadUserAvatar(authentication.getName(), file));
    }

    /**
     * A donor picks one of the bundled illustrations instead — or sends an empty value to go back
     * to plain initials.
     */
    @PutMapping("/avatar/preset")
    public Map<String, String> setUserPreset(@RequestParam(value = "preset", required = false) String preset,
                                             Authentication authentication) {
        return avatar(profileImageService.setUserPreset(authentication.getName(), preset));
    }

    /** An NGO uploads its logo. The service checks the caller owns this particular NGO. */
    @PostMapping(value = "/ngo/{ngoId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadNgoLogo(@PathVariable String ngoId,
                                             @RequestPart("file") MultipartFile file,
                                             Authentication authentication) {
        return avatar(profileImageService.uploadNgoLogo(ngoId, authentication.getName(), file));
    }

    @PutMapping("/ngo/{ngoId}/logo/preset")
    public Map<String, String> setNgoPreset(@PathVariable String ngoId,
                                            @RequestParam(value = "preset", required = false) String preset,
                                            Authentication authentication) {
        return avatar(profileImageService.setNgoPreset(ngoId, authentication.getName(), preset));
    }

    /**
     * Map.of() would be the obvious way to build this, but it throws on a null value — and null is
     * exactly what "I removed my picture" returns. HashMap accepts it.
     */
    private Map<String, String> avatar(String url) {
        Map<String, String> body = new HashMap<>();
        body.put("avatarUrl", url);
        return body;
    }
}
