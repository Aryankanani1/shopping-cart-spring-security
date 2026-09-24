import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { productsApi } from '../../api/products'
import type { ProductInput } from '../../api/products'
import { categoriesApi } from '../../api/categories'
import { queryKeys } from '../../api/queryKeys'
import type { ProductDto } from '../../api/types'
import { errMessage } from '../../lib/errors'
import { formatMoney } from '../../lib/format'
import { Loader, ErrorNote, EmptyState } from '../../components/ui'
import { ProductForm } from './ProductForm'
import { ProductImages } from './ProductImages'

export function AdminProductsPage() {
  const [page, setPage] = useState(0)
  // null = form closed; 'new' = create; a product = edit that product.
  const [editing, setEditing] = useState<ProductDto | 'new' | null>(null)
  const queryClient = useQueryClient()

  const { data, isLoading, error, isFetching } = useQuery({
    queryKey: queryKeys.admin.products(page),
    queryFn: () => productsApi.list({ page, size: 20, sort: 'id' }),
    placeholderData: keepPreviousData,
  })
  const categories = useQuery({ queryKey: queryKeys.categories, queryFn: categoriesApi.list })

  // A write touches both the admin list and the public catalogue.
  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['admin', 'products'] })
    queryClient.invalidateQueries({ queryKey: queryKeys.products.all })
  }

  const save = useMutation({
    mutationFn: (input: ProductInput) =>
      editing && editing !== 'new' ? productsApi.update(editing.id, input) : productsApi.create(input),
    onSuccess: (saved) => {
      invalidate()
      // A new product stays open (now in edit mode) so images can be added right
      // away — uploads need the product's id. Saving an edit closes the form.
      setEditing(editing === 'new' ? saved : null)
    },
  })

  const remove = useMutation({
    mutationFn: (id: number) => productsApi.remove(id),
    onSuccess: invalidate,
  })

  if (isLoading) return <Loader label="Loading products" />
  if (error) return <ErrorNote message={errMessage(error)} />

  return (
    <>
      <div className="admin-bar">
        <button className="btn btn--accent" onClick={() => setEditing('new')} disabled={editing !== null}>
          + New product
        </button>
      </div>

      {editing !== null && (
        <ProductForm
          key={editing === 'new' ? 'new' : editing.id}
          initial={editing === 'new' ? null : editing}
          categories={categories.data ?? []}
          onSubmit={(input) => save.mutate(input)}
          onCancel={() => setEditing(null)}
          busy={save.isPending}
          error={save.isError ? errMessage(save.error) : null}
        />
      )}

      {editing !== null && editing !== 'new' && <ProductImages key={editing.id} productId={editing.id} />}

      {remove.isError && <ErrorNote message={errMessage(remove.error)} />}

      {!data || data.content.length === 0 ? (
        <EmptyState title="No products">Create your first product to see it here.</EmptyState>
      ) : (
        <>
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Brand</th>
                  <th>Category</th>
                  <th className="admin-table__num">Price</th>
                  <th className="admin-table__num">Stock</th>
                  <th className="admin-table__right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((p) => (
                  <tr key={p.id}>
                    <td>{p.name}</td>
                    <td className="faint">{p.brand}</td>
                    <td className="faint">{p.categoryName}</td>
                    <td className="admin-table__num price">{formatMoney(p.price)}</td>
                    <td className="admin-table__num">{p.inventory}</td>
                    <td className="admin-table__right">
                      <button className="linkbtn" onClick={() => setEditing(p)}>
                        Edit
                      </button>
                      <button
                        className="linkbtn linkbtn--danger"
                        onClick={() => {
                          if (window.confirm(`Delete "${p.name}"? This cannot be undone.`)) remove.mutate(p.id)
                        }}
                        disabled={remove.isPending}
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="admin-pager">
            <button
              className="btn btn--ghost"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={data.first || isFetching}
            >
              ← Prev
            </button>
            <span className="faint">
              Page {data.number + 1} of {Math.max(1, data.totalPages)}
            </span>
            <button
              className="btn btn--ghost"
              onClick={() => setPage((p) => p + 1)}
              disabled={data.last || isFetching}
            >
              Next →
            </button>
          </div>
        </>
      )}
    </>
  )
}
