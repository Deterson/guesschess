import { defineStore } from 'pinia'
import { ref } from 'vue'

const STORAGE_KEY = 'guesschess:nightMode'

function readStored(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) !== 'false'
  } catch {
    return true
  }
}

function applyToDocument(nightMode: boolean) {
  document.documentElement.classList.toggle('day', !nightMode)
}

/**
 * Préférence purement locale au navigateur (pas de compte requis, contrairement aux
 * paramètres de la page Profil) : c'est une préférence d'affichage de l'appareil, pas
 * un comportement de jeu, donc elle doit rester utilisable en anonyme.
 */
export const useThemeStore = defineStore('theme', () => {
  const nightMode = ref(readStored())
  applyToDocument(nightMode.value)

  function setNightMode(value: boolean) {
    nightMode.value = value
    applyToDocument(value)
    try {
      localStorage.setItem(STORAGE_KEY, String(value))
    } catch {
      // stockage indisponible (navigation privée, quota...) : le thème reste actif pour la session
    }
  }

  return { nightMode, setNightMode }
})
