package com.fashionsearch.studio;

import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/studio")
@ConditionalOnProperty(name = "closie.studio.enabled", havingValue = "true")
public class StudioController {
  final StudioService studio;
  final StudioStore store;
  final StudioJobs jobs;
  final StudioAssets assets;
  final ImageGenerationProvider provider;
  final GeminiVision vision;

  public StudioController(
      StudioService studio,
      StudioStore store,
      StudioJobs jobs,
      StudioAssets assets,
      ImageGenerationProvider provider,
      GeminiVision vision) {
    this.studio = studio;
    this.store = store;
    this.jobs = jobs;
    this.assets = assets;
    this.provider = provider;
    this.vision = vision;
  }

  @GetMapping("/status")
  public Object status() {
    return Map.of(
        "enabled",
        true,
        "imageConfigured",
        provider.configured(),
        "provider",
        "GEMINI",
        "model",
        Objects.toString(provider.model(), ""),
        "interpretation",
        "CURATED_RULES",
        "visionConfigured",
        vision.configured(),
        "aesthetics",
        StyleLibrary.STYLES);
  }

  @GetMapping("/discover")
  public Object discover(
      @RequestHeader("X-Studio-Session") String session,
      @RequestParam(defaultValue = "") String category) {
    return studio.discover(StudioService.owner(session), category);
  }

  @GetMapping("/items/{id}")
  public Object item(@RequestHeader("X-Studio-Session") String session, @PathVariable UUID id) {
    return store.get(StudioService.owner(session), id);
  }

  @GetMapping("/items")
  public Object items(
      @RequestHeader("X-Studio-Session") String session, @RequestParam String kind) {
    if (!Set.of("CONCEPT", "OWNED", "OUTFIT").contains(kind))
      throw new IllegalArgumentException("Invalid type.");
    return store.list(StudioService.owner(session), kind);
  }

  @PostMapping("/find")
  public Object find(
      @RequestHeader("X-Studio-Session") String session,
      @RequestBody StudioService.FindRequest request) {
    return studio.find(StudioService.owner(session), request);
  }

  public record RevisionRequest(UUID revisionId) {}

  @PostMapping("/items/{id}/revert")
  public Object revert(
      @RequestHeader("X-Studio-Session") String session,
      @PathVariable UUID id,
      @RequestBody RevisionRequest request) {
    return studio.revert(
        StudioService.owner(session), id, Objects.requireNonNull(request.revisionId()));
  }

  @GetMapping("/preferences")
  public Object preferences(@RequestHeader("X-Studio-Session") String session) {
    return studio.preferences(StudioService.owner(session));
  }

  @PutMapping("/preferences")
  public Object preferences(
      @RequestHeader("X-Studio-Session") String session,
      @RequestBody StudioService.Preferences request) {
    return studio.preferences(StudioService.owner(session), request);
  }

  @PostMapping("/owned/identify")
  public Object identify(
      @RequestHeader("X-Studio-Session") String session,
      @RequestBody StudioService.IdentifyRequest request) {
    return studio.identify(StudioService.owner(session), request);
  }

  @PostMapping("/owned/{id}/confirm")
  public Object confirm(
      @RequestHeader("X-Studio-Session") String session,
      @PathVariable UUID id,
      @RequestBody StudioService.ConfirmRequest request) {
    return studio.confirm(StudioService.owner(session), id, request);
  }

  @PostMapping("/outfits")
  public Object outfit(
      @RequestHeader("X-Studio-Session") String session,
      @RequestBody StudioService.OutfitRequest request) {
    return studio.outfit(StudioService.owner(session), request);
  }

  @PostMapping("/outfits/{id}/replace")
  public Object replace(
      @RequestHeader("X-Studio-Session") String session,
      @PathVariable UUID id,
      @RequestBody StudioService.ReplaceRequest request) {
    return studio.replace(StudioService.owner(session), id, request);
  }

  @PostMapping("/items/{id}/generate")
  public Object generate(
      @RequestHeader("X-Studio-Session") String session,
      @PathVariable UUID id,
      @RequestBody RevisionRequest request) {
    return jobs.enqueue(StudioService.owner(session), id, request.revisionId(), false);
  }

  @PostMapping("/outfits/{id}/doll")
  public Object doll(
      @RequestHeader("X-Studio-Session") String session,
      @PathVariable UUID id,
      @RequestBody RevisionRequest request) {
    return jobs.enqueue(StudioService.owner(session), id, request.revisionId(), true);
  }

  @GetMapping("/jobs")
  public Object jobs(@RequestHeader("X-Studio-Session") String session) {
    String owner = StudioService.owner(session);
    return store
        .jdbc()
        .query(
            "SELECT * FROM studio_jobs WHERE owner=? ORDER BY created_at DESC LIMIT 40",
            (r, n) -> jobs.row(r),
            owner);
  }

  @GetMapping("/jobs/{id}")
  public Object job(@RequestHeader("X-Studio-Session") String session, @PathVariable UUID id) {
    return jobs.get(StudioService.owner(session), id);
  }

  @PostMapping("/jobs/{id}/cancel")
  public Object cancel(@RequestHeader("X-Studio-Session") String session, @PathVariable UUID id) {
    return jobs.cancel(StudioService.owner(session), id);
  }

  @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Object upload(
      @RequestHeader("X-Studio-Session") String session, @RequestParam MultipartFile file)
      throws Exception {
    String owner = StudioService.owner(session);
    if (store
            .jdbc()
            .queryForObject("SELECT count(*) FROM studio_assets WHERE owner=?", Long.class, owner)
        >= 30)
      throw new IllegalArgumentException("Photo storage limit reached. Delete an old photo first.");
    return Map.of(
        "image",
        assets.save(owner, file.getBytes()),
        "notice",
        "Private photo; metadata removed. Confirm its visible attributes manually. No vision"
            + " provider is enabled.");
  }

  @GetMapping("/uploads")
  public Object uploads(@RequestHeader("X-Studio-Session") String session) {
    String owner = StudioService.owner(session);
    return store
        .jdbc()
        .query(
            "SELECT a.id FROM studio_assets a WHERE a.owner=? AND NOT EXISTS (SELECT 1 FROM"
                + " studio_documents d WHERE d.owner=a.owner AND"
                + " strpos(d.payload::text,a.id::text)>0) AND NOT EXISTS (SELECT 1 FROM"
                + " studio_revisions r JOIN studio_documents d ON d.id=r.document_id WHERE"
                + " d.owner=a.owner AND strpos(r.payload::text,a.id::text)>0) AND NOT EXISTS"
                + " (SELECT 1 FROM studio_jobs j WHERE j.owner=a.owner AND"
                + " strpos(COALESCE(j.result::text,''),a.id::text)>0) ORDER BY a.created_at DESC"
                + " LIMIT 30",
            (r, n) ->
                Map.of(
                    "id",
                    r.getObject(1, UUID.class),
                    "image",
                    "/api/studio/assets/" + r.getObject(1, UUID.class)),
            owner);
  }

  @GetMapping("/assets/{id}")
  public ResponseEntity<byte[]> asset(
      @RequestHeader("X-Studio-Session") String session, @PathVariable UUID id) throws Exception {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(assets.read(StudioService.owner(session), id));
  }

  @DeleteMapping("/assets/{id}")
  public void deleteAsset(@RequestHeader("X-Studio-Session") String session, @PathVariable UUID id)
      throws Exception {
    assets.delete(StudioService.owner(session), id);
  }

  @DeleteMapping("/items/{id}")
  public void delete(@RequestHeader("X-Studio-Session") String session, @PathVariable UUID id)
      throws Exception {
    studio.delete(StudioService.owner(session), id);
  }
}
