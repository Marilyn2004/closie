import React, { useState, useEffect } from "react";
import { studioApi as api, privateImage, last, slots } from "./api";
import "./studio.css";
const emptyPrefs = {
  aesthetics: [],
  colors: [],
  saved: [],
  hidden: [],
  lifestyle: "",
  budget: "",
};
function Picture({ src, alt, ...props }) {
  const [url, setUrl] = useState("");
  useEffect(() => {
    let active = true,
      blob = "";
    setUrl("");
    if (src?.startsWith("/api/studio/assets/"))
      privateImage(src)
        .then((v) => {
          blob = v;
          if (active) setUrl(v);
          else URL.revokeObjectURL(v);
        })
        .catch(() => {});
    else if (/^\/concepts\/[a-z-]+\.(png|jpg)$/.test(src || "")) setUrl(src);
    return () => {
      active = false;
      if (blob) URL.revokeObjectURL(blob);
    };
  }, [src]);
  return url ? (
    <img src={url} alt={alt} {...props} />
  ) : (
    <div className="image-pending" role="img" aria-label={alt}>
      ✧<span>Design brief</span>
      <small>Image not generated</small>
    </div>
  );
}
export default function StudioApp() {
  const [tab, setTab] = useState("Discover"),
    [status, setStatus] = useState(null),
    [prefs, setPrefs] = useState(emptyPrefs),
    [all, setAll] = useState([]),
    [owned, setOwned] = useState([]),
    [outfits, setOutfits] = useState([]);
  const [category, setCategory] = useState(""),
    [query, setQuery] = useState(""),
    [results, setResults] = useState([]),
    [history, setHistory] = useState([]),
    [selected, setSelected] = useState(null),
    [revision, setRevision] = useState(null),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [jobs, setJobs] = useState([]),
    [outfit, setOutfit] = useState(null),
    [replaceSlot, setReplaceSlot] = useState("");
  const [collection, setCollection] = useState("");
  const isPublic = !!status?.publicDemo;
  const [unusedPhotos, setUnusedPhotos] = useState([]);
  const [analyzePhoto, setAnalyzePhoto] = useState(false);
  const [origin, setOrigin] = useState("USER_PHOTO"),
    [description, setDescription] = useState(""),
    [url, setUrl] = useState(""),
    [photo, setPhoto] = useState(""),
    [identification, setIdentification] = useState(null),
    [confirmation, setConfirmation] = useState({
      category: "TOP",
      color: "",
      fit: "relaxed",
      details: "",
    }),
    [occasion, setOccasion] = useState("class and campus");
  async function refresh() {
    const [a, p, o, f, j, u] = await Promise.all([
      api("/discover"),
      api("/preferences"),
      api("/items?kind=OWNED"),
      api("/items?kind=OUTFIT"),
      api("/jobs"),
      api("/uploads"),
    ]);
    setAll(a);
    setPrefs(p);
    setOwned(o);
    setOutfits(f);
    setJobs(j);
    setUnusedPhotos(u);
    setResults((old) => old.map((d) => a.find((x) => x.id === d.id) || d));
  }
  useEffect(() => {
    api("/status")
      .then(setStatus)
      .catch((e) => setError(e.message));
    refresh().catch((e) => setError(e.message));
  }, []);
  async function run(fn) {
    setError("");
    setBusy(true);
    try {
      await fn();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => {
    if (!jobs.some((j) => ["QUEUED", "RUNNING"].includes(j.state))) return;
    const timer = setInterval(async () => {
      try {
        const updates = await Promise.all(
          jobs.map((j) =>
            ["QUEUED", "RUNNING"].includes(j.state) ? api("/jobs/" + j.id) : j,
          ),
        );
        setJobs(updates);
        if (
          updates.some(
            (j, i) =>
              j.state !== jobs[i].state &&
              ["SUCCEEDED", "FAILED"].includes(j.state),
          )
        ) {
          await refresh();
          if (selected) {
            const d = await api("/items/" + selected.id);
            setSelected(d);
            setRevision(last(d)?.id);
          }
          if (updates.some((j) => j.state === "FAILED"))
            setNotice(
              "Generation failed. Previous versions are preserved. You can retry within the daily limit.",
            );
        }
      } catch (e) {
        setError(e.message);
      }
    }, 1800);
    return () => clearInterval(timer);
  }, [jobs, selected?.id]);
  const current =
    selected?.revisions?.find((r) => r.id === revision) || last(selected);
  const currentOutfit = last(outfit);
  function open(d) {
    setSelected(d);
    setRevision(last(d)?.id);
    setMessage("");
    setReplaceSlot("");
  }
  async function save(d) {
    const next = {
      ...prefs,
      saved: prefs.saved.includes(d.id)
        ? prefs.saved.filter((id) => id !== d.id)
        : [...prefs.saved, d.id],
    };
    setPrefs(await api("/preferences", next, "PUT"));
  }
  async function find(e) {
    e.preventDefault();
    await run(async () => {
      const ordinal = /second|2nd/i.test(query)
        ? 1
        : /third|3rd/i.test(query)
          ? 2
          : 0;
      const isFollowup =
        results.length > 0 &&
        /^(make|keep|more|less|i prefer|change|same|something more)/i.test(
          query.trim(),
        );
      const previous = isFollowup
        ? results[Math.min(ordinal, results.length - 1)]
        : null;
      if (
        previous &&
        /^i prefer (the )?(first|second|third|[123](st|nd|rd)) (one|option)[.!]?$/i.test(
          query.trim(),
        )
      ) {
        open(previous);
        setHistory((h) => [...h, query]);
        setNotice("Selected that direction. Refine it in the concept panel.");
        return;
      }
      const r = await api("/find", {
        message: query,
        conceptId: previous?.id,
        revisionId: last(previous)?.id,
      });
      setResults(r.concepts);
      setHistory((h) => [...h, query]);
      setNotice(r.notice);
      setTab("Find");
      await refresh();
    });
  }
  async function edit(e) {
    e.preventDefault();
    await run(async () => {
      const r = await api("/find", {
        message,
        conceptId: selected.id,
        revisionId: current.id,
      });
      if (isPublic) {
        setResults(r.concepts);
        setSelected(null);
        setTab("Find");
        setNotice(r.notice);
        return;
      }
      const d = r.concepts[0];
      open(d);
      setResults((old) => [d, ...old.filter((x) => x.id !== d.id)]);
      setNotice(
        "New version saved as a design brief. Generate its image when ready; the earlier image stays in history.",
      );
      await refresh();
    });
  }
  async function generate(d, rid, doll = false) {
    await run(async () => {
      const chosen = d.revisions?.find((r) => r.id === rid) || last(d);
      if (!doll && status?.imageConfigured && chosen?.data.state === "READY") {
        const copy = await api("/find", {
          message: "Keep the same garment and all construction details",
          conceptId: d.id,
          revisionId: rid,
        });
        d = copy.concepts[0];
        rid = last(d).id;
        open(d);
      }
      const j = await api(
        (doll ? "/outfits/" : "/items/") +
          d.id +
          (doll ? "/doll" : "/generate"),
        {
          revisionId: rid,
          regenerate: !doll && chosen?.data.state === "READY",
        },
      );
      setJobs((old) => [j, ...old.filter((x) => x.id !== j.id)]);
      setNotice(
        doll
          ? "Doll preview queued. It is an approximation, not an exact reproduction."
          : "Image queued. You can keep browsing.",
      );
    });
  }
  async function style(d, rid) {
    await run(async () => {
      const o = await api("/outfits", {
        anchorId: d.id,
        revisionId: rid,
        message: occasion,
      });
      setOutfit(o);
      setTab("Style");
      setSelected(null);
      await refresh();
    });
  }
  async function identify(e) {
    e.preventDefault();
    await run(async () => {
      const d = await api("/owned/identify", {
        origin,
        description,
        url,
        image: photo,
        analyzePhoto,
      });
      setIdentification(d);
      const b = d.data.visionAttributes || d.data.suggested;
      setConfirmation({
        category: b.category,
        color: b.color,
        fit: b.fit,
        details: Array.isArray(b.details) ? b.details.join(", ") : b.details,
      });
      await refresh();
    });
  }
  async function upload(file) {
    if (!file) return;
    await run(async () => {
      if (
        !["image/png", "image/jpeg"].includes(file.type) ||
        file.size > 8 * 1024 * 1024
      )
        throw new Error("Choose a PNG or JPEG under 8 MB.");
      const form = new FormData();
      form.append("file", file);
      const r = await api("/uploads", form);
      if (photo)
        await api(photo.replace("/api/studio", ""), undefined, "DELETE");
      setPhoto(r.image);
      setNotice(r.notice);
    });
  }
  async function remove(d) {
    await run(async () => {
      await api("/items/" + d.id, undefined, "DELETE");
      if (selected?.id === d.id) setSelected(null);
      if (outfit?.data.anchorId === d.id) setOutfit(null);
      if (identification?.id === d.id) {
        setIdentification(null);
        setPhoto("");
      }
      await refresh();
    });
  }
  const filtered = all.filter(
    (d) =>
      (!category || last(d)?.data.brief.category === category) &&
      !prefs.hidden.includes(d.id) &&
      (!collection ||
        (d.data.collections || [last(d)?.data.brief.aesthetic]).includes(
          collection,
        )),
  );
  const listing =
    tab === "Saved"
      ? all.filter((d) => prefs.saved.includes(d.id))
      : tab === "Find"
        ? results
        : filtered;
  return (
    <div className="studio">
      <header>
        <a
          className="wordmark"
          href="#"
          onClick={(e) => {
            e.preventDefault();
            setTab("Discover");
          }}
        >
          closie<span>✳</span>
        </a>
        <nav aria-label="Main">
          {["Discover", "Find", "Style", "Saved", "My Style"].map((t) => (
            <button
              key={t}
              className={tab === t ? "active" : ""}
              onClick={() => setTab(t)}
            >
              {t}
            </button>
          ))}
        </nav>
        <span className="profile-dot" aria-label="Your studio">
          m
        </span>
      </header>
      <main>
        {isPublic && (
          <p className="notice" role="status">
            Public demo · Browse, search and remix pre-generated AI concepts.
            Live image generation and private uploads are disabled.
          </p>
        )}
        <div className="studio-ribbon">
          YOUR PERSONAL AI STYLIST <span>✦</span> A little inspiration. A lot
          more you.
        </div>
        {error && (
          <div role="alert" className="alert">
            <span>{error}</span>
            <button onClick={() => setError("")}>Dismiss</button>
          </div>
        )}
        {notice && (
          <div role="status" className="notice">
            {notice}
            <button aria-label="Dismiss notice" onClick={() => setNotice("")}>
              ×
            </button>
          </div>
        )}
        {tab === "Discover" && (
          <section className="hero">
            <div>
              <p className="eyebrow">THE CAMPUS EDIT / VOL. 01</p>
              <h1>
                Your next
                <br />
                “that's so <em>me.</em>”
              </h1>
              <p>
                Soft colors, playful details, everyday possibilities.
                <br />
                Find a little inspiration. Make it your own.
              </p>
              <button
                className="primary"
                onClick={() => {
                  setTab("Find");
                  setQuery("Something cute and comfortable for class");
                }}
              >
                Find my next favorite ↗
              </button>
              <span className="scribble">you bring the vibe ♡</span>
            </div>
            <div className="hero-art">
              <span className="sticker">
                dream it.
                <br />
                style it.
              </span>
              <Picture
                src="/concepts/blue-hoodie.png"
                alt="Original baby blue hoodie concept"
              />
              <div className="mini-note">a softer kind of statement ✧</div>
            </div>
          </section>
        )}
        {tab === "Find" && (
          <section className="find-intro">
            <p className="eyebrow">LET'S FIND YOUR THING</p>
            <h1>
              What's the <em>vibe?</em>
            </h1>
            <p>Tell me what you're imagining. No fashion vocabulary needed.</p>
          </section>
        )}
        {(tab === "Discover" || tab === "Find") && (
          <form className="search" onSubmit={find}>
            <span>✧</span>
            <input
              aria-label="Describe your style"
              placeholder="A baby blue hoodie that isn't too basic…"
              value={query}
              maxLength={1200}
              onChange={(e) => setQuery(e.target.value)}
              required
            />
            <button className="primary" disabled={busy || !query.trim()}>
              Explore ↗
            </button>
          </form>
        )}
        {tab === "Find" && (
          <>
            <div className="suggestions">
              {[
                "Something cute for class",
                "A baby blue hoodie",
                "A fitted pink cardigan",
                "A coastal casual outfit",
              ].map((t) => (
                <button key={t} onClick={() => setQuery(t)}>
                  {t} ↗
                </button>
              ))}
            </div>
            <p className="fine">
              {isPublic ? (
                "Search the existing concept capsule in everyday language. Results are pre-generated AI inspiration, not new live images or products for sale."
              ) : (
                <>
                  Curated rules prepare design directions. Live generation:{" "}
                  {status?.imageConfigured
                    ? "configured; each image needs your request"
                    : "off · briefs and the original capsule remain available"}
                  . Concepts are inspiration, not products for sale.
                </>
              )}
            </p>
            {history.length > 0 && (
              <details>
                <summary>Your recent requests</summary>
                {history.map((h, i) => (
                  <button key={i} onClick={() => setQuery(h)}>
                    {h}
                  </button>
                ))}
              </details>
            )}
          </>
        )}
        {["Discover", "Find", "Saved"].includes(tab) && (
          <>
            <section className="section-heading">
              <div>
                <p className="eyebrow">
                  {tab === "Find"
                    ? "YOUR DESIGN DIRECTIONS"
                    : tab === "Saved"
                      ? "KEEP THE GOOD ONES"
                      : "GOOD FINDS, JUST FOR YOU"}
                </p>
                <h2>
                  {tab === "Saved"
                    ? "Your little collection"
                    : tab === "Find"
                      ? "A few ways to wear the feeling"
                      : "Made for your mood"}{" "}
                  <span>✧</span>
                </h2>
              </div>
              <small>Original fashion concepts · no retail availability</small>
            </section>
            {tab === "Discover" && (
              <div className="categories">
                {[["", "For you"], ...Object.entries(slots)]
                  .filter(
                    ([key]) =>
                      !isPublic ||
                      !key ||
                      all.some((d) => last(d).data.brief.category === key),
                  )
                  .map(([key, label]) => (
                    <button
                      key={key}
                      className={category === key ? "chosen" : ""}
                      onClick={() => setCategory(key)}
                    >
                      {label}
                    </button>
                  ))}
              </div>
            )}
            {tab === "Discover" && isPublic && (
              <label className="collection-filter">
                Style collection
                <select
                  aria-label="Style collection"
                  value={collection}
                  onChange={(e) => setCollection(e.target.value)}
                >
                  <option value="">All campus styles</option>
                  {status?.aesthetics.map((a) => (
                    <option key={a}>{a}</option>
                  ))}
                </select>
              </label>
            )}
            <div className="concept-grid">
              {listing.map((d, i) => {
                const r = last(d),
                  b = r.data.brief;
                return (
                  <article
                    className={"concept-card tint-" + (i % 4)}
                    key={d.id}
                  >
                    <div className="card-image">
                      <button
                        className="image-button"
                        aria-label={"View " + b.color + " " + b.garment}
                        onClick={() => open(d)}
                      >
                        <Picture
                          src={r.data.image}
                          alt={b.color + " " + b.garment}
                        />
                      </button>
                      <button
                        className="save"
                        aria-label={
                          (prefs.saved.includes(d.id) ? "Unsave " : "Save ") +
                          b.garment
                        }
                        onClick={() => run(() => save(d))}
                      >
                        {prefs.saved.includes(d.id) ? "♥" : "♡"}
                      </button>
                      <span className="concept-tag">
                        {r.data.state === "READY"
                          ? isPublic
                            ? "PRE-GENERATED AI"
                            : "FASHION CONCEPT"
                          : "DESIGN BRIEF"}
                      </span>
                    </div>
                    <div className="card-meta">
                      <div>
                        <h3>
                          {b.color} {b.garment}
                        </h3>
                        <p>
                          {b.aesthetic} · {b.fit}
                        </p>
                      </div>
                      <button
                        aria-label={"Style " + b.garment}
                        onClick={() => style(d, r.id)}
                      >
                        ↗
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>
            {listing.length === 0 && (
              <div className="empty">
                <span>♡</span>
                <h3>
                  {tab === "Find"
                    ? "Bring us a little idea."
                    : "A little space for inspiration."}
                </h3>
                <p>
                  {tab === "Saved"
                    ? "Tap a heart to keep a concept here."
                    : "Try another category or describe a garment above."}
                </p>
              </div>
            )}
          </>
        )}
        {tab === "Saved" && outfits.some((d) => prefs.saved.includes(d.id)) && (
          <section>
            <h2>Saved looks</h2>
            <div className="categories">
              {outfits
                .filter((d) => prefs.saved.includes(d.id))
                .map((d, i) => (
                  <button
                    key={d.id}
                    onClick={() => {
                      setOutfit(d);
                      setTab("Style");
                    }}
                  >
                    View saved look {d.data.title || i + 1}
                  </button>
                ))}
            </div>
          </section>
        )}
        {tab === "My Style" && (
          <section className="settings">
            <p className="eyebrow">MAKE YOURSELF AT HOME</p>
            <h1>
              A little more <em>you.</em>
            </h1>
            <p>
              These preferences rank the capsule. No images are generated when
              you save.
            </p>
            <h3>Your aesthetics</h3>
            <div className="categories">
              {status?.aesthetics?.map((a) => (
                <button
                  key={a}
                  className={prefs.aesthetics.includes(a) ? "chosen" : ""}
                  onClick={() =>
                    setPrefs((p) => ({
                      ...p,
                      aesthetics: p.aesthetics.includes(a)
                        ? p.aesthetics.filter((v) => v !== a)
                        : [...p.aesthetics, a],
                    }))
                  }
                >
                  {a}
                </button>
              ))}
            </div>
            <label>
              Favorite colors
              <input
                value={prefs.colors.join(", ")}
                onChange={(e) =>
                  setPrefs((p) => ({
                    ...p,
                    colors: e.target.value
                      .split(",")
                      .map((c) => c.trim().toLowerCase())
                      .filter(Boolean),
                  }))
                }
                placeholder="pink, cream, blue"
              />
            </label>
            <label>
              Life lately
              <textarea
                maxLength={500}
                value={prefs.lifestyle}
                onChange={(e) =>
                  setPrefs((p) => ({ ...p, lifestyle: e.target.value }))
                }
                placeholder="Classes, coffee runs, chilly lecture halls…"
              />
            </label>
            <label>
              Future shopping budget (optional)
              <input
                maxLength={40}
                value={prefs.budget}
                onChange={(e) =>
                  setPrefs((p) => ({ ...p, budget: e.target.value }))
                }
              />
            </label>
            <p className="fine">
              Concepts have no prices. This budget is context for future
              verified shopping results; it does not price these designs.
            </p>
            <button
              className="primary"
              onClick={() =>
                run(async () => {
                  await api("/preferences", prefs, "PUT");
                  await refresh();
                  setNotice(
                    "Your preferences are saved in this private browser studio.",
                  );
                })
              }
            >
              Save my style
            </button>
            <button
              onClick={() =>
                run(async () => {
                  await api("/preferences", emptyPrefs, "PUT");
                  await refresh();
                })
              }
            >
              Reset preferences & favorites
            </button>
          </section>
        )}
        {tab === "Style" && (
          <>
            <section className="style-intro">
              <p className="eyebrow">A LITTLE MIX, A LITTLE MAGIC</p>
              <h1>
                Make it an <em>outfit.</em>
              </h1>
              <p>
                Start with a Closie concept, or something already in your
                closet.
              </p>
              <label>
                Where are we wearing it?
                <input
                  value={occasion}
                  onChange={(e) => setOccasion(e.target.value)}
                  maxLength={1200}
                />
              </label>
            </section>
            {outfit && currentOutfit && (
              <section className="outfit-section">
                <div className="section-heading">
                  <div>
                    <p className="eyebrow">YOUR OUTFIT BOARD</p>
                    <h2>Built around your favorite.</h2>
                  </div>
                  <span className="pill">Anchor locked ♡</span>
                </div>
                <div className="outfit-board">
                  {currentOutfit.data.items.map((item) => (
                    <article
                      key={item.slot}
                      className={"board-piece slot-" + item.slot}
                    >
                      <Picture src={item.image} alt={item.title} />
                      <h3>{item.title}</h3>
                      <span className="pill">
                        {item.id === outfit.data.anchorId
                          ? "Your anchor · preserved"
                          : slots[item.slot]}
                      </span>
                      {item.id !== outfit.data.anchorId && (
                        <button
                          onClick={() =>
                            setReplaceSlot(
                              replaceSlot === item.slot ? "" : item.slot,
                            )
                          }
                        >
                          Change this piece ↗
                        </button>
                      )}
                    </article>
                  ))}
                </div>
                <p>{currentOutfit.data.explanation}</p>
                {replaceSlot && (
                  <div className="replacement-picker">
                    <h3>Change {slots[replaceSlot]} only</h3>
                    {all
                      .filter(
                        (d) =>
                          last(d).data.brief.category === replaceSlot &&
                          last(d).data.state === "READY",
                      )
                      .map((d) => (
                        <button
                          key={d.id}
                          onClick={() =>
                            run(async () => {
                              setOutfit(
                                await api(
                                  "/outfits/" + outfit.id + "/replace",
                                  { slot: replaceSlot, conceptId: d.id },
                                ),
                              );
                              setReplaceSlot("");
                              await refresh();
                            })
                          }
                        >
                          <Picture
                            src={last(d).data.image}
                            alt={last(d).data.brief.title}
                          />
                          {last(d).data.brief.color}{" "}
                          {last(d).data.brief.garment}
                        </button>
                      ))}
                  </div>
                )}
                <div className="actions">
                  <button
                    className="primary"
                    onClick={() => run(() => save(outfit))}
                  >
                    {prefs.saved.includes(outfit.id)
                      ? "♥ Look saved"
                      : "♡ Save look"}
                  </button>
                  {!isPublic && (
                    <button
                      className="primary"
                      disabled={busy}
                      onClick={() => generate(outfit, currentOutfit.id, true)}
                    >
                      Try a doll preview ✧
                    </button>
                  )}
                  <button onClick={() => setOutfit(null)}>
                    Start another outfit
                  </button>
                </div>
                <p className="fine">
                  {isPublic
                    ? "Doll previews and live generation are disabled in this public demo. Changing a piece reuses an existing concept image."
                    : "Optional generated illustration. Garment details may differ. Only runs when requested and a provider is configured."}
                </p>
                <details>
                  <summary>
                    Outfit history · {outfit.revisions.length} versions
                  </summary>
                  {outfit.revisions.map((r, i) => (
                    <button
                      key={r.id}
                      onClick={() =>
                        run(async () => {
                          setOutfit(
                            await api("/items/" + outfit.id + "/revert", {
                              revisionId: r.id,
                            }),
                          );
                          await refresh();
                        })
                      }
                    >
                      Restore version {i + 1}
                    </button>
                  ))}
                </details>
              </section>
            )}
            {isPublic && (
              <section className="owned-form">
                <p className="eyebrow">STYLE MY ITEM</p>
                <h2>Already yours. Fresh possibilities.</h2>
                <div className="categories">
                  <button disabled>Upload a photo</button>
                  <button disabled>Brand / model</button>
                  <button disabled>Product link</button>
                </div>
                <p>
                  Private uploads, photo recognition and retailer matching are
                  unavailable in this public demo. Try Style this on a sample
                  concept or open a pre-arranged look below.
                </p>
                <p className="fine">
                  The private local mode keeps the existing item-confirmation
                  workflow. No recognition or exact product match is simulated
                  here.
                </p>
              </section>
            )}
            {!isPublic && (
              <section className="owned-form">
                <p className="eyebrow">STYLE MY ITEM</p>
                <h2>Already yours. Fresh possibilities.</h2>
                <div className="categories">
                  {[
                    ["USER_PHOTO", "Upload a photo"],
                    ["BRAND_MODEL", "Brand / model"],
                    ["PRODUCT_URL", "Product link"],
                  ].map(([key, title]) => (
                    <button
                      key={key}
                      className={origin === key ? "chosen" : ""}
                      onClick={() => {
                        setOrigin(key);
                        setIdentification(null);
                      }}
                    >
                      {title}
                    </button>
                  ))}
                </div>
                <form onSubmit={identify}>
                  {origin === "USER_PHOTO" && (
                    <>
                      <label className="upload">
                        ＋ Add a garment photo
                        <input
                          aria-label="Garment photo"
                          type="file"
                          accept="image/png,image/jpeg"
                          onChange={(e) => upload(e.target.files[0])}
                        />
                      </label>
                      {photo && (
                        <div className="uploaded">
                          <Picture
                            src={photo}
                            alt="Your private garment photo"
                          />
                          <button
                            type="button"
                            onClick={() =>
                              run(async () => {
                                await api(
                                  photo.replace("/api/studio", ""),
                                  undefined,
                                  "DELETE",
                                );
                                setPhoto("");
                              })
                            }
                          >
                            Delete uploaded photo
                          </button>
                        </div>
                      )}
                      {status?.visionConfigured && (
                        <label className="photo-consent">
                          <input
                            type="checkbox"
                            checked={analyzePhoto}
                            onChange={(e) => setAnalyzePhoto(e.target.checked)}
                          />{" "}
                          Analyze this garment photo with Gemini (optional)
                        </label>
                      )}
                      <p className="fine">
                        PNG or JPEG, up to 8 MB. Private metadata is stripped.
                        {status?.visionConfigured
                          ? "Optional AI analysis sends the sanitized garment photo to Gemini only when you opt in below."
                          : "Describe and confirm your garment; automatic vision is not enabled."}
                      </p>
                    </>
                  )}
                  {origin === "PRODUCT_URL" && (
                    <label>
                      Retailer product link
                      <input
                        type="url"
                        value={url}
                        onChange={(e) => setUrl(e.target.value)}
                        placeholder="https://…"
                        required
                      />
                    </label>
                  )}
                  <label>
                    {origin === "BRAND_MODEL"
                      ? "Brand, product name, model / SKU and color"
                      : "Describe your garment"}
                    <textarea
                      maxLength={1200}
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                      placeholder="My black puffer jacket, relaxed fit…"
                      required
                    />
                  </label>
                  <button
                    className="primary"
                    disabled={busy || (origin === "USER_PHOTO" && !photo)}
                  >
                    Identify & review ↗
                  </button>
                </form>
                <p className="fine">
                  We only look for currently eligible records in the configured
                  catalog. We do not scrape arbitrary retailer pages. No exact
                  match? You can still style your item.
                </p>
                {identification && (
                  <div className="confirmation">
                    <h3>Does this look right?</h3>
                    <span className="pill">
                      {identification.data.matchStatus.replaceAll("_", " ")}
                    </span>
                    <p>{identification.data.identification}</p>
                    {identification.data.candidates.map((c, i) => (
                      <p key={i}>
                        {c.product.title} — {c.status.replaceAll("_", " ")}.{" "}
                        {c.evidence}
                      </p>
                    ))}
                    <label>
                      Category
                      <select
                        value={confirmation.category}
                        onChange={(e) =>
                          setConfirmation((c) => ({
                            ...c,
                            category: e.target.value,
                          }))
                        }
                      >
                        {Object.entries(slots).map(([key, title]) => (
                          <option key={key} value={key}>
                            {title}
                          </option>
                        ))}
                      </select>
                    </label>
                    {["color", "fit", "details"].map((key) => (
                      <label key={key}>
                        {key}
                        <input
                          maxLength={500}
                          value={confirmation[key]}
                          onChange={(e) =>
                            setConfirmation((c) => ({
                              ...c,
                              [key]: e.target.value,
                            }))
                          }
                        />
                      </label>
                    ))}
                    <button
                      className="primary"
                      disabled={busy}
                      onClick={() =>
                        run(async () => {
                          const d = await api(
                            "/owned/" + identification.id + "/confirm",
                            confirmation,
                          );
                          setIdentification(d);
                          const o = await api("/outfits", {
                            anchorId: d.id,
                            message: occasion,
                          });
                          setOutfit(o);
                          await refresh();
                        })
                      }
                    >
                      Confirm my item & style it ↗
                    </button>
                    <button onClick={() => remove(identification)}>
                      Delete this item & photo
                    </button>
                  </div>
                )}
              </section>
            )}
            {unusedPhotos.length > 0 && (
              <section>
                <h3>Unattached private photos</h3>
                <p className="fine">
                  Photos you uploaded but have not attached to an item. You can
                  remove them here.
                </p>
                <div className="closet-list">
                  {unusedPhotos.map((p) => (
                    <article key={p.id}>
                      <Picture
                        src={p.image}
                        alt="Unattached private garment photo"
                      />
                      <button
                        onClick={() =>
                          run(async () => {
                            await api("/assets/" + p.id, undefined, "DELETE");
                            if (photo === p.image) setPhoto("");
                            await refresh();
                          })
                        }
                      >
                        Delete photo
                      </button>
                    </article>
                  ))}
                </div>
              </section>
            )}
            {owned.length > 0 && (
              <section>
                <h2>In your closet</h2>
                <div className="closet-list">
                  {owned.map((d) => (
                    <article key={d.id}>
                      <Picture src={d.data.image} alt={d.data.description} />
                      <h3>{d.data.description}</h3>
                      <span>
                        {d.data.confirmed
                          ? "Confirmed by you"
                          : "Needs confirmation"}
                      </span>
                      <button
                        onClick={() => {
                          if (d.data.confirmed) style(d);
                          else {
                            setIdentification(d);
                            setConfirmation({
                              category: d.data.suggested.category,
                              color: d.data.suggested.color,
                              fit: d.data.suggested.fit,
                              details: d.data.suggested.details.join(", "),
                            });
                          }
                        }}
                      >
                        {d.data.confirmed ? "Style this" : "Review item"}
                      </button>
                      <button onClick={() => remove(d)}>Delete item</button>
                    </article>
                  ))}
                </div>
              </section>
            )}
            {outfits.length > 0 && (
              <section>
                <h2>{isPublic ? "Outfit collection" : "Your outfit boards"}</h2>
                <div className="categories">
                  {outfits.map((d, i) => (
                    <React.Fragment key={d.id}>
                      <button onClick={() => setOutfit(d)}>
                        View {d.data.title || `Outfit ${i + 1}`} ·{" "}
                        {d.revisions.length} versions
                      </button>
                      <button
                        aria-label={`Save look ${d.data.title || i + 1}`}
                        onClick={() => run(() => save(d))}
                      >
                        {prefs.saved.includes(d.id) ? "♥" : "♡"}
                      </button>
                    </React.Fragment>
                  ))}
                </div>
              </section>
            )}
          </>
        )}
        {jobs.length > 0 && (
          <section className="jobs">
            <h3>Image studio</h3>
            {jobs.map((j) => (
              <div key={j.id}>
                <span>
                  {j.operation === "DOLL" ? "Doll preview" : "Garment image"} ·{" "}
                  {j.state.toLowerCase()}
                </span>
                {["QUEUED", "RUNNING"].includes(j.state) && (
                  <button
                    onClick={() =>
                      run(async () => {
                        const next = await api("/jobs/" + j.id + "/cancel", {});
                        setJobs((old) =>
                          old.map((v) => (v.id === j.id ? next : v)),
                        );
                      })
                    }
                  >
                    Cancel
                  </button>
                )}
                {j.error && <p>{j.error}</p>}
                {j.result?.image && j.operation === "DOLL" && (
                  <>
                    <Picture
                      src={j.result.image}
                      alt="Generated approximate doll styling preview"
                    />
                    <p className="fine">
                      AI approximation · inspect garment differences.
                    </p>
                  </>
                )}
              </div>
            ))}
          </section>
        )}
        <footer>
          <span className="wordmark">closie✳</span>
          <p>
            Your personal AI stylist. Original concepts, real possibilities.
          </p>
          <small>
            {isPublic
              ? "Public demo · pre-generated AI concepts · "
              : "Local concept studio · curated capsule + structured design briefs · "}{" "}
            {isPublic
              ? "live image generation disabled"
              : status?.imageConfigured
                ? "image provider configured"
                : "live image generation not configured"}
          </small>
        </footer>
      </main>
      {selected && current && (
        <div className="modal-backdrop" onClick={() => setSelected(null)}>
          <section
            role="dialog"
            aria-modal="true"
            aria-label="Concept details"
            className="concept-dialog"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              className="close"
              aria-label="Close concept"
              onClick={() => setSelected(null)}
            >
              ×
            </button>
            <div className="detail-image">
              <Picture
                src={current.data.image}
                alt={
                  current.data.brief.color + " " + current.data.brief.garment
                }
              />
            </div>
            <div className="detail-copy">
              <p className="eyebrow">
                {isPublic
                  ? "PRE-GENERATED AI CONCEPT · NOT FOR SALE"
                  : "FASHION CONCEPT · NOT FOR SALE"}
              </p>
              <h2>
                {current.data.brief.color} {current.data.brief.garment}
              </h2>
              <p>
                {current.data.brief.aesthetic} · {current.data.brief.fit} ·{" "}
                {current.data.brief.fabric}
              </p>
              <p>{current.data.brief.details.join(", ")}</p>
              <div className="actions">
                <button
                  className="primary"
                  onClick={() => style(selected, current.id)}
                >
                  Style this ↗
                </button>
                <button onClick={() => run(() => save(selected))}>
                  {prefs.saved.includes(selected.id) ? "♥ Saved" : "♡ Save"}
                </button>
              </div>
              <form onSubmit={edit}>
                <label>
                  {isPublic
                    ? "Find similar existing concepts"
                    : "Make it yours"}
                  <input
                    aria-label="Refine concept"
                    value={message}
                    onChange={(e) => setMessage(e.target.value)}
                    maxLength={1200}
                    placeholder="Keep the cardigan, change the neckline…"
                    required
                  />
                </label>
                <button disabled={busy}>
                  {isPublic
                    ? "Search this refinement ↗"
                    : "Save a new design version ↗"}
                </button>
              </form>
              {isPublic ? (
                <p className="fine">
                  Pre-generated AI fashion inspiration. Live image editing,
                  regeneration and doll previews are unavailable in this demo.
                </p>
              ) : (
                <>
                  <button
                    disabled={busy}
                    onClick={() => generate(selected, current.id)}
                  >
                    Generate this version's image ✧
                  </button>
                  <p className="fine">
                    {current.data.provider} ·{" "}
                    {current.data.quality ||
                      "Image not yet generated. Previous images are kept in history."}
                  </p>
                </>
              )}
              <details>
                <summary>Design brief & assumptions</summary>
                <dl>
                  {Object.entries(current.data.brief).map(([key, value]) => (
                    <div key={key}>
                      <dt>{key}</dt>
                      <dd>{Array.isArray(value) ? value.join("; ") : value}</dd>
                    </div>
                  ))}
                </dl>
              </details>
              <label>
                Version history
                <select
                  disabled={busy}
                  value={current.id}
                  onChange={(e) => setRevision(e.target.value)}
                >
                  {selected.revisions.map((r, i) => (
                    <option key={r.id} value={r.id}>
                      Version {i + 1} · {r.data.state}
                    </option>
                  ))}
                </select>
              </label>
              {current.id !== last(selected).id && (
                <button
                  onClick={() =>
                    run(async () => {
                      const d = await api("/items/" + selected.id + "/revert", {
                        revisionId: current.id,
                      });
                      open(d);
                      await refresh();
                    })
                  }
                >
                  Restore this version
                </button>
              )}
              {!isPublic && !current.data.provider?.includes("CURATED") && (
                <button onClick={() => remove(selected)}>
                  Delete concept & generated images
                </button>
              )}
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
