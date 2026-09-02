import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getAccountSettings, updateAccountSettings } from '../services/api'

/**
 * Parametres du compte (page "Profil > Parametres"). Reserves aux comptes connectes,
 * comme le reste de la page profil - un joueur anonyme garde le comportement par
 * defaut (turnBlinkReminder = true) sans pouvoir le modifier.
 */
export const useSettingsStore = defineStore('settings', () => {
  const turnBlinkReminder = ref(true)
  const loaded = ref(false)

  async function load(authToken: string) {
    const settings = await getAccountSettings(authToken)
    turnBlinkReminder.value = settings.turnBlinkReminder
    loaded.value = true
  }

  async function setTurnBlinkReminder(value: boolean, authToken: string) {
    const settings = await updateAccountSettings({ turnBlinkReminder: value }, authToken)
    turnBlinkReminder.value = settings.turnBlinkReminder
  }

  return { turnBlinkReminder, loaded, load, setTurnBlinkReminder }
})
