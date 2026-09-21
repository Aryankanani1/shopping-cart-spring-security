import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { productsApi } from '../api/products'
import { categoriesApi } from '../api/categories'
import { queryKeys } from '../api/queryKeys'
import { errMessage } from '../lib/errors'
import { ProductCard } from '../components/ProductCard'
import { Loader, ErrorNote, EmptyState } from '../components/ui'

// Sort values must use fields the API allowlists: id, name, price, brand.
const SORTS = [
  { label: 'Newest', value: 'id,desc' },
  { label: 'Price · low to high', value: 'price,asc' },
  { label: 'Price · high to low', value: 'price,desc' },
  { label: 'Name · A–Z', value: 'name,asc' },
]
const PAGE_SIZE = 12

export function ProductsPage() {
  const [params, setParams] = useSearchParams()
  const name = params.get('name') ?? ''
  const category = params.get('category') ?? ''
  const sort = params.get('sort') ?? 'id,desc'
  const page = Math.max(0, Number(params.get('page') ?? '0') || 0)

  const [term, setTerm] = useState(name)
  useEffect(() => setTerm(name), [name])

  const cats = useQuery({ queryKey: queryKeys.categories, queryFn: () => categoriesApi.list() })
  const filters = { name, category, sort, page, size: PAGE_SIZE }
  const productsQuery = useQuery({
    queryKey: queryKeys.products.list(filters),
    queryFn: () => productsApi.list(filters),
    // Keep the previous page visible while the next loads — no loader flash on paging.
    placeholderData: keepPreviousData,
  })
  const data = productsQuery.data
  const loading = productsQuery.isLoading
  const error = productsQuery.error ? errMessage(productsQuery.error) : null

  function update(next: Record<string, string | undefined>, resetPage = false) {
    const p = new URLSearchParams(params)
    for (const [k, v] of Object.entries(next)) {
      if (v === undefined || v === '') p.delete(k)
      else p.set(k, v)
    }
    if (resetPage) p.delete('page')
    setParams(p)
  }

  function onSearch(e: FormEvent) {
    e.preventDefault()
    update({ name: term.trim() }, true)
  }

  const products = data?.content ?? []
  const hasFilters = Boolean(name || category)

  return (
    <div className="container section">
      <div className="page-head">
        <span className="eyebrow">The catalogue</span>
        <h1>Shop</h1>
      </div>

      <div className="filters">
        <form className="filters__search" onSubmit={onSearch} role="search">
          <input
            className="field"
            placeholder="Search products…"
            value={term}
            onChange={(e) => setTerm(e.target.value)}
            aria-label="Search products"
          />
          <button className="btn btn--sm" type="submit">
            Search
          </button>
        </form>
        <div className="filters__controls">
          <select
            className="field"
            value={category}
            onChange={(e) => update({ category: e.target.value }, true)}
            aria-label="Filter by category"
          >
            <option value="">All categories</option>
            {(cats.data ?? []).map((c) => (
              <option key={c.id} value={c.name}>
                {c.name}
              </option>
            ))}
          </select>
          <select
            className="field"
            value={sort}
            onChange={(e) => update({ sort: e.target.value }, true)}
            aria-label="Sort products"
          >
            {SORTS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {hasFilters && (
        <div className="filters__active">
          <span className="faint">
            {data ? `${data.totalElements} result${data.totalElements === 1 ? '' : 's'}` : '…'}
          </span>
          <button className="linkbtn" onClick={() => update({ name: undefined, category: undefined }, true)}>
            Clear filters
          </button>
        </div>
      )}

      {loading && <Loader label="Loading products" />}
      {error && <ErrorNote message={error} />}
      {!loading && !error && products.length === 0 && (
        <EmptyState title="Nothing matches those filters.">
          <button
            className="btn btn--ghost"
            onClick={() => update({ name: undefined, category: undefined }, true)}
          >
            Reset filters
          </button>
        </EmptyState>
      )}
      {!loading && !error && products.length > 0 && (
        <>
          <div className="product-grid">
            {products.map((product, i) => (
              <ProductCard key={product.id} product={product} index={i} />
            ))}
          </div>
          {data && data.totalPages > 1 && (
            <div className="pager">
              <button
                className="btn btn--ghost btn--sm"
                disabled={data.first}
                onClick={() => update({ page: String(page - 1) })}
              >
                ← Prev
              </button>
              <span className="pager__count faint">
                Page {data.number + 1} of {data.totalPages}
              </span>
              <button
                className="btn btn--ghost btn--sm"
                disabled={data.last}
                onClick={() => update({ page: String(page + 1) })}
              >
                Next →
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
