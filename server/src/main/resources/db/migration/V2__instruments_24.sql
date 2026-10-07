-- Довідник інструментів: 10 кодів → 24 (shared/instruments.json, docs/instruments.md).
-- Код saxophone перейменовано на saxophone_alto (поле renamed у довіднику).
--
-- Список кодів тут — знімок довідника на момент міграції. Новий інструмент у
-- shared/instruments.json вимагає нової міграції з оновленим CHECK; розбіжність
-- ловить ApiFlowTest (кожен код з довідника має прийматися базою).

alter table user_instruments drop constraint user_instruments_instrument_check;

update user_instruments set instrument = 'saxophone_alto' where instrument = 'saxophone';

-- Партії: старий детектор складав усі саксофони в один код. Тенор, якщо це
-- видно з назви або програми General MIDI (67, 68), — saxophone_tenor, решта —
-- saxophone_alto, як і каже довідник для саксофона без уточнення.
update score_parts set instrument = case
        when lower(name) like '%tenor%' or lower(name) like '%тенор%' or midi_program in (67, 68)
            then 'saxophone_tenor'
        else 'saxophone_alto'
    end
where instrument = 'saxophone';

alter table user_instruments add constraint user_instruments_instrument_check check (instrument in (
    'piano', 'accordion',
    'guitar', 'bass_guitar', 'ukulele', 'bandura',
    'violin', 'viola', 'cello', 'double_bass',
    'flute', 'recorder', 'sopilka', 'oboe', 'clarinet', 'bassoon', 'saxophone_alto', 'saxophone_tenor',
    'trumpet', 'french_horn', 'trombone', 'tuba',
    'drums', 'voice'));

alter table score_parts add constraint score_parts_instrument_check check (instrument is null or instrument in (
    'piano', 'accordion',
    'guitar', 'bass_guitar', 'ukulele', 'bandura',
    'violin', 'viola', 'cello', 'double_bass',
    'flute', 'recorder', 'sopilka', 'oboe', 'clarinet', 'bassoon', 'saxophone_alto', 'saxophone_tenor',
    'trumpet', 'french_horn', 'trombone', 'tuba',
    'drums', 'voice'));
