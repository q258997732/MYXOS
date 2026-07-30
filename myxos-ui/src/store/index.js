import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const username = ref(localStorage.getItem('username') || '')
  const role = ref(localStorage.getItem('role') || '')

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')

  function setUser(user) {
    token.value = user.token
    username.value = user.username
    role.value = user.role
    localStorage.setItem('token', user.token)
    localStorage.setItem('username', user.username)
    localStorage.setItem('role', user.role)
  }

  function clearUser() {
    token.value = ''
    username.value = ''
    role.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('role')
  }

  return { token, username, role, isLoggedIn, isAdmin, setUser, clearUser }
})
