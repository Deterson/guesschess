-- GameVariant.GUESSCHESS et GameVariant.GUESSMATE sont renommes respectivement en
-- NOGUESSMATE et GUESSCHESS (guessmate devient la variante par defaut "guesschess" ;
-- l'ancienne regle de base devient "noguessmate"). On remappe les valeurs deja
-- stockees plutot que de wiper, pour ne pas perdre les parties existantes.
update games set variant = 'GUESSCHESS_TMP' where variant = 'GUESSCHESS';
update games set variant = 'GUESSCHESS' where variant = 'GUESSMATE';
update games set variant = 'NOGUESSMATE' where variant = 'GUESSCHESS_TMP';

alter table games alter column variant set default 'GUESSCHESS';
