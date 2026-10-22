# Davebook ❤️

Eine kleine App nur für David & Sophie.

- **Herz** – auf das große Herz tippen, beim anderen erscheint „David denkt an dich ❤️“ bzw. „Sophie denkt an dich ❤️“.
- **Tagesspruch** – jeder kann dem anderen einen Spruch dalassen, der ganz oben auf dessen Startseite steht (mit Ideen-Knopf 💡).
- **Gemeinsamer Kalender** – Termine, Dates und Jahrestage eintragen, mit Countdown und Benachrichtigung beim anderen.
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
- Ein kleiner Hintergrunddienst hält die Verbindung offen (die stille Benachrichtigung „Davebook ist verbunden“ kann man in den Android-Einstellungen ausblenden).

## Bauen

Jeder Push auf `main` baut über GitHub Actions eine neue APK und legt sie als Release ab. Die Versionsnummer steigt automatisch, Updates lassen sich einfach drüberinstallieren.
