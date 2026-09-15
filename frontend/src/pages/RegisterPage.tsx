import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { ApiError } from '../api/client'
import { errMessage } from '../hooks/useAsync'
import { ErrorNote } from '../components/ui'
import { vars } from '../lib/format'

type FormState = { firstName: string; lastName: string; email: string; password: string }

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState<FormState>({ firstName: '', lastName: '', email: '', password: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function set<K extends keyof FormState>(key: K, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      await register(form)
      navigate('/', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) {
        setFieldErrors(err.fieldErrors)
      } else {
        setError(errMessage(err))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="container auth">
      <div className="auth__card rise">
        <span className="eyebrow">Join Meridian</span>
        <h1 className="auth__title">Create account</h1>

        <form className="stack" style={vars({ '--gap': '1.1rem' })} onSubmit={onSubmit}>
          {error && <ErrorNote message={error} />}

          <div className="auth__names">
            <div>
              <label className="label" htmlFor="firstName">
                First name
              </label>
              <input
                id="firstName"
                className="field"
                value={form.firstName}
                onChange={(e) => set('firstName', e.target.value)}
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
                value={form.lastName}
                onChange={(e) => set('lastName', e.target.value)}
                required
              />
              {fieldErrors.lastName && <span className="field__err">{fieldErrors.lastName}</span>}
            </div>
          </div>

          <div>
            <label className="label" htmlFor="email">
              Email
            </label>
            <input
              id="email"
              className="field"
              type="email"
              autoComplete="email"
              value={form.email}
              onChange={(e) => set('email', e.target.value)}
              required
            />
            {fieldErrors.email && <span className="field__err">{fieldErrors.email}</span>}
          </div>

          <div>
            <label className="label" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              className="field"
              type="password"
              autoComplete="new-password"
              value={form.password}
              onChange={(e) => set('password', e.target.value)}
              required
              minLength={6}
            />
            {fieldErrors.password && <span className="field__err">{fieldErrors.password}</span>}
          </div>

          <button className="btn btn--accent btn--block" type="submit" disabled={submitting}>
            {submitting ? 'Creating…' : 'Create account'}
          </button>
        </form>

        <p className="auth__alt muted">
          Already have an account?{' '}
          <Link to="/login" className="link link--accent">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
