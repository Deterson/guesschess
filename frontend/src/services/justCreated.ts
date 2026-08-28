const PREFIX = 'guesschess_just_created_'

/**
 * Signale que gameId vient d'être créé par ce navigateur (posé juste avant la
 * redirection vers /game/{gameId}, anonyme ou après retour d'OAuth) - GameView le
 * consomme une seule fois au montage pour savoir si elle doit afficher la bannière
 * d'invitation, sans dépendre d'un état "personne n'a encore rejoint" côté serveur.
 */
export function mark(gameId: string): void {
  sessionStorage.setItem(PREFIX + gameId, '1')
}

export function consume(gameId: string): boolean {
  const key = PREFIX + gameId
  const found = sessionStorage.getItem(key) === '1'
  sessionStorage.removeItem(key)
  return found
}
