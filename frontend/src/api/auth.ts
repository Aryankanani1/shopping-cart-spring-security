import { request } from './client'
import type { JwtResponse, UserDto } from './types'

export interface RegisterBody {
  firstName: string
  lastName: string
  email: string
  password: string
}

export const authApi = {
  login: (email: string, password: string) =>
    request<JwtResponse>('/auth/login', { method: 'POST', auth: false, body: { email, password } }),

  /** Self-registration: POST /users. Returns the created user (201). */
  register: (body: RegisterBody) =>
    request<UserDto>('/users', { method: 'POST', auth: false, body }),

  /** Revoke the refresh token server-side. Idempotent. */
  logout: (refreshToken: string) =>
    request<null>('/auth/logout', { method: 'POST', auth: false, body: { refreshToken } }),
}
