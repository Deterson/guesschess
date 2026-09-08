-- GameVariant.GUESSCHESS et GameVariant.NOGUESSMATE sont renommes respectivement en
-- GUESSMATE et GUESSCHESS (retour a la denomination d'avant V7 : "guesschess" redevient
-- la regle de base, "guessmate" la variante ou une devinette correcte sur un echec met
-- fin a la partie immediatement). On remappe les valeurs deja stockees plutot que de
-- wiper, pour ne pas perdre les parties existantes.
update games set variant = 'GUESSCHESS_TMP' where variant = 'GUESSCHESS';
update games set variant = 'GUESSCHESS' where variant = 'NOGUESSMATE';
update games set variant = 'GUESSMATE' where variant = 'GUESSCHESS_TMP';

alter table games alter column variant set default 'GUESSMATE';
