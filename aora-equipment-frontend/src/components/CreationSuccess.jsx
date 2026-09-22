import { useEffect, useRef } from 'react'
import { CheckCircle2 } from 'lucide-react'
import './CreationSuccess.css'

export default function CreationSuccess({ title, children }) {
  const panel = useRef(null)
  useEffect(() => {
    panel.current?.focus({ preventScroll: true })
    panel.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }, [title])

  return <div ref={panel} className="creation-success" role="status" tabIndex={-1}>
    <CheckCircle2 size={40} aria-hidden="true" />
    <div><h2>{title}</h2>{children ? <p>{children}</p> : null}</div>
  </div>
}
