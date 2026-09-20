import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { errMessage } from '../lib/errors'
import { ErrorNote } from '../components/ui'
import { vars } from '../lib/format'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await login(email, password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(errMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  function useDemo() {
    setEmail('user1@gmail.com')
    setPassword('123456')
  }

  return (
    <div className="container auth">
      <div className="auth__card rise">
        <span className="eyebrow">Welcome back</span>
        <h1 className="auth__title">Sign in</h1>

        <form className="stack" style={vars({ '--gap': '1.1rem' })} onSubmit={onSubmit}>
          {error && <ErrorNote message={error} />}

          <div>
            <label className="label" htmlFor="email">
              Email
            </label>
            <input
              id="email"
              className="field"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>

          <div>
            <label className="label" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              className="field"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          <button className="btn btn--accent btn--block" type="submit" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <p className="auth__demo faint">
          Dev seed account:{' '}
          <button type="button" className="linkbtn" onClick={useDemo}>
            user1@gmail.com / 123456
          </button>
        </p>

        <p className="auth__alt muted">
          New here?{' '}
          <Link to="/register" className="link link--accent">
            Create an account
          </Link>
        </p>
      </div>
    </div>
  )
}
