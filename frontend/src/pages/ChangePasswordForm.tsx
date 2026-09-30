import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { errMessage } from '../lib/errors'
import { useAuth } from '../context/AuthContext'
import { ErrorNote } from '../components/ui'
import { vars } from '../lib/format'

// Mirrors ChangePasswordRequest on the backend (BCrypt hashes at most 72 bytes).
const MIN_LENGTH = 6
const MAX_LENGTH = 72

export function ChangePasswordForm() {
  const { changePassword } = useAuth()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const mutation = useMutation({
    mutationFn: (body: { currentPassword: string; newPassword: string }) =>
      changePassword(body.currentPassword, body.newPassword),
  })

  // Catch the obvious mistakes before a round-trip; the server re-checks all of it.
  function validate(): Record<string, string> {
    const errs: Record<string, string> = {}
    if (newPassword.length < MIN_LENGTH || newPassword.length > MAX_LENGTH) {
      errs.newPassword = `New password must be ${MIN_LENGTH}-${MAX_LENGTH} characters`
    } else if (newPassword === currentPassword) {
      errs.newPassword = 'New password must be different from the current one'
    }
    if (confirmPassword !== newPassword) errs.confirmPassword = 'Passwords don’t match'
    return errs
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaved(false)
    const errs = validate()
    setFieldErrors(errs)
    if (Object.keys(errs).length > 0) return
    try {
      await mutation.mutateAsync({ currentPassword, newPassword })
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setSaved(true)
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) setFieldErrors(err.fieldErrors)
      else setError(errMessage(err))
    }
  }

  return (
    <form className="account__card card stack" style={vars({ '--gap': '1.1rem' })} onSubmit={onSubmit}>
      <h3 className="serif">Password</h3>
      {error && <ErrorNote message={error} />}
      {saved && (
        <p className="note note--ok" role="status">
          Password changed. You’ve been signed out on your other devices.
        </p>
      )}

      <div>
        <label className="label" htmlFor="currentPassword">
          Current password
        </label>
        <input
          id="currentPassword"
          className="field"
          type="password"
          autoComplete="current-password"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          required
        />
        {fieldErrors.currentPassword && <span className="field__err">{fieldErrors.currentPassword}</span>}
      </div>

      <div>
        <label className="label" htmlFor="newPassword">
          New password
        </label>
        <input
          id="newPassword"
          className="field"
          type="password"
          autoComplete="new-password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          required
        />
        {fieldErrors.newPassword && <span className="field__err">{fieldErrors.newPassword}</span>}
      </div>

      <div>
        <label className="label" htmlFor="confirmPassword">
          Confirm new password
        </label>
        <input
          id="confirmPassword"
          className="field"
          type="password"
          autoComplete="new-password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          required
        />
        {fieldErrors.confirmPassword && <span className="field__err">{fieldErrors.confirmPassword}</span>}
      </div>

      <button className="btn btn--accent" type="submit" disabled={mutation.isPending}>
        {mutation.isPending ? 'Changing…' : 'Change password'}
      </button>
    </form>
  )
}
