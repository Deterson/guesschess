/**
 * Types miroir des DTOs backend (com.guesschess.infrastructure.{web,websocket}.dto)
 * et des enums du domaine. A tenir a jour manuellement en l'absence de generateur
 * (voir decision : pas de springdoc/OpenAPI, DTOs peu nombreux).
 */

export type Color = 'WHITE' | 'BLACK'
export type ColorLower = 'white' | 'black'
export type GameVariant = 'GUESSCHESS' | 'GUESSMATE'
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
  | 'DRAW_INSUFFICIENT_MATERIAL'

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

// ---- WebSocket STOMP (infrastructure/websocket/dto) ----

export interface LegalMoveMessage {
  from: string
  to: string
  promotion: PromotionPieceType | null
}

export interface MoveHistoryEntry {
  from: string
  to: string
  piece: PieceCode
  captured: PieceCode | null
  type: MoveType
  promotion: PromotionPieceType | null
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
  movePlayed: boolean
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
}

export interface ErrorMessage {
  code: string
  message: string
}
