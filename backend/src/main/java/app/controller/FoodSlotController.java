package app.controller;

import app.dto.FoodSettingsRequest;
import app.dto.FoodSlotRequest;
import app.dto.NgoResponse;
import app.enums.FoodSlotStatus;
import app.model.FoodSlot;
import app.service.FoodSlotService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Food slots. Reading is public so anyone can see which meals still need a sponsor; writing is
 * NGO-only (see SecurityConfig) and each call also checks the NGO owns the slot. Sponsoring a slot
 * is NOT an endpoint here — it is a donation with a foodSlotId, via /api/donations.
 */
@RestController
@RequestMapping("/api")
public class FoodSlotController {
    private final FoodSlotService foodSlotService;

    public FoodSlotController(FoodSlotService foodSlotService) { this.foodSlotService = foodSlotService; }

    /** GET /api/food-slots?from=2026-09-29&to=2026-10-05&status=OPEN&ngoId=... — all filters optional. */
    @GetMapping("/food-slots")
    public List<FoodSlot> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String ngoId,
            @RequestParam(required = false) FoodSlotStatus status) {
        return foodSlotService.list(from, to, ngoId, status);
    }

    @PostMapping("/ngos/{ngoId}/food-slots")
    public FoodSlot create(@PathVariable String ngoId, @RequestBody FoodSlotRequest request, Authentication auth) {
        return foodSlotService.create(ngoId, auth.getName(), request);
    }

    /** Mark an open slot as covered by someone the NGO arranged offline. Body: { "sponsorName": "..." } (optional). */
    @PatchMapping("/ngos/{ngoId}/food-slots/{slotId}/fill")
    public FoodSlot markFilled(@PathVariable String ngoId, @PathVariable String slotId,
                               @RequestBody(required = false) Map<String, String> body, Authentication auth) {
        return foodSlotService.markFilledOffline(ngoId, slotId, auth.getName(), body == null ? null : body.get("sponsorName"));
    }

    /**
     * Save the NGO's per-person meal costs and headcounts, which price every slot it posts after.
     * Returns the public NGO view so the dashboard can show the saved values straight away.
     */
    @PutMapping("/ngos/{ngoId}/food-settings")
    public NgoResponse updateFoodSettings(@PathVariable String ngoId, @RequestBody FoodSettingsRequest request,
                                          Authentication auth) {
        return new NgoResponse(foodSlotService.updateFoodSettings(ngoId, auth.getName(), request));
    }

    @PatchMapping("/ngos/{ngoId}/food-slots/{slotId}/reopen")
    public FoodSlot reopen(@PathVariable String ngoId, @PathVariable String slotId, Authentication auth) {
        return foodSlotService.reopen(ngoId, slotId, auth.getName());
    }

    @DeleteMapping("/ngos/{ngoId}/food-slots/{slotId}")
    public void delete(@PathVariable String ngoId, @PathVariable String slotId, Authentication auth) {
        foodSlotService.delete(ngoId, slotId, auth.getName());
    }
}
