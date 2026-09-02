/**
 * Types miroir des DTOs backend (com.guesschess.infrastructure.{web,websocket}.dto)
 * et des enums du domaine. A tenir a jour manuellement en l'absence de generateur
 * (voir decision : pas de springdoc/OpenAPI, DTOs peu nombreux).
 */

export type Color = 'WHITE' | 'BLACK'
export type ColorLower = 'white' | 'black'
export type GameVariant = 'GUESSCHESS' | 'NOGUESSMATE'
export type GameStatus = 'ONGOING' | 'FINISHED'
export type PieceType = 'PAWN' | 'KNIGHT' | 'BISHOP' | 'ROOK' | 'QUEEN' | 'KING'
export type PromotionPieceType = Exclude<PieceType, 'PAWN' | 'KING'>
export type MoveType = 'NORMAL' | 'DOUBLE_PAWN_PUSH' | 'CASTLE_KINGSIDE' | 'CASTLE_QUEENSIDE' | 'EN_PASSANT' | 'PROMOTION'
export type GameResultCause =
  | 'CHECKMATE'
  | 'KING_CAPTURED'
  | 'CHECK_PARRY_GUESSED'
  | 'STALEMATE'
  | 'DRAW_FIFTY_MOVE_RULE'
  | 'DRAW_THREEFOLD_REPETITION'
  | 'DRAW_THREE_GUESS_REPETITION'
  | 'DRAW_INSUFFICIENT_MATERIAL'
  | 'DRAW_BY_AGREEMENT'

/** "wP", "bK"... ou null pour une case vide (voir GameMessageMapper.toCode). */
export type PieceCode = `${'w' | 'b'}${'P' | 'N' | 'B' | 'R' | 'Q' | 'K'}`
export type BoardCell = PieceCode | null
export type Board = BoardCell[][]

// ---- REST (infrastructure/web/dto) ----

export interface CreateGameHttpRequest {
  variant: GameVariant | null
  color: Color | 'RANDOM'
}

export interface CreateGameHttpResponse {
  gameId: string
  variant: GameVariant
  creatorColor: Color
  creatorToken: string
}

export interface ErrorResponse {
  error: string
  message: string
}

export interface JoinGameHttpResponse {
  gameId: string
  color: Color
  token: string
}

export interface MyAccessHttpResponse {
  color: Color
  token: string
}

/**
 * Un round de l'historique detaille (etape 11 - GET /api/games/{id}/history).
 * realSan est null quand le round a ete annule (devinette correcte) - aucun coup
 * n'a alors ete reellement joue. guessedFrom/guessedTo/guessedSan sont null quand
 * aucune devinette n'a ete soumise. boardAfter est null uniquement pour le round
 * terminal Guessmate (roi capture via devinette correcte en echec - voir
 * GameHistoryEntryHttpResponse.java).
 */
export interface GameHistoryEntry {
  moveNumber: number
  mover: Color
  guesser: Color
  actualFrom: string
  actualTo: string
  realSan: string | null
  guessedFrom: string | null
  guessedTo: string | null
  guessedSan: string | null
  guessedCorrectly: boolean
  boardAfter: Board | null
}

export interface GameHistoryHttpResponse {
  initialBoard: Board
  rounds: GameHistoryEntry[]
}

export interface AccountResponse {
  id: string
  displayName: string
  email: string | null
}

export type GameOutcome = 'WON' | 'LOST' | 'DRAW' | 'ONGOING'

/** Une ligne de "Mes parties" (etape 8 - GET /api/account/games). */
export type OpponentType = 'NONE' | 'ACCOUNT' | 'ANONYMOUS'

export interface GameSummaryHttpResponse {
  gameId: string
  myColor: Color
  opponentName: string | null
  opponentType: OpponentType
  outcome: GameOutcome
  board: Board
}

// ---- WebSocket STOMP (infrastructure/websocket/dto) ----

export interface LegalMoveMessage {
  from: string
  to: string
  promotion: PromotionPieceType | null
}

export interface MoveHistoryEntry {
  color: Color
  san: string
}

export interface ResultMessage {
  winner: Color | null
  cause: GameResultCause
}

/** Round deja resolu : reveler la devinette est le comportement voulu (cf. RoundSummaryMessage.java). */
export interface RoundSummaryMessage {
  mover: Color
  guesser: Color
  actualFrom: string
  actualTo: string
  guessedFrom: string | null
  guessedTo: string | null
  guessedCorrectly: boolean
}

/**
 * Ma propre soumission pour le round en cours (jamais celle de l'adversaire) - voir
 * MySubmissionMessage.java. Toujours { submitted: false, from: null, to: null,
 * promotion: null } dans un message reçu via /topic/games/{gameId} (diffusion
 * publique) ; seule la réponse à /app/games/{id}/view (canal privé
 * /user/queue/game.state) la renseigne réellement. submitted=true avec from/to null
 * signifie "devinette explicitement absente soumise" (bouton "Ne pas deviner").
 */
export interface MySubmissionMessage {
  submitted: boolean
  from: string | null
  to: string | null
  promotion: PromotionPieceType | null
}

export interface GameStateMessage {
  gameId: string
  variant: GameVariant
  board: Board
  sideToMove: Color
  status: GameStatus
  result: ResultMessage | null
  lastRound: RoundSummaryMessage | null
  legalMoves: LegalMoveMessage[]
  moveHistory: MoveHistoryEntry[]
  full: boolean
  mySubmission: MySubmissionMessage
  roundCount: number
  inCheck: boolean
  drawOfferedBy: Color | null
}

export interface ErrorMessage {
  code: string
  message: string
}

/** Chat ephemere : jamais persiste, ni par le backend ni par ce store (voir ChatMessage.java). */
export interface ChatMessage {
  color: Color
  text: string
}
