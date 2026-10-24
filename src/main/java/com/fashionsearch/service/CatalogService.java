package com.fashionsearch.service;
import com.fashionsearch.dto.*;
import com.fashionsearch.model.Product;
import com.fashionsearch.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.*;
import java.time.*;
import java.util.*;
@Service
public class CatalogService {
 private final ProductRepository repository; private final boolean ebayDisplay;
 public CatalogService(ProductRepository repository,@Value("${closie.providers.ebay.display-enabled:false}") boolean ebayDisplay) {this.repository=repository;this.ebayDisplay=ebayDisplay;}
 public record Results(List<ProductView> products,long total,int page,boolean hasMore) {}
 public Results search(SearchContext c,int page) {
  if(page<0||page>1000)throw new IllegalArgumentException("Invalid page.");
  var result=repository.findAll(spec(c,true),PageRequest.of(page,24));
  return new Results(result.getContent().stream().map(ProductView::of).toList(),result.getTotalElements(),page,result.hasNext());
 }
 public Optional<ProductView> product(long id) {
  return repository.findOne(spec(new SearchContext("",null,null,null,null,null,null,null,null),false).and((r,q,b)->b.equal(r.get("id"),id))).map(ProductView::of);
 }
 public boolean matches(long id,SearchContext c) {
  return repository.exists(spec(c,false).and((r,q,b)->b.equal(r.get("id"),id)));
 }
 public List<ProductView> candidates(SearchContext c) {
  return repository.findAll(spec(c,false),PageRequest.of(0,1000,Sort.by("price").ascending().and(Sort.by("id")))).getContent().stream().map(ProductView::of).toList();
 }
 private Specification<Product> spec(SearchContext c,boolean keywords) {
  return (root,query,b)->{
   List<Predicate> predicates=new ArrayList<>();
   if(c.currency()!=null)predicates.add(b.equal(root.get("currency"),c.currency()));
   predicates.add(b.notEqual(root.get("availability"),"UNAVAILABLE"));
   // Conservative display policy: eBay requires explicit operator review and <=6h freshness.
   Predicate ebay=b.equal(root.get("source"),"ebay");
   Predicate ebayAllowed=ebayDisplay?b.and(ebay,b.greaterThanOrEqualTo(root.get("lastSeenAt"),Instant.now().minus(Duration.ofHours(6)))):b.disjunction();
   // Other providers require explicit reviewed source IDs in config; see provider policy.
   Predicate permittedOther=permittedSources.isEmpty()?b.disjunction():root.get("source").in(permittedSources);
   predicates.add(b.or(ebayAllowed,permittedOther));
   predicates.add(b.like(b.lower(root.get("imageUrl")),"https://%"));
   predicates.add(b.like(b.lower(root.get("productUrl")),"https://%"));
   if(c.minPrice()!=null)predicates.add(b.greaterThanOrEqualTo(root.get("price"),c.minPrice()));
   if(c.maxPrice()!=null)predicates.add(b.lessThanOrEqualTo(root.get("price"),c.maxPrice()));
   if(text(c.brand()))predicates.add(b.equal(b.lower(root.get("brand")),c.brand().toLowerCase(Locale.ROOT)));
   if(text(c.color()))predicates.add(b.equal(b.lower(root.get("color")),c.color().toLowerCase(Locale.ROOT)));
   if(text(c.category()))predicates.add(b.or(like(b,root,"category",c.category()),like(b,root,"title",c.category()),b.equal(root.get("outfitSlot"),c.category().toUpperCase(Locale.ROOT))));
   if(keywords&&text(c.keywords()))for(String token:c.keywords().split("\\s+")) {
    if(!token.isBlank())predicates.add(b.or(like(b,root,"title",token),like(b,root,"description",token),like(b,root,"category",token),like(b,root,"brand",token)));
   }
   if(keywords && query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
    Expression<Integer> relevance=b.literal(0);
    for(String preference:Arrays.asList(c.aesthetic(),c.occasion()))if(text(preference)) {
     Predicate match=b.or(like(b,root,"title",preference),like(b,root,"description",preference),like(b,root,"styleTags",preference),like(b,root,"occasionTags",preference));
     relevance=b.sum(relevance,b.<Integer>selectCase().when(match,10).otherwise(0));
    }
    List<Order> ordering=new ArrayList<>();
    if(text(c.aesthetic())||text(c.occasion()))ordering.add(b.desc(relevance));
    ordering.add(b.desc(root.get("lastSeenAt"))); ordering.add(b.asc(root.get("id")));
    query.orderBy(ordering);
   }
   return b.and(predicates.toArray(Predicate[]::new));
  };
 }
 private List<String> permittedSources=List.of();
 @Value("${closie.providers.permitted-sources:}")
 public void setPermittedSources(String sources) { permittedSources=Arrays.stream(sources.split(",")).map(String::trim).filter(s->!s.isEmpty()&&!s.equals("ebay")).toList(); }
 private boolean text(String s){return s!=null&&!s.isBlank();}
 private Predicate like(CriteriaBuilder b,Root<Product> r,String field,String term) {
  String escaped=term.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_");
  return b.like(b.lower(r.get(field)),"%"+escaped+"%",'!');
 }
}
