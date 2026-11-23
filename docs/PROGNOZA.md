# Prognozowanie wydatków

Prognoza daje orientacyjną kwotę budżetu na kolejny miesiąc kalendarzowy. Domyślny widok pokazuje średnią prostą z ostatnich trzech zakończonych miesięcy. Widok **Porównaj metody** pokazuje dodatkowo średnią ważoną z wagami 1, 2 i 3 od najstarszego do najnowszego miesiąca.

Bieżący miesiąc jest pomijany ze względu na niepełne dane. Przy obliczeniu w listopadzie wejściem są sierpień, wrzesień i październik, a prognoza dotyczy grudnia. Horyzont wynosi dwa miesiące od ostatniego zakończonego miesiąca.

Weryfikacja historyczna przesuwa datę obliczeń w czasie. Każdy wynik korzysta wyłącznie z wcześniejszych wydatków i jest porównywany z faktyczną kwotą dla tego samego miesiąca. MAE to średni bezwzględny błąd w PLN. Dla 12 zakończonych miesięcy można wykonać 8 takich porównań. Przyciski 6/12/24/60 miesięcy określają zakres historii do weryfikacji.

| Syntetyczny szereg | MAE średniej prostej | MAE średniej ważonej |
| --- | ---: | ---: |
| Stałe wydatki | 0,00 PLN | 0,00 PLN |
| Rosnący trend | 300,00 PLN | 266,67 PLN |
| Sporadyczna kosztowna naprawa | 452,92 PLN | 453,13 PLN |

Eksperyment porównuje metody w kontrolowanych warunkach. Wyniki dotyczą zadanych danych syntetycznych. Ocena dokładności na rzeczywistych pojazdach wymaga osobnej próby z opisem pochodzenia i kompletności danych. Miesiące bez wpisów po rozpoczęciu historii liczą się jako zero, więc brak wpisu może zaniżyć oszacowanie. Krótkie średnie mają ograniczoną zdolność przewidywania rzadkich napraw.

Skrypt odtwarza dane syntetyczne i zapisuje dane wejściowe, kolejne porównania oraz metryki. Uruchom go z katalogu głównego repozytorium.

```powershell
python scripts/forecast_experiment.py --demo --as-of 2026-11-12 --history-months 12 --output out/forecast-demo
```
