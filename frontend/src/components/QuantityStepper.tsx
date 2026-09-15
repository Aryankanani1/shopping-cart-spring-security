interface Props {
  value: number
  min?: number
  max?: number
  onChange: (value: number) => void
  disabled?: boolean
  busy?: boolean
}

export function QuantityStepper({ value, min = 1, max = 99, onChange, disabled, busy }: Props) {
  return (
    <div className="stepper" aria-busy={busy || undefined}>
      <button
        type="button"
        className="stepper__btn"
        onClick={() => onChange(Math.max(min, value - 1))}
        disabled={disabled || busy || value <= min}
        aria-label="Decrease quantity"
      >
        &minus;
      </button>
      <span className="stepper__val" aria-live="polite">
        {value}
      </span>
      <button
        type="button"
        className="stepper__btn"
        onClick={() => onChange(Math.min(max, value + 1))}
        disabled={disabled || busy || value >= max}
        aria-label="Increase quantity"
      >
        +
      </button>
    </div>
  )
}
