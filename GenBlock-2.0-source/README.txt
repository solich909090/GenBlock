GENBLOCK 2.0 - projekt zrodlowy

Funkcje:
- 100 poziomow generatora (poziomy 1-100).
- /genblock give <gracz> <poziom> - komenda administratora.
- Generator to specjalna skrzynia; prawy przycisk daje surowiec, a generator automatycznie produkuje surowiec co 30 sekund.
- /sklepik - sklep GUI z materialami Minecrafta dostepnymi w API serwera; lista dzieli sie na strony.
- Ceny mozna zmieniac w config.yml w sekcji item-prices.
- Saldo graczy jest zapisywane w config.yml.

Budowanie:
1. Zainstaluj JDK 25 i Apache Maven.
2. Otworz terminal w folderze projektu.
3. Wpisz: mvn clean package
4. Gotowy plik bedzie w target/GenBlock-2.0.0.jar
5. Wgraj JAR do serwera Paper i uruchom serwer ponownie.

WAZNE:
- Projekt jest przygotowany pod Paper 26.2 i Java 25, ale w tym srodowisku nie zostal skompilowany ani przetestowany.
- Wersja Paper API w pom.xml moze wymagać aktualizacji do najnowszego builda 26.2.
- Sklep pokazuje wszystkie przedmioty Material.isItem() udostepniane przez dana wersje API. Nie oznacza to absolutnie kazdego przedmiotu z modow.
- Aternos nie pozwala zwykle na reczne wgrywanie dowolnych wlasnych JAR-ow; plugin musi byc dostepny w ich katalogu, aby zainstalowac go z panelu.
