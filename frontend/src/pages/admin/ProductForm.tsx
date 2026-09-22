import { useState } from 'react'
import type { CategoryDto, ProductDto } from '../../api/types'
import type { ProductInput } from '../../api/products'
import { ErrorNote } from '../../components/ui'

interface Props {
  /** The product being edited, or null when creating a new one. */
  initial: ProductDto | null
  categories: CategoryDto[]
  onSubmit: (input: ProductInput) => void
  onCancel: () => void
  busy: boolean
  error: string | null
}

/**
 * Create/edit form for a product. Uncontrolled-ish: seeds local state from the
 * `initial` product once (the parent remounts it with a `key` when the target
 * changes), then owns its fields until submit.
 */
export function ProductForm({ initial, categories, onSubmit, onCancel, busy, error }: Props) {
  const [name, setName] = useState(initial?.name ?? '')
  const [brand, setBrand] = useState(initial?.brand ?? '')
  const [price, setPrice] = useState(initial ? String(initial.price) : '')
  const [inventory, setInventory] = useState(initial ? String(initial.inventory) : '0')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [categoryName, setCategoryName] = useState(initial?.categoryName ?? categories[0]?.name ?? '')

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    onSubmit({
      name: name.trim(),
      brand: brand.trim(),
      price: Number(price),
      inventory: Number(inventory),
      description: description.trim(),
      categoryName,
    })
  }

  return (
    <form className="admin-form" onSubmit={handleSubmit}>
      <h2 className="admin-form__title serif">{initial ? `Edit ${initial.name}` : 'New product'}</h2>

      {error && <ErrorNote message={error} />}

      <div className="admin-form__grid">
        <label className="admin-field">
          <span className="admin-field__label">Name</span>
          <input className="field" value={name} onChange={(e) => setName(e.target.value)} required />
        </label>
        <label className="admin-field">
          <span className="admin-field__label">Brand</span>
          <input className="field" value={brand} onChange={(e) => setBrand(e.target.value)} required />
        </label>
        <label className="admin-field">
          <span className="admin-field__label">Price</span>
          <input
            className="field"
            type="number"
            step="0.01"
            min="0.01"
            value={price}
            onChange={(e) => setPrice(e.target.value)}
            required
          />
        </label>
        <label className="admin-field">
          <span className="admin-field__label">Inventory</span>
          <input
            className="field"
            type="number"
            min="0"
            value={inventory}
            onChange={(e) => setInventory(e.target.value)}
            required
          />
        </label>
        <label className="admin-field">
          <span className="admin-field__label">Category</span>
          <select className="field" value={categoryName} onChange={(e) => setCategoryName(e.target.value)} required>
            {categories.map((c) => (
              <option key={c.id} value={c.name}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      <label className="field">
        <span className="field__label">Description</span>
        <textarea
          className="field"
          rows={3}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </label>

      <div className="admin-form__actions">
        <button type="submit" className="btn btn--accent" disabled={busy}>
          {busy ? 'Saving…' : initial ? 'Save changes' : 'Create product'}
        </button>
        <button type="button" className="btn btn--ghost" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
      </div>
    </form>
  )
}
