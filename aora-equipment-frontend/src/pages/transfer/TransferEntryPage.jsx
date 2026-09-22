import { useAuth0 } from '@auth0/auth0-react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchCurrentUser } from '../../api/transferApi'

export default function TransferEntryPage() {
  const { getAccessTokenSilently, user, logout } = useAuth0()
  const navigate = useNavigate()
  const scannerRef = useRef(null)
  const [equipmentCode, setEquipmentCode] = useState('')
  const [profile, setProfile] = useState(null)
  const [scanning, setScanning] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    fetchCurrentUser({ getAccessTokenSilently })
      .then((currentUser) => { if (!cancelled) setProfile(currentUser) })
      .catch((requestError) => { if (!cancelled) setError(requestError.message || 'Unable to load your user profile.') })
    return () => { cancelled = true }
  }, [getAccessTokenSilently])

  useEffect(() => {
    if (!scanning || !scannerRef.current || !('BarcodeDetector' in window)) return undefined
    const video = scannerRef.current
    let active = true
    let stream
    let animationFrame

    async function scan() {
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
        video.srcObject = stream
        await video.play()
        const detector = new window.BarcodeDetector({ formats: ['qr_code'] })
        const read = async () => {
          if (!active) return
          const codes = await detector.detect(video)
          if (codes[0]?.rawValue) {
            setEquipmentCode(codes[0].rawValue)
            setScanning(false)
            return
          }
          animationFrame = requestAnimationFrame(read)
        }
        read()
      } catch (requestError) {
        setError(requestError.message || 'Camera access was not available.')
        setScanning(false)
      }
    }

    scan()
    return () => {
      active = false
      cancelAnimationFrame(animationFrame)
      stream?.getTracks().forEach((track) => track.stop())
    }
  }, [scanning])

  const continueToTransfers = (event) => {
    event.preventDefault()
    const code = equipmentCode.trim()
    if (code) navigate(`/transfers/manage?equipmentCode=${encodeURIComponent(code)}${event.nativeEvent.submitter?.value === 'receive' ? '&mode=receive' : ''}`)
  }

  return (
    <div className="dashboard-shell">
      <header className="dashboard-header">
        <div><p className="eyebrow">AORA EQUIPMENT</p><h1>Start a transfer</h1><p className="muted">Signed in as {user?.name || user?.email}</p></div>
        <div className="header-actions"><button className="button button-secondary" onClick={() => navigate('/dashboard')}>Dashboard</button><button className="button button-secondary" onClick={() => navigate('/equipment')}>Equipment</button><button className="button button-secondary" onClick={() => logout({ logoutParams: { returnTo: window.location.origin } })}>Sign out</button></div>
      </header>
      <main className="dashboard-content transfer-entry-content">
        {profile?.role === 'MANAGER' ? <button className="button button-secondary" onClick={() => navigate('/transfers/manage?mode=all')}>All transfers</button> : null}
        <section className="equipment-panel transfer-entry-panel">
          <div className="panel-heading"><div><h2>Find equipment</h2><p className="muted">Enter the equipment code or scan its QR code to continue.</p></div></div>
          <form className="transfer-entry-form" onSubmit={continueToTransfers}>
            <label><span>Equipment code</span><input autoFocus required value={equipmentCode} onChange={(event) => setEquipmentCode(event.target.value)} placeholder="e.g. EXC-001" /></label>
            <div className="entry-actions"><button type="button" className="button button-secondary" onClick={() => { setError(''); setScanning((current) => !current) }}>{scanning ? 'Stop scanner' : 'Scan QR code'}</button><button className="button button-primary">Continue</button>{['MANAGER', 'STORE_MANAGER'].includes(profile?.role) ? <button className="button button-primary" type="submit" value="receive">Receive at store</button> : null}</div>
            {scanning ? ('BarcodeDetector' in window ? <video className="qr-preview" ref={scannerRef} muted playsInline /> : <p className="muted">QR scanning is not supported by this browser. Enter the equipment code instead.</p>) : null}
            {profile?.role === 'SITE_ADMIN' ? <p className="entry-context">Your worksite: {profile.currentWorksite?.name || 'Not assigned'}</p> : null}
            {error ? <p className="error-message">{error}</p> : null}
          </form>
        </section>
      </main>
    </div>
  )
}
