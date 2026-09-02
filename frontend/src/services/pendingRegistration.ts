const STORAGE_KEY = 'guesschess_pending_registration_token'

/**
 * Traverse la redirection de OAuthCallbackView vers /choose-login (etape 14) : un
 * pendingToken n'est jamais un JWT de session (voir JwtService), donc jamais posé
 * dans useAuthStore - juste le temps de cette navigation.
 */
export function save(pendingToken: string): void {
  sessionStorage.setItem(STORAGE_KEY, pendingToken)
}

export function peek(): string | null {
  return sessionStorage.getItem(STORAGE_KEY)
}

export function consume(): string | null {
  const token = sessionStorage.getItem(STORAGE_KEY)
  sessionStorage.removeItem(STORAGE_KEY)
  return token
}
