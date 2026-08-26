import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

const STORAGE_KEY = 'guesschess_jwt'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(STORAGE_KEY))
  const isLoggedIn = computed(() => token.value !== null)

  function login(newToken) {
    token.value = newToken
    localStorage.setItem(STORAGE_KEY, newToken)
  }

  function logout() {
    token.value = null
    localStorage.removeItem(STORAGE_KEY)
  }

  return { token, isLoggedIn, login, logout }
})
