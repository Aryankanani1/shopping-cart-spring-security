import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { NEXT_STATUSES, ordersApi } from '../../api/orders'
import { queryKeys } from '../../api/queryKeys'
import { errMessage } from '../../lib/errors'
import { formatDate, formatMoney } from '../../lib/format'
import { Loader, ErrorNote, EmptyState } from '../../components/ui'
import { StatusBadge } from '../../components/StatusBadge'

export function AdminOrdersPage() {
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()

  const { data, isLoading, error, isFetching } = useQuery({
    queryKey: queryKeys.orders.adminList(page),
    queryFn: () => ordersApi.adminList(page),
    placeholderData: keepPreviousData,
  })

  const setStatus = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) => ordersApi.updateStatus(id, status),
    onSuccess: () => {
      // Refresh every order view: this list, any detail page, and user histories.
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all })
    },
  })

  if (isLoading) return <Loader label="Loading orders" />
  if (error) return <ErrorNote message={errMessage(error)} />
  if (!data || data.content.length === 0) {
    return <EmptyState title="No orders yet">Orders placed by customers will appear here.</EmptyState>
  }

  return (
    <>
      {setStatus.isError && <ErrorNote message={errMessage(setStatus.error)} />}

      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Order</th>
              <th>Date</th>
              <th>Customer</th>
              <th className="admin-table__num">Total</th>
              <th>Status</th>
              <th>Change status</th>
            </tr>
          </thead>
          <tbody>
            {data.content.map((order) => {
              const nextOptions = NEXT_STATUSES[order.status] ?? []
              const rowBusy = setStatus.isPending && setStatus.variables?.id === order.id
              return (
                <tr key={order.id}>
                  <td>#{order.id}</td>
                  <td className="faint">{formatDate(order.orderDate)}</td>
                  <td>{order.userEmail}</td>
                  <td className="admin-table__num price">{formatMoney(order.totalAmount)}</td>
                  <td>
                    <StatusBadge status={order.status} />
                  </td>
                  <td>
                    {nextOptions.length === 0 ? (
                      <span className="faint">—</span>
                    ) : (
                      <select
                        className="admin-select"
                        value=""
                        disabled={rowBusy}
                        onChange={(e) => {
                          const status = e.target.value
                          if (status) setStatus.mutate({ id: order.id, status })
                        }}
                      >
                        <option value="">{rowBusy ? 'Updating…' : 'Move to…'}</option>
                        {nextOptions.map((s) => (
                          <option key={s} value={s}>
                            {s.toLowerCase()}
                          </option>
                        ))}
                      </select>
                    )}
                  </td>
                </tr>
              )
            })}
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
  )
}
