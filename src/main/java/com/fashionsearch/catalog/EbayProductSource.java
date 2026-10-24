package com.fashionsearch.catalog;
import com.fashionsearch.client.EbayClient;
import org.springframework.stereotype.Component;
@Component
public class EbayProductSource implements ProductSource {
 private final EbayClient client;
 public EbayProductSource(EbayClient client){this.client=client;}
 public String source(){return "ebay";}
 public Page fetch(String keyword,int offset){
  var page=client.search(keyword,offset);
  return new Page(page.products().stream().map(p->new SourceProduct(p.itemId(),p.title(),p.shortDescription(),p.categoryName(),
   p.price(),p.currency(),p.imageUrl(),p.itemWebUrl(),null,"eBay",null,null,null,"UNKNOWN",null,null)).toList(),page.hasNext());
 }
}
