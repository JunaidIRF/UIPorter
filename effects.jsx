// ═══════════════════════════════════════════════════════════════════
// Effects — IR-assembly scroll reveals + scroll-jack hook
// ═══════════════════════════════════════════════════════════════════

const { useState: uS, useEffect: uE, useRef: uR } = React;

// ── <Compile ir="…"> ────────────────────────────────────────────────
// Wrap any block. Pre-state: shows IR shorthand (mono).
// On scroll-in: tokens shimmer, IR collapses, real content materialises.
function Compile({ ir, children, delay = 0, className = "" }) {
  const [state, setState] = uS("pre"); // pre → compiling → done
  const ref = uR(null);

  uE(() => {
    if (!ref.current) return;
    const io = new IntersectionObserver((entries) => {
      entries.forEach(e => {
        if (e.isIntersecting && state === "pre") {
          setTimeout(() => setState("compiling"), delay);
          setTimeout(() => setState("done"), delay + 650);
        }
      });
    }, { threshold: 0.18 });
    io.observe(ref.current);
    return () => io.disconnect();
  }, [state, delay]);

  return (
    <div ref={ref} className={`compile compile-${state} ${className}`}>
      <div className="compile-ir" aria-hidden="true">
        <span className="ir-brace">{`{`}</span>
        <span className="ir-text">{ir}</span>
        <span className="ir-brace">{`}`}</span>
      </div>
      <div className="compile-out">{children}</div>
    </div>
  );
}

// ── <StaggerGroup> ──────────────────────────────────────────────────
// Apply ascending compile delays to children.
function StaggerGroup({ children, baseDelay = 0, step = 90 }) {
  return React.Children.map(children, (child, i) => {
    if (!React.isValidElement(child)) return child;
    return React.cloneElement(child, { delay: baseDelay + i * step });
  });
}

// ── useScrollProgress(ref) ──────────────────────────────────────────
// Returns 0..1 progress of element through viewport
function useScrollProgress(ref) {
  const [p, setP] = uS(0);
  uE(() => {
    const tick = () => {
      const el = ref.current;
      if (!el) return;
      const r = el.getBoundingClientRect();
      const vh = window.innerHeight;
      const total = r.height + vh;
      const passed = vh - r.top;
      setP(Math.max(0, Math.min(1, passed / total)));
    };
    tick();
    window.addEventListener("scroll", tick, { passive: true });
    window.addEventListener("resize", tick);
    return () => {
      window.removeEventListener("scroll", tick);
      window.removeEventListener("resize", tick);
    };
  }, []);
  return p;
}

Object.assign(window, { Compile, StaggerGroup, useScrollProgress });
