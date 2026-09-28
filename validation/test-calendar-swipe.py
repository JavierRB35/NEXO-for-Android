from pathlib import Path

js = Path('app/src/main/assets/app.js').read_text(encoding='utf-8')
css = Path('app/src/main/assets/styles.css').read_text(encoding='utf-8')

required = [
    'function attachMonthSwipe(element,onPrevious,onNext)',
    "attachMonthSwipe($('#calendarGrid'),()=>changeCalendarMonth(-1),()=>changeCalendarMonth(1));",
    'if(dx<0)onNext();else onPrevious();',
    "$('#calendarPrev').addEventListener('click',()=>changeCalendarMonth(-1));",
    "$('#calendarNext').addEventListener('click',()=>changeCalendarMonth(1));",
]
for marker in required:
    assert marker in js, f'Missing calendar swipe marker: {marker}'
assert '.calendar-grid{touch-action:pan-y}' in css
assert 'Math.abs(dx)>=48' in js
assert 'Math.abs(dx)>Math.abs(dy)*1.35' in js
print('OK calendar swipe regression checks')
assert 'calendarMonthPickerBtn' in js
assert 'duration:220' in js
assert 'prefers-reduced-motion: reduce' in js
