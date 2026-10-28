import React, { useEffect, useRef, useState } from "react";
import { api, money, readLocal, writeLocal } from "./api";
import Icon from "./components/Icon";
import ProductCard from "./components/ProductCard";
import ProductDetail from "./components/ProductDetail";
import StylePanel from "./components/StylePanel";
import "./style.css";
const categories = [
  ["All finds", ""],
  ["Tops & knits", "TOP"],
  ["Bottoms", "BOTTOM"],
  ["Dresses", "ONE_PIECE"],
  ["Shoes", "SHOES"],
  ["Bags", "BAG"],
  ["Jewelry & extras", "ACCESSORY"],
  ["Outerwear", "OUTERWEAR"],
];
const initialFilters = {
  brand: "",
  color: "",
  category: "",
  minPrice: "",
  maxPrice: "",
  currency: "USD",
};
const sorts = [
  ["relevance", "For you"],
  ["price_asc", "Price: low to high"],
  ["price_desc", "Price: high to low"],
  ["recent", "Recently added"],
];
export default function RetailApp() {
  const [status, setStatus] = useState(null),
    [tab, setTab] = useState("Discover"),
    [products, setProducts] = useState([]),
    [total, setTotal] = useState(0),
    [page, setPage] = useState(0),
    [hasMore, setHasMore] = useState(false),
    [loading, setLoading] = useState(false),
    [error, setError] = useState("");
  const [saved, setSaved] = useState([]),
    [unavailable, setUnavailable] = useState([]),
    [category, setCategory] = useState(""),
    [sort, setSort] = useState("relevance"),
    [filters, setFilters] = useState(initialFilters),
    [anchor, setAnchor] = useState(null),
    [detail, setDetail] = useState(null);
  const [prefs, setPrefs] = useState(() => ({
      ...{ aesthetic: "", budget: "", mood: "" },
      ...readLocal("closie.preferences", {}),
    })),
    [showPrefs, setShowPrefs] = useState(false),
    [message, setMessage] = useState(""),
    [history, setHistory] = useState([]),
    [context, setContext] = useState(null),
    [mode, setMode] = useState("rules");
  const [currencyExplicit, setCurrencyExplicit] = useState(false);
  const request = useRef(null),
    serial = useRef(0);
  useEffect(() => {
    const controller = new AbortController();
    api("/status", undefined, controller.signal)
      .then((value) => {
        setStatus(value);
        const ids = readLocal(
          `closie.saved.${value.catalogMode}`,
          value.catalogMode === "live" ? readLocal("closie.saved", []) : [],
        );
        setSaved(
          Array.isArray(ids)
            ? ids.filter(Number.isSafeInteger).slice(0, 200)
            : [],
        );
      })
      .catch((e) => {
        if (e.name !== "AbortError") setError(e.message);
      });
    return () => controller.abort();
  }, []);
  useEffect(() => {
    if (status) writeLocal(`closie.saved.${status.catalogMode}`, saved);
  }, [saved, status]);
  useEffect(() => writeLocal("closie.preferences", prefs), [prefs]);
  const begin = () => {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    const id = ++serial.current;
    setLoading(true);
    setError("");
    return { controller, id };
  };
  async function browse(nextPage = 0) {
    const { controller, id } = begin();
    try {
      let result;
      if (tab === "Saved") {
        const data = await api(
          "/products/saved",
          { ids: saved },
          controller.signal,
        );
        setUnavailable(data.unavailableIds);
        result = {
          products: data.products,
          total: data.products.length,
          hasMore: false,
        };
      } else {
        const params = new URLSearchParams({
          category,
          currency: filters.currency,
          page: nextPage,
          sort,
        });
        if (prefs.budget) params.set("maxPrice", prefs.budget);
        if (prefs.aesthetic) params.set("aesthetic", prefs.aesthetic);
        result = (
          await api(`/discover?${params}`, undefined, controller.signal)
        ).results;
      }
      if (serial.current !== id) return;
      setProducts(
        nextPage ? (old) => [...old, ...result.products] : result.products,
      );
      setTotal(result.total);
      setHasMore(result.hasMore);
      setPage(nextPage);
    } catch (e) {
      if (e.name !== "AbortError" && serial.current === id) setError(e.message);
    } finally {
      if (serial.current === id) setLoading(false);
    }
  }
  useEffect(() => {
    if (status && (tab === "Discover" || tab === "Saved")) browse();
    else {
      request.current?.abort();
      serial.current++;
      setLoading(false);
      setError("");
    }
    return () => request.current?.abort();
  }, [
    status,
    tab,
    category,
    sort,
    prefs,
    filters.currency,
    tab === "Saved" ? saved : null,
  ]);
  const save = (id) =>
    setSaved((old) =>
      old.includes(id)
        ? old.filter((x) => x !== id)
        : old.length < 200
          ? [...old, id]
          : old,
    );
  function navigate(next) {
    request.current?.abort();
    serial.current++;
    setLoading(false);
    setError("");
    setTab(next);
    setProducts([]);
    setHasMore(false);
    setTotal(0);
    if (next === "Find") {
      setContext(null);
      setHistory([]);
    }
  }
  const styleThis = (product) => {
    setAnchor(product);
    navigate("Style");
  };
  function filterChange(key, value) {
    if (key === "currency") setCurrencyExplicit(true);
    setFilters((old) => ({ ...old, [key]: value }));
    setContext((old) =>
      old
        ? {
            ...old,
            [key]: key.endsWith("Price")
              ? value === ""
                ? null
                : Number(value)
              : value,
          }
        : old,
    );
  }
  async function find(event, nextPage = 0) {
    event?.preventDefault();
    if (!message.trim() && !context) return;
    const { controller, id } = begin();
    const text = message;
    const explicit = Object.fromEntries(
      Object.entries(filters)
        .filter(
          ([key, v]) => v !== "" && (key !== "currency" || currencyExplicit),
        )
        .map(([k, v]) => [k, k.endsWith("Price") ? Number(v) : v]),
    );
    if (!context && prefs.aesthetic) explicit.aesthetic = prefs.aesthetic;
    try {
      const data = await api(
        "/search",
        {
          message: nextPage ? "" : text,
          context,
          filters: explicit,
          page: nextPage,
          sort,
        },
        controller.signal,
      );
      if (serial.current !== id) return;
      setFilters((old) => ({ ...old, currency: data.context.currency }));
      setContext(data.context);
      setMode(data.interpretationMode);
      setProducts(
        nextPage
          ? (old) => [...old, ...data.results.products]
          : data.results.products,
      );
      setTotal(data.results.total);
      setHasMore(data.results.hasMore);
      setPage(nextPage);
      if (!nextPage) {
        if (text) setHistory((old) => [...old, text]);
        setMessage("");
      }
    } catch (e) {
      if (e.name !== "AbortError" && serial.current === id) setError(e.message);
    } finally {
      if (serial.current === id) setLoading(false);
    }
  }
  const card = (product, index) => (
    <ProductCard
      key={product.id}
      product={product}
      index={index}
      saved={saved.includes(product.id)}
      onSave={save}
      onStyle={styleThis}
      onInspect={setDetail}
    />
  );
  return (
    <>
      <header className="site-header">
        <a
          className="wordmark"
          href="#"
          onClick={(e) => {
            e.preventDefault();
            navigate("Discover");
          }}
        >
          closie<span>✳</span>
        </a>
        <nav aria-label="Main navigation">
          {["Discover", "Find", "Style", "Saved"].map((item) => (
            <button
              key={item}
              aria-current={tab === item ? "page" : undefined}
              onClick={() => navigate(item)}
            >
              {item}
            </button>
          ))}
        </nav>
        <button className="personal" onClick={() => setShowPrefs(!showPrefs)}>
          My style <span className="avatar">C</span>
        </button>
      </header>
      {status && (
        <aside
          className={`catalog-banner ${status.catalogMode === "demo" ? "demo-banner" : ""}`}
          role="note"
        >
          <span>
            {status.catalogMode === "demo"
              ? "SAMPLE COLLECTION"
              : "LIVE CATALOG"}
          </span>
          {status.notice}
          <span>
            {status.aiConfigured
              ? "AI enabled when available"
              : "Rules-based stylist available"}
          </span>
        </aside>
      )}
      <main>
        {showPrefs && (
          <section className="preferences" aria-label="My style preferences">
            <div>
              <span className="eyebrow">MAKE IT YOURS</span>
              <h2>A little more you.</h2>
              <p>Preferences and favorites stay in this browser.</p>
            </div>
            <label>
              Your aesthetic
              <select
                value={prefs.aesthetic}
                onChange={(e) =>
                  setPrefs({ ...prefs, aesthetic: e.target.value })
                }
              >
                <option value="">Open to anything</option>
                {[
                  "minimalist",
                  "feminine",
                  "casual",
                  "classic",
                  "romantic",
                  "streetwear",
                ].map((x) => (
                  <option key={x}>{x}</option>
                ))}
              </select>
            </label>
            <label>
              Your mood
              <select
                value={prefs.mood}
                onChange={(e) => setPrefs({ ...prefs, mood: e.target.value })}
              >
                <option value="">A little of everything</option>
                {["effortless", "playful", "relaxed", "polished", "sleek"].map(
                  (x) => (
                    <option key={x}>{x}</option>
                  ),
                )}
              </select>
            </label>
            <label>
              Typical budget
              <input
                type="number"
                min="0"
                step="0.01"
                value={prefs.budget}
                onChange={(e) => setPrefs({ ...prefs, budget: e.target.value })}
                placeholder="No limit"
              />
            </label>
            <button className="text-button" onClick={() => setShowPrefs(false)}>
              Done ↗
            </button>
          </section>
        )}
        {tab === "Discover" && (
          <>
            <section className="hero">
              <div>
                <span className="eyebrow">YOUR PERSONAL AI STYLIST</span>
                <h1>
                  Good finds.
                  <br />
                  <em>Very you.</em>
                </h1>
                <p>
                  A new favorite. A fresh combination.
                  <br />
                  Discover the pieces that feel like you.
                </p>
                <button className="primary" onClick={() => navigate("Style")}>
                  Find your next look <Icon name="spark" />
                </button>
                <button
                  className="hero-search-link"
                  onClick={() => navigate("Find")}
                >
                  Looking for a specific piece? ↗
                </button>
              </div>
              {status?.catalogMode === "demo" ? (
                <div
                  className="hero-collage"
                  aria-label="Closie sample outfit illustrations"
                >
                  <img
                    src="/demo/ivory-knit.svg"
                    alt="Sample ivory cardigan illustration"
                  />
                  <img
                    src="/demo/black-skirt.svg"
                    alt="Sample satin skirt illustration"
                  />
                  <img
                    src="/demo/black-flats.svg"
                    alt="Sample ballet flats illustration"
                  />
                  <span>
                    THE SOFT STRUCTURE EDIT
                    <br />
                    <small>Original sample illustrations</small>
                  </span>
                </div>
              ) : (
                <div className="hero-note">
                  <span className="orbit">c.</span>
                  <p>
                    A wardrobe with
                    <br />
                    <em>your point of view.</em>
                  </p>
                  <span className="note-small">YOUR BUDGET · YOUR STYLE</span>
                </div>
              )}
            </section>
            <section className="feed-header">
              <div>
                <span className="eyebrow">THE DISCOVERY EDIT</span>
                <h2>
                  Meet your next favorites<span className="dot">.</span>
                </h2>
              </div>
              <button
                className="saved-filter"
                onClick={() => navigate("Saved")}
              >
                <Icon name="heart" /> Saved ({saved.length})
              </button>
            </section>
            <div className="category-row" aria-label="Browse categories">
              {categories.map(([label, value]) => (
                <button
                  key={label}
                  className={category === value ? "selected" : ""}
                  onClick={() => setCategory(value)}
                >
                  {label}
                </button>
              ))}
            </div>
            <div className="feed-tools">
              <p className="catalog-note">
                {prefs.aesthetic ? `Your ${prefs.aesthetic} edit · ` : ""}
                {total}{" "}
                {status?.catalogMode === "demo"
                  ? "sample designs"
                  : "catalog finds"}
              </p>
              <label>
                Sort
                <select value={sort} onChange={(e) => setSort(e.target.value)}>
                  {sorts.map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          </>
        )}
        {tab === "Saved" && (
          <>
            <section className="page-intro">
              <span className="eyebrow">YOUR LITTLE COLLECTION</span>
              <h1>
                Worth <em>coming back to.</em>
              </h1>
              <p>
                Favorite pieces saved only in this browser. We check catalog
                eligibility each time you open them.
              </p>
            </section>
            {unavailable.length > 0 && (
              <div className="saved-notice">
                {unavailable.length} saved{" "}
                {unavailable.length === 1 ? "piece is" : "pieces are"} no longer
                available in this catalog.{" "}
                <button
                  className="text-button"
                  onClick={() =>
                    setSaved((old) =>
                      old.filter((id) => !unavailable.includes(id)),
                    )
                  }
                >
                  Remove unavailable saves
                </button>
              </div>
            )}
          </>
        )}
        {tab === "Find" && (
          <>
            <section className="page-intro">
              <span className="eyebrow">LESS SCROLLING, MORE FINDING</span>
              <h1>
                Tell us what <em>you’re after.</em>
              </h1>
              <p>
                A piece, a feeling, an occasion. Start with what’s on your mind.
              </p>
            </section>
            <section className="search-panel">
              <form onSubmit={find}>
                <label className="sr-only" htmlFor="query">
                  Describe what you want
                </label>
                <div className="query-box">
                  <Icon name="search" />
                  <input
                    id="query"
                    maxLength="1000"
                    value={message}
                    onChange={(e) => setMessage(e.target.value)}
                    placeholder={
                      context
                        ? "Refine your finds… e.g. Something more feminine"
                        : "Find me a black leather jacket under $100"
                    }
                    required={!context}
                  />
                  <button className="primary" disabled={loading}>
                    Find it <Icon name="arrow" />
                  </button>
                </div>
                <div className="filters">
                  {[
                    ["brand", "Brand"],
                    ["color", "Color"],
                  ].map(([key, label]) => (
                    <label key={key}>
                      {label}
                      <input
                        value={filters[key]}
                        onChange={(e) => filterChange(key, e.target.value)}
                        placeholder={`Any ${label.toLowerCase()}`}
                        maxLength="100"
                      />
                    </label>
                  ))}
                  <label>
                    Category
                    <select
                      value={filters.category}
                      onChange={(e) => filterChange("category", e.target.value)}
                    >
                      {categories.map(([label, value]) => (
                        <option key={label} value={value}>
                          {label}
                        </option>
                      ))}
                    </select>
                  </label>
                  {[
                    ["minPrice", "Min price"],
                    ["maxPrice", "Max price"],
                  ].map(([key, label]) => (
                    <label key={key}>
                      {label}
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        value={filters[key]}
                        onChange={(e) => filterChange(key, e.target.value)}
                        placeholder="Any"
                      />
                    </label>
                  ))}
                  <label>
                    Currency
                    <select
                      value={filters.currency}
                      onChange={(e) => filterChange("currency", e.target.value)}
                    >
                      {["USD", "EUR", "GBP"].map((value) => (
                        <option key={value}>{value}</option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Sort
                    <select
                      value={sort}
                      onChange={(e) => setSort(e.target.value)}
                    >
                      {sorts.map(([value, label]) => (
                        <option key={value} value={value}>
                          {label}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
              </form>
              {history.length > 0 && (
                <div className="conversation">
                  <span className="eyebrow">YOUR SEARCH, REFINED</span>
                  {history.map((text, index) => (
                    <span className="conversation-chip" key={index}>
                      {text}
                    </span>
                  ))}
                  <button
                    className="text-button"
                    onClick={() => {
                      navigate("Find");
                      setFilters(initialFilters);
                      setCurrencyExplicit(false);
                      setMessage("");
                    }}
                  >
                    Start fresh
                  </button>
                </div>
              )}
              {context && (
                <p className="context-line">
                  {[
                    context.category,
                    context.brand,
                    context.color,
                    context.aesthetic,
                    context.occasion,
                    context.maxPrice != null
                      ? `Up to ${money(context.maxPrice, context.currency)}`
                      : null,
                  ]
                    .filter(Boolean)
                    .join(" · ") || "All catalog finds"}{" "}
                  ·{" "}
                  {mode === "rules"
                    ? "Rules-based interpretation"
                    : "AI-assisted interpretation"}
                </p>
              )}
            </section>
            {context && (
              <div className="results-title">
                <h2>Your finds</h2>
                <span>{total} matching pieces</span>
              </div>
            )}
          </>
        )}
        {tab === "Style" && (
          <StylePanel
            anchor={anchor}
            onClearAnchor={() => setAnchor(null)}
            prefs={prefs}
            renderProduct={card}
            currency={filters.currency}
            demo={status?.catalogMode === "demo"}
          />
        )}
        {error && (
          <div className="error" role="alert">
            {error}
            <button
              className="text-button"
              onClick={() => {
                if (tab === "Discover" || tab === "Saved") browse();
                else setError("");
              }}
            >
              Try again
            </button>
          </div>
        )}
        {tab !== "Style" && (
          <>
            {loading && (
              <p role="status" className="status">
                Finding your next favorites…
              </p>
            )}
            {!loading &&
              !error &&
              products.length === 0 &&
              (tab !== "Find" || context) && (
                <section className="empty">
                  <Icon
                    name={tab === "Saved" ? "heart" : "search"}
                    width="32"
                    height="32"
                  />
                  <h2>
                    {tab === "Saved"
                      ? "Your favorites start with a heart."
                      : "Nothing here just yet."}
                  </h2>
                  <p>
                    {tab === "Saved"
                      ? "Tap the heart on a piece you love, then find it here."
                      : "Try a broader category, another color, or a different budget."}
                  </p>
                </section>
              )}
            <div className="product-grid">{products.map(card)}</div>
            {hasMore && (
              <div className="load-more">
                <button
                  disabled={loading}
                  onClick={() =>
                    tab === "Find" ? find(null, page + 1) : browse(page + 1)
                  }
                >
                  Discover more ↓
                </button>
              </div>
            )}
          </>
        )}
      </main>
      <footer>
        <span className="wordmark">
          closie<span>✳</span>
        </span>
        <p>Your style. Your pace. Your point of view.</p>
        <span>YOUR PERSONAL AI STYLIST</span>
      </footer>
      {detail && (
        <ProductDetail
          product={detail}
          onClose={() => setDetail(null)}
          onStyle={styleThis}
        />
      )}
    </>
  );
}
