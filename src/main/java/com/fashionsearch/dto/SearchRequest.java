package com.fashionsearch.dto;

public record SearchRequest(
    String message, SearchContext context, SearchContext filters, Integer page, String sort) {
  public SearchRequest(String message, SearchContext context, SearchContext filters, Integer page) {
    this(message, context, filters, page, "relevance");
  }
}
