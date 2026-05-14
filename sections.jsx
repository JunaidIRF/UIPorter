// ═══════════════════════════════════════════════════════════════════
// Sections — JavaFX/WinForms only · engineering terminal aesthetic
// ═══════════════════════════════════════════════════════════════════

function SectionHead({ num, name, title, lede }) {
  return (
    <div className="section-head">
      <div className="left">
        <span className="num">§ {num}</span>
        <span className="name">{name}</span>
      </div>
      <div>
        <h2 dangerouslySetInnerHTML={{ __html: title }} />
        <p className="lede">{lede}</p>
      </div>
    </div>
  );
}

// ── IR pipeline (scroll-jacked) ─────────────────────────────────────
function IRSection() {
  const stickyRef = uR(null);
  const outerRef = uR(null);
  const progress = useScrollProgress(outerRef);

  // JS-driven pin (robust against any ancestor that breaks position: sticky)
  React.useEffect(() => {
    const outer = outerRef.current;
    const sticky = stickyRef.current;
    if (!outer || !sticky) return;
    const TOPBAR = 56;
    const apply = () => {
      const r = outer.getBoundingClientRect();
      const stickyH = sticky.offsetHeight;
      const vh = window.innerHeight;
      if (r.top >= TOPBAR) {
        sticky.style.position = "absolute";
        sticky.style.top = "0px";
        sticky.style.bottom = "auto";
      } else if (r.bottom > vh) {
        sticky.style.position = "fixed";
        sticky.style.top = TOPBAR + "px";
        sticky.style.bottom = "auto";
        sticky.style.left = "0";
        sticky.style.right = "0";
      } else {
        sticky.style.position = "absolute";
        sticky.style.top = "auto";
        sticky.style.bottom = "0px";
        sticky.style.left = "";
        sticky.style.right = "";
      }
    };
    apply();
    window.addEventListener("scroll", apply, { passive: true });
    window.addEventListener("resize", apply);
    return () => {
      window.removeEventListener("scroll", apply);
      window.removeEventListener("resize", apply);
    };
  }, []);
  // 4 stages: 0 parse, 1 ast, 2 ir, 3 emit. Map progress 0.15..0.85 → stages.
  const stageF = Math.max(0, Math.min(0.999, (progress - 0.15) / 0.7)) * 4;
  const stage = Math.min(3, Math.floor(stageF));
  const within = stageF - stage;

  const stages = [
    { tag: "01 · READ", op: "read source", note: "the converter looks at your JavaFX / WinForms file" },
    { tag: "02 · MAP", op: "map structure", note: "buttons, fields, layouts — recovered as a tree" },
    { tag: "03 · TRANSLATE", op: "→ canonical IR", note: "one shared shape every adapter understands" },
    { tag: "04 · WRITE", op: "write target", note: "emit clean, idiomatic code on the other side" }
  ];

  return (
    <div className="ir-outer" ref={outerRef} id="how">
      <section className="ir-sticky section shell" ref={stickyRef} data-screen-label="02 IR pipeline">
        <SectionHead
          num="02"
          name="HOW IT WORKS"
          title='ONE BRAIN. <span class="scribble">two</span> DIALECTS.'
          lede="Drop in JavaFX. Out comes WinForms. Or the other way. The converter doesn't translate framework to framework directly — it sends your code through one shared shape, then writes it back out. Scroll to follow."
        />

        <div className="pipe-stage-row">
          {stages.map((s, i) => (
            <div key={i} className={"pipe-stage" + (i === stage ? " active" : "") + (i < stage ? " done" : "")}>
              <div className="ps-num">{s.tag}</div>
              <div className="ps-op">{s.op}</div>
              <div className="ps-note">{s.note}</div>
            </div>
          ))}
          <div className="pipe-progress-line">
            <div className="pipe-progress-fill" style={{ width: `${Math.max(0, Math.min(100, ((stage + within) / 4) * 100))}%` }}></div>
          </div>
        </div>

        <div className="pipe-arena">
          <div className="pipe-pane source">
            <div className="pp-head"><span>// {stages[stage].tag}</span><span>{stages[stage].op}</span></div>
            <div className="pp-body">
              <PipeStageContent stage={stage} within={within} />
            </div>
          </div>
          <div className="pipe-brain">
            <BrainGraph stage={stage} />
          </div>
        </div>
      </section>
    </div>
  );
}

function PipeStageContent({ stage, within }) {
  // 0: raw source · 1: AST tree · 2: IR key/value · 3: emitted code
  if (stage === 0) {
    const src = `Button login = new Button("Sign in");\nlogin.setBackground(Color.web("#5b5bff"));\nlogin.setPrefSize(160, 40);\nlogin.setOnAction(this::handleLogin);`;
    const cap = Math.floor(src.length * (0.4 + within * 0.6));
    return <pre className="pp-raw">{src.slice(0, cap)}<span className="caret-bar">▍</span></pre>;
  }
  if (stage === 1) {
    return (
      <pre className="pp-ast">
        {`└─ Decl[Button login]
   ├─ Ctor("Sign in")
   ├─ Call setBackground
   │   └─ Color.web("#5b5bff")
   ├─ Call setPrefSize(160, 40)
   └─ EventBind onAction → handleLogin`}
      </pre>
    );
  }
  if (stage === 2) {
    return (
      <ul className="pp-ir">
        <li><span>n.type</span><span>Button</span></li>
        <li><span>n.text</span><span>"Sign in"</span></li>
        <li><span>backColor</span><span className="amber">#5b5bff</span></li>
        <li><span>size</span><span>160 × 40</span></li>
        <li><span>onClick</span><span>handleLogin</span></li>
      </ul>
    );
  }
  return (
    <pre className="pp-emit">
      {`var login = new Button {
  Text     = "Sign in",
  BackColor = ColorTranslator.FromHtml("#5b5bff"),
  Size     = new Size(160, 40),
};
login.Click += handleLogin;`}
    </pre>
  );
}

function BrainGraph({ stage }) {
  // 6 leaf nodes around a center, lines connect when stage >= 2
  const leaves = [
    { k: "n.type", v: "Button", x: 24, y: 20 },
    { k: "n.text", v: '"Sign in"', x: 76, y: 20 },
    { k: "backColor", v: "#5b5bff", x: 18, y: 50 },
    { k: "size", v: "160×40", x: 82, y: 50 },
    { k: "onClick", v: "handleLogin", x: 24, y: 80 },
    { k: "font", v: "Inter 14", x: 76, y: 80 }
  ];
  const lit = stage >= 2;
  return (
    <>
      <svg className="brain-svg" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
        {leaves.map((l, i) => (
          <line key={i} x1="50" y1="50" x2={l.x} y2={l.y}
            stroke={lit ? "#7a7aff" : "#2a2a3e"}
            strokeWidth="0.15"
            strokeDasharray={lit ? "none" : "0.8 0.8"}
            style={{ transition: "stroke 360ms ease" }} />
        ))}
      </svg>
      <div className="brain-node center" style={{ left: "50%", top: "50%" }}>
        AppMetadata
        <span className="bn-s">{stage < 2 ? "· idle" : stage === 2 ? "· building" : "· ready"}</span>
      </div>
      {leaves.map((l, i) => (
        <div key={i}
          className={"brain-node" + (lit ? " active" : "")}
          style={{ left: `${l.x}%`, top: `${l.y}%`, transitionDelay: `${i * 60}ms`, opacity: lit ? 1 : 0.35 }}>
          <span className="bn-k">{l.k}</span>
          <span className="bn-v">{l.v}</span>
        </div>
      ))}
    </>
  );
}

// ── Features ────────────────────────────────────────────────────────
function Features() {
  const feats = [
    {
      span: 7, no: "F.01 / 08", tag: "round-trip", ir: "F.01 · round-trip · bi-directional",
      title: 'Go <span class="scribble">either</span> way.',
      desc: "Both adapters implement parse() and generate(). Send JavaFX to C#, then back — byte-faithful on the shapes that matter. No one-way street."
    },
    {
      span: 5, no: "F.02 / 08", tag: "live preview", ir: "F.02 · preview · sandboxed JavaFX",
      title: 'See it <span class="scribble">before</span> you ship.',
      desc: "A dedicated preview-runner spawns a sandboxed JavaFX window with the converted UI. No build cycle, no copy-paste, no surprises."
    },
    {
      span: 4, no: "F.03 / 08", tag: "syntax HL", ir: "F.03 · syntaxHL · RichTextFX",
      title: "RICH SYNTAX.",
      desc: "RichTextFX-powered editor. Keyword, string, number, comment colour per language. The editor looks like the editor you actually use."
    },
    {
      span: 4, no: "F.04 / 08", tag: "file dialogs", ir: "F.04 · fileDialogs · per-adapter",
      title: "SMART FILTERS.",
      desc: "Open and Save As adapt to the selected adapter. .java when JavaFX is active, .cs/.cpp when WinForms — automatically."
    },
    {
      span: 4, no: "F.05 / 08", tag: "options", ir: "F.05 · options · declarative",
      title: "PER-ADAPTER KNOBS.",
      desc: "Match WinForms default font. Skip fonts not installed locally. Each adapter declares its own options; the Settings panel adapts to match."
    },
    {
      span: 6, no: "F.06 / 08", tag: "ai assist", ir: "F.06 · AI · prompt→UI · improve()",
      title: 'PROMPT → UI. CODE → <span class="scribble">better</span> code.',
      desc: "Describe a layout in plain English, get framework-native code back. Or highlight a region and ask for a clean-up. Single prompt, single response — no chat history. Bring your own key."
    },
    {
      span: 6, no: "F.07 / 08", tag: "one IR", ir: "F.07 · IR · 1 graph, ∞ frameworks",
      title: 'NO N×N <span class="scribble">explosion.</span>',
      desc: "Every framework speaks to one shared graph. Adding a new framework is one file — not a forest of N×(N−1) bespoke converters."
    },
    {
      span: 12, no: "F.08 / 08", tag: "MIT · local · zero telemetry", ir: "F.08 · MIT · local · 0 telemetry",
      title: 'OPEN SOURCE. <span class="scribble">yours</span> TO FORK.',
      desc: "No cloud, no account, no analytics. Source on GitHub. Hackable, embeddable in your own dev tools. The adapter contract is five methods long."
    }
  ];
  return (
    <section className="section shell" id="features" data-screen-label="03 Features">
      <SectionHead
        num="03"
        name="THE INVENTORY"
        title='SMALL APP. <span class="scribble">loud</span> SUPERPOWERS.'
        lede="UIPorter is one window — but every pane is doing real work. Parsing, previewing, prompting, persisting."
      />
      <div className="features-grid">
        {feats.map((f, i) => (
          <Compile key={i} ir={f.ir} delay={i * 60} className={`feat feat-${f.span}`}>
            <div className="feat-inner">
              <div className="feat-no"><span>{f.no}</span><span className="tag">{f.tag}</span></div>
              <h3 dangerouslySetInnerHTML={{ __html: f.title }} />
              <p className="feat-desc">{f.desc}</p>
            </div>
          </Compile>
        ))}
      </div>
    </section>
  );
}

// ── Gallery ─────────────────────────────────────────────────────────
function Gallery() {
  const [sel, setSel] = useState(0);
  const [forward, setForward] = useState(true);
  const [transitionKey, setTransitionKey] = useState(0);
  const samples = window.SAMPLES.filter(s => s.pairs.find(p => p.from === "JavaFX") && s.pairs.find(p => p.from === "WinForms"));
  const cur = samples[sel];
  const javaFx = cur.pairs.find(p => p.from === "JavaFX");
  const winForms = cur.pairs.find(p => p.from === "WinForms");
  const fromPair = forward ? javaFx : winForms;
  const toPair = forward ? winForms : javaFx;

  const change = (fn) => {
    setTransitionKey(k => k + 1);
    fn();
  };

  return (
    <section className="section shell" id="gallery" data-screen-label="04 Gallery">
      <SectionHead
        num="04"
        name="RECEIPTS"
        title='PICK A SNIPPET. <span class="scribble">watch</span> IT PORT.'
        lede="Real outputs from the converter — not pretty pictures. Click any pair on the left, then flip the direction."
      />

      <div className="gallery">
        <div className="gallery-list">
          {samples.map((s, i) => (
            <button key={s.id}
              className={"gal-item" + (i === sel ? " active" : "")}
              onClick={() => change(() => { setSel(i); setForward(true); })}>
              <span className="gi-no">EX · 0{i + 1}</span>
              <span className="gi-title">{s.title}</span>
              <span className="gi-arrow">{s.desc}</span>
            </button>
          ))}
        </div>

        <div className="gal-stage">
          <div className="gal-stage-head">
            <span className="title">{cur.title}</span>
            <button className="flip-btn" onClick={() => change(() => setForward(f => !f))}>⇄ FLIP DIRECTION</button>
          </div>
          <div className="gal-stage-body" key={transitionKey}>
            <div className="gal-pane from gal-dissolve">
              <span className="gp-label"><span>FROM</span><span className="b">{fromPair.from} · {fromPair.ext}</span></span>
              {fromPair.code.map((t, k) => (
                <span key={k} className={t.c || "tok-p"} style={{ animationDelay: `${k * 8}ms` }}>{t.t}</span>
              ))}
            </div>
            <div className="gal-pane to gal-dissolve to-side">
              <span className="gp-label"><span>TO</span><span className="b">{toPair.from} · {toPair.ext}</span></span>
              {toPair.code.map((t, k) => (
                <span key={k} className={t.c || "tok-p"} style={{ animationDelay: `${180 + k * 10}ms` }}>{t.t}</span>
              ))}
            </div>
            <div className="gal-bridge">
              <span>↳ IR ↲</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

// ── Naive web visualization (5 nodes, all pairs connected) ─────────
function NaiveWeb({ fwks }) {
  // Position 5 nodes evenly on a circle
  const cx = 50, cy = 52, r = 34;
  const pts = fwks.map((label, i) => {
    const a = (-Math.PI / 2) + (i * 2 * Math.PI / fwks.length);
    return { label, x: cx + r * Math.cos(a), y: cy + r * Math.sin(a) };
  });
  // All unordered pairs
  const pairs = [];
  for (let i = 0; i < pts.length; i++) {
    for (let j = i + 1; j < pts.length; j++) pairs.push([i, j]);
  }
  return (
    <div className="naive-web">
      <div className="nw-count">
        <span>converters</span>
        <span className="big">20</span>
        <span>and growing</span>
      </div>
      <svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
        <defs>
          <marker id="nw-arrow" viewBox="0 0 8 8" refX="6" refY="4" markerWidth="5" markerHeight="5" orient="auto">
            <path d="M0,0 L8,4 L0,8 z" fill="rgba(255,122,168,0.55)" />
          </marker>
        </defs>
        {pairs.map(([i, j], k) => {
          const a = pts[i], b = pts[j];
          // Slight curve so two directions are visible
          const mx = (a.x + b.x) / 2;
          const my = (a.y + b.y) / 2;
          const dx = b.x - a.x, dy = b.y - a.y;
          const nx = -dy, ny = dx;
          const len = Math.hypot(nx, ny) || 1;
          const off = 1.6;
          const c1x = mx + (nx / len) * off;
          const c1y = my + (ny / len) * off;
          const c2x = mx - (nx / len) * off;
          const c2y = my - (ny / len) * off;
          return (
            <g key={k}>
              <path d={`M${a.x},${a.y} Q${c1x},${c1y} ${b.x},${b.y}`} fill="none" stroke="rgba(255,122,168,0.42)" strokeWidth="0.35" markerEnd="url(#nw-arrow)" />
              <path d={`M${b.x},${b.y} Q${c2x},${c2y} ${a.x},${a.y}`} fill="none" stroke="rgba(255,122,168,0.42)" strokeWidth="0.35" markerEnd="url(#nw-arrow)" />
            </g>
          );
        })}
      </svg>
      {pts.map((p, i) => (
        <div key={i} className="nw-node" style={{ left: `${p.x}%`, top: `${p.y}%` }}>{p.label}</div>
      ))}
      <div className="nw-formula"><b>n × (n−1)</b> = 20 EDGES</div>
    </div>
  );
}

// ── Why a canonical IR? ─────────────────────────────────────────────
function WhyIR() {
  // 5x5 matrix — diagonal cells are self→self (skipped)
  const fwks = ["JavaFX", "WinForms", "Tkinter", "ImGUI", "Qt"];
  const matrix = [];
  for (let r = 0; r < 5; r++) {
    for (let c = 0; c < 5; c++) {
      matrix.push({ r, c, diag: r === c });
    }
  }
  const ours = fwks;
  return (
    <section className="section shell why-ir" data-screen-label="02b Why IR">
      <SectionHead
        num="02·b"
        name="THE ARCHITECTURAL BET"
        title='WHY A <span class="scribble">canonical</span> IR?'
        lede="Most converters are pairs of point-to-point translators. That works for two frameworks. At five it's a swamp. UIPorter routes everything through one neutral graph — so adding a framework is linear, not quadratic."
      />
      <div className="why-grid">
        <Compile ir="naive · N×(N−1) translators · explodes" className="why-card naive">
          <div className="why-inner">
            <div className="why-tag"><span>OPT · A</span><span className="bad">NAIVE PAIRS</span></div>
            <h3>EVERY FRAMEWORK<br />WIRED TO EVERY OTHER.</h3>
            <p>5 frameworks → <strong>20 converters</strong>. 10 → 90. Quadratic. Each new framework retroactively makes every existing one harder.</p>
            <NaiveWeb fwks={fwks} />
            <div className="why-foot"><span>n × (n−1) = O(n²)</span><span className="bad">REJECTED</span></div>
          </div>
        </Compile>

        <Compile ir="hub · 1 IR · n adapters · linear growth" className="why-card hub" delay={120}>
          <div className="why-inner">
            <div className="why-tag"><span>OPT · B</span><span className="good">THE PICKED PATH</span></div>
            <h3>EVERY FRAMEWORK SPEAKS<br />TO <span className="acc">ONE BRAIN.</span></h3>
            <p>5 frameworks → <strong>5 adapters</strong>. 10 → 10. Linear. A new framework slots in. The other adapters never learn it exists.</p>
            <div className="hub-diagram">
              <div className="hub-count">
                <span>adapters</span>
                <span className="big">5</span>
                <span>1 per framework</span>
              </div>
              <svg className="hub-svg" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
                <defs>
                  <radialGradient id="hub-glow" cx="50%" cy="52%" r="38%">
                    <stop offset="0%" stopColor="rgba(122,122,255,0.18)" />
                    <stop offset="100%" stopColor="rgba(122,122,255,0)" />
                  </radialGradient>
                </defs>
                <circle cx="50" cy="52" r="42" fill="url(#hub-glow)" />
                {ours.map((o, i) => {
                  const angle = (i / ours.length) * Math.PI * 2 - Math.PI / 2;
                  const x = 50 + Math.cos(angle) * 34;
                  const y = 52 + Math.sin(angle) * 34;
                  return <line key={o} x1="50" y1="52" x2={x} y2={y} stroke="rgba(122,122,255,0.55)" strokeWidth="0.4" strokeDasharray="1.4 0.9" />;
                })}
              </svg>
              <div className="hub-core" aria-label="canonical IR">
                <div>
                  <div className="hub-core-l">IR</div>
                  <div className="hub-core-s">AppMetadata</div>
                </div>
              </div>
              {ours.map((o, i) => {
                const angle = (i / ours.length) * Math.PI * 2 - Math.PI / 2;
                const x = 50 + Math.cos(angle) * 34;
                const y = 52 + Math.sin(angle) * 34;
                return (
                  <div key={o} className="hub-leaf" style={{ left: `${x}%`, top: `${y}%` }}>
                    <span>{o}</span>
                  </div>
                );
              })}
              <div className="hub-formula"><b>n adapters</b> = O(n)</div>
            </div>
            <div className="why-foot"><span>n adapters = O(n)</span><span className="good">SHIPPED</span></div>
          </div>
        </Compile>
      </div>
    </section>
  );
}

// ── Adapters ────────────────────────────────────────────────────────
function Adapters() {
  const tiles = [
    { no: "A.01", name: "JavaFX", note: "Java 21 · OpenJFX runtime", ext: ".java", status: "shipped" },
    { no: "A.02", name: "Fxml", note: "Declarative UI · XML based", ext: ".fxml", status: "shipped" },
    { no: "A.03", name: "WinForms", note: "C# + C++/CLI", ext: ".cs / .cpp", status: "shipped" },
    { no: "A.04", name: "Tkinter", note: "Python · in design", ext: ".py", status: "planned" },
    { no: "A.05", name: "Dear ImGUI", note: "C++ · immediate-mode", ext: ".cpp", status: "planned" },
    { no: "A.06", name: "Qt Widgets", note: "C++ / PyQt · PRs welcome", ext: ".cpp / .py", status: "planned" },
    { no: "A.07", name: "YOURS", note: "five methods · one register line", ext: ".???", status: "empty" }
  ];

  const sample = `package com.jabcodex.uiporter.adapters;

public class YourAdapter implements FrameworkAdapter {

  public String   getDisplayName()    { return "Your Framework"; }
  public String[] getFileExtensions() { return new String[]{".xx"}; }
  public String   getSyntaxLanguage() { return "your-lang"; }

  public AppMetadata parse(String source) {
    // walk widgets · normalise types · colours → hex
    return app;
  }

  public String generate(AppMetadata app, Map<String,Object> opts) {
    // emit idiomatic widgets in your dialect
    return sb.toString();
  }
}

// one line in App.java:
registry.register(new YourAdapter());`;

  return (
    <section className="section shell" id="adapters">
      <SectionHead
        num="05"
        name="THE PLUGIN CONTRACT"
        title='NEW FRAMEWORK. <span class="scribble">one</span> FILE.'
        lede="The shipped adapters are the same shape as your future one. Implement five methods. Register one line. Your framework now appears in both dropdowns, the file dialogs adapt, the syntax highlighter follows. No core edits required."
      />

      <div className="adapt-grid">
        <div className="adapt-tiles">
          {tiles.map((t, i) => (
            <Compile key={t.name} ir={`A.${t.no.slice(2)} · ${t.name.toLowerCase()} · status=${t.status}`} delay={i * 60} className={"adapt-tile " + (t.status === "shipped" ? "ready" : "")}>
              <div className="at-inner">
                <span className="at-no">{t.no}</span>
                <div>
                  <div className="at-name">
                    {t.name === "YOURS" ? <><em>your</em> adapter</> : t.name}
                  </div>
                  <div className="at-meta">
                    <span className="ext">{t.ext}</span>
                    <span>{t.note}</span>
                  </div>
                </div>
                <span className={"at-status " + t.status}>
                  {t.status === "shipped" ? "● shipped"
                    : t.status === "planned" ? "◐ planned"
                      : "○ empty slot"}
                </span>
              </div>
            </Compile>
          ))}
        </div>

        <div className="code-block">
          <div className="cb-head">
            <span>adapters/<span className="b">YourAdapter.java</span></span>
            <span>EXAMPLE · 21 LINES</span>
          </div>
          <pre><AdapterCode src={sample} /></pre>
        </div>
      </div>
    </section>
  );
}

function AdapterCode({ src }) {
  const KEYS = ["package", "public", "class", "implements", "return", "new", "import"];
  const TYPES = ["String", "AppMetadata", "Map", "FrameworkAdapter", "YourAdapter", "Object"];
  const out = [];
  const re = /("[^"]*"|\/\/[^\n]*|\b\w+\b|[^\w"]+)/g;
  let m, k = 0;
  while ((m = re.exec(src)) !== null) {
    const w = m[0];
    if (w.startsWith("//")) out.push(<span key={k++} className="tok-c">{w}</span>);
    else if (w.startsWith('"')) out.push(<span key={k++} className="tok-s">{w}</span>);
    else if (KEYS.includes(w)) out.push(<span key={k++} className="tok-k">{w}</span>);
    else if (TYPES.includes(w)) out.push(<span key={k++} className="tok-t">{w}</span>);
    else out.push(<span key={k++}>{w}</span>);
  }
  return <>{out}</>;
}

// ── AI ──────────────────────────────────────────────────────────────
function AI() {
  return (
    <section className="section shell" id="ai" data-screen-label="07 AI">
      <SectionHead
        num="06"
        name="AI ASSIST · OPTIONAL"
        title='THREE AI MOVES. <span class="scribble">all</span> CONTEXTUAL.'
        lede="The AI doesn't replace the converter — it surrounds it. Prompt new UI into existence, refine what's already there, or ask a one-off question about your code. Each request is a single prompt with a single response — no persistent chat. Disable it entirely if you'd rather not."
      />
      <AIPromptDemo />
      <div className="ai-grid">
        <Compile ir="AI.02 · improve() · in-place rewrite" delay={120} className="ai-card">
          <div className="ai-inner">
            <div className="ai-head">
              <span className="ai-tag">AI · 02</span>
              <h3>IMPROVE <span className="scribble">code</span></h3>
            </div>
            <p className="ai-desc">Highlight a region, tell it what's wrong. The AI rewrites in-place — colours normalised, names sane, intent preserved.</p>
            <div className="ai-mock">
              <span className="prompt">Make this button match the rest of the form and fix the magic numbers.</span>
              <span className="out-line">// renamed magic 91,91,255 → THEME_INDIGO</span>
              <span className="out-line">login.BackColor = Theme.Indigo;</span>
              <span className="out-line">login.Size = Theme.PrimarySize;</span>
            </div>
          </div>
        </Compile>
        <Compile ir="AI.03 · ask · single-prompt Q&A" delay={240} className="ai-card">
          <div className="ai-inner">
            <div className="ai-head">
              <span className="ai-tag">AI · 03</span>
              <h3>ASK <span className="scribble">once</span></h3>
            </div>
            <p className="ai-desc">Type a question about your code, get one focused answer back. The AI sees your current file and target framework — no re-explaining needed. One prompt in, one response out.</p>
            <div className="ai-mock">
              <span className="prompt">Why did my GridPane lose its gaps in the C# output?</span>
              <span className="out-line">TableLayoutPanel uses Margin not Hgap.</span>
              <span className="out-line">Re-emitted with grid.Margin = (12,8,12,8).</span>
              <span className="out-line">Round-trip now stable.</span>
            </div>
          </div>
        </Compile>
      </div>
    </section>
  );
}

function AIPromptDemo() {
  const ref = uR(null);
  const PROMPT = "a glassmorphic settings card with a dark blue tint, a single text field, a toggle, and a primary save button";
  const [typed, setTyped] = useState("");
  const [phase, setPhase] = useState("idle"); // idle, typing, generating, done
  const [genStep, setGenStep] = useState(0);

  uE(() => {
    if (!ref.current) return;
    const io = new IntersectionObserver((entries) => {
      entries.forEach(e => {
        if (e.isIntersecting && phase === "idle") setPhase("typing");
      });
    }, { threshold: 0.35 });
    io.observe(ref.current);
    return () => io.disconnect();
  }, [phase]);

  uE(() => {
    if (phase !== "typing") return;
    let i = 0;
    const tk = setInterval(() => {
      i++;
      setTyped(PROMPT.slice(0, i));
      if (i >= PROMPT.length) {
        clearInterval(tk);
        setTimeout(() => setPhase("generating"), 380);
      }
    }, 24);
    return () => clearInterval(tk);
  }, [phase]);

  uE(() => {
    if (phase !== "generating") return;
    let s = 0;
    const tk = setInterval(() => {
      s++;
      setGenStep(s);
      if (s >= 5) { clearInterval(tk); setPhase("done"); }
    }, 320);
    return () => clearInterval(tk);
  }, [phase]);

  const outLines = [
    { c: "tok-c", t: "// AppMetadata · 4 nodes · target = JavaFX\n" },
    { c: "tok-k", t: "VBox" }, { c: "tok-p", t: " card = " }, { c: "tok-k", t: "new" }, { c: "tok-p", t: " VBox(12);\n" },
    { c: "tok-p", t: "card.setStyle(" }, { c: "tok-s", t: '"-fx-background-color:#1f1f2eCC;"' }, { c: "tok-p", t: ");\n" },
    { c: "tok-p", t: "card.getChildren().addAll(usernameField, syncToggle, saveBtn);" }
  ];

  return (
    <div className="ai-demo" ref={ref}>
      <div className="ai-demo-grid">
        <div className="ai-demo-prompt">
          <div className="adp-head">
            <span>AI · 01</span>
            <span className="adp-arrow">PROMPT → UI</span>
          </div>
          <div className="adp-input">
            <span className="adp-prefix">▸</span>
            <span className="adp-typing">{typed}</span>
            {phase === "typing" && <span className="caret-bar">▍</span>}
          </div>
          <div className="adp-steps">
            <div className={"adp-step" + (phase === "generating" && genStep >= 1 ? " on" : "") + (genStep >= 2 ? " done" : "")}>
              <span className="sn">1</span><span className="sl">Parse the prompt</span><span className="st">NLU</span>
            </div>
            <div className={"adp-step" + (genStep >= 2 ? " on" : "") + (genStep >= 3 ? " done" : "")}>
              <span className="sn">2</span><span className="sl">Call Gemini API</span><span className="st">GEMINI</span>
            </div>
            <div className={"adp-step" + (genStep >= 3 ? " on" : "") + (genStep >= 4 ? " done" : "")}>
              <span className="sn">3</span><span className="sl">Receive JavaFX code</span><span className="st">OUTPUT</span>
            </div>
            <div className={"adp-step" + (genStep >= 4 ? " on" : "") + (genStep >= 5 ? " done" : "")}>
              <span className="sn">4</span><span className="sl">Apply to project</span><span className="st">EMIT</span>
            </div>
          </div>
        </div>

        <div className="ai-demo-out">
          <div className="ado-tabs">
            <div className={"ado-tab" + (phase === "done" ? " on" : "")}>RENDERED</div>
            <div className="ado-tab">CODE · JavaFX</div>
            <div className="ado-meta">{phase === "done" ? "READY" : phase === "generating" ? "GENERATING…" : "WAITING"}</div>
          </div>
          <div className="ado-stage">
            <div className={"glass-card" + (phase === "done" ? " materialised" : "")}>
              <div className="gc-label">SETTINGS</div>
              <div className="gc-field">
                <span className="gc-fl">Username</span>
                <div className="gc-fi"></div>
              </div>
              <div className="gc-toggle">
                <span className="gc-fl">Sync to cloud</span>
                <div className="gc-tg"><div className="gc-tg-dot"></div></div>
              </div>
              <button className="gc-btn"><span>Save</span></button>
            </div>
            {phase !== "done" && <div className="ado-skeleton" aria-hidden="true">
              <div className="sk sk-1"></div><div className="sk sk-2"></div>
              <div className="sk sk-3"></div><div className="sk sk-4"></div>
            </div>}
          </div>
          <div className="ado-code">
            {outLines.map((l, i) => (
              <span key={i} className={l.c} style={{ opacity: phase === "done" ? 1 : 0, transition: `opacity 240ms ease ${i * 60}ms` }}>{l.t}</span>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Install ─────────────────────────────────────────────────────────
function Install() {
  return (
    <section className="install" id="install">
      <div className="shell" style={{ position: "relative" }}>
        <span className="label bracket"><span className="dot"></span>GET STARTED · 30 SECONDS</span>
        <h2>THREE STEPS. <span className="scribble">zero</span> SETUP.</h2>
        <p>Make sure you have JDK installed, grab the installer from GitHub releases, and you're running in under a minute. No databases, no servers, no SaaS sign-up.</p>

        <div className="install-steps">
          <div className="step">
            <div className="n">STEP · i</div>
            <h4>JDK</h4>
            <p className="desc">Install JDK (preferably 25) — required for the live preview to work correctly.</p>
            <code><a href="https://adoptium.net/" target="_blank" rel="noopener" className="step-link">java --version → 25.x recommended</a></code>
          </div>
          <div className="step">
            <div className="n">STEP · ii</div>
            <h4>DOWNLOAD</h4>
            <p className="desc">Go to the latest release on GitHub and grab the UIPorter setup.</p>
            <code><a href="https://github.com/JunaidIRF/UIPorter/releases/latest" target="_blank" rel="noopener" className="step-link">github.com/Jabcodex/UIPorter/releases/latest</a></code>
          </div>
          <div className="step">
            <div className="n">STEP · iii</div>
            <h4>INSTALL</h4>
            <p className="desc">Run the setup. UIPorter installs like any desktop app. Launch and convert.</p>
            <code><a href="#install" className="step-link">UIPorter-Setup.exe → Install → Run</a></code>
          </div>
        </div>

        <div style={{ marginTop: 48 }}>
          <a className="btn btn-primary" href="https://github.com/JunaidIRF/UIPorter/releases/latest" target="_blank" rel="noopener">
            <GhIcon />DOWNLOAD LATEST RELEASE →
          </a>
        </div>
      </div>
    </section>
  );
}

// ── Footer ──────────────────────────────────────────────────────────
function Foot() {
  return (
    <footer className="foot shell">
      <div className="foot-grid">
        <div>
          <div className="foot-brand"><img src="icon.png" className="foot-brand-icon" alt="UIPorter" />UI<span className="acc">PORTER.</span></div>
          <p className="foot-brand-sub">A pluggable UI-code converter for JavaFX and WinForms. Adapters are five methods long.</p>
        </div>
        <div>
          <h5>§ PRODUCT</h5>
          <a href="#features">Features</a>
          <a href="#how">Architecture</a>
          <a href="#gallery">Examples</a>
          <a href="#ai">AI assist</a>
        </div>
        <div>
          <h5>§ DEVELOP</h5>
          <a href="#adapters">Adapters</a>
          <a href="https://github.com/JunaidIRF/UIPorter" target="_blank" rel="noopener">Source</a>
          <a href="https://github.com/JunaidIRF/UIPorter/issues" target="_blank" rel="noopener">Open issues</a>
          <a href="https://github.com/JunaidIRF/UIPorter/pulls" target="_blank" rel="noopener">Contribute</a>
        </div>
        <div>
          <h5>§ PROJECT</h5>
          <a href="#install">Download</a>
          <a href="https://github.com/JunaidIRF/UIPorter/releases" target="_blank" rel="noopener">Releases</a>
          <a href="https://github.com/JunaidIRF/UIPorter/blob/main/LICENSE" target="_blank" rel="noopener">License · MIT</a>
          <a href="https://github.com/JunaidIRF" target="_blank" rel="noopener">@Jabcodex</a>
        </div>
      </div>
      <div className="foot-bottom">
        <span>© 2026 Jabcodex · BUILT IN JAVAFX · SHIPPED IN 21ST CENTURY JAVA</span>
        <span>MIT</span>
      </div>
    </footer>
  );
}

Object.assign(window, { IRSection, WhyIR, Features, Gallery, Adapters, AI, Install, Foot });
