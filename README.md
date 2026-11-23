# CarCare Analytics

CarCare to aplikacja na Androida do prowadzenia dziennika pojazdów, analizy kosztów eksploatacji i pilnowania terminów. Użytkownik może zapisywać tankowania, przebieg, wydatki i wizyty w serwisie, śledzić ważność OC i badania technicznego, oszacować wydatki na kolejny miesiąc oraz wyeksportować koszty do CSV.

- `android/` — Kotlin, Jetpack Compose, Retrofit i WorkManager. Interfejs jest dostępny po polsku i angielsku.
- `backend/` — Java 21, Spring Boot, REST API, uwierzytelnianie JWT i migracje Flyway dla PostgreSQL 17.

## Uruchomienie lokalne

1. Skopiuj `.env.example` do `.env`. Ustaw własne `POSTGRES_PASSWORD` oraz osobny losowy `JWT_SECRET` o długości co najmniej 32 bajtów.
2. W katalogu głównym uruchom `docker compose up --build -d`. Backend nasłuchuje na porcie 8080, a Swagger UI działa pod adresem `http://localhost:8080/swagger-ui/index.html`.
3. Otwórz katalog `android/` w Android Studio z JDK 21 i Android SDK 36. Uruchom wariant debug na emulatorze. Domyślny adres API to `http://10.0.2.2:8080/api/v1/`.
4. Użyj opcji rejestracji w aplikacji, aby utworzyć konto i dodać pierwszy pojazd.

Na telefonie podłączonym przez USB z włączonym debugowaniem wykonaj `adb reverse tcp:8080 tcp:8080`. W katalogu `android/` zbuduj i zainstaluj wariant debug z adresem API telefonu.

```powershell
.\gradlew.bat :app:installDebug "-PAPI_BASE_URL=http://127.0.0.1:8080/api/v1/"
```

Polecenie `docker compose down` zatrzymuje serwer i bazę. Dane PostgreSQL pozostają w wolumenie.

## Weryfikacja i dokumentacja

- Backend — `mvn verify` w katalogu `backend/`. Domyślne testy integracyjne wymagają działającego Dockera.
- Android — `.\gradlew.bat :app:testDebugUnitTest` w katalogu `android/`.
- [Architektura i schemat bazy danych](docs/BAZA_DANYCH.md)
- [Metody prognozowania i odtworzenie eksperymentu](docs/PROGNOZA.md)
