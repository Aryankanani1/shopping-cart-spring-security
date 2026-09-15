// Registers jest-dom matchers (toBeInTheDocument, toHaveAttribute, …) on
// Vitest's expect, and tears down the rendered DOM between tests.
import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

// jsdom's localStorage can be absent or opaque depending on the document origin;
// provide a deterministic in-memory implementation for tests that touch the
// token store, so `localStorage.*` never throws.
if (typeof globalThis.localStorage === 'undefined') {
  class MemoryStorage {
    private store = new Map<string, string>()
    get length() {
      return this.store.size
    }
    clear() {
      this.store.clear()
    }
    getItem(key: string) {
      return this.store.has(key) ? this.store.get(key)! : null
    }
    setItem(key: string, value: string) {
      this.store.set(key, String(value))
    }
    removeItem(key: string) {
      this.store.delete(key)
    }
    key(index: number) {
      return Array.from(this.store.keys())[index] ?? null
    }
  }
  Object.defineProperty(globalThis, 'localStorage', {
    value: new MemoryStorage(),
    configurable: true,
    writable: true,
  })
}

afterEach(() => {
  cleanup()
})
