import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getMe } from '../services/api'
import type { AccountResponse } from '../types/api'

/**
 * Photo du compte connecte (etape 14) - au-dela du simple JWT (useAuthStore), permet
 * au routeur de savoir si ce compte a deja choisi son login (needsLogin) avant de le
 * laisser aller ailleurs que /choose-login (voir router/index.ts). Un compte cree via
 * l'inscription (etape 14) a toujours un login des sa creation - needsLogin ne vaut
 * jamais vrai pour lui ; seul un compte historique (cree avant cette etape) peut se
 * retrouver dans cet etat, une seule fois, jusqu'a ce qu'il en choisisse un.
 */
export const useAccountStore = defineStore('account', () => {
  const me = ref<AccountResponse | null>(null)
  const loaded = ref(false)
  const needsLogin = computed(() => me.value !== null && me.value.login === null)

  async function load(authToken: string) {
    me.value = await getMe(authToken)
    loaded.value = true
  }

  function setMe(account: AccountResponse) {
    me.value = account
    loaded.value = true
  }

  function reset() {
    me.value = null
    loaded.value = false
  }

  return { me, loaded, needsLogin, load, setMe, reset }
})
