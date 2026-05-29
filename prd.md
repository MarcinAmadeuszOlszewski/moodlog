# PRD — Daily Journal z analizą nastroju

## 1. Przegląd projektu

| Pole | Wartość |
|---|---|
| Nazwa | MoodLog |
| Typ | Aplikacja webowa |
| Stack | Spring Boot + Thymeleaf + PostgreSQL + OpenAI API |
| Autor | — |
| Wersja PRD | 1.0 |
| Data | 2026-05-27 |

### Opis w jednym zdaniu
Użytkownik zapisuje codzienne wpisy do dziennika, a aplikacja automatycznie klasyfikuje nastrój, taguje wpis i wizualizuje trendy emocjonalne w czasie — pomagając dostrzegać wzorce, których samemu trudno zauważyć.

---

## 2. Problem i kontekst

Większość ludzi nie jest w stanie obiektywnie ocenić własnego stanu emocjonalnego w czasie. Prowadzenie dziennika pomaga, ale ręczna analiza wpisów jest żmudna i subiektywna. Brakuje prostego narzędzia, które:
- przyjmie swobodny tekst (bez formularzy i checkboxów),
- automatycznie rozpozna nastrój,
- pokaże trendy bez wysiłku ze strony użytkownika.

**Użytkownik docelowy:** osoba dorosła prowadząca dziennik lub dbająca o higienę mentalną — nie musi być techniczna.

---

## 3. Cele projektu (zakres kursu)

| # | Cel | Miara sukcesu |
|---|---|---|
| 1 | Działający przepływ MVP w pierwszym tygodniu | Użytkownik może wpisać notatkę i zobaczyć tag nastroju |
| 2 | Logika biznesowa oparta o AI | Każdy wpis ma przypisany nastrój z OpenAI API |
| 3 | Wizualizacja trendów | Wykres nastrojów za ostatnie 7 / 30 dni |
| 4 | Kontrola dostępu | Logowanie przez Spring Security, dane izolowane per user |
| 5 | Testowalność przepływu | Co najmniej 1 test e2e weryfikujący zapis wpisu i zwrot tagu |
| 6 | CI/CD | Pipeline GitHub Actions: build + testy przy każdym push |

---

## 4. Zakres MVP — co JEST w projekcie

- Rejestracja i logowanie użytkownika
- Dodawanie wpisu dziennika (tytuł opcjonalny + treść)
- Automatyczna klasyfikacja nastroju przez OpenAI API (po zapisie)
- Ręczna korekta tagu nastroju przez użytkownika
- Lista wpisów z tagami i datami
- Widok szczegółowy wpisu
- Edycja i usuwanie wpisu
- Wykres nastrojów — tygodniowy i miesięczny
- Prosta strona profilowa z historią wpisów

## 4.1 Co NIE jest w MVP (explicit out-of-scope)

- Import / eksport do PDF lub CSV
- Powiadomienia email / push
- Aplikacja mobilna
- Analiza korelacji czynników zewnętrznych (sen, aktywność) — faza 2
- Współdzielenie wpisów
- Płatności, plany subskrypcji
- Multi-language UI

---

## 5. Tech Stack

| Warstwa | Technologia | Uzasadnienie |
|---|---|---|
| Backend | Spring Boot 3.x (Java 21) | Znajomość, dobre wsparcie agenta, duże LLM-context |
| Frontend | Thymeleaf + Bootstrap 5 | Zero build toolchain na MVP, SSR, proste formy |
| Baza danych | PostgreSQL 16 | Relacyjna, dobra integracja z Spring Data JPA |
| ORM | Spring Data JPA / Hibernate | Standardowy w ekosystemie Spring |
| Auth | Spring Security + session-based | Prosta integracja, brak potrzeby JWT na MVP |
| AI | OpenAI API (GPT-4o-mini) | Tanie, szybkie, wystarczające do klasyfikacji nastroju |
| Wykresy | Chart.js (via Thymeleaf fragment) | Lekkie, dobrze udokumentowane, zero frameworku JS |
| CI/CD | GitHub Actions | Darmowe, standardowe, łatwe do skonfigurowania |
| Konteneryzacja | Docker + docker-compose (dev) | Łatwy start bazy lokalnie |

---

## 6. Architektura danych

### Encje

#### `users`
| Kolumna | Typ | Opis |
|---|---|---|
| id | UUID (PK) | Identyfikator użytkownika |
| email | VARCHAR(255) UNIQUE | Login |
| password_hash | VARCHAR(255) | BCrypt |
| display_name | VARCHAR(100) | Nazwa wyświetlana |
| created_at | TIMESTAMP | Data rejestracji |

#### `journal_entries`
| Kolumna | Typ | Opis |
|---|---|---|
| id | UUID (PK) | Identyfikator wpisu |
| user_id | UUID (FK → users) | Właściciel wpisu |
| title | VARCHAR(255) NULL | Opcjonalny tytuł |
| content | TEXT | Treść wpisu |
| mood_tag | VARCHAR(50) NULL | Tag nastroju z AI |
| mood_score | SMALLINT NULL | Wartość 1–5 (1=bardzo zły, 5=bardzo dobry) |
| mood_overridden | BOOLEAN DEFAULT false | Czy użytkownik ręcznie zmienił tag |
| created_at | TIMESTAMP | Data i czas wpisu |
| updated_at | TIMESTAMP | Ostatnia edycja |

#### `mood_tags` (słownik — enum lub tabela)
Predefiniowane wartości: `BARDZO_DOBRY`, `DOBRY`, `NEUTRALNY`, `ZŁY`, `BARDZO_ZŁY`
Opcjonalnie wzbogacone o emocje szczegółowe: `SPOKOJNY`, `PODEKSCYTOWANY`, `SMUTNY`, `ZESTRESOWANY`, `ZMĘCZONY`

---

## 7. User Stories

### Epik 1: Uwierzytelnianie

---

**US-001 — Rejestracja konta**

Jako nowy użytkownik
chcę założyć konto podając email i hasło
aby móc bezpiecznie przechowywać swoje prywatne wpisy.

**Kryteria akceptacji:**
- [ ] Formularz rejestracji zawiera pola: email, hasło, potwierdzenie hasła, wyświetlana nazwa
- [ ] Email musi być unikalny — duplikat zwraca komunikat błędu
- [ ] Hasło minimum 8 znaków
- [ ] Po poprawnej rejestracji użytkownik jest automatycznie zalogowany i przekierowany na stronę główną
- [ ] Hasło przechowywane jako BCrypt hash

---

**US-002 — Logowanie**

Jako zarejestrowany użytkownik
chcę zalogować się emailem i hasłem
aby uzyskać dostęp do swoich wpisów.

**Kryteria akceptacji:**
- [ ] Formularz logowania: email + hasło
- [ ] Błędne dane → komunikat "Nieprawidłowy email lub hasło" (bez rozróżniania co jest błędne)
- [ ] Po zalogowaniu przekierowanie na dashboard
- [ ] Sesja wygasa po 24h nieaktywności

---

**US-003 — Wylogowanie**

Jako zalogowany użytkownik
chcę móc się wylogować
aby zabezpieczyć dostęp do swoich danych.

**Kryteria akceptacji:**
- [ ] Przycisk "Wyloguj" dostępny w nawigacji
- [ ] Po wylogowaniu przekierowanie na stronę logowania
- [ ] Sesja zostaje unieważniona po stronie serwera

---

### Epik 2: Zarządzanie wpisami

---

**US-004 — Dodanie nowego wpisu**

Jako zalogowany użytkownik
chcę wpisać tekst swojego dziennika
aby aplikacja mogła go przeanalizować i zapisać.

**Kryteria akceptacji:**
- [ ] Formularz zawiera jedno pole: treść wpisu (wymagane)  ~~opcjonalny tytuł~~
- [ ] Brak minimalnej długości — walidacja po stronie AI
- [ ] Treść może mieć max 5000 znaków
- [ ] Po kliknięciu "Zapisz" wpis trafia do bazy ze statusem "analizowany"
- [ ] Użytkownik widzi komunikat "Analizuję nastrój..." podczas oczekiwania na AI
- [ ] Po zakończeniu analizy strona odświeża się z przypisanym tagiem nastroju
- [ ] Jeśli OpenAI API jest niedostępne — wpis zapisuje się z tagiem `NIEZNANY` i informacją dla użytkownika

---

**US-005 — Przeglądanie listy wpisów**

Jako zalogowany użytkownik
chcę widzieć listę swoich wpisów
aby szybko znaleźć konkretny dzień lub przeglądać historię.

**Kryteria akceptacji:**
- [ ] Lista posortowana malejąco po dacie (najnowsze na górze)
- [ ] Każdy element listy pokazuje: datę, tytuł lub pierwsze 80 znaków treści, tag nastroju z kolorowym wskaźnikiem
- [ ] Paginacja — 20 wpisów na stronę
- [ ] Użytkownik widzi wyłącznie własne wpisy

---

**US-006 — Widok szczegółowy wpisu**

Jako zalogowany użytkownik
chcę otworzyć konkretny wpis
aby przeczytać jego pełną treść i zobaczyć przypisany nastrój.

**Kryteria akceptacji:**
- [ ] Widok pokazuje: datę, tytuł, pełną treść, tag nastroju, informację czy tag był ręcznie zmieniony
- [ ] Dostępne przyciski: "Edytuj" i "Usuń"
- [ ] Próba wejścia na wpis innego użytkownika zwraca 403

---

**US-007 — Edycja wpisu**

Jako zalogowany użytkownik
chcę edytować treść wpisu
aby poprawić błędy lub uzupełnić myśli.

**Kryteria akceptacji:**
- [ ] Formularz edycji prefillowany aktualną treścią
- [ ] Po zapisaniu zmienionej treści AI ponownie klasyfikuje nastrój
- [ ] Poprzedni tag jest nadpisywany, `mood_overridden` resetowane do `false`
- [ ] Wyświetlane `updated_at` po edycji

---

**US-008 — Usuwanie wpisu**

Jako zalogowany użytkownik
chcę usunąć wpis
aby zachować porządek w dzienniku.

**Kryteria akceptacji:**
- [ ] Przed usunięciem pojawia się modal z potwierdzeniem "Czy na pewno chcesz usunąć ten wpis?"
- [ ] Usunięcie jest nieodwracalne (hard delete)
- [ ] Po usunięciu przekierowanie na listę wpisów z komunikatem sukcesu

---

### Epik 3: Analiza nastroju przez AI

---

**US-009 — Automatyczna klasyfikacja nastroju**

Jako użytkownik piszący wpis
chcę żeby aplikacja automatycznie określiła mój nastrój
aby nie musieć ręcznie tagować każdego wpisu.

**Kryteria akceptacji:**
- [ ] Po zapisaniu wpisu backend wysyła treść do OpenAI API (GPT-4o-mini)
- [ ] Prompt zwraca: `mood_tag` (jeden z predefiniowanych) + `mood_score` (1–5) + krótkie uzasadnienie (max 1 zdanie, tylko do użytku wewnętrznego / logu)
- [ ] Wynik jest zapisywany w `journal_entries`
- [ ] Czas odpowiedzi AI nie blokuje UI (asynchroniczne wywołanie lub loader)
- [ ] Koszt API nie przekracza $0.01 na wpis przy typowej długości (500–1000 znaków)

---

**US-010 — Ręczna korekta tagu nastroju**

Jako użytkownik
chcę móc zmienić tag nastroju przypisany przez AI
aby skorygować błędną klasyfikację.

**Kryteria akceptacji:**
- [ ] Na widoku wpisu dostępny jest dropdown z listą tagów nastroju
- [ ] Po wyborze i zapisaniu: `mood_tag` zaktualizowany, `mood_overridden = true`
- [ ] Wpis oznaczony jako "ręcznie poprawiony" (widoczna ikona lub etykieta)
- [ ] Korekta nie powoduje ponownego wywołania AI

---

### Epik 4: Wizualizacja i trendy

---

**US-011 — Wykres nastrojów tygodniowy**

Jako użytkownik
chcę widzieć wykres nastrojów z ostatnich 7 dni
aby szybko ocenić jak minął mój tydzień.

**Kryteria akceptacji:**
- [ ] Wykres liniowy lub słupkowy (Chart.js) pokazuje `mood_score` (1–5) dla każdego dnia
- [ ] Dni bez wpisu są oznaczone jako brak danych (przerwa na wykresie)
- [ ] Wykres dostępny na dashboardzie po zalogowaniu
- [ ] Kliknięcie punktu na wykresie otwiera wpis z tego dnia

---

**US-012 — Wykres nastrojów miesięczny**

Jako użytkownik
chcę widzieć trend nastroju z ostatnich 30 dni
aby dostrzec długoterminowe wzorce.

**Kryteria akceptacji:**
- [ ] Widok "Statystyki" zawiera wykres za ostatnie 30 dni
- [ ] Dodatkowo: rozkład tagów w postaci wykresu kołowego (ile dni: dobry / neutralny / zły)
- [ ] Widoczna średnia `mood_score` za miesiąc
- [ ] Dane dotyczą wyłącznie zalogowanego użytkownika

---

**US-013 — Podsumowanie tygodnia na dashboardzie**

Jako zalogowany użytkownik
chcę widzieć skrócone podsumowanie obecnego tygodnia na stronie głównej
aby jednym rzutem oka ocenić swój stan emocjonalny bez wchodzenia w szczegóły.

**Kryteria akceptacji:**
- [ ] Dashboard pokazuje: liczbę wpisów w bieżącym tygodniu, dominujący tag nastroju, średni `mood_score` za tydzień
- [ ] Widoczny pasek postępu "dni z wpisem" (np. 4/7)
- [ ] Jeśli brak wpisów w tym tygodniu — wyświetlany komunikat zachęcający do pierwszego wpisu
- [ ] Dane odświeżają się przy każdym załadowaniu dashboardu

---

### Epik 5: CI/CD i jakość

---

**US-014 — Automatyczny build i testy przy pushu**

Jako developer
chcę żeby każdy push do repozytorium uruchamiał build i testy
aby szybko wykrywać regresje bez manualnego sprawdzania.

**Kryteria akceptacji:**
- [ ] GitHub Actions pipeline uruchamia się przy push na `main` i `develop`
- [ ] Pipeline: checkout → `./mvnw test` → raport wyników
- [ ] Nieudane testy blokują merge do `main`
- [ ] Build nie wymaga zewnętrznych usług (AI, PostgreSQL mockowane w testach)
- [ ] Czas pipelinu < 3 minuty

---

**US-015 — Test end-to-end kluczowego przepływu**

Jako developer
chcę mieć co najmniej jeden test weryfikujący główny przepływ użytkownika
aby mieć pewność że podstawowa funkcjonalność działa po każdej zmianie.

**Kryteria akceptacji:**
- [ ] Test integracyjny (Spring Boot Test + MockMvc lub Testcontainers) pokrywa: logowanie → dodanie wpisu → weryfikacja zapisu w bazie → weryfikacja zwrotu tagu nastroju
- [ ] OpenAI API mockowane (Mockito / WireMock) — test nie wywołuje prawdziwego API
- [ ] Test uruchamia się w ramach `mvn test`
- [ ] Test opisany komentarzem "Happy path — główny przepływ użytkownika"

---

## 8. Przepływ użytkownika (Happy Path MVP)

[Strona logowania]
↓ login
[Dashboard]
├── wykres nastrojów (7 dni)
├── podsumowanie tygodnia
└── przycisk "Nowy wpis"
↓
[Formularz wpisu]
└── wpisz treść → kliknij "Zapisz"
↓
[Analiza AI w tle]
└── loader "Analizuję nastrój..."
↓
[Widok wpisu]
├── treść wpisu
├── tag nastroju (np. "Dobry 😊")
└── opcja ręcznej korekty tagu
↓
[Powrót na Dashboard]
└── zaktualizowany wykres



---

## 9. Definicja tagów nastroju

| Tag | Mood Score | Kolor UI | Opis dla promptu AI |
|---|---|---|---|
| `BARDZO_DOBRY` | 5 | zielony | Radość, energia, entuzjazm, spełnienie |
| `DOBRY` | 4 | jasnozielony | Spokój, zadowolenie, dobry humor |
| `NEUTRALNY` | 3 | szary | Brak wyraźnych emocji, rutyna |
| `ZŁY` | 2 | pomarańczowy | Smutek, frustracja, zmęczenie, stres |
| `BARDZO_ZŁY` | 1 | czerwony | Przygnębienie, złość, niepokój, kryzys |

---

## 10. Prompt AI (szablon)
System:
Jesteś asystentem analizującym wpisy z dziennika osobistego.
Klasyfikujesz nastrój autora na podstawie treści wpisu.
Odpowiadasz wyłącznie w formacie JSON, bez żadnego dodatkowego tekstu.

User:
Przeanalizuj poniższy wpis i zwróć:

mood_tag: jeden z [BARDZO_DOBRY, DOBRY, NEUTRALNY, ZŁY, BARDZO_ZŁY]

mood_score: liczba całkowita od 1 do 5 (1=bardzo zły, 5=bardzo dobry)

reasoning: maksymalnie jedno zdanie po polsku wyjaśniające klasyfikację

Wpis:
"""
{entry_content}
"""

Odpowiedź (tylko JSON):
{
"mood_tag": "...",
"mood_score": ...,
"reasoning": "..."
}



---

## 11. Ryzyka i mitygacje

| Ryzyko | Prawdopodobieństwo | Mitygacja |
|---|---|---|
| OpenAI API niedostępne | Niskie | Fallback: zapisz wpis z tagiem `NIEZNANY`, powiadom użytkownika |
| Przekroczenie limitu tokenów | Niskie | Przytnij treść do 2000 znaków przed wysłaniem do API |
| Zbyt wysokie koszty API | Niskie | GPT-4o-mini ≈ $0.002/1K tokenów — przy 100 wpisach/mies. < $0.50 |
| Zbyt duże MVP | Wysokie | Analiza korelacji (sen/aktywność) — explicitly out-of-scope |
| Thymeleaf vs. React friction | Niskie | Thymeleaf na MVP, React można dodać w fazie 2 |

---

## 12. Harmonogram MVP (orientacyjny)

| Tydzień | Zakres |
|---|---|
| Tydzień 1 | Setup projektu, auth (US-001–003), model danych, formularz wpisu bez AI (US-004) |
| Tydzień 2 | Integracja OpenAI API (US-009), lista i widok wpisu (US-005–006), korekta tagu (US-010) |
| Tydzień 3 | Edycja/usuwanie (US-007–008), wykresy Chart.js (US-011–013), dashboard |
| Tydzień 4 | CI/CD (US-014–015), testy, poprawki UX, przygotowanie do certyfikacji |

---

## 13. Kryteria ukończenia projektu (Definition of Done)

- [ ] Wszystkie US z epików 1–4 zaimplementowane i działające
- [ ] Co najmniej 1 test e2e (US-015) przechodzi w pipeline
- [ ] GitHub Actions pipeline zielony na `main`
- [ ] Aplikacja uruchamia się przez `docker-compose up` (lokalne demo)
- [ ] PRD, README z instrukcją uruchomienia i opis architektury w repozytorium
- [ ] Demo flow możliwe do zaprezentowania bez konta developerskiego




