import { useEffect, useRef } from "react";
import { money, safeImage } from "../api";
export default function ProductDetail({ product: p, onClose, onStyle }) {
  const ref = useRef();
  useEffect(() => {
    const dialog = ref.current;
    dialog.showModal();
    return () => dialog.close();
  }, []);
  return (
    <dialog
      ref={ref}
      className="product-dialog"
      onCancel={onClose}
      aria-labelledby="detail-title"
    >
      <button
        className="dialog-close"
        onClick={onClose}
        aria-label="Close product details"
      >
        ×
      </button>
      <img src={safeImage(p)} alt={p.title} />
      <div>
        <span className="eyebrow">SAMPLE COLLECTION · NOT FOR SALE</span>
        <h2 id="detail-title">{p.title}</h2>
        <p>
          {p.brand} · {money(p.price, p.currency)} sample price
        </p>
        <p>{p.description}</p>
        <dl>
          <dt>Palette</dt>
          <dd>{p.color || "Unspecified"}</dd>
          <dt>Style notes</dt>
          <dd>{p.styleTags || "Open to interpretation"}</dd>
          <dt>Occasions</dt>
          <dd>{p.occasionTags || "Everyday"}</dd>
        </dl>
        <p className="fine-print">
          An original Closie illustration and fictional product record. This is
          not retailer inventory, an offer to sell, or a verified price.
        </p>
        <button
          className="primary"
          onClick={() => {
            onStyle(p);
            onClose();
          }}
        >
          Style this piece ↗
        </button>
      </div>
    </dialog>
  );
}
