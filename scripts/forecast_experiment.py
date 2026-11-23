"""Reproducible Decimal reference experiment: 3-month SMA/WMA, direct horizon=2, rolling origin.

Input: app expenses CSV (date, amount_pln, vehicle_id, currency) or monthly CSV (series, month, amount_pln).
Demo data are synthetic and do not establish accuracy on real vehicle expenditure.
"""
import argparse
import csv
from datetime import date
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
import json

CENT = Decimal('0.01')
def money(value):
    return value.quantize(CENT, rounding=ROUND_HALF_UP)
def shift(month, offset):
    value = month.year * 12 + month.month - 1 + offset
    return date(value // 12, value % 12 + 1, 1)
def month_text(value):
    return value.strftime('%Y-%m')
def predict(window, weighted):
    return money(sum((value * (i + 1 if weighted else 1) for i, value in enumerate(window)), Decimal(0)) / (6 if weighted else 3))
def evaluate(values):
    errors = []
    for target in range(4, len(values)):
        window = [row[1] for row in values[target-4:target-1]]
        simple, weighted = predict(window, False), predict(window, True)
        actual = values[target][1]
        errors.append({'target_month': month_text(values[target][0]), 'training_from': month_text(values[target-4][0]),
            'training_to': month_text(values[target-2][0]), 'excluded_month': month_text(values[target-1][0]),
            'actual_pln': actual, 'simple_pln': simple, 'weighted_pln': weighted,
            'simple_absolute_error_pln': abs(actual-simple), 'weighted_absolute_error_pln': abs(actual-weighted)})
    simple_mae = money(sum((e['simple_absolute_error_pln'] for e in errors), Decimal(0)) / len(errors)) if errors else None
    weighted_mae = money(sum((e['weighted_absolute_error_pln'] for e in errors), Decimal(0)) / len(errors)) if errors else None
    return errors, simple_mae, weighted_mae
def save_csv(path, fields, rows):
    with path.open('w', newline='', encoding='utf-8-sig') as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, lineterminator='\r\n'); writer.writeheader(); writer.writerows(rows)
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--demo', action='store_true'); group.add_argument('--input', type=Path)
    parser.add_argument('--as-of', required=True, type=date.fromisoformat)
    parser.add_argument('--history-months', default=12, type=int)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if not 3 <= args.history_months <= 60: parser.error('--history-months must be 3..60')
    current = args.as_of.replace(day=1)
    series = {}
    if args.demo:
        for name, amounts in {'constant': [250]*12, 'trend': list(range(100,1300,100)),
            'rare_repair': [200,210,190,200,205,200,2000,220,210,200,215,205]}.items():
            series[name] = {shift(current, i-12): Decimal(value) for i, value in enumerate(amounts)}
    else:
        with args.input.open(newline='', encoding='utf-8-sig') as source:
            for row in csv.DictReader(source):
                period = date.fromisoformat(row['date']) if row.get('date') else date.fromisoformat(row['month']+'-01')
                period = period.replace(day=1)
                if period >= current: continue
                if row.get('currency', 'PLN') != 'PLN': raise ValueError('Only PLN is supported')
                value = Decimal(row['amount_pln'])
                if not value.is_finite() or value < 0 or value != money(value): raise ValueError('Expected nonnegative decimal with at most two fractional digits')
                name = row.get('series') or row.get('vehicle_id') or 'vehicle'
                data = series.setdefault(name, {}); data[period] = data.get(period, Decimal(0)) + value
    args.output.mkdir(parents=True, exist_ok=True)
    all_history, all_errors, metrics = [], [], []
    for name, data in sorted(series.items()):
        start = max(min(data), shift(current, -args.history_months)); values = []
        while start < current:
            value = money(data.get(start, Decimal(0))); values.append((start, value)); start = shift(start, 1)
        all_history.extend({'series': name, 'month': month_text(period), 'amount_pln': value} for period,value in values)
        errors, simple_mae, weighted_mae = evaluate(values)
        all_errors.extend(dict(series=name, **row) for row in errors)
        winner = '' if simple_mae is None else 'TIE' if simple_mae == weighted_mae else 'SIMPLE_MOVING_AVERAGE' if simple_mae < weighted_mae else 'WEIGHTED_MOVING_AVERAGE'
        metrics.append({'series': name, 'completed_months': len(values), 'target_month': month_text(shift(current,1)),
            'simple_forecast_pln': predict([v for _,v in values[-3:]],False) if len(values)>=3 else None,
            'weighted_forecast_pln': predict([v for _,v in values[-3:]],True) if len(values)>=3 else None,
            'evaluation_count': len(errors), 'simple_mae_pln': simple_mae, 'weighted_mae_pln': weighted_mae, 'best_method': winner})
    if not metrics: raise ValueError('No completed expense months found')
    save_csv(args.output/'history.csv', ['series','month','amount_pln'], all_history)
    save_csv(args.output/'backtest.csv', ['series','target_month','training_from','training_to','excluded_month','actual_pln','simple_pln','weighted_pln','simple_absolute_error_pln','weighted_absolute_error_pln'], all_errors)
    save_csv(args.output/'metrics.csv', list(metrics[0]), metrics)
    metadata = {'asOfDate': args.as_of.isoformat(), 'synthetic': args.demo, 'windowMonths': 3, 'weights': [1,2,3], 'horizonMonths': 2,
        'historyMonths': args.history_months, 'rounding': 'HALF_UP to cents before error', 'zeroMonths': 'fill after first recorded expense month', 'source': 'built-in deterministic demo' if args.demo else args.input.name}
    (args.output/'metadata.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    source_note = '**Dane syntetyczne.**' if args.demo else 'Dane pochodzą z pliku CSV użytkownika. Pochodzenie i kompletność wymagają odrębnego opisu.'
    report = ['# Porównanie metod prognozowania', '', f'Data obliczeń to {args.as_of.isoformat()}. {source_note}', '',
        'Okno obejmuje trzy pełne miesiące. Wagi WMA wynoszą 1, 2 i 3, a horyzont obejmuje dwa miesiące od ostatniego pełnego miesiąca. Bieżący miesiąc jest pomijany. Każda weryfikacja wykorzystuje wyłącznie wcześniejsze dane. Prognozę zaokrągla się do groszy metodą HALF_UP, a MAE to średnia z błędów bezwzględnych.', '',
        '| Szereg | Miesiące | Porównania | Prognoza SMA w PLN | Prognoza WMA w PLN | MAE SMA | MAE WMA | Niższy MAE |', '| --- | --- | --- | --- | --- | --- | --- | --- |']
    for row in metrics:
        report.append('| '+' | '.join(str(row[key]) if row[key] is not None else 'Brak danych' for key in ['series','completed_months','evaluation_count','simple_forecast_pln','weighted_forecast_pln','simple_mae_pln','weighted_mae_pln','best_method'])+' |')
    report += ['', 'Przy stałych wydatkach wyniki obu metod są równe. Na rosnącym trendzie większa waga nowszych miesięcy może ograniczyć opóźnienie prognozy. Jednorazowa kosztowna naprawa wpływa na błąd i na późniejsze prognozy. Wyniki dotyczą badanych danych syntetycznych.', '',
        'Miesiące bez wpisów po rozpoczęciu historii mają wartość zero. Brak wpisu może zaniżyć prognozę. Ocena trafności na rzeczywistych pojazdach wymaga osobnej próby z opisem pochodzenia, kompletności i anonimizacji danych.', '',
        'Metodykę weryfikacji z przesuwanym początkiem prognozowania opisują [Hyndman i Athanasopoulos](https://otexts.com/fpp3/toolbox.html#time-series-cross-validation).', '',
        'Pliki history.csv, backtest.csv i metrics.csv zawierają dane wejściowe, poszczególne porównania i metryki. Parametry zapisano w metadata.json.']
    (args.output/'REPORT.md').write_text('\n'.join(report)+'\n', encoding='utf-8')
    for row in metrics: print(f"{row['series']}: targets={row['evaluation_count']} SMA_MAE={row['simple_mae_pln']} WMA_MAE={row['weighted_mae_pln']}")
if __name__ == '__main__':
    main()
