package com.fashionsearch.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ebay")
public class EbayProperties {

  private String apiBaseUrl = "https://api.ebay.com";

  private String clientId = "";

  private String clientSecret = "";

  private String marketplaceId = "EBAY_US";

  private List<String> searchKeywords =
      new ArrayList<>(List.of("leather jacket", "cardigan", "dress", "sneakers"));

  private int requestIntervalMillis = 200;
  private boolean enrichDetails = false;
  private int detailsPerPage = 5;

  public int getRequestIntervalMillis() {
    return requestIntervalMillis;
  }

  public void setRequestIntervalMillis(int value) {
    requestIntervalMillis = Math.max(0, Math.min(5000, value));
  }

  public boolean isEnrichDetails() {
    return enrichDetails;
  }

  public void setEnrichDetails(boolean value) {
    enrichDetails = value;
  }

  public int getDetailsPerPage() {
    return detailsPerPage;
  }

  public void setDetailsPerPage(int value) {
    detailsPerPage = Math.max(0, Math.min(20, value));
  }

  private int pageSize = 50;

  private int maxPages = 2;

  public String getApiBaseUrl() {
    return apiBaseUrl;
  }

  public void setApiBaseUrl(String apiBaseUrl) {
    this.apiBaseUrl = apiBaseUrl;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public String getMarketplaceId() {
    return marketplaceId;
  }

  public void setMarketplaceId(String marketplaceId) {
    this.marketplaceId = marketplaceId;
  }

  public List<String> getSearchKeywords() {
    return searchKeywords;
  }

  public void setSearchKeywords(List<String> searchKeywords) {
    this.searchKeywords = searchKeywords;
  }

  public int getPageSize() {
    return pageSize;
  }

  public void setPageSize(int pageSize) {
    this.pageSize = pageSize;
  }

  public int getMaxPages() {
    return maxPages;
  }

  public void setMaxPages(int maxPages) {
    this.maxPages = maxPages;
  }
}
