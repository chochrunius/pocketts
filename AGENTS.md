# Pokyny pro agenty

- Toto je osobní fork PocketTS. Zachovávej upstream architekturu a existující funkčnost.
- Preferuj malé, cílené změny před refaktoringem. Znovu používej existující implementace.
- Dodržuj postupy v [DEVELOPING.md](DEVELOPING.md) a odpovídající kontroly v [docs/TESTING.md](docs/TESTING.md). Tyto dokumenty zde neduplikuj.
- Hostitelem je Windows 11; WSL2 a Docker jsou povoleny. Preferuj dokumentovaný WSL/Docker workflow.
- Nikdy nezasahuj do nesouvisejících WSL distribucí, Docker prostředků, projektů ani služeb.
- Zachovávej nesouvisející soubory, necommitovanou práci a historii Gitu.
- Chraň tajné údaje, přihlašovací údaje a podpisové klíče; nezveřejňuj je ve výstupech ani je neukládej do Gitu.
- Změny přiměřeně otestuj podle dokumentace. Rozlišuj automatické testy od ověření na telefonu.
- Automaticky neprováděj commity, push, tagy, releasy ani nasazení.
- Komunikuj česky, stručně a pragmaticky. U produkce rozlišuj analýzu, návrh a skutečně provedené změny; připrav rollback, kde je relevantní.
- Uveď provedené změny, výsledky testů, zbývající omezení a sekvenci příkazů pro reprodukci postupu.
