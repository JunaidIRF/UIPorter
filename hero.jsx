// ═══════════════════════════════════════════════════════════════════
// Hero — animated converter + live UI preview (wireframe → painted)
// ═══════════════════════════════════════════════════════════════════

const { useState, useEffect, useRef } = React;

function StaticCode({ tokens }) {
  return <>{tokens.map((t, i) => <span key={i} className={t.c || "tok-p"}>{t.t}</span>)}</>;
}

function CodePane({ tokens, progress = 1 }) {
  let consumed = 0;
  const total = tokens.reduce((n, t) => n + t.t.length, 0);
  const cap = Math.floor(total * progress);
  const out = [];
  for (let i = 0; i < tokens.length; i++) {
    if (consumed >= cap) break;
    const tok = tokens[i];
    const slice = tok.t.slice(0, cap - consumed);
    out.push(<span key={i} className={tok.c || "tok-p"}>{slice}</span>);
    consumed += slice.length;
  }
  return <>{out}{progress < 1 && <span className="caret-bar">▍</span>}</>;
}

// ── Mock UI preview per sample (wireframe → painted) ────────────────
function UIPreview({ sampleId, kind, progress }) {
  // progress 0..1: 0 = wireframe, 0.5 = filled outline, 1 = fully painted
  const w = progress;
  const fill = Math.max(0, (w - 0.35) / 0.65);
  const paint = Math.max(0, (w - 0.65) / 0.35);

  if (sampleId === "button") {
    return (
      <div className="ui-preview">
        <div className="up-canvas pv-btn">
          <button className="pv-button" type="button">Sign in</button>
        </div>
      </div>
    );
  }
  if (sampleId === "form") {
    return (
      <div className="ui-preview">
        <div className="up-canvas pv-form">
          <div className="pv-form-row">
            <span className="pv-form-lbl">Username</span>
            <span className="pv-form-fld">jabcodex</span>
          </div>
          <div className="pv-form-row">
            <span className="pv-form-lbl">Password</span>
            <span className="pv-form-fld pv-form-dots">••••••••</span>
          </div>
          <button className="pv-button pv-button-sm" type="button">Log in</button>
        </div>
      </div>
    );
  }
  if (sampleId === "colors") {
    const swatches = [
      { c: "#5b5bff", n: "indigo" },
      { c: "#ff7aa8", n: "pink" },
      { c: "#ffb84a", n: "amber" },
      { c: "#7ad97a", n: "green" }
    ];
    return (
      <div className="ui-preview">
        <div className="up-canvas pv-colors">
          {swatches.map((s, i) => (
            <div key={i} className="pv-sw">
              <div className="pv-sw-chip" style={{ background: s.c }}></div>
              <span className="pv-sw-name">{s.n}</span>
            </div>
          ))}
        </div>
      </div>
    );
  }
  if (sampleId === "layout") {
    return (
      <div className="ui-preview">
        <div className="up-canvas pv-grid">
          {[0,1,2,3].map(i => (
            <div key={i} className="pv-cell">
              <span className="pv-cell-coord">R{Math.floor(i/2)+1}·C{i%2+1}</span>
            </div>
          ))}
        </div>
      </div>
    );
  }
  return null;
}

function Hero() {
  const samples = window.SAMPLES.filter(s =>
    s.pairs.find(p => p.from === "JavaFX") && s.pairs.find(p => p.from === "WinForms")
  );

  const [sampleIdx, setSampleIdx] = useState(0);
  const [dir, setDir] = useState(0); // 0: javaFx→winForms, 1: winForms→javaFx
  const [progress, setProgress] = useState(0);

  const sample = samples[sampleIdx];
  const javaFx = sample.pairs.find(p => p.from === "JavaFX");
  const winForms = sample.pairs.find(p => p.from === "WinForms");
  const fromPair = dir === 0 ? javaFx : winForms;
  const toPair = dir === 0 ? winForms : javaFx;

  // Static theme swatches that don't reflow with the converter sample
  const themeProgress = Math.min(1, progress * 1.2);

  useEffect(() => {
    setProgress(0);
    let raf;
    const start = performance.now();
    const dur = 3200;
    const step = (t) => {
      const p = Math.min(1, (t - start) / dur);
      setProgress(1 - Math.pow(1 - p, 2.2));
      if (p < 1) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    const adv = setTimeout(() => {
      if (dir === 0) {
        setDir(1); // flip direction first
      } else {
        setDir(0);
        setSampleIdx((sampleIdx + 1) % samples.length); // then advance sample
      }
    }, 4600);
    return () => { cancelAnimationFrame(raf); clearTimeout(adv); };
  }, [dir, sampleIdx]);

  return (
    <section className="hero shell" data-section="hero" data-screen-label="01 Hero">
      <div className="hero-grid">
        <div className="hero-head">
          <span className="label bracket"><span className="dot"></span>UIPorter v1.0 · pluggable adapter engine</span>
          <h1>PORT<br/><span className="acc">ANY UI.</span></h1>
          <p className="hero-sub">
            A two-way converter for <strong>JavaFX</strong> and <strong>WinForms</strong>.
            Parse your interface, send it through a canonical IR, write it back out in
            the other framework. Plug in the next one in <span className="serif-i">one file.</span>
          </p>
          <div className="cta-row">
            <a className="btn btn-primary" href="#install">↓ Download</a>
            <a className="btn btn-ghost" href="https://github.com/JunaidIRF/UIPorter" target="_blank" rel="noopener">
              <GhIcon />View source
            </a>
          </div>
          <div className="hero-meta">
            <span><strong>JAVA·21</strong></span>
            <span><strong>JAVAFX</strong></span>
            <span><strong>2</strong>·SHIPPED</span>
            <span><strong>3</strong>·PLANNED</span>
            <span><strong>MIT</strong></span>
          </div>
        </div>

        <div className="hero-right">
          <div className="converter">
            <div className="converter-chrome">
              <div className="lights"><span></span><span></span><span></span></div>
              <div className="title">UIPorter · live conversion</div>
              <div className="title" style={{ opacity: 0.45 }}>{sample.title}</div>
            </div>
            <div className="converter-tabs">
              <div className="converter-tab from">
                <span className="role">FROM</span>
                <span className="lang">{fromPair.from} {fromPair.ext}</span>
              </div>
              <div className="converter-tab to">
                <span className="role">TO</span>
                <span className="lang">{toPair.from} {toPair.ext}</span>
              </div>
            </div>
            <div className="converter-body">
              <div className="pane from"><StaticCode tokens={fromPair.code} /></div>
              <div className="pane to"><CodePane tokens={toPair.code} progress={progress} /></div>
            </div>
            <div className="converter-foot">
              <div className="status">
                <span className="status-dot"></span>
                <span>AST parsed · {Math.round(progress * 100)}% emitted</span>
              </div>
              <span>canonical IR · {fromPair.code.length} nodes</span>
            </div>
          </div>

          <div className="preview-strip">
            <div className="preview-strip-head">
              <span className="ps-label">RENDERED · {dir === 0 ? "WINFORMS" : "JAVAFX"}</span>
              <span className="ps-meta" style={{ marginRight: 0 }}>{sample.title.toUpperCase()}</span>
            </div>
            <UIPreview sampleId={sample.id} kind={sample.kind} progress={1} />
          </div>
        </div>
      </div>
    </section>
  );
}

function GhIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 .5C5.65.5.5 5.65.5 12c0 5.08 3.29 9.39 7.86 10.92.58.1.79-.25.79-.56 0-.28-.01-1-.02-1.97-3.2.7-3.87-1.54-3.87-1.54-.52-1.33-1.28-1.69-1.28-1.69-1.05-.71.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.76 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.55-.29-5.24-1.28-5.24-5.69 0-1.26.45-2.29 1.18-3.1-.12-.29-.51-1.46.11-3.05 0 0 .97-.31 3.18 1.18A11 11 0 0 1 12 6.8c.98.01 1.97.13 2.9.39 2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.11 3.05.74.81 1.18 1.84 1.18 3.1 0 4.42-2.69 5.39-5.25 5.68.41.36.78 1.06.78 2.14 0 1.55-.01 2.79-.01 3.17 0 .31.21.67.8.56C20.21 21.39 23.5 17.08 23.5 12 23.5 5.65 18.35.5 12 .5z"/>
    </svg>
  );
}

Object.assign(window, { Hero, GhIcon });
