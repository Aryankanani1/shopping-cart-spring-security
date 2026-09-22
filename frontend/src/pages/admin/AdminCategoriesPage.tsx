import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { categoriesApi } from '../../api/categories'
import { queryKeys } from '../../api/queryKeys'
import type { CategoryDto } from '../../api/types'
import { errMessage } from '../../lib/errors'
import { Loader, ErrorNote, EmptyState } from '../../components/ui'

export function AdminCategoriesPage() {
  const [newName, setNewName] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editingName, setEditingName] = useState('')
  const queryClient = useQueryClient()

  const { data, isLoading, error } = useQuery({
    queryKey: queryKeys.categories,
    queryFn: categoriesApi.list,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.categories })

  const create = useMutation({
    mutationFn: (name: string) => categoriesApi.create(name),
    onSuccess: () => {
      invalidate()
      setNewName('')
    },
  })
  const rename = useMutation({
    mutationFn: ({ id, name }: { id: number; name: string }) => categoriesApi.update(id, name),
    onSuccess: () => {
      invalidate()
      setEditingId(null)
    },
  })
  const remove = useMutation({
    mutationFn: (id: number) => categoriesApi.remove(id),
    onSuccess: invalidate,
  })

  const mutError = create.error ?? rename.error ?? remove.error

  function startEdit(c: CategoryDto) {
    setEditingId(c.id)
    setEditingName(c.name)
  }

  if (isLoading) return <Loader label="Loading categories" />
  if (error) return <ErrorNote message={errMessage(error)} />

  return (
    <>
      <form
        className="admin-inline-form"
        onSubmit={(e) => {
          e.preventDefault()
          if (newName.trim()) create.mutate(newName.trim())
        }}
      >
        <input
          className="field"
          placeholder="New category name"
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
        />
        <button className="btn btn--accent" disabled={create.isPending || !newName.trim()}>
          {create.isPending ? 'Adding…' : 'Add'}
        </button>
      </form>

      {mutError && <ErrorNote message={errMessage(mutError)} />}

      {!data || data.length === 0 ? (
        <EmptyState title="No categories">Add a category above to get started.</EmptyState>
      ) : (
        <ul className="admin-list">
          {data.map((c) => (
            <li key={c.id} className="admin-list__row">
              {editingId === c.id ? (
                <form
                  className="admin-inline-form admin-inline-form--tight"
                  onSubmit={(e) => {
                    e.preventDefault()
                    if (editingName.trim()) rename.mutate({ id: c.id, name: editingName.trim() })
                  }}
                >
                  <input
                    className="field"
                    value={editingName}
                    onChange={(e) => setEditingName(e.target.value)}
                    autoFocus
                  />
                  <button className="btn btn--sm btn--accent" disabled={rename.isPending}>
                    Save
                  </button>
                  <button type="button" className="btn btn--sm btn--ghost" onClick={() => setEditingId(null)}>
                    Cancel
                  </button>
                </form>
              ) : (
                <>
                  <span className="admin-list__name">{c.name}</span>
                  <span className="admin-list__actions">
                    <button className="linkbtn" onClick={() => startEdit(c)}>
                      Rename
                    </button>
                    <button
                      className="linkbtn linkbtn--danger"
                      onClick={() => {
                        if (window.confirm(`Delete category "${c.name}"?`)) remove.mutate(c.id)
                      }}
                      disabled={remove.isPending}
                    >
                      Delete
                    </button>
                  </span>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
