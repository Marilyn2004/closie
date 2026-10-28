package com.fashionsearch.studio;

import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
@ConditionalOnProperty(name = "closie.studio.enabled", havingValue = "true")
public class StudioStore {
  final JdbcTemplate db;
  final JsonMapper json = JsonMapper.builder().build();

  public JdbcTemplate jdbc() {
    return db;
  }

  public JsonMapper mapper() {
    return json;
  }

  public StudioStore(JdbcTemplate db) {
    this.db = db;
  }

  public record Revision(
      UUID id, UUID documentId, UUID parentId, JsonNode data, String createdAt) {}

  public record Document(UUID id, String kind, JsonNode data, List<Revision> revisions) {}

  String encode(Object value) {
    return json.writeValueAsString(value);
  }

  JsonNode decode(String value) {
    return json.readTree(value);
  }

  <T> T convert(JsonNode value, Class<T> type) {
    return json.treeToValue(value, type);
  }

  public UUID create(String owner, String kind, Object payload) {
    UUID id = UUID.randomUUID();
    db.update(
        "INSERT INTO studio_documents(id,owner,kind,payload) VALUES(?,?,?,?::jsonb)",
        id,
        owner,
        kind,
        encode(payload));
    return id;
  }

  public Revision append(UUID id, UUID parent, Object payload) {
    UUID rid = UUID.randomUUID();
    db.update(
        "INSERT INTO studio_revisions(id,document_id,parent_id,payload) VALUES(?,?,?,?::jsonb)",
        rid,
        id,
        parent,
        encode(payload));
    return revision(rid);
  }

  public Revision revision(UUID id) {
    return db
        .query(
            "SELECT * FROM studio_revisions WHERE id=?",
            (r, n) ->
                new Revision(
                    r.getObject("id", UUID.class),
                    r.getObject("document_id", UUID.class),
                    r.getObject("parent_id", UUID.class),
                    decode(r.getString("payload")),
                    r.getString("created_at")),
            id)
        .stream()
        .findFirst()
        .orElseThrow(StudioStore::missing);
  }

  public Document get(String owner, UUID id) {
    var docs =
        db.query(
            "SELECT kind,payload FROM studio_documents WHERE id=? AND (owner=? OR owner='CURATED')",
            (r, n) -> new Document(id, r.getString(1), decode(r.getString(2)), revisions(id)),
            id,
            owner);
    return docs.stream().findFirst().orElseThrow(StudioStore::missing);
  }

  public void writable(String owner, UUID id) {
    if (Boolean.FALSE.equals(
        db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM studio_documents WHERE id=? AND owner=?)",
            Boolean.class,
            id,
            owner))) throw missing();
  }

  public List<Revision> revisions(UUID id) {
    return db.query(
        "SELECT * FROM studio_revisions WHERE document_id=? ORDER BY revision_order",
        (r, n) ->
            new Revision(
                r.getObject("id", UUID.class),
                id,
                r.getObject("parent_id", UUID.class),
                decode(r.getString("payload")),
                r.getString("created_at")),
        id);
  }

  public List<Document> list(String owner, String kind) {
    return db
        .query(
            "SELECT id FROM studio_documents WHERE kind=? AND (owner=? OR owner='CURATED') ORDER BY"
                + " created_at,id LIMIT 300",
            (r, n) -> r.getObject(1, UUID.class),
            kind,
            owner)
        .stream()
        .map(id -> get(owner, id))
        .toList();
  }

  static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Item unavailable.");
  }
}
