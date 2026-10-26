import { useEffect, useRef, useState } from "react";
import { api, money } from "../api";
import Icon from "./Icon";
export default function StylePanel({
  anchor,
  onClearAnchor,
  prefs,
  renderProduct,
  currency = "USD",
  demo,
}) {
  const [message, setMessage] = useState(""),
    [budget, setBudget] = useState(prefs.budget || ""),
    [unit, setUnit] = useState(anchor?.currency || currency),
    [context, setContext] = useState(null),
    [turns, setTurns] = useState([]),
    [outfit, setOutfit] = useState(null),
    [loading, setLoading] = useState(false),
    [error, setError] = useState(""),
    [extras, setExtras] = useState(true);
  const [currencyExplicit, setCurrencyExplicit] = useState(false);
  const request = useRef(null);
  useEffect(() => () => request.current?.abort(), []);
  useEffect(() => {
    request.current?.abort();
    setContext(null);
    setTurns([]);
    setOutfit(null);
    setError("");
    setLoading(false);
    setUnit(anchor?.currency || currency);
  }, [anchor?.id]);
  async function submit(event, override) {
    event?.preventDefault();
    const text = override ?? message;
    if (!text.trim() && !anchor && !context) {
      setError("Tell us a little about the look you want.");
      return;
    }
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    setLoading(true);
    setError("");
    const filters = {};
    if (anchor || currencyExplicit) filters.currency = anchor?.currency || unit;
    if (budget !== "") filters.maxPrice = Number(budget);
    if (!context && prefs.aesthetic) filters.aesthetic = prefs.aesthetic;
    if (!context && prefs.mood) filters.mood = prefs.mood;
    try {
      const data = await api(
        "/style",
        {
          message: text,
          context,
          filters,
          anchorId: anchor?.id ?? null,
          includeExtras: extras,
        },
        controller.signal,
      );
      if (controller.signal.aborted) return;
      setUnit(data.currency);
      setOutfit(data);
      setContext(data.context);
      setTurns((old) => [...old, text]);
      setMessage("");
    } catch (e) {
      if (e.name !== "AbortError") setError(e.message);
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }
  function fresh() {
    request.current?.abort();
    setLoading(false);
    setContext(null);
    setTurns([]);
    setOutfit(null);
    setMessage("");
    setError("");
    setBudget(prefs.budget || "");
  }
  return (
    <>
      <section className="page-intro">
        <span className="eyebrow">YOUR PERSONAL STYLING SESSION</span>
        <h1>
          Make it <em>an outfit.</em>
        </h1>
        <p>A mood, a plan, a piece you love. Let’s bring it together.</p>
      </section>
      <section className="stylist-layout">
        <form className="stylist-form" onSubmit={submit}>
          <span className="eyebrow">
            {context ? "LET’S REFINE IT" : "YOUR STYLING BRIEF"}
          </span>
          <h2>{context ? "What would you change?" : "Where are we going?"}</h2>
          <label>
            Occasion, aesthetic & preferences
            <textarea
              maxLength="1000"
              rows="4"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              placeholder={
                context
                  ? "More playful, suitable for dinner, or cheaper…"
                  : "An effortless feminine look for a coffee date, with an Aritzia vibe. My budget is $150."
              }
            />
          </label>
          {!context && (
            <div className="prompt-examples">
              {[
                "Feminine coffee date under $150",
                "Minimalist college outfit under $120",
                "A dinner date dress under $140",
              ].map((text) => (
                <button
                  type="button"
                  key={text}
                  aria-label={text}
                  onClick={() => setMessage(text)}
                >
                  {text} ↗
                </button>
              ))}
            </div>
          )}
          <div className="budget-row">
            <label>
              Total item budget ({anchor?.currency || unit})
              <input
                type="number"
                min="0"
                step="0.01"
                value={budget}
                onChange={(e) => {
                  setBudget(e.target.value);
                  if (e.target.value === "" && context)
                    setContext({ ...context, maxPrice: null });
                }}
                placeholder="Use budget in your request"
              />
            </label>
            <label>
              Currency
              <select
                disabled={!!anchor}
                value={anchor?.currency || unit}
                onChange={(e) => {
                  setCurrencyExplicit(true);
                  setUnit(e.target.value);
                  setContext((old) =>
                    old ? { ...old, currency: e.target.value } : old,
                  );
                }}
              >
                {["USD", "EUR", "GBP"].map((c) => (
                  <option key={c}>{c}</option>
                ))}
              </select>
            </label>
          </div>
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={extras}
              onChange={(e) => setExtras(e.target.checked)}
            />{" "}
            Add a bag or finishing accessory if the budget allows
          </label>
          {anchor && (
            <div className="anchor">
              <p>
                Starting with <strong>{anchor.title}</strong> ·{" "}
                {money(anchor.price, anchor.currency)}
              </p>
              <button
                type="button"
                className="text-button"
                onClick={onClearAnchor}
              >
                Remove anchor
              </button>
            </div>
          )}
          <button className="primary" disabled={loading}>
            {loading
              ? "Styling your look…"
              : context
                ? "Refine my outfit"
                : "Build my outfit"}{" "}
            <Icon name="spark" />
          </button>
          {context && (
            <button type="button" className="text-button" onClick={fresh}>
              Start a new styling session
            </button>
          )}
          <p className="fine-print">
            {demo
              ? "Sample designs and prices, not for sale."
              : "Check size and stock with each retailer."}{" "}
            Budget covers item prices, excluding shipping and tax.
          </p>
        </form>
        <div className="stylist-output">
          {error && (
            <div className="error" role="alert">
              {error}
            </div>
          )}
          {loading && (
            <p className="status" role="status">
              Bringing the pieces together…
            </p>
          )}
          {outfit ? (
            <>
              <div className="outfit-title">
                <span className="eyebrow">
                  {outfit.complete
                    ? "YOUR LOOK, TOGETHER"
                    : "A START, NOT A COMPLETE LOOK"}
                </span>
                <h2>
                  {outfit.complete
                    ? "Consider it styled."
                    : "A few pieces are missing."}
                </h2>
                <strong className="outfit-total">
                  {money(outfit.total, outfit.currency)}
                </strong>
                <p>{outfit.explanation}</p>
                <span className="mode-label">
                  {outfit.stylingMode === "ai-assisted"
                    ? "AI-curated outfit & explanation"
                    : outfit.interpretationMode === "ai-assisted"
                      ? "AI-understood preferences · rules-based outfit"
                      : "Rules-based stylist"}
                </span>
                <p className="context-line">
                  {[
                    outfit.context.aesthetic,
                    outfit.context.mood,
                    outfit.context.vibe ? `${outfit.context.vibe} vibe` : null,
                    outfit.context.occasion,
                    outfit.context.maxPrice != null
                      ? `Up to ${money(outfit.context.maxPrice, outfit.currency)}`
                      : null,
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
              </div>
              <div className="outfit-grid">
                {outfit.products.map(renderProduct)}
              </div>
              <div
                className="styling-history"
                aria-label="Styling conversation"
              >
                {turns.filter(Boolean).map((turn, i) => (
                  <span className="conversation-chip" key={i}>
                    {turn}
                  </span>
                ))}
              </div>
              <div className="refine-chips">
                {[
                  "Something more feminine",
                  "Actually, make it suitable for a dinner date",
                  "Cheaper",
                ].map((text) => (
                  <button
                    key={text}
                    aria-label={text}
                    disabled={loading}
                    onClick={() => submit(null, text)}
                  >
                    {text} ↗
                  </button>
                ))}
              </div>
            </>
          ) : (
            <div className="stylist-empty">
              <Icon name="spark" width="48" height="48" />
              <h2>
                Your next look
                <br />
                <em>starts here.</em>
              </h2>
              <p>
                Tell us your plans. We’ll find a coordinated base and thoughtful
                finishing pieces.
              </p>
            </div>
          )}
        </div>
      </section>
    </>
  );
}
