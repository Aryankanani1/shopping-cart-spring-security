// Read the (unverified) claims out of a JWT access token, purely to drive UI
// gating. The backend re-verifies the signature and re-checks authorities on
// every request, so decoding here is not a trust boundary — it only decides
// which admin affordances to render. A tampered token buys nothing: the server
// still rejects the call.

interface JwtClaims {
  roles?: string[]
  [key: string]: unknown
}

/** Decode a JWT payload without verifying the signature. Returns null on any malformed input. */
function decodeClaims(token: string | undefined | null): JwtClaims | null {
  if (!token) return null
  const payload = token.split('.')[1]
  if (!payload) return null
  try {
    // JWT uses base64url; restore standard base64 before decoding.
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    )
    return JSON.parse(json) as JwtClaims
  } catch {
    return null
  }
}

/** The authority strings carried in the token's `roles` claim (e.g. ["ROLE_ADMIN"]). */
export function rolesFromToken(token: string | undefined | null): string[] {
  const roles = decodeClaims(token)?.roles
  return Array.isArray(roles) ? roles : []
}

/** Whether the token grants the admin authority. */
export function isAdminToken(token: string | undefined | null): boolean {
  return rolesFromToken(token).includes('ROLE_ADMIN')
}
