import { useState } from "react";
import { money, safeUrl, safeImage } from "../api";
import Icon from "./Icon";
export default function ProductCard({
  product: p,
  saved,
  onSave,
  onStyle,
  onInspect,
  index = 0,
}) {
  const [broken, setBroken] = useState(false);
  const image = safeImage(p),
    url = p.demo ? null : safeUrl(p.productUrl);
  const picture =
    image && !broken ? (
      <img
        loading="lazy"
        src={image}
        alt={p.title}
        onError={() => setBroken(true)}
      />
    ) : (
      <div className="image-unavailable">Image unavailable</div>
    );
  return (
    <article className={`product product-${index % 4}`}>
      <div className="product-image">
        {p.demo ? (
          <button
            className="image-open"
            aria-label={`Details for ${p.title}`}
            onClick={() => onInspect(p)}
          >
            {picture}
          </button>
        ) : (
          <a href={url || undefined} target="_blank" rel="noopener noreferrer">
            {picture}
          </a>
        )}
        {p.demo && <span className="sample-pill">Sample design</span>}
        <button
          className={`save ${saved ? "is-saved" : ""}`}
          aria-label={`${saved ? "Unsave" : "Save"} ${p.title}`}
          aria-pressed={saved}
          onClick={() => onSave(p.id)}
        >
          <Icon name="heart" />
        </button>
        <button className="style-this" onClick={() => onStyle(p)}>
          <Icon name="spark" /> Style this
        </button>
      </div>
      <div className="product-meta">
        <span>{p.brand || "Brand not provided"}</span>
        <strong>{money(p.price, p.currency)}</strong>
      </div>
      <h3>{p.title}</h3>
      <div className="retailer-row">
        {p.demo ? (
          <button className="sample-detail-link" onClick={() => onInspect(p)}>
            Explore sample ↗
          </button>
        ) : url ? (
          <a href={url} target="_blank" rel="noopener noreferrer">
            View at {p.retailer || p.source} ↗
          </a>
        ) : (
          <span>Link unavailable</span>
        )}
        <span>
          {p.demo
            ? "Not for sale"
            : p.availability === "AVAILABLE"
              ? "Provider-reported stock"
              : "Check availability"}
        </span>
      </div>
    </article>
  );
}
