import type { NotificationDto } from '../api/types'
import { formatMoney } from './format'

/**
 * The sentence for an inbox entry. The API sends structured facts (type, name,
 * prices) rather than text, so wording and money formatting live here.
 */
export function notificationText(n: NotificationDto): string {
  switch (n.type) {
    case 'WISHLIST_REMINDER':
      return `Reminder: ${n.productName} is still on your wishlist.`
    case 'PRICE_DROP':
      return `Price drop: ${n.productName} is now ${formatMoney(n.newPrice)} (was ${formatMoney(n.oldPrice)}).`
    case 'BACK_IN_STOCK':
      return `Back in stock: ${n.productName} is available again.`
  }
}
