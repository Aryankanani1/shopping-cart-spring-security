import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { usersApi } from '../api/users'
import { ApiError } from '../api/client'
import { queryKeys } from '../api/queryKeys'
import { errMessage } from '../lib/errors'
import { useAuth } from '../context/AuthContext'
import { Loader, ErrorNote } from '../components/ui'
import { vars } from '../lib/format'

export function AccountPage() {
  const { userId, logout } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const userKey = queryKeys.user(userId)

  const {
    data: user,
    isLoading,
    error: loadError,
  } = useQuery({
    queryKey: userKey,
    queryFn: () => usersApi.get(userId as number),
    enabled: userId != null,
  })

  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  // Seed the form once the profile arrives (and whenever it changes underneath us).
  useEffect(() => {
    if (user) {
      setFirstName(user.firstName)
      setLastName(user.lastName)
    }
  }, [user])

  const updateMutation = useMutation({
    mutationFn: (body: { firstName: string; lastName: string }) =>
      usersApi.update(userId as number, body),
    onSuccess: (updated) => {
      queryClient.setQueryData(userKey, updated)
      setSaved(true)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => usersApi.remove(userId as number),
    onSuccess: async () => {
      await logout()
      navigate('/', { replace: true })
    },
  })

  const dirty = Boolean(user) && (firstName !== user?.firstName || lastName !== user?.lastName)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    setSaved(false)
    try {
      await updateMutation.mutateAsync({ firstName: firstName.trim(), lastName: lastName.trim() })
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) setFieldErrors(err.fieldErrors)
      else setError(errMessage(err))
    }
  }

  async function onDelete() {
    setError(null)
    try {
      await deleteMutation.mutateAsync()
    } catch (err) {
      setError(errMessage(err))
      setConfirmingDelete(false)
    }
  }

  if (isLoading) {
    return (
      <div className="container section">
        <Loader label="Loading your account" />
      </div>
    )
  }
  if (loadError || !user) {
    return (
      <div className="container section">
        <ErrorNote message={loadError ? errMessage(loadError) : 'Account not found.'} />
      </div>
    )
  }

  return (
    <div className="container section">
      <div className="page-head">
        <span className="eyebrow">Your account</span>
        <h1>Account</h1>
      </div>

      <div className="account">
        <form className="account__card card stack" style={vars({ '--gap': '1.1rem' })} onSubmit={onSubmit}>
          <h3 className="serif">Profile</h3>
          {error && <ErrorNote message={error} />}
          {saved && (
            <p className="note note--ok" role="status">
              Your details have been saved.
            </p>
          )}

          <div className="auth__names">
            <div>
              <label className="label" htmlFor="firstName">
                First name
              </label>
              <input
                id="firstName"
                className="field"
                value={firstName}
                onChange={(e) => {
                  setFirstName(e.target.value)
                  setSaved(false)
                }}
                required
              />
              {fieldErrors.firstName && <span className="field__err">{fieldErrors.firstName}</span>}
            </div>
            <div>
              <label className="label" htmlFor="lastName">
                Last name
              </label>
              <input
                id="lastName"
                className="field"
                value={lastName}
                onChange={(e) => {
                  setLastName(e.target.value)
                  setSaved(false)
                }}
                required
              />
              {fieldErrors.lastName && <span className="field__err">{fieldErrors.lastName}</span>}
            </div>
          </div>

          <div>
            <label className="label" htmlFor="email">
              Email
            </label>
            {/* Email is the login identity and isn't editable via the API. */}
            <input id="email" className="field" type="email" value={user.email} disabled readOnly />
            <span className="faint">Email can’t be changed.</span>
          </div>

          <button
            className="btn btn--accent"
            type="submit"
            disabled={!dirty || updateMutation.isPending}
          >
            {updateMutation.isPending ? 'Saving…' : 'Save changes'}
          </button>
        </form>

        <aside className="account__danger card stack" style={vars({ '--gap': '0.75rem' })}>
          <h3 className="serif">Delete account</h3>
          <p className="muted">
            Permanently delete your account and sign out. This can’t be undone.
          </p>
          {!confirmingDelete ? (
            <button className="btn btn--ghost" type="button" onClick={() => setConfirmingDelete(true)}>
              Delete account
            </button>
          ) : (
            <div className="stack" style={vars({ '--gap': '0.5rem' })}>
              <p className="note note--error" role="alert">
                Are you sure? This is permanent.
              </p>
              <div className="account__confirm">
                <button
                  className="btn btn--danger"
                  type="button"
                  onClick={onDelete}
                  disabled={deleteMutation.isPending}
                >
                  {deleteMutation.isPending ? 'Deleting…' : 'Yes, delete'}
                </button>
                <button
                  className="linkbtn"
                  type="button"
                  onClick={() => setConfirmingDelete(false)}
                  disabled={deleteMutation.isPending}
                >
                  Cancel
                </button>
              </div>
            </div>
          )}
        </aside>
      </div>
    </div>
  )
}
