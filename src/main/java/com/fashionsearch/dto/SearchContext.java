package com.fashionsearch.dto;
import java.math.BigDecimal;
/** Only user-owned preferences cross the optional LLM boundary. No catalog content. */
public record SearchContext(String keywords, String category, String brand, String color,
 BigDecimal minPrice, BigDecimal maxPrice, String currency, String aesthetic, String occasion) {
 public static SearchContext empty() { return new SearchContext("",null,null,null,null,null,"USD",null,null); }
}
