package com.fashionsearch.dto;
public record SearchRequest(String message, SearchContext context, SearchContext filters, Integer page) {}
