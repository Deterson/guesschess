import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import GameView from '../views/GameView.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    {
      path: '/game/:gameId',
      name: 'game',
      component: GameView,
      props: (route) => ({
        gameId: route.params.gameId,
        token: route.query.token,
        color: route.query.color,
      }),
    },
  ],
})
