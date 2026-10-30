# Музыка RIFT Artillery 0.3.0

16 оригинальных зацикленных композиций: по одной для каждой из 12 локаций и четыре дополнительных трека для ручного выбора. Музыка записана заранее в Ogg Vorbis; сторонние записи и скачанные сэмплы не используются.

В меню и на паузе есть «Выбор музыки». «По локации» автоматически меняет тему вместе с картой; ручной выбор сохраняет композицию при переходе на другую арену. Кнопки переключают предыдущий и следующий трек с переходом через конец списка. Выбор и отдельный выключатель музыки сохраняются между запусками. В открытом музыкальном меню трек можно прослушать даже с паузы. После закрытия окна на паузе воспроизведение останавливается.

| Тема | Файл | Длительность | Темп |
|---|---|---:|---:|
| Неоновые крыши | [neon.ogg](../app/src/main/assets/music/neon.ogg) | 34.286 с | 112 BPM |
| Сердце обсидиана | [volcano.ogg](../app/src/main/assets/music/volcano.ogg) | 40 с | 96 BPM |
| Тихая орбита | [orbit.ogg](../app/src/main/assets/music/orbit.ogg) | 43.636 с | 88 BPM |
| Кислотный контур | [chemical.ogg](../app/src/main/assets/music/chemical.ogg) | 32 с | 120 BPM |
| Северное сияние | [arctic.ogg](../app/src/main/assets/music/arctic.ogg) | 48 с | 80 BPM |
| Пульс машин | [scrap.ogg](../app/src/main/assets/music/scrap.ogg) | 35.556 с | 108 BPM |
| Красные горизонты | [canyon.ogg](../app/src/main/assets/music/canyon.ogg) | 38.4 с | 100 BPM |
| Огни причала | [harbor.ogg](../app/src/main/assets/music/harbor.ogg) | 36.923 с | 104 BPM |
| Зелёный горизонт | [jungle.ogg](../app/src/main/assets/music/jungle.ogg) | 34.909 с | 110 BPM |
| Эхо руин | [ruins.ogg](../app/src/main/assets/music/ruins.ogg) | 42.667 с | 90 BPM |
| Грозовой фронт | [storm.ogg](../app/src/main/assets/music/storm.ogg) | 32.542 с | 118 BPM |
| Кристальный свет | [crystal.ogg](../app/src/main/assets/music/crystal.ogg) | 45.714 с | 84 BPM |
| Рывок | [rush.ogg](../app/src/main/assets/music/rush.ogg) | 30 с | 128 BPM |
| Ночной патруль | [nocturne.ogg](../app/src/main/assets/music/nocturne.ogg) | 41.739 с | 92 BPM |
| Аркадный разлом | [retro.ogg](../app/src/main/assets/music/retro.ogg) | 30.968 с | 124 BPM |
| Последний рубеж | [boss.ogg](../app/src/main/assets/music/boss.ogg) | 36.226 с | 106 BPM |

Первые 12 тем соответствуют картам в порядке их каталога; «Рывок», «Ночной патруль», «Аркадный разлом» и «Последний рубеж» доступны для ручного выбора.

Android использует MediaPlayer, управление audio focus и освобождение ресурсов при пересоздании Activity. Unity использует AudioSource и импортированные AudioClip. Звуковые эффекты выключаются отдельно.

[Исходная композиция](../tools/Compose-Music.py) воспроизводима с NumPy и FFmpeg; `--only-new` сохраняет уже существующие записи. [Проверка файлов](../tools/Verify-Music.py) декодирует все 16 записей и проверяет длительность, стерео, тишину, уровень, клиппинг и стык петли. [Фактические результаты](verification/v0.3.0/music-verification.json). На Android дополнительно проверено воспроизведение каждого трека через MediaPlayer, в Windows — движение позиции каждого AudioClip.
