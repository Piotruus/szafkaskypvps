SZAFA - zwierzatka i kostiumy (/szafa, /szafka)
================================================
Wymagania: Paper 1.21.4+ (Java 21). Bez zewnetrznych pluginow.

KOMPILACJA
  GitHub: wrzuc caly folder do repozytorium - plik .github/workflows/build.yml sam zbuduje
          plugin (zakladka Actions -> Artifacts -> Szafa-jar).
  Lokalnie: mvn clean package  ->  target/Szafa.jar  -> wrzuc do plugins/

RESOURCE PACK (osobny plik Szafa-resourcepack.zip)
  server.properties -> resource-pack=<link do zipa>, resource-pack-sha1=<sha1>, require-resource-pack=true
  WAZNE: klient zapamietuje pack po sha1/linku. Po kazdej zmianie packa wpisz NOWE sha1 (i najlepiej
  nowy link, np. ...zip?v=2), inaczej gracze dalej maja stary pack (fioletowo-czarne kostki, zle glowy).
  Sha1 liczysz: Linux/Mac `sha1sum plik.zip`, Windows `certutil -hashfile plik.zip SHA1`.

AKTUALIZACJA PLUGINU
  Stary plugins/Szafa/config.yml zostaje zapisany jako config.backup-vX.yml, a plugin wgrywa nowy
  (config-version). Dzieki temu po aktualizacji nie zostaja stare nazwy modeli ani ustawienia.
  Swoje zmiany z kopii przepisz do nowego config.yml i zrob /szafaadmin reload.
  Bez packa itemy to zwykle papiery, a kostiumy zwykle zbroje iron.

JAK TO DZIALA
  Zwierzatka i kostiumy to FIZYCZNE ITEMY. Admin daje item komenda, gracz bierze go do reki
  i klika PPM. Item znika, a zwierzatko/kostium sie zaklada.
  Czas (np. 7/14/30 dni) liczy sie OD PIERWSZEGO ZALOZENIA - wtedy item dostaje stala date
  wygasniecia. Zdjecie (klik w slot w /szafa) oddaje item do ekwipunku, ale czas wygasniecia
  PLYNIE DALEJ takze gdy item lezy w ekwipunku. Ponowne zalozenie nie resetuje czasu.
  Wygasniete itemy nie dzialaja.
  Listy (Lista Petow / Lista Kostiumow) to tylko katalog do podgladu.
  Przedluzanie: item "Zwoj przedluzenia" (7/14/30 dni):
    - przeciagnij zwoj (LPM) na item zwierzatka/kostiumu w ekwipunku, albo
    - w /szafa kliknij ZEGAR pod aktywnym zwierzatkiem/kostiumem (LPM = najmniejszy zwoj, PPM = najwiekszy).
  Zwoj przesuwa DATE WAZNOSCI.

KOSTIUMY - JAK DZIALAJA
  Kostium jest wirtualny (nie zajmuje slotow zbroi). Zbroje mozna normalnie zakladac i zdejmowac.
  - Tulow, rece i nogi: model zbroi z packa (falszywe pakiety wyposazenia).
  - Glowa: osobny model 3D przyklejony do glowy gracza (NIE helm). Prawdziwy helm da sie nosic,
    ale gdy kostium ma glowe, helm jest wizualnie ukryty (hide-helmet-with-costume).
  - Wlasciciel widzi kostium w F5 (show-costume-to-self). Gdy klika w swoj ekwipunek, plugin
    pokazuje mu prawdziwa zbroje - dzieki temu zdejmowanie/zakladanie dziala bez kopii i duchow.
  - Jesli glowa kostiumu jest odwrocona tylem: costume-head-yaw-offset: 180.

KOMENDY GRACZA
  /szafa, /szafka

KOMENDY ADMINA (uprawnienie szafa.admin, dziala z konsoli)
  /szafaadmin give   <gracz> <pet|kostium> <id> <dni|perm> [ilosc]   - item do ekwipunku
  /szafaadmin scroll <gracz> <dni> [ilosc]                           - Zwoj przedluzenia
  /szafaadmin grant  <gracz> <pet|kostium> <id> <dni|perm>           - zaklada od razu (bez itemu)
  /szafaadmin take   <gracz> <pet|kostium>                           - zabiera aktywne
  /szafaadmin list   <gracz>
  /szafaadmin reload

ID zwierzatek: endermanek, kotek, killerek
ID kostiumow:  koziolek, spiderman, lodowy_straznik

USTAWIENIA (plugins/Szafa/config.yml)
  show-costume-to-self (widok kostiumu u siebie w F5), pet-scale, pet-nametag,
  pet-yaw-offset (jesli zwierzatko patrzy tylem ustaw 180), join-warning-hours,
  scroll (nazwa i opis zwoju) oraz nazwy/opisy/bonusy kazdego zwierzatka i kostiumu.
