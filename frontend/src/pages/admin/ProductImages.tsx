import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { productsApi } from '../../api/products'
import { imagesApi } from '../../api/images'
import { imageUrl } from '../../api/client'
import { queryKeys } from '../../api/queryKeys'
import { errMessage } from '../../lib/errors'
import { Loader, ErrorNote } from '../../components/ui'

/** The formats the API accepts (see ImageService); the picker offers only these. */
const ACCEPTED_TYPES = 'image/jpeg,image/png,image/webp,image/gif'

/**
 * Image manager for one product: shows its current images and lets an admin
 * upload more or remove one. Reads the product fresh rather than using the list
 * row the form was opened with, so the gallery reflects each change immediately.
 */
export function ProductImages({ productId }: { productId: number }) {
  const queryClient = useQueryClient()

  const { data: product, isLoading, error } = useQuery({
    queryKey: queryKeys.products.detail(productId),
    queryFn: () => productsApi.get(productId),
  })

  // Images appear in the admin list, the catalogue and the product page.
  function invalidate() {
    queryClient.invalidateQueries({ queryKey: queryKeys.products.all })
    queryClient.invalidateQueries({ queryKey: ['admin', 'products'] })
  }

  const upload = useMutation({
    mutationFn: (files: File[]) => imagesApi.upload(productId, files),
    onSuccess: invalidate,
  })

  const remove = useMutation({
    mutationFn: (imageId: number) => imagesApi.remove(imageId),
    onSuccess: invalidate,
  })

  function handleFiles(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? [])
    // Reset so choosing the same file again still fires onChange.
    e.target.value = ''
    if (files.length > 0) upload.mutate(files)
  }

  const images = product?.images ?? []
  const actionError = upload.error ?? remove.error

  return (
    <section className="admin-form" aria-labelledby="product-images-title">
      <h2 id="product-images-title" className="admin-form__title serif">
        Images
      </h2>

      {isLoading && <Loader label="Loading images" />}
      {error && <ErrorNote message={errMessage(error)} />}
      {actionError && <ErrorNote message={errMessage(actionError)} />}

      {product &&
        (images.length === 0 ? (
          <p className="faint">No images yet.</p>
        ) : (
          <ul className="admin-images">
            {images.map((img) => (
              <li key={img.imageId} className="admin-images__item">
                <img src={imageUrl(img.imageId)} alt={img.imageName} className="admin-images__thumb" />
                <button
                  type="button"
                  className="linkbtn linkbtn--danger"
                  onClick={() => {
                    if (window.confirm(`Remove ${img.imageName}?`)) remove.mutate(img.imageId)
                  }}
                  disabled={remove.isPending}
                >
                  Remove
                </button>
              </li>
            ))}
          </ul>
        ))}

      <label className="admin-field">
        <span className="admin-field__label">Add images</span>
        <input type="file" accept={ACCEPTED_TYPES} multiple onChange={handleFiles} disabled={upload.isPending} />
      </label>
      <p className="faint admin-images__hint">
        {upload.isPending ? 'Uploading…' : 'JPEG, PNG, WebP or GIF, up to 5 MB each.'}
      </p>
    </section>
  )
}
