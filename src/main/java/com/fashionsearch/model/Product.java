package com.fashionsearch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.Length;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "products",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_products_source_external_id",
            columnNames = {"source", "external_id"}))
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "external_id", nullable = false, length = 255)
  private String externalId;

  @Column(nullable = false, length = 500)
  private String title;

  @Column(length = Length.LONG32)
  private String description;

  @Column(length = 255)
  private String category;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "image_url", length = Length.LONG32)
  private String imageUrl;

  @Column(name = "product_url", length = Length.LONG32)
  private String productUrl;

  @Column(nullable = false, length = 50)
  private String source;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "brand", length = 255)
  private String brand;

  @Column(name = "retailer", length = 255)
  private String retailer;

  @Column(name = "outfit_slot", length = 30)
  private String outfitSlot;

  @Column(name = "color", length = 80)
  private String color;

  @Column(name = "available_sizes", length = Length.LONG32)
  private String availableSizes;

  @Column(name = "availability", length = 30)
  private String availability = "UNKNOWN";

  @Column(name = "style_tags", length = Length.LONG32)
  private String styleTags;

  @Column(name = "occasion_tags", length = Length.LONG32)
  private String occasionTags;

  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  public Long getId() {
    return id;
  }

  public String getExternalId() {
    return externalId;
  }

  public void setExternalId(String externalId) {
    this.externalId = externalId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public String getProductUrl() {
    return productUrl;
  }

  public void setProductUrl(String productUrl) {
    this.productUrl = productUrl;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public String getBrand() {
    return brand;
  }

  public void setBrand(String value) {
    this.brand = value;
  }

  public String getRetailer() {
    return retailer;
  }

  public void setRetailer(String value) {
    this.retailer = value;
  }

  public String getOutfitSlot() {
    return outfitSlot;
  }

  public void setOutfitSlot(String value) {
    this.outfitSlot = value;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String value) {
    this.color = value;
  }

  public String getAvailableSizes() {
    return availableSizes;
  }

  public void setAvailableSizes(String value) {
    this.availableSizes = value;
  }

  public String getAvailability() {
    return availability;
  }

  public void setAvailability(String value) {
    this.availability = value;
  }

  public String getStyleTags() {
    return styleTags;
  }

  public void setStyleTags(String value) {
    this.styleTags = value;
  }

  public String getOccasionTags() {
    return occasionTags;
  }

  public void setOccasionTags(String value) {
    this.occasionTags = value;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(Instant value) {
    this.lastSeenAt = value;
  }
}
