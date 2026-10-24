package com.fashionsearch.catalog;
import com.fashionsearch.model.Product;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
class CatalogNormalizerTest {
 @Test void classifiesOutfitSlots(){assertThat(CatalogNormalizer.slot("Wool cardigan","Sweaters")).isEqualTo("TOP");assertThat(CatalogNormalizer.slot("White sneakers","Shoes")).isEqualTo("SHOES");assertThat(CatalogNormalizer.slot("Midi dress",null)).isEqualTo("ONE_PIECE");assertThat(CatalogNormalizer.slot("Silver necklace",null)).isEqualTo("ACCESSORY");}
 @Test void preservesUnknownProviderFacts(){Product p=new Product();new CatalogNormalizer().enrich(p,new SourceProduct("id","Black shirt",null,null,BigDecimal.TEN,"USD",null,null,null,null,null,null,null,null,null,null));assertThat(p.getBrand()).isNull();assertThat(p.getColor()).isNull();assertThat(p.getAvailability()).isEqualTo("UNKNOWN");assertThat(p.getAvailableSizes()).isNull();}
}
