import type { Router } from 'vue-router'
import type { useGameStore } from '../stores/game'
import { consume as consumePendingAction } from './pendingAction'
import { createGame, joinGame } from './api'

/**
 * Rejoue l'action que l'utilisateur voulait faire avant de devoir passer par OAuth
 * (creer/rejoindre une partie) une fois un JWT de session obtenu - partage entre
 * OAuthCallbackView (compte deja existant) et ChooseLoginView (etape 14 : inscription
 * fraiche, ou compte historique qui vient de poser son login), les deux menant au
 * meme point une fois authentifie.
 */
export async function resumeAfterLogin(token: string, router: Router, gameStore: ReturnType<typeof useGameStore>): Promise<void> {
  const action = consumePendingAction()
  if (!action) {
    router.replace('/')
    return
  }

  if (action.type === 'create') {
    const created = await createGame(action.variant, action.color, token)
    await gameStore.joinGame({
      gameId: created.gameId,
      token: created.creatorToken,
      color: created.creatorColor.toLowerCase() as 'white' | 'black',
    })
    router.replace(`/game/${created.gameId}`)
  } else if (action.type === 'join') {
    const joined = await joinGame(action.gameId, token)
    await gameStore.joinGame({ gameId: joined.gameId, token: joined.token, color: joined.color.toLowerCase() as 'white' | 'black' })
    router.replace(`/game/${action.gameId}`)
  } else if (action.type === 'login') {
    router.replace(action.returnTo || '/')
  } else {
    router.replace('/')
  }
}
