# Davebook ❤️

Eine kleine App nur für David & Sophie.

- **Herz** – auf das große Herz tippen, beim anderen erscheint „David denkt an dich ❤️“ bzw. „Sophie denkt an dich ❤️“.
- **Tagesspruch** – jeder kann dem anderen einen Spruch dalassen, der ganz oben auf dessen Startseite steht (mit Ideen-Knopf 💡).
- **Gemeinsamer Kalender** – Termine, Dates und Jahrestage eintragen, mit Countdown und Benachrichtigung beim anderen.
- **Erinnerungen** – jeder kann für den anderen wiederkehrende Erinnerungen anlegen (z. B. „Genug getrunken? 💧“ alle 8 Std, nachts stumm). Sie kommen als Benachrichtigung auf dem Handy des anderen, auch wenn die App zu ist.
- **Sudoku** – eigener Tab mit einem kleinen Sudoku: Leicht, Mittel und Schwer, Notizen, Tipps, 3 Herzen pro Sudoku, Bestzeiten. Gleiche Nummer = gleiches Sudoku auf beiden Handys, ihr könnt also Zeiten vergleichen.
- **Sophies Setup** – „Willkommen bei Davebook mein Hase ❤️“ → „Wie sehr liebst du David?“ (Unfassbar / Unendlich / Unbeschreiblich).

## Installieren

1. Auf beiden Handys öffnen: **https://github.com/dave6647/davebook/releases/latest/download/Davebook.apk**
2. Herunterladen, öffnen und „Installation aus unbekannten Quellen“ für den Browser erlauben.
3. **Sophie** öffnet die App und geht durchs Setup. Am Ende zeigt ihr Handy einen 6-stelligen Code.
4. **David** öffnet die App, tippt unten auf „Ich bin David“ und gibt den Code ein. Fertig.

Tipp: In der App auf „Erlauben“ bei der Akku-Karte tippen, damit Herzen auch bei gesperrtem Handy sofort ankommen.

## Wie es funktioniert

- Kein Konto, kein Server: Die Handys tauschen sich über [ntfy.sh](https://ntfy.sh) aus, einen kostenlosen Push-Dienst.
- Alles ist mit AES-256 verschlüsselt. Der Schlüssel wird beim Koppeln per Code übertragen und existiert nur auf euren beiden Handys.
- Die Daten liegen auf beiden Handys. Wer länger offline war, bekommt beim nächsten Verbinden automatisch den Stand vom anderen.
- Ein kleiner Hintergrunddienst hält die Verbindung offen. Android verlangt dafür eine Benachrichtigung; die App führt mit „Ausblenden“ direkt zum Schalter „Hintergrund-Verbindung“, danach ist sie weg. Ob ihr verbunden seid, zeigt die App oben auf der Startseite.

## Bauen

Jeder Push auf `main` baut über GitHub Actions eine neue APK und legt sie als Release ab. Die Versionsnummer steigt automatisch, Updates lassen sich einfach drüberinstallieren.

## Sudoku

Aufgebaut wie Pfeilfrei: Das ganze Spiel liegt in `web/sudoku.html` (HTML, CSS, JavaScript), die App zeigt es im Tab „Sudoku“ in einer WebView (`ui/SudokuScreen.kt`).

| Pfad | Inhalt |
| --- | --- |
| `web/sudoku.html` | Das komplette Spiel. Die Sudoku-Erzeugung steht zwischen `// ENGINE START` und `// ENGINE END`. |
| `web/fonts/` | Schrift Overpass (SIL Open Font License), wird eingebettet. |
| `tools/build_sudoku.py` | Erzeugt daraus `app/src/main/assets/sudoku.html` für die App. |
| `tools/test_sudoku.js` | Prüft: genau eine Lösung, ohne Raten lösbar, Schwierigkeit stimmt, Nr. 1–20 unverändert. |

- Sudokus werden aus Schwierigkeit und Nummer erzeugt und sind immer gleich. Leicht (≈ 38 Vorgaben) und Mittel (≈ 30) gehen nur mit „hier passt nur eine Zahl“, Schwer (≈ 26) braucht zusätzlich Paare oder Zeiger. Geraten werden muss nie.
- Ändert sich die Erzeugung, `GEN` erhöhen und die neue Prüfsumme in `tools/test_sudoku.js` eintragen. Angefangene Sudokus laufen weiter, weil sie ihr Feld selbst speichern.
- Spielstand liegt nur auf dem Handy (SharedPreferences `sudoku`), er wird nicht synchronisiert.
- Nach Änderungen: `python3 tools/build_sudoku.py` und `node tools/test_sudoku.js` (macht der GitHub-Build auch).
- Im Browser testen: `app/src/main/assets/sudoku.html` öffnen (Spielstand dann im `localStorage`).
