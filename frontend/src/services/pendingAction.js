const STORAGE_KEY = 'guesschess_pending_action'

/**
 * Traverse une redirection OAuth complète (sortie du SPA) : ce que l'utilisateur
 * était en train de faire (créer une partie, rejoindre une invitation) est sauvé ici
 * juste avant la redirection, puis rejoué par OAuthCallbackView une fois le JWT reçu.
 */
export function save(action) {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(action))
}

export function consume() {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  sessionStorage.removeItem(STORAGE_KEY)
  return JSON.parse(raw)
}
