/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Origin of the Spring API (empty in dev — the Vite proxy handles /api). */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
