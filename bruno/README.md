# Collection Bruno - Guesschess

Un appel Bruno par endpoint STOMP expose par `GameController` : creation de partie,
soumission d'un coup, soumission d'une devinette. Chaque requete est de type
WebSocket (`type: ws`) et embarque, comme messages pre-composes a envoyer dans
l'ordre, les frames STOMP brutes necessaires (CONNECT, SUBSCRIBE, SEND) puisque
Bruno n'a pas de client STOMP integre.

## Limitation connue de Bruno

Chaque frame STOMP doit se terminer par un octet NUL reel (byte 0x00). Bruno a un
bug ouvert et non resolu qui remplace cet octet par sa representation textuelle au
moment de l'envoi, ce qui empeche le serveur de reconnaitre la frame (meme le
CONNECT initial echoue) :
https://github.com/usebruno/bruno/issues/6091

Les fichiers `.bru` de cette collection contiennent bien un vrai octet 0x00 a la fin
de chaque frame (verifiable avec `xxd`) - c'est le format correct, pret a fonctionner
des que ce bug sera corrige cote Bruno. En attendant, ces requetes peuvent echouer
silencieusement (pas de reponse CONNECTED). Le flux complet est deja verifie de
bout en bout par `StompFlowIntegrationTest` dans le code Java, qui reste la
reference fiable pour tester le serveur.

## Utilisation

1. Lancer le serveur (`mvn spring-boot:run`), par defaut sur `ws://localhost:8080/ws`.
2. Ouvrir **01 - Create game**, se connecter, envoyer les messages dans l'ordre
   (CONNECT, SUBSCRIBE, SEND). La reponse sur `/user/queue/games.created` contient
   `gameId`, `whiteToken`, `blackToken`.
3. Reporter ces trois valeurs dans les variables de la collection (`collection.bru`,
   ou via l'onglet Variables de Bruno) : `gameId`, `whiteToken`, `blackToken`.
4. Ouvrir **02 - Submit move** ET **03 - Submit guess**, se connecter, envoyer les
   messages dans l'ordre dans chacune. Coup par defaut : e2-e4.

Un round n'est resolu que lorsque les **deux** sont arrives - peu importe l'ordre.
Celui qui arrive en premier ne recoit qu'un accuse prive (`/user/queue/move.ack` ou
`/user/queue/guess.ack`, message `recorded_waiting_for_...`) ; l'etat resultant n'est
diffuse sur `/topic/games/{{gameId}}` qu'a l'arrivee du second. Lancer uniquement
**02** (ou uniquement **03**) laisse le round en attente indefiniment - c'est normal,
pas un bug : le tour ne progresse jamais tant que l'autre moitie n'a pas ete
soumise (`03 - Submit guess` avec from/to vides = "pas de devinette", une soumission
valable qui compte quand meme).

Les jetons et l'id de partie sont ephemeres (pas de comptes joueurs avant l'etape 4
de la roadmap) : il faut relancer l'etape 1 a chaque nouvelle partie.
