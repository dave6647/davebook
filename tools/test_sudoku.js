// Prüft die Sudoku-Erzeugung in web/sudoku.html (Teil zwischen // ENGINE START und // ENGINE END).
// Läuft bei jedem GitHub-Build; schlägt ein Test fehl, wird keine APK gebaut.
//
//   node tools/test_sudoku.js        (prüft je Schwierigkeit Nr. 1–60)
//   node tools/test_sudoku.js 300    (mehr Sudokus)
//
// Geprüft wird:
// - jedes Sudoku hat genau eine Lösung, und die passt zu den Vorgaben
// - es ist ohne Raten lösbar, und die Schwierigkeit stimmt (Leicht/Mittel: nur Singles, Schwer: braucht mehr)
// - gleiche Nummer = gleiches Sudoku (auf beiden Handys)
// - Nr. 1–20 bleiben gleich, solange GEN gleich bleibt (Prüfsumme unten). Ändert sich die Erzeugung absichtlich,
//   GEN in web/sudoku.html erhöhen und die neue Prüfsumme hier eintragen.
const fs = require('fs');
const src = fs.readFileSync(__dirname + '/../web/sudoku.html', 'utf8');
const block = src.split('// ENGINE START')[1].split('// ENGINE END')[0];
const E = new Function(block + '; return { GEN, DIFFS, generatePuzzle, solve, logicSolve, UNITS };')();

const CHECKSUMS = { 1: 'f8b5eceb' };

const COUNT = Math.max(1, parseInt(process.argv[2], 10) || 60);
const errors = [];
const fnv = s => { let h = 2166136261; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return (h >>> 0).toString(16).padStart(8, '0'); };

for (const d of Object.keys(E.DIFFS)) {
  const spec = E.DIFFS[d];
  let slow = 0, sumGivens = 0, minG = 81, maxG = 0;
  for (let n = 1; n <= COUNT; n++) {
    const t = Date.now();
    const p = E.generatePuzzle(d, n);
    const ms = Date.now() - t;
    slow = Math.max(slow, ms);
    const tag = `${d} Nr. ${n}`;
    if (p.diff !== d) { errors.push(`${tag}: Ersatz aus "${p.diff}" statt eigener Schwierigkeit`); continue; }
    if (p.puzzle.length !== 81 || p.solution.length !== 81) { errors.push(`${tag}: falsche Länge`); continue; }
    // Lösung gültig und passend zu den Vorgaben
    for (const u of E.UNITS) if (new Set(u.map(i => p.solution[i])).size !== 9) { errors.push(`${tag}: Lösung ungültig`); break; }
    for (let i = 0; i < 81; i++) if (p.puzzle[i] && p.puzzle[i] !== p.solution[i]) { errors.push(`${tag}: Vorgabe passt nicht zur Lösung`); break; }
    // Genau eine Lösung
    const sol = E.solve(p.puzzle, 2);
    if (sol.count !== 1) errors.push(`${tag}: ${sol.count} Lösungen`);
    // Ohne Raten lösbar, Schwierigkeit stimmt
    const r = E.logicSolve(p.puzzle, spec.logic);
    if (!r.solved) errors.push(`${tag}: nicht ohne Raten lösbar`);
    else if (r.values.join('') !== p.solution.join('')) errors.push(`${tag}: Logik findet eine andere Lösung`);
    if (r.level !== spec.logic) errors.push(`${tag}: Stufe ${r.level} statt ${spec.logic}`);
    const g = p.puzzle.filter(x => x).length;
    if (g !== p.givens) errors.push(`${tag}: givens stimmt nicht`);
    if (g > spec.givens + 4) errors.push(`${tag}: ${g} Vorgaben (zu viele für ${d})`);
    sumGivens += g; minG = Math.min(minG, g); maxG = Math.max(maxG, g);
    // Gleiche Nummer = gleiches Sudoku
    if (n <= 5 && E.generatePuzzle(d, n).puzzle.join('') !== p.puzzle.join('')) errors.push(`${tag}: nicht immer gleich`);
  }
  console.log(`${d.padEnd(6)} ${COUNT} Sudokus: Vorgaben ${minG}–${maxG} (Schnitt ${(sumGivens / COUNT).toFixed(1)}), langsamstes ${slow} ms`);
}

// Nr. 1–20 jeder Schwierigkeit unverändert
let all = '';
for (const d of Object.keys(E.DIFFS)) for (let n = 1; n <= 20; n++) all += E.generatePuzzle(d, n).puzzle.join('');
const sum = fnv(all);
if (!CHECKSUMS[E.GEN]) errors.push(`Keine Prüfsumme für GEN ${E.GEN}. Eintragen in tools/test_sudoku.js: ${E.GEN}: '${sum}'`);
else if (CHECKSUMS[E.GEN] !== sum) errors.push(`Nr. 1–20 haben sich geändert (Prüfsumme ${sum}). Absichtlich? Dann GEN erhöhen und hier die neue Prüfsumme eintragen.`);

if (errors.length) {
  console.error(`Sudoku-Test fehlgeschlagen (${errors.length}):\n- ` + errors.slice(0, 40).join('\n- '));
  process.exit(1);
}
console.log(`Sudoku OK (GEN ${E.GEN}, Prüfsumme ${sum})`);
